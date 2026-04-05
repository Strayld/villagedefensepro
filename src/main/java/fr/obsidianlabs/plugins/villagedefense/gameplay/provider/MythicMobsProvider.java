package fr.obsidianlabs.plugins.villagedefense.gameplay.provider;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.gameplay.model.MobEntry;
import fr.obsidianlabs.plugins.villagedefense.gameplay.model.MobSource;
import fr.obsidianlabs.plugins.villagedefense.wave.WaveManager;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Fournit les mobs définis dans MythicMobs au système de vagues avancé.
 * Enregistré uniquement si MythicMobs est présent sur le serveur.
 */
public class MythicMobsProvider implements MobProvider {

    private final VillageDefensePlugin plugin;

    public MythicMobsProvider(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public MobSource getSource() {
        return MobSource.MYTHICMOBS;
    }

    @Override
    public List<MobOption> getAvailableMobs() {
        List<MobOption> options = new ArrayList<>();
        for (MythicMob mob : MythicBukkit.inst().getMobManager().getMobTypes()) {
            String id = mob.getInternalName();
            String display = stripColor(mob.getDisplayName().toString());
            if (display.isBlank()) display = id;
            options.add(new MobOption(id, MobSource.MYTHICMOBS, display, "MythicMobs", Material.SPAWNER));
        }
        return options;
    }

    @Override
    public boolean canSpawn(MobEntry entry) {
        return MythicBukkit.inst().getMobManager().getMythicMob(entry.getId()).isPresent();
    }

    @Override
    public SpawnResult spawn(Location location, MobEntry entry, WaveSpawnContext context) {
        Optional<MythicMob> mobType = MythicBukkit.inst().getMobManager().getMythicMob(entry.getId());
        if (mobType.isEmpty()) {
            return SpawnResult.fail("Mob MythicMobs introuvable : " + entry.getId());
        }

        var activeMob = mobType.get().spawn(BukkitAdapter.adapt(location), 1.0);
        if (activeMob == null) {
            return SpawnResult.fail("Échec du spawn MythicMobs pour : " + entry.getId());
        }

        Entity entity = BukkitAdapter.adapt(activeMob.getEntity());
        if (!(entity instanceof LivingEntity mob)) {
            // Entité non-vivante (ex : vex invoquée) — on la tag quand même
            entity.setMetadata(WaveManager.MOB_ARENA_META, new FixedMetadataValue(plugin, context.getArenaId()));
            return SpawnResult.ok(entity.getUniqueId());
        }

        // Tag arène
        mob.setMetadata(WaveManager.MOB_ARENA_META, new FixedMetadataValue(plugin, context.getArenaId()));
        mob.getPersistentDataContainer().set(
            plugin.getKey(WaveManager.MOB_ARENA_META), PersistentDataType.STRING, context.getArenaId()
        );

        // Multiplicateurs HP/dégâts/vitesse appliqués par-dessus les stats MythicMobs
        if (context.getHealthMultiplier() != 1.0) {
            var maxHp = mob.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (maxHp != null) {
                double newHp = Math.min(maxHp.getBaseValue() * context.getHealthMultiplier(), 2048.0);
                maxHp.setBaseValue(newHp);
                mob.setHealth(newHp);
            }
        }

        if (context.getDamageMultiplier() != 1.0) {
            var dmg = mob.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
            if (dmg != null) {
                dmg.setBaseValue(dmg.getBaseValue() * context.getDamageMultiplier());
            }
        }

        if (context.getSpeedMultiplier() != 1.0) {
            var speed = mob.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
            if (speed != null) {
                speed.setBaseValue(Math.min(speed.getBaseValue() * context.getSpeedMultiplier(), 0.5));
            }
        }

        return SpawnResult.ok(entity.getUniqueId());
    }

    /** Supprime les codes couleur legacy (§x) et MiniMessage (<tag>). */
    private String stripColor(String input) {
        if (input == null) return "";
        return input.replaceAll("§[0-9a-fk-orA-FK-OR]", "")
                    .replaceAll("<[^>]+>", "")
                    .trim();
    }
}
