package fr.obsidianlabs.plugins.villagedefense.listener;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaState;
import fr.obsidianlabs.plugins.villagedefense.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;

/**
 * Gère les panneaux d'arène :
 *  - Clic droit → rejoindre (lobby) ou regarder (partie en cours)
 *  - Mise à jour périodique du contenu (toutes les 0.5 s)
 */
public class SignListener implements Listener {

    private final VillageDefensePlugin plugin;

    public SignListener(VillageDefensePlugin plugin) {
        this.plugin = plugin;
        startSignUpdateTask();
    }

    // ---- Clic droit sur un panneau ----

    @EventHandler(priority = EventPriority.HIGH)
    public void onSignClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Sign)) return;

        Player player = event.getPlayer();
        Arena arena = findArenaBySign(block.getLocation());
        if (arena == null) return;

        event.setCancelled(true);

        if (plugin.getArenaManager().getArenaOfParticipant(player) != null) {
            ChatUtil.send(player, plugin.getMessages().get("arena.already-in-arena"));
            return;
        }
        if (!arena.isSetup()) {
            ChatUtil.send(player, plugin.getMessages().get("arena.not-setup", arena.getId()));
            return;
        }
        if (arena.getState() == ArenaState.RESETTING) {
            ChatUtil.send(player, plugin.getMessages().get("arena.resetting-click"));
            return;
        }
        if (!plugin.getArenaManager().canPlayerAccessArena(player, arena)) {
            String key = arena.isTestMode() ? "arena.test-only" : "arena.disabled";
            ChatUtil.send(player, plugin.getMessages().get(key, arena.getDisplayName()));
            return;
        }

        // Partie en cours → rejoindre en spectateur
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
            if (arena.getPlayerCount() < arena.getMaxPlayers()) {
                ChatUtil.send(player, plugin.getMessages().get("arena.game-already-started"));
                return;
            }
            ChatUtil.send(player, plugin.getMessages().get("arena.full",
                arena.getId(), arena.getPlayerCount(), arena.getMaxPlayers()));
            return;
        }

        plugin.getPerkManager().prepareForArenaEntry(player, arena, false);
        plugin.getPlayerManager().prepareForLobby(player);
        var vdp = plugin.getPlayerManager().getPlayer(player);
        if (vdp != null) {
            vdp.setReturnLocation(player.getLocation().clone());
            vdp.setCurrentArenaId(arena.getId());
        }
        plugin.getArenaManager().joinArena(player, arena);
        ChatUtil.send(player, plugin.getMessages().get("arena.joined", arena.getDisplayName()));
        plugin.getGameManager().checkAndStart(arena);
    }

    // ---- Mise à jour périodique ----

    private void startSignUpdateTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateAllSigns, 10L, 10L);
    }

    private void updateAllSigns() {
        for (Arena arena : plugin.getArenaManager().getAllArenas()) {
            Location signLoc = arena.getSignLocation();
            if (signLoc == null) continue;
            Block block = signLoc.getBlock();
            if (!(block.getState() instanceof Sign sign)) continue;
            updateSign(sign, arena);
            sign.update(true, false);
        }
    }

    /** Met à jour un panneau physique selon l'état de l'arène. */
    public void updateSign(Sign sign, Arena arena) {
        var m = plugin.getMessages();
        sign.setLine(0, ChatUtil.color("&6&l" + arena.getDisplayName()));

        if (!arena.isSetup()) {
            sign.setLine(1, ChatUtil.color(m.format("sign.configuring")));
            sign.setLine(2, "");
            sign.setLine(3, "");
            return;
        }

        boolean running = arena.getState() == ArenaState.RUNNING
                       || arena.getState() == ArenaState.BETWEEN_WAVES;

        sign.setLine(1, ChatUtil.color(getStatusLine(arena)));
        sign.setLine(2, ChatUtil.color(getPlayersLine(arena)));
        sign.setLine(3, ChatUtil.color(getActionLine(arena, running)));
    }

    private String getPlayersLine(Arena arena) {
        return plugin.getMessages().format("sign.players",
            arena.getPlayerCount(), arena.getMaxPlayers());
    }

    private String getStatusLine(Arena arena) {
        var m = plugin.getMessages();
        return switch (arena.getState()) {
            case WAITING       -> m.format("sign.waiting");
            case STARTING      -> m.format("sign.starting");
            case RUNNING       -> m.format("sign.running", arena.getCurrentWave());
            case BETWEEN_WAVES -> m.format("sign.between-waves");
            case ENDING        -> m.format("sign.ending");
            case RESETTING     -> m.format("sign.resetting");
            case SETUP         -> m.format("sign.configuring");
        };
    }

    private String getActionLine(Arena arena, boolean running) {
        var m = plugin.getMessages();
        if (arena.isDisabled()) {
            return m.format("sign.disabled");
        }
        if (arena.isTestMode()) {
            return m.format("sign.testing");
        }
        if (running) {
            return m.format("sign.watch");
        }
        return arena.isJoinWindowOpen() ? m.format("sign.join") : "";
    }

    // ---- Helper ----

    private Arena findArenaBySign(Location loc) {
        for (Arena arena : plugin.getArenaManager().getAllArenas()) {
            Location signLoc = arena.getSignLocation();
            if (signLoc != null && isSameBlock(signLoc, loc)) return arena;
        }
        return null;
    }

    private boolean isSameBlock(Location a, Location b) {
        if (a.getWorld() == null || !a.getWorld().equals(b.getWorld())) return false;
        return a.getBlockX() == b.getBlockX()
            && a.getBlockY() == b.getBlockY()
            && a.getBlockZ() == b.getBlockZ();
    }
}
