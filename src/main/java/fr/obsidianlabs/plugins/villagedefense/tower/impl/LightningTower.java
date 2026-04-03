package fr.obsidianlabs.plugins.villagedefense.tower.impl;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.compat.VersionCompat;
import fr.obsidianlabs.plugins.villagedefense.tower.Tower;
import fr.obsidianlabs.plugins.villagedefense.tower.TowerSlot;
import fr.obsidianlabs.plugins.villagedefense.tower.TowerType;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Frappe une cible principale et enchaîne sur les proches.
 * OVERLOAD : enchaîne sur 3 cibles supplémentaires (au lieu de 2).
 * STUN     : 30 % de chance de paralyser chaque cible touchée (1.5s).
 */
public class LightningTower extends Tower {

    public LightningTower(VillageDefensePlugin plugin, TowerSlot slot) {
        super(plugin, TowerType.LIGHTNING, slot);
    }

    @Override
    protected void attack(Arena arena, LivingEntity primaryTarget) {
        double damage = getDamage();

        // Nombre de cibles secondaires selon la spécialisation
        int chainCount = switch (getSpecialization()) {
            case OVERLOAD -> 3;
            default       -> 2;
        };

        // Frappe principale
        strikeTarget(primaryTarget, damage);

        // Enchaînement sur les cibles proches
        List<LivingEntity> struck = new ArrayList<>();
        struck.add(primaryTarget);

        LivingEntity chainFrom = primaryTarget;
        for (int i = 0; i < chainCount; i++) {
            LivingEntity next = findNearest(arena, chainFrom.getLocation(), 100.0, struck);
            if (next == null) break;
            strikeTarget(next, damage * 0.5);
            struck.add(next);
            chainFrom = next;
        }

        getLocation().getWorld().playSound(
            getFireLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.5f);
    }

    private void strikeTarget(LivingEntity mob, double damage) {
        mob.damage(damage);
        mob.getWorld().strikeLightningEffect(mob.getLocation());

        // STUN : 30 % de paralysie
        if (getSpecialization() == fr.obsidianlabs.plugins.villagedefense.tower.TowerSpecialization.STUN
                && Math.random() < 0.30) {
            mob.addPotionEffect(new PotionEffect(VersionCompat.SLOWNESS, 30, 10, false, false));
        }
    }

    private LivingEntity findNearest(Arena arena, org.bukkit.Location center,
                                     double rangeSq, List<LivingEntity> exclude) {
        LivingEntity nearest = null;
        double best = rangeSq;
        for (UUID uid : arena.getActiveMobUUIDs()) {
            Entity e = org.bukkit.Bukkit.getEntity(uid);
            if (!(e instanceof LivingEntity mob)) continue;
            if (!mob.isValid() || mob.isDead()) continue;
            if (exclude.contains(mob)) continue;
            double d = mob.getLocation().distanceSquared(center);
            if (d < best) { best = d; nearest = mob; }
        }
        return nearest;
    }
}
