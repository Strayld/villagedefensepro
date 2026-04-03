package fr.obsidianlabs.plugins.villagedefense.api;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.achievement.AchievementDefinition;
import fr.obsidianlabs.plugins.villagedefense.api.event.VillageDefenseProgressEvent;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.player.VDPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public class VillageDefenseApiService implements VillageDefenseApi {

    private static final String API_VERSION = "1.0.0";

    private final VillageDefensePlugin plugin;

    public VillageDefenseApiService(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        Bukkit.getServicesManager().register(VillageDefenseApi.class, this, plugin, ServicePriority.Normal);
    }

    public void unregister() {
        Bukkit.getServicesManager().unregister(VillageDefenseApi.class, this);
    }

    @Override
    public String getApiVersion() {
        return API_VERSION;
    }

    @Override
    public VillageDefensePlayerSnapshot getPlayerSnapshot(UUID playerId) {
        VDPlayer player = plugin.getPlayerManager().getPlayer(playerId);
        return player == null ? null : toPlayerSnapshot(player);
    }

    @Override
    public VillageDefenseArenaSnapshot getArenaSnapshot(String arenaId) {
        Arena arena = plugin.getArenaManager().getArena(arenaId);
        return arena == null ? null : toArenaSnapshot(arena);
    }

    @Override
    public Collection<VillageDefenseArenaSnapshot> getArenaSnapshots() {
        return plugin.getArenaManager().getAllArenas().stream()
            .map(this::toArenaSnapshot)
            .toList();
    }

    @Override
    public Collection<VillageDefenseAchievementDefinition> getAchievementDefinitions() {
        return plugin.getAchievementManager().getAll().stream()
            .map(def -> new VillageDefenseAchievementDefinition(
                VillageDefenseApiIds.of(def.getId()),
                def.getDisplayName(),
                def.getDescription(),
                def.getCondition().name(),
                def.getTarget(),
                def.getIcon()
            ))
            .toList();
    }

    @Override
    public boolean isPlayerInArena(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        return player != null && plugin.getArenaManager().getArenaOfParticipant(player) != null;
    }

    @Override
    public boolean isPlayerSpectating(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        return player != null && plugin.getArenaManager().getArenaOfSpectator(player) != null;
    }

    @Override
    public boolean grantCoins(UUID playerId, long amount) {
        if (amount <= 0) return false;
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return false;
        plugin.getPlayerManager().addCoins(player, amount, false);
        plugin.getPlayerManager().savePlayer(player);
        return true;
    }

    @Override
    public boolean grantXp(UUID playerId, long amount) {
        if (amount <= 0) return false;
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return false;
        plugin.getPlayerManager().awardXp(player, amount);
        plugin.getPlayerManager().savePlayer(player);
        return true;
    }

    @Override
    public boolean unlockAchievement(UUID playerId, String achievementId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return false;

        AchievementDefinition def = plugin.getAchievementManager().getById(achievementId);
        if (def == null) return false;

        return plugin.getPlayerManager().unlockAchievement(player, def, true);
    }

    public void fireProgress(
        Player player,
        Arena arena,
        VillageDefenseProgressType progressType,
        long amount,
        long total,
        String detail
    ) {
        if (player == null || !player.isOnline()) return;

        String arenaId = arena != null ? arena.getId() : "";
        int currentWave = arena != null ? arena.getCurrentWave() : 0;

        Bukkit.getPluginManager().callEvent(new VillageDefenseProgressEvent(
            player,
            arenaId,
            progressType,
            amount,
            total,
            currentWave,
            detail == null ? "" : detail
        ));
    }

    private VillageDefensePlayerSnapshot toPlayerSnapshot(VDPlayer player) {
        Set<String> unlockedKits = player.getUnlockedKits().stream()
            .map(VillageDefenseApiIds::of)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<String> unlockedAchievements = player.getCompletedAchievements().stream()
            .map(VillageDefenseApiIds::of)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

        return new VillageDefensePlayerSnapshot(
            player.getUuid(),
            player.getName(),
            player.getCoins(),
            player.getLevel(),
            player.getTotalXp(),
            player.getTotalKills(),
            player.getTotalWavesCompleted(),
            player.getTotalGamesPlayed(),
            player.getTotalCoinsEarned(),
            player.getSessionKills(),
            player.getSessionCoins(),
            VillageDefenseApiIds.of(player.getSelectedKit()),
            player.getCurrentArenaId(),
            unlockedKits,
            unlockedAchievements
        );
    }

    private VillageDefenseArenaSnapshot toArenaSnapshot(Arena arena) {
        return new VillageDefenseArenaSnapshot(
            arena.getId(),
            arena.getDisplayName(),
            arena.getState().name(),
            arena.getCurrentWave(),
            arena.getPlayerCount(),
            arena.getSpectatorCount(),
            arena.getMaxPlayers(),
            arena.getVillageLives(),
            arena.getActiveMobCount(),
            arena.getWavePendingSpawnCount(),
            arena.getWaveRemainingMobCount()
        );
    }
}
