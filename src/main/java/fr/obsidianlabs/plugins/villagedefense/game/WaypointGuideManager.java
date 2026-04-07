package fr.obsidianlabs.plugins.villagedefense.game;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaPath;
import fr.obsidianlabs.plugins.villagedefense.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gère les ArmorStands-guides pour le mode WAYPOINT.
 * Chaque chemin actif dans une arène a UN ArmorStand guide.
 * Les mobs assignés à ce chemin ciblent cet ArmorStand.
 * L'ArmorStand avance waypoint par waypoint, puis est supprimé
 * (les mobs retournent naturellement vers les villageois).
 */
public class WaypointGuideManager {

    private final VillageDefensePlugin plugin;

    // arenaId -> (pathId -> guide state)
    private final Map<String, Map<String, PathGuide>> activeGuides = new HashMap<>();

    // mobUUID -> pathId (quel chemin suit ce mob)
    private final Map<UUID, String> mobPathAssignment = new ConcurrentHashMap<>();

    // Tâche globale de pilotage (toutes les 5 ticks)
    private BukkitTask guideTask;

    public WaypointGuideManager(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        guideTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 5L);
    }

    public void stop() {
        if (guideTask != null) { guideTask.cancel(); guideTask = null; }
        for (var arenaEntry : activeGuides.values())
            for (var guide : arenaEntry.values())
                guide.remove();
        activeGuides.clear();
        mobPathAssignment.clear();
    }

    /**
     * Appelé par WaveManager après le spawn d'un mob.
     * spawnLoc est déjà +0,2,0 (tel que retourné par getRandomSpawnPoint).
     */
    public void registerMob(Arena arena, LivingEntity mob, Location spawnLoc) {
        if (!arena.hasPaths()) return;

        String assignedPathId = findPathForSpawn(arena, spawnLoc);
        if (assignedPathId == null) return;

        ArenaPath path = arena.getPath(assignedPathId);
        if (path == null || path.isEmpty()) return;

        mobPathAssignment.put(mob.getUniqueId(), assignedPathId);

        PathGuide guide = ensureGuide(arena, path);
        if (guide != null && guide.isActive()) {
            ((Creature) mob).setTarget(guide.armorStand);
        }
    }

    /** Appelé quand un mob meurt ou quitte l'arène. */
    public void unregisterMob(UUID mobUUID) {
        mobPathAssignment.remove(mobUUID);
    }

    /** Retourne true si ce mob est actuellement guidé par un chemin waypoint. */
    public boolean isMobRegistered(UUID uuid) {
        return mobPathAssignment.containsKey(uuid);
    }

    /** Appelé en fin de vague ou fin de partie : nettoie tous les guides de l'arène. */
    public void clearArena(Arena arena) {
        Map<String, PathGuide> guides = activeGuides.remove(arena.getId());
        if (guides != null) {
            for (PathGuide g : guides.values()) g.remove();
        }
        for (UUID uid : new HashSet<>(arena.getActiveMobUUIDs())) {
            mobPathAssignment.remove(uid);
        }
    }

    private void tick() {
        // Tout s'exécute sur le thread principal Bukkit → pas de risque de CME
        for (var arenaEntry : activeGuides.entrySet()) {
            for (var guide : arenaEntry.getValue().values()) {
                guide.tick();
            }
        }
    }

    private PathGuide ensureGuide(Arena arena, ArenaPath path) {
        activeGuides.computeIfAbsent(arena.getId(), k -> new HashMap<>());
        Map<String, PathGuide> arenaGuides = activeGuides.get(arena.getId());

        if (!arenaGuides.containsKey(path.getId())) {
            Location start = path.getWaypoints().get(0);
            if (start.getWorld() == null) return null;
            PathGuide guide = new PathGuide(arena, path, start);
            arenaGuides.put(path.getId(), guide);
        }
        return arenaGuides.get(path.getId());
    }

    private String findPathForSpawn(Arena arena, Location spawnLoc) {
        // spawnLoc est +0,2,0 — on compare contre les mob spawns élevés de même montant
        double bestDist = 16.0; // rayon 4 blocs²
        String bestPathId = null;
        for (Location mobSpawn : arena.getMobSpawnPoints()) {
            if (mobSpawn.getWorld() == null || !mobSpawn.getWorld().equals(spawnLoc.getWorld())) continue;
            Location elevated = mobSpawn.clone().add(0, 2, 0);
            double dist = elevated.distanceSquared(spawnLoc);
            if (dist < bestDist) {
                String serialized = LocationUtil.serialize(mobSpawn);
                String pathId = arena.getSpawnerPathId(serialized);
                if (pathId != null) {
                    bestDist = dist;
                    bestPathId = pathId;
                }
            }
        }
        return bestPathId;
    }

    // ── Inner class ────────────────────────────────────────────────────────────

    private class PathGuide {
        final Arena arena;
        final ArenaPath path;
        ArmorStand armorStand;
        int waypointIndex = 0;
        boolean finished = false;

        PathGuide(Arena arena, ArenaPath path, Location startLoc) {
            this.arena = arena;
            this.path = path;
            Location spawnAt = startLoc.clone().add(0, 0.1, 0);
            this.armorStand = startLoc.getWorld().spawn(spawnAt, ArmorStand.class, as -> {
                as.setVisible(false);
                as.setGravity(false);
                as.setInvulnerable(true);
                as.setSilent(true);
                as.setSmall(true);
                as.setMarker(true);
                as.setCustomNameVisible(false);
                as.setAI(false);
            });
        }

        boolean isActive() { return !finished && armorStand != null && armorStand.isValid(); }

        void tick() {
            if (finished || armorStand == null || !armorStand.isValid()) return;

            List<Location> waypoints = path.getWaypoints();
            if (waypointIndex >= waypoints.size()) {
                finish();
                return;
            }

            Location target = waypoints.get(waypointIndex);
            Location current = armorStand.getLocation();

            if (current.getWorld() == null || !current.getWorld().equals(target.getWorld())) {
                finish();
                return;
            }

            double distSq = current.distanceSquared(target);
            if (distSq < 9.0) { // < 3 blocs
                waypointIndex++;
                if (waypointIndex >= waypoints.size()) {
                    finish();
                    return;
                }
                target = waypoints.get(waypointIndex);
            }

            // Déplacer l'ArmorStand vers le prochain waypoint (0.3 bloc par tick)
            double dx = target.getX() - current.getX();
            double dy = target.getY() - current.getY();
            double dz = target.getZ() - current.getZ();
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len > 0) {
                double speed = 0.3;
                Location next = current.clone().add(dx / len * speed, dy / len * speed, dz / len * speed);
                armorStand.teleport(next);
            }

            retargetMobs();
        }

        private void retargetMobs() {
            for (UUID uid : arena.getActiveMobUUIDs()) {
                String assignedPath = mobPathAssignment.get(uid);
                if (!path.getId().equals(assignedPath)) continue;
                Entity e = plugin.getServer().getEntity(uid);
                if (e instanceof Creature creature && e.isValid() && !e.isDead()) {
                    LivingEntity currentTarget = creature.getTarget();
                    if (!(currentTarget instanceof ArmorStand)) {
                        creature.setTarget(armorStand);
                    }
                }
            }
        }

        void finish() {
            finished = true;
            // Libérer les mobs — ils vont naturellement cibler les villageois
            for (UUID uid : new HashSet<>(arena.getActiveMobUUIDs())) {
                if (path.getId().equals(mobPathAssignment.get(uid))) {
                    mobPathAssignment.remove(uid);
                    Entity e = plugin.getServer().getEntity(uid);
                    if (e instanceof Creature creature && e.isValid() && !e.isDead()) {
                        Villager v = plugin.getGameManager().findNearestVillager(arena, e.getLocation());
                        if (v != null) creature.setTarget(v);
                        else creature.setTarget(null);
                    }
                }
            }
            remove();
            Map<String, PathGuide> arenaGuides = activeGuides.get(arena.getId());
            if (arenaGuides != null) arenaGuides.remove(path.getId());
        }

        void remove() {
            if (armorStand != null && armorStand.isValid()) armorStand.remove();
            armorStand = null;
        }
    }
}
