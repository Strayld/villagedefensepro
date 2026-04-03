package fr.obsidianlabs.plugins.villagedefense.gui;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.tower.SchematicCategory;
import fr.obsidianlabs.plugins.villagedefense.tower.TowerSchematic;
import fr.obsidianlabs.plugins.villagedefense.util.ChatUtil;
import fr.obsidianlabs.plugins.villagedefense.util.ItemBuilder;
import fr.obsidianlabs.plugins.villagedefense.util.Perm;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

/**
 * GUI de sélection de catégorie de schematic — 54 slots (6 rangées).
 *
 * Layout :
 *   Rangée 0 : séparateurs
 *   Rangée 1 : ARCHER · TREBUCHET · MAGE · ICE
 *   Rangée 2 : LIGHTNING · CANNON · SPAWN_MONSTER · TOWER_SLOT
 *   Rangée 3-4 : séparateurs
 *   Rangée 5 : Retour (slot 49)
 */
public class SchematicCategoryGUI {

    public static final String TITLE = "gui.schematic-cat.title";

    // 8 catégories sur 2 rangées, espacées
    private static final int[] CAT_SLOTS = {10, 12, 14, 16, 19, 21, 23, 25};
    private static final int SLOT_BACK = 49;
    private static final SchematicCategory[] CATEGORIES = SchematicCategory.values();

    private final VillageDefensePlugin plugin;

    public SchematicCategoryGUI(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    private String m(String key, Object... args) {
        return plugin.getMessages().format(key, args);
    }

    // ─── Ouverture ────────────────────────────────────────────────────────────

    public void open(Player admin, Arena arena) {
        if (!plugin.hasPermission(admin, Perm.ADMIN_SCHEMATIC)) {
            ChatUtil.send(admin, plugin.getMessages().format("general.no-permission"));
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 54, m(TITLE));

        for (int i = 0; i < 54; i++) inv.setItem(i, ItemBuilder.separator());

        for (int i = 0; i < CATEGORIES.length && i < CAT_SLOTS.length; i++) {
            inv.setItem(CAT_SLOTS[i], buildCategoryItem(CATEGORIES[i]));
        }

        inv.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW)
            .name(m("admin.common.back"))
            .build());

        admin.openInventory(inv);
    }

    // ─── Builder ──────────────────────────────────────────────────────────────

    private org.bukkit.inventory.ItemStack buildCategoryItem(SchematicCategory cat) {
        int captured = countCaptured(cat);
        int total    = cat.getMaxLevels();

        String status = m("gui.schematic-cat.status",
            captured == total ? "&a" : "&e", captured, total);

        String desc = m(cat.getDescKey());

        return new ItemBuilder(cat.getIcon())
            .name("&d&l" + m(cat.getNameKey()))
            .lore(desc, "", status, "", m("admin.cat.lore-click"))
            .build();
    }

    private int countCaptured(SchematicCategory cat) {
        int count = 0;
        for (int level = 1; level <= cat.getMaxLevels(); level++) {
            if (!TowerSchematic.getBlocks(cat, level).isEmpty()) count++;
        }
        return count;
    }

    // ─── Clic ─────────────────────────────────────────────────────────────────

    public void handleClick(InventoryClickEvent event, Player admin) {
        event.setCancelled(true);
        int slot = event.getSlot();

        // L'arène est optionnelle (null quand ouvert depuis le GUI admin global)
        var setup = plugin.getArenaManager().getSetup(admin);
        Arena arena = setup != null ? setup.getArena() : null;

        if (slot == SLOT_BACK) {
            if (arena != null) {
                plugin.getArenaSetupGui().open(admin, arena);
            } else {
                plugin.getVdAdminGui().open(admin);
            }
            return;
        }

        for (int i = 0; i < CAT_SLOTS.length; i++) {
            if (slot == CAT_SLOTS[i] && i < CATEGORIES.length) {
                admin.closeInventory();
                plugin.getSchematicGui().open(admin, arena, CATEGORIES[i]);
                return;
            }
        }
    }
}
