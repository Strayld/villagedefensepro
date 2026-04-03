package fr.obsidianlabs.plugins.villagedefense.perk;

import org.bukkit.Material;

/**
 * Atouts achetables en cours de partie depuis le GUI des atouts.
 * maxStacks : nombre max de fois qu'on peut acheter cet atout. -1 = limité par MAX_COMPANIONS.
 */
public enum PerkType {

    MAX_HEALTH(
        Material.GOLDEN_APPLE,
        150, 1, 3
    ),
    ATTACK_DAMAGE(
        Material.IRON_SWORD,
        100, 1, 3
    ),
    COMPANION_WOLF(
        Material.BONE,
        300, 3, -1
    ),
    COMPANION_GOLEM(
        Material.IRON_BLOCK,
        500, 6, -1
    ),
    WAVE_SKIP(
        Material.CLOCK,
        800, 5, 1
    ),
    BUY_VILLAGER(
        Material.VILLAGER_SPAWN_EGG,
        2000, 5, 2
    ),
    VILLAGE_GUARD(
        Material.CARVED_PUMPKIN,
        1500, 8, 0
    );

    private final Material icon;
    private final long cost;
    private final int levelRequired;
    private final int maxStacks;

    PerkType(Material icon, long cost, int levelRequired, int maxStacks) {
        this.icon          = icon;
        this.cost          = cost;
        this.levelRequired = levelRequired;
        this.maxStacks     = maxStacks;
    }

    public String getNameKey() { return "perk." + name().toLowerCase().replace('_', '-') + ".name"; }
    public String getDescKey() { return "perk." + name().toLowerCase().replace('_', '-') + ".desc"; }
    public Material getIcon()         { return icon; }
    public long    getCost()          { return cost; }
    public int     getLevelRequired() { return levelRequired; }
    /** Nombre max de stacks, ou -1 si limité par le plafond compagnon global. */
    public int     getMaxStacks()     { return maxStacks; }
    public boolean isCompanion()      { return this == COMPANION_WOLF || this == COMPANION_GOLEM; }
}
