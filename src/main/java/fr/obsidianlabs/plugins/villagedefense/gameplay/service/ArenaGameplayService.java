package fr.obsidianlabs.plugins.villagedefense.gameplay.service;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.gameplay.model.*;
import fr.obsidianlabs.plugins.villagedefense.gameplay.persistence.GameplayConfigRepository;

import java.util.HashMap;
import java.util.Map;

/**
 * Point d'entrée principal du système Gameplay.
 * Charge, cache, expose et sauvegarde les settings par arène.
 */
public class ArenaGameplayService {

    private final VillageDefensePlugin plugin;
    private final GameplayConfigRepository repository;
    private final Map<String, ArenaGameplaySettings> cache = new HashMap<>();

    public ArenaGameplayService(VillageDefensePlugin plugin) {
        this.plugin     = plugin;
        this.repository = new GameplayConfigRepository(plugin);
    }

    public ArenaGameplaySettings getSettings(String arenaId) {
        ArenaGameplaySettings settings = cache.computeIfAbsent(arenaId, id -> {
            ArenaGameplaySettings loaded = repository.load(id);
            return loaded != null ? loaded : ArenaGameplaySettings.createDefault();
        });
        settings.getGeneralSettings().setEnabled(true);
        settings.getGeneralSettings().setUseAdvancedWaveSystem(true);
        return settings;
    }

    public void saveSettings(String arenaId) {
        ArenaGameplaySettings settings = cache.get(arenaId);
        if (settings != null) {
            repository.save(arenaId, settings);
        }
    }

    public void resetSettings(String arenaId) {
        ArenaGameplaySettings fresh = ArenaGameplaySettings.createDefault();
        cache.put(arenaId, fresh);
        repository.save(arenaId, fresh);
    }

    public boolean hasSettings(String arenaId) {
        return cache.containsKey(arenaId) || repository.exists(arenaId);
    }

    /** Évicte le cache pour forcer un rechargement depuis disque. */
    public void evict(String arenaId) {
        cache.remove(arenaId);
    }

    /**
     * Construit la composition d'une vague en suivant le flow runtime complet.
     */
    public WaveComposition buildWave(
            String arenaId,
            int waveIndex,
            GameDifficulty selectedDifficulty,
            WaveCategoryResolver categoryResolver,
            WaveDifficultyCalculator difficultyCalculator,
            WaveComposer composer) {

        ArenaGameplaySettings settings = getSettings(arenaId);

        // 1. Résoudre catégorie
        WaveCategory category = categoryResolver.resolveCategory(waveIndex, settings);

        // 2. Récupérer l'éventuel override
        WaveOverride override = settings.getWaveOverrides().get(waveIndex);
        if (override != null && override.isEnabled() && override.getForcedCategory() != null) {
            category = override.getForcedCategory();
        }

        // 3. Calculer le profil de difficulté
        WaveDifficultyProfile profile = difficultyCalculator.calculate(waveIndex, selectedDifficulty, category, settings);

        // 4. Récupérer le template de catégorie
        WaveCategoryTemplate template = settings.getCategoryTemplates().get(category);
        if (template == null || !template.isEnabled()) {
            plugin.getLogger().warning("[ArenaGameplayService] Template manquant ou désactivé pour la catégorie "
                    + category + " (vague " + waveIndex + ")");
            return new WaveComposition(waveIndex, category, java.util.List.of());
        }

        // 5. Résoudre le pool (override éventuel)
        String poolId = template.getMobPoolId();
        if (override != null && override.isEnabled() && override.getForcedPoolId() != null) {
            poolId = override.getForcedPoolId();
        }

        MobPool pool = settings.getMobPools().get(poolId);
        if (pool == null || !pool.isEnabled()) {
            plugin.getLogger().warning("[ArenaGameplayService] Pool manquant ou désactivé : '"
                    + poolId + "' (vague " + waveIndex + ")");
            return new WaveComposition(waveIndex, category, java.util.List.of());
        }

        // 6. Composer la vague
        return composer.compose(waveIndex, profile, template, pool, override);
    }
}
