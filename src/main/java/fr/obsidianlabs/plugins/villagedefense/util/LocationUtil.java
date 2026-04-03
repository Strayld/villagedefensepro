package fr.obsidianlabs.plugins.villagedefense.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

public final class LocationUtil {

    private LocationUtil() {}

    public static String serialize(Location loc) {
        if (loc == null || loc.getWorld() == null) return "null";
        return loc.getWorld().getName() + "," +
               loc.getX() + "," +
               loc.getY() + "," +
               loc.getZ() + "," +
               loc.getYaw() + "," +
               loc.getPitch();
    }

    public static Location deserialize(String s) {
        if (s == null || s.equals("null")) return null;
        String[] parts = s.split(",");
        if (parts.length < 4) return null;
        try {
            String world = parts[0];
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = parts.length > 4 ? Float.parseFloat(parts[4]) : 0f;
            float pitch = parts.length > 5 ? Float.parseFloat(parts[5]) : 0f;
            var w = Bukkit.getWorld(world);
            if (w == null) return null;
            return new Location(w, x, y, z, yaw, pitch);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static void save(ConfigurationSection section, String key, Location loc) {
        section.set(key, serialize(loc));
    }

    public static Location load(ConfigurationSection section, String key) {
        return deserialize(section.getString(key));
    }

    public static double distanceSquared2D(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }
}
