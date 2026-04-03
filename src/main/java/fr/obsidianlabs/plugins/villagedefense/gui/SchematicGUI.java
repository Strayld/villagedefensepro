package fr.obsidianlabs.plugins.villagedefense.gui;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaSetup;
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
import org.bukkit.inventory.ItemStack;

/**
 * GUI de sélection de niveau pour une catégorie de schematic donnée — 27 slots.
 *
 * Layout :
 *   Rangée 0 : séparateurs + en-tête de catégorie (slot 4)
 *   Rangée 1 : niveaux cliquables (slots 10, 12, 14 pour 3 niveaux ; slot 13 pour 1 niveau)
 *   Rangée 2 : [← Retour] (slot 18)  +  séparateurs
 */
public class SchematicGUI {

    public static final String TITLE = "gui.schematic.title";

    private final VillageDefensePlugin plugin;

    public SchematicGUI(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    private String m(String key, Object... args) {
        return plugin.getMessages().format(key, args);
    }

    // ─── Ouverture ────────────────────────────────────────────────────────────

    public void open(Player admin, Arena arena, SchematicCategory category) {
        if (!plugin.hasPermission(admin, Perm.ADMIN_SCHEMATIC)) {
            ChatUtil.send(admin, plugin.getMessages().format("general.no-permission"));
            return;
        }
        String title = m(TITLE, m(category.getNameKey()));
        Inventory inv = Bukkit.createInventory(null, 27, title);

        for (int i = 0; i < 27; i++) inv.setItem(i, ItemBuilder.separator());

        // En-tête de catégorie (slot 4, rangée 0)
        inv.setItem(4, buildHeaderItem(category));

        // Résoudre l'arenaId pour afficher le statut de surcharge
        var setup = plugin.getArenaManager().getSetup(admin);
        String arenaId = (arena != null) ? arena.getId()
            : (setup != null && setup.getArena() != null) ? setup.getArena().getId()
            : null;

        // Niveaux cliquables
        int maxLevels = category.getMaxLevels();
        if (maxLevels == 1) {
            inv.setItem(13, buildLevelItem(category, 1, arenaId));
        } else {
            int[] levelSlots = {10, 12, 14};
            for (int level = 1; level <= maxLevels && level <= levelSlots.length; level++) {
                inv.setItem(levelSlots[level - 1], buildLevelItem(category, level, arenaId));
            }
        }

        // Bouton retour (slot 18) — retourne au GUI catégories
        inv.setItem(18, new ItemBuilder(Material.ARROW)
            .name(m("admin.common.back"))
            .build());
        // Stocker l'arène dans le titre n'est pas possible, on passe par ArenaSetup

        admin.openInventory(inv);
    }

    // ─── Builders ─────────────────────────────────────────────────────────────

    private ItemStack buildHeaderItem(SchematicCategory cat) {
        return new ItemBuilder(cat.getIcon())
            .name("&e&l" + m(cat.getNameKey()))
            .lore(m("gui.schematic.header-lore1"),
                  m("gui.schematic.header-lore2"))
            .build();
    }

    private ItemStack buildLevelItem(SchematicCategory cat, int level, String arenaId) {
        boolean hasOverride  = TowerSchematic.hasArenaOverride(cat, level, arenaId);
        int     arenaCount   = arenaId != null ? TowerSchematic.getBlocks(cat, level, arenaId).size() : 0;
        int     globalCount  = TowerSchematic.getBlocks(cat, level).size();

        Material icon = iconForLevel(cat, level);
        String levelLabel = cat.getMaxLevels() > 1 ? m("gui.schematic.level-label", level) : "";

        java.util.List<String> lore = new java.util.ArrayList<>();
        if (arenaId != null) {
            if (hasOverride) {
                lore.add(m("gui.schematic.override-arena", arenaCount));
                lore.add(m("gui.schematic.global-label", globalCount));
            } else {
                lore.add(m("gui.schematic.uses-global", globalCount));
                lore.add(m("gui.schematic.no-override"));
            }
        } else {
            lore.add(globalCount > 0
                ? m("gui.schematic.captured", globalCount)
                : m("gui.schematic.default", globalCount));
        }
        lore.add("");
        lore.add(m("admin.schematic.lore-click-place"));
        lore.add(m("admin.schematic.lore-click-preview"));
        lore.add(m("admin.schematic.lore-click-delete"));

        return new ItemBuilder(icon)
            .name("&d" + m(cat.getNameKey()) + levelLabel)
            .lore(lore)
            .build();
    }

    private Material iconForLevel(SchematicCategory cat, int level) {
        return switch (cat) {
            case ARCHER -> switch (level) {
                case 1  -> Material.BIRCH_FENCE;
                case 2  -> Material.OAK_PLANKS;
                default -> Material.COBBLESTONE_WALL;
            };
            case TREBUCHET -> switch (level) {
                case 1  -> Material.SPRUCE_PLANKS;
                case 2  -> Material.OAK_LOG;
                default -> Material.DARK_OAK_LOG;
            };
            case MAGE -> switch (level) {
                case 1  -> Material.QUARTZ_PILLAR;
                case 2  -> Material.QUARTZ_BLOCK;
                default -> Material.QUARTZ_BRICKS;
            };
            case ICE -> switch (level) {
                case 1  -> Material.PACKED_ICE;
                case 2  -> Material.BLUE_ICE;
                default -> Material.ICE;
            };
            case LIGHTNING -> switch (level) {
                case 1  -> Material.LIGHTNING_ROD;
                case 2  -> Material.COPPER_BLOCK;
                default -> Material.OXIDIZED_COPPER;
            };
            case CANNON -> switch (level) {
                case 1  -> Material.TNT;
                case 2  -> Material.IRON_BLOCK;
                default -> Material.NETHERITE_BLOCK;
            };
            case SPAWN_MONSTER -> Material.NETHERRACK;
            case TOWER_SLOT    -> Material.GOLD_BLOCK;
        };
    }

    // ─── Clic ─────────────────────────────────────────────────────────────────

    public void handleClick(InventoryClickEvent event, Player admin) {
        event.setCancelled(true);
        int slot = event.getSlot();

        // Bouton retour → GUI catégories
        if (slot == 18) {
            var setup = plugin.getArenaManager().getSetup(admin);
            Arena arena = setup != null ? setup.getArena() : null;
            plugin.getSchematicCategoryGui().open(admin, arena);
            return;
        }

        // Trouver la catégorie et le niveau à partir du titre de l'inventaire
        SchematicCategory cat = getCategoryFromTitle(event.getInventory());
        if (cat == null) return;

        int level = getLevelFromSlot(slot, cat);
        if (level < 1) return;

        // Si aucun setup actif, créer une session standalone (sans arène)
        ArenaSetup setup = plugin.getArenaManager().getSetup(admin);
        if (setup == null) {
            setup = plugin.getArenaManager().startSchematicSetup(admin);
            // Pas de wand — les modes capture/édition gèrent l'inventaire eux-mêmes
        }

        admin.closeInventory();

        // Différer d'un tick pour que le client traite la fermeture du GUI avant
        // de recevoir la mise à jour de l'inventaire hotbar.
        final ArenaSetup finalSetup = setup;
        final SchematicCategory finalCat = cat;
        final int finalLevel = level;
        if (event.isRightClick()) {
            plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> finalSetup.enterEditMode(finalCat, finalLevel), 1L);
        } else {
            plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> finalSetup.enterCaptureMode(finalCat, finalLevel), 1L);
        }
    }

    private SchematicCategory getCategoryFromTitle(Inventory inv) {
        String plain = inv.getViewers().isEmpty()
            ? ""
            : inv.getViewers().get(0).getOpenInventory().getTitle();
        for (SchematicCategory cat : SchematicCategory.values()) {
            if (plain.equals(m(TITLE, m(cat.getNameKey())))) return cat;
        }
        return null;
    }

    private int getLevelFromSlot(int slot, SchematicCategory cat) {
        if (cat.getMaxLevels() == 1) {
            return slot == 13 ? 1 : -1;
        }
        return switch (slot) {
            case 10 -> 1;
            case 12 -> 2;
            case 14 -> 3;
            default -> -1;
        };
    }
}
