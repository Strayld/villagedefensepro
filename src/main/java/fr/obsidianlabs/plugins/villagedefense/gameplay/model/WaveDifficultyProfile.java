package fr.obsidianlabs.plugins.villagedefense.gameplay.model;

/** Profil calculé à runtime — non persisté. */
public class WaveDifficultyProfile {

    private final int waveIndex;
    private final WaveCategory category;

    private final double totalBudget;

    private final double healthMultiplier;
    private final double damageMultiplier;
    private final double speedMultiplier;
    private final double rewardMultiplier;

    private final int maxMobTypes;

    public WaveDifficultyProfile(
            int waveIndex,
            WaveCategory category,
            double totalBudget,
            double healthMultiplier,
            double damageMultiplier,
            double speedMultiplier,
            double rewardMultiplier,
            int maxMobTypes) {
        this.waveIndex        = waveIndex;
        this.category         = category;
        this.totalBudget      = totalBudget;
        this.healthMultiplier = healthMultiplier;
        this.damageMultiplier = damageMultiplier;
        this.speedMultiplier  = speedMultiplier;
        this.rewardMultiplier = rewardMultiplier;
        this.maxMobTypes      = maxMobTypes;
    }

    public int getWaveIndex()           { return waveIndex; }
    public WaveCategory getCategory()   { return category; }
    public double getTotalBudget()      { return totalBudget; }
    public double getHealthMultiplier() { return healthMultiplier; }
    public double getDamageMultiplier() { return damageMultiplier; }
    public double getSpeedMultiplier()  { return speedMultiplier; }
    public double getRewardMultiplier() { return rewardMultiplier; }
    public int getMaxMobTypes()         { return maxMobTypes; }
}
