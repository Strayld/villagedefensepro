package fr.obsidianlabs.plugins.villagedefense.perk;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaState;
import fr.obsidianlabs.plugins.villagedefense.player.VDPlayer;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Gère les atouts achetés en cours de partie (données de session uniquement).
 * Réinitialise les attributs et supprime les compagnons en fin de partie.
 */
public class PerkManager {

    public static final int MAX_COMPANIONS = 3;
    public static final String COMPANION_META = "vd_companion";

    private final VillageDefensePlugin plugin;

    /** UUID joueur → { PerkType → stacks achetés } */
    private final Map<UUID, Map<PerkType, Integer>> playerPerks = new HashMap<>();

    /** UUID joueur → ID de l'arène propriétaire de la session d'atouts. */
    private final Map<UUID, String> playerSessionArenaIds = new HashMap<>();

    /** UUID joueur → liste d'UUID des entités compagnons */
    private final Map<UUID, List<UUID>> playerCompanions = new HashMap<>();

    /** UUID golem → tâche de reciblage */
    private final Map<UUID, BukkitTask> golemTasks = new HashMap<>();

    public PerkManager(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    // ── Lecture ──────────────────────────────────────────────────────────────

    public int getStacks(Player player, PerkType type) {
        return playerPerks
            .getOrDefault(player.getUniqueId(), Collections.emptyMap())
            .getOrDefault(type, 0);
    }

    /** Nombre de compagnons vivants pour ce joueur. */
    public int getCompanionCount(Player player) {
        List<UUID> list = playerCompanions.getOrDefault(player.getUniqueId(), Collections.emptyList());
        return (int) list.stream()
            .map(org.bukkit.Bukkit::getEntity)
            .filter(e -> e != null && e.isValid() && !e.isDead())
            .count();
    }

    // ── Prix effectif ────────────────────────────────────────────────────────

    /**
     * Retourne le coût réel de l'atout pour cette arène.
     * WAVE_SKIP coûte 200 × numéro de vague (min 200).
     */
    public long getEffectiveCost(PerkType type, Arena arena) {
        if (type == PerkType.WAVE_SKIP) {
            int wave = (arena != null) ? Math.max(1, arena.getCurrentWave()) : 1;
            return 200L * wave;
        }
        return type.getCost();
    }

    // ── Achat ────────────────────────────────────────────────────────────────

    /**
     * Tente d'acheter l'atout pour ce joueur.
     * Vérifie niveau, pièces, et plafonds de stacks.
     * Retourne true si l'achat a réussi.
     */
    public boolean purchase(Player player, Arena arena, PerkType type) {
        VDPlayer vdp = plugin.getPlayerManager().getPlayer(player);
        if (vdp == null) return false;

        if (vdp.getLevel() < type.getLevelRequired()) {
            plugin.getMessages().send(player, "perks.level-required", type.getLevelRequired());
            return false;
        }

        if (type.isCompanion() && getCompanionCount(player) >= MAX_COMPANIONS) {
            plugin.getMessages().send(player, "perks.max-companions");
            return false;
        }

        if (!type.isCompanion() && type != PerkType.BUY_VILLAGER && type.getMaxStacks() > 0
                && getStacks(player, type) >= type.getMaxStacks()) {
            plugin.getMessages().send(player, "perks.maxed");
            return false;
        }

        // VILLAGE_GUARD : tous les gardiens précédents doivent être morts
        if (type == PerkType.VILLAGE_GUARD && arena != null) {
            long aliveGuards = arena.getGuardUUIDs().stream()
                .map(org.bukkit.Bukkit::getEntity)
                .filter(e -> e != null && e.isValid() && !e.isDead())
                .count();
            if (aliveGuards > 0) {
                fr.obsidianlabs.plugins.villagedefense.util.ChatUtil.send(player,
                    plugin.getMessages().format("perk.guards-alive"));
                return false;
            }
        }

        // BUY_VILLAGER : plafond basé sur le nombre total de villageois (max 10)
        if (type == PerkType.BUY_VILLAGER && arena != null) {
            int maxVillagers = plugin.getConfig().getInt("village.villager-count", 10);
            if (arena.getVillagerCount() >= maxVillagers) {
                plugin.getMessages().send(player, "perks.maxed");
                return false;
            }
            int lastWave = arena.getLastVillagerPurchaseWave();
            int currentWave = arena.getCurrentWave();
            if (lastWave >= 0 && currentWave < lastWave + 5) {
                plugin.getMessages().send(player, "perks.cooldown", lastWave + 5);
                return false;
            }
        }

        long cost = getEffectiveCost(type, arena);
        if (!vdp.removeCoins(cost)) {
            plugin.getMessages().send(player, "perks.not-enough-coins");
            return false;
        }

        applyPerk(player, arena, type);
        plugin.getMessages().send(player, "perks.purchased", plugin.getMessages().format(type.getNameKey()), cost);
        return true;
    }

    /**
     * Réinitialise l'usage du saut de vague pour tous les joueurs de l'arène.
     * Appelé au début de chaque nouvelle vague.
     */
    public void resetWaveSkips(Arena arena) {
        for (Player p : arena.getPlayers()) {
            Map<PerkType, Integer> stacks = playerPerks.get(p.getUniqueId());
            if (stacks != null) stacks.remove(PerkType.WAVE_SKIP);
        }
    }

    // ── Application ──────────────────────────────────────────────────────────

    private void applyPerk(Player player, Arena arena, PerkType type) {
        if (arena != null) {
            playerSessionArenaIds.put(player.getUniqueId(), arena.getId());
        }

        Map<PerkType, Integer> stacks =
            playerPerks.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        stacks.merge(type, 1, Integer::sum);

        switch (type) {
            case MAX_HEALTH -> {
                var attr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                if (attr != null) {
                    double newMax = attr.getBaseValue() + 4.0;
                    attr.setBaseValue(newMax);
                    player.setHealth(Math.min(player.getHealth() + 4.0, newMax));
                }
            }
            case ATTACK_DAMAGE -> {
                var attr = player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
                if (attr != null) attr.setBaseValue(attr.getBaseValue() + 2.0);
            }
            case COMPANION_WOLF  -> spawnWolf(player, arena);
            case COMPANION_GOLEM -> spawnGolem(player, arena);
            case WAVE_SKIP       -> plugin.getGameManager().skipWave(arena, player);
            case BUY_VILLAGER    -> plugin.getGameManager().spawnExtraVillager(arena, player);
            case VILLAGE_GUARD   -> plugin.getGameManager().spawnVillageGuards(arena, player);
        }
    }

    private void spawnWolf(Player player, Arena arena) {
        Wolf wolf = (Wolf) player.getWorld().spawnEntity(player.getLocation(), EntityType.WOLF);
        wolf.setTamed(true);
        wolf.setOwner(player);
        wolf.setAngry(false);
        wolf.setMetadata(COMPANION_META, new FixedMetadataValue(plugin, player.getUniqueId().toString()));
        wolf.setCustomName(fr.obsidianlabs.plugins.villagedefense.util.ChatUtil.color(
            plugin.getMessages().format("perk.wolf-name", player.getName())
        ));
        wolf.setCustomNameVisible(true);

        var hp = wolf.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (hp != null) { hp.setBaseValue(40.0); wolf.setHealth(40.0); }

        var followRange = wolf.getAttribute(Attribute.GENERIC_FOLLOW_RANGE);
        if (followRange != null) followRange.setBaseValue(64.0);

        registerCompanion(player, wolf.getUniqueId());
    }

    private void spawnGolem(Player player, Arena arena) {
        IronGolem golem = (IronGolem) player.getWorld().spawnEntity(player.getLocation(), EntityType.IRON_GOLEM);
        golem.setPlayerCreated(true);
        golem.setMetadata(COMPANION_META, new FixedMetadataValue(plugin, player.getUniqueId().toString()));
        golem.setCustomName(fr.obsidianlabs.plugins.villagedefense.util.ChatUtil.color(
            plugin.getMessages().format("perk.golem-name", player.getName())
        ));
        golem.setCustomNameVisible(true);

        // Recibler toutes les 2 secondes sur le mob ennemi le plus proche
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (golem.isDead() || !golem.isValid()) return;
            LivingEntity nearest = null;
            double best = Double.MAX_VALUE;
            for (UUID uid : arena.getActiveMobUUIDs()) {
                Entity mob = org.bukkit.Bukkit.getEntity(uid);
                if (mob == null || !mob.isValid() || mob.isDead()) continue;
                double d = mob.getLocation().distanceSquared(golem.getLocation());
                if (d < best) { best = d; nearest = (LivingEntity) mob; }
            }
            if (nearest != null) golem.setTarget(nearest);
        }, 20L, 40L);

