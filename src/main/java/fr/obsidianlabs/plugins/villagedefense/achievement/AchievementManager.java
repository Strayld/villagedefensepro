package fr.obsidianlabs.plugins.villagedefense.achievement;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AchievementManager {

    private final VillageDefensePlugin plugin;
    private final File file;
    private final Map<String, AchievementDefinition> definitions = new LinkedHashMap<>();

    public AchievementManager(VillageDefensePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "achievements.yml");
    }

    public void load() {
        if (!file.exists()) {
            createDefaults();
            save();
            return;
        }

        definitions.clear();
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("achievements");
        if (root == null) return;

        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) continue;

            String displayName = sec.getString("display-name", id);
            String description = sec.getString("description", "");
            Material icon = parseMaterial(sec.getString("icon", "NETHER_STAR"));
            AchievementCondition condition = parseCondition(sec.getString("condition", "KILLS"));
            long target = sec.getLong("target", 1);

            definitions.put(id.toUpperCase(), new AchievementDefinition(
                id, displayName, description, icon, condition, target
            ));
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        ConfigurationSection root = cfg.createSection("achievements");

        for (AchievementDefinition def : definitions.values()) {
            ConfigurationSection sec = root.createSection(def.getId());
            sec.set("display-name", def.getDisplayName());
            sec.set("description", def.getDescription());
            sec.set("icon", def.getIcon().name());
            sec.set("condition", def.getCondition().name());
            sec.set("target", def.getTarget());
        }

        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder achievements.yml : " + e.getMessage());
        }
    }

    public void createDefaults() {
        definitions.clear();
        addDef("FIRST_BLOOD",    "Premier Sang",            "Tuer votre premier ennemi.",           Material.IRON_SWORD,        AchievementCondition.KILLS,        1);
        addDef("ZOMBIE_SLAYER",  "Tueur de Zombies",        "Éliminer 100 ennemis.",                Material.DIAMOND_SWORD,     AchievementCondition.KILLS,      100);
        addDef("ZOMBIE_LEGEND",  "Légende des Zombies",     "Éliminer 500 ennemis.",                Material.NETHERITE_SWORD,   AchievementCondition.KILLS,      500);
        addDef("SURVIVOR_I",     "Survivant I",             "Compléter 10 vagues.",                 Material.SHIELD,            AchievementCondition.WAVES,       10);
        addDef("SURVIVOR_II",    "Survivant II",            "Compléter 25 vagues.",                 Material.GOLDEN_CHESTPLATE, AchievementCondition.WAVES,       25);
        addDef("SURVIVOR_III",   "Survivant III",           "Compléter 50 vagues.",                 Material.DIAMOND_CHESTPLATE,AchievementCondition.WAVES,       50);
        addDef("VETERAN",        "Vétéran",                 "Jouer 10 parties.",                    Material.COMPASS,           AchievementCondition.GAMES,       10);
        addDef("WEALTHY",        "Riche",                   "Gagner 10 000 pièces au total.",       Material.GOLD_BLOCK,        AchievementCondition.COINS_EARNED, 10000);
        addDef("MILLIONNAIRE",   "Millionnaire",            "Gagner 100 000 pièces au total.",      Material.DIAMOND,           AchievementCondition.COINS_EARNED, 100000);
    }

    private void addDef(String id, String name, String desc, Material icon,
                        AchievementCondition condition, long target) {
        definitions.put(id, new AchievementDefinition(id, name, desc, icon, condition, target));
    }

    public List<AchievementDefinition> getAll() {
        return new ArrayList<>(definitions.values());
    }

    public AchievementDefinition getById(String id) {
        if (id == null) return null;
        return definitions.get(id.toUpperCase());
    }

    public void add(AchievementDefinition def) {
        definitions.put(def.getId().toUpperCase(), def);
        save();
    }

    public void remove(String id) {
        definitions.remove(id.toUpperCase());
        save();
    }

    private Material parseMaterial(String name) {
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Material.NETHER_STAR;
        }
    }

    private AchievementCondition parseCondition(String name) {
        try {
            return AchievementCondition.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return AchievementCondition.KILLS;
        }
    }
}
