package fr.obsidianlabs.plugins.villagedefense.arena;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Sauvegarde l'état de tous les blocs dans une région cubique et les restaure
 * après la fin d'une partie.
 *
 * Capture : instantanée (un tour de boucle synchrone).
 * Restauration : batchée (BATCH_SIZE blocs / tick) pour éviter les lags.
 *               Les inventaires des conteneurs (coffres, barils, entonnoirs…)
 *               sont vidés à la fin de la restauration.
 */
public class RegionSnapshot {

    private static final int BATCH_SIZE = 2000;

    private record Entry(int x, int y, int z, BlockData data) {}

    private final String worldName;
    private final List<Entry>  entries;
    /** Positions des blocs-conteneurs détectés lors de la capture. */
    private final List<int[]>  containerPositions;

    private RegionSnapshot(String worldName, List<Entry> entries, List<int[]> containerPositions) {
        this.worldName          = worldName;
        this.entries            = entries;
        this.containerPositions = containerPositions;
    }

    // ── Capture ───────────────────────────────────────────────────────────────

    /**
     * Capture tous les blocs dans la région définie par pos1 et pos2.
     * Doit être appelé sur le thread principal.
     */
    public static RegionSnapshot capture(Location pos1, Location pos2) {
        World world = pos1.getWorld();
        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        int capacity = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        List<Entry> entries            = new ArrayList<>(capacity);
        List<int[]> containerPositions = new ArrayList<>();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    entries.add(new Entry(x, y, z,
                        world.getBlockAt(x, y, z).getBlockData().clone()));
                    // Mémoriser les conteneurs pour les vider à la restauration
                    if (world.getBlockAt(x, y, z).getState() instanceof InventoryHolder) {
                        containerPositions.add(new int[]{x, y, z});
                    }
                }
            }
        }

        return new RegionSnapshot(world.getName(), entries, containerPositions);
    }

    // ── Restauration ──────────────────────────────────────────────────────────

    /**
     * Restaure tous les blocs en BATCH_SIZE blocs par tick, puis vide les
     * inventaires de tous les conteneurs de la région.
     *
     * @param onComplete callback exécuté sur le thread principal une fois terminé.
     */
    public void restore(Plugin plugin, Runnable onComplete) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            if (onComplete != null) Bukkit.getScheduler().runTask(plugin, onComplete);
            return;
        }

        final int[] cursor = {0};

        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            int end = Math.min(cursor[0] + BATCH_SIZE, entries.size());
            for (int i = cursor[0]; i < end; i++) {
                Entry e = entries.get(i);
                // false = pas de physique (plus rapide, évite le déclenchement de redstone etc.)
                world.getBlockAt(e.x(), e.y(), e.z()).setBlockData(e.data(), false);
            }
            cursor[0] = end;

            if (cursor[0] >= entries.size()) {
                task.cancel();
                // Vider tous les conteneurs (coffres, barils, entonnoirs…)
                clearContainers(world);
                if (onComplete != null)
                    Bukkit.getScheduler().runTask(plugin, onComplete);
            }
        }, 0L, 1L);
    }

    /**
     * Vide l'inventaire de chaque conteneur enregistré lors de la capture.
     * Appelé synchroniquement juste après la dernière restauration de blocs.
     */
    private void clearContainers(World world) {
        for (int[] pos : containerPositions) {
            BlockState state = world.getBlockAt(pos[0], pos[1], pos[2]).getState();
            if (state instanceof InventoryHolder holder) {
                holder.getInventory().clear();
            }
        }
    }

    public int getBlockCount() { return entries.size(); }
}
