package fr.obsidianlabs.plugins.villagedefense.tower.impl;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.tower.Tower;
import fr.obsidianlabs.plugins.villagedefense.tower.TowerSlot;
import fr.obsidianlabs.plugins.villagedefense.tower.TowerSpecialization;
import fr.obsidianlabs.plugins.villagedefense.tower.TowerType;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

/**
 * Lance un TNT vers un cluster de mobs.
 * Le TNT n'endommage PAS les blocs (géré dans l'ArenaListener via EntityExplodeEvent).
 */
public class TrebuchetTower extends Tower {

    public TrebuchetTower(VillageDefensePlugin plugin, TowerSlot slot) {
        super(plugin, TowerType.TREBUCHET, slot);
    }

    @Override
    protected LivingEntity findTarget(Arena arena) {
        // Le trébuchet vise le cluster avec le plus de mobs
        return findClusterCenter(arena);
    }

    private LivingEntity findClusterCenter(Arena arena) {
        Location center = slot.getLocation();
        double rangeSq = getRange() * getRange();

        LivingEntity bestTarget = null;
        int bestNeighbors = 0;

        for (java.util.UUID uid : arena.getActiveMobUUIDs()) {
            var entity = org.bukkit.Bukkit.getEntity(uid);
            if (!(entity instanceof LivingEntity mob)) continue;
            if (!mob.isValid() || mob.isDead()) continue;
            if (mob.getLocation().distanceSquared(center) > rangeSq) continue;

            // Compte les voisins dans un rayon de 5 blocs
            int neighbors = 0;
            for (java.util.UUID uid2 : arena.getActiveMobUUIDs()) {
                var e2 = org.bukkit.Bukkit.getEntity(uid2);
                if (e2 == null || !e2.isValid()) continue;
                if (e2.getLocation().distanceSquared(mob.getLocation()) <= 25) neighbors++;
            }

            if (neighbors > bestNeighbors) {
                bestNeighbors = neighbors;
                bestTarget = mob;
            }
        }
        return bestTarget;
    }

    @Override
    protected void attack(Arena arena, LivingEntity target) {
        Location from = getFireLocation();
        Location targetLoc = target.getLocation().add(0, 0.5, 0);

        Vector velocity = calculateProjectileVelocity(from, targetLoc);
        if (velocity == null) return;

        TNTPrimed tnt = from.getWorld().spawn(from, TNTPrimed.class);
        tnt.setVelocity(velocity);
        tnt.setFuseTicks(100); // 5 s max — l'explosion est gérée dans ArenaListener

        tnt.setMetadata("vd_trebuchet",    new FixedMetadataValue(plugin, arena.getId()));
        tnt.setMetadata("vd_tower_damage", new FixedMetadataValue(plugin, getDamage()));

        if (getSpecialization() == TowerSpecialization.INCENDIARY_TNT) {
            tnt.setMetadata("vd_incendiary_tnt", new FixedMetadataValue(plugin, true));
        } else if (getSpecialization() == TowerSpecialization.CLUSTER) {
            tnt.setMetadata("vd_cluster_tnt", new FixedMetadataValue(plugin, true));
        }

        from.getWorld().playSound(from, Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.5f);
    }

    /**
     * Calcule la vélocité (blocs/tick) pour atteindre {@code to} en arche parabolique.
     * speed = 0.5 blocs/tick → arc haut de style trébuchet.
     * La gravité Minecraft est ~0.04 blocs/tick².
     */
    private Vector calculateProjectileVelocity(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double distXZ = Math.sqrt(dx * dx + dz * dz);

        if (distXZ < 0.5) return null;

        double speed = 0.5;              // blocs/tick (vitesse horizontale)
        double g     = 0.04;            // gravité Minecraft, blocs/tick²
        double t     = distXZ / speed;  // ticks de vol

        double vx = dx / t;
        double vy = (dy / t) + (g * t / 2.0);
        double vz = dz / t;

        return new Vector(vx, vy, vz);  // déjà en blocs/tick → pas de division supplémentaire
    }
}
