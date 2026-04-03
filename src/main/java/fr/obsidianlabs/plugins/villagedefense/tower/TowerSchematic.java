package fr.obsidianlabs.plugins.villagedefense.tower;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Gère les schematics de tours chargés depuis des fichiers YAML.
 *
 * Structure sur disque :
 *   plugins/VillageDefense/schematics/
 *     archer/
 *       level_1/tour.schem
 *       level_2/tour.schem
 *       level_3/tour.schem
 *     trebuchet/
 *       level_1/tour.schem
 *       level_2/tour.schem
 *       level_3/tour.schem
 *     mage/
 *       level_1/tour.schem
 *       level_2/tour.schem
 *       level_3/tour.schem
 *
 * Format de tour.schem (YAML) :
 *   tower: "Tour d'Archer"
 *   level: 1
 *   blocks:
 *     - { dx: 0, dy: 0, dz: 0, material: OAK_PLANKS }
 *     - { dx: 0, dy: 1, dz: 0, material: BIRCH_FENCE }
 *     ...
 *
 * Les fichiers manquants sont générés automatiquement avec les structures par défaut.
 * Après modification, rechargez avec /vdadmin reload.
 */
public class TowerSchematic {

    public record BlockOffset(int dx, int dy, int dz, String blockData) {
        /** Constructeur de commodité pour les blocs codés en dur (état par défaut). */
        public BlockOffset(int dx, int dy, int dz, Material material) {
            this(dx, dy, dz, material.createBlockData().getAsString());
        }
        /** Retourne le Material de ce BlockData. */
        public Material material() {
            try { return Bukkit.createBlockData(blockData).getMaterial(); }
            catch (Exception e) { return Material.AIR; }
        }
    }

    /**
     * Entité décorative capturée dans un schematic.
     * Le type est stocké en String pour rester compatible entre les versions de Paper.
     */
    public record EntityOffset(double dx, double dy, double dz, String entityTypeName) {
        /** Retourne l'EntityType correspondant, ou null si inconnu. */
        public EntityType entityType() {
            try { return EntityType.valueOf(entityTypeName); }
            catch (Exception e) { return null; }
        }
    }

    /** Noms des types d'entités décoratives supportés lors de la capture. */
    static final Set<String> SUPPORTED_ENTITY_NAMES = Set.of(
        "END_CRYSTAL", "ARMOR_STAND", "ITEM_FRAME", "GLOW_ITEM_FRAME"
    );

    /** Retourne true si ce type d'entité fait partie des entités décoratives supportées. */
    public static boolean isSupportedEntity(String typeName) {
        return SUPPORTED_ENTITY_NAMES.contains(typeName);
    }

    // Cache blocs :  global → "archer_1" …   par arène → "arena:<id>:archer_1" …
    private static final Map<String, List<BlockOffset>>  CACHE        = new HashMap<>();
    // Cache entités (même structure de clés)
    private static final Map<String, List<EntityOffset>> ENTITY_CACHE = new HashMap<>();

    private static String arenaCacheKey(String arenaId, String configKey, int level) {
        return "arena:" + arenaId + ":" + configKey + "_" + level;
    }

    /** Vérifie s'il existe une surcharge de schematic spécifique à l'arène. */
    public static boolean hasArenaOverride(SchematicCategory cat, int level, String arenaId) {
        if (arenaId == null) return false;
        return CACHE.containsKey(arenaCacheKey(arenaId, cat.getConfigKey(), level));
    }

    /** Vérifie s'il existe une surcharge de schematic spécifique à l'arène pour un TowerType. */
    public static boolean hasArenaOverride(TowerType type, int level, String arenaId) {
        if (arenaId == null) return false;
        return CACHE.containsKey(arenaCacheKey(arenaId, type.getConfigKey(), level));
    }

    // ── API publique ───────────────────────────────────────────────────────────

    /** Génère les fichiers manquants et charge tous les schematics. */
    public static void init(VillageDefensePlugin plugin) {
        generateMissingDefaults(plugin);
        loadAll(plugin);
    }

    /** Recharge tous les schematics depuis le disque (utilisé par /vdadmin reload). */
    public static void reload(VillageDefensePlugin plugin) {
        CACHE.clear();
        generateMissingDefaults(plugin);
        loadAll(plugin);
        plugin.getLogger().info("[Schematics] " + CACHE.size() + " schematic(s) chargé(s).");
    }

    /**
     * Retourne les blocs globaux pour un type/niveau.
     */
    public static List<BlockOffset> getBlocks(TowerType type, int level) {
        return CACHE.getOrDefault(cacheKey(type, level), List.of());
    }

    /**
     * Retourne les blocs globaux pour une catégorie/niveau.
     */
    public static List<BlockOffset> getBlocks(SchematicCategory cat, int level) {
        return CACHE.getOrDefault(cacheKey(cat, level), List.of());
    }

    /**
     * Retourne les blocs pour un type/niveau, en privilégiant la surcharge de l'arène si elle existe.
     * @param arenaId ID de l'arène, ou null pour les blocs globaux uniquement.
     */
    public static List<BlockOffset> getBlocks(TowerType type, int level, String arenaId) {
        if (arenaId != null) {
            var override = CACHE.get(arenaCacheKey(arenaId, type.getConfigKey(), level));
            if (override != null) return override;
        }
        return CACHE.getOrDefault(cacheKey(type, level), List.of());
    }

    /**
     * Retourne les blocs pour une catégorie/niveau, en privilégiant la surcharge de l'arène si elle existe.
     * @param arenaId ID de l'arène, ou null pour les blocs globaux uniquement.
     */
    public static List<BlockOffset> getBlocks(SchematicCategory cat, int level, String arenaId) {
        if (arenaId != null) {
            var override = CACHE.get(arenaCacheKey(arenaId, cat.getConfigKey(), level));
            if (override != null) return override;
        }
        return CACHE.getOrDefault(cacheKey(cat, level), List.of());
    }

    /** Retourne les entités décoratives pour un type/niveau. */
    public static List<EntityOffset> getEntities(TowerType type, int level) {
        return ENTITY_CACHE.getOrDefault(cacheKey(type, level), List.of());
    }

    /** Retourne les entités décoratives pour un type/niveau, en privilégiant la surcharge d'arène. */
    public static List<EntityOffset> getEntities(TowerType type, int level, String arenaId) {
        if (arenaId != null) {
            var override = ENTITY_CACHE.get(arenaCacheKey(arenaId, type.getConfigKey(), level));
            if (override != null) return override;
        }
        return ENTITY_CACHE.getOrDefault(cacheKey(type, level), List.of());
    }

    /** Retourne les entités décoratives pour une catégorie/niveau. */
    public static List<EntityOffset> getEntities(SchematicCategory cat, int level) {
        if (cat.isTowerCategory()) return getEntities(cat.getTowerType(), level);
        return ENTITY_CACHE.getOrDefault(cacheKey(cat, level), List.of());
    }

    /** Retourne les entités décoratives pour une catégorie/niveau, en privilégiant la surcharge d'arène. */
    public static List<EntityOffset> getEntities(SchematicCategory cat, int level, String arenaId) {
        if (cat.isTowerCategory()) return getEntities(cat.getTowerType(), level, arenaId);
        if (arenaId != null) {
            var override = ENTITY_CACHE.get(arenaCacheKey(arenaId, cat.getConfigKey(), level));
            if (override != null) return override;
        }
        return ENTITY_CACHE.getOrDefault(cacheKey(cat, level), List.of());
    }

    /** Capture globale pour une SchematicCategory. */
    public static void capture(VillageDefensePlugin plugin, SchematicCategory cat, int level,
                                Location pos1, Location pos2) {
        capture(plugin, cat, level, pos1, pos2, null);
    }

    /**
     * Capture in-world pour une SchematicCategory (SPAWN_MONSTER, TOWER_SLOT, ou tour).
     * @param arenaId null = global ; non-null = surcharge propre à cette arène
     */
    public static void capture(VillageDefensePlugin plugin, SchematicCategory cat, int level,
                                Location pos1, Location pos2, String arenaId) {
        if (cat.isTowerCategory()) {
            capture(plugin, cat.getTowerType(), level, pos1, pos2, arenaId);
            return;
        }
        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        // Passe 1 : trouver l'étendue réelle des blocs non-AIR (auto-trim)
        int actMinX = Integer.MAX_VALUE, actMaxX = Integer.MIN_VALUE;
        int actMinY = Integer.MAX_VALUE;
        int actMinZ = Integer.MAX_VALUE, actMaxZ = Integer.MIN_VALUE;
        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = pos1.getWorld().getBlockAt(x, y, z);
                    if (block.getType() == Material.AIR || block.getType() == Material.CAVE_AIR) continue;
                    if (x < actMinX) actMinX = x; if (x > actMaxX) actMaxX = x;
                    if (y < actMinY) actMinY = y;
                    if (z < actMinZ) actMinZ = z; if (z > actMaxZ) actMaxZ = z;
                }
        if (actMinX == Integer.MAX_VALUE) {
            plugin.getLogger().warning("[Schematics] Aucun bloc non-AIR dans la sélection " + cat.getConfigKey() + ".");
            return;
        }
        int centerX = (actMinX + actMaxX) / 2;
        int groundY = actMinY;
        int centerZ = (actMinZ + actMaxZ) / 2;

