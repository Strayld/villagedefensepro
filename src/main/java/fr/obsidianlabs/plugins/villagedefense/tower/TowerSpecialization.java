package fr.obsidianlabs.plugins.villagedefense.tower;

import org.bukkit.Material;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Spécialisations déblocables pour chaque type de tour au niveau maximum.
 * Chaque type de tour dispose de DEUX spécialisations mutuellement exclusives.
 */
public enum TowerSpecialization {

    NONE(Material.BARRIER, null),

    // ── Archer ────────────────────────────────────────────────────────────
    MULTI_SHOT(
        Material.FEATHER,
        TowerType.ARCHER
    ),
    INCENDIARY_ARROW(
        Material.BLAZE_POWDER,
        TowerType.ARCHER
    ),

    // ── Trébuchet ─────────────────────────────────────────────────────────
    INCENDIARY_TNT(
        Material.FIRE_CHARGE,
        TowerType.TREBUCHET
    ),
    CLUSTER(
        Material.GUNPOWDER,
        TowerType.TREBUCHET
    ),

    // ── Mage ──────────────────────────────────────────────────────────────
    FIRE(
        Material.FIRE_CHARGE,
        TowerType.MAGE
    ),
    POISON(
        Material.FERMENTED_SPIDER_EYE,
        TowerType.MAGE
    ),

    // ── Glace ─────────────────────────────────────────────────────────────
    BLIZZARD(
        Material.SNOWBALL,
        TowerType.ICE
    ),
    FROST_NOVA(
        Material.PACKED_ICE,
        TowerType.ICE
    ),

    // ── Foudre ────────────────────────────────────────────────────────────
    OVERLOAD(
        Material.REDSTONE,
        TowerType.LIGHTNING
    ),
    STUN(
        Material.IRON_INGOT,
        TowerType.LIGHTNING
    ),

    // ── Canon ─────────────────────────────────────────────────────────────
    EXPLOSIVE_SHOT(
        Material.TNT,
        TowerType.CANNON
    ),
    GRAPESHOT(
        Material.IRON_NUGGET,
        TowerType.CANNON
    );

    private final Material icon;
    private final TowerType towerType;

    TowerSpecialization(Material icon, TowerType towerType) {
        this.icon        = icon;
        this.towerType   = towerType;
    }

    public Material getIcon()      { return icon; }
    public TowerType getTowerType() { return towerType; }

    public String getNameKey() { return "spec." + name().toLowerCase().replace('_', '-') + ".name"; }
    public String getDescKey() { return "spec." + name().toLowerCase().replace('_', '-') + ".desc"; }

    /** Retourne les spécialisations disponibles pour un type de tour donné. */
    public static List<TowerSpecialization> forType(TowerType type) {
        return Arrays.stream(values())
            .filter(s -> s.towerType == type)
            .collect(Collectors.toList());
    }
}
