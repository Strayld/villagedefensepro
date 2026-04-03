package fr.obsidianlabs.plugins.villagedefense.api;

public record VillageDefenseArenaSnapshot(
    String arenaId,
    String displayName,
    String state,
    int currentWave,
    int playerCount,
    int spectatorCount,
    int maxPlayers,
    int villageLives,
    int activeMobCount,
    int pendingSpawnCount,
    int remainingMobCount
) {}
