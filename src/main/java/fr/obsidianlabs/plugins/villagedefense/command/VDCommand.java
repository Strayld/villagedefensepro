package fr.obsidianlabs.plugins.villagedefense.command;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaState;
import fr.obsidianlabs.plugins.villagedefense.player.VDPlayer;
import fr.obsidianlabs.plugins.villagedefense.util.ChatUtil;
import fr.obsidianlabs.plugins.villagedefense.util.Perm;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /vd - Commande principale joueur
 */
public class VDCommand implements CommandExecutor, TabCompleter {

    private final VillageDefensePlugin plugin;

    public VDCommand(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatUtil.send(sender, plugin.getMessages().format("general.player-only"));
            return true;
        }

        if (!plugin.hasPermission(player, Perm.PLAY)) {
            ChatUtil.send(player, plugin.getMessages().format("general.no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "join"   -> cmdJoin(player, args);
            case "rejoin" -> cmdRejoin(player);
            case "leave"  -> cmdLeave(player);
            case "kit"    -> cmdKit(player);
            case "shop"   -> cmdShop(player);
            case "stats"  -> cmdStats(player);
            case "list"   -> cmdList(player);
            case "top"    -> cmdTop(player);
            case "help"   -> sendHelp(player);
            default -> ChatUtil.send(player, plugin.getMessages().format("general.unknown-command"));
        }

        return true;
    }

    private void cmdJoin(Player player, String[] args) {
        if (plugin.getArenaManager().getArenaOfParticipant(player) != null) {
            ChatUtil.send(player, plugin.getMessages().get("arena.already-in-arena"));
            return;
        }

        if (args.length < 2) {
            // Si une seule arène disponible, rejoindre automatiquement
            var arenas = plugin.getArenaManager().getAllArenas().stream()
                .filter(Arena::isSetup)
                .filter(a -> plugin.getArenaManager().canPlayerJoinArena(player, a))
                .toList();
            if (arenas.size() == 1) {
                joinArena(player, arenas.get(0));
            } else if (arenas.isEmpty()) {
                ChatUtil.send(player, plugin.getMessages().get("arena.none-available"));
            } else {
                ChatUtil.send(player, plugin.getMessages().get("arena.multiple-available"));
                cmdList(player);
            }
            return;
        }

        Arena arena = plugin.getArenaManager().getArena(args[1]);
        if (arena == null) {
            ChatUtil.send(player, plugin.getMessages().get("arena.not-found", args[1]));
            return;
        }
        joinArena(player, arena);
    }

    private void joinArena(Player player, Arena arena) {
        if (!arena.isSetup()) {
            ChatUtil.send(player, plugin.getMessages().get("arena.not-setup", arena.getId()));
            return;
        }
        if (arena.getState() == ArenaState.RESETTING) {
            ChatUtil.send(player, plugin.getMessages().get("arena.resetting-click"));
            return;
        }
        if (!plugin.getArenaManager().canPlayerAccessArena(player, arena)) {
            sendAvailabilityDenied(player, arena);
            return;
        }
        // Partie en cours → spectateur
        if (arena.isSpectatable()) {
            plugin.getPerkManager().prepareForArenaEntry(player, arena, false);
            plugin.getPlayerManager().prepareForLobby(player);
            var vdp = plugin.getPlayerManager().getPlayer(player);
            if (vdp != null) {
                vdp.setReturnLocation(player.getLocation().clone());
                vdp.setCurrentArenaId(arena.getId());
            }
            plugin.getArenaManager().joinAsSpectator(player, arena);
            ChatUtil.send(player, plugin.getMessages().get("arena.spectating", arena.getDisplayName()));
            return;
        }
        if (!arena.isJoinWindowOpen()) {
            sendJoinUnavailable(player, arena);
            return;
        }

        plugin.getPerkManager().prepareForArenaEntry(player, arena, false);
        plugin.getPlayerManager().prepareForLobby(player);
        plugin.getArenaManager().joinArena(player, arena);
        plugin.getPlayerManager().getPlayer(player).setCurrentArenaId(arena.getId());
        ChatUtil.send(player, plugin.getMessages().get("arena.joined", arena.getDisplayName()));
        plugin.getGameManager().checkAndStart(arena);
    }

    private void cmdRejoin(Player player) {
        if (plugin.getArenaManager().getArenaOfPlayer(player) != null) {
            ChatUtil.send(player, plugin.getMessages().get("arena.already-in-arena"));
            return;
        }
        if (!plugin.getGameManager().hasValidRejoinToken(player)) {
            ChatUtil.send(player, plugin.getMessages().get("arena.rejoin-expired"));
            return;
        }
        String arenaId = plugin.getGameManager().getRejoinArenaId(player);
        Arena arena = plugin.getArenaManager().getArena(arenaId);
        if (arena == null || arena.getState() != ArenaState.RUNNING) {
            plugin.getGameManager().clearRejoinToken(player);
            ChatUtil.send(player, plugin.getMessages().get("arena.rejoin-expired"));
            return;
        }
        plugin.getPerkManager().prepareForArenaEntry(player, arena, true);
        plugin.getPlayerManager().prepareForLobby(player);
        if (!plugin.getArenaManager().rejoinArena(player, arena)) {
            ChatUtil.send(player, plugin.getMessages().get("arena.rejoin-failed"));
            return;
        }
        VDPlayer vdp = plugin.getPlayerManager().getPlayer(player);
        if (vdp != null) vdp.setCurrentArenaId(arena.getId());
        plugin.getGameManager().clearRejoinToken(player);
        ChatUtil.send(player, plugin.getMessages().get("arena.rejoined", arena.getDisplayName()));
    }

    private void cmdLeave(Player player) {
        if (plugin.getArenaManager().getArenaOfParticipant(player) == null) {
            ChatUtil.send(player, plugin.getMessages().get("arena.not-in-arena"));
            return;
        }
        plugin.getArenaManager().leaveArena(player);
        VDPlayer vdp = plugin.getPlayerManager().getPlayer(player);
        if (vdp != null) vdp.setCurrentArenaId(null);
        ChatUtil.send(player, plugin.getMessages().get("arena.left"));
    }

    private void cmdKit(Player player) {
        plugin.getKitGui().open(player);
    }

    private void cmdShop(Player player) {
        Arena arena = plugin.getArenaManager().getArenaOfPlayer(player);
        if (arena == null) {
            ChatUtil.send(player, plugin.getMessages().get("arena.not-in-arena"));
            return;
        }
        plugin.getShopGui().open(player);
    }

    private void cmdStats(Player player) {
        VDPlayer vdp = plugin.getPlayerManager().getPlayer(player);
        if (vdp == null) return;
        ChatUtil.send(player, plugin.getMessages().format("player.stats-header"));
        ChatUtil.send(player, plugin.getMessages().format("player.stats-level", vdp.getLevel()));
        ChatUtil.send(player, plugin.getMessages().format("player.stats-kills", vdp.getTotalKills()));
        ChatUtil.send(player, plugin.getMessages().format("player.stats-waves", vdp.getTotalWavesCompleted()));
        ChatUtil.send(player, plugin.getMessages().format("player.stats-games", vdp.getTotalGamesPlayed()));
        ChatUtil.send(player, plugin.getMessages().format("player.stats-coins", vdp.getTotalCoinsEarned()));
    }

    private void cmdList(Player player) {
        ChatUtil.send(player, plugin.getMessages().format("arena.list-header"));
        var arenas = plugin.getArenaManager().getAllArenas().stream()
            .filter(a -> plugin.getArenaManager().shouldShowArena(player, a))
            .toList();
        if (arenas.isEmpty()) {
            ChatUtil.send(player, plugin.getMessages().format("arena.list-empty"));
            return;
        }
        for (Arena arena : arenas) {
            ChatUtil.send(player, plugin.getMessages().format("arena.list-entry",
                arena.getDisplayName(),
                arena.getPlayerCount(), arena.getMaxPlayers(),
                arena.getCurrentWave(),
                arena.getState().name(),
                getAvailabilitySuffix(arena)));
        }
    }

    private void cmdTop(Player player) {
        plugin.getLeaderboardGui().open(player, "total_kills");
    }

    private void sendHelp(Player player) {
        ChatUtil.send(player, plugin.getMessages().format("command.help-header"));
        ChatUtil.send(player, plugin.getMessages().format("command.help-join"));
        ChatUtil.send(player, plugin.getMessages().format("command.help-rejoin"));
        ChatUtil.send(player, plugin.getMessages().format("command.help-leave"));
        ChatUtil.send(player, plugin.getMessages().format("command.help-kit"));
        ChatUtil.send(player, plugin.getMessages().format("command.help-shop"));
        ChatUtil.send(player, plugin.getMessages().format("command.help-stats"));
        ChatUtil.send(player, plugin.getMessages().format("command.help-list"));
        ChatUtil.send(player, plugin.getMessages().format("command.help-top"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(List.of("join", "rejoin", "leave", "kit", "shop", "stats", "list", "top", "help"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
            if (sender instanceof Player player) {
                plugin.getArenaManager().getAllArenas().stream()
                    .filter(Arena::isSetup)
                    .filter(a -> plugin.getArenaManager().shouldShowArena(player, a))
                    .map(Arena::getId)
                    .forEach(completions::add);
            }
        }
        return completions.stream()
            .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
            .toList();
    }

    private void sendAvailabilityDenied(Player player, Arena arena) {
        String key = arena.isTestMode() ? "arena.test-only" : "arena.disabled";
        ChatUtil.send(player, plugin.getMessages().get(key, arena.getDisplayName()));
    }

    private void sendJoinUnavailable(Player player, Arena arena) {
        if (arena.getPlayerCount() >= arena.getMaxPlayers()) {
            ChatUtil.send(player, plugin.getMessages().get("arena.full",
                arena.getId(), arena.getPlayerCount(), arena.getMaxPlayers()));
            return;
        }
        if (arena.getState() == ArenaState.RESETTING) {
            ChatUtil.send(player, plugin.getMessages().get("arena.resetting-click"));
            return;
        }
        ChatUtil.send(player, plugin.getMessages().get("arena.game-already-started"));
    }

    private String getAvailabilitySuffix(Arena arena) {
        return arena.isTestMode()
            ? plugin.getMessages().format("arena.list-availability-test")
            : "";
    }
}
