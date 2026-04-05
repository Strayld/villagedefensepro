package fr.obsidianlabs.plugins.villagedefense.gameplay.model;

import java.util.ArrayList;
import java.util.List;

public class MobPool {

    private String id;
    private String displayName;
    private WaveCategory defaultCategory;
    private List<MobEntry> entries;
    private boolean enabled = true;

    public MobPool() {
        this.entries = new ArrayList<>();
    }

    public MobPool(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
        this.entries = new ArrayList<>();
    }

    public String getId()                            { return id; }
    public void setId(String v)                      { this.id = v; }

    public String getDisplayName()                   { return displayName; }
    public void setDisplayName(String v)             { this.displayName = v; }

    public WaveCategory getDefaultCategory()         { return defaultCategory; }
    public void setDefaultCategory(WaveCategory v)   { this.defaultCategory = v; }

    public List<MobEntry> getEntries()               { return entries; }
    public void setEntries(List<MobEntry> v)         { this.entries = v; }

    public boolean isEnabled()                       { return enabled; }
    public void setEnabled(boolean v)                { this.enabled = v; }
}
