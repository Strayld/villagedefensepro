package fr.obsidianlabs.plugins.villagedefense.api;

import java.util.Locale;

public final class VillageDefenseApiIds {

    private VillageDefenseApiIds() {}

    public static String of(Enum<?> value) {
        return of(value.name());
    }

    public static String of(String raw) {
        return raw.toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
    }
}
