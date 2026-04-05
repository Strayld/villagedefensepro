package fr.obsidianlabs.plugins.villagedefense.gameplay.model;

public class WaveOverride {

    private int waveIndex;
    private boolean enabled = true;

    private WaveCategory forcedCategory;
    private String forcedPoolId;
    private CompositionMode forcedCompositionMode;

    private Double healthMultiplierOverride;
    private Double damageMultiplierOverride;
    private Double speedMultiplierOverride;

    public WaveOverride() {}

    public WaveOverride(int waveIndex) {
        this.waveIndex = waveIndex;
    }

    public int getWaveIndex()                              { return waveIndex; }
    public void setWaveIndex(int v)                        { this.waveIndex = v; }

    public boolean isEnabled()                             { return enabled; }
    public void setEnabled(boolean v)                      { this.enabled = v; }

    public WaveCategory getForcedCategory()                { return forcedCategory; }
    public void setForcedCategory(WaveCategory v)          { this.forcedCategory = v; }

    public String getForcedPoolId()                        { return forcedPoolId; }
    public void setForcedPoolId(String v)                  { this.forcedPoolId = v; }

    public CompositionMode getForcedCompositionMode()      { return forcedCompositionMode; }
    public void setForcedCompositionMode(CompositionMode v){ this.forcedCompositionMode = v; }

    public Double getHealthMultiplierOverride()            { return healthMultiplierOverride; }
    public void setHealthMultiplierOverride(Double v)      { this.healthMultiplierOverride = v; }

    public Double getDamageMultiplierOverride()            { return damageMultiplierOverride; }
    public void setDamageMultiplierOverride(Double v)      { this.damageMultiplierOverride = v; }

    public Double getSpeedMultiplierOverride()             { return speedMultiplierOverride; }
    public void setSpeedMultiplierOverride(Double v)       { this.speedMultiplierOverride = v; }
}
