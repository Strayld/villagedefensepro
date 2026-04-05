package fr.obsidianlabs.plugins.villagedefense.gameplay.service;

import fr.obsidianlabs.plugins.villagedefense.gameplay.model.ArenaGameplaySettings;
import fr.obsidianlabs.plugins.villagedefense.gameplay.model.WaveCategory;
import fr.obsidianlabs.plugins.villagedefense.gameplay.model.WaveScalingSettings;

/**
 * Détermine la catégorie d'une vague selon son index.
 * Priorité : SPECIAL > BOSS > ELITE > NORMAL
 */
public class WaveCategoryResolver {

    public WaveCategory resolveCategory(int waveIndex, ArenaGameplaySettings settings) {
        WaveScalingSettings scaling = settings.getWaveScalingSettings();

        int specialInterval = scaling.getSpecialInterval();
        int bossInterval    = scaling.getBossInterval();
        int eliteInterval   = scaling.getEliteInterval();

        if (specialInterval > 0 && waveIndex % specialInterval == 0) return WaveCategory.SPECIAL;
        if (bossInterval    > 0 && waveIndex % bossInterval    == 0) return WaveCategory.BOSS;
        if (eliteInterval   > 0 && waveIndex % eliteInterval   == 0) return WaveCategory.ELITE;

        return WaveCategory.NORMAL;
    }
}
