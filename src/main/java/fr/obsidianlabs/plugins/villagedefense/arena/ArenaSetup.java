package fr.obsidianlabs.plugins.villagedefense.arena;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.compat.VersionCompat;
import fr.obsidianlabs.plugins.villagedefense.tower.SchematicCategory;
import fr.obsidianlabs.plugins.villagedefense.tower.TowerSchematic;
import fr.obsidianlabs.plugins.villagedefense.tower.TowerSlot;
import fr.obsidianlabs.plugins.villagedefense.tower.TowerType;
import fr.obsidianlabs.plugins.villagedefense.trap.TrapSlot;
import fr.obsidianlabs.plugins.villagedefense.util.ChatUtil;
import fr.obsidianlabs.plugins.villagedefense.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Gère la session de configuration in-game d'une arène.
 * Mode actif = ce que fait la baguette quand l'admin clique.
 */
public class ArenaSetup {

    public enum WandMode {
        /** Shift+gauche = spawn mob | Shift+droit = emplacement tour */
        DEFAULT,
        /** Gauche = Pos1 | Droit = Pos2 (limites de l'arène) */
        BOUNDARY,
        /** Prochain clic droit sur un coffre = coffre shop */
        CHEST,
        /** Gauche = origine (0,0,0) | Droit = coin opposé → capture schematic */
        SCHEMATIC,
        /** Shift+gauche = supprimer spawn mob | Shift+droit = supprimer emplacement tour */
        REMOVE,
        /** Shift+droit = ajouter emplacement piège | Shift+gauche = supprimer emplacement piège */
        TRAP,
        /** Shift+droit = ajouter spawn villageois | Shift+gauche = supprimer spawn villageois */
        VILLAGER_SPAWN,
        /** Prochain clic droit sur un panneau = panneau d'arène */
        SIGN,
        /** Shift+droit = définir l'emplacement du villageois-marchand (shop) */
        SHOP_VILLAGER,
        /** Shift+droit = définir l'emplacement du villageois-enchanteur */
        ENCHANT_VILLAGER,
        /** Clic droit = ajouter waypoint | Clic gauche = supprimer le dernier waypoint */
        WAYPOINT
    }

    public static final String WAND_NBT_KEY  = "vd_setup_wand";
    public static final String SCHEM_ITEM_KEY = "vd_schem_item";

    private final VillageDefensePlugin plugin;
    private final Player admin;
    private final Arena arena;
    private WandMode mode = WandMode.DEFAULT;

    // Anti-doublon : timestamp du dernier ajout de point (évite les doubles clics Paper)
    private long lastAddMs = 0L;
    private static final long ADD_COOLDOWN_MS = 300L;

    // Capture de schematic
    private Location schematicOrigin;   // coin 1 (bas)
    private Location schematicPos2;     // coin 2 (haut) — null = pas encore défini
    private SchematicCategory pendingSchematicCategory;
    private TowerType pendingSchematicType;   // conservé pour compatibilité
    private int pendingSchematicLevel;

    // Chemin waypoint en cours d'édition
    private String editingPathId = null;

    // Visualisation de la zone (particules)
    private int particleTaskId = -1;

    // Aperçu de schematic in-game
    private java.util.Map<Location, BlockData> previewSnapshot = null;
    private java.util.List<java.util.UUID> previewEntityIds  = null;
    private Location previewPos2 = null;

    // ── Session hotbar de capture/édition de schematic ──
    private ItemStack[] savedInventory   = null;   // inventaire sauvegardé avant le hotbar
    private boolean     isEditMode       = false;  // true = édition avec aperçu ; false = capture manuelle
    private java.util.List<TowerSchematic.BlockOffset>  currentBlocks   = null;
    private java.util.List<TowerSchematic.EntityOffset> currentEntities = null;
    private Location    previewCenter    = null;

    public ArenaSetup(VillageDefensePlugin plugin, Player admin, Arena arena) {
        this.plugin = plugin;
        this.admin = admin;
        this.arena = arena;
    }

    private String m(String key, Object... args) {
        return plugin.getMessages().format(key, args);
    }

    public Arena getArena() { return arena; }
    public Player getAdmin() { return admin; }
    public WandMode getMode() { return mode; }
    public String getEditingPathId() { return editingPathId; }
    public void setEditingPathId(String id) { this.editingPathId = id; }

    public void setMode(WandMode mode) {
        if (this.mode == WandMode.SCHEMATIC && mode != WandMode.SCHEMATIC) {
            cancelZoneParticles();
            schematicPos2 = null;
        }
        this.mode = mode;
        updateWandLore();
        notifyModeChange();
    }

    // ---- Baguette ----

    public void giveWand() {
        ItemStack wand = buildWand();
        admin.getInventory().setItem(8, wand);
    }

    private ItemStack buildWand() {
        ItemStack wand = new ItemBuilder(Material.STICK)
            .name(m("admin.wand.name"))
            .lore(getLoreForMode())
            .glowing()
            .build();

        wand.editMeta(meta -> meta.getPersistentDataContainer().set(
            plugin.getKey(WAND_NBT_KEY), PersistentDataType.STRING, arena != null ? arena.getId() : "schematic_only"
        ));
        return wand;
    }

    private String[] getLoreForMode() {
        return switch (mode) {
            case DEFAULT -> new String[]{
                m("admin.wand.lore-default-1"),
                m("admin.wand.lore-default-2"),
                m("admin.wand.lore-default-3"),
                "",
                m("admin.wand.lore-default-5")
            };
            case BOUNDARY -> new String[]{
                m("admin.wand.lore-boundary-1"),
                m("admin.wand.lore-boundary-2"),
                m("admin.wand.lore-boundary-3"),
                "",
                m("admin.wand.lore-boundary-5")
            };
            case CHEST -> new String[]{
                m("admin.wand.lore-chest-1"),
                m("admin.wand.lore-chest-2"),
                m("admin.wand.lore-chest-3")
            };
            case SCHEMATIC -> new String[]{
                m("admin.wand.lore-schem-1"),
                m("admin.wand.lore-schem-2"),
                m("admin.wand.lore-schem-3"),
                m("admin.wand.lore-schem-4"),
                m("admin.wand.lore-schem-5"),
                "",
                m("admin.wand.lore-schem-zone",
                    currentSchematicName(),
                    pendingSchematicLevel)
                    + (schematicPos2 != null ? m("admin.wand.lore-schem-zone-ok") : "")
            };
            case REMOVE -> new String[]{
                m("admin.wand.lore-remove-1"),
                m("admin.wand.lore-remove-2"),
                m("admin.wand.lore-remove-3"),
                "",
                m("admin.wand.lore-remove-5")
            };
            case TRAP -> new String[]{
                m("admin.wand.lore-trap-1"),
                m("admin.wand.lore-trap-2"),
                m("admin.wand.lore-trap-3"),
                "",
                m("admin.wand.lore-trap-5")
            };
            case VILLAGER_SPAWN -> new String[]{
                m("admin.wand.lore-villager-1"),
                m("admin.wand.lore-villager-2"),
                m("admin.wand.lore-villager-3"),
                "",
                m("admin.wand.lore-villager-5")
            };
            case SIGN -> new String[]{
                m("admin.wand.lore-sign-1"),
                m("admin.wand.lore-sign-2")
            };
            case SHOP_VILLAGER -> new String[]{
                m("admin.wand.lore-shop-villager-1"),
                m("admin.wand.lore-shop-villager-2"),
                m("admin.wand.lore-shop-villager-3")
            };
            case ENCHANT_VILLAGER -> new String[]{
                m("admin.wand.lore-enchant-villager-1"),
                m("admin.wand.lore-enchant-villager-2"),
                m("admin.wand.lore-enchant-villager-3")
            };
            case WAYPOINT -> new String[]{
                m("admin.wand.lore-waypoint-1"),
                m("admin.wand.lore-waypoint-2"),
                m("admin.wand.lore-waypoint-3")
            };
        };
    }

