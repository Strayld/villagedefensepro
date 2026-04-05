package fr.obsidianlabs.plugins.villagedefense.gameplay.model;

public class WaveCategoryTemplate {

    private WaveCategory category;
    private String mobPoolId;
    private CompositionMode compositionMode;

    private double categoryBudgetMultiplier = 1.0;
    private double healthMultiplier         = 1.0;
    private double damageMultiplier         = 1.0;
    private double speedMultiplier          = 1.0;

    private int maxMobTypes    = 3;
    private int defaultMobCount = 10;
    private boolean enabled    = true;

    public WaveCategoryTemplate(WaveCategory category) {
        this.category = category;
        this.compositionMode = CompositionMode.WEIGHTED;
    }

    public WaveCategory getCategory()                        { return category; }
    public void setCategory(WaveCategory v)                  { this.category = v; }

    public String getMobPoolId()                             { return mobPoolId; }
    public void setMobPoolId(String v)                       { this.mobPoolId = v; }

    public CompositionMode getCompositionMode()              { return compositionMode; }
    public void setCompositionMode(CompositionMode v)        { this.compositionMode = v; }

    public double getCategoryBudgetMultiplier()              { return categoryBudgetMultiplier; }
    public void setCategoryBudgetMultiplier(double v)        { this.categoryBudgetMultiplier = v; }

    public double getHealthMultiplier()                      { return healthMultiplier; }
    public void setHealthMultiplier(double v)                { this.healthMultiplier = v; }

    public double getDamageMultiplier()                      { return damageMultiplier; }
    public void setDamageMultiplier(double v)                { this.damageMultiplier = v; }

    public double getSpeedMultiplier()                       { return speedMultiplier; }
    public void setSpeedMultiplier(double v)                 { this.speedMultiplier = v; }

    public int getMaxMobTypes()                              { return maxMobTypes; }
    public void setMaxMobTypes(int v)                        { this.maxMobTypes = v; }

    public int getDefaultMobCount()                          { return defaultMobCount; }
    public void setDefaultMobCount(int v)                    { this.defaultMobCount = v; }

    public boolean isEnabled()                               { return enabled; }
    public void setEnabled(boolean v)                        { this.enabled = v; }
}
