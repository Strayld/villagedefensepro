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
 * GUI d'administration de la base de données.
 * Permet de choisir le backend local H2 (type SQLITE) ou MySQL et de saisir les paramètres de connexion
 * sans modifier config.yml à la main.
 *
 * Layout (54 slots) :
 *  Ligne 0 : séparateurs + statut actuel (slot 4)
 *  Ligne 1 : [H2 local] slot 11 | [MySQL] slot 15
 *  Lignes 2-3 : paramètres MySQL (host, port, db, user, pass) — visibles si MySQL sélectionné
 *  Ligne 5 : [Retour] slot 45 | [Sauvegarder & Recharger] slot 53
 */
public class DatabaseGUI {

    public static final String TITLE = "gui.database.title";

    private static final int SLOT_STATUS  = 4;
    private static final int SLOT_SQLITE  = 11;
    private static final int SLOT_MYSQL   = 15;
    private static final int SLOT_HOST    = 19;
    private static final int SLOT_PORT    = 21;
    private static final int SLOT_DBNAME  = 23;
    private static final int SLOT_USER    = 28;
    private static final int SLOT_PASS    = 30;
    private static final int SLOT_BACK    = 45;
    private static final int SLOT_SAVE    = 53;

    private final VillageDefensePlugin plugin;

    public DatabaseGUI(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    private String m(String key, Object... args) {
        return plugin.getMessages().format(key, args);
    }

    // ── Ouverture ─────────────────────────────────────────────────────────────

    public void open(Player admin) {
        if (!plugin.hasPermission(admin, Perm.ADMIN_DATABASE)) {
            ChatUtil.send(admin, plugin.getMessages().format("general.no-permission"));
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 54, m(TITLE));
        for (int i = 0; i < 54; i++) inv.setItem(i, ItemBuilder.separator());
        for (int i = 45; i < 54; i++) inv.setItem(i, ItemBuilder.separator(Material.BLACK_STAINED_GLASS_PANE));

        String type   = plugin.getConfig().getString("database.type", "SQLITE").toUpperCase();
        boolean mysql = type.equals("MYSQL");

        // Statut courant
        boolean connected = plugin.getDatabase() != null && plugin.getDatabase().isConnected();
        String statusLabel = connected ? m("admin.db.status-connected") : m("admin.db.status-disconnected");
        inv.setItem(SLOT_STATUS, new ItemBuilder(Material.COMPARATOR)
            .name(m("admin.db.config-title"))
            .lore(m("admin.db.status-lore", statusLabel),
                  mysql ? m("admin.db.host-lore", plugin.getConfig().getString("database.host", "localhost"))
                        : m("admin.db.sqlite-lore"),
                  "", m("admin.common.click-to-edit"))
            .build());

        // Bouton backend local H2 (type SQLITE)
        var sqliteItem = new ItemBuilder(Material.CHEST)
            .name(mysql ? m("admin.db.sqlite-inactive") : m("admin.db.sqlite-active"))
            .lore(m("admin.db.sqlite-lore"), "",
                  mysql ? m("admin.db.sqlite-inactive-lore") : m("admin.db.sqlite-active-lore"));
        if (!mysql) sqliteItem.glowing();
        inv.setItem(SLOT_SQLITE, sqliteItem.build());

        // Bouton MySQL
        var mysqlItem = new ItemBuilder(Material.BARREL)
            .name(mysql ? m("admin.db.mysql-active") : m("admin.db.mysql-inactive"))
            .lore(m("admin.db.mysql-lore"), "",
                  mysql ? m("admin.db.mysql-active-lore") : m("admin.db.mysql-inactive-lore"));
        if (mysql) mysqlItem.glowing();
        inv.setItem(SLOT_MYSQL, mysqlItem.build());

        // Champs MySQL (visibles uniquement si MySQL)
        if (mysql) {
            String host   = plugin.getConfig().getString("database.host", "localhost");
            int    port   = plugin.getConfig().getInt("database.port", 3306);
            String dbName = plugin.getConfig().getString("database.name", "villagedefense");
            String user   = plugin.getConfig().getString("database.username", "root");
            String pass   = plugin.getConfig().getString("database.password", "");

            inv.setItem(SLOT_HOST, new ItemBuilder(Material.NAME_TAG)
                .name(m("admin.db.host"))
                .lore(m("admin.db.host-lore", host), "", m("admin.common.click-to-edit"))
                .build());

            inv.setItem(SLOT_PORT, new ItemBuilder(Material.COMPARATOR)
                .name(m("admin.db.port"))
                .lore(m("admin.db.port-lore", port), "", m("admin.common.click-to-edit"))
                .build());

            inv.setItem(SLOT_DBNAME, new ItemBuilder(Material.BOOK)
                .name(m("admin.db.dbname"))
                .lore(m("admin.db.dbname-lore", dbName), "", m("admin.common.click-to-edit"))
                .build());

            inv.setItem(SLOT_USER, new ItemBuilder(Material.PLAYER_HEAD)
                .name(m("admin.db.user"))
                .lore(m("admin.db.user-lore", user), "", m("admin.common.click-to-edit"))
                .build());

            inv.setItem(SLOT_PASS, new ItemBuilder(Material.TRIPWIRE_HOOK)
                .name(m("admin.db.pass"))
                .lore(m("admin.db.pass-lore", pass.isEmpty() ? m("admin.db.empty-password") : "*".repeat(pass.length())),
                      "", m("admin.common.click-to-edit"))
                .build());
        }

        // Navigation
        inv.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW)
            .name(m("admin.common.back"))
            .build());

