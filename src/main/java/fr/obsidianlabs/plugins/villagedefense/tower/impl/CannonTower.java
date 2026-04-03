package fr.obsidianlabs.plugins.villagedefense.tower.impl;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.compat.VersionCompat;
import fr.obsidianlabs.plugins.villagedefense.tower.Tower;
import fr.obsidianlabs.plugins.villagedefense.tower.TowerSlot;
import fr.obsidianlabs.plugins.villagedefense.tower.TowerType;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Canon à longue portée : cadence lente, dégâts élevés, explosion de zone à l'impact.
 * EXPLOSIVE_SHOT : rayon d'explosion élargi (×1.8).
 * GRAPESHOT      : tire un cône de 5 projectiles à dégâts réduits.
 */
public class CannonTower extends Tower {

    public CannonTower(VillageDefensePlugin plugin, TowerSlot slot) {
        super(plugin, TowerType.CANNON, slot);
    }

    @Override
    protected void attack(Arena arena, LivingEntity target) {
        Location from = getFireLocation();
        double damage = getDamage();

        if (getSpecialization() == fr.obsidianlabs.plugins.villagedefense.tower.TowerSpecialization.GRAPESHOT) {
            fireGrapeshot(arena, from, target.getLocation().clone().add(0, 0.5, 0), damage);
            return;
        }

        double blastRadius = switch (getSpecialization()) {
            case EXPLOSIVE_SHOT -> 5.0;
            default             -> 3.0;
        };

        // Effet visuel de départ
        from.getWorld().spawnParticle(VersionCompat.LARGE_SMOKE, from, 8, 0.15, 0.15, 0.15, 0.05);
        from.getWorld().playSound(from, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 0.6f);

        // Calcule le temps de vol (~1 bloc/tick horizontal)
        Location targetLoc = target.getLocation().clone().add(0, 0.5, 0);
        double distXZ = Math.sqrt(Math.pow(targetLoc.getX() - from.getX(), 2)
                                + Math.pow(targetLoc.getZ() - from.getZ(), 2));
        long flightTicks = Math.max(10L, Math.min((long) distXZ, 60L));

        String arenaId = arena.getId();
        double radiusSq = blastRadius * blastRadius;

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Arena a = plugin.getArenaManager().getArena(arenaId);
            if (a == null) return;

            targetLoc.getWorld().spawnParticle(VersionCompat.EXPLOSION_EMITTER, targetLoc, 2, 0.3, 0.3, 0.3);
            targetLoc.getWorld().playSound(targetLoc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.85f);

            for (UUID uid : new ArrayList<>(a.getActiveMobUUIDs())) {
                Entity e = org.bukkit.Bukkit.getEntity(uid);
                if (!(e instanceof LivingEntity mob)) continue;
                if (!mob.isValid() || mob.isDead()) continue;
                if (mob.getLocation().distanceSquared(targetLoc) <= radiusSq) {
                    mob.damage(damage);
                }
            }
        }, flightTicks);
    }

    /** Cône de 5 projectiles simulés : dégâts sur les mobs dans un arc de 45° devant la tour. */
    private void fireGrapeshot(Arena arena, Location from, Location targetLoc, double damage) {
        Vector mainDir = targetLoc.toVector().subtract(from.toVector());
        if (mainDir.lengthSquared() < 0.01) return;
        mainDir.setY(0).normalize();

        double singleDmg = damage * 0.55;
        double rangeSq   = getRange() * getRange();
        int    maxHits   = 5;
        int    hits      = 0;

        for (UUID uid : new ArrayList<>(arena.getActiveMobUUIDs())) {
            if (hits >= maxHits) break;
            Entity e = org.bukkit.Bukkit.getEntity(uid);
            if (!(e instanceof LivingEntity mob)) continue;
            if (!mob.isValid() || mob.isDead()) continue;
            if (mob.getLocation().distanceSquared(from) > rangeSq) continue;

            Vector toMob = mob.getLocation().subtract(from).toVector().setY(0);
            if (toMob.lengthSquared() < 0.01) {
                mob.damage(singleDmg);
                hits++;
                continue;
            }
            toMob.normalize();
            // Environ ±22.5° de chaque côté du tir central (cos 45° ≈ 0.707)
            if (mainDir.dot(toMob) >= 0.707) {
                mob.damage(singleDmg);
                hits++;
            }
        }

        from.getWorld().spawnParticle(Particle.CRIT, from, 25, 0.6, 0.4, 0.6, 0.25);
        from.getWorld().playSound(from, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);
    }
}
