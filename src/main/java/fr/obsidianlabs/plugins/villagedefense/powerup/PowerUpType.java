package fr.obsidianlabs.plugins.villagedefense.powerup;

import fr.obsidianlabs.plugins.villagedefense.compat.VersionCompat;
import org.bukkit.Material;
import org.bukkit.potion.PotionEffectType;

public enum PowerUpType {

    RESISTANCE(
        Material.TOTEM_OF_UNDYING,
        VersionCompat.RESISTANCE, 3, 400
    ),
    SPEED(
        Material.SUGAR,
        PotionEffectType.SPEED, 1, 600
    ),
    STRENGTH(
        Material.BLAZE_POWDER,
        VersionCompat.STRENGTH, 1, 600
    ),
    REGENERATION(
        Material.GHAST_TEAR,
        PotionEffectType.REGENERATION, 1, 300
    ),
    /** Élimine instantanément tous les mobs actifs sauf le boss de la vague. */
    ZOMBIE_CLEAR(
        Material.NETHER_STAR,
        null, 0, 0
    ),
    /** Chaque attaque tue en un coup pendant 10 secondes. */
    INSTANT_KILL(
        Material.ARROW,
        null, 0, 200
    ),
    /** Double les dégâts infligés pendant 30 secondes. */
    DOUBLE_DAMAGE(
        Material.FIRE_CHARGE,
        null, 0, 600
    );

    private final Material icon;
    private final PotionEffectType effectType; // null = effet spécial géré manuellement
    private final int amplifier;
    private final int durationTicks;

    PowerUpType(Material icon, PotionEffectType effectType, int amplifier, int durationTicks) {
        this.icon          = icon;
        this.effectType    = effectType;
        this.amplifier     = amplifier;
        this.durationTicks = durationTicks;
    }

    public String getNameKey() { return "powerup." + name().toLowerCase().replace('_', '-') + ".name"; }
    public String getChatKey() { return "powerup." + name().toLowerCase().replace('_', '-') + ".chat"; }
    public Material         getIcon()           { return icon; }
    /** Peut être null pour les effets spéciaux (ZOMBIE_CLEAR, INSTANT_KILL, DOUBLE_DAMAGE). */
    public PotionEffectType getEffectType()     { return effectType; }
    public int              getAmplifier()      { return amplifier; }
    public int              getDurationTicks()  { return durationTicks; }
    /** Vrai si l'effet est instantané (pas de durée). */
    public boolean          isInstant()         { return durationTicks == 0; }
    /** Vrai si l'effet est géré manuellement (pas une potion standard). */
    public boolean          isSpecial()         { return effectType == null; }
}