    private void updateWandLore() {
        var item = admin.getInventory().getItem(8);
        if (item == null || !isWand(plugin, item)) return;
        item.editMeta(meta -> {
            var lore = java.util.Arrays.stream(getLoreForMode())
                .map(l -> ChatUtil.color("&7" + l))
                .toList();
            meta.setLore(lore);
        });
    }

    private void notifyModeChange() {
        String msg = switch (mode) {
            case DEFAULT        -> m("admin.wand.mode-default");
            case BOUNDARY       -> m("admin.wand.mode-boundary");
            case CHEST          -> m("admin.wand.mode-chest");
            case SCHEMATIC      -> m("admin.wand.mode-schematic");
            case REMOVE         -> m("admin.wand.mode-remove");
            case TRAP           -> m("admin.wand.mode-trap");
            case VILLAGER_SPAWN -> m("admin.wand.mode-villager");
            case SIGN            -> m("admin.wand.mode-sign");
            case SHOP_VILLAGER    -> m("admin.wand.mode-shop-villager");
            case ENCHANT_VILLAGER -> m("admin.wand.mode-enchant-villager");
            case WAYPOINT         -> m("admin.wand.mode-waypoint");
        };
        ChatUtil.send(admin, msg);
    }

    /** Passe en mode SCHEMATIC pour capturer la structure d'une tour (legacy). */
    public void startSchematicCapture(TowerType type, int level) {
        this.pendingSchematicType     = type;
        this.pendingSchematicCategory = SchematicCategory.ofTowerType(type);
        this.pendingSchematicLevel    = level;
        this.schematicOrigin          = null;
        this.schematicPos2            = null;
        setMode(WandMode.SCHEMATIC);
    }

    /** Passe en mode SCHEMATIC pour capturer n'importe quelle catégorie de schematic. */
    public void startSchematicCapture(SchematicCategory cat, int level) {
        this.pendingSchematicCategory = cat;
        this.pendingSchematicType     = cat.getTowerType(); // null pour non-tour
        this.pendingSchematicLevel    = level;
        this.schematicOrigin          = null;
        this.schematicPos2            = null;
        setMode(WandMode.SCHEMATIC);
    }

    public static boolean isWand(VillageDefensePlugin plugin, ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
            .has(plugin.getKey(WAND_NBT_KEY), PersistentDataType.STRING);
    }

    public static String getArenaIdFromWand(VillageDefensePlugin plugin, ItemStack item) {
        if (!isWand(plugin, item)) return null;
        return item.getItemMeta().getPersistentDataContainer()
            .get(plugin.getKey(WAND_NBT_KEY), PersistentDataType.STRING);
    }

    // ---- Actions par mode ----

    /** Clic gauche avec la baguette */
    public void onLeftClick(Location loc) {
        switch (mode) {
            case DEFAULT -> {
                if (arena == null) return; // session standalone : pas d'arène associée
                if (admin.isSneaking()) addMobSpawn(loc);
            }
            case BOUNDARY -> setPos1(loc);
            case CHEST -> {} // CHEST ne réagit qu'au clic droit sur bloc
            case SCHEMATIC -> {
                if (admin.isSneaking() && hasPreview()) {
                    clearPreview();
                    ChatUtil.send(admin, m("admin.schem.preview-cancelled"));
                    setMode(WandMode.DEFAULT);
                    return;
                }
                schematicOrigin = loc.getBlock().getLocation();
                schematicPos2 = null; // reset du coin 2 si on redéfinit le coin 1
                cancelZoneParticles();
                ChatUtil.send(admin, m("admin.schem.point1-set", formatLoc(loc)));
                updateWandLore();
            }
            case REMOVE -> {
                if (admin.isSneaking()) removeMobSpawnAt(loc);
            }
            case TRAP -> {
                if (admin.isSneaking()) removeTrapSlotAt(loc);
            }
            case VILLAGER_SPAWN -> {
                if (admin.isSneaking()) removeVillagerSpawnAt(loc);
            }
            case SIGN, SHOP_VILLAGER, ENCHANT_VILLAGER -> {} // géré dans PlayerListener
            case WAYPOINT -> {} // géré dans PlayerListener (ajout/suppression waypoint)
        }
    }

    /** Clic droit avec la baguette (dans l'air ou sur un bloc non-coffre) */
    public void onRightClick(Location loc) {
        switch (mode) {
            case DEFAULT -> {
                if (arena == null) return; // session standalone : pas d'arène associée
                if (admin.isSneaking()) addTowerSlot(loc);
            }
            case BOUNDARY -> setPos2(loc);
            case CHEST -> {} // géré dans ArenaConfigListener (clic sur bloc coffre)
            case SCHEMATIC -> {
                if (schematicOrigin == null) {
                    ChatUtil.send(admin, m("admin.schem.point1-missing"));
                    return;
                }
                Location clickedLoc = loc.getBlock().getLocation();
                // ── Premier clic droit : définir la zone et afficher les particules ──
                if (schematicPos2 == null) {
                    schematicPos2 = clickedLoc;
                    startZoneParticles(schematicOrigin, schematicPos2);
                    ChatUtil.send(admin, m("admin.schem.point2-set", formatLoc(schematicPos2)));
                    ChatUtil.send(admin, m("admin.schem.zone-ready"));
                    updateWandLore();
                    return;
                }
                // ── Deuxième clic droit : exécuter la capture ──
                cancelZoneParticles();
                String arenaId = arena != null ? arena.getId() : null;
                String name;
                if (pendingSchematicCategory != null) {
                    TowerSchematic.capture(plugin, pendingSchematicCategory, pendingSchematicLevel,
                        schematicOrigin, schematicPos2, arenaId);
                    name = m(pendingSchematicCategory.getNameKey());
                } else {
                    TowerSchematic.capture(plugin, pendingSchematicType, pendingSchematicLevel,
                        schematicOrigin, schematicPos2, arenaId);
                    name = m(pendingSchematicType.getNameKey());
                }
                ChatUtil.send(admin, arenaId != null
                    ? m("admin.schem.captured-local", name, pendingSchematicLevel, arenaId)
                    : m("admin.schem.captured-global", name, pendingSchematicLevel));
                // Supprimer les entités de la zone (preview ou autres) PUIS restaurer le terrain
                removeEntitiesInZone(schematicOrigin, schematicPos2);
                clearPreview();
                schematicPos2 = null;
                if (arena == null) {
                    admin.getInventory().setItem(8, null);
                    plugin.getArenaManager().endSetup(admin);
                } else {
                    setMode(WandMode.DEFAULT);
                }
                plugin.getSchematicCategoryGui().open(admin, arena);
            }
            case REMOVE -> {
                if (admin.isSneaking()) removeTowerSlotAt(loc);
            }
            case TRAP -> {
                if (admin.isSneaking()) addTrapSlot(loc);
            }
            case VILLAGER_SPAWN -> {
                if (admin.isSneaking()) addVillagerSpawn(loc);
            }
            case SIGN -> {} // géré dans PlayerListener (clic sur bloc panneau)
            case SHOP_VILLAGER -> {
                arena.setShopVillagerLocation(loc.clone());
                plugin.getArenaManager().saveArena(arena);
                ChatUtil.send(admin, m("admin.setup.shop-villager-placed", formatLoc(loc)));
                setMode(WandMode.DEFAULT);
            }
            case ENCHANT_VILLAGER -> {
                arena.setEnchantVillagerLocation(loc.clone());
                plugin.getArenaManager().saveArena(arena);
                ChatUtil.send(admin, m("admin.setup.enchant-villager-placed", formatLoc(loc)));
                setMode(WandMode.DEFAULT);
            }
            case WAYPOINT -> {} // géré dans PlayerListener
        }
    }

