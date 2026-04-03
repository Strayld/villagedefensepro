package fr.obsidianlabs.plugins.villagedefense.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaAttemptEntry;
import fr.obsidianlabs.plugins.villagedefense.network.NetworkArenaEntry;
import fr.obsidianlabs.plugins.villagedefense.player.VDPlayer;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class MySQLDatabase implements Database {

    private final VillageDefensePlugin plugin;
    private HikariDataSource dataSource;

    public MySQLDatabase(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        FileConfiguration config = plugin.getConfig();
        String host = config.getString("database.host", "localhost");
        int port = config.getInt("database.port", 3306);
        String dbName = config.getString("database.name", "villagedefense");
        String username = config.getString("database.username", "root");
        String password = config.getString("database.password", "");
        int poolSize = config.getInt("database.pool-size", 10);

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
        hikariConfig.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + dbName +
                "?useSSL=false&autoReconnect=true&characterEncoding=utf8&useUnicode=true");
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.setMaximumPoolSize(poolSize);
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setConnectionTimeout(30000);
        hikariConfig.setIdleTimeout(600000);
        hikariConfig.setMaxLifetime(1800000);
        hikariConfig.setPoolName("VillageDefense-MySQL");

        this.dataSource = new HikariDataSource(hikariConfig);
        createTables();
        plugin.getLogger().info("[Database] MySQL initialisé sur " + host + ":" + port);
    }

    private void createTables() {
        String sql = """
                CREATE TABLE IF NOT EXISTS vd_players (
                    uuid VARCHAR(36) PRIMARY KEY,
                    name VARCHAR(16) NOT NULL,
                    coins BIGINT DEFAULT 0,
                    level INT DEFAULT 1,
                    total_xp BIGINT DEFAULT 0,
                    total_kills INT DEFAULT 0,
                    total_waves INT DEFAULT 0,
                    total_games INT DEFAULT 0,
                    total_coins_earned BIGINT DEFAULT 0,
                    unlocked_kits TEXT,
                    achievements TEXT
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """;
        String arenaStatsSql = """
                CREATE TABLE IF NOT EXISTS vd_arena_stats (
                    uuid     VARCHAR(36) NOT NULL,
                    arena_id VARCHAR(64) NOT NULL,
                    kills    INT DEFAULT 0,
                    waves    INT DEFAULT 0,
                    games    INT DEFAULT 0,
                    PRIMARY KEY (uuid, arena_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """;
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            stmt.execute(arenaStatsSql);
            ensureColumnExists(conn, "vd_players", "achievements", "TEXT");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Database] Erreur création tables MySQL", e);
        }
    }

    private void ensureColumnExists(Connection conn, String tableName, String columnName, String definition)
            throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getColumns(conn.getCatalog(), null, tableName, columnName)) {
            if (rs.next()) {
                return;
            }
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("[Database] Connexion MySQL fermée.");
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
            return new VDPlayer(uuid, name);
        });
    }

    @Override
    public CompletableFuture<Void> savePlayer(VDPlayer player) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                    INSERT INTO vd_players
                    (uuid, name, coins, level, total_xp, total_kills, total_waves, total_games, total_coins_earned, unlocked_kits, achievements)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                    name=VALUES(name), coins=VALUES(coins), level=VALUES(level),
                    total_xp=VALUES(total_xp), total_kills=VALUES(total_kills),
                    total_waves=VALUES(total_waves), total_games=VALUES(total_games),
                    total_coins_earned=VALUES(total_coins_earned), unlocked_kits=VALUES(unlocked_kits),
                    achievements=VALUES(achievements)
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

    // ── Sync réseau ───────────────────────────────────────────────────────────

    @Override
    public void initNetworkTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS vd_network_arenas (
                    server_id    VARCHAR(64)  NOT NULL,
                    arena_id     VARCHAR(64)  NOT NULL,
                    display_name VARCHAR(128) NOT NULL,
                    state        VARCHAR(32)  NOT NULL,
                    availability VARCHAR(16)  NOT NULL,
                    player_count INT          NOT NULL DEFAULT 0,
                    max_players  INT          NOT NULL DEFAULT 6,
                    current_wave INT          NOT NULL DEFAULT 0,
                    icon_material VARCHAR(64),
                    last_updated BIGINT       NOT NULL,
                    PRIMARY KEY (server_id, arena_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """;
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Database] Erreur création table vd_network_arenas", e);
        }
    }

    @Override
    public void upsertNetworkArena(NetworkArenaEntry entry) {
        String sql = """
                INSERT INTO vd_network_arenas
                (server_id, arena_id, display_name, state, availability,
                 player_count, max_players, current_wave, icon_material, last_updated)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    display_name=VALUES(display_name), state=VALUES(state),
                    availability=VALUES(availability), player_count=VALUES(player_count),
                    max_players=VALUES(max_players), current_wave=VALUES(current_wave),
                    icon_material=VALUES(icon_material), last_updated=VALUES(last_updated)
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entry.getServerId());
            ps.setString(2, entry.getArenaId());
            ps.setString(3, entry.getDisplayName());
            ps.setString(4, entry.getState());
            ps.setString(5, entry.getAvailability());
            ps.setInt(6, entry.getPlayerCount());
            ps.setInt(7, entry.getMaxPlayers());
            ps.setInt(8, entry.getCurrentWave());
            ps.setString(9, entry.getIconMaterial());
            ps.setLong(10, entry.getLastUpdated());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Database] Erreur upsert vd_network_arenas: " + e.getMessage());
        }
    }

    // ── Tokens de rejoin réseau ────────────────────────────────────────────

    @Override
    public void initRejoinTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS vd_player_rejoin (
                    player_uuid       VARCHAR(36)  NOT NULL PRIMARY KEY,
                    player_name       VARCHAR(16)  NOT NULL,
                    server_id         VARCHAR(64)  NOT NULL,
                    arena_id          VARCHAR(64)  NOT NULL,
                    arena_display_name VARCHAR(128) NOT NULL,
                    expiry_timestamp  BIGINT       NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """;
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Database] Erreur création table vd_player_rejoin", e);
        }
    }

    @Override
    public void saveRejoinToken(UUID playerUuid, String playerName,
                                String serverId, String arenaId,
                                String arenaDisplayName, long expiryTimestamp) {
        String sql = """
                INSERT INTO vd_player_rejoin
                (player_uuid, player_name, server_id, arena_id, arena_display_name, expiry_timestamp)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    player_name=VALUES(player_name), server_id=VALUES(server_id),
                    arena_id=VALUES(arena_id), arena_display_name=VALUES(arena_display_name),
                    expiry_timestamp=VALUES(expiry_timestamp)
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, playerName);
            ps.setString(3, serverId);
            ps.setString(4, arenaId);
            ps.setString(5, arenaDisplayName);
            ps.setLong(6, expiryTimestamp);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Database] Erreur sauvegarde rejoin token: " + e.getMessage());
        }
    }

    @Override
    public void deleteRejoinToken(UUID playerUuid) {
        String sql = "DELETE FROM vd_player_rejoin WHERE player_uuid = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Database] Erreur suppression rejoin token: " + e.getMessage());
        }
    }

    // ── Joins en attente depuis le Hub ────────────────────────────────────────

    @Override
    public void initHubPendingTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS vd_hub_pending (
                    player_uuid  VARCHAR(36) NOT NULL PRIMARY KEY,
                    arena_id     VARCHAR(64) NOT NULL,
                    action       VARCHAR(16) NOT NULL DEFAULT 'JOIN',
                    expires_at   BIGINT      NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """;
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Database] Erreur création table vd_hub_pending", e);
        }
    }

    @Override
    public void writeHubPending(UUID playerUuid, String arenaId,
                                String action, long expiryTimestamp) {
        String sql = """
                INSERT INTO vd_hub_pending (player_uuid, arena_id, action, expires_at)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    arena_id=VALUES(arena_id), action=VALUES(action), expires_at=VALUES(expires_at)
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, arenaId);
            ps.setString(3, action);
            ps.setLong(4, expiryTimestamp);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Database] Erreur writeHubPending: " + e.getMessage());
        }
    }

    @Override
    public HubPendingEntry consumeHubPending(UUID playerUuid) {
        String selectSql = "SELECT arena_id, action, expires_at FROM vd_hub_pending WHERE player_uuid = ?";
        String deleteSql = "DELETE FROM vd_hub_pending WHERE player_uuid = ?";
        try (Connection conn = dataSource.getConnection()) {
            HubPendingEntry entry = null;
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        long expiresAt = rs.getLong("expires_at");
                        if (System.currentTimeMillis() <= expiresAt) {
                            entry = new HubPendingEntry(
                                rs.getString("arena_id"),
                                rs.getString("action")
                            );
                        }
                    }
                }
            }
            // Supprimer dans tous les cas (expiré ou non)
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setString(1, playerUuid.toString());
                ps.executeUpdate();
            }
            return entry;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Database] Erreur consumeHubPending: " + e.getMessage());
            return null;
        }
    }

    @Override
    public void removeNetworkArenas(String serverId) {
        String sql = "DELETE FROM vd_network_arenas WHERE server_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, serverId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Database] Erreur suppression vd_network_arenas: " + e.getMessage());
        }
    }

    @Override
    public CompletableFuture<List<LeaderboardEntry>> getTopPlayers(int limit, String orderBy) {
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
        String col = switch (orderBy) {
            case "waves", "games" -> orderBy;
            default -> "kills";
        };
        return CompletableFuture.supplyAsync(() -> {
            List<LeaderboardEntry> list = new ArrayList<>();
            String sql = "SELECT p.name, s.kills, s.waves, s.games " +
                         "FROM vd_arena_stats s JOIN vd_players p ON s.uuid = p.uuid " +
                         "WHERE s.arena_id = ? ORDER BY s." + col + " DESC LIMIT ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, arenaId);
                ps.setInt(2, limit);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    list.add(new LeaderboardEntry(
                        rs.getString("name"),
                        rs.getInt("kills"),
                        rs.getInt("waves"),
                        rs.getInt("games"),
                        0L
                    ));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "[Database] Erreur leaderboard arène", e);
            }
            return list;
        });
    }

    @Override
    public void recordArenaKill(UUID uuid, String arenaId) {
        CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO vd_arena_stats (uuid, arena_id, kills) VALUES (?, ?, 1) " +
                         "ON DUPLICATE KEY UPDATE kills = kills + 1";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, arenaId);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "[Database] Erreur recordArenaKill", e);
            }
        });
    }

    @Override
    public void recordArenaWave(UUID uuid, String arenaId) {
        CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO vd_arena_stats (uuid, arena_id, waves) VALUES (?, ?, 1) " +
                         "ON DUPLICATE KEY UPDATE waves = waves + 1";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, arenaId);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "[Database] Erreur recordArenaWave", e);
            }
        });
    }

    @Override
    public void recordArenaGame(UUID uuid, String arenaId) {
        CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO vd_arena_stats (uuid, arena_id, games) VALUES (?, ?, 1) " +
                         "ON DUPLICATE KEY UPDATE games = games + 1";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, arenaId);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "[Database] Erreur recordArenaGame", e);
            }
        });
    }

    // ── Tentatives par arène ──────────────────────────────────────────────────

    @Override
    public void initAttemptTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS vd_arena_attempts (
                    player_uuid   VARCHAR(36) NOT NULL,
                    arena_id      VARCHAR(64) NOT NULL,
                    attempts_used INT         NOT NULL DEFAULT 1,
                    window_start  BIGINT      NOT NULL,
                    PRIMARY KEY (player_uuid, arena_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
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
            String sql = """
                    INSERT INTO vd_arena_attempts (player_uuid, arena_id, attempts_used, window_start)
                    VALUES (?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE attempts_used=VALUES(attempts_used), window_start=VALUES(window_start)
                    """;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, arenaId);
                ps.setInt(3, attemptsUsed);
                ps.setLong(4, windowStart);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "[Database] Erreur saveAttemptEntry: " + e.getMessage());
            }
        });
    }
}
