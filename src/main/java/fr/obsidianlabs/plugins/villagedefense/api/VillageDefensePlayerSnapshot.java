package fr.obsidianlabs.plugins.villagedefense.api;

import java.util.Set;
import java.util.UUID;

public record VillageDefensePlayerSnapshot(
    UUID uuid,
    String name,
    long coins,
    int level,
    long totalXp,
    int totalKills,
    int totalWavesCompleted,
    int totalGamesPlayed,
    long totalCoinsEarned,
    int sessionKills,
    long sessionCoins,
    String selectedKitId,
    String currentArenaId,
    Set<String> unlockedKitIds,
    Set<String> unlockedAchievementIds
) {}
