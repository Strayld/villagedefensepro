package fr.obsidianlabs.plugins.villagedefense.tower;

import org.bukkit.Location;
import org.bukkit.block.data.BlockData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Représente un emplacement prédéfini dans l'arène où une tour peut être placée.
 */
public class TowerSlot {

    private final String id;
    private final Location location;
    private Tower placedTower; // null si vide

    /**
     * Snapshot des blocs d'origine capturé juste avant la construction de la tour.
     * Clé = "dx,dy,dz" (offsets relatifs au slot). Null si aucun snapshot.
     */
    private Map<String, BlockData> preSnapshot;

    /**
     * Snapshot de l'emplacement (TOWER_SLOT schematic) capturé avant effacement lors du placement.
     * Permet de restaurer la plateforme quand la tour est vendue/retirée.
     */
    private Map<String, BlockData> slotSchematicSnapshot;

    public TowerSlot(Location location) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.location = location.clone();
    }

    public TowerSlot(String id, Location location) {
        this.id = id;
        this.location = location.clone();
    }

    public String getId() { return id; }
    public Location getLocation() { return location.clone(); }

    public boolean isEmpty() { return placedTower == null; }
    public Tower getPlacedTower() { return placedTower; }
    public void setPlacedTower(Tower tower) { this.placedTower = tower; }
    public void clearTower() { this.placedTower = null; preSnapshot = null; slotSchematicSnapshot = null; }

    public Map<String, BlockData> getPreSnapshot() { return preSnapshot; }
    public void setPreSnapshot(Map<String, BlockData> snapshot) { this.preSnapshot = snapshot; }

    public Map<String, BlockData> getSlotSchematicSnapshot() { return slotSchematicSnapshot; }
    public void setSlotSchematicSnapshot(Map<String, BlockData> snapshot) { this.slotSchematicSnapshot = snapshot; }

    /** Distance au carré depuis un point (pour trouver le slot le plus proche) */
    public double distanceSquaredFrom(Location loc) {
        if (!location.getWorld().equals(loc.getWorld())) return Double.MAX_VALUE;
        return location.distanceSquared(loc);
    }
}
