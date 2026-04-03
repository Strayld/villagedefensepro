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

import java.util.ArrayList;
import java.util.UUID;

/**
 * Attaque de zone : ralentit et endommage tous les mobs dans la portée.
 */
public class MageTower extends Tower {

    public MageTower(VillageDefensePlugin plugin, TowerSlot slot) {
        super(plugin, TowerType.MAGE, slot);
    }

    @Override
    protected void attack(Arena arena, LivingEntity primaryTarget) {
        Location center = getFireLocation();
        double rangeSq = getRange() * getRange();
        double damage = getDamage();
        int slownessDuration = plugin.getConfig().getInt(
            "towers.mage.levels." + level + ".slowness-duration", 60);

        int hitCount = 0;

        for (UUID uid : new ArrayList<>(arena.getActiveMobUUIDs())) {
            Entity rawEntity = org.bukkit.Bukkit.getEntity(uid);
            if (!(rawEntity instanceof LivingEntity mob)) continue;
            if (!mob.isValid() || mob.isDead()) continue;
            if (mob.getLocation().distanceSquared(center) > rangeSq) continue;

            // Dégâts magiques
            mob.damage(damage);

            // Ralentissement (base)
            mob.addPotionEffect(new PotionEffect(
                VersionCompat.SLOWNESS,
                slownessDuration,
                level - 1,
                false, false
            ));

            // ── Effets de spécialisation ──────────────────────────────────────
            switch (getSpecialization()) {
                case FIRE ->
                    mob.setFireTicks(4 * 20); // 4 secondes de feu

                case POISON ->
                    mob.addPotionEffect(new PotionEffect(
                        PotionEffectType.POISON, slownessDuration, 1, false, false));

                default -> { /* pas de spec ou spec non-mage */ }
            }

            hitCount++;
        }

        // Effets visuels
        if (hitCount > 0) {
            center.getWorld().spawnParticle(VersionCompat.ENTITY_EFFECT, center, 30, getRange() / 2, 1, getRange() / 2, 0.1);
            center.getWorld().playSound(center, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.5f, 1.2f + (level * 0.1f));
        }
    }
}
