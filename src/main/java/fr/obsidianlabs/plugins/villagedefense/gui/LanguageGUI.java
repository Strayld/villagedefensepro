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
 * GUI de sélection de langue du plugin.
 * Chaque item représente une langue disponible.
 */
public class LanguageGUI {

    public static final String TITLE = "gui.language.title";

    // Langues disponibles : code → nom affiché → icône
    private static final Object[][] LANGUAGES = {
        { "fr", "gui.language.name.fr", Material.PAPER },
        { "en", "gui.language.name.en", Material.MAP   },
        { "de", "gui.language.name.de", Material.BOOK  },
    };

    private final VillageDefensePlugin plugin;

    public LanguageGUI(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    private String m(String key, Object... args) {
        return plugin.getMessages().format(key, args);
    }

    public void open(Player admin) {
        if (!plugin.hasPermission(admin, Perm.ADMIN_LANG)) {
            ChatUtil.send(admin, plugin.getMessages().format("general.no-permission"));
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 27, m(TITLE));
        for (int i = 0; i < 27; i++) inv.setItem(i, ItemBuilder.separator());

        String currentLang = plugin.getConfig().getString("language", "fr");

        int[] slots = {10, 13, 16};
        for (int i = 0; i < LANGUAGES.length && i < slots.length; i++) {
            String code    = (String) LANGUAGES[i][0];
            String nameKey = (String) LANGUAGES[i][1];
            String name    = m(nameKey);
            Material icon  = (Material) LANGUAGES[i][2];
            boolean active = code.equals(currentLang);

            var item = new ItemBuilder(icon)
                .name(active ? "&a✓ &e&l" + name : "&7" + name)
                .lore(active ? m("admin.common.enabled") : m("admin.common.click-to-select"));
            if (active) item.glowing();
            inv.setItem(slots[i], item.build());
        }

        // Bouton retour
        inv.setItem(18, new ItemBuilder(Material.ARROW)
            .name(m("admin.common.back"))
            .build());

        admin.openInventory(inv);
    }

    public void handleClick(InventoryClickEvent event, Player admin) {
        event.setCancelled(true);
        int slot = event.getSlot();

        if (slot == 18) {
            plugin.getVdAdminGui().open(admin);
            return;
        }

        int[] slots = {10, 13, 16};
        for (int i = 0; i < LANGUAGES.length && i < slots.length; i++) {
            if (slot == slots[i]) {
                String code = (String) LANGUAGES[i][0];
                String name = m((String) LANGUAGES[i][1]);
                plugin.getConfig().set("language", code);
                plugin.saveConfig();
                plugin.getMessages().reload();
                ChatUtil.send(admin, plugin.getMessages().format("admin.lang.changed", name));
                open(admin); // Rafraîchir
                return;
            }
        }
    }
}