    /** Clic droit sur un panneau — uniquement en mode SIGN */
    public void onSignClick(Location signLoc) {
        if (mode != WandMode.SIGN) return;
        arena.setSignLocation(signLoc.getBlock().getLocation());
        ChatUtil.send(admin, m("admin.setup.sign-registered", formatLoc(signLoc)));
        setMode(WandMode.DEFAULT);
        plugin.getArenaSetupGui().open(admin, arena);
    }

    /** Clic droit sur un coffre — uniquement en mode CHEST */
    public void onChestClick(Location chestLoc) {
        if (mode != WandMode.CHEST) return;
        arena.setShopChestLocation(chestLoc.getBlock().getLocation());
        ChatUtil.send(admin, m("admin.setup.chest-registered", formatLoc(chestLoc)));
        setMode(WandMode.DEFAULT);
        // Rouvrir le GUI
        plugin.getArenaSetupGui().open(admin, arena);
    }

    // ---- Setters de configuration ----

    public void setSpawn(Location loc) {
        arena.setLobbyLocation(loc);
        ChatUtil.send(admin, m("admin.setup.player-spawn-set", formatLoc(loc)));
    }

    public void setVillageCenter(Location loc) {
        arena.setVillageCenter(loc);
        ChatUtil.send(admin, m("admin.setup.village-center-msg", formatLoc(loc)));
        // Marquer avec une particule
        loc.getWorld().spawnParticle(VersionCompat.HAPPY_VILLAGER, loc, 20, 1, 1, 1);
    }

    private void setPos1(Location loc) {
        arena.setPos1(loc.getBlock().getLocation());
        ChatUtil.send(admin, m("admin.setup.bounds-p1", formatLoc(loc)));
        drawBoundsPreview();
    }

    private void setPos2(Location loc) {
        arena.setPos2(loc.getBlock().getLocation());
        ChatUtil.send(admin, m("admin.setup.bounds-p2", formatLoc(loc)));
        drawBoundsPreview();
    }

    private boolean checkAddCooldown() {
        long now = System.currentTimeMillis();
        if (now - lastAddMs < ADD_COOLDOWN_MS) return false;
        lastAddMs = now;
        return true;
    }

    private void addMobSpawn(Location loc) {
        if (!checkAddCooldown()) return;
        arena.addMobSpawnPoint(loc);
        // Placer le schematic d'entrée de monstre (arène en priorité, puis global, sinon trou nether)
        var spawnBlocks = TowerSchematic.getBlocks(SchematicCategory.SPAWN_MONSTER, 1, arena.getId());
        if (!spawnBlocks.isEmpty()) {
            placeSchematic(loc, spawnBlocks);
        } else {
            buildNetherPit(loc);
        }
        ChatUtil.send(admin, plugin.getMessages().format("setup.spawn-added", arena.getMobSpawnPoints().size()));
        loc.getWorld().spawnParticle(Particle.FLAME, loc.clone().add(0.5, 0.5, 0.5), 10, 0.3, 0.3, 0.3);
    }

    private void addTowerSlot(Location loc) {
        if (!checkAddCooldown()) return;
        TowerSlot slot = new TowerSlot(loc);
        arena.addTowerSlot(slot);
        ChatUtil.send(admin, plugin.getMessages().format("setup.tower-slot-added", arena.getTowerSlots().size()));
        // Placer le schematic de marqueur (arène en priorité, puis global, sinon GOLD_BLOCK)
        var slotBlocks = TowerSchematic.getBlocks(SchematicCategory.TOWER_SLOT, 1, arena.getId());
        if (!slotBlocks.isEmpty()) {
            placeSchematic(loc, slotBlocks);
        } else {
            loc.getBlock().setType(Material.GOLD_BLOCK);
        }
        loc.getWorld().spawnParticle(VersionCompat.TOTEM_OF_UNDYING, loc.clone().add(0.5, 1, 0.5), 15, 0.3, 0.5, 0.3);
    }

    private void removeMobSpawnAt(Location loc) {
        Location nearest = arena.getNearestMobSpawn(loc, 9.0); // rayon 3 blocs
        if (nearest == null) {
            ChatUtil.send(admin, m("admin.setup.mob-spawn-not-found"));
            return;
        }
        arena.removeMobSpawnPoint(nearest);
        clearSchematic(nearest, SchematicCategory.SPAWN_MONSTER, 1, arena.getId());
        loc.getWorld().spawnParticle(VersionCompat.SMOKE, nearest.clone().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3);
        ChatUtil.send(admin, m("admin.setup.mob-spawn-removed", arena.getMobSpawnPoints().size()));
    }

    private void removeTowerSlotAt(Location loc) {
        TowerSlot nearest = arena.getNearestSlot(loc, 9.0); // rayon 3 blocs
        if (nearest == null) {
            ChatUtil.send(admin, m("admin.setup.tower-slot-not-found"));
            return;
        }
        if (!nearest.isEmpty()) {
            ChatUtil.send(admin, m("admin.setup.tower-slot-occupied"));
            return;
        }
        arena.removeTowerSlot(nearest);
        clearSchematic(nearest.getLocation(), SchematicCategory.TOWER_SLOT, 1, arena.getId());
        loc.getWorld().spawnParticle(VersionCompat.SMOKE, nearest.getLocation().clone().add(0.5, 1, 0.5), 20, 0.3, 0.5, 0.3);
        ChatUtil.send(admin, m("admin.setup.tower-slot-removed", arena.getTowerSlots().size()));
    }

    private void addTrapSlot(Location loc) {
        if (!checkAddCooldown()) return;
        TrapSlot slot = new TrapSlot(loc);
        arena.addTrapSlot(slot);
        loc.getBlock().setType(Material.SOUL_SAND); // marqueur visuel
        loc.getWorld().spawnParticle(VersionCompat.ENTITY_EFFECT,
            loc.clone().add(0.5, 1, 0.5), 15, 0.3, 0.5, 0.3, 0.1);
        ChatUtil.send(admin, m("admin.setup.trap-slot-added", arena.getTrapSlots().size()));
    }

