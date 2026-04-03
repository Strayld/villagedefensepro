package fr.obsidianlabs.plugins.villagedefense.tower.impl;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.tower.Tower;
import fr.obsidianlabs.plugins.villagedefense.tower.TowerSlot;
import fr.obsidianlabs.plugins.villagedefense.tower.TowerSpecialization;
import fr.obsidianlabs.plugins.villagedefense.tower.TowerType;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

public class ArcherTower extends Tower {

    public ArcherTower(VillageDefensePlugin plugin, TowerSlot slot) {
        super(plugin, TowerType.ARCHER, slot);
    }

    @Override
    protected void attack(Arena arena, LivingEntity target) {
        if (getSpecialization() == TowerSpecialization.MULTI_SHOT) {
            fireMultiShot(arena, target);
        } else {
            fireSingleArrow(arena, target,
                getSpecialization() == TowerSpecialization.INCENDIARY_ARROW);
        }
    }

    private void fireSingleArrow(Arena arena, LivingEntity target, boolean incendiary) {
        Location from = getFireLocation();
        Vector direction = target.getLocation().add(0, 1, 0)
            .toVector().subtract(from.toVector()).normalize();

        Arrow arrow = from.getWorld().spawnArrow(from, direction, 2.0f, 5.0f);
        arrow.setDamage(getDamage());
        arrow.setShooter(null);
        arrow.setPierceLevel(level >= 3 ? 1 : 0);
        arrow.setMetadata("vd_tower_arrow",
            new org.bukkit.metadata.FixedMetadataValue(plugin, arena.getId()));
        if (incendiary) {
            arrow.setMetadata("vd_fire_arrow",
                new org.bukkit.metadata.FixedMetadataValue(plugin, true));
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (arrow.isValid()) arrow.remove();
        }, 400L);

        from.getWorld().playSound(from, Sound.ENTITY_ARROW_SHOOT, 0.5f, 1.2f);
    }

    /** Tire 3 flèches en éventail (spécialisation MULTI_SHOT). */
    private void fireMultiShot(Arena arena, LivingEntity target) {
        Location from = getFireLocation();
        Vector base = target.getLocation().add(0, 1, 0)
            .toVector().subtract(from.toVector()).normalize();

        double[] yawOffsets = {-12, 0, 12}; // degrés
        for (double yawDeg : yawOffsets) {
            double rad = Math.toRadians(yawDeg);
            double cos = Math.cos(rad), sin = Math.sin(rad);
            Vector spread = new Vector(
                base.getX() * cos - base.getZ() * sin,
                base.getY(),
                base.getX() * sin + base.getZ() * cos
            ).normalize();

            Arrow arrow = from.getWorld().spawnArrow(from, spread, 1.8f, 6.0f);
            arrow.setDamage(getDamage() * 0.7); // légèrement réduit par flèche
            arrow.setShooter(null);
            arrow.setMetadata("vd_tower_arrow",
                new org.bukkit.metadata.FixedMetadataValue(plugin, arena.getId()));

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (arrow.isValid()) arrow.remove();
            }, 400L);
        }
        from.getWorld().playSound(from, Sound.ENTITY_ARROW_SHOOT, 0.6f, 1.4f);
    }
}
