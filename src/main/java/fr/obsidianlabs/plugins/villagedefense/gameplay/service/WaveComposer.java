package fr.obsidianlabs.plugins.villagedefense.gameplay.service;

import fr.obsidianlabs.plugins.villagedefense.gameplay.model.*;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Construit la composition finale d'une vague à partir des settings calculés.
 */
public class WaveComposer {

    private final Logger logger;

    public WaveComposer(Logger logger) {
        this.logger = logger;
    }

    public WaveComposition compose(
            int waveIndex,
            WaveDifficultyProfile profile,
            WaveCategoryTemplate template,
            MobPool pool,
            WaveOverride override) {

        // Mode de composition (override si présent)
        CompositionMode mode = template.getCompositionMode();
        if (override != null && override.isEnabled() && override.getForcedCompositionMode() != null) {
            mode = override.getForcedCompositionMode();
        }

        // Filtrer les mobs valides pour cette vague
        List<MobEntry> validEntries = pool.getEntries().stream()
                .filter(e -> e.isValidForWave(waveIndex))
                .collect(Collectors.toList());

        if (validEntries.isEmpty()) {
            logger.warning("[WaveComposer] Aucun mob valide dans le pool '" + pool.getId()
                    + "' pour la vague " + waveIndex + ". Vague ignorée.");
            return new WaveComposition(waveIndex, profile.getCategory(), List.of());
        }

        // Limiter la variété selon maxMobTypes
        int maxTypes = Math.min(profile.getMaxMobTypes(), validEntries.size());

        List<WaveMobSelection> selections = switch (mode) {
            case WEIGHTED -> composeWeighted(waveIndex, profile, validEntries, maxTypes);
            case RANDOM   -> composeRandom(waveIndex, profile, validEntries, maxTypes);
            case FIXED    -> composeFixed(validEntries, maxTypes);
        };

        return new WaveComposition(waveIndex, profile.getCategory(), selections);
    }

    // ---- Modes de composition ----

    private List<WaveMobSelection> composeWeighted(
            int waveIndex,
            WaveDifficultyProfile profile,
            List<MobEntry> entries,
            int maxTypes) {

        List<MobEntry> chosen = weightedSample(entries, maxTypes);
        return buildSelections(chosen, profile.getTotalBudget());
    }

    private List<WaveMobSelection> composeRandom(
            int waveIndex,
            WaveDifficultyProfile profile,
            List<MobEntry> entries,
            int maxTypes) {

        List<MobEntry> shuffled = new ArrayList<>(entries);
        Collections.shuffle(shuffled);
        List<MobEntry> chosen = shuffled.subList(0, Math.min(maxTypes, shuffled.size()));
        return buildSelections(chosen, profile.getTotalBudget());
    }

    private List<WaveMobSelection> composeFixed(List<MobEntry> entries, int maxTypes) {
        List<WaveMobSelection> result = new ArrayList<>();
        int count = 0;
        for (MobEntry e : entries) {
            if (count >= maxTypes) break;
            int amount = e.getMinAmount();
            result.add(new WaveMobSelection(e, amount));
            count++;
        }
        return result;
    }

    // ---- Helpers ----

    /**
     * Tirage pondéré sans remise pour sélectionner {@code count} types différents.
     */
    private List<MobEntry> weightedSample(List<MobEntry> entries, int count) {
        List<MobEntry> pool = new ArrayList<>(entries);
        List<MobEntry> result = new ArrayList<>();
        Random rng = new Random();

        for (int i = 0; i < count && !pool.isEmpty(); i++) {
            int totalWeight = pool.stream().mapToInt(MobEntry::getWeight).sum();
            int roll = rng.nextInt(totalWeight);
            int cumul = 0;
            MobEntry chosen = pool.get(0);
            for (MobEntry e : pool) {
                cumul += e.getWeight();
                if (roll < cumul) {
                    chosen = e;
                    break;
                }
            }
            result.add(chosen);
            pool.remove(chosen);
        }
        return result;
    }

    /**
     * Construit les sélections finales en remplissant le budget avec les mobs choisis.
     */
    private List<WaveMobSelection> buildSelections(List<MobEntry> chosen, double budget) {
        List<WaveMobSelection> result = new ArrayList<>();
        double remaining = budget;
        Random rng = new Random();

        for (MobEntry e : chosen) {
            if (remaining <= 0) break;

            int amount = e.getMinAmount() + rng.nextInt(Math.max(1, e.getMaxAmount() - e.getMinAmount() + 1));
            double cost = amount * e.getThreat();

            // Ajuster si on dépasse trop le budget (max 20% de dépassement)
            if (cost > remaining * 1.2 && e.getThreat() > 0) {
                amount = Math.max(e.getMinAmount(), (int)(remaining / e.getThreat()));
                cost = amount * e.getThreat();
            }

            if (amount > 0) {
                result.add(new WaveMobSelection(e, amount));
                remaining -= cost;
            }
        }
        return result;
    }
}
