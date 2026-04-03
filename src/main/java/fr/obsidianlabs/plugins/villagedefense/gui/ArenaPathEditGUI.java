package fr.obsidianlabs.plugins.villagedefense.gui;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaPath;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaSetup;
import fr.obsidianlabs.plugins.villagedefense.util.ChatUtil;
import fr.obsidianlabs.plugins.villagedefense.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;

public class ArenaPathEditGUI {

    public static final String TITLE = "gui.arena-path-edit.title";

    private static final int SLOT_INFO      = 13;
    private static final int SLOT_ADD_WP    = 20;
    private static final int SLOT_REMOVE_WP = 22;
    private static final int SLOT_VISUALIZE = 24;
    private static final int SLOT_SPAWNERS  = 31;
    private static final int SLOT_BACK      = 45;
    private static final int SLOT_CLEAR     = 49;

    private final VillageDefensePlugin plugin;

    public ArenaPathEditGUI(VillageDefensePlugin plugin) { this.plugin = plugin; }

    private String m(String key, Object... args) { return plugin.getMessages().format(key, args); }

    public void open(Player admin, Arena arena, String pathId) {
        ArenaPath path = arena.getPath(pathId);
        if (path == null) { plugin.getArenaPathsGui().open(admin, arena); return; }

        ArenaSetup setup = plugin.getArenaManager().getSetup(admin);
        if (setup != null) setup.setEditingPathId(pathId);

        Inventory inv = Bukkit.createInventory(null, 54, m(TITLE));
        for (int i = 0; i < 54; i++) inv.setItem(i, ItemBuilder.separator());

        long assignedCount = arena.getSpawnerPathAssignments().values().stream()
            .filter(v -> v.equals(pathId)).count();

        inv.setItem(SLOT_INFO, new ItemBuilder(Material.PAPER)
            .name("&e" + pathId)
            .lore(m("admin.paths.path-waypoints", path.size()),
                  m("admin.paths.path-spawners", assignedCount))
            .build());

        inv.setItem(SLOT_ADD_WP, new ItemBuilder(Material.BLAZE_ROD)
            .name(m("admin.paths.add-waypoint"))
            .lore(m("admin.paths.add-waypoint-lore"), "", m("admin.common.click-to-edit"))
            .build());

        inv.setItem(SLOT_REMOVE_WP, new ItemBuilder(Material.BONE)
            .name(m("admin.paths.remove-last-waypoint"))
            .lore(m("admin.paths.remove-last-lore", path.size()))
            .build());

        inv.setItem(SLOT_VISUALIZE, new ItemBuilder(Material.ENDER_EYE)
            .name(m("admin.paths.visualize"))
            .lore(m("admin.paths.visualize-lore"))
            .build());

        inv.setItem(SLOT_SPAWNERS, new ItemBuilder(Material.SPAWNER)
            .name(m("admin.paths.assign-spawners"))
            .lore(m("admin.paths.assign-spawners-lore"),
                  m("admin.paths.path-spawners", assignedCount),
                  "", m("admin.common.click-to-edit"))
            .build());

        inv.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW).name(m("admin.common.back")).build());

        inv.setItem(SLOT_CLEAR, new ItemBuilder(Material.TNT)
            .name(m("admin.paths.clear-waypoints"))
            .lore(m("admin.paths.clear-waypoints-lore"))
            .build());

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
        ArenaPath path = arena.getPath(pathId);
        if (path == null) return;

        switch (event.getRawSlot()) {
            case SLOT_BACK -> {
                setup.setEditingPathId(null);
                setup.setMode(ArenaSetup.WandMode.DEFAULT);
                plugin.getArenaPathsGui().open(admin, arena);
            }
            case SLOT_ADD_WP -> {
                setup.setMode(ArenaSetup.WandMode.WAYPOINT);
                admin.closeInventory();
                ChatUtil.send(admin, m("admin.paths.waypoint-mode-active", pathId));
            }
            case SLOT_REMOVE_WP -> {
                if (path.removeLastWaypoint()) {
                    plugin.getArenaManager().saveArena(arena);
                    ChatUtil.send(admin, m("admin.paths.waypoint-removed", path.size()));
                } else {
                    ChatUtil.send(admin, m("admin.paths.no-waypoints"));
                }
                open(admin, arena, pathId);
            }
            case SLOT_VISUALIZE -> {
                admin.closeInventory();
                visualizePath(admin, path);
            }
            case SLOT_SPAWNERS -> plugin.getArenaPathSpawnersGui().open(admin, arena, pathId);
            case SLOT_CLEAR -> {
                path.clearWaypoints();
                plugin.getArenaManager().saveArena(arena);
                ChatUtil.send(admin, m("admin.paths.waypoints-cleared", pathId));
                open(admin, arena, pathId);
            }
        }
    }

    private void visualizePath(Player admin, ArenaPath path) {
        if (path.isEmpty()) {
            ChatUtil.send(admin, m("admin.paths.no-waypoints"));
            return;
        }
        ChatUtil.send(admin, m("admin.paths.visualizing", path.size()));

        final int[] count = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!admin.isOnline() || count[0]++ >= 10) { task.cancel(); return; }
            List<Location> waypoints = path.getWaypoints();
            for (int i = 0; i < waypoints.size(); i++) {
                Location wp = waypoints.get(i).clone().add(0, 1, 0);
                if (wp.getWorld() == null) continue;
                admin.spawnParticle(Particle.FLAME, wp, 5, 0.2, 0.2, 0.2, 0.01);
                if (i > 0) spawnLine(admin, waypoints.get(i - 1).clone().add(0, 1, 0), wp);
            }
        }, 0L, 10L);
    }

    private void spawnLine(Player admin, Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = (int) (dist * 2);
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(255, 165, 0), 0.7f);
        for (int s = 1; s < steps; s++) {
            double t = (double) s / steps;
            Location point = from.clone().add(dx * t, dy * t, dz * t);
            if (point.getWorld() != null)
                admin.spawnParticle(Particle.REDSTONE, point, 1, dust);
        }
    }
}
