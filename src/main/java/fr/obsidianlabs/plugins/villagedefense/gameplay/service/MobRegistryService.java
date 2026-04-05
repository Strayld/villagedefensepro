package fr.obsidianlabs.plugins.villagedefense.gameplay.service;

import fr.obsidianlabs.plugins.villagedefense.gameplay.model.MobSource;
import fr.obsidianlabs.plugins.villagedefense.gameplay.provider.MobOption;
import fr.obsidianlabs.plugins.villagedefense.gameplay.provider.MobProvider;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Agrège toutes les sources de mobs disponibles et alimente la MobSelectionListGUI.
 */
public class MobRegistryService {

    private final List<MobProvider> providers = new ArrayList<>();

    public void registerProvider(MobProvider provider) {
        providers.add(provider);
    }

    public List<MobOption> getAllAvailableMobs() {
        return providers.stream()
                .flatMap(p -> p.getAvailableMobs().stream())
                .collect(Collectors.toList());
    }

    public List<MobOption> getAvailableMobs(MobSource source) {
        return providers.stream()
                .filter(p -> p.getSource() == source)
                .flatMap(p -> p.getAvailableMobs().stream())
                .collect(Collectors.toList());
    }

    public Optional<MobProvider> getProvider(MobSource source) {
        return providers.stream().filter(p -> p.getSource() == source).findFirst();
    }

    public List<MobProvider> getProviders() {
        return Collections.unmodifiableList(providers);
    }
}
