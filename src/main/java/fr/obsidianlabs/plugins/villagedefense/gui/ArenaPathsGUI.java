package fr.obsidianlabs.plugins.villagedefense.gui;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaPath;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaSetup;
import fr.obsidianlabs.plugins.villagedefense.util.ChatUtil;
import fr.obsidianlabs.plugins.villagedefense.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

public class ArenaPathsGUI {

    public static final String TITLE = "gui.arena-paths.title";

    private static final int SLOT_ADD  = 4;
    private static final int SLOT_BACK = 49;

    // Slots de liste : rangées 1-3, colonnes 1-7 (slots 10-16, 19-25, 28-34)
    private static final int[] PATH_SLOTS = buildPathSlots();

    private final VillageDefensePlugin plugin;

    public ArenaPathsGUI(VillageDefensePlugin plugin) { this.plugin = plugin; }

    private String m(String key, Object... args) { return plugin.getMessages().format(key, args); }

    public void open(Player admin, Arena arena) {
        Inventory inv = Bukkit.createInventory(null, 54, m(TITLE));
        for (int i = 0; i < 54; i++) inv.setItem(i, ItemBuilder.separator());

        inv.setItem(SLOT_ADD, new ItemBuilder(Material.LIME_DYE)
            .name(m("admin.paths.add-path"))
            .lore(m("admin.paths.add-path-lore"), "", m("admin.common.click-to-edit"))
            .build());

        List<ArenaPath> paths = arena.getPaths();
        for (int i = 0; i < Math.min(paths.size(), PATH_SLOTS.length); i++) {
            ArenaPath path = paths.get(i);
            long assignedCount = arena.getSpawnerPathAssignments().values().stream()
                .filter(v -> v.equals(path.getId())).count();
            inv.setItem(PATH_SLOTS[i], new ItemBuilder(Material.LEAD)
                .name("&e" + path.getId())
                .lore(m("admin.paths.path-waypoints", path.size()),
                      m("admin.paths.path-spawners", assignedCount),
                      "",
                      m("admin.paths.left-edit"),
                      m("admin.paths.right-delete"))
                .build());
        }

        inv.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW).name(m("admin.common.back")).build());
        admin.openInventory(inv);
    }

    public void handleClick(InventoryClickEvent event, Player admin) {
        event.setCancelled(true);
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlot() < 0 || event.getRawSlot() >= topSize) return;
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR
                || event.getCurrentItem().getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        ArenaSetup setup = plugin.getArenaManager().getSetup(admin);
        if (setup == null) return;
        Arena arena = setup.getArena();

        if (event.getRawSlot() == SLOT_BACK) {
            plugin.getArenaSetupGui().open(admin, arena);
            return;
        }

        if (event.getRawSlot() == SLOT_ADD) {
            plugin.getChatInputManager().prompt(admin, m("admin.paths.prompt-path-id"), name -> {
                name = name.trim().replaceAll("[^a-zA-Z0-9_-]", "");
                if (name.isEmpty()) { ChatUtil.send(admin, m("admin.paths.invalid-id")); return; }
                if (arena.getPath(name) != null) { ChatUtil.send(admin, m("admin.paths.id-exists")); return; }
                arena.addPath(new ArenaPath(name));
                plugin.getArenaManager().saveArena(arena);
                ChatUtil.send(admin, m("admin.paths.created", name));
                open(admin, arena);
            });
            return;
        }

        for (int i = 0; i < PATH_SLOTS.length; i++) {
            if (PATH_SLOTS[i] == event.getRawSlot()) {
                List<ArenaPath> paths = arena.getPaths();
                if (i >= paths.size()) return;
                ArenaPath path = paths.get(i);
                if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) {
                    arena.removePath(path.getId());
                    plugin.getArenaManager().saveArena(arena);
                    ChatUtil.send(admin, m("admin.paths.deleted", path.getId()));
                    open(admin, arena);
                } else {
                    plugin.getArenaPathEditGui().open(admin, arena, path.getId());
                }
                return;
            }
        }
    }

    private static int[] buildPathSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row <= 3; row++)
            for (int col = 1; col <= 7; col++)
                slots.add(row * 9 + col);
        return slots.stream().mapToInt(Integer::intValue).toArray();
    }
}
