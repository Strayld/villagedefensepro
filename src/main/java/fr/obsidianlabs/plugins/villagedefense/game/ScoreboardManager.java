package fr.obsidianlabs.plugins.villagedefense.game;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.player.VDPlayer;
import fr.obsidianlabs.plugins.villagedefense.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Scoreboard latéral personnel par joueur.
 * - Mode LOBBY (WAITING / STARTING) : arène, joueurs, état
 * - Mode GAME  (RUNNING / BETWEEN_WAVES) : vague, mobs, vies, pièces, respawn
 * - Mode SPECTATEUR : vague, mobs, vies, joueurs
 */
class ScoreboardManager {

    // Entrées invisibles pour le scoreboard de jeu (10 lignes)
    private static final String[] ENTRIES = {
        "§0§r", "§1§r", "§2§r", "§3§r",
        "§4§r", "§5§r", "§6§r", "§7§r", "§f§r", "§8§r"
    };

    // Entrées invisibles pour le scoreboard de lobby (7 lignes)
    private static final String[] LOBBY_ENTRIES = {
        "§8§r", "§9§r", "§a§r", "§b§r", "§c§r", "§d§r", "§e§r"
    };

    // Entrées invisibles pour le scoreboard spectateur (8 lignes)
    private static final String[] SPEC_ENTRIES = {
        "§0§0§r", "§1§1§r", "§2§2§r", "§3§3§r",
        "§4§4§r", "§5§5§r", "§6§6§r", "§7§7§r"
    };

    private final VillageDefensePlugin plugin;
    private final Map<UUID, Scoreboard> boards      = new HashMap<>();
    private final Map<UUID, Scoreboard> lobbyBoards = new HashMap<>();
    private final Map<UUID, Scoreboard> specBoards  = new HashMap<>();
    private int cachedMaxLives;

    ScoreboardManager(VillageDefensePlugin plugin) {
        this.plugin = plugin;
        reloadConfig();
    }

    /** Appelé au rechargement pour rafraîchir les valeurs depuis config.yml. */
    void reloadConfig() {
        cachedMaxLives = plugin.getConfig().getInt("game.village-lives", 10);
    }

    // ── Lobby ─────────────────────────────────────────────────────────────────

    void assignLobby(Player player, Arena arena) {
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        var msg = plugin.getMessages();
        Objective obj = sb.registerNewObjective(
            "vdl", "dummy",
            ChatUtil.color(msg.format(player, "scoreboard.title"))
        );
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        for (int i = 0; i < LOBBY_ENTRIES.length; i++) {
            Team team = sb.registerNewTeam("lb" + i);
            team.addEntry(LOBBY_ENTRIES[i]);
            Score score = obj.getScore(LOBBY_ENTRIES[i]);
            score.setScore(i + 1);
        }

        lobbyBoards.put(player.getUniqueId(), sb);
        player.setScoreboard(sb);
        updateLobby(player, arena);
    }

    void updateLobby(Player player, Arena arena) {
        Scoreboard sb = lobbyBoards.get(player.getUniqueId());
        if (sb == null) return;

        VDPlayer vdp = plugin.getPlayerManager().getPlayer(player);
        int level = vdp != null ? vdp.getLevel() : 1;

        var msg = plugin.getMessages();
        String stateStr = switch (arena.getState()) {
            case WAITING  -> msg.format(player, "scoreboard.lobby-waiting");
            case STARTING -> msg.format(player, "scoreboard.lobby-starting", arena.getCountdownSeconds());
            default       -> "&7...";
        };

        setLobbyLine(sb, 6, msg.format(player, "scoreboard.separator"));
        setLobbyLine(sb, 5, msg.format(player, "scoreboard.lobby-arena", arena.getDisplayName()));
        setLobbyLine(sb, 4, msg.format(player, "scoreboard.lobby-players", arena.getPlayerCount(), arena.getMaxPlayers()));
        setLobbyLine(sb, 3, msg.format(player, "scoreboard.lobby-state", stateStr));
        setLobbyLine(sb, 2, msg.format(player, "scoreboard.lobby-min", arena.getMinPlayers()));
        setLobbyLine(sb, 1, msg.format(player, "scoreboard.lobby-level", level));
        setLobbyLine(sb, 0, msg.format(player, "scoreboard.separator"));
    }

    // ── Partie ────────────────────────────────────────────────────────────────

    /** Remplace le scoreboard de lobby par le scoreboard de partie. */
    void assign(Player player, Arena arena) {
        lobbyBoards.remove(player.getUniqueId());

        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        var msg = plugin.getMessages();
        Objective obj = sb.registerNewObjective(
            "vd", "dummy",
            ChatUtil.color(msg.format(player, "scoreboard.title"))
        );
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        for (int i = 0; i < ENTRIES.length; i++) {
            Team team = sb.registerNewTeam("l" + i);
            team.addEntry(ENTRIES[i]);
            Score score = obj.getScore(ENTRIES[i]);
            score.setScore(i + 1);
        }

        boards.put(player.getUniqueId(), sb);
        player.setScoreboard(sb);
        update(player, arena, -1);
    }

