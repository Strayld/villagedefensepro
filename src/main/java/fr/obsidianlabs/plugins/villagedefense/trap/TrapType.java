package fr.obsidianlabs.plugins.villagedefense.trap;

import org.bukkit.Material;

/**
 * Types de pièges achetables par les joueurs sur les emplacements de pièges.
 */
public enum TrapType {

    //                                                                 triggerRadius  effectRadius
    SLOW_TRAP(
        Material.TRIPWIRE_HOOK,
        30,
        15_000L,
        2.0, 2.0
    ),

    DAMAGE_MINE(
        Material.TNT,
        50,
        0L,   // usage unique → pas de cooldown, le piège est détruit
        3.0, 3.0
    ),

    NET_TRAP(
        Material.COBWEB,
        40,
        25_000L,
        2.0, 2.0
    );

    private final Material icon;
    private final int      cost;
    private final long     cooldownMs;
    private final double   triggerRadius;
    private final double   effectRadius;

    TrapType(Material icon, int cost, long cooldownMs, double triggerRadius, double effectRadius) {
        this.icon          = icon;
        this.cost          = cost;
        this.cooldownMs    = cooldownMs;
        this.triggerRadius = triggerRadius;
        this.effectRadius  = effectRadius;
    }

    public Material getIcon()         { return icon; }
    public String getNameKey() { return "trap.type." + name().toLowerCase().replace('_', '-') + ".name"; }
    public String getDescKey() { return "trap.type." + name().toLowerCase().replace('_', '-') + ".desc"; }
    public int      getCost()         { return cost; }
    public long     getCooldownMs()   { return cooldownMs; }
    public double   getTriggerRadius(){ return triggerRadius; }
    public double   getEffectRadius() { return effectRadius; }
    /** Usage unique : se détruit après la première activation. */
    public boolean  isOneUse()        { return cooldownMs == 0L; }
}
