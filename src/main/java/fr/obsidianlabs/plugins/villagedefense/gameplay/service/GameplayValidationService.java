package fr.obsidianlabs.plugins.villagedefense.gameplay.service;

import fr.obsidianlabs.plugins.villagedefense.gameplay.model.*;

import java.util.ArrayList;
import java.util.List;

public class GameplayValidationService {

    public List<String> validate(ArenaGameplaySettings settings) {
        List<String> errors = new ArrayList<>();

        DifficultySettings diff = settings.getDifficultySettings();
        if (diff.getEnabledDifficulties().isEmpty()) {
            errors.add("Aucune difficulté activée.");
        }
        if (!diff.getEnabledDifficulties().contains(diff.getDefaultDifficulty())) {
            errors.add("La difficulté par défaut '" + diff.getDefaultDifficulty() + "' n'est pas dans la liste activée.");
        }
        for (GameDifficulty d : diff.getEnabledDifficulties()) {
            if (!diff.getProfiles().containsKey(d)) {
                errors.add("Profil manquant pour la difficulté " + d + ".");
            }
        }

        errors.addAll(validateScaling(settings.getWaveScalingSettings()));

        for (WaveCategoryTemplate t : settings.getCategoryTemplates().values()) {
            errors.addAll(validateTemplate(t));
            if (t.isEnabled() && t.getMobPoolId() != null
                    && !settings.getMobPools().containsKey(t.getMobPoolId())) {
                errors.add("Template " + t.getCategory() + " référence un pool inexistant : " + t.getMobPoolId());
            }
        }

        for (MobPool pool : settings.getMobPools().values()) {
            errors.addAll(validatePool(pool));
        }

        for (WaveOverride override : settings.getWaveOverrides().values()) {
            errors.addAll(validateOverride(override, settings));
        }

        return errors;
    }

    public List<String> validateScaling(WaveScalingSettings s) {
        List<String> errors = new ArrayList<>();
        if (s.getBaseBudget() <= 0)            errors.add("baseBudget doit être > 0.");
        if (s.getLinearGrowth() < 0)           errors.add("linearGrowth doit être >= 0.");
        if (s.getExponentialGrowth() < 0)      errors.add("exponentialGrowth doit être >= 0.");
        if (s.getMidGameStartWave() < 1)       errors.add("midGameStartWave doit être >= 1.");
        if (s.getLateGameStartWave() < s.getMidGameStartWave())
            errors.add("lateGameStartWave doit être >= midGameStartWave.");
        return errors;
    }

    public List<String> validateTemplate(WaveCategoryTemplate t) {
        List<String> errors = new ArrayList<>();
        if (t.isEnabled() && (t.getMobPoolId() == null || t.getMobPoolId().isBlank()))
            errors.add("Template " + t.getCategory() + " : mobPoolId manquant.");
        if (t.getCategoryBudgetMultiplier() <= 0) errors.add("Template " + t.getCategory() + " : categoryBudgetMultiplier doit être > 0.");
        if (t.getHealthMultiplier() <= 0)         errors.add("Template " + t.getCategory() + " : healthMultiplier doit être > 0.");
        if (t.getDamageMultiplier() <= 0)         errors.add("Template " + t.getCategory() + " : damageMultiplier doit être > 0.");
        if (t.getSpeedMultiplier() <= 0)          errors.add("Template " + t.getCategory() + " : speedMultiplier doit être > 0.");
        if (t.getMaxMobTypes() < 1)               errors.add("Template " + t.getCategory() + " : maxMobTypes doit être >= 1.");
        return errors;
    }

    public List<String> validatePool(MobPool pool) {
        List<String> errors = new ArrayList<>();
        if (pool.getId() == null || pool.getId().isBlank())          errors.add("Pool : id manquant.");
        if (pool.getDisplayName() == null || pool.getDisplayName().isBlank()) errors.add("Pool '" + pool.getId() + "' : displayName manquant.");
        if (pool.getEntries() == null)                               errors.add("Pool '" + pool.getId() + "' : entries null.");
        else {
            for (MobEntry e : pool.getEntries()) errors.addAll(validateEntry(pool.getId(), e));
        }
        return errors;
    }

    public List<String> validateEntry(String poolId, MobEntry e) {
        List<String> errors = new ArrayList<>();
        if (e.getId() == null || e.getId().isBlank())   errors.add("Pool '" + poolId + "' entrée : id manquant.");
        if (e.getSource() == null)                      errors.add("Pool '" + poolId + "' entrée '" + e.getId() + "' : source nulle.");
        if (e.getWeight() <= 0)                         errors.add("Pool '" + poolId + "' entrée '" + e.getId() + "' : weight doit être > 0.");
        if (e.getThreat() <= 0)                         errors.add("Pool '" + poolId + "' entrée '" + e.getId() + "' : threat doit être > 0.");
        if (e.getMinAmount() < 1)                       errors.add("Pool '" + poolId + "' entrée '" + e.getId() + "' : minAmount doit être >= 1.");
        if (e.getMaxAmount() < e.getMinAmount())        errors.add("Pool '" + poolId + "' entrée '" + e.getId() + "' : maxAmount < minAmount.");
        if (e.getMinWave() < 1)                         errors.add("Pool '" + poolId + "' entrée '" + e.getId() + "' : minWave doit être >= 1.");
        if (e.getMaxWave() != 0 && e.getMaxWave() < e.getMinWave())
            errors.add("Pool '" + poolId + "' entrée '" + e.getId() + "' : maxWave < minWave.");
        return errors;
    }

    public List<String> validateOverride(WaveOverride o, ArenaGameplaySettings settings) {
        List<String> errors = new ArrayList<>();
        if (o.getWaveIndex() < 1) errors.add("Override : waveIndex doit être >= 1.");
        if (o.getForcedPoolId() != null && !settings.getMobPools().containsKey(o.getForcedPoolId()))
            errors.add("Override vague " + o.getWaveIndex() + " : pool '" + o.getForcedPoolId() + "' inexistant.");
        if (o.getHealthMultiplierOverride() != null && o.getHealthMultiplierOverride() <= 0)
            errors.add("Override vague " + o.getWaveIndex() + " : healthMultiplierOverride doit être > 0.");
        if (o.getDamageMultiplierOverride() != null && o.getDamageMultiplierOverride() <= 0)
            errors.add("Override vague " + o.getWaveIndex() + " : damageMultiplierOverride doit être > 0.");
        if (o.getSpeedMultiplierOverride() != null && o.getSpeedMultiplierOverride() <= 0)
            errors.add("Override vague " + o.getWaveIndex() + " : speedMultiplierOverride doit être > 0.");
        return errors;
    }
}
