package fr.obsidianlabs.plugins.villagedefense.kit;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.kit.impl.*;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.Map;

public class KitManager {

    private final VillageDefensePlugin plugin;
    private final Map<KitType, Kit> kits = new EnumMap<>(KitType.class);

    public KitManager(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        kits.put(KitType.GUARDIAN, new GuardianKit(plugin));
        kits.put(KitType.RANGER, new RangerKit(plugin));
        kits.put(KitType.BERSERKER, new BerserkerKit(plugin));
        kits.put(KitType.MEDIC, new MedicKit(plugin));
        kits.put(KitType.ENGINEER, new EngineerKit(plugin));
        plugin.getLogger().info("[KitManager] " + kits.size() + " kits chargés.");
    }

    public Kit getKit(KitType type) {
        return kits.get(type);
    }

    public Map<KitType, Kit> getAllKits() {
        return kits;
    }

    public int getLevelRequired(KitType type) {
        FileConfiguration config = plugin.getConfig();
        String path = "kits." + type.getConfigKey() + ".level-required";
        return config.getInt(path, type.getDefaultLevelRequired());
    }

    public boolean hasCustomLevelRequired(KitType type) {
        return plugin.getConfig().isSet("kits." + type.getConfigKey() + ".level-required");
    }

    public void setLevelRequired(KitType type, int levelRequired) {
        plugin.getConfig().set("kits." + type.getConfigKey() + ".level-required", levelRequired);
        plugin.saveConfig();
    }

    public void resetLevelRequired(KitType type) {
        plugin.getConfig().set("kits." + type.getConfigKey() + ".level-required", null);
        plugin.saveConfig();
    }

    public int countCustomLevelRequirements() {
        int count = 0;
        for (KitType type : KitType.values()) {
            if (hasCustomLevelRequired(type)) {
                count++;
            }
        }
        return count;
    }
}
