package fr.obsidianlabs.plugins.villagedefense.gameplay.model;

public class MobEntry {

    private String id;
    private MobSource source;
    private String displayName;

    private int weight    = 10;
    private double threat = 5.0;

    private int minAmount = 1;
    private int maxAmount = 3;

    private int minWave = 1;
    private int maxWave = 0;  // 0 = illimité

    private boolean enabled = true;

    public MobEntry() {}

    public MobEntry(String id, MobSource source, String displayName) {
        this.id = id;
        this.source = source;
        this.displayName = displayName;
    }

    /** Vérifie si cette entrée est valide pour une vague donnée. */
    public boolean isValidForWave(int waveIndex) {
        if (!enabled) return false;
        if (waveIndex < minWave) return false;
        if (maxWave > 0 && waveIndex > maxWave) return false;
        return true;
    }

    public String getId()                       { return id; }
    public void setId(String v)                 { this.id = v; }

    public MobSource getSource()                { return source; }
    public void setSource(MobSource v)          { this.source = v; }

    public String getDisplayName()              { return displayName; }
    public void setDisplayName(String v)        { this.displayName = v; }

    public int getWeight()                      { return weight; }
    public void setWeight(int v)                { this.weight = v; }

    public double getThreat()                   { return threat; }
    public void setThreat(double v)             { this.threat = v; }

    public int getMinAmount()                   { return minAmount; }
    public void setMinAmount(int v)             { this.minAmount = v; }

    public int getMaxAmount()                   { return maxAmount; }
    public void setMaxAmount(int v)             { this.maxAmount = v; }

    public int getMinWave()                     { return minWave; }
    public void setMinWave(int v)               { this.minWave = v; }

    public int getMaxWave()                     { return maxWave; }
    public void setMaxWave(int v)               { this.maxWave = v; }

    public boolean isEnabled()                  { return enabled; }
    public void setEnabled(boolean v)           { this.enabled = v; }
}
