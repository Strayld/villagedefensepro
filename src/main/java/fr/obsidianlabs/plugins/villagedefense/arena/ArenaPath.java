package fr.obsidianlabs.plugins.villagedefense.arena;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ArenaPath {

    private final String id;
    private final List<Location> waypoints = new ArrayList<>();

    public ArenaPath(String id) {
        this.id = id;
    }

    public String getId() { return id; }

    public List<Location> getWaypoints() { return Collections.unmodifiableList(waypoints); }

    public void addWaypoint(Location loc) { waypoints.add(loc.clone()); }

    public boolean removeLastWaypoint() {
        if (waypoints.isEmpty()) return false;
        waypoints.remove(waypoints.size() - 1);
        return true;
    }

    public void clearWaypoints() { waypoints.clear(); }

    public boolean isEmpty() { return waypoints.isEmpty(); }

    public int size() { return waypoints.size(); }
}
