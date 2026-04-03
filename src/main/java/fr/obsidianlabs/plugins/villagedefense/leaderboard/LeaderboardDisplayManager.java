package fr.obsidianlabs.plugins.villagedefense.leaderboard;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.compat.FloatingText;
import fr.obsidianlabs.plugins.villagedefense.database.LeaderboardEntry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LeaderboardDisplayManager {

    private static final int DISPLAY_LIMIT = 5;
    private static final long REFRESH_INTERVAL_TICKS = 20L * 10L;
    private static final String[] ROTATION = {
        "total_kills",
        "total_waves",
        "total_games"
    };

    private final VillageDefensePlugin plugin;
    private final Map<String, FloatingText> displays = new HashMap<>();

    private BukkitTask refreshTask;
    private int rotationIndex = 0;
    private boolean refreshInFlight = false;

    public LeaderboardDisplayManager(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        refreshTask = plugin.getServer().getScheduler()
            .runTaskTimer(plugin, this::refreshNow, 40L, REFRESH_INTERVAL_TICKS);
    }

    public void shutdown() {
        if (refreshTask != null) {
            refreshTask.cancel();
        }
        removeAll();
    }

    public void refreshNow() {
        if (refreshInFlight || plugin.getArenaManager() == null) {
            return;
        }

        List<Arena> globalArenas = plugin.getArenaManager().getAllArenas().stream()
            .filter(Arena::hasLeaderboardDisplay)
            .toList();
        List<Arena> arenaArenas = plugin.getArenaManager().getAllArenas().stream()
            .filter(Arena::hasArenaLeaderboardDisplay)
            .toList();

        cleanupOrphans(globalArenas, arenaArenas);
        if (globalArenas.isEmpty() && arenaArenas.isEmpty()) {
            return;
        }

        if (plugin.getDatabase() == null || !plugin.getDatabase().isConnected()) {
            String unavailableText = buildUnavailableText();
            for (Arena arena : globalArenas) updateDisplay(arena.getId() + "_global", arena.getLeaderboardLocation(), unavailableText);
            for (Arena arena : arenaArenas)  updateDisplay(arena.getId() + "_arena",  arena.getArenaLeaderboardLocation(), unavailableText);
            return;
        }

        String category = ROTATION[rotationIndex];
        rotationIndex = (rotationIndex + 1) % ROTATION.length;
        refreshInFlight = true;

        // Refresh global leaderboards
        plugin.getDatabase().getTopPlayers(DISPLAY_LIMIT, category).whenComplete((entries, throwable) ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                refreshInFlight = false;
                if (!plugin.isEnabled()) return;

                String text = throwable != null ? buildUnavailableText()
                    : buildText(plugin.getMessages().format("gui.leaderboard.hologram-title"), category, entries != null ? entries : List.of());

                for (Arena arena : plugin.getArenaManager().getAllArenas().stream()
                        .filter(Arena::hasLeaderboardDisplay).toList()) {
                    updateDisplay(arena.getId() + "_global", arena.getLeaderboardLocation(), text);
                }
            })
        );

        // Refresh per-arena leaderboards
        for (Arena arena : arenaArenas) {
            final Arena a = arena;
            plugin.getDatabase().getTopPlayersByArena(DISPLAY_LIMIT, category, arena.getId())
                .whenComplete((entries, throwable) ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!plugin.isEnabled()) return;
                        String title = plugin.getMessages().format("gui.leaderboard.hologram-title-arena", a.getDisplayName());
                        String text = throwable != null ? buildUnavailableText()
                            : buildText(title, category, entries != null ? entries : List.of());
                        updateDisplay(a.getId() + "_arena", a.getArenaLeaderboardLocation(), text);
                    })
                );
        }
    }

    private void updateDisplay(String key, Location location, String text) {
        if (location == null || location.getWorld() == null) {
            removeDisplay(key);
            return;
        }
        displays.computeIfAbsent(key, k -> new FloatingText()).update(location, text);
    }

    private void cleanupOrphans(List<Arena> globalArenas, List<Arena> arenaArenas) {
        Set<String> activeKeys = new java.util.HashSet<>();
        globalArenas.forEach(a -> activeKeys.add(a.getId() + "_global"));
        arenaArenas.forEach(a -> activeKeys.add(a.getId() + "_arena"));

        for (String key : List.copyOf(displays.keySet())) {
            if (!activeKeys.contains(key)) {
                removeDisplay(key);
            }
        }
    }

    private void removeDisplay(String arenaId) {
        FloatingText display = displays.remove(arenaId);
        if (display != null) {
            display.remove();
        }
    }

    private void removeAll() {
        for (FloatingText display : displays.values()) {
            display.remove();
        }
        displays.clear();
    }

    private String buildText(String title, String category, List<LeaderboardEntry> entries) {
        StringBuilder builder = new StringBuilder();
        builder.append(title);
        builder.append('\n');
        builder.append(plugin.getMessages().format("gui.leaderboard.hologram-subtitle",
            DISPLAY_LIMIT, categoryLabel(category)));

        if (entries.isEmpty()) {
            builder.append('\n');
            builder.append(plugin.getMessages().format("gui.leaderboard.hologram-empty"));
            return builder.toString();
        }

        for (int i = 0; i < entries.size() && i < DISPLAY_LIMIT; i++) {
            LeaderboardEntry entry = entries.get(i);
            builder.append('\n');
            builder.append(plugin.getMessages().format("gui.leaderboard.hologram-entry",
                i + 1,
                entry.name(),
                categoryValue(category, entry)));
        }
        return builder.toString();
    }

    private String buildUnavailableText() {
        return plugin.getMessages().format("gui.leaderboard.hologram-title")
            + "\n"
            + plugin.getMessages().format("gui.leaderboard.hologram-unavailable");
    }

    private String categoryLabel(String category) {
        return switch (category) {
            case "total_waves" -> plugin.getMessages().format("gui.leaderboard.category-waves");
            case "total_games" -> plugin.getMessages().format("gui.leaderboard.category-games");
            default -> plugin.getMessages().format("gui.leaderboard.category-kills");
        };
    }

    private String categoryValue(String category, LeaderboardEntry entry) {
        return switch (category) {
            case "total_waves" -> plugin.getMessages().format("gui.leaderboard.hologram-value-waves", entry.totalWaves());
            case "total_games" -> plugin.getMessages().format("gui.leaderboard.hologram-value-games", entry.totalGames());
            default -> plugin.getMessages().format("gui.leaderboard.hologram-value-kills", entry.totalKills());
        };
    }
}
