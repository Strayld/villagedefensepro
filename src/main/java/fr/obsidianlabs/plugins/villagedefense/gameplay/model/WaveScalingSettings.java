package fr.obsidianlabs.plugins.villagedefense.gameplay.model;

public class WaveScalingSettings {

    private double baseBudget          = 20.0;
    private double linearGrowth        = 5.0;
    private double exponentialGrowth   = 0.1;

    private double baseHealthMultiplier = 1.0;
    private double baseDamageMultiplier = 1.0;
    private double baseSpeedMultiplier  = 1.0;

    private double healthPerWave = 0.05;
    private double damagePerWave = 0.03;
    private double speedPerWave  = 0.01;

    private int bossInterval    = 10;
    private int eliteInterval   = 5;
    private int specialInterval = 0;  // 0 = désactivé

    private int earlyGameMaxMobTypes = 2;
    private int midGameMaxMobTypes   = 3;
    private int lateGameMaxMobTypes  = 4;

    private int midGameStartWave  = 10;
    private int lateGameStartWave = 20;

    // ---- Getters / Setters ----

    public double getBaseBudget()                    { return baseBudget; }
    public void setBaseBudget(double v)              { this.baseBudget = v; }

    public double getLinearGrowth()                  { return linearGrowth; }
    public void setLinearGrowth(double v)            { this.linearGrowth = v; }

    public double getExponentialGrowth()             { return exponentialGrowth; }
    public void setExponentialGrowth(double v)       { this.exponentialGrowth = v; }

    public double getBaseHealthMultiplier()          { return baseHealthMultiplier; }
    public void setBaseHealthMultiplier(double v)    { this.baseHealthMultiplier = v; }

    public double getBaseDamageMultiplier()          { return baseDamageMultiplier; }
    public void setBaseDamageMultiplier(double v)    { this.baseDamageMultiplier = v; }

    public double getBaseSpeedMultiplier()           { return baseSpeedMultiplier; }
    public void setBaseSpeedMultiplier(double v)     { this.baseSpeedMultiplier = v; }

    public double getHealthPerWave()                 { return healthPerWave; }
    public void setHealthPerWave(double v)           { this.healthPerWave = v; }

    public double getDamagePerWave()                 { return damagePerWave; }
    public void setDamagePerWave(double v)           { this.damagePerWave = v; }

    public double getSpeedPerWave()                  { return speedPerWave; }
    public void setSpeedPerWave(double v)            { this.speedPerWave = v; }

    public int getBossInterval()                     { return bossInterval; }
    public void setBossInterval(int v)               { this.bossInterval = v; }

    public int getEliteInterval()                    { return eliteInterval; }
    public void setEliteInterval(int v)              { this.eliteInterval = v; }

    public int getSpecialInterval()                  { return specialInterval; }
    public void setSpecialInterval(int v)            { this.specialInterval = v; }

    public int getEarlyGameMaxMobTypes()             { return earlyGameMaxMobTypes; }
    public void setEarlyGameMaxMobTypes(int v)       { this.earlyGameMaxMobTypes = v; }

    public int getMidGameMaxMobTypes()               { return midGameMaxMobTypes; }
    public void setMidGameMaxMobTypes(int v)         { this.midGameMaxMobTypes = v; }

    public int getLateGameMaxMobTypes()              { return lateGameMaxMobTypes; }
    public void setLateGameMaxMobTypes(int v)        { this.lateGameMaxMobTypes = v; }

    public int getMidGameStartWave()                 { return midGameStartWave; }
    public void setMidGameStartWave(int v)           { this.midGameStartWave = v; }

    public int getLateGameStartWave()                { return lateGameStartWave; }
    public void setLateGameStartWave(int v)          { this.lateGameStartWave = v; }
}
