package fr.obsidianlabs.plugins.villagedefense.gameplay.persistence;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.gameplay.model.ArenaGameplaySettings;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Lit et écrit les fichiers arenas/{id}_gameplay.yml
 */
public class GameplayConfigRepository {

    private final VillageDefensePlugin plugin;
    private final GameplayConfigMapper mapper;
    private final File arenasFolder;

    public GameplayConfigRepository(VillageDefensePlugin plugin) {
        this.plugin       = plugin;
        this.mapper       = new GameplayConfigMapper();
        this.arenasFolder = new File(plugin.getDataFolder(), "arenas");
        if (!arenasFolder.exists()) arenasFolder.mkdirs();
    }

    public boolean exists(String arenaId) {
        return fileFor(arenaId).exists();
    }

    public ArenaGameplaySettings load(String arenaId) {
        File file = fileFor(arenaId);
        if (!file.exists()) return null;
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            return mapper.fromConfig(config);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                "[GameplayConfigRepository] Erreur chargement de " + file.getName(), e);
            return null;
        }
    }

    public void save(String arenaId, ArenaGameplaySettings settings) {
        File file = fileFor(arenaId);
        YamlConfiguration config = new YamlConfiguration();
        mapper.toConfig(settings, config);
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE,
                "[GameplayConfigRepository] Erreur sauvegarde de " + file.getName(), e);
        }
    }

    public void delete(String arenaId) {
        File file = fileFor(arenaId);
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("[GameplayConfigRepository] Impossible de supprimer " + file.getName());
        }
    }

    private File fileFor(String arenaId) {
        return new File(arenasFolder, arenaId + "_gameplay.yml");
    }
}
