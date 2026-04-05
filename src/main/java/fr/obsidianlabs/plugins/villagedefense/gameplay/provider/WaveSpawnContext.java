package fr.obsidianlabs.plugins.villagedefense.gameplay.provider;

import fr.obsidianlabs.plugins.villagedefense.gameplay.model.WaveCategory;

/** Contexte transmis au provider au moment du spawn. */
public class WaveSpawnContext {

    private final String arenaId;
    private final int waveIndex;
    private final WaveCategory category;
    private final double healthMultiplier;
    private final double damageMultiplier;
    private final double speedMultiplier;

    public WaveSpawnContext(String arenaId, int waveIndex, WaveCategory category,
                            double healthMultiplier, double damageMultiplier, double speedMultiplier) {
        this.arenaId          = arenaId;
        this.waveIndex        = waveIndex;
        this.category         = category;
        this.healthMultiplier = healthMultiplier;
        this.damageMultiplier = damageMultiplier;
        this.speedMultiplier  = speedMultiplier;
    }

    public String getArenaId()          { return arenaId; }
    public int getWaveIndex()           { return waveIndex; }
    public WaveCategory getCategory()   { return category; }
    public double getHealthMultiplier() { return healthMultiplier; }
    public double getDamageMultiplier() { return damageMultiplier; }
    public double getSpeedMultiplier()  { return speedMultiplier; }
}
