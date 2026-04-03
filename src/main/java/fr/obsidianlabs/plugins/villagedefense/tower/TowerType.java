package fr.obsidianlabs.plugins.villagedefense.tower;

import org.bukkit.Material;

public enum TowerType {

    ARCHER(
        Material.BIRCH_FENCE,
        Material.SKELETON_SKULL
    ),

    TREBUCHET(
        Material.OAK_LOG,
        Material.TNT
    ),

    MAGE(
        Material.QUARTZ_PILLAR,
        Material.AMETHYST_SHARD
    ),

    ICE(
        Material.PACKED_ICE,
        Material.BLUE_ICE
    ),

    LIGHTNING(
        Material.LIGHTNING_ROD,
        Material.LIGHTNING_ROD
    ),

    CANNON(
        Material.IRON_BLOCK,
        Material.TNT
    );

    private final Material baseMaterial;
    private final Material topMaterial;

    TowerType(Material baseMaterial, Material topMaterial) {
        this.baseMaterial = baseMaterial;
        this.topMaterial = topMaterial;
    }

    public Material getBaseMaterial() { return baseMaterial; }
    public Material getTopMaterial() { return topMaterial; }

    public String getConfigKey() {
        return name().toLowerCase();
    }

    public String getNameKey() { return "tower.type." + name().toLowerCase() + ".name"; }
    public String getDescKey() { return "tower.type." + name().toLowerCase() + ".desc"; }
}
