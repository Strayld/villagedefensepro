package fr.obsidianlabs.plugins.villagedefense.gameplay.provider;

import fr.obsidianlabs.plugins.villagedefense.gameplay.model.MobSource;
import org.bukkit.Material;

/** Représente un mob sélectionnable dans les GUIs admin. */
public class MobOption {

    private final String id;
    private final MobSource source;
    private final String displayName;
    private final String description;
    private final Material icon;

    public MobOption(String id, MobSource source, String displayName, String description, Material icon) {
        this.id          = id;
        this.source      = source;
        this.displayName = displayName;
        this.description = description;
        this.icon        = icon;
    }

    public String getId()          { return id; }
    public MobSource getSource()   { return source; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public Material getIcon()      { return icon; }
}
