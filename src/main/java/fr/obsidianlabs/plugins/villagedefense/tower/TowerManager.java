package fr.obsidianlabs.plugins.villagedefense.tower;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaState;
import fr.obsidianlabs.plugins.villagedefense.compat.FloatingText;
import fr.obsidianlabs.plugins.villagedefense.compat.VersionCompat;
import fr.obsidianlabs.plugins.villagedefense.tower.impl.ArcherTower;
import fr.obsidianlabs.plugins.villagedefense.tower.impl.CannonTower;
import fr.obsidianlabs.plugins.villagedefense.tower.impl.IceTower;
import fr.obsidianlabs.plugins.villagedefense.tower.impl.LightningTower;
import fr.obsidianlabs.plugins.villagedefense.tower.impl.MageTower;
import fr.obsidianlabs.plugins.villagedefense.tower.impl.TrebuchetTower;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class TowerManager {

    private final VillageDefensePlugin plugin;

    // arenaId -> liste des tours actives
    private final Map<String, List<Tower>> arenaTowers = new HashMap<>();

    // arenaId -> tâches d'animation en cours (pour annulation lors de clearArena)
    private final Map<String, List<Integer>> animationTasks = new HashMap<>();
    // tower -> tâches d'animation propres à cette tour (pour annulation lors d'un upgrade)
    private final Map<Tower, List<Integer>> towerAnimTasks = new HashMap<>();

    // slot -> hologramme flottant au-dessus de l'emplacement (clé par instance = unique en multi-arena)
    private final Map<TowerSlot, FloatingText> slotDisplays = new HashMap<>();

    // Task du ticker global
    private BukkitTask tickTask;

    public TowerManager(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        TowerSchematic.init(plugin);
        // 4 ticks (200ms) est suffisant pour la précision des tours — divise par 4 le coût CPU
        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickAll, 1L, 4L);
    }

    public void shutdown() {
        if (tickTask != null) tickTask.cancel();
    }

    private void tickAll() {
        for (var entry : arenaTowers.entrySet()) {
            Arena arena = plugin.getArenaManager().getArena(entry.getKey());
            if (arena == null) continue;
            if (arena.getState() != ArenaState.RUNNING) continue;
            for (Tower tower : entry.getValue()) {
                tower.tick(arena);
            }
        }
    }

    // ── Placement ─────────────────────────────────────────────────────────────

    public Tower placeTower(Arena arena, TowerSlot slot, TowerType type, Player owner) {
        Tower tower = createTower(type, slot);
        slot.setPlacedTower(tower);
        if (owner != null && plugin.getKitAbilityManager() != null) {
            plugin.getKitAbilityManager().onTowerPlaced(owner, tower);
        }
        captureAndClearSlotSchematic(slot, arena.getId());
        captureSlotSnapshot(tower);
        arenaTowers.computeIfAbsent(arena.getId(), k -> new ArrayList<>()).add(tower);
        buildTowerWithAnimation(arena.getId(), tower);
        spawnOrUpdateSlotDisplay(slot, buildTowerText(tower), slotDisplayPos(slot, tower));
        return tower;
    }

    /** Capture l'état actuel de tous les blocs qui seront remplacés par le schematic. */
    private void captureSlotSnapshot(Tower tower) {
        Location base = tower.getLocation();
        List<TowerSchematic.BlockOffset> blocks = TowerSchematic.getBlocks(tower.getType(), tower.getLevel());
        Map<String, BlockData> snapshot = new LinkedHashMap<>();
        for (TowerSchematic.BlockOffset offset : blocks) {
            String key = offset.dx() + "," + offset.dy() + "," + offset.dz();
            Location loc = base.clone().add(offset.dx(), offset.dy(), offset.dz());
            snapshot.put(key, loc.getBlock().getBlockData().clone());
        }
        tower.getSlot().setPreSnapshot(snapshot);
    }

    private Tower createTower(TowerType type, TowerSlot slot) {
        return switch (type) {
            case ARCHER    -> new ArcherTower(plugin, slot);
            case TREBUCHET -> new TrebuchetTower(plugin, slot);
            case MAGE      -> new MageTower(plugin, slot);
            case ICE       -> new IceTower(plugin, slot);
            case LIGHTNING -> new LightningTower(plugin, slot);
            case CANNON    -> new CannonTower(plugin, slot);
        };
    }

    // ── Visuals ───────────────────────────────────────────────────────────────

    /**
     * Clears all schematic blocks for the tower's current level and
     * restores the GOLD_BLOCK slot marker at the base.
     */
    public void clearTowerVisual(Tower tower) {
        Location base = tower.getLocation();
        List<TowerSchematic.BlockOffset> blocks =
            TowerSchematic.getBlocks(tower.getType(), tower.getLevel());
        Map<String, BlockData> snapshot = tower.getSlot().getPreSnapshot();

        for (TowerSchematic.BlockOffset offset : blocks) {
            Location loc = base.clone().add(offset.dx(), offset.dy(), offset.dz());
            String key = offset.dx() + "," + offset.dy() + "," + offset.dz();
            if (snapshot != null && snapshot.containsKey(key)) {
                loc.getBlock().setBlockData(snapshot.get(key));
            } else {
                loc.getBlock().setType(Material.AIR);
            }
        }

        // Supprimer les entités décoratives dans la zone du schematic
        List<TowerSchematic.EntityOffset> entityOffsets = TowerSchematic.getEntities(tower.getType(), tower.getLevel());
        if (!entityOffsets.isEmpty()) {
            double radius = blocks.stream()
                .mapToDouble(b -> Math.sqrt(b.dx() * b.dx() + b.dy() * b.dy() + b.dz() * b.dz()))
                .max().orElse(3) + 2;
            for (Entity nearby : base.getWorld().getNearbyEntities(base.clone().add(0.5, radius / 2, 0.5), radius, radius, radius)) {
                if (TowerSchematic.isSupportedEntity(nearby.getType().name())) {
                    nearby.remove();
                }
            }
        }
    }

    /**
     * Places schematic blocks one-by-one with particle + sound animation.
     * Sorted bottom-to-top for a natural construction feel.
     * Task IDs are tracked so clearArena() can cancel any pending tasks.
     */
    private void buildTowerWithAnimation(String arenaId, Tower tower) {
        // Annuler les tâches d'animation encore en attente pour cette tour
        cancelTowerAnimTasks(tower);

        List<TowerSchematic.BlockOffset> blocks =
            new ArrayList<>(TowerSchematic.getBlocks(tower.getType(), tower.getLevel()));
        blocks.sort(Comparator.comparingInt(TowerSchematic.BlockOffset::dy));

        Location base = tower.getLocation();
        List<Integer> arenaTaskIds  = animationTasks.computeIfAbsent(arenaId, k -> new ArrayList<>());
        List<Integer> towerTaskIds  = new ArrayList<>();
        towerAnimTasks.put(tower, towerTaskIds);

        base.getWorld().playSound(base, Sound.BLOCK_ANVIL_USE, 0.8f, 1.4f);
        base.getWorld().spawnParticle(VersionCompat.SMOKE,
            base.clone().add(0.5, 0.5, 0.5), 20, 0.5, 0.5, 0.5, 0.04);

        for (int i = 0; i < blocks.size(); i++) {
            final TowerSchematic.BlockOffset offset = blocks.get(i);
            int taskId = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                Location loc = base.clone().add(offset.dx(), offset.dy(), offset.dz());
                loc.getBlock().setBlockData(Bukkit.createBlockData(offset.blockData()), false);
                loc.getWorld().spawnParticle(Particle.CRIT,
                    loc.clone().add(0.5, 0.5, 0.5), 4, 0.2, 0.2, 0.2, 0.0);
                loc.getWorld().playSound(loc, soundForMaterial(offset.material()), 0.4f, 1.0f);
            }, (long) i * 2).getTaskId();
            arenaTaskIds.add(taskId);
            towerTaskIds.add(taskId);
        }

        long finishTick = (long) blocks.size() * 2 + 4;
        int fanfareId = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Location top = base.clone().add(0.5, blocks.stream()
                .mapToInt(TowerSchematic.BlockOffset::dy).max().orElse(2) + 1.0, 0.5);
            top.getWorld().playSound(top, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.3f);
            top.getWorld().spawnParticle(VersionCompat.TOTEM_OF_UNDYING, top, 30, 0.5, 0.5, 0.5, 0.1);

            // Spawner les entités décoratives du schematic
            for (TowerSchematic.EntityOffset e : TowerSchematic.getEntities(tower.getType(), tower.getLevel())) {
                org.bukkit.entity.EntityType type = e.entityType();
                if (type != null) base.getWorld().spawnEntity(base.clone().add(e.dx(), e.dy(), e.dz()), type);
            }
        }, finishTick).getTaskId();
        arenaTaskIds.add(fanfareId);
        towerTaskIds.add(fanfareId);
    }

    private void cancelTowerAnimTasks(Tower tower) {
        List<Integer> tasks = towerAnimTasks.remove(tower);
        if (tasks != null) {
            for (int id : tasks) plugin.getServer().getScheduler().cancelTask(id);
        }
    }

    private Sound soundForMaterial(Material mat) {
        String n = mat.name();
        if (n.contains("LOG") || n.contains("PLANKS") || n.contains("FENCE") || n.contains("DARK_OAK"))
            return Sound.BLOCK_WOOD_PLACE;
        if (n.contains("AMETHYST"))
            return Sound.BLOCK_AMETHYST_BLOCK_PLACE;
        if (n.contains("QUARTZ") || n.contains("STONE") || n.contains("COBBLE"))
            return Sound.BLOCK_STONE_PLACE;
        return Sound.BLOCK_STONE_PLACE;
    }

    // ── Upgrade ───────────────────────────────────────────────────────────────

    public boolean upgradeTower(Tower tower, Player player) {
        if (!tower.canUpgrade()) return false;

        int cost = getEffectiveUpgradeCost(tower, player);
        var vdPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (vdPlayer == null || !vdPlayer.removeCoins(cost)) return false;

        cancelTowerAnimTasks(tower); // annuler les tâches d'animation du niveau précédent
        clearTowerVisual(tower);     // restaure les blocs d'origine
        tower.upgrade();             // incrémenter le niveau
        captureSlotSnapshot(tower); // capture new baseline for upgraded level
        // Find arenaId for this tower
        String arenaId = arenaTowers.entrySet().stream()
            .filter(e -> e.getValue().contains(tower))
            .map(Map.Entry::getKey).findFirst().orElse(null);
        buildTowerWithAnimation(arenaId != null ? arenaId : "", tower);
        spawnOrUpdateSlotDisplay(tower.getSlot(), buildTowerText(tower), slotDisplayPos(tower.getSlot(), tower));

        return true;
    }

    public int getEffectiveUpgradeCost(Tower tower, Player player) {
        int baseCost = tower.getUpgradeCost();
        if (plugin.getKitAbilityManager() == null || player == null) {
            return baseCost;
        }
        return plugin.getKitAbilityManager().applyStructureDiscount(player, baseCost);
    }

    /** Améliore une tour gratuitement (vague bonus — sans coût). Retourne false si déjà au max. */
    public boolean upgradeFree(Arena arena, Tower tower) {
        if (!tower.canUpgrade()) return false;
        cancelTowerAnimTasks(tower);
        clearTowerVisual(tower);
        tower.upgrade();
        captureSlotSnapshot(tower);
        buildTowerWithAnimation(arena.getId(), tower);
        spawnOrUpdateSlotDisplay(tower.getSlot(), buildTowerText(tower), slotDisplayPos(tower.getSlot(), tower));
        return true;
    }

    // ── Vente / Suppression ───────────────────────────────────────────────────

    public int sellTower(Arena arena, TowerSlot slot, Player player) {
        Tower tower = slot.getPlacedTower();
        if (tower == null) return -1;

        int refund = tower.getSellValue();
        var vdPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (vdPlayer != null) vdPlayer.addCoins(refund);

        removeTowerAndClear(arena, slot);
        return refund;
    }

    /** Removes a tower from arena tracking and clears its visual blocks. */
    public void removeTowerAndClear(Arena arena, TowerSlot slot) {
        Tower tower = slot.getPlacedTower();
        if (tower == null) return;
        cancelTowerAnimTasks(tower);
        clearTowerVisual(tower);
        restoreSlotSchematic(slot);
        slot.clearTower();
        List<Tower> towers = arenaTowers.get(arena.getId());
        if (towers != null) towers.remove(tower);
        spawnOrUpdateSlotDisplay(slot, buildEmptySlotText(), slotDisplayPos(slot, null));
    }

    // ── Nettoyage arène ───────────────────────────────────────────────────────

    public void clearArena(Arena arena) {
        // Annuler toutes les tâches d'animation en cours pour cette arène
        List<Integer> tasks = animationTasks.remove(arena.getId());
        if (tasks != null) {
            for (int taskId : tasks) {
                plugin.getServer().getScheduler().cancelTask(taskId);
            }
        }
        List<Tower> towers = arenaTowers.remove(arena.getId());
        if (towers != null) {
            for (Tower tower : towers) {
                cancelTowerAnimTasks(tower);
                clearTowerVisual(tower);
                tower.getSlot().clearTower();
            }
        }
        // Remove all slot displays for this arena
        for (TowerSlot slot : arena.getTowerSlots()) {
            removeSlotDisplay(slot);
        }
    }

    // ── Displays flottants ────────────────────────────────────────────────────

    /** Initialise les textes flottants pour tous les emplacements d'une arène. */
    public void initSlotDisplays(Arena arena) {
        for (TowerSlot slot : arena.getTowerSlots()) {
            if (slot.isEmpty()) {
                spawnOrUpdateSlotDisplay(slot, buildEmptySlotText(), slotDisplayPos(slot, null));
            } else {
                Tower tower = slot.getPlacedTower();
                spawnOrUpdateSlotDisplay(slot, buildTowerText(tower), slotDisplayPos(slot, tower));
            }
        }
    }

    private void spawnOrUpdateSlotDisplay(TowerSlot slot, String text, Location pos) {
        if (pos.getWorld() == null) return;
        FloatingText display = slotDisplays.computeIfAbsent(slot, key -> new FloatingText());
        display.update(pos, text);
    }

    private void removeSlotDisplay(TowerSlot slot) {
        FloatingText display = slotDisplays.remove(slot);
        if (display != null) display.remove();
    }

    private Location slotDisplayPos(TowerSlot slot, Tower tower) {
        Location base = slot.getLocation();
        if (tower == null) {
            return base.clone().add(0.5, 2.5, 0.5);
        }
        int maxDy = TowerSchematic.getBlocks(tower.getType(), tower.getLevel()).stream()
            .mapToInt(TowerSchematic.BlockOffset::dy).max().orElse(2);
        return base.clone().add(0.5, maxDy + 2.0, 0.5);
    }

    private String buildEmptySlotText() {
        return plugin.getMessages().format("tower.display.empty-slot");
    }

    private String buildTowerText(Tower tower) {
        var msg = plugin.getMessages();
        String name  = msg.format(tower.getType().getNameKey());
        String level = msg.format("tower.display.level", tower.getLevel());
        String base = "&6" + name + " &7- &d" + level;
        if (tower.hasSpecialization()) {
            String spec = msg.format("tower.display.spec-label",
                msg.format(tower.getSpecialization().getNameKey()));
            base += "\n&b" + spec;
        }
        return base;
    }


    // ── Schematic de slot ─────────────────────────────────────────────────────

    /** Capture les blocs du schematic TOWER_SLOT puis les efface. */
    private void captureAndClearSlotSchematic(TowerSlot slot, String arenaId) {
        List<TowerSchematic.BlockOffset> blocks =
            TowerSchematic.getBlocks(SchematicCategory.TOWER_SLOT, 1, arenaId);
        if (blocks.isEmpty()) return;
        Location base = slot.getLocation();
        Map<String, BlockData> snapshot = new LinkedHashMap<>();
        for (TowerSchematic.BlockOffset offset : blocks) {
            String key = offset.dx() + "," + offset.dy() + "," + offset.dz();
            Location loc = base.clone().add(offset.dx(), offset.dy(), offset.dz());
            snapshot.put(key, loc.getBlock().getBlockData().clone());
            loc.getBlock().setType(Material.AIR);
        }
        slot.setSlotSchematicSnapshot(snapshot);
    }

    /** Restaure les blocs du schematic TOWER_SLOT depuis le snapshot sauvegardé. */
    private void restoreSlotSchematic(TowerSlot slot) {
        Map<String, BlockData> snapshot = slot.getSlotSchematicSnapshot();
        if (snapshot == null) return;
        Location base = slot.getLocation();
        for (Map.Entry<String, BlockData> entry : snapshot.entrySet()) {
            String[] parts = entry.getKey().split(",");
            int dx = Integer.parseInt(parts[0]);
            int dy = Integer.parseInt(parts[1]);
            int dz = Integer.parseInt(parts[2]);
            base.clone().add(dx, dy, dz).getBlock().setBlockData(entry.getValue());
        }
        slot.setSlotSchematicSnapshot(null);
    }

    /** Rafraîchit le texte flottant d'une tour (ex. après changement de spécialisation). */
    public void refreshDisplay(Tower tower) {
        spawnOrUpdateSlotDisplay(tower.getSlot(), buildTowerText(tower),
            slotDisplayPos(tower.getSlot(), tower));
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public List<Tower> getTowersInArena(Arena arena) {
        return Collections.unmodifiableList(
            arenaTowers.getOrDefault(arena.getId(), Collections.emptyList()));
    }

    public int getTowerCost(TowerType type) {
        return plugin.getConfig().getInt("towers." + type.getConfigKey() + ".cost", 100);
    }

    /** Coût effectif : utilise l'override de l'arène si défini, sinon la valeur config. */
    public int getTowerCost(TowerType type, Arena arena) {
        int override = arena.getTowerCostOverride(type);
        return override >= 0 ? override : getTowerCost(type);
    }

    /** Niveau joueur minimum requis pour placer cette tour dans l'arène. */
    public int getTowerLevelRequired(TowerType type, Arena arena) {
        return arena.getTowerLevelRequired(type);
    }
}
