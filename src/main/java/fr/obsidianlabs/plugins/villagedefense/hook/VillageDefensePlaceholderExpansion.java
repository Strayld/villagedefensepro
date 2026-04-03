package fr.obsidianlabs.plugins.villagedefense.hook;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.api.VillageDefenseApiIds;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaAvailability;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaState;
import fr.obsidianlabs.plugins.villagedefense.kit.KitType;
import fr.obsidianlabs.plugins.villagedefense.player.VDPlayer;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class VillageDefensePlaceholderExpansion extends PlaceholderExpansion {

    private static final List<String> ARENA_FIELDS = List.of(
        "display_name",
        "max_players",
        "village_lives",
        "active_mobs",
        "pending_spawns",
        "remaining_mobs",
        "availability",
        "spectatable",
        "spectators",
        "joinable",
        "players",
        "state",
        "wave",
        "name",
        "id"
    );

    private final VillageDefensePlugin plugin;

    public VillageDefensePlaceholderExpansion(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "villagedefense";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        String key = params.trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty()) {
            return "";
        }

        String global = resolveGlobal(key);
        if (global != null) {
            return global;
        }

        Player player = offlinePlayer != null ? offlinePlayer.getPlayer() : null;
        VDPlayer vdPlayer = offlinePlayer != null ? plugin.getPlayerManager().getPlayer(offlinePlayer.getUniqueId()) : null;

        String playerValue = resolvePlayer(key, player, vdPlayer);
        if (playerValue != null) {
            return playerValue;
        }

        String currentArenaValue = resolveCurrentArena(key, player, vdPlayer);
        if (currentArenaValue != null) {
            return currentArenaValue;
        }

        return resolveArenaById(key);
    }

    private String resolveGlobal(String key) {
        Collection<Arena> arenas = plugin.getArenaManager().getAllArenas();
        return switch (key) {
            case "plugin_version" -> plugin.getDescription().getVersion();
            case "api_version" -> plugin.getPublicApi() != null ? plugin.getPublicApi().getApiVersion() : "";
            case "arenas_total" -> Integer.toString(arenas.size());
            case "arenas_joinable" -> Long.toString(arenas.stream().filter(Arena::isJoinable).count());
            case "arenas_spectatable" -> Long.toString(arenas.stream().filter(Arena::isSpectatable).count());
            case "arenas_running" -> Long.toString(arenas.stream().filter(arena -> arena.getState() == ArenaState.RUNNING).count());
            case "arenas_active" -> Long.toString(arenas.stream().filter(arena -> arena.getAvailability() == ArenaAvailability.ACTIVE).count());
            case "arenas_test" -> Long.toString(arenas.stream().filter(arena -> arena.getAvailability() == ArenaAvailability.TEST).count());
            case "arenas_disabled" -> Long.toString(arenas.stream().filter(arena -> arena.getAvailability() == ArenaAvailability.DISABLED).count());
            default -> null;
        };
    }

    private String resolvePlayer(String key, Player player, VDPlayer vdPlayer) {
        if (!key.startsWith("player_")) {
            return null;
        }

        String playerArenaId = vdPlayer != null ? blankToEmpty(vdPlayer.getCurrentArenaId()) : "";
        KitType selectedKit = vdPlayer != null ? vdPlayer.getSelectedKit() : null;

        return switch (key) {
            case "player_in_arena" -> Boolean.toString(!playerArenaId.isEmpty()
                || (player != null && plugin.getArenaManager().getArenaOfParticipant(player) != null));
            case "player_spectating" -> Boolean.toString(player != null && plugin.getArenaManager().getArenaOfSpectator(player) != null);
            case "player_coins" -> longValue(vdPlayer != null ? vdPlayer.getCoins() : 0L);
            case "player_level" -> intValue(vdPlayer != null ? vdPlayer.getLevel() : 0);
            case "player_xp" -> longValue(vdPlayer != null ? vdPlayer.getTotalXp() : 0L);
            case "player_total_kills" -> intValue(vdPlayer != null ? vdPlayer.getTotalKills() : 0);
            case "player_total_waves" -> intValue(vdPlayer != null ? vdPlayer.getTotalWavesCompleted() : 0);
            case "player_total_games" -> intValue(vdPlayer != null ? vdPlayer.getTotalGamesPlayed() : 0);
            case "player_total_coins_earned" -> longValue(vdPlayer != null ? vdPlayer.getTotalCoinsEarned() : 0L);
            case "player_session_kills" -> intValue(vdPlayer != null ? vdPlayer.getSessionKills() : 0);
            case "player_session_coins" -> longValue(vdPlayer != null ? vdPlayer.getSessionCoins() : 0L);
            case "player_selected_kit", "player_selected_kit_id" -> selectedKit != null ? VillageDefenseApiIds.of(selectedKit) : "";
            case "player_selected_kit_name" -> selectedKit != null ? plugin.getMessages().format(selectedKit.getNameKey()) : "";
            case "player_current_arena", "player_current_arena_id" -> playerArenaId;
            case "player_current_arena_name" -> {
                Arena arena = resolveCurrentArena(player, vdPlayer);
                yield arena != null ? arena.getDisplayName() : "";
            }
            case "player_unlocked_kits_count" -> intValue(vdPlayer != null ? vdPlayer.getUnlockedKits().size() : 0);
            case "player_unlocked_achievements_count" -> intValue(vdPlayer != null ? vdPlayer.getCompletedAchievements().size() : 0);
            default -> null;
        };
    }

    private String resolveCurrentArena(String key, Player player, VDPlayer vdPlayer) {
        if (!key.startsWith("arena_")) {
            return null;
        }

        Arena arena = resolveCurrentArena(player, vdPlayer);
        if (arena == null) {
            return switch (key) {
                case "arena_id", "arena_name", "arena_display_name", "arena_state", "arena_wave",
                    "arena_players", "arena_spectators", "arena_max_players", "arena_village_lives",
                    "arena_active_mobs", "arena_pending_spawns", "arena_remaining_mobs",
                    "arena_availability", "arena_joinable", "arena_spectatable" -> "";
                default -> null;
            };
        }

        return switch (key) {
            case "arena_id" -> arena.getId();
            case "arena_name", "arena_display_name" -> arena.getDisplayName();
            case "arena_state" -> arena.getState().name().toLowerCase(Locale.ROOT);
            case "arena_wave" -> intValue(arena.getCurrentWave());
            case "arena_players" -> intValue(arena.getPlayerCount());
            case "arena_spectators" -> intValue(arena.getSpectatorCount());
            case "arena_max_players" -> intValue(arena.getMaxPlayers());
            case "arena_village_lives" -> intValue(arena.getVillageLives());
            case "arena_active_mobs" -> intValue(arena.getActiveMobCount());
            case "arena_pending_spawns" -> intValue(arena.getWavePendingSpawnCount());
            case "arena_remaining_mobs" -> intValue(arena.getWaveRemainingMobCount());
            case "arena_availability" -> arena.getAvailability().name().toLowerCase(Locale.ROOT);
            case "arena_joinable" -> Boolean.toString(arena.isJoinable());
            case "arena_spectatable" -> Boolean.toString(arena.isSpectatable());
            default -> null;
        };
    }

    private String resolveArenaById(String key) {
        if (!key.startsWith("arena_")) {
            return null;
        }

        String payload = key.substring("arena_".length());
        for (String field : ARENA_FIELDS) {
            String suffix = "_" + field;
            if (!payload.endsWith(suffix) || payload.length() <= suffix.length()) {
                continue;
            }

            String arenaId = payload.substring(0, payload.length() - suffix.length());
            Arena arena = plugin.getArenaManager().getArena(arenaId);
            if (arena == null) {
                return "";
            }

            return switch (field) {
                case "id" -> arena.getId();
                case "name", "display_name" -> arena.getDisplayName();
                case "state" -> arena.getState().name().toLowerCase(Locale.ROOT);
                case "wave" -> intValue(arena.getCurrentWave());
                case "players" -> intValue(arena.getPlayerCount());
                case "spectators" -> intValue(arena.getSpectatorCount());
                case "max_players" -> intValue(arena.getMaxPlayers());
                case "village_lives" -> intValue(arena.getVillageLives());
                case "active_mobs" -> intValue(arena.getActiveMobCount());
                case "pending_spawns" -> intValue(arena.getWavePendingSpawnCount());
                case "remaining_mobs" -> intValue(arena.getWaveRemainingMobCount());
                case "availability" -> arena.getAvailability().name().toLowerCase(Locale.ROOT);
                case "joinable" -> Boolean.toString(arena.isJoinable());
                case "spectatable" -> Boolean.toString(arena.isSpectatable());
                default -> null;
            };
        }

        return null;
    }

    private Arena resolveCurrentArena(Player player, VDPlayer vdPlayer) {
        if (player != null) {
            Arena arena = plugin.getArenaManager().getArenaOfParticipant(player);
            if (arena != null) {
                return arena;
            }
        }

        String currentArenaId = vdPlayer != null ? blankToEmpty(vdPlayer.getCurrentArenaId()) : "";
        return currentArenaId.isEmpty() ? null : plugin.getArenaManager().getArena(currentArenaId);
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String intValue(int value) {
        return Integer.toString(value);
    }

    private String longValue(long value) {
        return Long.toString(value);
    }
}
