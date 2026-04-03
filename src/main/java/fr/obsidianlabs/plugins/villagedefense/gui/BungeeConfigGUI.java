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

public class BungeeConfigGUI {

    public static final String TITLE = "gui.bungee-config.title";

    private static final int SLOT_STATUS = 4;
    private static final int SLOT_TOGGLE = 11;
    private static final int SLOT_SERVER = 15;
    private static final int SLOT_BACK = 18;
    private static final int SLOT_SAVE = 26;

    private final VillageDefensePlugin plugin;

    public BungeeConfigGUI(VillageDefensePlugin plugin) {
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
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, ItemBuilder.separator());
        }

        boolean enabled = plugin.getConfig().getBoolean("hooks.bungeecord.enabled", false);
        boolean active = plugin.getHookManager() != null && plugin.getHookManager().isBungeeCordEnabled();
        String returnServer = plugin.getConfig().getString("hooks.bungeecord.return-server", "").trim();
        String returnServerLabel = returnServer.isEmpty() ? m("admin.bungee.server-none") : returnServer;

        inv.setItem(SLOT_STATUS, new ItemBuilder(Material.COMPARATOR)
            .name(m("admin.bungee.config-title"))
            .lore(m("admin.bungee.status-lore",
                      active ? m("admin.bungee.status-active") : m("admin.bungee.status-inactive")),
                  m("admin.bungee.enabled-lore",
                      enabled ? m("admin.common.yes") : m("admin.common.no")),
                  m("admin.bungee.server-lore", returnServerLabel),
                  "",
                  m("admin.bungee.status-help"))
            .build());

        ItemBuilder toggleItem = new ItemBuilder(enabled ? Material.LIME_CONCRETE : Material.RED_CONCRETE)
            .name(enabled ? m("admin.bungee.disable") : m("admin.bungee.enable"))
            .lore(m("admin.bungee.toggle-lore"),
                  "",
                  m("admin.common.click-to-toggle"));
        if (enabled) {
            toggleItem.glowing();
        }
        inv.setItem(SLOT_TOGGLE, toggleItem.build());

        inv.setItem(SLOT_SERVER, new ItemBuilder(Material.NAME_TAG)
            .name(m("admin.bungee.return-server"))
            .lore(m("admin.bungee.server-lore", returnServerLabel),
                  "",
                  m("admin.common.click-to-edit"),
                  returnServer.isEmpty() ? "" : m("admin.bungee.shift-clear-server"))
            .build());

        inv.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW)
            .name(m("admin.common.back"))
            .build());

        inv.setItem(SLOT_SAVE, new ItemBuilder(Material.EMERALD)
            .name(m("admin.bungee.save-reload"))
            .lore(m("admin.bungee.save-reload-lore"))
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
            boolean enabled = plugin.getConfig().getBoolean("hooks.bungeecord.enabled", false);
            plugin.getConfig().set("hooks.bungeecord.enabled", !enabled);
            open(admin);
            return;
        }

        if (slot == SLOT_SERVER) {
            if (event.getClick().isShiftClick()) {
                plugin.getConfig().set("hooks.bungeecord.return-server", "");
                admin.sendMessage(m("admin.bungee.server-cleared"));
                open(admin);
                return;
            }

            plugin.getChatInputManager().prompt(admin, m("admin.bungee.prompt-server"), value -> {
                String target = value.trim();
                if (target.isEmpty()) {
                    admin.sendMessage(m("admin.bungee.invalid-server"));
                } else {
                    plugin.getConfig().set("hooks.bungeecord.return-server", target);
                    admin.sendMessage(m("admin.bungee.server-set", target));
                }
                open(admin);
            });
            return;
        }

        if (slot == SLOT_SAVE) {
            plugin.saveConfig();
            plugin.reloadConfig();
            admin.sendMessage(m("admin.bungee.save-reload-done"));
            open(admin);
        }
    }
}
