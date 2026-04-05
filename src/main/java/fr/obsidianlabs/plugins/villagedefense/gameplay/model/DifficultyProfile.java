package fr.obsidianlabs.plugins.villagedefense.gameplay.model;

public class DifficultyProfile {

    private double healthMultiplier;
    private double damageMultiplier;
    private double speedMultiplier;
    private double rewardMultiplier;

    public DifficultyProfile(double health, double damage, double speed, double reward) {
        this.healthMultiplier = health;
        this.damageMultiplier = damage;
        this.speedMultiplier = speed;
        this.rewardMultiplier = reward;
    }

    public static DifficultyProfile defaultFor(GameDifficulty difficulty) {
        return switch (difficulty) {
            case EASY   -> new DifficultyProfile(0.85, 0.85, 0.95, 0.9);
            case NORMAL -> new DifficultyProfile(1.0,  1.0,  1.0,  1.0);
            case HARD   -> new DifficultyProfile(1.2,  1.15, 1.05, 1.25);
            case CUSTOM -> new DifficultyProfile(1.0,  1.0,  1.0,  1.0);
        };
    }

    public double getHealthMultiplier()  { return healthMultiplier; }
    public double getDamageMultiplier()  { return damageMultiplier; }
    public double getSpeedMultiplier()   { return speedMultiplier; }
    public double getRewardMultiplier()  { return rewardMultiplier; }

    public void setHealthMultiplier(double v)  { this.healthMultiplier = v; }
    public void setDamageMultiplier(double v)  { this.damageMultiplier = v; }
    public void setSpeedMultiplier(double v)   { this.speedMultiplier = v; }
    public void setRewardMultiplier(double v)  { this.rewardMultiplier = v; }
}
