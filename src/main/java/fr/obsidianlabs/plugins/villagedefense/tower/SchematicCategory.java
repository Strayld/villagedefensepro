package fr.obsidianlabs.plugins.villagedefense.tower;

import org.bukkit.Material;

/**
 * Catégories de schematics disponibles dans l'éditeur admin.
 *
 * Les catégories de tours ont 3 niveaux.
 * Les catégories d'environnement (SPAWN_MONSTER, TOWER_SLOT) ont 1 seul niveau.
 */
public enum SchematicCategory {

    ARCHER      ("archer",        3, Material.SKELETON_SKULL, TowerType.ARCHER),
    TREBUCHET   ("trebuchet",     3, Material.TNT,            TowerType.TREBUCHET),
    MAGE        ("mage",          3, Material.AMETHYST_SHARD, TowerType.MAGE),
    ICE         ("ice",           3, Material.PACKED_ICE,     TowerType.ICE),
    LIGHTNING   ("lightning",     3, Material.LIGHTNING_ROD,  TowerType.LIGHTNING),
    CANNON      ("cannon",        3, Material.TNT_MINECART,   TowerType.CANNON),
    SPAWN_MONSTER("spawn_monster",1, Material.ZOMBIE_HEAD,    null),
    TOWER_SLOT  ("tower_slot",    1, Material.GOLD_BLOCK,     null);

    private final String configKey;
    private final int    maxLevels;
    private final Material icon;
    /** Référence vers le TowerType correspondant, ou null pour les catégories non-tour. */
    private final TowerType towerType;

    SchematicCategory(String configKey, int maxLevels, Material icon, TowerType towerType) {
        this.configKey   = configKey;
        this.maxLevels   = maxLevels;
        this.icon        = icon;
        this.towerType   = towerType;
    }

    public String   getConfigKey()   { return configKey; }
    public String   getNameKey() {
        return switch (this) {
            case SPAWN_MONSTER -> "gui.schematic-cat.name-spawn-monster";
            case TOWER_SLOT -> "gui.schematic-cat.name-tower-slot";
            default -> towerType.getNameKey();
        };
    }
    public String   getDescKey() {
        return "gui.schematic-cat.desc-" + name().toLowerCase().replace('_', '-');
    }
    public int      getMaxLevels()   { return maxLevels; }
    public Material getIcon()        { return icon; }
    public TowerType getTowerType()  { return towerType; }

    /** Vrai si cette catégorie correspond à un type de tour (ARCHER/TREBUCHET/MAGE). */
    public boolean isTowerCategory() { return towerType != null; }

    /** Retrouve la catégorie à partir d'un TowerType, ou null. */
    public static SchematicCategory ofTowerType(TowerType type) {
        for (SchematicCategory cat : values()) {
            if (cat.towerType == type) return cat;
        }
        return null;
    }
}
