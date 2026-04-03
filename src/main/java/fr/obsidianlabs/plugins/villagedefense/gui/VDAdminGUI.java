package fr.obsidianlabs.plugins.villagedefense.gui;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaSetup;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaState;
import fr.obsidianlabs.plugins.villagedefense.util.ChatUtil;
import fr.obsidianlabs.plugins.villagedefense.util.ItemBuilder;
import fr.obsidianlabs.plugins.villagedefense.util.Perm;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * GUI principal d'administration — ouvert par /vdadmin sans argument.
 *
 * Layout (54 slots, 6 rangées) :
 * Rangée 0 (0-8) : [Version][sep ×7][sep]
 * Rangées 1-4 (9-44): arènes (jusqu'à 28 items)
 * Rangée 5 (45-53) :
 * [sep][Schematics][sep][Database][sep][Langue][sep][Kits][Bungee]
 */
public class VDAdminGUI {

    public static final String TITLE = "gui.admin.title";

    // Slots des boutons fixes
    private static final int SLOT_CREATE_ARENA = 8;
    private static final int SLOT_SCHEMATICS = 46; // col 2
    private static final int SLOT_ACHIEVEMENTS = 48; // col 4
    private static final int SLOT_KIT_CONFIG = 50; // col 6
    private static final int SLOT_SETTINGS = 52; // col 8

    // Zone des arènes : slots 10-16, 19-25, 28-34, 37-43
    private static final int[] ARENA_SLOTS = buildArenaSlots();

    private final VillageDefensePlugin plugin;

    public VDAdminGUI(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    private String m(String key, Object... args) {
        return plugin.getMessages().format(key, args);
    }

    // ─── Ouverture ────────────────────────────────────────────────────────────

    public void open(Player admin) {
        Inventory inv = Bukkit.createInventory(null, 54, m(TITLE));
        for (int i = 0; i < 54; i++)
            inv.setItem(i, ItemBuilder.separator());

        // Séparateur rangée 5 (fond)
        for (int i = 45; i < 54; i++)
            inv.setItem(i, ItemBuilder.separator(Material.BLACK_STAINED_GLASS_PANE));

        // Haut-gauche : version du plugin
        inv.setItem(0, buildVersionItem());

        // Haut-droite : créer une nouvelle arène
        inv.setItem(SLOT_CREATE_ARENA, new ItemBuilder(Material.WRITABLE_BOOK)
                .name(m("admin.main.create-arena"))
                .lore(m("admin.main.arenas-lore"), m("admin.common.click-to-edit"))
                .build());

        // Arènes
        Collection<Arena> arenas = plugin.getArenaManager().getAllArenas();
        int idx = 0;
        for (Arena arena : arenas) {
            if (idx >= ARENA_SLOTS.length)
                break;
            inv.setItem(ARENA_SLOTS[idx++], buildArenaItem(arena));
        }

        // Si aucune arène
        if (arenas.isEmpty()) {
            inv.setItem(22, new ItemBuilder(Material.BARRIER)
                    .name(m("admin.main.no-arenas"))
                    .lore(m("gui.admin.no-arenas-lore"))
                    .build());
        }

        // Boutons de rangée basse
        inv.setItem(SLOT_SCHEMATICS, new ItemBuilder(Material.AMETHYST_SHARD)
                .name(m("admin.main.schematics"))
                .lore(m("admin.main.schematics-lore"),
                        m("admin.main.schematics-lore-global"),
                        "",
                        m("admin.common.click-to-select"))
                .build());

        inv.setItem(SLOT_ACHIEVEMENTS, new ItemBuilder(Material.NETHER_STAR)
                .name(m("admin.main.achievements-admin"))
                .lore(m("admin.main.achievements-admin-lore"),
                        "&7Succès configurés : &e" + plugin.getAchievementManager().getAll().size(),
                        "",
                        m("admin.common.click-to-edit"))
                .build());

        inv.setItem(SLOT_KIT_CONFIG, new ItemBuilder(Material.EXPERIENCE_BOTTLE)
                .name(m("admin.main.kit-levels"))
                .lore(m("admin.main.kit-levels-lore"),
                        m("admin.main.kit-levels-lore-count", plugin.getKitManager().countCustomLevelRequirements()),
                        "",
                        m("admin.common.click-to-edit"))
                .build());

        inv.setItem(SLOT_SETTINGS, new ItemBuilder(Material.COMPARATOR)
                .name(m("admin.main.settings"))
                .lore(m("admin.main.settings-lore"),
                        "",
                        m("admin.common.click-to-edit"))
                .build());

        admin.openInventory(inv);
    }

    // ─── Builders ─────────────────────────────────────────────────────────────

    private ItemStack buildVersionItem() {
        var desc = plugin.getDescription();
        return new ItemBuilder(Material.NETHER_STAR)
                .name(m("gui.admin.version-title", desc.getVersion()))
                .lore(m("gui.admin.version-author", String.join(", ", desc.getAuthors())),
                        "",
                        m("gui.admin.version-cmd-lore1"),
                        m("gui.admin.version-cmd-lore2"))
                .build();
    }

    private ItemStack buildArenaItem(Arena arena) {
        ArenaState state = arena.getState();
        String stateLabel = switch (state) {
            case WAITING -> m("gui.admin.arena-state-waiting");
            case STARTING -> m("gui.admin.arena-state-starting");
            case RUNNING -> m("gui.admin.arena-state-running", arena.getCurrentWave());
            case BETWEEN_WAVES -> m("gui.admin.arena-state-between");
            case ENDING -> m("gui.admin.arena-state-ending");
            case RESETTING -> m("gui.admin.arena-state-resetting");
            case SETUP -> m("gui.admin.arena-state-setup");
        };

        boolean ready = arena.isSetup();
        Material icon;
        if (arena.getCustomIcon() != null) {
            icon = arena.getCustomIcon();
        } else {
            icon = switch (state) {
                case RUNNING, BETWEEN_WAVES -> Material.REDSTONE_TORCH;
                case SETUP -> Material.COMPARATOR;
                default -> ready ? Material.TORCH : Material.DEAD_FIRE_CORAL;
            };
        }

        List<String> lore = new ArrayList<>();
        lore.add(m("gui.admin.arena-state-label", stateLabel));
        lore.add(m("gui.admin.arena-players", arena.getPlayerCount(), arena.getMaxPlayers()));
        lore.add(ready ? m("gui.admin.arena-configured-yes") : m("gui.admin.arena-configured-no"));
        lore.add("");
        lore.add(m("gui.admin.arena-click-edit"));

        return new ItemBuilder(icon)
                .name(m("gui.admin.arena-title", arena.getDisplayName(), arena.getId()))
                .lore(lore)
                .build();
    }

    // ─── Clic ─────────────────────────────────────────────────────────────────

    public void handleClick(InventoryClickEvent event, Player admin) {
        event.setCancelled(true);
        int slot = event.getSlot();

        if (slot == SLOT_CREATE_ARENA) {
            if (!plugin.hasPermission(admin, Perm.ADMIN_ARENA)) {
                ChatUtil.send(admin, plugin.getMessages().format("general.no-permission"));
                return;
            }
            plugin.getChatInputManager().prompt(admin, m("admin.cmd.prompt-create-id"), id -> {
                id = id.trim().toLowerCase().replaceAll("[^a-z0-9_\\-]", "");
                if (id.isEmpty()) {
                    ChatUtil.send(admin, m("admin.cmd.id-invalid"));
                    open(admin);
                    return;
                }
                if (plugin.getArenaManager().arenaExists(id)) {
                    ChatUtil.send(admin, m("admin.cmd.id-exists", id));
                    open(admin);
                    return;
                }
                Arena arena = plugin.getArenaManager().createArena(id);
                arena.setDisplayName(id);
                plugin.getArenaManager().saveArena(arena);
                ChatUtil.send(admin, m("admin.cmd.arena-created", id));
                openArenaSetup(admin, arena);
            });
            return;
        }

        if (slot == SLOT_SCHEMATICS) {
            if (!plugin.hasPermission(admin, Perm.ADMIN_SCHEMATIC)) {
                ChatUtil.send(admin, plugin.getMessages().format("general.no-permission"));
                return;
            }
            plugin.getSchematicCategoryGui().open(admin, null);
            return;
        }

        if (slot == SLOT_SETTINGS) {
            if (!plugin.hasPermission(admin, Perm.ADMIN_RELOAD)) {
                ChatUtil.send(admin, plugin.getMessages().format("general.no-permission"));
                return;
            }
            plugin.getSettingsGui().open(admin);
            return;
        }

        if (slot == SLOT_ACHIEVEMENTS) {
            plugin.getAchievementAdminGui().open(admin);
            return;
        }

        if (slot == SLOT_KIT_CONFIG) {
            if (!plugin.hasPermission(admin, Perm.ADMIN_PLAYER)) {
                ChatUtil.send(admin, plugin.getMessages().format("general.no-permission"));
                return;
            }
            plugin.getKitConfigGui().open(admin);
            return;
        }

        // Clic sur une arène
        for (int i = 0; i < ARENA_SLOTS.length; i++) {
            if (slot == ARENA_SLOTS[i]) {
                if (!plugin.hasPermission(admin, Perm.ADMIN_SETUP)) {
                    ChatUtil.send(admin, plugin.getMessages().format("general.no-permission"));
                    return;
                }
                Arena arena = getArenaAtIndex(i);
                if (arena == null)
                    return;
                openArenaSetup(admin, arena);
                return;
            }
        }
    }

    private void openArenaSetup(Player admin, Arena arena) {
        if (plugin.getArenaManager().isInSetup(admin)) {
            ArenaSetup existing = plugin.getArenaManager().getSetup(admin);
            if (existing != null && existing.getArena() != null
                    && existing.getArena().getId().equals(arena.getId())) {
                plugin.getArenaSetupGui().open(admin, arena);
                return;
            }
            // Clore la session en cours (standalone ou autre arène) avant d'en démarrer une
            // nouvelle
            if (existing != null && existing.getArena() == null) {
                // Session standalone : retirer aussi la baguette
                if (ArenaSetup.isWand(plugin, admin.getInventory().getItem(8)))
                    admin.getInventory().setItem(8, null);
            }
            plugin.getArenaManager().endSetup(admin);
        }
        ArenaSetup setup = plugin.getArenaManager().startSetup(admin, arena);
        setup.giveWand();
        plugin.getArenaSetupGui().open(admin, arena);
    }

    private Arena getArenaAtIndex(int index) {
        int i = 0;
        for (Arena arena : plugin.getArenaManager().getAllArenas()) {
            if (i++ == index)
                return arena;
        }
        return null;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static int[] buildArenaSlots() {
        List<Integer> slots = new ArrayList<>();
        // Rangées 1 à 4, colonnes 1 à 7 (évite les bords)
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                slots.add(row * 9 + col);
            }
        }
        return slots.stream().mapToInt(Integer::intValue).toArray();
    }
}
