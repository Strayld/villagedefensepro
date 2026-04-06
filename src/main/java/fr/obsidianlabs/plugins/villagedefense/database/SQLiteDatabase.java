package fr.obsidianlabs.plugins.villagedefense.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaAttemptEntry;
import fr.obsidianlabs.plugins.villagedefense.player.VDPlayer;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class SQLiteDatabase implements Database {

    private final VillageDefensePlugin plugin;
    private HikariDataSource dataSource;

    public SQLiteDatabase(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        File dbFile = new File(plugin.getDataFolder(), "data");

        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.h2.Driver");
        config.setJdbcUrl("jdbc:h2:file:" + dbFile.getAbsolutePath() + ";AUTO_SERVER=FALSE;DB_CLOSE_ON_EXIT=FALSE");
        config.setMaximumPoolSize(3);
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("VillageDefense-H2");

        this.dataSource = new HikariDataSource(config);
        createTables();
        plugin.getLogger().info("[Database] H2 initialisé.");
    }

    private void createTables() {
        String sql = """
                CREATE TABLE IF NOT EXISTS vd_players (
                    uuid TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    coins INTEGER DEFAULT 0,
                    level INTEGER DEFAULT 1,
                    total_xp INTEGER DEFAULT 0,
                    total_kills INTEGER DEFAULT 0,
                    total_waves INTEGER DEFAULT 0,
                    total_games INTEGER DEFAULT 0,
                    total_coins_earned INTEGER DEFAULT 0,
                    unlocked_kits TEXT DEFAULT 'GUARDIAN',
                    achievements TEXT DEFAULT ''
                );
                """;
        String arenaStatsSql = """
                CREATE TABLE IF NOT EXISTS vd_arena_stats (
                    uuid     TEXT NOT NULL,
                    arena_id TEXT NOT NULL,
                    kills    INTEGER DEFAULT 0,
                    waves    INTEGER DEFAULT 0,
                    games    INTEGER DEFAULT 0,
                    PRIMARY KEY (uuid, arena_id)
                );
                """;
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            stmt.execute(arenaStatsSql);
            // Migration: add column if it doesn't exist yet (older DB)
            stmt.execute("ALTER TABLE vd_players ADD COLUMN IF NOT EXISTS achievements TEXT DEFAULT ''");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Database] Erreur création tables", e);
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("[Database] Connexion H2 locale fermée.");
        }
    }

    @Override
    public CompletableFuture<VDPlayer> loadPlayer(UUID uuid, String name) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM vd_players WHERE uuid = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    VDPlayer player = new VDPlayer(uuid, rs.getString("name"));
                    player.setCoins(rs.getLong("coins"));
                    player.setLevel(rs.getInt("level"));
                    player.setTotalXp(rs.getLong("total_xp"));
                    player.setTotalKills(rs.getInt("total_kills"));
                    player.setTotalWavesCompleted(rs.getInt("total_waves"));
                    player.setTotalGamesPlayed(rs.getInt("total_games"));
                    player.setTotalCoinsEarned(rs.getLong("total_coins_earned"));
                    player.deserializeKits(rs.getString("unlocked_kits"));
                    player.deserializeAchievements(rs.getString("achievements"));
                    return player;
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "[Database] Erreur chargement joueur " + uuid, e);
            }
            // Nouveau joueur
            VDPlayer newPlayer = new VDPlayer(uuid, name);
            return newPlayer;
        });
    }

    @Override
    public CompletableFuture<Void> savePlayer(VDPlayer player) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                    MERGE INTO vd_players (uuid, name, coins, level, total_xp, total_kills, total_waves, total_games, total_coins_earned, unlocked_kits, achievements)
                    KEY(uuid)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, player.getUuid().toString());
                ps.setString(2, player.getName());
                ps.setLong(3, player.getCoins());
                ps.setInt(4, player.getLevel());
                ps.setLong(5, player.getTotalXp());
                ps.setInt(6, player.getTotalKills());
                ps.setInt(7, player.getTotalWavesCompleted());
                ps.setInt(8, player.getTotalGamesPlayed());
                ps.setLong(9, player.getTotalCoinsEarned());
                ps.setString(10, player.serializeKits());
                ps.setString(11, player.serializeAchievements());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "[Database] Erreur sauvegarde joueur " + player.getUuid(), e);
            }
        });
    }

    @Override
    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    @Override
    public CompletableFuture<List<LeaderboardEntry>> getTopPlayers(int limit, String orderBy) {
        // Whitelist pour éviter toute injection SQL
        String col = switch (orderBy) {
            case "total_waves", "total_games", "total_coins_earned" -> orderBy;
            default -> "total_kills";
        };
        return CompletableFuture.supplyAsync(() -> {
            List<LeaderboardEntry> list = new ArrayList<>();
            String sql = "SELECT name, total_kills, total_waves, total_games, total_coins_earned " +
                         "FROM vd_players ORDER BY " + col + " DESC LIMIT ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, limit);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    list.add(new LeaderboardEntry(
                        rs.getString("name"),
                        rs.getInt("total_kills"),
                        rs.getInt("total_waves"),
                        rs.getInt("total_games"),
                        rs.getLong("total_coins_earned")
                    ));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "[Database] Erreur leaderboard", e);
            }
            return list;
        });
    }

    @Override
    public CompletableFuture<List<LeaderboardEntry>> getTopPlayersByArena(int limit, String orderBy, String arenaId) {
        return CompletableFuture.completedFuture(List.of());
    }

    // ── Tentatives par arène ──────────────────────────────────────────────────

    @Override
    public void initAttemptTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS vd_arena_attempts (
                    player_uuid   TEXT    NOT NULL,
                    arena_id      TEXT    NOT NULL,
                    attempts_used INTEGER NOT NULL DEFAULT 1,
                    window_start  INTEGER NOT NULL,
                    PRIMARY KEY (player_uuid, arena_id)
                );
                """;
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Database] Erreur création table vd_arena_attempts", e);
        }
    }

    @Override
    public CompletableFuture<Map<String, ArenaAttemptEntry>> loadAttemptEntries(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, ArenaAttemptEntry> map = new HashMap<>();
            String sql = "SELECT arena_id, attempts_used, window_start FROM vd_arena_attempts WHERE player_uuid = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    map.put(rs.getString("arena_id"),
                            new ArenaAttemptEntry(rs.getInt("attempts_used"), rs.getLong("window_start")));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "[Database] Erreur loadAttemptEntries: " + e.getMessage());
            }
            return map;
        });
    }

    @Override
    public void saveAttemptEntry(UUID uuid, String arenaId, int attemptsUsed, long windowStart) {
        CompletableFuture.runAsync(() -> {
            String insert = "INSERT INTO vd_arena_attempts (player_uuid, arena_id, attempts_used, window_start) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING";
            String update = "UPDATE vd_arena_attempts SET attempts_used = ?, window_start = ? WHERE player_uuid = ? AND arena_id = ?";
            try (Connection conn = dataSource.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(insert)) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, arenaId);
                    ps.setInt(3, attemptsUsed);
                    ps.setLong(4, windowStart);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(update)) {
                    ps.setInt(1, attemptsUsed);
                    ps.setLong(2, windowStart);
                    ps.setString(3, uuid.toString());
                    ps.setString(4, arenaId);
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "[Database] Erreur saveAttemptEntry: " + e.getMessage());
            }
        });
    }
}
