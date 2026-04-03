package fr.obsidianlabs.plugins.villagedefense.gui;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.util.ChatUtil;
import fr.obsidianlabs.plugins.villagedefense.util.ItemBuilder;
import fr.obsidianlabs.plugins.villagedefense.util.Perm;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

/**
 * GUI de configuration de la synchronisation réseau (VillageDefenseHub).
 *
 * Layout (27 slots, 3 rangées) :
 *   Slot  4 : statut global (informatif)
 *   Slot 11 : toggle enabled
 *   Slot 13 : server-id
 *   Slot 15 : sync-interval
 *   Slot 18 : retour
 *   Slot 26 : sauvegarder &amp; appliquer
 */
public class NetworkConfigGUI {

    public static final String TITLE = "gui.network-config.title";

    private static final int SLOT_STATUS   = 4;
    private static final int SLOT_TOGGLE   = 11;
    private static final int SLOT_SERVER_ID = 13;
    private static final int SLOT_INTERVAL = 15;
    private static final int SLOT_BACK     = 18;
    private static final int SLOT_SAVE     = 26;

    private final VillageDefensePlugin plugin;

    public NetworkConfigGUI(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    private String m(String key, Object... args) {
        return plugin.getMessages().format(key, args);
    }

    public void open(Player admin) {
        if (!plugin.hasPermission(admin, Perm.ADMIN_RELOAD)) {
            ChatUtil.send(admin, plugin.getMessages().format("general.no-permission"));
            return;
        }

        boolean enabled   = plugin.getConfig().getBoolean("network.enabled", false);
        boolean active    = plugin.getNetworkSyncManager() != null && plugin.getNetworkSyncManager().isEnabled();
        String serverId   = plugin.getConfig().getString("network.server-id", "server-1");
        long interval     = plugin.getConfig().getLong("network.sync-interval", 10L);

        Inventory inv = Bukkit.createInventory(null, 27, m(TITLE));
        for (int i = 0; i < 27; i++) inv.setItem(i, ItemBuilder.separator());

        // Statut
        inv.setItem(SLOT_STATUS, new ItemBuilder(Material.COMPARATOR)
            .name(m("admin.network.config-title"))
            .lore(m("admin.network.status-lore",
                      active ? m("admin.network.status-active") : m("admin.network.status-inactive")),
                  m("admin.network.enabled-lore",
                      enabled ? m("admin.common.yes") : m("admin.common.no")),
                  m("admin.network.server-id-lore", serverId),
                  m("admin.network.interval-lore", interval),
                  "",
                  m("admin.network.status-help"))
            .build());

        // Toggle
        ItemBuilder toggleItem = new ItemBuilder(enabled ? Material.LIME_CONCRETE : Material.RED_CONCRETE)
            .name(enabled ? m("admin.network.disable") : m("admin.network.enable"))
            .lore(m("admin.network.toggle-lore"), "", m("admin.common.click-to-toggle"));
        if (enabled) toggleItem.glowing();
        inv.setItem(SLOT_TOGGLE, toggleItem.build());

        // Server ID
        inv.setItem(SLOT_SERVER_ID, new ItemBuilder(Material.NAME_TAG)
            .name(m("admin.network.server-id-label"))
            .lore(m("admin.network.server-id-lore", serverId),
                  "",
                  m("admin.network.server-id-warning-1"),
                  m("admin.network.server-id-warning-2"),
                  "",
                  m("admin.common.click-to-edit"))
            .build());

        // Sync interval
        inv.setItem(SLOT_INTERVAL, new ItemBuilder(Material.CLOCK)
            .name(m("admin.network.interval-label"))
            .lore(m("admin.network.interval-lore", interval),
                  m("admin.network.interval-help"),
                  "",
                  m("admin.common.click-to-edit"))
            .build());

        // Retour
        inv.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW)
            .name(m("admin.common.back"))
            .build());

        // Sauvegarder
        inv.setItem(SLOT_SAVE, new ItemBuilder(Material.EMERALD)
            .name(m("admin.network.save-reload"))
            .lore(m("admin.network.save-reload-lore"))
            .build());

        admin.openInventory(inv);
    }

    public void handleClick(InventoryClickEvent event, Player admin) {
        event.setCancelled(true);
        int slot = event.getSlot();

        if (slot == SLOT_BACK) {
            plugin.getVdAdminGui().open(admin);
            return;
        }

        if (slot == SLOT_TOGGLE) {
            boolean enabled = plugin.getConfig().getBoolean("network.enabled", false);
            plugin.getConfig().set("network.enabled", !enabled);
            open(admin);
            return;
        }

        if (slot == SLOT_SERVER_ID) {
            plugin.getChatInputManager().prompt(admin, m("admin.network.prompt-server-id"), value -> {
                String v = value.trim();
                if (v.isEmpty()) {
                    admin.sendMessage(m("admin.network.invalid-server-id"));
                } else {
                    plugin.getConfig().set("network.server-id", v);
                    admin.sendMessage(m("admin.network.server-id-set", v));
                }
                open(admin);
            });
            return;
        }

        if (slot == SLOT_INTERVAL) {
            plugin.getChatInputManager().prompt(admin, m("admin.network.prompt-interval"), value -> {
                try {
                    long v = Long.parseLong(value.trim());
                    if (v < 5) throw new NumberFormatException();
                    plugin.getConfig().set("network.sync-interval", v);
                    admin.sendMessage(m("admin.network.interval-set", v));
                } catch (NumberFormatException e) {
                    admin.sendMessage(m("admin.network.invalid-interval"));
                }
                open(admin);
            });
            return;
        }

        if (slot == SLOT_SAVE) {
            plugin.saveConfig();
            // Redémarre le NetworkSyncManager pour prendre en compte les nouveaux paramètres
            if (plugin.getNetworkSyncManager() != null) {
                plugin.getNetworkSyncManager().stop();
                plugin.getNetworkSyncManager().start();
            }
            admin.sendMessage(m("admin.network.save-reload-done"));
            open(admin);
        }
    }
}
