package fr.obsidianlabs.plugins.villagedefense.gui;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GUI de sélection d'icône pour une arène.
 *
 * Layout (54 slots, 6 rangées) :
 *   Rangées 0-4 (slots 0-44) : items sélectionnables (45 par page)
 *   Rangée 5 (slots 45-53)   : [sep×3][◀ Préc][sep][Suiv ▶][sep×3]
 *                                slot 48 = page précédente
 *                                slot 50 = page suivante
 *
 * 2 pages max → 90 items au total.
 */
public class IconPickerGUI {

    public static final String TITLE_KEY = "gui.icon-picker.title";

    private static final int SLOT_PREV = 48;
    private static final int SLOT_NEXT = 50;
    private static final int ITEMS_PER_PAGE = 45;
    private static final int MAX_PAGES = 2;

    /** Liste des matériaux proposés (90 max, 2 pages × 45). */
    private static final List<Material> ICON_MATERIALS = List.of(
        // ── Page 1 ─────────────────────────────────────────────────────────
        Material.TORCH,               Material.REDSTONE_TORCH,       Material.SOUL_TORCH,
        Material.LANTERN,             Material.SOUL_LANTERN,          Material.CAMPFIRE,
        Material.SOUL_CAMPFIRE,       Material.DIAMOND,               Material.EMERALD,
        Material.GOLD_INGOT,          Material.IRON_INGOT,            Material.NETHERITE_INGOT,
        Material.DIAMOND_SWORD,       Material.IRON_SWORD,            Material.GOLDEN_SWORD,
        Material.WOODEN_SWORD,        Material.STONE_SWORD,           Material.BOW,
        Material.CROSSBOW,            Material.TRIDENT,               Material.SHIELD,
        Material.DIAMOND_CHESTPLATE,  Material.IRON_CHESTPLATE,       Material.GOLDEN_CHESTPLATE,
        Material.LEATHER_CHESTPLATE,  Material.DIAMOND_HELMET,        Material.IRON_HELMET,
        Material.GOLDEN_HELMET,       Material.CHAINMAIL_HELMET,      Material.LEATHER_HELMET,
        Material.ZOMBIE_HEAD,         Material.SKELETON_SKULL,        Material.CREEPER_HEAD,
        Material.WITHER_SKELETON_SKULL, Material.DRAGON_HEAD,         Material.GRASS_BLOCK,
        Material.SAND,                Material.GRAVEL,                Material.COBBLESTONE,
        Material.STONE,               Material.TNT,                   Material.OBSIDIAN,
        Material.BEDROCK,             Material.NETHERRACK,            Material.END_STONE,

        // ── Page 2 ─────────────────────────────────────────────────────────
        Material.NETHER_STAR,         Material.BEACON,                Material.ENDER_EYE,
        Material.BLAZE_ROD,           Material.GHAST_TEAR,            Material.SPIDER_EYE,
        Material.ROTTEN_FLESH,        Material.BONE,                  Material.GUNPOWDER,
        Material.SLIME_BALL,          Material.PUMPKIN,               Material.CARVED_PUMPKIN,
        Material.JACK_O_LANTERN,      Material.OAK_LOG,               Material.DARK_OAK_LOG,
        Material.SPRUCE_LOG,          Material.BIRCH_LOG,             Material.JUNGLE_LOG,
        Material.IRON_BLOCK,          Material.GOLD_BLOCK,            Material.DIAMOND_BLOCK,
        Material.EMERALD_BLOCK,       Material.REDSTONE_BLOCK,        Material.LAPIS_BLOCK,
        Material.AMETHYST_BLOCK,      Material.TOTEM_OF_UNDYING,      Material.BELL,
        Material.BOOK,                Material.COMPASS,               Material.CLOCK,
        Material.SPYGLASS,            Material.ENDER_PEARL,           Material.BLAZE_POWDER,
        Material.MAGMA_CREAM,         Material.FERMENTED_SPIDER_EYE,  Material.HAY_BLOCK,
        Material.RED_BANNER,          Material.BLUE_BANNER,           Material.GREEN_BANNER,
        Material.YELLOW_BANNER,       Material.WHITE_BANNER,          Material.BLACK_BANNER,
        Material.COMPARATOR,          Material.REDSTONE_LAMP,         Material.GLOWSTONE
    );

    private final VillageDefensePlugin plugin;
    /** Page courante par joueur (0-indexed). */
    private final Map<UUID, Integer> pages = new HashMap<>();

