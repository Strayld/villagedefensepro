package fr.obsidianlabs.plugins.villagedefense.api;

import java.util.Collection;
import java.util.UUID;

/**
 * API publique de VillageDefensePro à récupérer via Bukkit ServicesManager.
 */
public interface VillageDefenseApi {

    String getApiVersion();

    VillageDefensePlayerSnapshot getPlayerSnapshot(UUID playerId);

    VillageDefenseArenaSnapshot getArenaSnapshot(String arenaId);

    Collection<VillageDefenseArenaSnapshot> getArenaSnapshots();

    Collection<VillageDefenseAchievementDefinition> getAchievementDefinitions();

    boolean isPlayerInArena(UUID playerId);

    boolean isPlayerSpectating(UUID playerId);

    boolean grantCoins(UUID playerId, long amount);

    boolean grantXp(UUID playerId, long amount);

    boolean unlockAchievement(UUID playerId, String achievementId);
}
