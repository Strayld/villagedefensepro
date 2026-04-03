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
 * GUI "Paramètres" — regroupement des options globales du plugin.
 *
 * Layout (27 slots, 3 rangées) :
 *   Rangée du milieu uniquement :
 *   Slot 10 : Base de données
 *   Slot 12 : Langue
 *   Slot 14 : Proxy (BungeeCord)
 *   Slot 16 : Sync Réseau
 *   Slot 18 : Retour
 */
public class SettingsGUI {

    public static final String TITLE = "gui.settings.title";

    private static final int SLOT_DATABASE = 10;
    private static final int SLOT_LANGUAGE = 12;
    private static final int SLOT_PROXY    = 14;
    private static final int SLOT_NETWORK  = 16;
    private static final int SLOT_BACK     = 18;

    private final VillageDefensePlugin plugin;

    public SettingsGUI(VillageDefensePlugin plugin) {
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

        Inventory inv = Bukkit.createInventory(null, 27, m(TITLE));
        for (int i = 0; i < 27; i++) inv.setItem(i, ItemBuilder.separator());

        // Base de données
        String dbType = plugin.getConfig().getString("database.type", "SQLITE").toUpperCase();
        String dbTypeName = dbType.equals("MYSQL")
            ? m("gui.admin.db-type.mysql")
            : m("gui.admin.db-type.local");
        inv.setItem(SLOT_DATABASE, new ItemBuilder(Material.FURNACE)
            .name(m("admin.main.database"))
            .lore(m("admin.main.database-lore-type", dbTypeName),
                  "",
                  m("admin.common.click-to-edit"))
            .build());

        // Langue
        inv.setItem(SLOT_LANGUAGE, new ItemBuilder(Material.PAPER)
            .name(m("admin.main.language"))
            .lore(m("admin.main.language-current",
                      plugin.getConfig().getString("language", "fr").toUpperCase()),
                  "",
                  m("admin.common.click-to-edit"))
            .build());

        // Proxy (BungeeCord)
        boolean bungeeEnabled = plugin.getConfig().getBoolean("hooks.bungeecord.enabled", false);
        String returnServer   = plugin.getConfig().getString("hooks.bungeecord.return-server", "").trim();
        inv.setItem(SLOT_PROXY, new ItemBuilder(Material.ENDER_PEARL)
            .name(m("admin.settings.proxy"))
            .lore(m("admin.settings.proxy-lore-status",
                      bungeeEnabled ? m("admin.bungee.status-enabled") : m("admin.bungee.status-disabled")),
                  m("admin.settings.proxy-lore-server",
                      returnServer.isEmpty() ? m("admin.bungee.server-none") : returnServer),
                  m("admin.settings.proxy-lore-desc"),
                  "",
                  m("admin.common.click-to-edit"))
            .build());

        // Sync Réseau
        boolean networkEnabled = plugin.getConfig().getBoolean("network.enabled", false);
        String networkServerId = plugin.getConfig().getString("network.server-id", "server-1");
        long networkInterval   = plugin.getConfig().getLong("network.sync-interval", 10L);
        inv.setItem(SLOT_NETWORK, new ItemBuilder(Material.OBSERVER)
            .name(m("admin.settings.network"))
            .lore(m("admin.settings.network-lore-status",
                      networkEnabled ? m("admin.bungee.status-enabled") : m("admin.bungee.status-disabled")),
                  m("admin.settings.network-lore-server", networkServerId),
                  m("admin.settings.network-lore-interval", networkInterval),
                  m("admin.settings.network-lore-desc"),
                  "",
                  m("admin.common.click-to-edit"))
            .build());

        // Retour
        inv.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW)
            .name(m("admin.common.back"))
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

        if (slot == SLOT_DATABASE) {
            if (!plugin.hasPermission(admin, Perm.ADMIN_DATABASE)) {
                ChatUtil.send(admin, m("general.no-permission"));
                return;
            }
            plugin.getDatabaseGui().open(admin);
            return;
        }

        if (slot == SLOT_LANGUAGE) {
            if (!plugin.hasPermission(admin, Perm.ADMIN_LANG)) {
                ChatUtil.send(admin, m("general.no-permission"));
                return;
            }
            plugin.getLanguageGui().open(admin);
            return;
        }

        if (slot == SLOT_PROXY) {
            plugin.getBungeeConfigGui().open(admin);
            return;
        }

        if (slot == SLOT_NETWORK) {
            plugin.getNetworkConfigGui().open(admin);
        }
    }
}