    void update(Player player, Arena arena, int respawnSeconds) {
        Scoreboard sb = boards.get(player.getUniqueId());
        if (sb == null) return;

        VDPlayer vdp  = plugin.getPlayerManager().getPlayer(player);
        long coins    = vdp != null ? vdp.getCoins() : 0;
        int  level    = vdp != null ? vdp.getLevel()  : 1;

        var msg = plugin.getMessages();
        String state = switch (arena.getState()) {
            case BETWEEN_WAVES -> msg.format(player, "scoreboard.state-between-waves", arena.getBetweenWaveSeconds());
            case RUNNING       -> msg.format(player, "scoreboard.state-running", Math.max(1, arena.getCurrentWave()));
            case STARTING      -> msg.format(player, "scoreboard.state-starting", arena.getCountdownSeconds());
            default            -> msg.format(player, "scoreboard.state-waiting");
        };

        setLine(sb, 9, msg.format(player, "scoreboard.separator"));
        setLine(sb, 8, msg.format(player, "scoreboard.sb-wave", Math.max(1, arena.getCurrentWave())));
        setLine(sb, 7, msg.format(player, "scoreboard.sb-state", state));
        setLine(sb, 6, msg.format(player, "scoreboard.sb-mobs", arena.getWaveRemainingMobCount()));
        setLine(sb, 5, msg.format(player, "scoreboard.sb-lives", arena.getVillageLives(), cachedMaxLives));
        setLine(sb, 4, msg.format(player, "scoreboard.sb-players", arena.getPlayerCount(), arena.getMaxPlayers()));
        setLine(sb, 3, msg.format(player, "scoreboard.separator"));
        setLine(sb, 2, msg.format(player, "scoreboard.sb-coins", coins));
        setLine(sb, 1, msg.format(player, "scoreboard.sb-level", level));
        setLine(sb, 0, respawnSeconds > 0
            ? msg.format(player, "scoreboard.sb-respawn", respawnSeconds)
            : msg.format(player, "scoreboard.separator"));
    }

    // ── Spectateur ────────────────────────────────────────────────────────────

    void assignSpectator(Player player, Arena arena) {
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        var msg = plugin.getMessages();
        Objective obj = sb.registerNewObjective(
            "vds", "dummy",
            ChatUtil.color(msg.format(player, "scoreboard.title"))
        );
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        for (int i = 0; i < SPEC_ENTRIES.length; i++) {
            Team team = sb.registerNewTeam("sp" + i);
            team.addEntry(SPEC_ENTRIES[i]);
            Score score = obj.getScore(SPEC_ENTRIES[i]);
            score.setScore(i + 1);
        }

        specBoards.put(player.getUniqueId(), sb);
        player.setScoreboard(sb);
        updateSpectator(player, arena);
    }

    void updateSpectator(Player player, Arena arena) {
        Scoreboard sb = specBoards.get(player.getUniqueId());
        if (sb == null) return;

        var msg = plugin.getMessages();
        String state = switch (arena.getState()) {
            case BETWEEN_WAVES -> msg.format(player, "scoreboard.state-between-waves", arena.getBetweenWaveSeconds());
            case RUNNING       -> msg.format(player, "scoreboard.state-running", Math.max(1, arena.getCurrentWave()));
            default            -> msg.format(player, "scoreboard.state-waiting");
        };

        setSpecLine(sb, 7, msg.format(player, "scoreboard.separator"));
        setSpecLine(sb, 6, msg.format(player, "scoreboard.spec-label"));
        setSpecLine(sb, 5, msg.format(player, "scoreboard.sb-wave", Math.max(1, arena.getCurrentWave())));
        setSpecLine(sb, 4, msg.format(player, "scoreboard.sb-state", state));
        setSpecLine(sb, 3, msg.format(player, "scoreboard.sb-mobs", arena.getWaveRemainingMobCount()));
        setSpecLine(sb, 2, msg.format(player, "scoreboard.sb-lives", arena.getVillageLives(), cachedMaxLives));
        setSpecLine(sb, 1, msg.format(player, "scoreboard.sb-players", arena.getPlayerCount(), arena.getMaxPlayers()));
        setSpecLine(sb, 0, msg.format(player, "scoreboard.separator"));
    }

    // ── Suppression ──────────────────────────────────────────────────────────

    /** Retire les scoreboards lobby, partie ET spectateur du joueur. */
    void remove(Player player) {
        boards.remove(player.getUniqueId());
        lobbyBoards.remove(player.getUniqueId());
        specBoards.remove(player.getUniqueId());
        if (player.isOnline()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setLine(Scoreboard sb, int index, String text) {
        Team team = sb.getTeam("l" + index);
        if (team == null) return;
        team.setPrefix(ChatUtil.color(text));
        team.setSuffix("");
    }

    private void setLobbyLine(Scoreboard sb, int index, String text) {
        Team team = sb.getTeam("lb" + index);
        if (team == null) return;
        team.setPrefix(ChatUtil.color(text));
        team.setSuffix("");
    }

    private void setSpecLine(Scoreboard sb, int index, String text) {
        Team team = sb.getTeam("sp" + index);
        if (team == null) return;
        team.setPrefix(ChatUtil.color(text));
        team.setSuffix("");
    }
}