    public IconPickerGUI(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    private String m(String key, Object... args) {
        return plugin.getMessages().format(key, args);
    }

    // ─── Ouverture ─────────────────────────────────────────────────────────

    public void open(Player admin, Arena arena) {
        open(admin, arena, pages.getOrDefault(admin.getUniqueId(), 0));
    }

    public void open(Player admin, Arena arena, int page) {
        page = Math.max(0, Math.min(page, MAX_PAGES - 1));
        pages.put(admin.getUniqueId(), page);

        String title = m(TITLE_KEY) + m("gui.icon-picker.page-suffix", page + 1, MAX_PAGES);
        Inventory inv = Bukkit.createInventory(null, 54, title);

        // Séparateurs fond
        for (int i = 0; i < 54; i++) inv.setItem(i, ItemBuilder.separator());
        for (int i = 45; i < 54; i++) inv.setItem(i, ItemBuilder.separator(Material.BLACK_STAINED_GLASS_PANE));

        // Items de la page
        int start = page * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEMS_PER_PAGE && (start + i) < ICON_MATERIALS.size(); i++) {
            Material mat = ICON_MATERIALS.get(start + i);
            List<String> lore = arena.getCustomIcon() == mat
                ? List.of(m("admin.common.enabled"), "", m("admin.icon.lore-select"))
                : List.of(m("admin.icon.lore-select"));
            inv.setItem(i, new ItemBuilder(mat).lore(lore).build());
        }

        // Navigation
        if (page > 0) {
            inv.setItem(SLOT_PREV, new ItemBuilder(Material.ARROW)
                .name(m("admin.schematic.page-prev"))
                .lore(m("gui.icon-picker.page-lore", page, MAX_PAGES))
                .build());
        }
        if (page < MAX_PAGES - 1 && (page + 1) * ITEMS_PER_PAGE < ICON_MATERIALS.size()) {
            inv.setItem(SLOT_NEXT, new ItemBuilder(Material.ARROW)
                .name(m("admin.schematic.page-next"))
                .lore(m("gui.icon-picker.page-lore", page + 2, MAX_PAGES))
                .build());
        }

        // Bouton réinitialiser
        inv.setItem(49, new ItemBuilder(Material.BARRIER)
            .name(m("admin.common.remove"))
            .lore(m("admin.common.click-to-select"))
            .build());

        admin.openInventory(inv);
    }

    // ─── Clic ──────────────────────────────────────────────────────────────

    public void handleClick(InventoryClickEvent event, Player admin) {
        event.setCancelled(true);
        int slot = event.getSlot();
        if (slot < 0 || slot >= 54) return;

        // Retrouver l'arène en cours de setup
        Arena arena = getArenaForAdmin(admin);
        if (arena == null) {
            admin.closeInventory();
            pages.remove(admin.getUniqueId());
            return;
        }

        int page = pages.getOrDefault(admin.getUniqueId(), 0);

        if (slot == SLOT_PREV && page > 0) {
            open(admin, arena, page - 1);
            return;
        }
        if (slot == SLOT_NEXT && page < MAX_PAGES - 1) {
            open(admin, arena, page + 1);
            return;
        }

        // Bouton réinitialiser
        if (slot == 49) {
            arena.setCustomIcon(null);
            plugin.getArenaManager().saveArena(arena);
            fr.obsidianlabs.plugins.villagedefense.util.ChatUtil.send(admin, m("admin.setup.icon-reset"));
            pages.remove(admin.getUniqueId());
            plugin.getArenaSetupGui().open(admin, arena);
            return;
        }

        // Clic sur un item (rangées 0-4)
        if (slot < ITEMS_PER_PAGE) {
            int index = page * ITEMS_PER_PAGE + slot;
            if (index >= ICON_MATERIALS.size()) return;
            Material chosen = ICON_MATERIALS.get(index);
            arena.setCustomIcon(chosen);
            plugin.getArenaManager().saveArena(arena);
            fr.obsidianlabs.plugins.villagedefense.util.ChatUtil.send(admin, m("admin.setup.icon-set"));
            pages.remove(admin.getUniqueId());
            plugin.getArenaSetupGui().open(admin, arena);
        }
    }

    public void cleanup(UUID playerId) {
        pages.remove(playerId);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private Arena getArenaForAdmin(Player admin) {
        var setup = plugin.getArenaManager().getSetup(admin);
        if (setup == null) return null;
        return setup.getArena();
    }
}