    private void removeTrapSlotAt(Location loc) {
        TrapSlot nearest = arena.getNearestTrapSlot(loc, 9.0);
        if (nearest == null) {
            ChatUtil.send(admin, m("admin.setup.trap-slot-not-found"));
            return;
        }
        arena.removeTrapSlot(nearest);
        nearest.getLocation().getBlock().setType(Material.AIR);
        loc.getWorld().spawnParticle(VersionCompat.SMOKE,
            nearest.getLocation().clone().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3);
        ChatUtil.send(admin, m("admin.setup.trap-slot-removed", arena.getTrapSlots().size()));
    }

    private void addVillagerSpawn(Location loc) {
        if (!checkAddCooldown()) return;
        arena.addVillagerSpawnPoint(loc);
        loc.getWorld().spawnParticle(VersionCompat.HAPPY_VILLAGER, loc.clone().add(0.5, 0.5, 0.5), 15, 0.5, 0.5, 0.5);
        ChatUtil.send(admin, m("admin.setup.villager-spawn-added", arena.getVillagerSpawnPoints().size()));
    }

    private void removeVillagerSpawnAt(Location loc) {
        Location nearest = arena.getNearestVillagerSpawn(loc, 9.0);
        if (nearest == null) {
            ChatUtil.send(admin, m("admin.setup.villager-spawn-not-found"));
            return;
        }
        arena.removeVillagerSpawnPoint(nearest);
        loc.getWorld().spawnParticle(VersionCompat.SMOKE, nearest.clone().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3);
        ChatUtil.send(admin, m("admin.setup.villager-spawn-removed", arena.getVillagerSpawnPoints().size()));
    }

    // ---- Particules de zone ----

