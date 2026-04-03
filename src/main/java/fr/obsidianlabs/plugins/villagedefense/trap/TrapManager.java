package fr.obsidianlabs.plugins.villagedefense.trap;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaState;
import fr.obsidianlabs.plugins.villagedefense.compat.FloatingText;
import fr.obsidianlabs.plugins.villagedefense.compat.VersionCompat;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Vérifie toutes les 10 ticks si un mob entre en contact avec un piège actif
 * et déclenche l'effet correspondant.
 */
public class TrapManager {


    private final VillageDefensePlugin plugin;
    private BukkitTask tickTask;

    // slot -> hologramme flottant au-dessus de l'emplacement (clé par instance = unique en multi-arena)
    private final Map<TrapSlot, FloatingText> trapDisplays = new HashMap<>();

    public TrapManager(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        tickTask = plugin.getServer().getScheduler()
            .runTaskTimer(plugin, this::tickAll, 5L, 10L);
    }

    public void shutdown() {
        if (tickTask != null) tickTask.cancel();
    }

    // ── Tick principal ────────────────────────────────────────────────────────

    private void tickAll() {
        for (Arena arena : plugin.getArenaManager().getAllArenas()) {
            if (arena.getState() != ArenaState.RUNNING) continue;
            for (TrapSlot trap : arena.getTrapSlots()) {
                spawnOrUpdateDisplay(trap);
                if (trap.isEmpty() || trap.isOnCooldown()) continue;
                checkAndTrigger(arena, trap);
            }
        }
    }

    private void checkAndTrigger(Arena arena, TrapSlot trapSlot) {
        TrapType type = trapSlot.getPlacedTrap();
        if (type == null) return;
        Location center = trapSlot.getLocation().clone().add(0.5, 0.5, 0.5);
        double triggerRangeSq = type.getTriggerRadius() * type.getTriggerRadius();

        for (UUID uid : arena.getActiveMobUUIDs()) {
            Entity e = org.bukkit.Bukkit.getEntity(uid);
            if (!(e instanceof LivingEntity mob)) continue;
            if (!mob.isValid() || mob.isDead()) continue;
            if (mob.getLocation().distanceSquared(center) <= triggerRangeSq) {
                triggerTrap(arena, trapSlot, center);
                return;
            }
        }
    }

    // ── Effets ────────────────────────────────────────────────────────────────

    private void triggerTrap(Arena arena, TrapSlot trapSlot, Location center) {
        TrapType type = trapSlot.getPlacedTrap();
        if (type == null) return;

        double effectRangeSq = type.getEffectRadius() * type.getEffectRadius();
        List<LivingEntity> targets = new ArrayList<>();
        for (UUID uid : new ArrayList<>(arena.getActiveMobUUIDs())) {
            Entity e = org.bukkit.Bukkit.getEntity(uid);
            if (!(e instanceof LivingEntity mob)) continue;
            if (!mob.isValid() || mob.isDead()) continue;
            if (mob.getLocation().distanceSquared(center) <= effectRangeSq) {
                targets.add(mob);
            }
        }

        switch (type) {

            case SLOW_TRAP -> {
                for (LivingEntity mob : targets) {
                    mob.addPotionEffect(new PotionEffect(
                        VersionCompat.SLOWNESS, 8 * 20, 3, false, false));
                }
                center.getWorld().spawnParticle(
                    Particle.SNOWFLAKE, center, 30, 1.0, 0.5, 1.0, 0.05);
                center.getWorld().playSound(
                    center, Sound.BLOCK_POWDER_SNOW_STEP, 1f, 0.5f);
                trapSlot.startCooldown(effectiveCooldown(trapSlot, type));
            }

            case DAMAGE_MINE -> {
                for (LivingEntity mob : targets) {
                    mob.damage(40.0);
                }
                center.getWorld().spawnParticle(
                    VersionCompat.EXPLOSION, center, 3, 0.5, 0.3, 0.5, 0.1);
                center.getWorld().playSound(
                    center, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.1f);
                trapSlot.clearTrap(); // usage unique
            }

            case NET_TRAP -> {
                for (LivingEntity mob : targets) {
                    mob.addPotionEffect(new PotionEffect(
                        VersionCompat.SLOWNESS, 3 * 20, 10, false, false));
                }
                center.getWorld().spawnParticle(
                    Particle.CRIT, center, 20, 0.8, 0.5, 0.8, 0.2);
                center.getWorld().playSound(
                    center, Sound.BLOCK_WOOL_HIT, 1f, 0.8f);
                trapSlot.startCooldown(effectiveCooldown(trapSlot, type));
            }
        }
    }

    private long effectiveCooldown(TrapSlot trapSlot, TrapType type) {
        if (plugin.getKitAbilityManager() == null) {
            return type.getCooldownMs();
        }
        return plugin.getKitAbilityManager().adjustTrapCooldown(trapSlot, type.getCooldownMs());
    }

    // ── Utilitaires ───────────────────────────────────────────────────────────

    /** Vide tous les pièges actifs d'une arène (appelé en fin de partie). */
    public void clearTraps(Arena arena) {
        removeAllTrapDisplays(arena);
        for (TrapSlot slot : arena.getTrapSlots()) {
            slot.clearTrap();
        }
    }

    // ── Displays flottants ────────────────────────────────────────────────────

    /** Initialise les textes flottants pour tous les emplacements d'une arène. */
    public void initTrapDisplays(Arena arena) {
        for (TrapSlot slot : arena.getTrapSlots()) {
            spawnOrUpdateDisplay(slot);
        }
    }

    /** Rafraîchit le texte flottant d'un emplacement (ex. après achat/retrait). */
    public void refreshTrapDisplay(TrapSlot slot) {
        spawnOrUpdateDisplay(slot);
    }

    public void removeAllTrapDisplays(Arena arena) {
        for (TrapSlot slot : arena.getTrapSlots()) {
            FloatingText display = trapDisplays.remove(slot);
            if (display != null) display.remove();
        }
    }

    private void spawnOrUpdateDisplay(TrapSlot slot) {
        String text = buildTrapText(slot);
        Location pos = slot.getLocation().clone().add(0.5, 2.5, 0.5);
        if (pos.getWorld() == null) return;
        FloatingText display = trapDisplays.computeIfAbsent(slot, key -> new FloatingText());
        display.update(pos, text);
    }

    private String buildTrapText(TrapSlot slot) {
        var msg = plugin.getMessages();
        if (slot.isEmpty()) {
            return msg.format("trap.display.empty-slot");
        }
        TrapType type = slot.getPlacedTrap();
        String name = "&e" + msg.format(type.getNameKey());
        String status = slot.isOnCooldown()
            ? "&c" + msg.format("trap.display.cooldown", slot.getRemainingCooldownSeconds())
            : "&a" + msg.format("trap.display.ready");
        return name + "\n" + status;
    }

    /** Trouve l'emplacement de piège le plus proche d'un point dans un rayon donné. */
    public TrapSlot getNearestTrapSlot(Arena arena, Location loc, double maxDistSq) {
        TrapSlot nearest = null;
        double   best    = maxDistSq;
        for (TrapSlot slot : arena.getTrapSlots()) {
            double d = slot.distanceSquaredFrom(loc);
            if (d < best) { best = d; nearest = slot; }
        }
        return nearest;
    }
}
