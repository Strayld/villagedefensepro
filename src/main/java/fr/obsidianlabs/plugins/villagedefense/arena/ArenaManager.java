package fr.obsidianlabs.plugins.villagedefense.arena;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.api.VillageDefenseProgressType;
import fr.obsidianlabs.plugins.villagedefense.tower.SchematicCategory;
import fr.obsidianlabs.plugins.villagedefense.tower.TowerSlot;
import fr.obsidianlabs.plugins.villagedefense.tower.TowerType;
import fr.obsidianlabs.plugins.villagedefense.trap.TrapSlot;
import fr.obsidianlabs.plugins.villagedefense.util.LocationUtil;
import fr.obsidianlabs.plugins.villagedefense.util.Perm;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class ArenaManager {

    private final VillageDefensePlugin plugin;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();
    private final Map<UUID, ArenaSetup> activeSetups = new HashMap<>();

    private File arenasFolder;

    public ArenaManager(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        arenasFolder = new File(plugin.getDataFolder(), "arenas");
        if (!arenasFolder.exists()) arenasFolder.mkdirs();
        // Délai d'1 tick pour garantir que tous les mondes sont disponibles
        // avant de désérialiser les locations (Bukkit.getWorld() fiable).
        plugin.getServer().getScheduler().runTaskLater(plugin, this::loadAll, 1L);
    }

    // ---- Chargement / Sauvegarde ----

    public void loadAll() {
        arenas.clear();
        File[] files = arenasFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;
        for (File file : files) {
            try {
                Arena arena = loadFromFile(file);
                if (arena != null) {
                    arenas.put(arena.getId(), arena);
                    plugin.getLogger().info("[ArenaManager] Arène chargée: " + arena.getId());
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[ArenaManager] Erreur chargement " + file.getName(), e);
            }
        }
        plugin.getLogger().info("[ArenaManager] " + arenas.size() + " arène(s) chargée(s).");
        if (plugin.getLeaderboardDisplayManager() != null) {
            plugin.getLeaderboardDisplayManager().refreshNow();
        }
    }

    private Arena loadFromFile(File file) {
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        String id = config.getString("id");
        if (id == null) return null;

        Arena arena = new Arena(id);
        arena.setDisplayName(config.getString("display-name", id));
        arena.setMaxPlayers(config.getInt("max-players", 6));
        arena.setMinPlayers(config.getInt("min-players", 1));
        arena.setAvailability(loadAvailability(config));
        try {
            String bm = config.getString("boundary-mode");
            if (bm != null) arena.setBoundaryMode(ArenaBoundaryMode.valueOf(bm));
        } catch (IllegalArgumentException ignored) {}

        arena.setWaveLimit(config.getInt("wave-limit", 0));
        arena.setAttemptLimit(config.getInt("attempt-limit", 0));
        arena.setAttemptWindowHours(config.getInt("attempt-window-hours", 12));
        arena.setRewardVaultEnabled(config.getBoolean("reward-vault-enabled", false));
        arena.setRewardVaultAmount(config.getDouble("reward-vault-amount", 0.0));
        config.getStringList("reward-commands").forEach(arena::addRewardCommand);

        arena.setLobbyLocation(loadLoc(config, "lobby", id));
        arena.setGameSpawnLocation(loadLoc(config, "game-spawn", id));
        arena.setSignLocation(loadLoc(config, "sign-location", id));
        arena.setLeaderboardLocation(loadLoc(config, "leaderboard-location", id));
        arena.setArenaLeaderboardLocation(loadLoc(config, "arena-leaderboard-location", id));
        arena.setVillageCenter(loadLoc(config, "village-center", id));
        arena.setPos1(loadLoc(config, "pos1", id));
        arena.setPos2(loadLoc(config, "pos2", id));
        arena.setShopChestLocation(loadLoc(config, "shop-chest", id));
        arena.setShopVillagerLocation(loadLoc(config, "shop-villager", id));
        arena.setEnchantVillagerLocation(loadLoc(config, "enchant-villager", id));

        String iconName = config.getString("custom-icon");
        if (iconName != null) {
            try { arena.setCustomIcon(org.bukkit.Material.valueOf(iconName)); }
            catch (IllegalArgumentException ignored) {}
        }

        // Points de spawn monstres
        List<String> spawns = config.getStringList("mob-spawns");
        for (String s : spawns) {
            var loc = LocationUtil.deserialize(s);
            if (loc != null) arena.addMobSpawnPoint(loc);
        }

        // Points de spawn villageois
        List<String> villagerSpawns = config.getStringList("villager-spawns");
        for (String s : villagerSpawns) {
            var loc = LocationUtil.deserialize(s);
            if (loc != null) arena.addVillagerSpawnPoint(loc);
        }

        // Emplacements de tours
        ConfigurationSection slotsSection = config.getConfigurationSection("tower-slots");
        if (slotsSection != null) {
            for (String slotId : slotsSection.getKeys(false)) {
                var loc = LocationUtil.deserialize(slotsSection.getString(slotId));
                if (loc != null) arena.addTowerSlot(new TowerSlot(slotId, loc));
            }
        }

        // Prix par slot de coffre shop
        ConfigurationSection pricesSection = config.getConfigurationSection("shop-item-prices");
        if (pricesSection != null) {
            for (String key : pricesSection.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(key);
                    arena.setShopItemPrice(slot, pricesSection.getLong(key));
                } catch (NumberFormatException ignored) {}
            }
        }

        // Prix par option d'enchantement
        ConfigurationSection enchantSection = config.getConfigurationSection("enchant-prices");
        if (enchantSection != null) {
            for (String key : enchantSection.getKeys(false)) {
                try {
                    int idx = Integer.parseInt(key);
                    arena.setEnchantPrice(idx, enchantSection.getLong(key));
                } catch (NumberFormatException ignored) {}
            }
        }

        // Config tours (coûts, levels requis, désactivées)
        ConfigurationSection towerCostSection = config.getConfigurationSection("tower-cost-overrides");
        if (towerCostSection != null) {
            for (String key : towerCostSection.getKeys(false)) {
                try { arena.setTowerCostOverride(TowerType.valueOf(key.toUpperCase()), towerCostSection.getInt(key)); }
                catch (IllegalArgumentException ignored) {}
            }
        }
        ConfigurationSection towerLevelSection = config.getConfigurationSection("tower-level-required");
        if (towerLevelSection != null) {
            for (String key : towerLevelSection.getKeys(false)) {
                try { arena.setTowerLevelRequired(TowerType.valueOf(key.toUpperCase()), towerLevelSection.getInt(key)); }
                catch (IllegalArgumentException ignored) {}
            }
        }
        List<String> disabledList = config.getStringList("disabled-towers");
        for (String key : disabledList) {
            try { arena.setTowerDisabled(TowerType.valueOf(key.toUpperCase()), true); }
            catch (IllegalArgumentException ignored) {}
        }

        // Emplacements de pièges
        ConfigurationSection trapSection = config.getConfigurationSection("trap-slots");
        if (trapSection != null) {
            for (String slotId : trapSection.getKeys(false)) {
                var loc = LocationUtil.deserialize(trapSection.getString(slotId));
                if (loc != null) arena.addTrapSlot(new TrapSlot(slotId, loc));
            }
        }

        // Chemins waypoints
        ConfigurationSection pathsSection = config.getConfigurationSection("paths");
        if (pathsSection != null) {
            for (String pathId : pathsSection.getKeys(false)) {
                ArenaPath path = new ArenaPath(pathId);
                List<String> wpList = pathsSection.getStringList(pathId + ".waypoints");
                for (String s : wpList) {
                    var loc = LocationUtil.deserialize(s);
                    if (loc != null) path.addWaypoint(loc);
                }
                if (!path.isEmpty()) arena.addPath(path);
            }
        }

        // Assignations spawner → chemin (stockées comme liste "locKey|pathId")
        List<String> assignList = config.getStringList("spawner-path-assignments");
        for (String entry : assignList) {
            int sep = entry.lastIndexOf('|');
            if (sep > 0) {
                String locKey = entry.substring(0, sep);
                String pathId = entry.substring(sep + 1);
                arena.setSpawnerPathAssignment(locKey, pathId);
            }
        }

        return arena;
    }

    public void saveArena(Arena arena) {
        File file = new File(arenasFolder, arena.getId() + ".yml");
        FileConfiguration config = new YamlConfiguration();

        config.set("id", arena.getId());
        config.set("display-name", arena.getDisplayName());
        config.set("max-players", arena.getMaxPlayers());
        config.set("min-players", arena.getMinPlayers());
        config.set("availability", arena.getAvailability().name());
        config.set("boundary-mode", arena.getBoundaryMode().name());
        config.set("wave-limit", arena.getWaveLimit());
        config.set("attempt-limit", arena.getAttemptLimit());
        config.set("attempt-window-hours", arena.getAttemptWindowHours());
        config.set("reward-vault-enabled", arena.isRewardVaultEnabled());
        config.set("reward-vault-amount", arena.getRewardVaultAmount());
        config.set("reward-commands", new java.util.ArrayList<>(arena.getRewardCommands()));
        config.set("enabled", arena.isEnabled());

        LocationUtil.save(config, "lobby", arena.getLobbyLocation());
        LocationUtil.save(config, "game-spawn", arena.getGameSpawnLocation());
        LocationUtil.save(config, "sign-location", arena.getSignLocation());
        LocationUtil.save(config, "leaderboard-location", arena.getLeaderboardLocation());
        LocationUtil.save(config, "arena-leaderboard-location", arena.getArenaLeaderboardLocation());
        LocationUtil.save(config, "village-center", arena.getVillageCenter());
        LocationUtil.save(config, "pos1", arena.getPos1());
        LocationUtil.save(config, "pos2", arena.getPos2());
        LocationUtil.save(config, "shop-chest", arena.getShopChestLocation());
        LocationUtil.save(config, "shop-villager", arena.getShopVillagerLocation());
        LocationUtil.save(config, "enchant-villager", arena.getEnchantVillagerLocation());
        if (arena.getCustomIcon() != null)
            config.set("custom-icon", arena.getCustomIcon().name());

        List<String> spawns = new ArrayList<>();
        for (var loc : arena.getMobSpawnPoints()) spawns.add(LocationUtil.serialize(loc));
        config.set("mob-spawns", spawns);

        List<String> vSpawns = new ArrayList<>();
        for (var loc : arena.getVillagerSpawnPoints()) vSpawns.add(LocationUtil.serialize(loc));
        config.set("villager-spawns", vSpawns);

        for (TowerSlot slot : arena.getTowerSlots()) {
            config.set("tower-slots." + slot.getId(), LocationUtil.serialize(slot.getLocation()));
        }

        // Prix par slot de coffre shop
        arena.getShopItemPrices().forEach((slot, price) ->
            config.set("shop-item-prices." + slot, price));

        // Prix par option d'enchantement
        arena.getEnchantPrices().forEach((idx, price) ->
            config.set("enchant-prices." + idx, price));

        // Config tours
        arena.getTowerCostOverrides().forEach((type, cost) ->
            config.set("tower-cost-overrides." + type.name().toLowerCase(), cost));
        arena.getTowerLevelRequiredMap().forEach((type, lvl) ->
            config.set("tower-level-required." + type.name().toLowerCase(), lvl));
        List<String> disabledNames = new ArrayList<>();
        for (TowerType t : arena.getDisabledTowers()) disabledNames.add(t.name().toLowerCase());
        config.set("disabled-towers", disabledNames);

        // Emplacements de pièges
        for (TrapSlot trap : arena.getTrapSlots()) {
            config.set("trap-slots." + trap.getId(), LocationUtil.serialize(trap.getLocation()));
        }

        // Chemins waypoints
        for (ArenaPath path : arena.getPaths()) {
            List<String> wpList = new ArrayList<>();
            for (org.bukkit.Location wp : path.getWaypoints()) wpList.add(LocationUtil.serialize(wp));
            config.set("paths." + path.getId() + ".waypoints", wpList);
        }

        // Assignations spawner → chemin (liste "locKey|pathId")
        List<String> assignList = new ArrayList<>();
        arena.getSpawnerPathAssignments().forEach((locKey, pathId) ->
            assignList.add(locKey + "|" + pathId));
        config.set("spawner-path-assignments", assignList);

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[ArenaManager] Erreur sauvegarde arène " + arena.getId(), e);
        }
        if (plugin.getLeaderboardDisplayManager() != null) {
            plugin.getLeaderboardDisplayManager().refreshNow();
        }
    }

    public void deleteArena(String id) {
        Arena arena = arenas.remove(id);
        if (arena != null) {
            // Nettoyer les blocs placés dans le monde (utilise la surcharge arène si elle existe)
            for (var spawn : arena.getMobSpawnPoints())
                ArenaSetup.clearSchematic(spawn, SchematicCategory.SPAWN_MONSTER, 1, arena.getId());
            for (TowerSlot slot : arena.getTowerSlots())
                if (slot.isEmpty())
                    ArenaSetup.clearSchematic(slot.getLocation(), SchematicCategory.TOWER_SLOT, 1, arena.getId());
        }
        File file = new File(arenasFolder, id + ".yml");
        if (file.exists()) file.delete();
        if (plugin.getLeaderboardDisplayManager() != null) {
            plugin.getLeaderboardDisplayManager().refreshNow();
        }
    }

    // ---- Gestion des arènes ----

    public Arena createArena(String id) {
        Arena arena = new Arena(id);
        arenas.put(id, arena);
        return arena;
    }

    public Arena getArena(String id) { return arenas.get(id); }
    public boolean arenaExists(String id) { return arenas.containsKey(id); }
    public Collection<Arena> getAllArenas() { return arenas.values(); }

    public Arena getArenaOfPlayer(Player player) {
        for (Arena arena : arenas.values()) {
            if (arena.hasPlayer(player)) return arena;
        }
        return null;
    }

    /** Retourne l'arène dont le joueur est spectateur, ou null. */
    public Arena getArenaOfSpectator(Player player) {
        for (Arena arena : arenas.values()) {
            if (arena.hasSpectator(player)) return arena;
        }
        return null;
    }

    /** Retourne l'arène dont le joueur est participant (joueur OU spectateur), ou null. */
    public Arena getArenaOfParticipant(Player player) {
        Arena a = getArenaOfPlayer(player);
        return a != null ? a : getArenaOfSpectator(player);
    }

    public boolean canUseTestMode(Player player) {
        return player != null && (
            plugin.hasPermission(player, Perm.TEST)
                || plugin.hasPermission(player, Perm.ADMIN)
                || plugin.hasPermission(player, Perm.BYPASS)
        );
    }

    public boolean canPlayerAccessArena(Player player, Arena arena) {
        if (arena == null) return false;
        return switch (arena.getAvailability()) {
            case ACTIVE -> true;
            case TEST -> canUseTestMode(player);
            case DISABLED -> false;
        };
    }

    public boolean shouldShowArena(Player player, Arena arena) {
        return arena != null && arena.isSetup() && !arena.isDisabled() && canPlayerAccessArena(player, arena);
    }

    public boolean canPlayerJoinArena(Player player, Arena arena) {
        return canPlayerAccessArena(player, arena) && arena.isJoinWindowOpen();
    }

    public boolean canPlayerSpectateArena(Player player, Arena arena) {
        return canPlayerAccessArena(player, arena) && arena.isSpectatable();
    }

    // ---- Setup ----

    public ArenaSetup startSetup(Player admin, Arena arena) {
        ArenaSetup setup = new ArenaSetup(plugin, admin, arena);
        activeSetups.put(admin.getUniqueId(), setup);
        if (arena != null) arena.setState(ArenaState.SETUP);
        return setup;
    }

    /** Démarre une session de capture de schematic sans arène associée. */
    public ArenaSetup startSchematicSetup(Player admin) {
        return startSetup(admin, null);
    }

    public void endSetup(Player admin) {
        activeSetups.remove(admin.getUniqueId());
    }

    public ArenaSetup getSetup(Player admin) {
        return activeSetups.get(admin.getUniqueId());
    }

    public boolean isInSetup(Player admin) {
        return activeSetups.containsKey(admin.getUniqueId());
    }

    // ---- Join / Leave ----

    public boolean joinArena(Player player, Arena arena) {
        if (!canPlayerJoinArena(player, arena)) return false;

        // Vérification de la limite de tentatives
        if (arena.hasAttemptLimit() && plugin.getAttemptManager() != null) {
            if (plugin.getAttemptManager().isBlocked(player.getUniqueId(), arena)) {
                long remaining = plugin.getAttemptManager().getWindowRemainingSeconds(player.getUniqueId(), arena);
                long hours = remaining / 3600;
                long mins  = (remaining % 3600) / 60;
                fr.obsidianlabs.plugins.villagedefense.util.ChatUtil.send(player,
                    plugin.getMessages().format("arena.attempt-blocked",
                        arena.getAttemptLimit(), arena.getAttemptWindowHours(), hours, mins));
                return false;
            }
        }

        arena.addPlayer(player);
        player.teleport(arena.getLobbyLocation());
        plugin.getGameManager().onPlayerJoinArena(player, arena);
        plugin.fireProgress(player, arena, VillageDefenseProgressType.ARENA_JOIN, 1, 0, "join");
        return true;
    }

    public boolean rejoinArena(Player player, Arena arena) {
        if (arena.getState() != ArenaState.RUNNING) return false;
        arena.addPlayer(player);
        plugin.getGameManager().onPlayerRejoinArena(player, arena);
        plugin.fireProgress(player, arena, VillageDefenseProgressType.ARENA_JOIN, 1, 0, "rejoin");
        return true;
    }

    public void leaveArena(Player player) {
        leaveArena(player, true);
    }

    public void leaveArena(Player player, boolean allowProxyReturn) {
        // Joueur normal
        Arena arena = getArenaOfPlayer(player);
        if (arena != null) {
            arena.removePlayer(player);
            plugin.getGameManager().onPlayerLeaveArena(player, arena);
            plugin.fireProgress(player, arena, VillageDefenseProgressType.ARENA_LEAVE, 1, 0, "player");
            plugin.getPlayerManager().restorePlayer(player);
            if (allowProxyReturn && plugin.getHookManager().sendPlayerToReturnServer(player)) {
                return;
            }
            var vdp = plugin.getPlayerManager().getPlayer(player);
            org.bukkit.Location returnLoc = (vdp != null && vdp.getReturnLocation() != null)
                ? vdp.getReturnLocation() : player.getWorld().getSpawnLocation();
            player.teleport(returnLoc);
            return;
        }
        // Spectateur
        Arena specArena = getArenaOfSpectator(player);
        if (specArena != null) {
            specArena.removeSpectator(player);
            plugin.getGameManager().onSpectatorLeaveArena(player, specArena);
            plugin.fireProgress(player, specArena, VillageDefenseProgressType.ARENA_LEAVE, 1, 0, "spectator");
            plugin.getPlayerManager().restorePlayer(player);
            if (allowProxyReturn && plugin.getHookManager().sendPlayerToReturnServer(player)) {
                return;
            }
            var vdp = plugin.getPlayerManager().getPlayer(player);
            org.bukkit.Location returnLoc = (vdp != null && vdp.getReturnLocation() != null)
                ? vdp.getReturnLocation() : player.getWorld().getSpawnLocation();
            player.teleport(returnLoc);
        }
    }

    public boolean joinAsSpectator(Player player, Arena arena) {
        if (!canPlayerSpectateArena(player, arena)) return false;
        arena.addSpectator(player);
        plugin.getGameManager().onSpectatorJoinArena(player, arena);
        plugin.fireProgress(player, arena, VillageDefenseProgressType.ARENA_SPECTATE_JOIN, 1, 0, "");
        return true;
    }

    // ---- Helpers ----

    /** Charge une location en loggant un avertissement si le monde est introuvable. */
    private org.bukkit.Location loadLoc(FileConfiguration config, String key, String arenaId) {
        String raw = config.getString(key);
        if (raw == null || raw.equals("null")) return null;
        org.bukkit.Location loc = LocationUtil.deserialize(raw);
        if (loc == null) {
            plugin.getLogger().warning("[ArenaManager] Arène '" + arenaId + "' : impossible de charger la location '"
                    + key + "' (monde introuvable ?) → valeur : " + raw);
        }
        return loc;
    }

    private ArenaAvailability loadAvailability(FileConfiguration config) {
        String raw = config.getString("availability");
        if (raw != null && !raw.isBlank()) {
            try {
                return ArenaAvailability.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("[ArenaManager] Mode d'accessibilite inconnu '" + raw
                    + "', retour au fallback legacy 'enabled'.");
            }
        }
        return config.getBoolean("enabled", true)
            ? ArenaAvailability.ACTIVE
            : ArenaAvailability.DISABLED;
    }

    public void shutdown() {
        for (Arena arena : arenas.values()) {
            if (arena.getState() == ArenaState.RUNNING || arena.getState() == ArenaState.BETWEEN_WAVES) {
                for (Player p : arena.getPlayers()) {
                    plugin.getPlayerManager().restorePlayer(p);
                    p.teleport(p.getWorld().getSpawnLocation());
                }
            }
            saveArena(arena);
        }
    }
}
