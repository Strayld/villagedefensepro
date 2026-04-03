package fr.obsidianlabs.plugins.villagedefense.gui;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaBoundaryMode;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaSetup;
import fr.obsidianlabs.plugins.villagedefense.compat.VersionCompat;
import fr.obsidianlabs.plugins.villagedefense.util.ChatUtil;
import fr.obsidianlabs.plugins.villagedefense.util.ItemBuilder;
import fr.obsidianlabs.plugins.villagedefense.util.Perm;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Sous-GUI « Emplacements Arènes » — limites, spawns et coffre shop.
 *
 * Layout 36 slots :
 *  Rangée 0 : [sep]
 *  Rangée 1 : [sep][LOBBY][GSPAWN][CENTER][POS1][POS2][SHOP][BOUNDARY][sep]
 *  Rangée 2 : séparateurs
 *  Rangée 3 : [sep][sep][sep][sep][BACK][sep][sep][sep][sep]
 */
public class ArenaPositionsGUI {

    public static final String TITLE = "gui.arena-positions.title";

    private static final int SLOT_LOBBY_SPAWN = 10;
    private static final int SLOT_GAME_SPAWN  = 11;
    private static final int SLOT_CENTER      = 12;
    private static final int SLOT_POS1        = 13;
    private static final int SLOT_POS2        = 14;
    private static final int SLOT_SHOP_CHEST  = 15;
    private static final int SLOT_BOUNDARY    = 16;
    private static final int SLOT_BACK        = 31;

    private final VillageDefensePlugin plugin;

    public ArenaPositionsGUI(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    private String m(String key, Object... args) {
        return plugin.getMessages().format(key, args);
    }

    public void open(Player admin, Arena arena) {
        if (!plugin.hasPermission(admin, Perm.ADMIN_SETUP)) {
            ChatUtil.send(admin, m("general.no-permission"));
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 36, m(TITLE));
        for (int i = 0; i < 36; i++) inv.setItem(i, ItemBuilder.separator());

        inv.setItem(SLOT_LOBBY_SPAWN, buildLobbySpawnItem(arena));
        inv.setItem(SLOT_GAME_SPAWN,  buildGameSpawnItem(arena));
        inv.setItem(SLOT_CENTER,      buildCenterItem(arena));
        inv.setItem(SLOT_POS1,        buildPos1Item(arena));
        inv.setItem(SLOT_POS2,        buildPos2Item(arena));
        inv.setItem(SLOT_SHOP_CHEST,  buildShopChestItem(arena));
        inv.setItem(SLOT_BOUNDARY,    buildBoundaryItem(arena));
        inv.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW)
            .name(m("admin.common.back")).build());

