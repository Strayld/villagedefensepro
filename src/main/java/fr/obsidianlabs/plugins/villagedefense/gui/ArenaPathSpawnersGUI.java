package fr.obsidianlabs.plugins.villagedefense.gui;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaSetup;
import fr.obsidianlabs.plugins.villagedefense.util.ItemBuilder;
import fr.obsidianlabs.plugins.villagedefense.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

public class ArenaPathSpawnersGUI {

    public static final String TITLE = "gui.arena-path-spawners.title";

    private static final int SLOT_BACK = 49;
    private static final int[] SPAWNER_SLOTS = buildSpawnerSlots();

    private final VillageDefensePlugin plugin;

    public ArenaPathSpawnersGUI(VillageDefensePlugin plugin) { this.plugin = plugin; }

    private String m(String key, Object... args) { return plugin.getMessages().format(key, args); }

    public void open(Player admin, Arena arena, String pathId) {
        Inventory inv = Bukkit.createInventory(null, 54, m(TITLE));
        for (int i = 0; i < 54; i++) inv.setItem(i, ItemBuilder.separator());

        List<Location> spawners = arena.getMobSpawnPoints();
        for (int i = 0; i < Math.min(spawners.size(), SPAWNER_SLOTS.length); i++) {
            Location loc = spawners.get(i);
            String serialized = LocationUtil.serialize(loc);
            String assignedPath = arena.getSpawnerPathId(serialized);
            boolean isThisPath  = pathId.equals(assignedPath);
            boolean isOtherPath = assignedPath != null && !isThisPath;

            String statusColor = isThisPath ? "&a" : (isOtherPath ? "&6" : "&7");
            String statusText  = isThisPath
                ? m("admin.paths.spawner-assigned-this")
                : (isOtherPath
                    ? m("admin.paths.spawner-assigned-other", assignedPath)
                    : m("admin.paths.spawner-free"));

            inv.setItem(SPAWNER_SLOTS[i], new ItemBuilder(Material.ZOMBIE_SPAWN_EGG)
                .name(statusColor + "Spawner #" + (i + 1))
                .lore(m("admin.paths.spawner-location",
                        String.format("%.0f, %.0f, %.0f", loc.getX(), loc.getY(), loc.getZ())),
                      statusText,
                      "",
                      isThisPath
                          ? m("admin.paths.click-unassign")
                          : m("admin.paths.click-assign"))
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
        String pathId = setup.getEditingPathId();
        if (pathId == null) return;

        if (event.getRawSlot() == SLOT_BACK) {
            plugin.getArenaPathEditGui().open(admin, arena, pathId);
            return;
        }

        for (int i = 0; i < SPAWNER_SLOTS.length; i++) {
            if (SPAWNER_SLOTS[i] == event.getRawSlot()) {
                List<Location> spawners = arena.getMobSpawnPoints();
                if (i >= spawners.size()) return;
                Location loc = spawners.get(i);
                String serialized = LocationUtil.serialize(loc);
                String current = arena.getSpawnerPathId(serialized);
                if (pathId.equals(current)) {
                    arena.removeSpawnerPathAssignment(serialized);
                } else {
                    arena.setSpawnerPathAssignment(serialized, pathId);
                }
                plugin.getArenaManager().saveArena(arena);
                open(admin, arena, pathId);
                return;
            }
        }
    }

    private static int[] buildSpawnerSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row <= 4; row++)
            for (int col = 1; col <= 7; col++)
                slots.add(row * 9 + col);
        return slots.stream().mapToInt(Integer::intValue).toArray();
    }
}
