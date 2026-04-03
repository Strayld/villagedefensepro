package fr.obsidianlabs.plugins.villagedefense.gui;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaSetup;
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
 * Sous-GUI « Emplacements Gameplay » — spawns mobs, villageois, marchands, enchanteur, tours, pièges, chemins.
 *
 * Layout 36 slots :
 *  Rangée 0 : séparateurs
 *  Rangée 1 : [sep][MOBS][VILLAGERS][MARCHANDS][ENCHANTEUR][TOURS][PATHS][PIEGES][sep]
 *  Rangée 2 : séparateurs
 *  Rangée 3 : [sep][sep][sep][sep][BACK][sep][sep][sep][sep]
 */
public class ArenaGameplayLocationsGUI {

    public static final String TITLE = "gui.arena-gameplay.title";

    private static final int SLOT_MOB_SPAWNS       = 10;
    private static final int SLOT_VILLAGER_SPAWNS  = 11;
    private static final int SLOT_SHOP_VILLAGER    = 12;
    private static final int SLOT_ENCHANT_VILLAGER = 13;
    private static final int SLOT_TOWERS           = 14;
    private static final int SLOT_PATHS            = 15;
    private static final int SLOT_TRAPS            = 16;
    private static final int SLOT_BACK             = 31;

    private final VillageDefensePlugin plugin;

