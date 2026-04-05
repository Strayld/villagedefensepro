package fr.obsidianlabs.plugins.villagedefense.gameplay.service;

import fr.obsidianlabs.plugins.villagedefense.gameplay.model.*;

/**
 * Calcule le profil final de difficulté d'une vague.
 *
 * Formules :
 *   budget  = baseBudget + (wave * linearGrowth) + (wave² * exponentialGrowth)
 *   budget *= categoryBudgetMultiplier
 *
 *   health = (baseHealth + wave * healthPerWave) * categoryHealth * difficultyHealth
 *   damage = (baseDamage + wave * damagePerWave) * categoryDamage * difficultyDamage
 *   speed  = (baseSpeed  + wave * speedPerWave)  * categorySpeed  * difficultySpeed
 */
public class WaveDifficultyCalculator {

    public WaveDifficultyProfile calculate(
            int waveIndex,
            GameDifficulty selectedDifficulty,
            WaveCategory category,
            ArenaGameplaySettings settings) {

        WaveScalingSettings scaling      = settings.getWaveScalingSettings();
        DifficultyProfile diffProfile    = settings.getDifficultySettings().getProfile(selectedDifficulty);
        WaveCategoryTemplate catTemplate = settings.getCategoryTemplates().get(category);

        // Fallback si le template de catégorie est absent
        double catBudgetMult  = catTemplate != null ? catTemplate.getCategoryBudgetMultiplier() : 1.0;
        double catHealthMult  = catTemplate != null ? catTemplate.getHealthMultiplier()         : 1.0;
        double catDamageMult  = catTemplate != null ? catTemplate.getDamageMultiplier()         : 1.0;
        double catSpeedMult   = catTemplate != null ? catTemplate.getSpeedMultiplier()          : 1.0;

        // Budget
        double budget = scaling.getBaseBudget()
                + (waveIndex * scaling.getLinearGrowth())
                + ((double) waveIndex * waveIndex * scaling.getExponentialGrowth());
        budget *= catBudgetMult;

        // Stats
        double health = (scaling.getBaseHealthMultiplier() + waveIndex * scaling.getHealthPerWave())
                * catHealthMult
                * diffProfile.getHealthMultiplier();

        double damage = (scaling.getBaseDamageMultiplier() + waveIndex * scaling.getDamagePerWave())
                * catDamageMult
                * diffProfile.getDamageMultiplier();

        double speed  = (scaling.getBaseSpeedMultiplier()  + waveIndex * scaling.getSpeedPerWave())
                * catSpeedMult
                * diffProfile.getSpeedMultiplier();

        double reward = diffProfile.getRewardMultiplier();

        // Max mob types selon le palier de jeu
        int maxMobTypes;
        if (waveIndex >= scaling.getLateGameStartWave()) {
            maxMobTypes = scaling.getLateGameMaxMobTypes();
        } else if (waveIndex >= scaling.getMidGameStartWave()) {
            maxMobTypes = scaling.getMidGameMaxMobTypes();
        } else {
            maxMobTypes = scaling.getEarlyGameMaxMobTypes();
        }
        if (catTemplate != null) {
            maxMobTypes = Math.min(maxMobTypes, catTemplate.getMaxMobTypes());
        }

        return new WaveDifficultyProfile(waveIndex, category, budget, health, damage, speed, reward, maxMobTypes);
    }
}
