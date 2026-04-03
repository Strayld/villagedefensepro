package fr.obsidianlabs.plugins.villagedefense.database;

/**
 * Entrée du classement global (retournée par Database#getTopPlayers).
 */
public record LeaderboardEntry(
    String name,
    int    totalKills,
    int    totalWaves,
    int    totalGames,
    long   totalCoinsEarned
) {}
