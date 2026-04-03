package fr.obsidianlabs.plugins.villagedefense.tower;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Représente une tour placée dans une arène.
 */
public abstract class Tower {

    protected final VillageDefensePlugin plugin;
    protected final TowerType type;
    protected final TowerSlot slot;

    protected int level = 1;
    protected int tickCounter = 0;
    protected TowerSpecialization specialization = TowerSpecialization.NONE;
    protected UUID owner;
    protected boolean reinforced;

    public Tower(VillageDefensePlugin plugin, TowerType type, TowerSlot slot) {
        this.plugin = plugin;
        this.type   = type;
        this.slot   = slot;
    }

    // --- Config helpers ---

    protected double getStatDouble(String key) {
        String path = "towers." + type.getConfigKey() + ".levels." + level + "." + key;
        return plugin.getConfig().getDouble(path,
            plugin.getConfig().getDouble("towers." + type.getConfigKey() + ".base-" + key, 0));
    }

    protected int getStatInt(String key) {
        return (int) getStatDouble(key);
    }

    public double getAttackInterval() {
        double baseInterval = plugin.getConfig().getDouble(
            "towers." + type.getConfigKey() + ".levels." + level + ".attack-interval",
            plugin.getConfig().getDouble("towers." + type.getConfigKey() + ".attack-interval", 20)
        );
        return plugin.getKitAbilityManager() != null
            ? plugin.getKitAbilityManager().adjustTowerAttackInterval(this, baseInterval)
            : baseInterval;
    }

    public double getRange() { return getRangeAtLevel(level); }

    public double getRangeAtLevel(int lvl) {
        return plugin.getConfig().getDouble(
            "towers." + type.getConfigKey() + ".levels." + lvl + ".range",
            plugin.getConfig().getDouble("towers." + type.getConfigKey() + ".base-range", 15)
        );
    }

    public double getDamage() { return getDamageAtLevel(level); }

    public double getDamageAtLevel(int lvl) {
        double baseDamage = plugin.getConfig().getDouble(
            "towers." + type.getConfigKey() + ".levels." + lvl + ".damage",
            plugin.getConfig().getDouble("towers." + type.getConfigKey() + ".base-damage", 6)
        );
        return plugin.getKitAbilityManager() != null
            ? plugin.getKitAbilityManager().adjustTowerDamage(this, baseDamage)
            : baseDamage;
    }

    public int getMaxLevel() { return 3; }

    public int getUpgradeCost() {
        int baseCost = plugin.getConfig().getInt("towers." + type.getConfigKey() + ".cost", 100);
        double mult = plugin.getConfig().getDouble("towers." + type.getConfigKey() + ".upgrade-cost-multiplier", 1.5);
        return (int)(baseCost * Math.pow(mult, level));
    }

    public int getSellValue() {
        int baseCost = plugin.getConfig().getInt("towers." + type.getConfigKey() + ".cost", 100);
        double mult = plugin.getConfig().getDouble("towers." + type.getConfigKey() + ".upgrade-cost-multiplier", 1.5);
        int totalCost = 0;
        for (int i = 0; i < level; i++) {
            totalCost += (int)(baseCost * Math.pow(mult, i));
        }
        return totalCost / 2; // 50% de remboursement
    }

    // --- Tick ---

    /** Appelé chaque tick par le TowerManager */
    public void tick(Arena arena) {
        tickCounter++;
        if (tickCounter >= getAttackInterval()) {
            tickCounter = 0;
            LivingEntity target = findTarget(arena);
            if (target != null) {
                attack(arena, target);
            }
        }
    }

    /** Trouve la cible la plus proche dans la portée */
    protected LivingEntity findTarget(Arena arena) {
        Location center = slot.getLocation();
        double rangeSq = getRange() * getRange();
        LivingEntity nearest = null;
        double nearestDist = rangeSq;

        for (UUID uid : arena.getActiveMobUUIDs()) {
            var entity = org.bukkit.Bukkit.getEntity(uid);
            if (!(entity instanceof LivingEntity living)) continue;
            if (!living.isValid() || living.isDead()) continue;
            double dist = living.getLocation().distanceSquared(center);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = living;
            }
        }
        return nearest;
    }

    /** Effectue l'attaque sur la cible */
    protected abstract void attack(Arena arena, LivingEntity target);

    // --- Upgrade ---

    public boolean canUpgrade() { return level < getMaxLevel(); }

    public void upgrade() {
        if (canUpgrade()) level++;
    }

    // --- Getters ---

    public TowerType getType() { return type; }
    public TowerSlot getSlot() { return slot; }
    public int getLevel() { return level; }
    public Location getLocation() { return slot.getLocation(); }
    public TowerSpecialization getSpecialization() { return specialization; }
    public void setSpecialization(TowerSpecialization spec) { this.specialization = spec; }
    public boolean hasSpecialization() { return specialization != TowerSpecialization.NONE; }
    public UUID getOwner() { return owner; }
    public void setOwner(UUID owner) { this.owner = owner; }
    public boolean isReinforced() { return reinforced; }
    public void setReinforced(boolean reinforced) { this.reinforced = reinforced; }
    public void readyAttack() { tickCounter = Math.max(0, (int) Math.ceil(getAttackInterval()) - 1); }

    /**
     * Retourne la position de tir : 2.5 blocs au-dessus du bloc le plus haut du schematic.
     * Évite que les projectiles spawent à l'intérieur de la structure.
     */
    public Location getFireLocation() {
        List<TowerSchematic.BlockOffset> blocks = TowerSchematic.getBlocks(type, level);
        OptionalInt maxDy = blocks.stream().mapToInt(TowerSchematic.BlockOffset::dy).max();
        double topY = maxDy.isPresent() ? maxDy.getAsInt() : 2;
        Location base = slot.getLocation();
        return new Location(base.getWorld(),
            base.getX() + 0.5,
            base.getY() + topY + 2.5,
            base.getZ() + 0.5);
    }

}