        golemTasks.put(golem.getUniqueId(), task);
        registerCompanion(player, golem.getUniqueId());
    }

    private void registerCompanion(Player player, UUID companionUuid) {
        playerCompanions.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>())
                        .add(companionUuid);
    }

    // ── Nettoyage ────────────────────────────────────────────────────────────

    /** Annule les atouts de session d'un joueur (fin de partie ou déconnexion). */
    public void clearSession(Player player) {
        clearSessionInternal(player.getUniqueId(), player, true);
    }

    /**
     * Efface une session orpheline avant une nouvelle entrée en arène.
     * Préserve uniquement le vrai cas de rejoin dans la même partie en cours.
     */
    public void prepareForArenaEntry(Player player, Arena arena, boolean preserveSameArenaSession) {
        UUID uid = player.getUniqueId();
        String sessionArenaId = playerSessionArenaIds.get(uid);
        if (sessionArenaId == null) {
            return;
        }

        boolean keepSession = preserveSameArenaSession
            && arena != null
            && sessionArenaId.equals(arena.getId())
            && (arena.getState() == ArenaState.RUNNING || arena.getState() == ArenaState.BETWEEN_WAVES)
            && plugin.getGameManager().hasValidRejoinToken(player)
            && sessionArenaId.equals(plugin.getGameManager().getRejoinArenaId(player));

        if (keepSession) {
            return;
        }

        clearDetachedSession(uid);
        plugin.getGameManager().clearRejoinToken(player);
    }

    /** Efface une session déjà détachée du joueur (leave, déco, changement d'arène). */
    private void clearDetachedSession(UUID uid) {
        clearSessionInternal(uid, null, false);
    }

    /**
     * Efface la session d'atouts, en rétablissant les attributs uniquement
     * si le joueur est encore actif dans la partie au moment du nettoyage.
     */
    private void clearSessionInternal(UUID uid, Player player, boolean revertAttributes) {
        Map<PerkType, Integer> stacks = playerPerks.remove(uid);
        playerSessionArenaIds.remove(uid);

        if (revertAttributes && player != null && stacks != null) {
            int healthStacks = stacks.getOrDefault(PerkType.MAX_HEALTH, 0);
            int dmgStacks    = stacks.getOrDefault(PerkType.ATTACK_DAMAGE, 0);
            if (healthStacks > 0) {
                var attr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                if (attr != null) attr.setBaseValue(Math.max(2.0, attr.getBaseValue() - healthStacks * 4.0));
            }
            if (dmgStacks > 0) {
                var attr = player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
                if (attr != null) attr.setBaseValue(Math.max(1.0, attr.getBaseValue() - dmgStacks * 2.0));
            }
        }

        plugin.getPowerUpManager().clearPlayer(uid);

        List<UUID> companions = playerCompanions.remove(uid);
        if (companions != null) {
            for (UUID cUid : companions) {
                BukkitTask t = golemTasks.remove(cUid);
                if (t != null) t.cancel();
                Entity e = org.bukkit.Bukkit.getEntity(cUid);
                if (e != null && e.isValid()) e.remove();
            }
        }
    }

    /** Nettoie la session de tous les joueurs d'une arène. */
    public void clearArenaSession(Arena arena) {
        Set<UUID> sessionPlayers = new HashSet<>();
        for (Player player : arena.getPlayers()) {
            sessionPlayers.add(player.getUniqueId());
        }
        for (Map.Entry<UUID, String> entry : new HashMap<>(playerSessionArenaIds).entrySet()) {
            if (arena.getId().equals(entry.getValue())) {
                sessionPlayers.add(entry.getKey());
            }
        }

        for (UUID uid : sessionPlayers) {
            Player player = org.bukkit.Bukkit.getPlayer(uid);
            if (player != null && arena.hasPlayer(player)) {
                clearSession(player);
            } else {
                clearDetachedSession(uid);
            }
        }
    }
}
