package fr.obsidianlabs.plugins.villagedefense.kit;

import org.bukkit.Material;

public enum KitType {

    GUARDIAN(
        Material.SHIELD,
        "&9", 1
    ),

    RANGER(
        Material.BOW,
        "&a", 3
    ),

    BERSERKER(
        Material.IRON_AXE,
        "&c", 5
    ),

    MEDIC(
        Material.GHAST_TEAR,
        "&d", 7
    ),

    ENGINEER(
        Material.REDSTONE,
        "&6", 10
    );

    private final Material icon;
    private final String colorCode;
    private final int defaultLevelRequired;

    KitType(Material icon, String colorCode, int defaultLevelRequired) {
        this.icon                 = icon;
        this.colorCode            = colorCode;
        this.defaultLevelRequired = defaultLevelRequired;
    }

    public Material getIcon()       { return icon; }
    public String getColorCode()    { return colorCode; }
    public int getDefaultLevelRequired() { return defaultLevelRequired; }
    public String getConfigKey()    { return name().toLowerCase(); }
    public String getNameKey() { return "kit.type." + name().toLowerCase() + ".name"; }
    public String getDescKey() { return "kit.type." + name().toLowerCase() + ".desc"; }
    public String getLoreKey() { return "kit.type." + name().toLowerCase() + ".lore"; }

    public static KitType getDefault() {
        return GUARDIAN;
    }

    public static KitType fromSerialized(String value) {
        if (value == null || value.isBlank()) {
            return getDefault();
        }

        return switch (value.trim().toUpperCase()) {
            case "WARRIOR", "GUARDIAN" -> GUARDIAN;
            case "ARCHER", "RANGER" -> RANGER;
            case "MAGE", "BERSERKER" -> BERSERKER;
            case "MEDIC" -> MEDIC;
            case "TRAPPER", "ENGINEER" -> ENGINEER;
            default -> getDefault();
        };
    }
}
