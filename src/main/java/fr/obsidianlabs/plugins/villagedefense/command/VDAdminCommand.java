package fr.obsidianlabs.plugins.villagedefense.command;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaSetup;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaState;
import fr.obsidianlabs.plugins.villagedefense.player.VDPlayer;
import fr.obsidianlabs.plugins.villagedefense.tower.TowerSchematic;
import fr.obsidianlabs.plugins.villagedefense.util.ChatUtil;
import fr.obsidianlabs.plugins.villagedefense.util.Perm;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /vdadmin - Commande d'administration
 */
public class VDAdminCommand implements CommandExecutor, TabCompleter {

    private final VillageDefensePlugin plugin;

    public VDAdminCommand(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!plugin.hasPermission(sender, Perm.ADMIN)) {
            ChatUtil.send(sender, plugin.getMessages().format("general.no-permission"));
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player player) {
                plugin.getVdAdminGui().open(player);
            } else {
                sendAdminHelp(sender);
            }
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create"     -> cmdCreate(sender, args);
            case "delete"     -> cmdDelete(sender, args);
            case "setup"      -> cmdSetup(sender, args);
            case "reload"     -> cmdReload(sender);
            case "forcestart" -> cmdForceStart(sender, args);
            case "forcestop"  -> cmdForceStop(sender, args);
            case "tp"         -> cmdTp(sender, args);
            case "info"       -> cmdInfo(sender, args);
            case "coins"      -> cmdCoins(sender, args);
            case "setwave"    -> cmdSetWave(sender, args);
            case "setlevel"   -> cmdSetLevel(sender, args);
            default           -> sendAdminHelp(sender);
        }

        return true;
    }

    /** Vérifie un nœud de permission spécifique et envoie le message si refusé. */
    private boolean checkPerm(CommandSender sender, String node) {
        if (plugin.hasPermission(sender, node)) return true;
        ChatUtil.send(sender, plugin.getMessages().format("general.no-permission"));
        return false;
    }

    private void cmdCreate(CommandSender sender, String[] args) {
        if (!checkPerm(sender, Perm.ADMIN_ARENA)) return;
        if (args.length < 2) {
            ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.usage-create"));
            return;
        }
        String id = args[1];
        if (plugin.getArenaManager().arenaExists(id)) {
            ChatUtil.send(sender, plugin.getMessages().get("arena.already-exists", id));
            return;
        }
        plugin.getArenaManager().createArena(id);
        ChatUtil.send(sender, plugin.getMessages().get("arena.created", id));
    }

    private void cmdDelete(CommandSender sender, String[] args) {
        if (!checkPerm(sender, Perm.ADMIN_ARENA)) return;
        if (args.length < 2) {
            ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.usage-delete"));
            return;
        }
        Arena arena = getArena(sender, args[1]);
        if (arena == null) return;

        plugin.getArenaManager().deleteArena(args[1]);
        ChatUtil.send(sender, plugin.getMessages().get("arena.deleted", args[1]));
    }

    private void cmdSetup(CommandSender sender, String[] args) {
        if (!checkPerm(sender, Perm.ADMIN_SETUP)) return;
        if (!(sender instanceof Player player)) {
            ChatUtil.send(sender, plugin.getMessages().format("general.player-only"));
            return;
        }

        if (args.length < 2) {
            ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.usage-setup"));
            return;
        }

        // Sous-commandes textuelles (compatibilité ancienne méthode)
        if (args[1].equalsIgnoreCase("exit")) {
            ArenaSetup setup = plugin.getArenaManager().getSetup(player);
            if (setup != null) {
                plugin.getArenaManager().endSetup(player);
                ChatUtil.send(player, plugin.getMessages().get("setup.mode-disabled"));
            }
            return;
        }

        Arena arena = getArena(sender, args[1]);
        if (arena == null) return;

        if (plugin.getArenaManager().isInSetup(player)) {
            // Déjà en setup → rouvrir le GUI de la même arène ou de la nouvelle
            ArenaSetup existing = plugin.getArenaManager().getSetup(player);
            if (existing != null && existing.getArena().getId().equals(arena.getId())) {
                plugin.getArenaSetupGui().open(player, arena);
                return;
            }
            plugin.getArenaManager().endSetup(player);
        }

        ArenaSetup setup = plugin.getArenaManager().startSetup(player, arena);
        setup.giveWand();
        plugin.getArenaSetupGui().open(player, arena);
    }

    private void cmdReload(CommandSender sender) {
        if (!checkPerm(sender, Perm.ADMIN_RELOAD)) return;
        plugin.reloadConfig();
        plugin.getMessages().reload();
        TowerSchematic.reload(plugin);
        ChatUtil.send(sender, plugin.getMessages().get("general.reload"));
    }

    private void cmdCoins(CommandSender sender, String[] args) {
        if (!checkPerm(sender, Perm.ADMIN_PLAYER)) return;
        if (args.length < 3) {
            ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.usage-coins"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.player-not-found", args[1]));
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.invalid-amount", args[2]));
            return;
        }

        VDPlayer vdp = plugin.getPlayerManager().getPlayer(target);
        if (vdp == null) {
            ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.player-data-error", target.getName()));
            return;
        }

        vdp.addCoins(amount);
        ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.coins-given", amount, target.getName()));
        ChatUtil.send(target, plugin.getMessages().format("admin.cmd.coins-received", amount));
    }

    private void cmdSetWave(CommandSender sender, String[] args) {
        if (!checkPerm(sender, Perm.ADMIN_GAME)) return;
        if (args.length < 3) {
            ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.usage-setwave"));
            return;
        }
        Arena arena = getArena(sender, args[1]);
        if (arena == null) return;

        if (arena.getState() != ArenaState.RUNNING && arena.getState() != ArenaState.BETWEEN_WAVES) {
            ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.arena-not-running", arena.getId()));
            return;
        }

        int wave;
        try {
            wave = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.invalid-wave", args[2]));
            return;
        }
        if (wave < 0) {
            ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.wave-too-low"));
            return;
        }

        // Tuer les mobs de la vague en cours et annuler le timer inter-vague
        plugin.getWaveManager().killAllMobs(arena);
        arena.setCurrentWave(wave);

        ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.wave-set", arena.getId(), wave));
        // Démarrer la prochaine vague (wave+1) immédiatement
        plugin.getWaveManager().startNextWave(arena);
    }

    private void cmdSetLevel(CommandSender sender, String[] args) {
        if (!checkPerm(sender, Perm.ADMIN_PLAYER)) return;
        if (args.length < 3) {
            ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.usage-setlevel"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.player-not-found", args[1]));
            return;
        }

        int level;
        try {
            level = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.invalid-level", args[2]));
            return;
        }
        if (level < 1) {
            ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.level-too-low"));
            return;
        }

        VDPlayer vdp = plugin.getPlayerManager().getPlayer(target);
        if (vdp == null) {
            ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.player-data-error", target.getName()));
            return;
        }

        vdp.setLevelWithXp(level);
        // Débloquer automatiquement tout ce qui est désormais accessible
        plugin.getPlayerManager().checkAndGrantUnlocks(target, vdp, level);

        ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.level-set", target.getName(), level));
        ChatUtil.send(target, plugin.getMessages().format("admin.cmd.level-set-target", level));
    }

    private void cmdForceStart(CommandSender sender, String[] args) {
        if (!checkPerm(sender, Perm.ADMIN_GAME)) return;
        if (args.length < 2) {
            ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.usage-forcestart"));
            return;
        }
        Arena arena = getArena(sender, args[1]);
        if (arena == null) return;

        plugin.getGameManager().startCountdown(arena);
        ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.forcestart", arena.getId()));
    }

    private void cmdForceStop(CommandSender sender, String[] args) {
        if (!checkPerm(sender, Perm.ADMIN_GAME)) return;
        if (args.length < 2) {
            ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.usage-forcestop"));
            return;
        }
        Arena arena = getArena(sender, args[1]);
        if (arena == null) return;

        plugin.getGameManager().endGame(arena, false);
        ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.forcestop", arena.getId()));
    }

    private void cmdTp(CommandSender sender, String[] args) {
        if (!checkPerm(sender, Perm.ADMIN_GAME)) return;
        if (!(sender instanceof Player player)) {
            ChatUtil.send(sender, plugin.getMessages().format("general.player-only"));
            return;
        }
        if (args.length < 2) {
            ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.usage-tp"));
            return;
        }
        Arena arena = getArena(sender, args[1]);
        if (arena == null) return;
        if (arena.getLobbyLocation() == null) {
            ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.no-lobby"));
            return;
        }
        player.teleport(arena.getLobbyLocation());
        ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.teleported", arena.getId()));
    }

    private void cmdInfo(CommandSender sender, String[] args) {
        if (!checkPerm(sender, Perm.ADMIN_GAME)) return;
        if (args.length < 2) {
            ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.usage-info"));
            return;
        }
        Arena arena = getArena(sender, args[1]);
        if (arena == null) return;

        ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.info-header", arena.getId()));
        ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.info-name", arena.getDisplayName()));
        ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.info-state", arena.getState()));
        ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.info-players", arena.getPlayerCount(), arena.getMaxPlayers()));
        ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.info-wave", arena.getCurrentWave()));
        ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.info-lives", arena.getVillageLives()));
        ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.info-mobs", arena.getActiveMobCount()));
        ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.info-towers", arena.getTowerSlots().size()));
        ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.info-spawns", arena.getMobSpawnPoints().size()));
        ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.info-setup", arena.isSetup() ? "&a✓" : "&c✗"));
    }

    private Arena getArena(CommandSender sender, String id) {
        Arena arena = plugin.getArenaManager().getArena(id);
        if (arena == null) {
            ChatUtil.send(sender, plugin.getMessages().get("arena.not-found", id));
        }
        return arena;
    }

    private void sendAdminHelp(CommandSender sender) {
        ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.help-header"));
        ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.help-create"));
        ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.help-delete"));
        ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.help-setup"));
        ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.help-setup-save"));
        ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.help-setup-info"));
        ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.help-setup-exit"));
        ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.help-forcestart"));
        ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.help-forcestop"));
        ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.help-tp"));
        ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.help-info"));
        ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.help-coins"));
        ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.help-setwave"));
        ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.help-setlevel"));
        ChatUtil.send(sender, plugin.getMessages().format("admin.cmd.help-reload"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!plugin.hasPermission(sender, Perm.ADMIN)) return List.of();

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(List.of("create", "delete", "setup", "reload",
                "forcestart", "forcestop", "tp", "info", "coins", "setwave", "setlevel"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "delete", "forcestart", "forcestop", "tp", "info", "setwave" ->
                    plugin.getArenaManager().getAllArenas().forEach(a -> completions.add(a.getId()));
                case "setup" -> {
                    plugin.getArenaManager().getAllArenas().forEach(a -> completions.add(a.getId()));
                    completions.addAll(List.of("save", "info", "exit"));
                }
                case "coins", "setlevel" ->
                    Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("coins"))
                completions.addAll(List.of("100", "500", "1000"));
            else if (args[0].equalsIgnoreCase("setwave"))
                completions.addAll(List.of("1", "5", "10", "15", "20"));
            else if (args[0].equalsIgnoreCase("setlevel"))
                completions.addAll(List.of("1", "3", "5", "7", "10", "15", "20"));
        }

        return completions.stream()
            .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
            .toList();
    }
}
