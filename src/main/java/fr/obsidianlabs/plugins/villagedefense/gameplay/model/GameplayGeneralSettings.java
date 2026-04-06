package fr.obsidianlabs.plugins.villagedefense.gameplay.model;

public class GameplayGeneralSettings {

    private boolean enabled              = true;
    private boolean useAdvancedWaveSystem = true;
    private boolean allowWaveOverrides   = true;
    private boolean allowMythicMobs      = false;

    public boolean isEnabled()                    { return enabled; }
    public void setEnabled(boolean v)             { this.enabled = v; }

    public boolean isUseAdvancedWaveSystem()      { return useAdvancedWaveSystem; }
    public void setUseAdvancedWaveSystem(boolean v){ this.useAdvancedWaveSystem = v; }

    public boolean isAllowWaveOverrides()         { return allowWaveOverrides; }
    public void setAllowWaveOverrides(boolean v)  { this.allowWaveOverrides = v; }

    public boolean isAllowMythicMobs()            { return allowMythicMobs; }
    public void setAllowMythicMobs(boolean v)     { this.allowMythicMobs = v; }
}
