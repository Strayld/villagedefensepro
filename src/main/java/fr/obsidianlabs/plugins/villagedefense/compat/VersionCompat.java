package fr.obsidianlabs.plugins.villagedefense.compat;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rail;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class VersionCompat {

    private static final Method BLOCK_ROTATE_METHOD = resolveBlockRotateMethod();

    public static final Material TRACKING_COMPASS = material("RECOVERY_COMPASS", "COMPASS");

    public static final Enchantment SHARPNESS = enchantment("sharpness", "SHARPNESS", "DAMAGE_ALL");
    public static final Enchantment POWER = enchantment("power", "POWER", "ARROW_DAMAGE");
    public static final Enchantment FLAME = enchantment("flame", "FLAME", "ARROW_FIRE");
    public static final Enchantment INFINITY = enchantment("infinity", "INFINITY", "ARROW_INFINITE");
    public static final Enchantment PROTECTION = enchantment("protection", "PROTECTION", "PROTECTION_ENVIRONMENTAL");
    public static final Enchantment FIRE_PROTECTION = enchantment("fire_protection", "FIRE_PROTECTION", "PROTECTION_FIRE");
    public static final Enchantment PROJECTILE_PROTECTION = enchantment("projectile_protection", "PROJECTILE_PROTECTION", "PROTECTION_PROJECTILE");
    public static final Enchantment FEATHER_FALLING = enchantment("feather_falling", "FEATHER_FALLING", "PROTECTION_FALL");
    public static final Enchantment UNBREAKING = enchantment("unbreaking", "UNBREAKING", "DURABILITY");

    public static final PotionEffectType RESISTANCE = effect("RESISTANCE", "DAMAGE_RESISTANCE");
    public static final PotionEffectType STRENGTH = effect("STRENGTH", "INCREASE_DAMAGE");
    public static final PotionEffectType SLOWNESS = effect("SLOWNESS", "SLOW");

    public static final PotionType HEALING = potionType("HEALING", "INSTANT_HEAL");
    public static final PotionType HARMING = potionType("HARMING", "INSTANT_DAMAGE");
    public static final PotionType SWIFTNESS = potionType("SWIFTNESS", "SPEED");
    public static final PotionType REGENERATION = potionType("REGENERATION", "REGEN");

    public static final Particle HAPPY_VILLAGER = particle("HAPPY_VILLAGER", "VILLAGER_HAPPY");
    public static final Particle TOTEM_OF_UNDYING = particle("TOTEM_OF_UNDYING", "TOTEM");
    public static final Particle SMOKE = particle("SMOKE", "SMOKE_NORMAL");
    public static final Particle LARGE_SMOKE = particle("LARGE_SMOKE", "SMOKE_LARGE");
    public static final Particle ENTITY_EFFECT = particle("ENTITY_EFFECT", "SPELL_MOB");
    public static final Particle ENCHANT = particle("ENCHANT", "ENCHANTMENT_TABLE");
    public static final Particle BLOCK = particle("BLOCK", "BLOCK_CRACK");
    public static final Particle EXPLOSION = particle("EXPLOSION", "EXPLOSION_NORMAL", "EXPLOSION_LARGE");
    public static final Particle EXPLOSION_EMITTER = particle("EXPLOSION_EMITTER", "EXPLOSION_HUGE");

    private VersionCompat() {}

    public static void rotateBlockData(BlockData blockData, StructureRotation rotation) {
        if (blockData == null || rotation == StructureRotation.NONE) {
            return;
        }
        if (tryNativeRotate(blockData, rotation)) {
            return;
        }
        int turns = switch (rotation) {
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
            default -> 0;
        };
        for (int i = 0; i < turns; i++) {
            rotateQuarterTurn(blockData);
        }
    }

    private static boolean tryNativeRotate(BlockData blockData, StructureRotation rotation) {
        if (BLOCK_ROTATE_METHOD == null) {
            return false;
        }
        try {
            BLOCK_ROTATE_METHOD.invoke(blockData, rotation);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static void rotateQuarterTurn(BlockData blockData) {
        if (blockData instanceof Directional directional) {
            BlockFace rotated = rotateFace(directional.getFacing());
            if (directional.getFaces().contains(rotated)) {
                directional.setFacing(rotated);
            }
        }

        if (blockData instanceof Rotatable rotatable) {
            rotatable.setRotation(rotateFace(rotatable.getRotation()));
        }

        if (blockData instanceof Orientable orientable) {
            switch (orientable.getAxis()) {
                case X -> orientable.setAxis(org.bukkit.Axis.Z);
                case Z -> orientable.setAxis(org.bukkit.Axis.X);
                default -> { }
            }
        }

        if (blockData instanceof MultipleFacing multipleFacing) {
            var currentFaces = new ArrayList<>(multipleFacing.getFaces());
            for (BlockFace face : currentFaces) {
                multipleFacing.setFace(face, false);
            }
            for (BlockFace face : currentFaces) {
                BlockFace rotated = rotateFace(face);
                if (multipleFacing.getAllowedFaces().contains(rotated)) {
                    multipleFacing.setFace(rotated, true);
                }
            }
        }

        if (blockData instanceof Rail rail) {
            rail.setShape(rotateRailShape(rail.getShape()));
        }
    }

    private static Rail.Shape rotateRailShape(Rail.Shape shape) {
        return switch (shape) {
            case NORTH_SOUTH -> Rail.Shape.EAST_WEST;
            case EAST_WEST -> Rail.Shape.NORTH_SOUTH;
            case ASCENDING_NORTH -> Rail.Shape.ASCENDING_EAST;
            case ASCENDING_EAST -> Rail.Shape.ASCENDING_SOUTH;
            case ASCENDING_SOUTH -> Rail.Shape.ASCENDING_WEST;
            case ASCENDING_WEST -> Rail.Shape.ASCENDING_NORTH;
            case SOUTH_EAST -> Rail.Shape.SOUTH_WEST;
            case SOUTH_WEST -> Rail.Shape.NORTH_WEST;
            case NORTH_WEST -> Rail.Shape.NORTH_EAST;
            case NORTH_EAST -> Rail.Shape.SOUTH_EAST;
        };
    }

    private static BlockFace rotateFace(BlockFace face) {
        if (face == BlockFace.UP || face == BlockFace.DOWN || face == BlockFace.SELF) {
            return face;
        }

        List<String> order = List.of(
            "NORTH",
            "NORTH_NORTH_EAST",
            "NORTH_EAST",
            "EAST_NORTH_EAST",
            "EAST",
            "EAST_SOUTH_EAST",
            "SOUTH_EAST",
            "SOUTH_SOUTH_EAST",
            "SOUTH",
            "SOUTH_SOUTH_WEST",
            "SOUTH_WEST",
            "WEST_SOUTH_WEST",
            "WEST",
            "WEST_NORTH_WEST",
            "NORTH_WEST",
            "NORTH_NORTH_WEST"
        );

        int index = order.indexOf(face.name());
        if (index >= 0) {
            return BlockFace.valueOf(order.get((index + 4) % order.size()));
        }
        return face;
    }

    private static Method resolveBlockRotateMethod() {
        try {
            return BlockData.class.getMethod("rotate", StructureRotation.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Material material(String... names) {
        for (String name : names) {
            try {
                return Material.valueOf(name);
            } catch (IllegalArgumentException ignored) {
            }
        }
        throw new IllegalStateException("Material introuvable: " + String.join(", ", names));
    }

    private static Particle particle(String... names) {
        for (String name : names) {
            try {
                return Particle.valueOf(name);
            } catch (IllegalArgumentException ignored) {
            }
        }
        throw new IllegalStateException("Particle introuvable: " + String.join(", ", names));
    }

    private static PotionType potionType(String... names) {
        for (String name : names) {
            try {
                return PotionType.valueOf(name);
            } catch (IllegalArgumentException ignored) {
            }
        }
        throw new IllegalStateException("PotionType introuvable: " + String.join(", ", names));
    }

    private static PotionEffectType effect(String... names) {
        for (String name : names) {
            PotionEffectType type = PotionEffectType.getByName(name);
            if (type != null) {
                return type;
            }
        }
        throw new IllegalStateException("PotionEffectType introuvable: " + String.join(", ", names));
    }

    private static Enchantment enchantment(String key, String... aliases) {
        Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(key));
        if (enchantment != null) {
            return enchantment;
        }
        for (String alias : aliases) {
            enchantment = Enchantment.getByName(alias);
            if (enchantment != null) {
                return enchantment;
            }
        }
        throw new IllegalStateException("Enchantment introuvable: " + key);
    }
}
