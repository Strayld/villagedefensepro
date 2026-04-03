package fr.obsidianlabs.plugins.villagedefense.achievement;

import org.bukkit.Material;

public class AchievementDefinition {

    private final String id;
    private String displayName;
    private String description;
    private Material icon;
    private AchievementCondition condition;
    private long target;

    public AchievementDefinition(String id, String displayName, String description,
                                  Material icon, AchievementCondition condition, long target) {
        this.id = id.toUpperCase();
        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
        this.condition = condition;
        this.target = target;
    }

    public String getId()                     { return id; }
    public String getDisplayName()            { return displayName; }
    public String getDescription()            { return description; }
    public Material getIcon()                 { return icon; }
    public AchievementCondition getCondition() { return condition; }
    public long getTarget()                   { return target; }

    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setDescription(String description) { this.description = description; }
    public void setIcon(Material icon)             { this.icon = icon; }
    public void setCondition(AchievementCondition condition) { this.condition = condition; }
    public void setTarget(long target)             { this.target = target; }
}
