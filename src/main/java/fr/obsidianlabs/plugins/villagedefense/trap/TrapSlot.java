package fr.obsidianlabs.plugins.villagedefense.trap;

import org.bukkit.Location;

import java.util.UUID;

/**
 * Emplacement de piège placé par l'admin dans l'arène.
 * Durant la partie, les joueurs peuvent y acheter et activer un piège.
 */
public class TrapSlot {

    private final String   id;
    private final Location location;
    private TrapType placedTrap   = null;
    private long     cooldownUntil = 0L;
    private UUID owner;
    private boolean reinforced;

    public TrapSlot(Location location) {
        this.id       = UUID.randomUUID().toString().substring(0, 8);
        this.location = location.getBlock().getLocation().clone();
    }

    public TrapSlot(String id, Location location) {
        this.id       = id;
        this.location = location.getBlock().getLocation().clone();
    }

    // ── État ──────────────────────────────────────────────────────────────────

    public boolean isEmpty()      { return placedTrap == null; }
    public boolean isOnCooldown() { return System.currentTimeMillis() < cooldownUntil; }

    public void startCooldown(long durationMs) {
        cooldownUntil = System.currentTimeMillis() + durationMs;
    }

    public void clearTrap() {
        placedTrap    = null;
        cooldownUntil = 0L;
        owner = null;
        reinforced = false;
    }

    public void clearCooldown() { cooldownUntil = 0L; }

    public long getRemainingCooldownSeconds() {
        long remaining = cooldownUntil - System.currentTimeMillis();
        return remaining > 0 ? (remaining / 1000L) : 0L;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public String   getId()          { return id; }
    public Location getLocation()    { return location.clone(); }
    public TrapType getPlacedTrap()  { return placedTrap; }
    public void     setPlacedTrap(TrapType trap) { this.placedTrap = trap; }
    public UUID getOwner() { return owner; }
    public void setOwner(UUID owner) { this.owner = owner; }
    public boolean isReinforced() { return reinforced; }
    public void setReinforced(boolean reinforced) { this.reinforced = reinforced; }

    public double distanceSquaredFrom(Location other) {
        if (location.getWorld() == null || !location.getWorld().equals(other.getWorld()))
            return Double.MAX_VALUE;
        return location.distanceSquared(other);
    }
}
