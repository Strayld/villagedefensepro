package fr.obsidianlabs.plugins.villagedefense.api;

import org.bukkit.Material;

public record VillageDefenseAchievementDefinition(
    String id,
    String nameKey,
    String descriptionKey,
    String condition,
    long target,
    Material icon
) {}