    public ArenaGameplayLocationsGUI(VillageDefensePlugin plugin) {
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

        inv.setItem(SLOT_MOB_SPAWNS,       buildMobSpawnsItem(arena));
        inv.setItem(SLOT_VILLAGER_SPAWNS,  buildVillagerSpawnsItem(arena));
        inv.setItem(SLOT_SHOP_VILLAGER,    buildShopVillagerItem(arena));
        inv.setItem(SLOT_ENCHANT_VILLAGER, buildEnchantVillagerItem(arena));
        inv.setItem(SLOT_TOWERS,           buildTowersItem(arena));
        inv.setItem(SLOT_TRAPS,            buildTrapsItem(arena));
        inv.setItem(SLOT_PATHS,            buildPathsItem(arena));
        inv.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW)
            .name(m("admin.common.back")).build());

        admin.openInventory(inv);
    }

    // ── Builders ─────────────────────────────────────────────────────────────

    private ItemStack buildMobSpawnsItem(Arena arena) {
        int count = arena.getMobSpawnPoints().size();
        return new ItemBuilder(count > 0 ? Material.ZOMBIE_SPAWN_EGG : Material.BARRIER)
            .name(m("admin.setup.add-spawn") + " " + (count > 0 ? "&a(" + count + ")" : "&c(0)"))
            .lore(m("admin.setup.add-spawn-lore", count),
                  "", m("admin.setup.mob-spawns-lore-mode-add"), m("admin.setup.mob-spawns-lore-mode-remove"))
            .build();
    }

    private ItemStack buildVillagerSpawnsItem(Arena arena) {
        int count = arena.getVillagerSpawnPoints().size();
        return new ItemBuilder(count > 0 ? Material.VILLAGER_SPAWN_EGG : Material.BARRIER)
            .name(m("admin.setup.villager-spawns") + " " + (count > 0 ? "&a(" + count + ")" : "&c(0)"))
            .lore(m("admin.setup.villager-spawns-lore1", count),
                  m("admin.setup.villager-spawns-lore2"),
                  "", m("admin.setup.villager-spawns-click"), m("admin.setup.villager-spawns-hint"))
            .build();
    }

    private ItemStack buildShopVillagerItem(Arena arena) {
        boolean set = arena.getShopVillagerLocation() != null;
        return new ItemBuilder(set ? Material.VILLAGER_SPAWN_EGG : Material.BARRIER)
            .name(m("admin.setup.shop-villager-item") + (set ? " &a✓" : " &c✗"))
            .lore(set ? m("admin.setup.shop-villager-set", formatLoc(arena.getShopVillagerLocation()))
                      : m("admin.setup.shop-villager-unset"),
                  "", m("admin.setup.shop-villager-lore1"), m("admin.setup.shop-villager-lore2"),
                  "", m("admin.setup.shop-villager-click"), m("admin.setup.shop-villager-click2"))
            .build();
    }

    private ItemStack buildEnchantVillagerItem(Arena arena) {
        boolean set = arena.getEnchantVillagerLocation() != null;
        return new ItemBuilder(set ? Material.ENCHANTED_BOOK : Material.BARRIER)
            .name(m("admin.setup.enchant-villager-item") + (set ? " &a✓" : " &c✗"))
            .lore(set ? m("admin.setup.enchant-villager-set", formatLoc(arena.getEnchantVillagerLocation()))
                      : m("admin.setup.enchant-villager-unset"),
                  "", m("admin.setup.enchant-villager-lore1"), m("admin.setup.enchant-villager-lore2"),
                  "", m("admin.setup.enchant-villager-click"), m("admin.setup.enchant-villager-click2"))
            .build();
    }

    private ItemStack buildTowersItem(Arena arena) {
        int count = arena.getTowerSlots().size();
        return new ItemBuilder(count > 0 ? Material.GOLD_BLOCK : Material.BARRIER)
            .name(m("admin.setup.add-tower") + " " + (count > 0 ? "&a(" + count + ")" : "&c(0)"))
            .lore(m("admin.setup.add-tower-lore", count),
                  "", m("admin.setup.towers-lore-mode-add"), m("admin.setup.towers-lore-mode-remove"))
            .build();
    }

    private ItemStack buildTrapsItem(Arena arena) {
        int count = arena.getTrapSlots().size();
        return new ItemBuilder(count > 0 ? Material.TRIPWIRE_HOOK : Material.BARRIER)
            .name(m("admin.setup.add-trap") + " " + (count > 0 ? "&a(" + count + ")" : "&c(0)"))
            .lore(m("admin.setup.add-trap-lore", count),
                  "", m("admin.setup.trap-lore-click"),
                  m("admin.setup.trap-lore-add"), m("admin.setup.trap-lore-remove"))
            .build();
    }

    private ItemStack buildPathsItem(Arena arena) {
        int pathCount = arena.getPaths().size();
        long assignedCount = arena.getSpawnerPathAssignments().size();
        return new ItemBuilder(pathCount > 0 ? Material.LEAD : Material.STRING)
            .name(m("admin.setup.paths"))
            .lore(m("admin.setup.paths-lore1"),
                  m("admin.setup.paths-lore2", pathCount, assignedCount),
                  "",
                  m("admin.setup.paths-optional"),
                  "", m("admin.common.click-to-edit"))
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
            case SLOT_MOB_SPAWNS, SLOT_TOWERS -> {
                admin.closeInventory();
                if (click.isShiftClick()) {
                    setup.setMode(ArenaSetup.WandMode.REMOVE);
                    ChatUtil.send(admin, event.getRawSlot() == SLOT_MOB_SPAWNS
                        ? m("admin.setup.mob-spawns-wand-remove")
                        : m("admin.setup.towers-wand-remove"));
                } else {
                    setup.setMode(ArenaSetup.WandMode.DEFAULT);
                    ChatUtil.send(admin, event.getRawSlot() == SLOT_MOB_SPAWNS
                        ? m("admin.setup.mob-spawns-wand-add")
                        : m("admin.setup.towers-wand-add"));
                }
            }
            case SLOT_VILLAGER_SPAWNS -> {
                admin.closeInventory();
                setup.setMode(ArenaSetup.WandMode.VILLAGER_SPAWN);
                ChatUtil.send(admin, m("admin.setup.villager-wand"));
            }
            case SLOT_SHOP_VILLAGER -> {
                setup.setMode(ArenaSetup.WandMode.SHOP_VILLAGER);
                admin.closeInventory();
            }
            case SLOT_ENCHANT_VILLAGER -> {
                setup.setMode(ArenaSetup.WandMode.ENCHANT_VILLAGER);
                admin.closeInventory();
            }
            case SLOT_TRAPS -> {
                admin.closeInventory();
                setup.setMode(ArenaSetup.WandMode.TRAP);
                ChatUtil.send(admin, m("admin.setup.trap-wand"));
            }
            case SLOT_PATHS -> plugin.getArenaPathsGui().open(admin, arena);
            case SLOT_BACK -> plugin.getArenaSetupGui().open(admin, arena);
        }
    }

    private String formatLoc(org.bukkit.Location loc) {
        if (loc == null) return "?";
        return String.format("%.0f,%.0f,%.0f", loc.getX(), loc.getY(), loc.getZ());
    }
}
