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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Attaque de zone glaciale : ralentit et endommage tous les mobs dans la portée.
 * BLIZZARD     : ajoute Cécité aux cibles gelées.
 * FROST_NOVA   : repousse les ennemis en plus de les ralentir.
 */
public class IceTower extends Tower {

    public IceTower(VillageDefensePlugin plugin, TowerSlot slot) {
        super(plugin, TowerType.ICE, slot);
    }

    @Override
    protected void attack(Arena arena, LivingEntity primaryTarget) {
        Location center = getFireLocation();
        double rangeSq  = getRange() * getRange();
        double damage   = getDamage();
        int freezeDuration = getStatInt("slowness-duration");
        if (freezeDuration <= 0) freezeDuration = 40;

        int hitCount = 0;

        for (UUID uid : new ArrayList<>(arena.getActiveMobUUIDs())) {
            Entity rawEntity = org.bukkit.Bukkit.getEntity(uid);
            if (!(rawEntity instanceof LivingEntity mob)) continue;
            if (!mob.isValid() || mob.isDead()) continue;
            if (mob.getLocation().distanceSquared(center) > rangeSq) continue;

            mob.damage(damage);

            // Slowness III (amplifier 2)
            mob.addPotionEffect(new PotionEffect(
                VersionCompat.SLOWNESS, freezeDuration, 2, false, false));

            switch (getSpecialization()) {
                case BLIZZARD ->
                    mob.addPotionEffect(new PotionEffect(
                        PotionEffectType.BLINDNESS, freezeDuration / 2, 0, false, false));

                case FROST_NOVA -> {
                    // Repousse le mob depuis le centre de la tour
                    Vector knockback = mob.getLocation().subtract(center).toVector();
                    if (knockback.lengthSquared() > 0.01) {
                        knockback.normalize().multiply(2.0).setY(0.3);
                        mob.setVelocity(knockback);
                    }
                }
                default -> { /* aucune spécialisation */ }
            }

            hitCount++;
        }

        if (hitCount > 0) {
            center.getWorld().spawnParticle(
                Particle.SNOWFLAKE, center, 40, getRange() / 2.5, 0.5, getRange() / 2.5, 0.05);
            center.getWorld().playSound(center, Sound.BLOCK_GLASS_BREAK, 0.6f, 1.8f);
        }
    }
}
