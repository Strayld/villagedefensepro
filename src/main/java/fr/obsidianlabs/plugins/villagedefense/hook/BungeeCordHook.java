package fr.obsidianlabs.plugins.villagedefense.hook;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

final class BungeeCordHook implements PluginMessageListener {

    private static final String CHANNEL = "BungeeCord";

    private final VillageDefensePlugin plugin;
    private boolean available;

    BungeeCordHook(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    void enable() {
        disable();
        if (!plugin.getConfig().getBoolean("hooks.bungeecord.enabled", false)) {
            available = false;
            return;
        }
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        available = true;
    }

    void disable() {
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, this);
        available = false;
    }

    boolean isAvailable() {
        return available;
    }

    boolean sendPlayerToReturnServer(Player player) {
        if (!available || player == null || !player.isOnline()) {
            return false;
        }
        String targetServer = plugin.getConfig().getString("hooks.bungeecord.return-server", "").trim();
        if (targetServer.isEmpty()) {
            return false;
        }

        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeUTF("Connect");
                out.writeUTF(targetServer);
            }
            player.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
            return true;
        } catch (IOException ex) {
            plugin.getLogger().warning("[Hooks] Impossible d'envoyer " + player.getName()
                + " vers le proxy '" + targetServer + "' : " + ex.getMessage());
            return false;
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
            String subchannel = in.readUTF();
            // BungeeCord Forward : sous-canal "villagedefense:hub"
            // Format de la donnée : action (String) | playerUUID (String) | arenaId (String)
            if (!"villagedefense:hub".equals(subchannel)) return;
            // BungeeCord encode les données forwardées avec un prefixe short (longueur)
            short dataLen = in.readShort();
            byte[] data = new byte[dataLen];
            in.readFully(data);
            DataInputStream msg = new DataInputStream(new ByteArrayInputStream(data));
            String action = msg.readUTF();
            if (!"JOIN_ARENA".equals(action)) return;
            UUID playerUuid = UUID.fromString(msg.readUTF());
            String arenaId = msg.readUTF();
            plugin.getGameManager().queueHubJoin(playerUuid, arenaId);
        } catch (IOException | IllegalArgumentException ex) {
            plugin.getLogger().warning("[BungeeCord] Message villagedefense:hub malformé : " + ex.getMessage());
        }
    }
}