        inv.setItem(SLOT_SAVE, new ItemBuilder(Material.EMERALD)
            .name(m("admin.db.save-reload"))
            .lore(m("admin.common.click-to-edit"))
            .build());

        admin.openInventory(inv);
    }

    // ── Clics ─────────────────────────────────────────────────────────────────

    public void handleClick(InventoryClickEvent event, Player admin) {
        event.setCancelled(true);
        int slot = event.getSlot();

        if (slot == SLOT_BACK) {
            plugin.getVdAdminGui().open(admin);
            return;
        }

        if (slot == SLOT_SAVE) {
            plugin.saveConfig();
            plugin.reloadDatabase();
            admin.sendMessage(m("admin.db.save-reload"));
            open(admin);
            return;
        }

        if (slot == SLOT_SQLITE) {
            plugin.getConfig().set("database.type", "SQLITE");
            open(admin);
            return;
        }

        if (slot == SLOT_MYSQL) {
            plugin.getConfig().set("database.type", "MYSQL");
            open(admin);
            return;
        }

        String type = plugin.getConfig().getString("database.type", "SQLITE").toUpperCase();
        if (!type.equals("MYSQL")) return;

        if (slot == SLOT_HOST) {
            promptField(admin, m("admin.db.prompt-host"), "database.host");
        } else if (slot == SLOT_PORT) {
            promptField(admin, m("admin.db.prompt-port"), "database.port");
        } else if (slot == SLOT_DBNAME) {
            promptField(admin, m("admin.db.prompt-dbname"), "database.name");
        } else if (slot == SLOT_USER) {
            promptField(admin, m("admin.db.prompt-user"), "database.username");
        } else if (slot == SLOT_PASS) {
            promptField(admin, m("admin.db.prompt-pass"), "database.password");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void promptField(Player admin, String prompt, String configKey) {
        plugin.getChatInputManager().prompt(admin, prompt, value -> {
            value = value.trim();
            // Port : valider que c'est un entier
            if (configKey.equals("database.port")) {
                try {
                    plugin.getConfig().set(configKey, Integer.parseInt(value));
                } catch (NumberFormatException e) {
                    admin.sendMessage(m("admin.db.port-invalid"));
                    open(admin);
                    return;
                }
            } else {
                plugin.getConfig().set(configKey, value);
            }
            String displayValue = configKey.equals("database.password") ? "***" : value;
            admin.sendMessage(m("admin.db.field-set", configKey, displayValue));
            open(admin);
        });
    }
}
