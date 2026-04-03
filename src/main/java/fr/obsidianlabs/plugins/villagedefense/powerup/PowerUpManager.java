package fr.obsidianlabs.plugins.villagedefense.powerup;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.api.VillageDefenseApiIds;
import fr.obsidianlabs.plugins.villagedefense.api.VillageDefenseProgressType;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.util.ChatUtil;
import fr.obsidianlabs.plugins.villagedefense.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gère les power-ups que les mobs peuvent lâcher en mourant.
 */
public class PowerUpManager {

    private static final String PDC_KEY = "vd_powerup";
    private static final long GROUND_LIFETIME_TICKS = 45L * 20L;

    private final VillageDefensePlugin plugin;

    // UUID joueur → timestamp d'expiration en ms
    private final Map<UUID, Long> instantKillExpiry  = new HashMap<>();
    private final Map<UUID, Long> doubleDamageExpiry = new HashMap<>();

    public PowerUpManager(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    private String m(String key, Object... args) {
        return plugin.getMessages().format(key, args);
    }

    // ── Drop ─────────────────────────────────────────────────────────────────

    public void tryDropPowerUp(Arena arena, LivingEntity mob) {
        double chance = switch (mob.getType()) {
            case ZOMBIE, SKELETON, SPIDER -> 0.15;
            case WOLF, CREEPER, WITCH     -> 0.25;
            default                       -> 0.0;
        };

        if (chance <= 0 || ThreadLocalRandom.current().nextDouble() > chance) return;

        PowerUpType[] types = PowerUpType.values();
        PowerUpType type = types[ThreadLocalRandom.current().nextInt(types.length)];

        Item item = mob.getLocation().add(0, 0.5, 0)
            .getWorld().dropItem(mob.getLocation().add(0, 0.5, 0), buildItem(type));
        item.setVelocity(new org.bukkit.util.Vector(0, 0.2, 0));
        item.setPickupDelay(10);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (item.isValid() && !item.isDead()) {
                item.remove();
            }
        }, GROUND_LIFETIME_TICKS);
    }

    private ItemStack buildItem(PowerUpType type) {
        String durationLine = type.isInstant()
            ? m("powerup.duration-instant")
            : m("powerup.duration", type.getDurationTicks() / 20);

        ItemStack stack = new ItemBuilder(type.getIcon())
            .name(m(type.getChatKey()))
            .lore(m("powerup.pickup-lore"), durationLine)
            .glowing()
            .build();

        stack.editMeta(meta -> meta.getPersistentDataContainer()
            .set(plugin.getKey(PDC_KEY), PersistentDataType.STRING, type.name()));

        return stack;
    }

    // ── Pickup ───────────────────────────────────────────────────────────────

    public boolean handlePickup(Player player, ItemStack item) {
        if (!item.hasItemMeta()) return false;
        String typeName = item.getItemMeta().getPersistentDataContainer()
            .get(plugin.getKey(PDC_KEY), PersistentDataType.STRING);
        if (typeName == null) return false;

        PowerUpType type;
        try {
            type = PowerUpType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            return false;
        }

        applyPowerUp(player, type);
        return true;
    }

    private void applyPowerUp(Player picker, PowerUpType type) {
        Arena arena = plugin.getArenaManager().getArenaOfPlayer(picker);
        Iterable<Player> targets = (arena != null) ? arena.getPlayers() : java.util.List.of(picker);

        if (!type.isSpecial()) {
            // Effet potion appliqué à tous les joueurs de l'arène
            PotionEffect effect = new PotionEffect(
                type.getEffectType(), type.getDurationTicks(), type.getAmplifier(), false, true, true);
            for (Player p : targets) p.addPotionEffect(effect);
        } else {
            switch (type) {
                case ZOMBIE_CLEAR  -> applyPurge(picker, arena);
                case INSTANT_KILL  -> activateTimedAll(instantKillExpiry,  targets, type.getDurationTicks());
                case DOUBLE_DAMAGE -> activateTimedAll(doubleDamageExpiry, targets, type.getDurationTicks());
                default -> {}
            }
        }

        announce(picker, arena, m(type.getChatKey()));
        plugin.fireProgress(picker, arena, VillageDefenseProgressType.POWERUP_PICKUP, 1, 0, VillageDefenseApiIds.of(type));
        for (Player p : targets) p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
    }

    private void activateTimedAll(Map<UUID, Long> map, Iterable<Player> players, int durationTicks) {
        long expiry = System.currentTimeMillis() + (long) durationTicks * 50L;
        for (Player p : players) map.put(p.getUniqueId(), expiry);
    }

    private void applyPurge(Player player, Arena arena) {
        if (arena == null) return;
        int count = 0;
        for (UUID uid : new ArrayList<>(arena.getActiveMobUUIDs())) {
            if (plugin.getGameManager().isBossEntity(arena, uid)) {
                continue;
            }

            Entity e = Bukkit.getEntity(uid);
            if (e instanceof LivingEntity && e.isValid()) {
                e.remove();
                // Notifier le WaveManager manuellement
                boolean waveComplete = plugin.getWaveManager().onMobDeath(arena, e);
                if (waveComplete && arena.getState() ==
                        fr.obsidianlabs.plugins.villagedefense.arena.ArenaState.RUNNING) {
                    plugin.getGameManager().onWaveCompleted(arena);
                }
                count++;
            }
        }
        if (count > 0) {
            broadcastArena(arena, m("powerup.zombie-clear", count, player.getName()));
        }
    }

    // ── API pour ArenaListener ────────────────────────────────────────────────

    /**
     * Retourne le multiplicateur de dégâts actif pour ce joueur.
     * 999 = instant kill, 2.0 = double dégâts, 1.0 = normal.
     */
    public double getDamageMultiplier(Player player) {
        long now = System.currentTimeMillis();
        UUID uid = player.getUniqueId();
        if (instantKillExpiry.getOrDefault(uid, 0L) > now)  return 999.0;
        if (doubleDamageExpiry.getOrDefault(uid, 0L) > now) return 2.0;
        return 1.0;
    }

    // ── Nettoyage ─────────────────────────────────────────────────────────────

    public void clearPlayer(UUID uid) {
        instantKillExpiry.remove(uid);
        doubleDamageExpiry.remove(uid);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void announce(Player player, Arena arena, String label) {
        ChatUtil.send(player, m("powerup.announce-self", label));
        if (arena != null) {
            for (Player p : arena.getPlayers()) {
                if (p != player) {
                    ChatUtil.send(p, m("powerup.announce-others", player.getName(), label));
                }
            }
        }
    }

    private void broadcastArena(Arena arena, String msg) {
        for (Player p : arena.getPlayers()) {
            ChatUtil.send(p, msg);
        }
    }
}