        // Passe 2 : construire les BlockOffset avec l'origine réelle
        List<BlockOffset> blocks = new ArrayList<>();
        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = pos1.getWorld().getBlockAt(x, y, z);
                    if (block.getType() == Material.AIR || block.getType() == Material.CAVE_AIR) continue;
                    blocks.add(new BlockOffset(x - centerX, y - groundY, z - centerZ, block.getBlockData().getAsString()));
                }

        List<EntityOffset> entities = captureEntities(pos1, minX, minY, minZ, maxX, maxY, maxZ, centerX, groundY, centerZ);

        File file = schematicFile(plugin, cat, level, arenaId);
        file.getParentFile().mkdirs();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("tower", localizedName(plugin, cat));
        yaml.set("level", level);
        List<Map<String, Object>> blockList = new ArrayList<>();
        for (BlockOffset b : blocks) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("dx", b.dx()); entry.put("dy", b.dy()); entry.put("dz", b.dz());
            entry.put("blockdata", b.blockData());
            blockList.add(entry);
        }
        yaml.set("blocks", blockList);
        if (!entities.isEmpty()) {
            List<Map<String, Object>> entityList = new ArrayList<>();
            for (EntityOffset e : entities) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("dx", e.dx()); entry.put("dy", e.dy()); entry.put("dz", e.dz());
                entry.put("type", e.entityTypeName());
                entityList.add(entry);
            }
            yaml.set("entities", entityList);
        }
        try { yaml.save(file); }
        catch (IOException e) {
            plugin.getLogger().warning("[Schematics] Erreur capture " + cat.getConfigKey() + ": " + e.getMessage());
            return;
        }
        String key = arenaId != null
            ? arenaCacheKey(arenaId, cat.getConfigKey(), level)
            : cacheKey(cat, level);
        CACHE.put(key, Collections.unmodifiableList(blocks));
        ENTITY_CACHE.put(key, Collections.unmodifiableList(entities));
        String scope = arenaId != null ? " [arène: " + arenaId + "]" : " [global]";
        plugin.getLogger().info("[Schematics] Capture " + cat.getConfigKey() + " lvl" + level
            + scope + " → " + blocks.size() + " blocs, " + entities.size() + " entité(s).");
    }

    // ── Capture in-world ───────────────────────────────────────────────────────

    /** Capture globale pour un TowerType. */
    public static void capture(VillageDefensePlugin plugin, TowerType type, int level,
                                Location pos1, Location pos2) {
        capture(plugin, type, level, pos1, pos2, null);
    }

    /**
     * Capture tous les blocs non-AIR dans la sélection définie par {@code pos1} et {@code pos2}.
     * @param arenaId null = global ; non-null = surcharge propre à cette arène
     */
    public static void capture(VillageDefensePlugin plugin, TowerType type, int level,
                                Location pos1, Location pos2, String arenaId) {
        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        // Passe 1 : trouver l'étendue réelle des blocs non-AIR (auto-trim)
        int actMinX = Integer.MAX_VALUE, actMaxX = Integer.MIN_VALUE;
        int actMinY = Integer.MAX_VALUE;
        int actMinZ = Integer.MAX_VALUE, actMaxZ = Integer.MIN_VALUE;
        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = pos1.getWorld().getBlockAt(x, y, z);
                    if (block.getType() == Material.AIR || block.getType() == Material.CAVE_AIR) continue;
                    if (x < actMinX) actMinX = x; if (x > actMaxX) actMaxX = x;
                    if (y < actMinY) actMinY = y;
                    if (z < actMinZ) actMinZ = z; if (z > actMaxZ) actMaxZ = z;
                }
        if (actMinX == Integer.MAX_VALUE) {
            plugin.getLogger().warning("[Schematics] Aucun bloc non-AIR dans la sélection " + type.getConfigKey() + ".");
            return;
        }
        int centerX = (actMinX + actMaxX) / 2;
        int groundY = actMinY;
        int centerZ = (actMinZ + actMaxZ) / 2;

        // Passe 2 : construire les BlockOffset avec l'origine réelle
        List<BlockOffset> blocks = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = pos1.getWorld().getBlockAt(x, y, z);
                    if (block.getType() == Material.AIR || block.getType() == Material.CAVE_AIR) continue;
                    blocks.add(new BlockOffset(x - centerX, y - groundY, z - centerZ, block.getBlockData().getAsString()));
                }
            }
        }

        List<EntityOffset> entities = captureEntities(pos1, minX, minY, minZ, maxX, maxY, maxZ, centerX, groundY, centerZ);

        // Sauvegarder
        File file = schematicFile(plugin, type, level, arenaId);
        file.getParentFile().mkdirs();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("tower", localizedName(plugin, type));
        yaml.set("level", level);
        List<Map<String, Object>> blockList = new ArrayList<>();
        for (BlockOffset b : blocks) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("dx", b.dx());
            entry.put("dy", b.dy());
            entry.put("dz", b.dz());
            entry.put("blockdata", b.blockData());
            blockList.add(entry);
        }
        yaml.set("blocks", blockList);
        if (!entities.isEmpty()) {
            List<Map<String, Object>> entityList = new ArrayList<>();
            for (EntityOffset e : entities) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("dx", e.dx());
                entry.put("dy", e.dy());
                entry.put("dz", e.dz());
                entry.put("type", e.entityTypeName());
                entityList.add(entry);
            }
            yaml.set("entities", entityList);
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("[Schematics] Erreur sauvegarde capture: " + e.getMessage());
            return;
        }

        // Mettre en cache
        String key = arenaId != null
            ? arenaCacheKey(arenaId, type.getConfigKey(), level)
            : cacheKey(type, level);
        CACHE.put(key, Collections.unmodifiableList(blocks));
        ENTITY_CACHE.put(key, Collections.unmodifiableList(entities));
        String scope = arenaId != null ? " [arène: " + arenaId + "]" : " [global]";
        plugin.getLogger().info("[Schematics] Capture " + type.getConfigKey() + " lvl" + level
            + scope + " → " + blocks.size() + " blocs, " + entities.size() + " entité(s).");
    }

    // ── Sauvegarde directe (depuis listes en mémoire — mode édition) ──────────

    /** Sauvegarde un schematic directement depuis des listes de blocs/entités déjà calculées.
     *  Utilisé en mode édition pour éviter un re-scan du monde qui peut rater des entités
     *  situées exactement à la limite de la zone (ex. cristaux de l'End). */
    public static void save(VillageDefensePlugin plugin, SchematicCategory cat, int level,
                            List<BlockOffset> blocks, List<EntityOffset> entities, String arenaId) {
        if (cat.isTowerCategory()) {
            save(plugin, cat.getTowerType(), level, blocks, entities, arenaId);
            return;
        }
        File file = schematicFile(plugin, cat, level, arenaId);
        file.getParentFile().mkdirs();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("tower", plugin.getMessages().raw(cat.getNameKey()));
        yaml.set("level", level);
        List<Map<String, Object>> blockList = new ArrayList<>();
        for (BlockOffset b : blocks) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("dx", b.dx()); entry.put("dy", b.dy()); entry.put("dz", b.dz());
            entry.put("blockdata", b.blockData());
            blockList.add(entry);
        }
        yaml.set("blocks", blockList);
        if (!entities.isEmpty()) {
            List<Map<String, Object>> entityList = new ArrayList<>();
            for (EntityOffset e : entities) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("dx", e.dx()); entry.put("dy", e.dy()); entry.put("dz", e.dz());
                entry.put("type", e.entityTypeName());
                entityList.add(entry);
            }
            yaml.set("entities", entityList);
        }
        try { yaml.save(file); }
        catch (IOException e) {
            plugin.getLogger().warning("[Schematics] Erreur save " + cat.getConfigKey() + ": " + e.getMessage());
            return;
        }
        String key = arenaId != null ? arenaCacheKey(arenaId, cat.getConfigKey(), level) : cacheKey(cat, level);
        CACHE.put(key, Collections.unmodifiableList(blocks));
        ENTITY_CACHE.put(key, Collections.unmodifiableList(entities));
        String scope = arenaId != null ? " [arène: " + arenaId + "]" : " [global]";
        plugin.getLogger().info("[Schematics] Save (edit) " + cat.getConfigKey() + " lvl" + level
            + scope + " → " + blocks.size() + " blocs, " + entities.size() + " entité(s).");
    }

    public static void save(VillageDefensePlugin plugin, TowerType type, int level,
                            List<BlockOffset> blocks, List<EntityOffset> entities, String arenaId) {
        File file = schematicFile(plugin, type, level, arenaId);
        file.getParentFile().mkdirs();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("tower", plugin.getMessages().raw(type.getNameKey()));
        yaml.set("level", level);
        List<Map<String, Object>> blockList = new ArrayList<>();
        for (BlockOffset b : blocks) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("dx", b.dx()); entry.put("dy", b.dy()); entry.put("dz", b.dz());
            entry.put("blockdata", b.blockData());
            blockList.add(entry);
        }
        yaml.set("blocks", blockList);
        if (!entities.isEmpty()) {
            List<Map<String, Object>> entityList = new ArrayList<>();
            for (EntityOffset e : entities) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("dx", e.dx()); entry.put("dy", e.dy()); entry.put("dz", e.dz());
                entry.put("type", e.entityTypeName());
                entityList.add(entry);
            }
            yaml.set("entities", entityList);
        }
        try { yaml.save(file); }
        catch (IOException e) {
            plugin.getLogger().warning("[Schematics] Erreur save " + type.getConfigKey() + ": " + e.getMessage());
            return;
        }
        String key = arenaId != null ? arenaCacheKey(arenaId, type.getConfigKey(), level) : cacheKey(type, level);
        CACHE.put(key, Collections.unmodifiableList(blocks));
        ENTITY_CACHE.put(key, Collections.unmodifiableList(entities));
        String scope = arenaId != null ? " [arène: " + arenaId + "]" : " [global]";
        plugin.getLogger().info("[Schematics] Save (edit) " + type.getConfigKey() + " lvl" + level
            + scope + " → " + blocks.size() + " blocs, " + entities.size() + " entité(s).");
    }

    // ── Chargement ─────────────────────────────────────────────────────────────

    private static void loadAll(VillageDefensePlugin plugin) {
        // Schematics globaux
        for (TowerType type : TowerType.values())
            for (int level = 1; level <= 3; level++)
                loadSchematic(plugin, type, level, null);
        for (SchematicCategory cat : SchematicCategory.values()) {
            if (cat.isTowerCategory()) continue;
            for (int level = 1; level <= cat.getMaxLevels(); level++)
                loadSchematicCat(plugin, cat, level, null);
        }
        // Surcharges par arène
        File arenasDir = new File(plugin.getDataFolder(), "schematics/arenas");
        if (arenasDir.isDirectory()) {
            File[] arenaFolders = arenasDir.listFiles(File::isDirectory);
            if (arenaFolders != null) {
                for (File arenaFolder : arenaFolders) {
                    String arenaId = arenaFolder.getName();
                    for (TowerType type : TowerType.values())
                        for (int level = 1; level <= 3; level++)
                            if (schematicFile(plugin, type, level, arenaId).exists())
                                loadSchematic(plugin, type, level, arenaId);
                    for (SchematicCategory cat : SchematicCategory.values()) {
                        if (cat.isTowerCategory()) continue;
                        for (int level = 1; level <= cat.getMaxLevels(); level++)
                            if (schematicFile(plugin, cat, level, arenaId).exists())
                                loadSchematicCat(plugin, cat, level, arenaId);
                    }
                }
            }
        }
    }

    private static void loadSchematic(VillageDefensePlugin plugin, TowerType type, int level, String arenaId) {
        File file = schematicFile(plugin, type, level, arenaId);
        if (!file.exists()) {
            if (arenaId == null) plugin.getLogger().warning("[Schematics] Fichier manquant: " + file.getPath());
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<Map<?, ?>> rawList = yaml.getMapList("blocks");
        List<BlockOffset> blocks = new ArrayList<>(rawList.size());

        for (Map<?, ?> map : rawList) {
            try {
                int dx = toInt(map.get("dx"));
                int dy = toInt(map.get("dy"));
                int dz = toInt(map.get("dz"));
                String blockDataStr;
                Object bdRaw = map.get("blockdata");
                if (bdRaw != null) {
                    blockDataStr = String.valueOf(bdRaw);
                } else {
                    // Rétrocompatibilité : anciens fichiers avec uniquement "material"
                    String matName = String.valueOf(map.get("material")).toUpperCase();
                    Material mat = Material.matchMaterial(matName);
                    if (mat == null) {
                        plugin.getLogger().warning("[Schematics] Matériau inconnu '" + matName
                            + "' dans " + file.getName() + " — ignoré.");
                        continue;
                    }
                    blockDataStr = mat.createBlockData().getAsString();
                }
                blocks.add(new BlockOffset(dx, dy, dz, blockDataStr));
            } catch (Exception e) {
                plugin.getLogger().warning("[Schematics] Entrée invalide dans "
                    + file.getName() + ": " + map + " — " + e.getMessage());
            }
        }

        List<EntityOffset> entities = loadEntityOffsets(yaml, file.getName(), plugin);
        String key = arenaId != null ? arenaCacheKey(arenaId, type.getConfigKey(), level) : cacheKey(type, level);
        CACHE.put(key, Collections.unmodifiableList(blocks));
        ENTITY_CACHE.put(key, Collections.unmodifiableList(entities));
    }

    private static void loadSchematicCat(VillageDefensePlugin plugin, SchematicCategory cat, int level, String arenaId) {
        File file = schematicFile(plugin, cat, level, arenaId);
        if (!file.exists()) {
            if (arenaId == null) plugin.getLogger().warning("[Schematics] Fichier manquant: " + file.getPath());
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<Map<?, ?>> rawList = yaml.getMapList("blocks");
        List<BlockOffset> blocks = new ArrayList<>(rawList.size());
        for (Map<?, ?> map : rawList) {
            try {
                int dx = toInt(map.get("dx"));
                int dy = toInt(map.get("dy"));
                int dz = toInt(map.get("dz"));
                String blockDataStr;
                Object bdRaw = map.get("blockdata");
                if (bdRaw != null) {
                    blockDataStr = String.valueOf(bdRaw);
                } else {
                    // Rétrocompatibilité : anciens fichiers avec uniquement "material"
                    String matName = String.valueOf(map.get("material")).toUpperCase();
                    Material mat = Material.matchMaterial(matName);
                    if (mat == null) {
                        plugin.getLogger().warning("[Schematics] Matériau inconnu '" + matName + "' — ignoré.");
                        continue;
                    }
                    blockDataStr = mat.createBlockData().getAsString();
                }
                blocks.add(new BlockOffset(dx, dy, dz, blockDataStr));
            } catch (Exception e) {
                plugin.getLogger().warning("[Schematics] Entrée invalide: " + map + " — " + e.getMessage());
            }
        }
        List<EntityOffset> entities = loadEntityOffsets(yaml, file.getName(), plugin);
        String key = arenaId != null ? arenaCacheKey(arenaId, cat.getConfigKey(), level) : cacheKey(cat, level);
        CACHE.put(key, Collections.unmodifiableList(blocks));
        ENTITY_CACHE.put(key, Collections.unmodifiableList(entities));
    }

    /** Lit la section {@code entities} d'un fichier .schem et retourne les EntityOffset. */
    private static List<EntityOffset> loadEntityOffsets(YamlConfiguration yaml, String fileName,
                                                         VillageDefensePlugin plugin) {
        List<Map<?, ?>> rawList = yaml.getMapList("entities");
        if (rawList.isEmpty()) return List.of();
        List<EntityOffset> entities = new ArrayList<>(rawList.size());
        for (Map<?, ?> map : rawList) {
            try {
                double dx = toDouble(map.get("dx"));
                double dy = toDouble(map.get("dy"));
                double dz = toDouble(map.get("dz"));
                String typeName = String.valueOf(map.get("type")).toUpperCase();
                if (!SUPPORTED_ENTITY_NAMES.contains(typeName)) {
                    plugin.getLogger().warning("[Schematics] Type d'entité non supporté '"
                        + typeName + "' dans " + fileName + " — ignoré.");
                    continue;
                }
                entities.add(new EntityOffset(dx, dy, dz, typeName));
            } catch (Exception e) {
                plugin.getLogger().warning("[Schematics] Entité invalide dans "
                    + fileName + ": " + map + " — " + e.getMessage());
            }
        }
        return entities;
    }

    private static double toDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        return Double.parseDouble(String.valueOf(o));
    }

    // ── Génération des fichiers par défaut ─────────────────────────────────────

    private static void generateMissingDefaults(VillageDefensePlugin plugin) {
        for (TowerType type : TowerType.values()) {
            for (int level = 1; level <= 3; level++) {
                File file = schematicFile(plugin, type, level);
                if (!file.exists()) saveDefault(plugin, type, level, file);
            }
        }
        // Catégories non-tour (SPAWN_MONSTER, TOWER_SLOT)
        for (SchematicCategory cat : SchematicCategory.values()) {
            if (cat.isTowerCategory()) continue;
            for (int level = 1; level <= cat.getMaxLevels(); level++) {
                File file = schematicFile(plugin, cat, level);
                if (!file.exists()) saveDefaultCat(plugin, cat, level, file);
            }
        }
    }

    private static void saveDefault(VillageDefensePlugin plugin, TowerType type, int level, File file) {
        file.getParentFile().mkdirs();

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("tower", localizedName(plugin, type));
        yaml.set("level", level);
        yaml.set("description", plugin.getMessages().format(
            "admin.schem.file-description-level", localizedName(plugin, type), level));

        List<Map<String, Object>> blockList = new ArrayList<>();
        for (BlockOffset b : hardcodedBlocks(type, level)) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("dx", b.dx());
            entry.put("dy", b.dy());
            entry.put("dz", b.dz());
            entry.put("blockdata", b.blockData());
            blockList.add(entry);
        }
        yaml.set("blocks", blockList);

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("[Schematics] Impossible de sauvegarder "
                + file.getName() + ": " + e.getMessage());
        }
    }

    private static void saveDefaultCat(VillageDefensePlugin plugin, SchematicCategory cat, int level, File file) {
        file.getParentFile().mkdirs();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("tower", localizedName(plugin, cat));
        yaml.set("level", level);
        yaml.set("description", plugin.getMessages().format(
            "admin.schem.file-description", localizedName(plugin, cat)));
        List<Map<String, Object>> blockList = new ArrayList<>();
        for (BlockOffset b : hardcodedCatBlocks(cat, level)) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("dx", b.dx()); entry.put("dy", b.dy()); entry.put("dz", b.dz());
            entry.put("blockdata", b.blockData());
            blockList.add(entry);
        }
        yaml.set("blocks", blockList);
        try { yaml.save(file); }
        catch (IOException e) {
            plugin.getLogger().warning("[Schematics] Impossible de sauvegarder "
                + file.getName() + ": " + e.getMessage());
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** Scanne les entités décoratives dans la boîte englobante et retourne leurs offsets. */
    private static List<EntityOffset> captureEntities(
            Location world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
            int centerX, int groundY, int centerZ) {
        double halfX = (maxX - minX) / 2.0 + 1;
        double halfY = (maxY - minY) / 2.0 + 2; // +2 pour capturer les entités posées au-dessus (ex. cristaux de l'End)
        double halfZ = (maxZ - minZ) / 2.0 + 1;
        double scanCX = (minX + maxX) / 2.0;
        double scanCY = (minY + maxY) / 2.0;
        double scanCZ = (minZ + maxZ) / 2.0;
        Location scanCenter = new Location(world.getWorld(), scanCX, scanCY, scanCZ);

        List<EntityOffset> entities = new ArrayList<>();
        for (Entity entity : world.getWorld().getNearbyEntities(scanCenter, halfX, halfY, halfZ)) {
            if (!SUPPORTED_ENTITY_NAMES.contains(entity.getType().name())) continue;
            Location eloc = entity.getLocation();
            entities.add(new EntityOffset(
                eloc.getX() - centerX,
                eloc.getY() - groundY,
                eloc.getZ() - centerZ,
                entity.getType().name()
            ));
        }
        return entities;
    }

    private static File schematicFile(VillageDefensePlugin plugin, TowerType type, int level) {
        return new File(plugin.getDataFolder(),
            "schematics/" + type.getConfigKey() + "/level_" + level + "/tour.schem");
    }

    private static File schematicFile(VillageDefensePlugin plugin, TowerType type, int level, String arenaId) {
        if (arenaId == null) return schematicFile(plugin, type, level);
        return new File(plugin.getDataFolder(),
            "schematics/arenas/" + arenaId + "/" + type.getConfigKey() + "/level_" + level + "/tour.schem");
    }

    private static File schematicFile(VillageDefensePlugin plugin, SchematicCategory cat, int level) {
        return new File(plugin.getDataFolder(),
            "schematics/" + cat.getConfigKey() + "/level_" + level + "/tour.schem");
    }

    private static File schematicFile(VillageDefensePlugin plugin, SchematicCategory cat, int level, String arenaId) {
        if (arenaId == null) return schematicFile(plugin, cat, level);
        return new File(plugin.getDataFolder(),
            "schematics/arenas/" + arenaId + "/" + cat.getConfigKey() + "/level_" + level + "/tour.schem");
    }

    private static String localizedName(VillageDefensePlugin plugin, TowerType type) {
        return plugin.getMessages().format(type.getNameKey());
    }

    private static String localizedName(VillageDefensePlugin plugin, SchematicCategory cat) {
        return plugin.getMessages().format(cat.getNameKey());
    }

    private static String cacheKey(TowerType type, int level) {
        return type.getConfigKey() + "_" + level;
    }

    private static String cacheKey(SchematicCategory cat, int level) {
        return cat.getConfigKey() + "_" + level;
    }

    private static int toInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        return Integer.parseInt(String.valueOf(o));
    }

    // ── Structures par défaut ──────────────────────────────────────────────────

    private static List<BlockOffset> hardcodedBlocks(TowerType type, int level) {
        return switch (type) {
            case ARCHER    -> archerBlocks(level);
            case TREBUCHET -> trebuchetBlocks(level);
            case MAGE      -> mageBlocks(level);
            case LIGHTNING -> lightningBlocks(level);
            case ICE    -> iceBlocks(level);
            case CANNON -> cannonBlocks(level);
        };
    }

    // --- ARCHER ---

    private static List<BlockOffset> archerBlocks(int level) {
        return switch (level) {
            case 1 -> List.of(
                new BlockOffset(-1, 0, -1, "minecraft:spruce_slab[type=bottom,waterlogged=false]"),
                new BlockOffset(-1, 0,  0, "minecraft:oak_log[axis=x]"),
                new BlockOffset(-1, 0,  1, "minecraft:spruce_slab[type=bottom,waterlogged=false]"),
                new BlockOffset(-1, 2,  0, "minecraft:oak_stairs[facing=east,half=top,shape=straight,waterlogged=false]"),
                new BlockOffset( 0, 0, -1, "minecraft:oak_log[axis=z]"),
                new BlockOffset( 0, 0,  0, "minecraft:oak_log[axis=y]"),
                new BlockOffset( 0, 0,  1, "minecraft:oak_log[axis=z]"),
                new BlockOffset( 0, 1,  0, "minecraft:oak_log[axis=y]"),
                new BlockOffset( 0, 2, -1, "minecraft:oak_stairs[facing=south,half=top,shape=straight,waterlogged=false]"),
                new BlockOffset( 0, 2,  0, "minecraft:oak_log[axis=y]"),
                new BlockOffset( 0, 2,  1, "minecraft:oak_stairs[facing=north,half=top,shape=straight,waterlogged=false]"),
                new BlockOffset( 0, 3,  0, "minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]"),
                new BlockOffset( 1, 0, -1, "minecraft:spruce_slab[type=bottom,waterlogged=false]"),
                new BlockOffset( 1, 0,  0, "minecraft:oak_log[axis=x]"),
                new BlockOffset( 1, 0,  1, "minecraft:spruce_slab[type=bottom,waterlogged=false]"),
                new BlockOffset( 1, 2,  0, "minecraft:oak_stairs[facing=west,half=top,shape=straight,waterlogged=false]")
            );
            case 2 -> List.of(
                new BlockOffset(-1, 0, -1, "minecraft:cobblestone_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset(-1, 0,  0, "minecraft:oak_log[axis=x]"),
                new BlockOffset(-1, 0,  1, "minecraft:cobblestone_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset(-1, 1,  0, "minecraft:spruce_fence[east=true,north=false,south=false,waterlogged=false,west=false]"),
                new BlockOffset(-1, 2,  0, "minecraft:spruce_fence[east=true,north=false,south=false,waterlogged=false,west=false]"),
                new BlockOffset(-1, 3,  0, "minecraft:cobblestone_stairs[facing=east,half=top,shape=straight,waterlogged=false]"),
                new BlockOffset(-1, 4,  0, "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]"),
                new BlockOffset( 0, 0, -1, "minecraft:oak_log[axis=z]"),
                new BlockOffset( 0, 0,  0, "minecraft:oak_log[axis=y]"),
                new BlockOffset( 0, 0,  1, "minecraft:oak_log[axis=z]"),
                new BlockOffset( 0, 1, -1, "minecraft:spruce_fence[east=false,north=false,south=true,waterlogged=false,west=false]"),
                new BlockOffset( 0, 1,  0, "minecraft:oak_log[axis=y]"),
                new BlockOffset( 0, 1,  1, "minecraft:spruce_fence[east=false,north=true,south=false,waterlogged=false,west=false]"),
                new BlockOffset( 0, 2, -1, "minecraft:spruce_fence[east=false,north=false,south=true,waterlogged=false,west=false]"),
                new BlockOffset( 0, 2,  0, "minecraft:oak_log[axis=y]"),
                new BlockOffset( 0, 2,  1, "minecraft:spruce_fence[east=false,north=true,south=false,waterlogged=false,west=false]"),
                new BlockOffset( 0, 3, -1, "minecraft:cobblestone_stairs[facing=south,half=top,shape=straight,waterlogged=false]"),
                new BlockOffset( 0, 3,  0, "minecraft:oak_log[axis=y]"),
                new BlockOffset( 0, 3,  1, "minecraft:cobblestone_stairs[facing=north,half=top,shape=straight,waterlogged=false]"),
                new BlockOffset( 0, 4, -1, "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]"),
                new BlockOffset( 0, 4,  0, "minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]"),
                new BlockOffset( 0, 4,  1, "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]"),
                new BlockOffset( 1, 0, -1, "minecraft:cobblestone_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset( 1, 0,  0, "minecraft:oak_log[axis=x]"),
                new BlockOffset( 1, 0,  1, "minecraft:cobblestone_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset( 1, 1,  0, "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=true]"),
                new BlockOffset( 1, 2,  0, "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=true]"),
                new BlockOffset( 1, 3,  0, "minecraft:cobblestone_stairs[facing=west,half=top,shape=straight,waterlogged=false]"),
                new BlockOffset( 1, 4,  0, "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]")
            );
            case 3 -> List.of(
                new BlockOffset(-2, 0,  0, "minecraft:spruce_slab[type=bottom,waterlogged=false]"),
                new BlockOffset(-1, 0, -1, "minecraft:cobblestone_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset(-1, 0,  0, "minecraft:stone_bricks"),
                new BlockOffset(-1, 0,  1, "minecraft:cobblestone_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset(-1, 1,  0, "minecraft:spruce_fence[east=true,north=false,south=false,waterlogged=false,west=false]"),
                new BlockOffset(-1, 2,  0, "minecraft:spruce_fence[east=true,north=false,south=false,waterlogged=false,west=false]"),
                new BlockOffset(-1, 3,  0, "minecraft:spruce_fence[east=true,north=false,south=false,waterlogged=false,west=false]"),
                new BlockOffset(-1, 4,  0, "minecraft:cobblestone_stairs[facing=east,half=top,shape=straight,waterlogged=false]"),
                new BlockOffset(-1, 5,  0, "minecraft:cobblestone_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset( 0, 0, -2, "minecraft:spruce_slab[type=bottom,waterlogged=false]"),
                new BlockOffset( 0, 0, -1, "minecraft:stone_bricks"),
                new BlockOffset( 0, 0,  0, "minecraft:oak_log[axis=y]"),
                new BlockOffset( 0, 0,  1, "minecraft:stone_bricks"),
                new BlockOffset( 0, 0,  2, "minecraft:spruce_slab[type=bottom,waterlogged=false]"),
                new BlockOffset( 0, 1, -1, "minecraft:spruce_fence[east=false,north=false,south=true,waterlogged=false,west=false]"),
                new BlockOffset( 0, 1,  0, "minecraft:stone_bricks"),
                new BlockOffset( 0, 1,  1, "minecraft:spruce_fence[east=false,north=true,south=false,waterlogged=false,west=false]"),
                new BlockOffset( 0, 2, -1, "minecraft:spruce_fence[east=false,north=false,south=true,waterlogged=false,west=false]"),
                new BlockOffset( 0, 2,  0, "minecraft:stone_bricks"),
                new BlockOffset( 0, 2,  1, "minecraft:spruce_fence[east=false,north=true,south=false,waterlogged=false,west=false]"),
                new BlockOffset( 0, 3, -1, "minecraft:spruce_fence[east=false,north=false,south=true,waterlogged=false,west=false]"),
                new BlockOffset( 0, 3,  0, "minecraft:oak_log[axis=y]"),
                new BlockOffset( 0, 3,  1, "minecraft:spruce_fence[east=false,north=true,south=false,waterlogged=false,west=false]"),
                new BlockOffset( 0, 4, -1, "minecraft:cobblestone_stairs[facing=south,half=top,shape=straight,waterlogged=false]"),
                new BlockOffset( 0, 4,  0, "minecraft:oak_log[axis=y]"),
                new BlockOffset( 0, 4,  1, "minecraft:cobblestone_stairs[facing=north,half=top,shape=straight,waterlogged=false]"),
                new BlockOffset( 0, 5, -1, "minecraft:cobblestone_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset( 0, 5,  0, "minecraft:stone_bricks"),
                new BlockOffset( 0, 5,  1, "minecraft:cobblestone_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset( 0, 6,  0, "minecraft:campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]"),
                new BlockOffset( 1, 0, -1, "minecraft:cobblestone_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset( 1, 0,  0, "minecraft:stone_bricks"),
                new BlockOffset( 1, 0,  1, "minecraft:cobblestone_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset( 1, 1,  0, "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=true]"),
                new BlockOffset( 1, 2,  0, "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=true]"),
                new BlockOffset( 1, 3,  0, "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=true]"),
                new BlockOffset( 1, 4,  0, "minecraft:cobblestone_stairs[facing=west,half=top,shape=straight,waterlogged=false]"),
                new BlockOffset( 1, 5,  0, "minecraft:cobblestone_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset( 2, 0,  0, "minecraft:spruce_slab[type=bottom,waterlogged=false]")
            );
            default -> List.of();
        };
    }

    // --- TREBUCHET ---

    private static List<BlockOffset> trebuchetBlocks(int level) {
        return switch (level) {
            case 1 -> List.of(
                new BlockOffset(-2, 1,  0, "minecraft:tnt[unstable=false]"),
                new BlockOffset(-2, 2,  0, "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]"),
                new BlockOffset(-2, 3,  0, "minecraft:spruce_fence[east=true,north=false,south=false,waterlogged=false,west=false]"),
                new BlockOffset(-1, 0, -1, "minecraft:oak_log[axis=x]"),
                new BlockOffset(-1, 0,  1, "minecraft:oak_log[axis=x]"),
                new BlockOffset(-1, 3,  0, "minecraft:spruce_fence[east=true,north=false,south=false,waterlogged=false,west=true]"),
                new BlockOffset( 0, 0, -1, "minecraft:oak_log[axis=y]"),
                new BlockOffset( 0, 0,  1, "minecraft:oak_log[axis=y]"),
                new BlockOffset( 0, 1, -1, "minecraft:oak_log[axis=y]"),
                new BlockOffset( 0, 1,  1, "minecraft:oak_log[axis=y]"),
                new BlockOffset( 0, 2, -1, "minecraft:oak_log[axis=y]"),
                new BlockOffset( 0, 2,  1, "minecraft:oak_log[axis=y]"),
                new BlockOffset( 0, 3, -1, "minecraft:spruce_fence[east=false,north=false,south=true,waterlogged=false,west=false]"),
                new BlockOffset( 0, 3,  0, "minecraft:spruce_fence[east=false,north=true,south=true,waterlogged=false,west=true]"),
                new BlockOffset( 0, 3,  1, "minecraft:spruce_fence[east=false,north=true,south=false,waterlogged=false,west=false]"),
                new BlockOffset( 1, 0, -1, "minecraft:oak_log[axis=x]"),
                new BlockOffset( 1, 0,  1, "minecraft:oak_log[axis=x]")
            );
            case 2 -> List.of(
                new BlockOffset(-2, 1,  0, "minecraft:tnt[unstable=false]"),
                new BlockOffset(-2, 2,  0, "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]"),
                new BlockOffset(-2, 3,  0, "minecraft:spruce_fence[east=true,north=false,south=false,waterlogged=false,west=false]"),
                new BlockOffset(-1, 0, -1, "minecraft:cobblestone"),
                new BlockOffset(-1, 0,  1, "minecraft:cobblestone"),
                new BlockOffset(-1, 2, -1, "minecraft:oak_log[axis=x]"),
                new BlockOffset(-1, 2,  1, "minecraft:oak_log[axis=x]"),
                new BlockOffset(-1, 3,  0, "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=true]"),
                new BlockOffset(-1, 4,  0, "minecraft:spruce_fence[east=true,north=false,south=false,waterlogged=false,west=false]"),
                new BlockOffset( 0, 0, -1, "minecraft:oak_log[axis=y]"),
                new BlockOffset( 0, 0,  1, "minecraft:oak_log[axis=y]"),
                new BlockOffset( 0, 1, -1, "minecraft:oak_log[axis=y]"),
                new BlockOffset( 0, 1,  1, "minecraft:oak_log[axis=y]"),
                new BlockOffset( 0, 2, -1, "minecraft:oak_log[axis=y]"),
                new BlockOffset( 0, 2,  1, "minecraft:oak_log[axis=y]"),
                new BlockOffset( 0, 3, -1, "minecraft:oak_log[axis=y]"),
                new BlockOffset( 0, 3,  1, "minecraft:oak_log[axis=y]"),
                new BlockOffset( 0, 4, -1, "minecraft:spruce_fence[east=false,north=false,south=true,waterlogged=false,west=false]"),
                new BlockOffset( 0, 4,  0, "minecraft:spruce_fence[east=false,north=true,south=true,waterlogged=false,west=true]"),
                new BlockOffset( 0, 4,  1, "minecraft:spruce_fence[east=false,north=true,south=false,waterlogged=false,west=false]"),
                new BlockOffset( 1, 0, -1, "minecraft:cobblestone"),
                new BlockOffset( 1, 0,  1, "minecraft:cobblestone"),
                new BlockOffset( 1, 2, -1, "minecraft:oak_log[axis=x]"),
                new BlockOffset( 1, 2,  1, "minecraft:oak_log[axis=x]")
            );
            case 3 -> List.of(
                new BlockOffset(-2, 1,  0, "minecraft:tnt[unstable=false]"),
                new BlockOffset(-2, 2,  0, "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]"),
                new BlockOffset(-2, 3,  0, "minecraft:spruce_fence[east=true,north=false,south=false,waterlogged=false,west=false]"),
                new BlockOffset(-1, 3,  0, "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=true]"),
                new BlockOffset(-1, 4,  0, "minecraft:spruce_fence[east=true,north=false,south=false,waterlogged=false,west=false]"),
                new BlockOffset( 0, 0, -1, "minecraft:cobblestone"),
                new BlockOffset( 0, 0,  1, "minecraft:cobblestone"),
                new BlockOffset( 0, 3, -1, "minecraft:oak_log[axis=x]"),
                new BlockOffset( 0, 3,  1, "minecraft:oak_log[axis=x]"),
                new BlockOffset( 0, 4,  0, "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=true]"),
                new BlockOffset( 0, 5,  0, "minecraft:spruce_fence[east=true,north=false,south=false,waterlogged=false,west=false]"),
                new BlockOffset( 1, 0, -2, "minecraft:cobblestone"),
                new BlockOffset( 1, 0, -1, "minecraft:cobblestone"),
                new BlockOffset( 1, 0,  1, "minecraft:cobblestone"),
                new BlockOffset( 1, 0,  2, "minecraft:cobblestone"),
                new BlockOffset( 1, 1, -1, "minecraft:oak_log[axis=y]"),
                new BlockOffset( 1, 1,  1, "minecraft:oak_log[axis=y]"),
                new BlockOffset( 1, 2, -1, "minecraft:oak_log[axis=y]"),
                new BlockOffset( 1, 2,  1, "minecraft:oak_log[axis=y]"),
                new BlockOffset( 1, 3, -1, "minecraft:oak_log[axis=y]"),
                new BlockOffset( 1, 3,  1, "minecraft:oak_log[axis=y]"),
                new BlockOffset( 1, 4, -1, "minecraft:oak_log[axis=y]"),
                new BlockOffset( 1, 4,  1, "minecraft:oak_log[axis=y]"),
                new BlockOffset( 1, 5, -1, "minecraft:spruce_fence[east=false,north=false,south=true,waterlogged=false,west=false]"),
                new BlockOffset( 1, 5,  0, "minecraft:spruce_fence[east=false,north=true,south=true,waterlogged=false,west=true]"),
                new BlockOffset( 1, 5,  1, "minecraft:spruce_fence[east=false,north=true,south=false,waterlogged=false,west=false]"),
                new BlockOffset( 2, 0, -1, "minecraft:cobblestone"),
                new BlockOffset( 2, 0,  1, "minecraft:cobblestone"),
                new BlockOffset( 2, 3, -1, "minecraft:oak_log[axis=x]"),
                new BlockOffset( 2, 3,  1, "minecraft:oak_log[axis=x]")
            );
            default -> List.of();
        };
    }

    // --- MAGE ---

    private static List<BlockOffset> mageBlocks(int level) {
        return switch (level) {
            case 1 -> List.of(
                new BlockOffset(-1, 0,  0, "minecraft:spruce_fence[east=true,north=false,south=false,waterlogged=false,west=false]"),
                new BlockOffset(-1, 1,  0, "minecraft:spruce_fence[east=true,north=false,south=false,waterlogged=false,west=false]"),
                new BlockOffset(-1, 2, -1, "minecraft:oak_slab[type=top,waterlogged=false]"),
                new BlockOffset(-1, 2,  0, "minecraft:oak_log[axis=x]"),
                new BlockOffset(-1, 2,  1, "minecraft:oak_slab[type=top,waterlogged=false]"),
                new BlockOffset(-1, 3,  0, "minecraft:purpur_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset( 0, 0, -1, "minecraft:spruce_fence[east=false,north=false,south=true,waterlogged=false,west=false]"),
                new BlockOffset( 0, 0,  0, "minecraft:oak_log[axis=y]"),
                new BlockOffset( 0, 0,  1, "minecraft:spruce_fence[east=false,north=true,south=false,waterlogged=false,west=false]"),
                new BlockOffset( 0, 1, -1, "minecraft:spruce_fence[east=false,north=false,south=true,waterlogged=false,west=false]"),
                new BlockOffset( 0, 1,  0, "minecraft:light_blue_stained_glass"),
                new BlockOffset( 0, 1,  1, "minecraft:spruce_fence[east=false,north=true,south=false,waterlogged=false,west=false]"),
                new BlockOffset( 0, 2, -1, "minecraft:oak_log[axis=z]"),
                new BlockOffset( 0, 2,  0, "minecraft:oak_log[axis=y]"),
                new BlockOffset( 0, 2,  1, "minecraft:oak_log[axis=z]"),
                new BlockOffset( 0, 3,  0, "minecraft:obsidian"),
                new BlockOffset( 1, 0,  0, "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=true]"),
                new BlockOffset( 1, 1,  0, "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=true]"),
                new BlockOffset( 1, 2, -1, "minecraft:oak_slab[type=top,waterlogged=false]"),
                new BlockOffset( 1, 2,  0, "minecraft:oak_log[axis=x]"),
                new BlockOffset( 1, 2,  1, "minecraft:oak_slab[type=top,waterlogged=false]"),
                new BlockOffset( 1, 3,  0, "minecraft:purpur_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]")
            );
            case 2 -> List.of(
                new BlockOffset(-1, 0, -1, "minecraft:oak_stairs[facing=south,half=top,shape=straight,waterlogged=false]"),
                new BlockOffset(-1, 0,  0, "minecraft:oak_log[axis=x]"),
                new BlockOffset(-1, 0,  1, "minecraft:oak_stairs[facing=north,half=top,shape=straight,waterlogged=false]"),
                new BlockOffset(-1, 1,  0, "minecraft:spruce_fence[east=true,north=false,south=false,waterlogged=false,west=false]"),
                new BlockOffset(-1, 2,  0, "minecraft:spruce_fence[east=true,north=false,south=false,waterlogged=false,west=false]"),
                new BlockOffset(-1, 3,  0, "minecraft:oak_stairs[facing=east,half=top,shape=straight,waterlogged=false]"),
                new BlockOffset( 0, 0, -1, "minecraft:oak_log[axis=z]"),
                new BlockOffset( 0, 0,  0, "minecraft:cracked_stone_bricks"),
                new BlockOffset( 0, 0,  1, "minecraft:oak_log[axis=z]"),
                new BlockOffset( 0, 1, -1, "minecraft:cobblestone_wall[east=none,north=none,south=tall,up=true,waterlogged=false,west=none]"),
                new BlockOffset( 0, 1,  0, "minecraft:cracked_stone_bricks"),
                new BlockOffset( 0, 1,  1, "minecraft:cobblestone_wall[east=none,north=tall,south=none,up=true,waterlogged=false,west=none]"),
                new BlockOffset( 0, 2, -1, "minecraft:cobblestone_wall[east=none,north=none,south=low,up=true,waterlogged=false,west=none]"),
                new BlockOffset( 0, 2,  0, "minecraft:cracked_stone_bricks"),
                new BlockOffset( 0, 2,  1, "minecraft:cobblestone_wall[east=none,north=low,south=none,up=true,waterlogged=false,west=none]"),
                new BlockOffset( 0, 3, -1, "minecraft:purpur_stairs[facing=south,half=top,shape=straight,waterlogged=false]"),
                new BlockOffset( 0, 3,  0, "minecraft:oak_log[axis=y]"),
                new BlockOffset( 0, 3,  1, "minecraft:purpur_stairs[facing=north,half=top,shape=straight,waterlogged=false]"),
                new BlockOffset( 0, 4, -1, "minecraft:purpur_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset( 0, 4,  0, "minecraft:obsidian"),
                new BlockOffset( 0, 4,  1, "minecraft:purpur_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset( 1, 0, -1, "minecraft:oak_stairs[facing=south,half=top,shape=straight,waterlogged=false]"),
                new BlockOffset( 1, 0,  0, "minecraft:oak_log[axis=x]"),
                new BlockOffset( 1, 0,  1, "minecraft:oak_stairs[facing=north,half=top,shape=straight,waterlogged=false]"),
                new BlockOffset( 1, 1,  0, "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=true]"),
                new BlockOffset( 1, 2,  0, "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=true]"),
                new BlockOffset( 1, 3,  0, "minecraft:oak_stairs[facing=west,half=top,shape=straight,waterlogged=false]")
            );
            case 3 -> List.of(
                new BlockOffset(-2, 0,  0, "minecraft:oak_slab[type=bottom,waterlogged=false]"),
                new BlockOffset(-1, 0, -1, "minecraft:oak_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset(-1, 0,  0, "minecraft:cracked_stone_bricks"),
                new BlockOffset(-1, 0,  1, "minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset(-1, 1,  0, "minecraft:polished_blackstone_brick_wall[east=tall,north=none,south=none,up=true,waterlogged=false,west=none]"),
                new BlockOffset(-1, 2,  0, "minecraft:polished_blackstone_brick_wall[east=tall,north=none,south=none,up=true,waterlogged=false,west=none]"),
                new BlockOffset(-1, 3,  0, "minecraft:polished_blackstone_brick_wall[east=low,north=none,south=none,up=true,waterlogged=false,west=none]"),
                new BlockOffset(-1, 4,  0, "minecraft:purpur_stairs[facing=east,half=top,shape=straight,waterlogged=false]"),
                new BlockOffset(-1, 5,  0, "minecraft:purpur_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset( 0, 0, -2, "minecraft:oak_slab[type=bottom,waterlogged=false]"),
                new BlockOffset( 0, 0, -1, "minecraft:cracked_stone_bricks"),
                new BlockOffset( 0, 0,  0, "minecraft:oak_log[axis=y]"),
                new BlockOffset( 0, 0,  1, "minecraft:cracked_stone_bricks"),
                new BlockOffset( 0, 0,  2, "minecraft:oak_slab[type=bottom,waterlogged=false]"),
                new BlockOffset( 0, 1, -1, "minecraft:polished_blackstone_brick_wall[east=none,north=none,south=tall,up=true,waterlogged=false,west=none]"),
                new BlockOffset( 0, 1,  0, "minecraft:obsidian"),
                new BlockOffset( 0, 1,  1, "minecraft:polished_blackstone_brick_wall[east=none,north=tall,south=none,up=true,waterlogged=false,west=none]"),
                new BlockOffset( 0, 2, -1, "minecraft:polished_blackstone_brick_wall[east=none,north=none,south=tall,up=true,waterlogged=false,west=none]"),
                new BlockOffset( 0, 2,  0, "minecraft:obsidian"),
                new BlockOffset( 0, 2,  1, "minecraft:polished_blackstone_brick_wall[east=none,north=tall,south=none,up=true,waterlogged=false,west=none]"),
                new BlockOffset( 0, 3, -1, "minecraft:polished_blackstone_brick_wall[east=none,north=none,south=low,up=true,waterlogged=false,west=none]"),
                new BlockOffset( 0, 3,  0, "minecraft:obsidian"),
                new BlockOffset( 0, 3,  1, "minecraft:polished_blackstone_brick_wall[east=none,north=low,south=none,up=true,waterlogged=false,west=none]"),
                new BlockOffset( 0, 4, -1, "minecraft:purpur_stairs[facing=south,half=top,shape=straight,waterlogged=false]"),
                new BlockOffset( 0, 4,  0, "minecraft:obsidian"),
                new BlockOffset( 0, 4,  1, "minecraft:purpur_stairs[facing=north,half=top,shape=straight,waterlogged=false]"),
                new BlockOffset( 0, 5, -1, "minecraft:purpur_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset( 0, 5,  0, "minecraft:obsidian"),
                new BlockOffset( 0, 5,  1, "minecraft:purpur_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset( 1, 0, -1, "minecraft:oak_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset( 1, 0,  0, "minecraft:cracked_stone_bricks"),
                new BlockOffset( 1, 0,  1, "minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset( 1, 1,  0, "minecraft:polished_blackstone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=tall]"),
                new BlockOffset( 1, 2,  0, "minecraft:polished_blackstone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=tall]"),
                new BlockOffset( 1, 3,  0, "minecraft:polished_blackstone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=low]"),
                new BlockOffset( 1, 4,  0, "minecraft:purpur_stairs[facing=west,half=top,shape=straight,waterlogged=false]"),
                new BlockOffset( 1, 5,  0, "minecraft:purpur_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset( 2, 0,  0, "minecraft:oak_slab[type=bottom,waterlogged=false]")
            );
            default -> List.of();
        };
    }

    // --- LIGHTNING ---

    private static List<BlockOffset> lightningBlocks(int level) {
        return switch (level) {
            case 1 -> List.of(
                new BlockOffset(-1, 0,  0, "minecraft:stone_bricks"),
                new BlockOffset(-1, 0,  1, "minecraft:cobblestone_wall[east=low,north=low,south=none,up=true,waterlogged=false,west=none]"),
                new BlockOffset(-1, 1,  0, "minecraft:stone_brick_stairs[facing=east,half=top,shape=straight,waterlogged=false]"),
                new BlockOffset(-1, 2,  0, "minecraft:cobblestone"),
                new BlockOffset( 0, 0, -1, "minecraft:stone_bricks"),
                new BlockOffset( 0, 0,  0, "minecraft:stone_bricks"),
                new BlockOffset( 0, 0,  1, "minecraft:stone_bricks"),
                new BlockOffset( 0, 1, -1, "minecraft:cobblestone_wall[east=low,north=none,south=low,up=true,waterlogged=false,west=none]"),
                new BlockOffset( 0, 1,  0, "minecraft:stone_bricks"),
                new BlockOffset( 0, 1,  1, "minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset( 0, 2,  0, "minecraft:stone_bricks"),
                new BlockOffset( 0, 2,  1, "minecraft:cobblestone"),
                new BlockOffset( 0, 3,  0, "minecraft:stone_bricks"),
                new BlockOffset( 0, 4,  0, "minecraft:deepslate_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]"),
                new BlockOffset( 0, 5,  0, "minecraft:deepslate_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]"),
                new BlockOffset( 1, 0, -1, "minecraft:stone_bricks"),
                new BlockOffset( 1, 0,  0, "minecraft:stone_bricks"),
                new BlockOffset( 1, 0,  1, "minecraft:cracked_stone_bricks"),
                new BlockOffset( 1, 1, -1, "minecraft:stone_bricks")
            );
            case 3 -> List.of(
                new BlockOffset(-2, 0,  0, "minecraft:cobblestone_wall[east=tall,north=none,south=none,up=true,waterlogged=false,west=none]"),
                new BlockOffset(-2, 1,  0, "minecraft:cobblestone_wall[east=low,north=none,south=none,up=true,waterlogged=false,west=none]"),
                new BlockOffset(-1, 0, -1, "minecraft:cobblestone"),
                new BlockOffset(-1, 0,  0, "minecraft:stone_bricks"),
                new BlockOffset(-1, 0,  1, "minecraft:cobblestone"),
                new BlockOffset(-1, 1,  0, "minecraft:stone_bricks"),
                new BlockOffset(-1, 2, -1, "minecraft:stone_bricks"),
                new BlockOffset( 0, 0, -2, "minecraft:cobblestone_wall[east=none,north=none,south=tall,up=true,waterlogged=false,west=none]"),
                new BlockOffset( 0, 0, -1, "minecraft:stone_bricks"),
                new BlockOffset( 0, 0,  0, "minecraft:stone_bricks"),
                new BlockOffset( 0, 0,  1, "minecraft:stone_bricks"),
                new BlockOffset( 0, 1, -2, "minecraft:cobblestone_wall[east=none,north=none,south=tall,up=true,waterlogged=false,west=none]"),
                new BlockOffset( 0, 1, -1, "minecraft:stone_bricks"),
                new BlockOffset( 0, 1,  0, "minecraft:stone_bricks"),
                new BlockOffset( 0, 1,  1, "minecraft:stone_bricks"),
                new BlockOffset( 0, 2, -2, "minecraft:cobblestone_wall[east=none,north=none,south=low,up=true,waterlogged=false,west=none]"),
                new BlockOffset( 0, 2, -1, "minecraft:stone_bricks"),
                new BlockOffset( 0, 2,  0, "minecraft:stone_bricks"),
                new BlockOffset( 0, 2,  1, "minecraft:cobblestone"),
                new BlockOffset( 0, 3, -1, "minecraft:stone_bricks"),
                new BlockOffset( 0, 3,  0, "minecraft:stone_bricks"),
                new BlockOffset( 0, 3,  1, "minecraft:cobblestone_wall[east=none,north=tall,south=none,up=true,waterlogged=false,west=none]"),
                new BlockOffset( 0, 4,  0, "minecraft:stone_bricks"),
                new BlockOffset( 0, 4,  1, "minecraft:cobblestone_wall[east=none,north=low,south=none,up=true,waterlogged=false,west=none]"),
                new BlockOffset( 0, 5,  0, "minecraft:cobblestone"),
                new BlockOffset( 0, 6,  0, "minecraft:stone_bricks"),
                new BlockOffset( 0, 7,  0, "minecraft:deepslate_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]"),
                new BlockOffset( 0, 8,  0, "minecraft:deepslate_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]"),
                new BlockOffset( 0, 9,  0, "minecraft:deepslate_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]"),
                new BlockOffset( 1, 0, -1, "minecraft:stone_bricks"),
                new BlockOffset( 1, 0,  0, "minecraft:stone_bricks"),
                new BlockOffset( 1, 0,  1, "minecraft:stone_bricks"),
                new BlockOffset( 1, 1, -1, "minecraft:stone_brick_stairs[facing=south,half=top,shape=straight,waterlogged=false]"),
                new BlockOffset( 1, 1,  0, "minecraft:cracked_stone_bricks"),
                new BlockOffset( 1, 1,  1, "minecraft:stone_bricks"),
                new BlockOffset( 1, 2, -1, "minecraft:stone_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset( 1, 2,  0, "minecraft:stone_bricks"),
                new BlockOffset( 1, 3,  0, "minecraft:cobblestone"),
                new BlockOffset( 1, 4,  0, "minecraft:stone_bricks")
            );
            case 2 -> List.of(
                new BlockOffset(-2, 0,  1, "minecraft:stone_brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset(-1, 0, -1, "minecraft:stone_bricks"),
                new BlockOffset(-1, 0,  0, "minecraft:cobblestone"),
                new BlockOffset(-1, 0,  1, "minecraft:stone_bricks"),
                new BlockOffset(-1, 0,  2, "minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset(-1, 1,  0, "minecraft:stone_brick_stairs[facing=east,half=top,shape=straight,waterlogged=false]"),
                new BlockOffset(-1, 1,  1, "minecraft:cobblestone"),
                new BlockOffset(-1, 2,  0, "minecraft:stone_bricks"),
                new BlockOffset(-1, 3,  0, "minecraft:cobblestone_wall[east=low,north=none,south=none,up=true,waterlogged=false,west=none]"),
                new BlockOffset( 0, 0, -2, "minecraft:stone_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset( 0, 0, -1, "minecraft:stone_bricks"),
                new BlockOffset( 0, 0,  0, "minecraft:stone_bricks"),
                new BlockOffset( 0, 0,  1, "minecraft:stone_bricks"),
                new BlockOffset( 0, 1, -1, "minecraft:stone_bricks"),
                new BlockOffset( 0, 1,  0, "minecraft:stone_bricks"),
                new BlockOffset( 0, 1,  1, "minecraft:cobblestone_wall[east=none,north=tall,south=none,up=true,waterlogged=false,west=low]"),
                new BlockOffset( 0, 2, -1, "minecraft:stone_bricks"),
                new BlockOffset( 0, 2,  0, "minecraft:stone_bricks"),
                new BlockOffset( 0, 2,  1, "minecraft:cobblestone_wall[east=none,north=low,south=none,up=true,waterlogged=false,west=none]"),
                new BlockOffset( 0, 3,  0, "minecraft:cobblestone"),
                new BlockOffset( 0, 4,  0, "minecraft:stone_bricks"),
                new BlockOffset( 0, 5,  0, "minecraft:deepslate_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]"),
                new BlockOffset( 0, 6,  0, "minecraft:deepslate_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]"),
                new BlockOffset( 0, 7,  0, "minecraft:deepslate_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]"),
                new BlockOffset( 1, 0, -1, "minecraft:stone_bricks"),
                new BlockOffset( 1, 0,  0, "minecraft:stone_bricks"),
                new BlockOffset( 1, 0,  1, "minecraft:cracked_stone_bricks"),
                new BlockOffset( 1, 1, -1, "minecraft:stone_brick_stairs[facing=west,half=top,shape=straight,waterlogged=false]"),
                new BlockOffset( 1, 1,  0, "minecraft:stone_bricks"),
                new BlockOffset( 1, 2,  0, "minecraft:stone_bricks"),
                new BlockOffset( 2, 0,  0, "minecraft:stone_brick_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]")
            );
            default -> List.of();
        };
    }

    // --- ICE ---

    private static List<BlockOffset> iceBlocks(int level) {
        return switch (level) {
            case 1 -> List.of(
                new BlockOffset(-1, 0,  0, "minecraft:quartz_stairs[facing=east,half=top,shape=straight,waterlogged=false]"),
                new BlockOffset(-1, 2,  0, "minecraft:quartz_stairs[facing=east,half=top,shape=straight,waterlogged=false]"),
                new BlockOffset( 0, 0, -1, "minecraft:quartz_stairs[facing=south,half=top,shape=straight,waterlogged=false]"),
                new BlockOffset( 0, 0,  0, "minecraft:quartz_pillar[axis=y]"),
                new BlockOffset( 0, 0,  1, "minecraft:quartz_stairs[facing=north,half=top,shape=straight,waterlogged=false]"),
                new BlockOffset( 0, 1,  0, "minecraft:quartz_pillar[axis=y]"),
                new BlockOffset( 0, 2, -1, "minecraft:quartz_stairs[facing=south,half=top,shape=straight,waterlogged=false]"),
                new BlockOffset( 0, 2,  0, "minecraft:quartz_pillar[axis=y]"),
                new BlockOffset( 0, 2,  1, "minecraft:quartz_stairs[facing=north,half=top,shape=straight,waterlogged=false]"),
                new BlockOffset( 0, 3,  0, "minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]"),
                new BlockOffset( 0, 4,  0, "minecraft:diamond_block"),
                new BlockOffset( 1, 0,  0, "minecraft:quartz_stairs[facing=west,half=top,shape=straight,waterlogged=false]"),
                new BlockOffset( 1, 2,  0, "minecraft:quartz_stairs[facing=west,half=top,shape=straight,waterlogged=false]")
            );
            case 2 -> List.of(
                new BlockOffset(-2, 0, -1, "minecraft:quartz_pillar[axis=y]"),
                new BlockOffset(-2, 0,  1, "minecraft:quartz_pillar[axis=y]"),
                new BlockOffset(-2, 1, -1, "minecraft:quartz_pillar[axis=y]"),
                new BlockOffset(-2, 1,  0, "minecraft:quartz_stairs[facing=east,half=top,shape=straight,waterlogged=false]"),
                new BlockOffset(-2, 1,  1, "minecraft:quartz_pillar[axis=y]"),
                new BlockOffset(-2, 2, -1, "minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]"),
                new BlockOffset(-2, 2,  0, "minecraft:smooth_quartz_slab[type=top,waterlogged=false]"),
                new BlockOffset(-2, 2,  1, "minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]"),
                new BlockOffset(-1, 0,  0, "minecraft:quartz_pillar[axis=y]"),
                new BlockOffset(-1, 1, -1, "minecraft:quartz_stairs[facing=south,half=top,shape=straight,waterlogged=false]"),
                new BlockOffset(-1, 1,  0, "minecraft:quartz_pillar[axis=y]"),
                new BlockOffset(-1, 1,  1, "minecraft:quartz_stairs[facing=north,half=top,shape=straight,waterlogged=false]"),
                new BlockOffset(-1, 2, -1, "minecraft:smooth_quartz_slab[type=top,waterlogged=false]"),
                new BlockOffset(-1, 2,  0, "minecraft:quartz_pillar[axis=y]"),
                new BlockOffset(-1, 2,  1, "minecraft:smooth_quartz_slab[type=top,waterlogged=false]"),
                new BlockOffset(-1, 3,  0, "minecraft:quartz_pillar[axis=y]"),
                new BlockOffset(-1, 4,  0, "minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]"),
                new BlockOffset(-1, 5,  0, "minecraft:diamond_block"),
                new BlockOffset( 0, 0, -1, "minecraft:quartz_pillar[axis=y]"),
                new BlockOffset( 0, 0,  1, "minecraft:quartz_pillar[axis=y]"),
                new BlockOffset( 0, 1, -1, "minecraft:quartz_pillar[axis=y]"),
                new BlockOffset( 0, 1,  0, "minecraft:quartz_stairs[facing=west,half=top,shape=straight,waterlogged=false]"),
                new BlockOffset( 0, 1,  1, "minecraft:quartz_pillar[axis=y]"),
                new BlockOffset( 0, 2, -1, "minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]"),
                new BlockOffset( 0, 2,  0, "minecraft:smooth_quartz_slab[type=top,waterlogged=false]"),
                new BlockOffset( 0, 2,  1, "minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]"),
                new BlockOffset( 1, 0,  2, "minecraft:short_grass")
            );
            case 3 -> List.of(
                new BlockOffset(-1, 0,  0, "minecraft:quartz_pillar[axis=y]"),
                new BlockOffset(-1, 1, -1, "minecraft:quartz_pillar[axis=y]"),
                new BlockOffset(-1, 1,  1, "minecraft:quartz_pillar[axis=y]"),
                new BlockOffset(-1, 2,  0, "minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]"),
                new BlockOffset(-1, 3,  0, "minecraft:quartz_pillar[axis=x]"),
                new BlockOffset(-1, 4, -1, "minecraft:quartz_pillar[axis=y]"),
                new BlockOffset(-1, 4,  1, "minecraft:quartz_pillar[axis=y]"),
                new BlockOffset(-1, 5, -1, "minecraft:quartz_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset(-1, 5,  1, "minecraft:quartz_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset( 0, 0, -1, "minecraft:quartz_pillar[axis=y]"),
                new BlockOffset( 0, 0,  0, "minecraft:quartz_pillar[axis=y]"),
                new BlockOffset( 0, 0,  1, "minecraft:quartz_pillar[axis=y]"),
                new BlockOffset( 0, 1,  0, "minecraft:quartz_pillar[axis=y]"),
                new BlockOffset( 0, 2, -1, "minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]"),
                new BlockOffset( 0, 2,  0, "minecraft:quartz_pillar[axis=y]"),
                new BlockOffset( 0, 2,  1, "minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]"),
                new BlockOffset( 0, 3, -1, "minecraft:quartz_pillar[axis=z]"),
                new BlockOffset( 0, 3,  0, "minecraft:quartz_pillar[axis=y]"),
                new BlockOffset( 0, 3,  1, "minecraft:quartz_pillar[axis=z]"),
                new BlockOffset( 0, 4,  0, "minecraft:quartz_pillar[axis=y]"),
                new BlockOffset( 0, 5,  0, "minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]"),
                new BlockOffset( 0, 6,  0, "minecraft:diamond_block"),
                new BlockOffset( 1, 0,  0, "minecraft:quartz_pillar[axis=y]"),
                new BlockOffset( 1, 1, -1, "minecraft:quartz_pillar[axis=y]"),
                new BlockOffset( 1, 1,  1, "minecraft:quartz_pillar[axis=y]"),
                new BlockOffset( 1, 2,  0, "minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]"),
                new BlockOffset( 1, 3,  0, "minecraft:quartz_pillar[axis=x]"),
                new BlockOffset( 1, 4, -1, "minecraft:quartz_pillar[axis=y]"),
                new BlockOffset( 1, 4,  1, "minecraft:quartz_pillar[axis=y]"),
                new BlockOffset( 1, 5, -1, "minecraft:quartz_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset( 1, 5,  1, "minecraft:quartz_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]")
            );
            default -> List.of();
        };
    }

    // --- CANNON ---

    private static List<BlockOffset> cannonBlocks(int level) {
        return switch (level) {
            case 1 -> List.of(
                new BlockOffset(-2, 0, -1, "minecraft:oak_log[axis=z]"),
                new BlockOffset(-2, 0,  0, "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]"),
                new BlockOffset(-2, 0,  1, "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]"),
                new BlockOffset(-2, 0,  2, "minecraft:oak_log[axis=z]"),
                new BlockOffset(-2, 1,  0, "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]"),
                new BlockOffset(-2, 1,  1, "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]"),
                new BlockOffset(-1, 0,  0, "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]"),
                new BlockOffset(-1, 0,  1, "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]"),
                new BlockOffset(-1, 1,  0, "minecraft:polished_blackstone_bricks"),
                new BlockOffset(-1, 1,  1, "minecraft:polished_blackstone_bricks"),
                new BlockOffset(-1, 2,  0, "minecraft:polished_blackstone_bricks"),
                new BlockOffset(-1, 2,  1, "minecraft:polished_blackstone_bricks"),
                new BlockOffset(-1, 3,  0, "minecraft:polished_blackstone_brick_wall[east=low,north=none,south=low,up=true,waterlogged=false,west=none]"),
                new BlockOffset(-1, 3,  1, "minecraft:polished_blackstone_brick_wall[east=low,north=low,south=none,up=true,waterlogged=false,west=none]"),
                new BlockOffset( 0, 0,  0, "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]"),
                new BlockOffset( 0, 0,  1, "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]"),
                new BlockOffset( 0, 1,  0, "minecraft:polished_blackstone_bricks"),
                new BlockOffset( 0, 1,  1, "minecraft:polished_blackstone_bricks"),
                new BlockOffset( 0, 2,  0, "minecraft:polished_blackstone_bricks"),
                new BlockOffset( 0, 2,  1, "minecraft:polished_blackstone_bricks"),
                new BlockOffset( 0, 3,  0, "minecraft:polished_blackstone_brick_wall[east=none,north=none,south=low,up=true,waterlogged=false,west=low]"),
                new BlockOffset( 0, 3,  1, "minecraft:polished_blackstone_brick_wall[east=none,north=low,south=none,up=true,waterlogged=false,west=low]"),
                new BlockOffset( 1, 0, -1, "minecraft:oak_log[axis=z]"),
                new BlockOffset( 1, 0,  0, "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]"),
                new BlockOffset( 1, 0,  1, "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]"),
                new BlockOffset( 1, 0,  2, "minecraft:oak_log[axis=z]"),
                new BlockOffset( 1, 1,  0, "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]"),
                new BlockOffset( 1, 1,  1, "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]")
            );
            case 3 -> List.of(
                new BlockOffset(-5, 0, -1, "minecraft:polished_blackstone_brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset(-5, 0,  2, "minecraft:polished_blackstone_brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset(-4, 0, -1, "minecraft:oak_log[axis=z]"),
                new BlockOffset(-4, 0,  0, "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]"),
                new BlockOffset(-4, 0,  1, "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]"),
                new BlockOffset(-4, 0,  2, "minecraft:oak_log[axis=z]"),
                new BlockOffset(-3, 0, -1, "minecraft:polished_blackstone_brick_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset(-3, 0,  0, "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]"),
                new BlockOffset(-3, 0,  1, "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]"),
                new BlockOffset(-3, 0,  2, "minecraft:polished_blackstone_brick_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset(-3, 1,  0, "minecraft:polished_blackstone_brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset(-3, 1,  1, "minecraft:polished_blackstone_brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset(-2, 0,  0, "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]"),
                new BlockOffset(-2, 0,  1, "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]"),
                new BlockOffset(-2, 1,  0, "minecraft:polished_blackstone_bricks"),
                new BlockOffset(-2, 1,  1, "minecraft:polished_blackstone_bricks"),
                new BlockOffset(-2, 2,  0, "minecraft:polished_blackstone_bricks"),
                new BlockOffset(-2, 2,  1, "minecraft:polished_blackstone_bricks"),
                new BlockOffset(-2, 3,  0, "minecraft:polished_blackstone_brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset(-2, 3,  1, "minecraft:polished_blackstone_brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset(-1, 0,  0, "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]"),
                new BlockOffset(-1, 0,  1, "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]"),
                new BlockOffset(-1, 1,  0, "minecraft:polished_blackstone_bricks"),
                new BlockOffset(-1, 1,  1, "minecraft:polished_blackstone_bricks"),
                new BlockOffset(-1, 2,  0, "minecraft:polished_blackstone_bricks"),
                new BlockOffset(-1, 2,  1, "minecraft:polished_blackstone_bricks"),
                new BlockOffset(-1, 3,  0, "minecraft:polished_blackstone_bricks"),
                new BlockOffset(-1, 3,  1, "minecraft:polished_blackstone_bricks"),
                new BlockOffset(-1, 4,  0, "minecraft:polished_blackstone_brick_wall[east=low,north=none,south=low,up=true,waterlogged=false,west=none]"),
                new BlockOffset(-1, 4,  1, "minecraft:polished_blackstone_brick_wall[east=low,north=low,south=none,up=true,waterlogged=false,west=none]"),
                new BlockOffset( 0, 0,  0, "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]"),
                new BlockOffset( 0, 0,  1, "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]"),
                new BlockOffset( 0, 1,  0, "minecraft:polished_blackstone_bricks"),
                new BlockOffset( 0, 1,  1, "minecraft:polished_blackstone_bricks"),
                new BlockOffset( 0, 2,  0, "minecraft:polished_blackstone_bricks"),
                new BlockOffset( 0, 2,  1, "minecraft:polished_blackstone_bricks"),
                new BlockOffset( 0, 3,  0, "minecraft:polished_blackstone_bricks"),
                new BlockOffset( 0, 3,  1, "minecraft:polished_blackstone_bricks"),
                new BlockOffset( 0, 4,  0, "minecraft:polished_blackstone_brick_wall[east=none,north=none,south=low,up=true,waterlogged=false,west=low]"),
                new BlockOffset( 0, 4,  1, "minecraft:polished_blackstone_brick_wall[east=none,north=low,south=none,up=true,waterlogged=false,west=low]"),
                new BlockOffset( 1, 0, -1, "minecraft:polished_blackstone_brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset( 1, 0,  0, "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]"),
                new BlockOffset( 1, 0,  1, "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]"),
                new BlockOffset( 1, 0,  2, "minecraft:polished_blackstone_brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset( 1, 1,  0, "minecraft:polished_blackstone_brick_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset( 1, 1,  1, "minecraft:polished_blackstone_brick_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset( 2, 0, -1, "minecraft:oak_log[axis=z]"),
                new BlockOffset( 2, 0,  0, "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]"),
                new BlockOffset( 2, 0,  1, "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]"),
                new BlockOffset( 2, 0,  2, "minecraft:oak_log[axis=z]"),
                new BlockOffset( 3, 0, -1, "minecraft:polished_blackstone_brick_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset( 3, 0,  2, "minecraft:polished_blackstone_brick_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset( 4, 0, -1, "minecraft:short_grass"),
                new BlockOffset( 4, 0,  0, "minecraft:short_grass")
            );
            case 2 -> List.of(
                new BlockOffset(-4, 0, -1, "minecraft:polished_blackstone_brick_wall[east=low,north=none,south=none,up=true,waterlogged=false,west=none]"),
                new BlockOffset(-4, 0,  1, "minecraft:short_grass"),
                new BlockOffset(-4, 0,  2, "minecraft:polished_blackstone_brick_wall[east=low,north=none,south=none,up=true,waterlogged=false,west=none]"),
                new BlockOffset(-3, 0, -1, "minecraft:oak_log[axis=z]"),
                new BlockOffset(-3, 0,  0, "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]"),
                new BlockOffset(-3, 0,  1, "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]"),
                new BlockOffset(-3, 0,  2, "minecraft:oak_log[axis=z]"),
                new BlockOffset(-2, 0, -1, "minecraft:polished_blackstone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=low]"),
                new BlockOffset(-2, 0,  0, "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]"),
                new BlockOffset(-2, 0,  1, "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]"),
                new BlockOffset(-2, 0,  2, "minecraft:polished_blackstone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=low]"),
                new BlockOffset(-2, 1,  0, "minecraft:polished_blackstone_brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset(-2, 1,  1, "minecraft:polished_blackstone_brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset(-1, 0,  0, "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]"),
                new BlockOffset(-1, 0,  1, "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]"),
                new BlockOffset(-1, 1,  0, "minecraft:cracked_polished_blackstone_bricks"),
                new BlockOffset(-1, 1,  1, "minecraft:cracked_polished_blackstone_bricks"),
                new BlockOffset(-1, 2,  0, "minecraft:cracked_polished_blackstone_bricks"),
                new BlockOffset(-1, 2,  1, "minecraft:cracked_polished_blackstone_bricks"),
                new BlockOffset(-1, 3,  0, "minecraft:polished_blackstone_brick_wall[east=low,north=none,south=low,up=true,waterlogged=false,west=none]"),
                new BlockOffset(-1, 3,  1, "minecraft:polished_blackstone_brick_wall[east=low,north=low,south=none,up=true,waterlogged=false,west=none]"),
                new BlockOffset( 0, 0,  0, "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]"),
                new BlockOffset( 0, 0,  1, "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]"),
                new BlockOffset( 0, 1,  0, "minecraft:cracked_polished_blackstone_bricks"),
                new BlockOffset( 0, 1,  1, "minecraft:cracked_polished_blackstone_bricks"),
                new BlockOffset( 0, 2,  0, "minecraft:cracked_polished_blackstone_bricks"),
                new BlockOffset( 0, 2,  1, "minecraft:cracked_polished_blackstone_bricks"),
                new BlockOffset( 0, 3,  0, "minecraft:polished_blackstone_brick_wall[east=none,north=none,south=low,up=true,waterlogged=false,west=low]"),
                new BlockOffset( 0, 3,  1, "minecraft:polished_blackstone_brick_wall[east=none,north=low,south=none,up=true,waterlogged=false,west=low]"),
                new BlockOffset( 1, 0, -1, "minecraft:polished_blackstone_brick_wall[east=low,north=none,south=none,up=true,waterlogged=false,west=none]"),
                new BlockOffset( 1, 0,  0, "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]"),
                new BlockOffset( 1, 0,  1, "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]"),
                new BlockOffset( 1, 0,  2, "minecraft:polished_blackstone_brick_wall[east=low,north=none,south=none,up=true,waterlogged=false,west=none]"),
                new BlockOffset( 1, 1,  0, "minecraft:polished_blackstone_brick_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset( 1, 1,  1, "minecraft:polished_blackstone_brick_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]"),
                new BlockOffset( 2, 0, -1, "minecraft:oak_log[axis=z]"),
                new BlockOffset( 2, 0,  0, "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]"),
                new BlockOffset( 2, 0,  1, "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]"),
                new BlockOffset( 2, 0,  2, "minecraft:oak_log[axis=z]"),
                new BlockOffset( 3, 0, -1, "minecraft:polished_blackstone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=low]"),
                new BlockOffset( 3, 0,  2, "minecraft:polished_blackstone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=low]"),
                new BlockOffset( 4, 0, -1, "minecraft:short_grass"),
                new BlockOffset( 4, 0,  0, "minecraft:short_grass")
            );
            default -> List.of();
        };
    }

    /** Fallback structure — simple stone pillar. */
    private static List<BlockOffset> defaultBlocks(int level) {
        List<BlockOffset> b = new ArrayList<>();
        int height = level + 2;
        for (int y = 0; y <= height; y++) b.add(new BlockOffset(0, y, 0, Material.STONE_BRICKS));
        return b;
    }

    // ── Structures par défaut non-tour ─────────────────────────────────────────

    private static List<BlockOffset> hardcodedCatBlocks(SchematicCategory cat, int level) {
        return switch (cat) {
            case SPAWN_MONSTER -> spawnMonsterBlocks();
            case TOWER_SLOT    -> towerSlotBlocks();
            default            -> List.of();
        };
    }

    /**
     * Portail médiéval d'entrée des monstres.
     *
     * Structure (vue de face, profondeur Z=0) :
     *   - Deux piliers de pierre (X=-2 et X=2) du sol (Y=0) jusqu'en Y=4
     *   - Crénelage sur les piliers en Y=5
     *   - Linteau de pierre taillée en Y=4 de X=-2 à X=2
     *   - Ouverture centrale (X=-1..1, Y=1..3) libre
     *   - Barres de fer (iron bars) en X=-1 et X=1 sur Y=1..2 (portcullis entrouverte)
     *   - Torches sur les piliers intérieurs en Y=3
     *   - Sol pavé de cobblestone (Z=-1..1, Y=0, X=-2..2)
     */
    private static List<BlockOffset> spawnMonsterBlocks() {
        return List.of(
            new BlockOffset(-2, 0, -1, "minecraft:andesite_wall[east=none,north=none,south=low,up=true,waterlogged=false,west=none]"),
            new BlockOffset(-2, 0,  0, "minecraft:nether_bricks"),
            new BlockOffset(-2, 0,  1, "minecraft:nether_bricks"),
            new BlockOffset(-1, 0, -3, "minecraft:short_grass"),
            new BlockOffset( 0, 0, -3, "minecraft:andesite_wall[east=low,north=none,south=none,up=true,waterlogged=false,west=none]"),
            new BlockOffset( 1, 0, -3, "minecraft:cracked_stone_bricks"),
            new BlockOffset( 1, 0,  1, "minecraft:andesite_wall[east=low,north=none,south=low,up=true,waterlogged=false,west=none]"),
            new BlockOffset( 1, 0,  2, "minecraft:nether_bricks"),
            new BlockOffset( 1, 1,  2, "minecraft:soul_campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]"),
            new BlockOffset( 2, 0, -2, "minecraft:cracked_stone_bricks"),
            new BlockOffset( 2, 0,  1, "minecraft:nether_bricks"),
            new BlockOffset( 2, 1, -2, "minecraft:andesite_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]"),
            new BlockOffset( 2, 1,  2, "minecraft:barrier[waterlogged=false]")
        );
    }

    /**
     * Marqueur d'emplacement de tour.
     * Plateforme de pierre 3×3 légèrement surélevée avec un bloc d'or central.
     */
    private static List<BlockOffset> towerSlotBlocks() {
        return List.of(
            new BlockOffset(-1, 0, -1, "minecraft:spruce_slab[type=bottom,waterlogged=false]"),
            new BlockOffset(-1, 0,  0, "minecraft:spruce_slab[type=bottom,waterlogged=false]"),
            new BlockOffset(-1, 0,  1, "minecraft:oak_log[axis=y]"),
            new BlockOffset(-1, 1,  1, "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]"),
            new BlockOffset( 0, 0,  0, "minecraft:campfire[facing=west,lit=true,signal_fire=false,waterlogged=false]"),
            new BlockOffset( 1, 0, -1, "minecraft:spruce_slab[type=bottom,waterlogged=false]"),
            new BlockOffset( 1, 0,  1, "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]")
        );
    }
}
