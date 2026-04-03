package fr.obsidianlabs.plugins.villagedefense.network;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.database.MySQLDatabase;
import org.bukkit.scheduler.BukkitTask;

/**
 * Exporte périodiquement l'état de toutes les arènes locales dans la table partagée
 * {@code vd_network_arenas} pour que le plugin Hub puisse les découvrir.
 *
 * <p>La synchronisation est uniquement active lorsque {@code network.enabled: true} ET
 * que la base de données configurée est MySQL (base partagée entre serveurs).
 * En mode H2 local, la synchronisation est silencieusement désactivée.
 */
public class NetworkSyncManager {

    private final VillageDefensePlugin plugin;
    private BukkitTask syncTask;

    public NetworkSyncManager(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!isEnabled()) return;

        plugin.getDatabase().initNetworkTable();
        plugin.getDatabase().initRejoinTable();
        plugin.getDatabase().initHubPendingTable();

        long intervalTicks = plugin.getConfig().getLong("network.sync-interval", 10L) * 20L;
        syncTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin, this::sync, 40L, intervalTicks);
        plugin.getLogger().info("[Network] Sync réseau activée — serveur: \"" + getServerId() + "\""
                + ", intervalle: " + (intervalTicks / 20) + "s.");
    }

    public void stop() {
        if (syncTask != null) {
            syncTask.cancel();
            syncTask = null;
        }
        if (!isEnabled()) return;
        plugin.getDatabase().removeNetworkArenas(getServerId());
        plugin.getLogger().info("[Network] Entrées réseau supprimées pour \"" + getServerId() + "\".");
    }

    private void sync() {
        String serverId = getServerId();
        long now = System.currentTimeMillis();
        for (Arena arena : plugin.getArenaManager().getAllArenas()) {
            String icon = arena.getCustomIcon() != null ? arena.getCustomIcon().name() : null;
            NetworkArenaEntry entry = new NetworkArenaEntry(
                    serverId,
                    arena.getId(),
                    arena.getDisplayName(),
                    arena.getState().name(),
                    arena.getAvailability().name(),
                    arena.getPlayers().size(),
                    arena.getMaxPlayers(),
                    arena.getCurrentWave(),
                    icon,
                    now);
            plugin.getDatabase().upsertNetworkArena(entry);
        }
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("network.enabled", false)
                && plugin.getDatabase() instanceof MySQLDatabase;
    }

    public String getServerId() {
        return plugin.getConfig().getString("network.server-id", "server-1");
    }
}