        admin.openInventory(inv);
    }

    // ── Builders ─────────────────────────────────────────────────────────────

    private ItemStack buildLobbySpawnItem(Arena arena) {
        boolean set = arena.getLobbyLocation() != null;
        return new ItemBuilder(Material.COMPASS)
            .name(m("admin.setup.lobby-spawn"))
            .lore(set ? m("admin.setup.lobby-spawn-set", formatLoc(arena.getLobbyLocation()))
                      : m("admin.setup.lobby-spawn-unset"),
                  "", m("admin.setup.lobby-spawn-lore"), m("admin.setup.lobby-spawn-click"))
            .build();
    }

    private ItemStack buildGameSpawnItem(Arena arena) {
        boolean set = arena.getGameSpawnLocation() != null;
        return new ItemBuilder(VersionCompat.TRACKING_COMPASS)
            .name(m("admin.setup.game-spawn"))
            .lore(set ? m("admin.setup.game-spawn-set", formatLoc(arena.getGameSpawnLocation()))
                      : m("admin.setup.game-spawn-unset"),
                  "", m("admin.setup.game-spawn-lore1"), m("admin.setup.game-spawn-lore2"),
                  m("admin.setup.game-spawn-click"), m("admin.setup.game-spawn-shift"))
            .build();
    }

    private ItemStack buildCenterItem(Arena arena) {
        boolean set = arena.getVillageCenter() != null;
        return new ItemBuilder(Material.GRASS_BLOCK)
            .name(m("admin.setup.set-village-center"))
            .lore(set ? m("admin.setup.set-village-center-set", formatLoc(arena.getVillageCenter()))
                      : m("admin.setup.set-village-center-unset"),
                  "", m("admin.setup.set-village-center-lore"))
            .build();
    }

    private ItemStack buildPos1Item(Arena arena) {
        boolean set = arena.getPos1() != null;
        return new ItemBuilder(set ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE)
            .name(set ? m("admin.setup.set-pos1") + " &a✓" : m("admin.setup.set-pos1"))
            .lore(set ? m("admin.setup.set-pos1-set", formatLoc(arena.getPos1()))
                      : m("admin.setup.set-pos1-unset"),
                  "", m("admin.setup.set-pos1-lore"))
            .build();
    }

    private ItemStack buildPos2Item(Arena arena) {
        boolean set = arena.getPos2() != null;
        return new ItemBuilder(set ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE)
            .name(set ? m("admin.setup.set-pos2") + " &a✓" : m("admin.setup.set-pos2"))
            .lore(set ? m("admin.setup.set-pos2-set", formatLoc(arena.getPos2()))
                      : m("admin.setup.set-pos2-unset"),
                  "", m("admin.setup.set-pos2-lore"))
            .build();
    }

    private ItemStack buildShopChestItem(Arena arena) {
        boolean set = arena.getShopChestLocation() != null;
        if (set) {
            return new ItemBuilder(Material.CHEST)
                .name(m("admin.setup.shop-item-registered"))
                .lore(m("admin.setup.shop-item-set", formatLoc(arena.getShopChestLocation())),
                      "", m("admin.setup.shop-item-lore1"), m("admin.setup.shop-item-lore2"),
                      "", m("admin.setup.shop-item-set-shift"))
                .build();
        }
        return new ItemBuilder(Material.TRAPPED_CHEST)
            .name(m("admin.setup.shop-item"))
            .lore(m("admin.setup.shop-item-unset"),
                  "", m("admin.setup.shop-item-lore1"), m("admin.setup.shop-item-lore2"),
                  "", m("admin.setup.shop-item-unset-click"), m("admin.setup.shop-item-unset-click2"))
            .build();
    }

    private ItemStack buildBoundaryItem(Arena arena) {
        ArenaBoundaryMode mode = arena.getBoundaryMode();
        boolean isPushBack = mode == ArenaBoundaryMode.PUSH_BACK;
        return new ItemBuilder(isPushBack ? Material.BARRIER : Material.ENDER_EYE)
            .name(m("admin.setup.boundary-mode"))
            .lore(m("admin.setup.boundary-mode-current",
                      m(isPushBack ? "admin.setup.boundary-push-back" : "admin.setup.boundary-tp-center")),
                  "",
                  m("admin.setup.boundary-push-back-lore"),
                  m("admin.setup.boundary-tp-center-lore"),
                  "", m("admin.setup.boundary-click"))
            .build();
    }

    // ── Clics ────────────────────────────────────────────────────────────────

    public void handleClick(InventoryClickEvent event, Player admin) {
        event.setCancelled(true);
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlot() < 0 || event.getRawSlot() >= topSize) return;
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
        if (event.getCurrentItem().getType() == Material.GRAY_STAINED_GLASS_PANE
         || event.getCurrentItem().getType() == Material.BLACK_STAINED_GLASS_PANE) return;

        ArenaSetup setup = plugin.getArenaManager().getSetup(admin);
        if (setup == null) return;
        Arena arena = setup.getArena();
        ClickType click = event.getClick();

        switch (event.getRawSlot()) {
            case SLOT_LOBBY_SPAWN -> {
                setup.setSpawn(admin.getLocation());
                open(admin, arena);
            }
            case SLOT_GAME_SPAWN -> {
                if (click.isShiftClick()) {
                    arena.setGameSpawnLocation(null);
                    ChatUtil.send(admin, m("admin.setup.game-spawn-cleared"));
                } else {
                    arena.setGameSpawnLocation(admin.getLocation().clone());
                    ChatUtil.send(admin, m("admin.setup.game-spawn-set-msg", formatLoc(admin.getLocation())));
                }
                open(admin, arena);
            }
            case SLOT_CENTER -> {
                setup.setVillageCenter(admin.getLocation());
                open(admin, arena);
            }
            case SLOT_POS1 -> {
                arena.setPos1(admin.getLocation().clone());
                ChatUtil.send(admin, plugin.getMessages().get("setup.pos1-set", formatLoc(admin.getLocation())));
                open(admin, arena);
            }
            case SLOT_POS2 -> {
                arena.setPos2(admin.getLocation().clone());
                ChatUtil.send(admin, plugin.getMessages().get("setup.pos2-set", formatLoc(admin.getLocation())));
                open(admin, arena);
            }
            case SLOT_SHOP_CHEST -> {
                setup.setMode(ArenaSetup.WandMode.CHEST);
                admin.closeInventory();
            }
            case SLOT_BOUNDARY -> {
                arena.setBoundaryMode(arena.getBoundaryMode().next());
                open(admin, arena);
            }
            case SLOT_BACK -> plugin.getArenaSetupGui().open(admin, arena);
        }
    }

    private String formatLoc(org.bukkit.Location loc) {
        if (loc == null) return "?";
        return String.format("%.0f,%.0f,%.0f", loc.getX(), loc.getY(), loc.getZ());
    }
}
