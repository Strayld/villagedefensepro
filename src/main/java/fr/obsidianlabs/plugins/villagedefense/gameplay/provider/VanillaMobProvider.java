package fr.obsidianlabs.plugins.villagedefense.gameplay.provider;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.gameplay.model.MobEntry;
import fr.obsidianlabs.plugins.villagedefense.gameplay.model.MobSource;
import fr.obsidianlabs.plugins.villagedefense.wave.WaveManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class VanillaMobProvider implements MobProvider {

    private final VillageDefensePlugin plugin;

    // Mobs vanilla autorisés (liste représentative, extensible)
    private static final EntityType[] SUPPORTED_TYPES = {
        EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CAVE_SPIDER,
        EntityType.CREEPER, EntityType.WITCH, EntityType.HUSK, EntityType.STRAY,
        EntityType.DROWNED, EntityType.PILLAGER, EntityType.VINDICATOR, EntityType.RAVAGER,
        EntityType.EVOKER, EntityType.VEX, EntityType.BLAZE, EntityType.WITHER_SKELETON,
        EntityType.WOLF, EntityType.SLIME, EntityType.MAGMA_CUBE, EntityType.PHANTOM
    };

    public VanillaMobProvider(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public MobSource getSource() {
        return MobSource.VANILLA;
    }

    @Override
    public List<MobOption> getAvailableMobs() {
        List<MobOption> options = new ArrayList<>();
        for (EntityType type : SUPPORTED_TYPES) {
            String name = formatName(type.name());
            options.add(new MobOption(type.name(), MobSource.VANILLA, name, "Mob vanilla Minecraft", iconFor(type)));
        }
        return options;
    }

    @Override
    public boolean canSpawn(MobEntry entry) {
        try {
            EntityType.valueOf(entry.getId());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public SpawnResult spawn(Location location, MobEntry entry, WaveSpawnContext context) {
        EntityType type;
        try {
            type = EntityType.valueOf(entry.getId());
        } catch (IllegalArgumentException e) {
            return SpawnResult.fail("EntityType inconnu : " + entry.getId());
        }

        World world = location.getWorld();
        if (world == null) return SpawnResult.fail("World null à la location de spawn.");

        Entity entity = world.spawnEntity(location, type);
        if (!(entity instanceof LivingEntity mob)) {
            entity.remove();
            return SpawnResult.fail("L'entité spawnée n'est pas un LivingEntity.");
        }

        // Tag arène
        mob.setMetadata(WaveManager.MOB_ARENA_META, new FixedMetadataValue(plugin, context.getArenaId()));
        mob.getPersistentDataContainer().set(
            plugin.getKey(WaveManager.MOB_ARENA_META), PersistentDataType.STRING, context.getArenaId()
        );

        // HP
        var maxHpAttr = mob.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHpAttr != null) {
            double newHp = Math.min(maxHpAttr.getBaseValue() * context.getHealthMultiplier(), 2048.0);
            maxHpAttr.setBaseValue(newHp);
            mob.setHealth(newHp);
        }

        // Dégâts
        var dmgAttr = mob.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (dmgAttr != null && context.getDamageMultiplier() != 1.0) {
            dmgAttr.setBaseValue(dmgAttr.getBaseValue() * context.getDamageMultiplier());
        }

        // Vitesse
        var speedAttr = mob.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null && context.getSpeedMultiplier() != 1.0) {
            speedAttr.setBaseValue(Math.min(speedAttr.getBaseValue() * context.getSpeedMultiplier(), 0.5));
        }

        // Portée de détection maximale
        var followRange = mob.getAttribute(Attribute.GENERIC_FOLLOW_RANGE);
        if (followRange != null) followRange.setBaseValue(128.0);

        return SpawnResult.ok(entity.getUniqueId());
    }

    private String formatName(String name) {
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(part.charAt(0)).append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    private Material iconFor(EntityType type) {
        return switch (type) {
            case ZOMBIE          -> Material.ROTTEN_FLESH;
            case SKELETON        -> Material.BONE;
            case SPIDER, CAVE_SPIDER -> Material.SPIDER_EYE;
            case CREEPER         -> Material.GUNPOWDER;
            case WITCH           -> Material.GLASS_BOTTLE;
            case RAVAGER         -> Material.SADDLE;
            case BLAZE           -> Material.BLAZE_ROD;
            case WITHER_SKELETON -> Material.WITHER_SKELETON_SKULL;
            case WOLF            -> Material.BONE;
            case SLIME           -> Material.SLIME_BALL;
            default              -> Material.ZOMBIE_SPAWN_EGG;
        };
    }
}