    /** Lance une tâche périodique qui dessine les 12 arêtes de la bbox avec des particules. */
    private void startZoneParticles(Location p1, Location p2) {
        cancelZoneParticles();
        if (p1 == null || p2 == null || p1.getWorld() == null) return;
        // Snapshot des coins pour la lambda (les références peuvent changer)
        final Location a = p1.clone();
        final Location b = p2.clone();
        particleTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> drawZoneParticles(a, b), 0L, 6L).getTaskId();
    }

    private void cancelZoneParticles() {
        if (particleTaskId != -1) {
            Bukkit.getScheduler().cancelTask(particleTaskId);
            particleTaskId = -1;
        }
    }

    /** Dessine les 12 arêtes du parallélépipède avec des particules END_ROD. */
    private void drawZoneParticles(Location p1, Location p2) {
        World w = p1.getWorld();
        if (w == null) return;
        double x1 = Math.min(p1.getBlockX(), p2.getBlockX());
        double y1 = Math.min(p1.getBlockY(), p2.getBlockY());
        double z1 = Math.min(p1.getBlockZ(), p2.getBlockZ());
        double x2 = Math.max(p1.getBlockX(), p2.getBlockX()) + 1;
        double y2 = Math.max(p1.getBlockY(), p2.getBlockY()) + 1;
        double z2 = Math.max(p1.getBlockZ(), p2.getBlockZ()) + 1;
        double step = 0.6;
        // Arêtes parallèles à X
        for (double x = x1; x <= x2; x += step) {
            w.spawnParticle(Particle.END_ROD, x, y1, z1, 1, 0, 0, 0, 0);
            w.spawnParticle(Particle.END_ROD, x, y1, z2, 1, 0, 0, 0, 0);
            w.spawnParticle(Particle.END_ROD, x, y2, z1, 1, 0, 0, 0, 0);
            w.spawnParticle(Particle.END_ROD, x, y2, z2, 1, 0, 0, 0, 0);
        }
        // Arêtes parallèles à Y
        for (double y = y1; y <= y2; y += step) {
            w.spawnParticle(Particle.END_ROD, x1, y, z1, 1, 0, 0, 0, 0);
            w.spawnParticle(Particle.END_ROD, x2, y, z1, 1, 0, 0, 0, 0);
            w.spawnParticle(Particle.END_ROD, x1, y, z2, 1, 0, 0, 0, 0);
            w.spawnParticle(Particle.END_ROD, x2, y, z2, 1, 0, 0, 0, 0);
        }
        // Arêtes parallèles à Z
        for (double z = z1; z <= z2; z += step) {
            w.spawnParticle(Particle.END_ROD, x1, y1, z, 1, 0, 0, 0, 0);
            w.spawnParticle(Particle.END_ROD, x2, y1, z, 1, 0, 0, 0, 0);
            w.spawnParticle(Particle.END_ROD, x1, y2, z, 1, 0, 0, 0, 0);
            w.spawnParticle(Particle.END_ROD, x2, y2, z, 1, 0, 0, 0, 0);
        }
    }

    /**
     * Supprime toutes les entités décoratives supportées dans la zone pos1→pos2.
     * Appelée AVANT clearPreview() pour s'assurer que les entités de l'aperçu
     * (modifiées ou non) ne restent pas dans le monde après la capture.
     */
    private void removeEntitiesInZone(Location pos1, Location pos2) {
        World w = pos1.getWorld();
        if (w == null) return;
        double cx = (pos1.getX() + pos2.getX()) / 2.0;
        double cy = (pos1.getY() + pos2.getY()) / 2.0;
        double cz = (pos1.getZ() + pos2.getZ()) / 2.0;
        double rx = Math.abs(pos1.getX() - pos2.getX()) / 2.0 + 1;
        double ry = Math.abs(pos1.getY() - pos2.getY()) / 2.0 + 1;
        double rz = Math.abs(pos1.getZ() - pos2.getZ()) / 2.0 + 1;
        for (Entity entity : w.getNearbyEntities(new Location(w, cx, cy, cz), rx, ry, rz)) {
            if (TowerSchematic.isSupportedEntity(entity.getType().name())) entity.remove();
        }
        // S'assurer aussi de supprimer les entités trackées (hors zone éventuelle)
        if (previewEntityIds != null) {
            for (java.util.UUID id : previewEntityIds) {
                Entity entity = Bukkit.getEntity(id);
                if (entity != null) entity.remove();
            }
            previewEntityIds = null;
        }
    }

    // ---- Aperçu de schematic ----

    /**
     * Spawne le schematic 5 blocs en face de l'admin pour inspection/modification.
     * Pré-configure automatiquement l'origine de capture (coin bas-gauche).
     * L'admin n'a plus qu'à faire un clic droit sur le coin opposé pour recapturer.
     * SHIFT+clic gauche en mode SCHEMATIC annule l'aperçu et restaure le terrain.
     */
    public void spawnPreview(SchematicCategory cat, int level) {
        clearPreview();

        // Centre : 5 blocs devant l'admin (direction horizontale)
        org.bukkit.util.Vector dir = admin.getLocation().getDirection().clone().setY(0);
        if (dir.lengthSquared() < 0.01) dir.setX(1);
        dir.normalize().multiply(5);
        Location center = admin.getLocation().clone().add(dir);
        center.setY(admin.getLocation().getBlockY());

        java.util.List<TowerSchematic.BlockOffset> blocks = TowerSchematic.getBlocks(cat, level);
        if (blocks.isEmpty()) {
            ChatUtil.send(admin, m("admin.schem.no-blocks"));
            return;
        }

        // Snapshot des blocs existants pour restauration ultérieure
        previewSnapshot = new java.util.LinkedHashMap<>();
        for (TowerSchematic.BlockOffset b : blocks) {
            Location loc = center.clone().add(b.dx(), b.dy(), b.dz());
            previewSnapshot.put(loc.clone(), loc.getBlock().getBlockData().clone());
        }

        // Placer les blocs
        placeSchematic(center, blocks);

        // Spawner les entités décoratives
        java.util.List<TowerSchematic.EntityOffset> entities = TowerSchematic.getEntities(cat, level);
        previewEntityIds = new java.util.ArrayList<>();
        for (TowerSchematic.EntityOffset e : entities) {
            EntityType type = e.entityType();
            if (type != null) {
                Entity entity = center.getWorld().spawnEntity(center.clone().add(e.dx(), e.dy(), e.dz()), type);
                previewEntityIds.add(entity.getUniqueId());
            }
        }

        // Stocker l'état courant pour rotation/recentrage (mode édition)
        currentBlocks   = new java.util.ArrayList<>(blocks);
        currentEntities = new java.util.ArrayList<>(entities);
        previewCenter   = center.clone();

        // Calculer les coins de la bounding box pour pré-configurer la capture
        int minDx = blocks.stream().mapToInt(TowerSchematic.BlockOffset::dx).min().orElse(0);
        int minDy = blocks.stream().mapToInt(TowerSchematic.BlockOffset::dy).min().orElse(0);
        int minDz = blocks.stream().mapToInt(TowerSchematic.BlockOffset::dz).min().orElse(0);
        int maxDx = blocks.stream().mapToInt(TowerSchematic.BlockOffset::dx).max().orElse(0);
        int maxDy = blocks.stream().mapToInt(TowerSchematic.BlockOffset::dy).max().orElse(0);
        int maxDz = blocks.stream().mapToInt(TowerSchematic.BlockOffset::dz).max().orElse(0);

        schematicOrigin = center.clone().add(minDx, minDy, minDz);
        previewPos2     = center.clone().add(maxDx, maxDy, maxDz);

        // Pré-configurer la capture : pos2 déjà connue, la zone est visible
        schematicPos2 = previewPos2.clone();

        pendingSchematicCategory = cat;
        pendingSchematicType     = cat.getTowerType();
        pendingSchematicLevel    = level;
        // setMode() en interne sans annuler la tâche qu'on va créer
        this.mode = WandMode.SCHEMATIC;
        updateWandLore();

        // Démarrer la visualisation de la zone
        startZoneParticles(schematicOrigin, schematicPos2);

        ChatUtil.send(admin, m("admin.schem.preview-placed", blocks.size()));
        ChatUtil.send(admin, m("admin.schem.preview-hint"));
        ChatUtil.send(admin, m("admin.schem.preview-cancel-hint"));
    }

    /** Restaure le terrain et supprime les entités trackées par l'aperçu. */
    public void clearPreview() {
        cancelZoneParticles();
        if (previewSnapshot != null) {
            for (java.util.Map.Entry<Location, BlockData> entry : previewSnapshot.entrySet()) {
                entry.getKey().getBlock().setBlockData(entry.getValue(), false);
            }
            previewSnapshot = null;
        }
        if (previewEntityIds != null) {
            for (java.util.UUID id : previewEntityIds) {
                Entity entity = Bukkit.getEntity(id);
                if (entity != null) entity.remove();
            }
            previewEntityIds = null;
        }
        previewPos2   = null;
        previewCenter = null;
    }

    public boolean hasPreview() { return previewSnapshot != null; }

    // ═══════════════════════════════════════════════════════════════════════
    //  MODE HOTBAR : CAPTURE & ÉDITION DE SCHEMATIC
    // ═══════════════════════════════════════════════════════════════════════

    /** Lance la session de capture manuelle (sans aperçu). */
    public void enterCaptureMode(SchematicCategory cat, int level) {
        if (isSchematicHotbarActive()) exitSchematicSession();
        if (hasPreview()) clearPreview();
        pendingSchematicCategory = cat;
        pendingSchematicType     = cat.getTowerType();
        pendingSchematicLevel    = level;
        schematicOrigin          = null;
        schematicPos2            = null;
        isEditMode               = false;
        currentBlocks            = null;
        currentEntities          = null;
        previewCenter            = null;
        cancelZoneParticles();
        saveAndClearInventory();
        giveCaptureHotbar();
        ChatUtil.send(admin, m("admin.schem.capture-mode"));
        ChatUtil.send(admin, m("admin.schem.capture-target", m(cat.getNameKey()), level));
    }

    /** Lance la session d'édition avec aperçu du schematic existant. */
    public void enterEditMode(SchematicCategory cat, int level) {
        if (isSchematicHotbarActive()) exitSchematicSession();
        pendingSchematicCategory = cat;
        pendingSchematicType     = cat.getTowerType();
        pendingSchematicLevel    = level;
        isEditMode               = true;
        spawnPreview(cat, level);   // place les blocs + remplit currentBlocks/Entities/previewCenter
        if (currentBlocks == null || currentBlocks.isEmpty()) {
            isEditMode = false;
            enterCaptureMode(cat, level);
            return;
        }
        saveAndClearInventory();
        giveEditHotbar();
        ChatUtil.send(admin, m("admin.schem.edit-mode"));
        ChatUtil.send(admin, m("admin.schem.edit-cancel-hint"));
    }

    // ── Gestion de l'inventaire ────────────────────────────────────────────

    private void saveAndClearInventory() {
        savedInventory = admin.getInventory().getContents().clone();
        admin.getInventory().clear();
    }

    private void giveCaptureHotbar() {
        var inv = admin.getInventory();
        inv.setItem(0, buildSchemItem(Material.LIME_CONCRETE,    m("admin.schem.hotbar-pos1"), "pos1",
            m("admin.schem.hotbar-pos1-lore"), m("admin.schem.hotbar-pos1-hint")));
        inv.setItem(1, buildSchemItem(Material.RED_CONCRETE,     m("admin.schem.hotbar-pos2"), "pos2",
            m("admin.schem.hotbar-pos2-lore"), m("admin.schem.hotbar-pos2-hint")));
        inv.setItem(2, buildSchemItem(Material.CYAN_STAINED_GLASS_PANE,  m("admin.schem.hotbar-width-plus"), "width_plus",
            m("admin.schem.hotbar-width-plus-lore")));
        inv.setItem(3, buildSchemItem(Material.CYAN_STAINED_GLASS_PANE,  m("admin.schem.hotbar-width-minus"), "width_minus",
            m("admin.schem.hotbar-width-minus-lore")));
        inv.setItem(4, buildSchemItem(Material.PURPLE_STAINED_GLASS_PANE, m("admin.schem.hotbar-height-plus"), "height_plus",
            m("admin.schem.hotbar-height-plus-lore")));
        inv.setItem(5, buildSchemItem(Material.PURPLE_STAINED_GLASS_PANE, m("admin.schem.hotbar-height-minus"), "height_minus",
            m("admin.schem.hotbar-height-minus-lore")));
        inv.setItem(7, buildSchemItem(Material.LIME_DYE,         m("admin.schem.hotbar-validate"), "validate",
            m("admin.schem.hotbar-validate-lore")));
        inv.setItem(8, buildSchemItem(Material.RED_DYE,          m("admin.schem.hotbar-cancel"), "cancel",
            m("admin.schem.hotbar-cancel-lore")));
    }

    private void giveEditHotbar() {
        var inv = admin.getInventory();
        inv.setItem(0, buildSchemItem(Material.BLUE_DYE,                  m("admin.schem.hotbar-rot-ccw"), "rot_ccw",
            m("admin.schem.hotbar-rot-ccw-lore")));
        inv.setItem(1, buildSchemItem(Material.YELLOW_DYE,                m("admin.schem.hotbar-rot-cw"), "rot_cw",
            m("admin.schem.hotbar-rot-cw-lore")));
        inv.setItem(2, buildSchemItem(Material.CYAN_STAINED_GLASS_PANE,   m("admin.schem.hotbar-width-plus"), "width_plus",
            m("admin.schem.hotbar-width-plus-lore")));
        inv.setItem(3, buildSchemItem(Material.CYAN_STAINED_GLASS_PANE,   m("admin.schem.hotbar-width-minus"), "width_minus",
            m("admin.schem.hotbar-width-minus-lore")));
        inv.setItem(4, buildSchemItem(Material.PURPLE_STAINED_GLASS_PANE, m("admin.schem.hotbar-height-plus"), "height_plus",
            m("admin.schem.hotbar-height-plus-lore")));
        inv.setItem(5, buildSchemItem(Material.PURPLE_STAINED_GLASS_PANE, m("admin.schem.hotbar-height-minus"), "height_minus",
            m("admin.schem.hotbar-height-minus-lore")));
        inv.setItem(6, buildSchemItem(Material.COMPASS,           m("admin.schem.hotbar-recenter"), "recenter",
            m("admin.schem.hotbar-recenter-lore")));
        inv.setItem(7, buildSchemItem(Material.LIME_DYE,          m("admin.schem.hotbar-validate"), "validate",
            m("admin.schem.hotbar-validate-edit-lore")));
        inv.setItem(8, buildSchemItem(Material.RED_DYE,           m("admin.schem.hotbar-cancel"), "cancel",
            m("admin.schem.hotbar-cancel-edit-lore")));
    }

    private ItemStack buildSchemItem(Material mat, String name, String action, String... loreLines) {
        ItemStack item = new ItemBuilder(mat).name(name).lore(loreLines).glowing().build();
        item.editMeta(meta -> meta.getPersistentDataContainer()
            .set(plugin.getKey(SCHEM_ITEM_KEY), PersistentDataType.STRING, action));
        return item;
    }

    // ── Détection d'items hotbar ───────────────────────────────────────────

    public boolean isSchematicHotbarActive() { return savedInventory != null; }

    public static boolean isSchemItem(VillageDefensePlugin plugin, ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
            .has(plugin.getKey(SCHEM_ITEM_KEY), PersistentDataType.STRING);
    }

    public static String getSchemAction(VillageDefensePlugin plugin, ItemStack item) {
        if (!isSchemItem(plugin, item)) return null;
        return item.getItemMeta().getPersistentDataContainer()
            .get(plugin.getKey(SCHEM_ITEM_KEY), PersistentDataType.STRING);
    }

    // ── Traitement des clics ───────────────────────────────────────────────

    public void onSchemItemClick(String action) {
        switch (action) {
            case "pos1" -> {
                schematicOrigin = admin.getLocation().getBlock().getLocation();
                if (schematicPos2 != null) { cancelZoneParticles(); startZoneParticles(schematicOrigin, schematicPos2); }
                ChatUtil.send(admin, schematicPos2 != null
                    ? m("admin.schem.pos1-with-pos2", formatLoc(schematicOrigin))
                    : m("admin.schem.pos1-without-pos2", formatLoc(schematicOrigin)));
            }
            case "pos2" -> {
                schematicPos2 = admin.getLocation().getBlock().getLocation();
                if (schematicOrigin != null) { cancelZoneParticles(); startZoneParticles(schematicOrigin, schematicPos2); }
                ChatUtil.send(admin, schematicOrigin != null
                    ? m("admin.schem.pos2-with-pos1", formatLoc(schematicPos2))
                    : m("admin.schem.pos2-without-pos1", formatLoc(schematicPos2)));
            }
            case "width_plus"  -> adjustZone(1,  0);
            case "width_minus" -> adjustZone(-1, 0);
            case "height_plus" -> adjustZone(0,  1);
            case "height_minus"-> adjustZone(0, -1);
            case "rot_cw"      -> rotatePending(true);
            case "rot_ccw"     -> rotatePending(false);
            case "recenter"    -> recenterPreview();
            case "validate"    -> validateSchematicSession();
            case "cancel"      -> cancelSchematicSession();
        }
    }

    private void adjustZone(int horizDelta, int vertDelta) {
        if (schematicOrigin == null || schematicPos2 == null) {
            ChatUtil.send(admin, m("admin.schem.pos-missing"));
            return;
        }
        if (horizDelta != 0) {
            int minX = Math.min(schematicOrigin.getBlockX(), schematicPos2.getBlockX());
            int maxX = Math.max(schematicOrigin.getBlockX(), schematicPos2.getBlockX());
            int minZ = Math.min(schematicOrigin.getBlockZ(), schematicPos2.getBlockZ());
            int maxZ = Math.max(schematicOrigin.getBlockZ(), schematicPos2.getBlockZ());
            if (horizDelta < 0 && maxX - minX < 2 && maxZ - minZ < 2) return;
            schematicOrigin.setX(minX - horizDelta);
            schematicOrigin.setZ(minZ - horizDelta);
            schematicPos2.setX(maxX + horizDelta);
            schematicPos2.setZ(maxZ + horizDelta);
        }
        if (vertDelta != 0) {
            int minY = Math.min(schematicOrigin.getBlockY(), schematicPos2.getBlockY());
            int maxY = Math.max(schematicOrigin.getBlockY(), schematicPos2.getBlockY());
            if (vertDelta < 0 && maxY <= minY) return;
            schematicPos2.setY(maxY + vertDelta);
        }
        cancelZoneParticles();
        startZoneParticles(schematicOrigin, schematicPos2);
        int w = Math.abs(schematicPos2.getBlockX() - schematicOrigin.getBlockX()) + 1;
        int d = Math.abs(schematicPos2.getBlockZ() - schematicOrigin.getBlockZ()) + 1;
        int h = Math.abs(schematicPos2.getBlockY() - schematicOrigin.getBlockY()) + 1;
        ChatUtil.send(admin, m("admin.schem.zone-size", w, d, h));
    }

    private void rotatePending(boolean clockwise) {
        if (!isEditMode || currentBlocks == null || previewCenter == null) {
            ChatUtil.send(admin, m("admin.schem.rotate-only-edit"));
            return;
        }
        // 1. Restaurer le terrain et supprimer les entités
        if (previewSnapshot != null) {
            for (var entry : previewSnapshot.entrySet())
                entry.getKey().getBlock().setBlockData(entry.getValue(), false);
            previewSnapshot = null;
        }
        if (previewEntityIds != null) {
            for (java.util.UUID id : previewEntityIds) { Entity e = Bukkit.getEntity(id); if (e != null) e.remove(); }
            previewEntityIds = null;
        }
        // 2. Rotation des blocs : CW (dx,dz)→(dz,-dx) ; CCW (dx,dz)→(-dz,dx)
        StructureRotation sr = clockwise ? StructureRotation.CLOCKWISE_90 : StructureRotation.COUNTERCLOCKWISE_90;
        java.util.List<TowerSchematic.BlockOffset> newBlocks = new java.util.ArrayList<>();
        for (TowerSchematic.BlockOffset b : currentBlocks) {
            int newDx = clockwise ?  b.dz() : -b.dz();
            int newDz = clockwise ? -b.dx() :  b.dx();
            BlockData bd;
            try {
                bd = Bukkit.createBlockData(b.blockData());
                VersionCompat.rotateBlockData(bd, sr);
            }
            catch (Exception ex) { bd = Bukkit.createBlockData(b.blockData()); }
            newBlocks.add(new TowerSchematic.BlockOffset(newDx, b.dy(), newDz, bd.getAsString()));
        }
        currentBlocks = newBlocks;
        // 3. Rotation des entités
        java.util.List<TowerSchematic.EntityOffset> newEntities = new java.util.ArrayList<>();
        for (TowerSchematic.EntityOffset e : currentEntities) {
            double newDx = clockwise ?  e.dz() : -e.dz();
            double newDz = clockwise ? -e.dx() :  e.dx();
            newEntities.add(new TowerSchematic.EntityOffset(newDx, e.dy(), newDz, e.entityTypeName()));
        }
        currentEntities = newEntities;
        // 4. Recalculer la zone + replacer
        recalcZoneFromCurrentBlocks();
        takePreviewSnapshot();
        placeCurrentBlocks();
        spawnCurrentEntities();
        cancelZoneParticles();
        if (schematicOrigin != null && schematicPos2 != null)
            startZoneParticles(schematicOrigin, schematicPos2);
        ChatUtil.send(admin, m("admin.schem.rotated",
            m(clockwise ? "admin.schem.rotation-cw" : "admin.schem.rotation-ccw")));
    }

    private void recenterPreview() {
        if (!isEditMode || currentBlocks == null || previewCenter == null) {
            ChatUtil.send(admin, m("admin.schem.recenter-only-edit"));
            return;
        }
        // Restaurer
        if (previewSnapshot != null) {
            for (var entry : previewSnapshot.entrySet())
                entry.getKey().getBlock().setBlockData(entry.getValue(), false);
            previewSnapshot = null;
        }
        if (previewEntityIds != null) {
            for (java.util.UUID id : previewEntityIds) { Entity e = Bukkit.getEntity(id); if (e != null) e.remove(); }
            previewEntityIds = null;
        }
        // Nouveau centre
        org.bukkit.util.Vector dir = admin.getLocation().getDirection().clone().setY(0);
        if (dir.lengthSquared() < 0.01) dir.setX(1);
        dir.normalize().multiply(5);
        previewCenter = admin.getLocation().clone().add(dir);
        previewCenter.setY(admin.getLocation().getBlockY());
        recalcZoneFromCurrentBlocks();
        takePreviewSnapshot();
        placeCurrentBlocks();
        spawnCurrentEntities();
        cancelZoneParticles();
        if (schematicOrigin != null && schematicPos2 != null)
            startZoneParticles(schematicOrigin, schematicPos2);
        ChatUtil.send(admin, m("admin.schem.recentered"));
    }

    // ── Validation / Annulation ────────────────────────────────────────────

    private void validateSchematicSession() {
        if (!isEditMode && (schematicOrigin == null || schematicPos2 == null)) {
            ChatUtil.send(admin, m("admin.schem.validate-pos-missing"));
            return;
        }
        if (isEditMode && (schematicOrigin == null || schematicPos2 == null)) {
            ChatUtil.send(admin, m("admin.schem.zone-undefined")); return;
        }
        String arenaId = arena != null ? arena.getId() : null;
        if (isEditMode) {
            // Mode édition : sauvegarder directement depuis les listes en mémoire
            // pour garantir que toutes les entités (ex. cristaux de l'End) sont préservées
            // sans dépendre d'un re-scan du monde potentiellement inexact.
            if (pendingSchematicCategory != null) {
                TowerSchematic.save(plugin, pendingSchematicCategory, pendingSchematicLevel,
                    currentBlocks, currentEntities != null ? currentEntities : java.util.List.of(), arenaId);
            } else if (pendingSchematicType != null) {
                TowerSchematic.save(plugin, pendingSchematicType, pendingSchematicLevel,
                    currentBlocks, currentEntities != null ? currentEntities : java.util.List.of(), arenaId);
            }
            removeEntitiesInZone(schematicOrigin, schematicPos2);
        } else {
            // Mode capture : scanner le monde entre pos1 et pos2
            if (pendingSchematicCategory != null) {
                TowerSchematic.capture(plugin, pendingSchematicCategory, pendingSchematicLevel,
                    schematicOrigin, schematicPos2, arenaId);
            } else if (pendingSchematicType != null) {
                TowerSchematic.capture(plugin, pendingSchematicType, pendingSchematicLevel,
                    schematicOrigin, schematicPos2, arenaId);
            }
        }
        String name = currentSchematicName();
        ChatUtil.send(admin, arenaId != null
            ? m("admin.schem.captured-local", name, pendingSchematicLevel, arenaId)
            : m("admin.schem.captured-global", name, pendingSchematicLevel));
        exitSchematicSession();
        if (arena == null) plugin.getArenaManager().endSetup(admin);
        plugin.getSchematicCategoryGui().open(admin, arena);
    }

    private String currentSchematicName() {
        if (pendingSchematicCategory != null) {
            return m(pendingSchematicCategory.getNameKey());
        }
        if (pendingSchematicType != null) {
            return m(pendingSchematicType.getNameKey());
        }
        return "?";
    }

    private void cancelSchematicSession() {
        ChatUtil.send(admin, m("admin.schem.capture-cancelled"));
        exitSchematicSession();
        if (arena == null) plugin.getArenaManager().endSetup(admin);
        plugin.getSchematicCategoryGui().open(admin, arena);
    }

    private void exitSchematicSession() {
        clearPreview();           // restaure le terrain (edit mode) + annule particules
        cancelZoneParticles();   // sécurité si clearPreview ne l'a pas fait
        if (savedInventory != null) {
            admin.getInventory().clear();
            for (int i = 0; i < savedInventory.length; i++)
                admin.getInventory().setItem(i, savedInventory[i]);
            savedInventory = null;
        }
        isEditMode      = false;
        currentBlocks   = null;
        currentEntities = null;
        previewCenter   = null;
        schematicOrigin = null;
        schematicPos2   = null;
        if (arena != null) { this.mode = WandMode.DEFAULT; updateWandLore(); }
    }

    // ── Helpers internes de l'aperçu ──────────────────────────────────────

    private void recalcZoneFromCurrentBlocks() {
        if (currentBlocks == null || currentBlocks.isEmpty() || previewCenter == null) return;
        int minDx = currentBlocks.stream().mapToInt(TowerSchematic.BlockOffset::dx).min().orElse(0);
        int minDy = currentBlocks.stream().mapToInt(TowerSchematic.BlockOffset::dy).min().orElse(0);
        int minDz = currentBlocks.stream().mapToInt(TowerSchematic.BlockOffset::dz).min().orElse(0);
        int maxDx = currentBlocks.stream().mapToInt(TowerSchematic.BlockOffset::dx).max().orElse(0);
        int maxDy = currentBlocks.stream().mapToInt(TowerSchematic.BlockOffset::dy).max().orElse(0);
        int maxDz = currentBlocks.stream().mapToInt(TowerSchematic.BlockOffset::dz).max().orElse(0);
        schematicOrigin = previewCenter.clone().add(minDx, minDy, minDz);
        schematicPos2   = previewCenter.clone().add(maxDx, maxDy, maxDz);
    }

    private void takePreviewSnapshot() {
        if (previewCenter == null || currentBlocks == null) return;
        previewSnapshot = new java.util.LinkedHashMap<>();
        for (TowerSchematic.BlockOffset b : currentBlocks) {
            Location loc = previewCenter.clone().add(b.dx(), b.dy(), b.dz());
            previewSnapshot.put(loc.clone(), loc.getBlock().getBlockData().clone());
        }
    }

    private void placeCurrentBlocks() {
        if (previewCenter != null && currentBlocks != null)
            placeSchematic(previewCenter, currentBlocks);
    }

    private void spawnCurrentEntities() {
        if (previewCenter == null || currentEntities == null) return;
        previewEntityIds = new java.util.ArrayList<>();
        for (TowerSchematic.EntityOffset e : currentEntities) {
            EntityType type = e.entityType();
            if (type != null) {
                Entity entity = previewCenter.getWorld()
                    .spawnEntity(previewCenter.clone().add(e.dx(), e.dy(), e.dz()), type);
                previewEntityIds.add(entity.getUniqueId());
            }
        }
    }

    /**
     * Place les blocs d'un schematic centré sur {@code center}.
     * Utilisé pour poser les entrées de monstres et les marqueurs de tours.
     */
    public static void placeSchematic(Location center, java.util.List<TowerSchematic.BlockOffset> blocks) {
        World world = center.getWorld();
        if (world == null) return;
        int cx = center.getBlockX(), cy = center.getBlockY(), cz = center.getBlockZ();
        for (TowerSchematic.BlockOffset b : blocks) {
            world.getBlockAt(cx + b.dx(), cy + b.dy(), cz + b.dz())
                .setBlockData(Bukkit.createBlockData(b.blockData()), false);
        }
    }

    /**
     * Efface les blocs d'un schematic global centré sur {@code center}.
     */
    public static void clearSchematic(Location center, SchematicCategory cat, int level) {
        clearSchematic(center, cat, level, null);
    }

    /**
     * Efface les blocs d'un schematic centré sur {@code center} en les remettant à AIR.
     * Utilise la surcharge de l'arène si elle existe, sinon le global.
     * @param arenaId null pour forcer le schematic global.
     */
    public static void clearSchematic(Location center, SchematicCategory cat, int level, String arenaId) {
        World world = center.getWorld();
        if (world == null) return;
        var blocks = TowerSchematic.getBlocks(cat, level, arenaId);
        if (blocks.isEmpty()) return;
        int cx = center.getBlockX(), cy = center.getBlockY(), cz = center.getBlockZ();
        for (TowerSchematic.BlockOffset b : blocks) {
            world.getBlockAt(cx + b.dx(), cy + b.dy(), cz + b.dz()).setType(Material.AIR);
        }
    }

    /**
     * Trou nether de secours si le schematic SPAWN_MONSTER n'est pas chargé.
     */
    private void buildNetherPit(Location center) {
        World world = center.getWorld();
        if (world == null) return;
        int cx = center.getBlockX(), cy = center.getBlockY(), cz = center.getBlockZ();
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                for (int dy = 0; dy >= -1; dy--)
                    world.getBlockAt(cx + dx, cy + dy, cz + dz).setType(Material.AIR);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                boolean isEdge = Math.abs(dx) == 1 || Math.abs(dz) == 1;
                world.getBlockAt(cx + dx, cy - 2, cz + dz)
                    .setType(isEdge ? Material.NETHER_BRICKS : Material.SOUL_SAND);
            }
        for (int[] dir : new int[][]{{-2,0},{2,0},{0,-2},{0,2}})
            for (int dy = -1; dy >= -2; dy--)
                world.getBlockAt(cx + dir[0], cy + dy, cz + dir[1]).setType(Material.NETHERRACK);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                if (Math.abs(dx) == 1 || Math.abs(dz) == 1) {
                    var block = world.getBlockAt(cx + dx, cy, cz + dz);
                    if (block.getType() == Material.AIR) block.setType(Material.NETHERRACK);
                }
    }

    /** Affiche les particules de délimitation quand les 2 points sont définis */
    private void drawBoundsPreview() {
        if (!arena.hasBounds()) return;
        Location p1 = arena.getPos1();
        Location p2 = arena.getPos2();
        if (!p1.getWorld().equals(p2.getWorld())) return;

        // Dessiner les 4 coins de la zone avec des particules
        double minX = Math.min(p1.getX(), p2.getX());
        double maxX = Math.max(p1.getX(), p2.getX());
        double minZ = Math.min(p1.getZ(), p2.getZ());
        double maxZ = Math.max(p1.getZ(), p2.getZ());
        double y = (p1.getY() + p2.getY()) / 2 + 1;

        // Coins
        for (double[] corner : new double[][]{{minX, minZ}, {maxX, minZ}, {minX, maxZ}, {maxX, maxZ}}) {
            p1.getWorld().spawnParticle(Particle.END_ROD,
                new Location(p1.getWorld(), corner[0], y, corner[1]), 5, 0, 0, 0, 0);
        }
        ChatUtil.send(admin, m("admin.setup.bounds-size", (int)(maxX-minX), (int)(maxZ-minZ)));
    }

    // ---- Validation ----

    public boolean validate() {
        boolean ok = true;
        if (!arena.hasLobbySpawn()) {
            ChatUtil.send(admin, plugin.getMessages().format("setup.missing-lobby")); ok = false;
        }
        if (!arena.hasGameSpawn()) {
            ChatUtil.send(admin, plugin.getMessages().format("setup.missing-game-spawn")); ok = false;
        }
        if (!arena.hasMobSpawns()) {
            ChatUtil.send(admin, plugin.getMessages().format("setup.missing-spawns")); ok = false;
        }
        if (!arena.hasVillagerSpawns()) {
            ChatUtil.send(admin, plugin.getMessages().format("setup.missing-villager-spawns")); ok = false;
        }
        if (!arena.hasTowerSlots()) {
            ChatUtil.send(admin, plugin.getMessages().format("setup.missing-tower-slots")); ok = false;
        }
        if (!arena.hasVillageCenter()) {
            ChatUtil.send(admin, plugin.getMessages().format("setup.missing-village-center")); ok = false;
        }
        if (!arena.hasBounds()) {
            ChatUtil.send(admin, plugin.getMessages().format("setup.missing-bounds")); ok = false;
        }
        if (!arena.hasShopChest()) {
            ChatUtil.send(admin, plugin.getMessages().format("setup.missing-shop")); ok = false;
        }
        if (!arena.hasShopVillager()) {
            ChatUtil.send(admin, plugin.getMessages().format("setup.missing-shop-villager")); ok = false;
        }
        if (!arena.hasEnchantVillager()) {
            ChatUtil.send(admin, plugin.getMessages().format("setup.missing-enchant-villager")); ok = false;
        }
        return ok;
    }

    // ---- Util ----

    private String formatLoc(Location loc) {
        return String.format("%.0f, %.0f, %.0f", loc.getX(), loc.getY(), loc.getZ());
    }
}
