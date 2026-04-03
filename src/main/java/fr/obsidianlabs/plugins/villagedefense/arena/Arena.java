package fr.obsidianlabs.plugins.villagedefense.arena;

import fr.obsidianlabs.plugins.villagedefense.tower.TowerSlot;
import fr.obsidianlabs.plugins.villagedefense.tower.TowerType;
import fr.obsidianlabs.plugins.villagedefense.trap.TrapSlot;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.*;

public class Arena {

    private final String id;
    private String displayName;

    // Configuration géographique
    private Location lobbyLocation;       // spawn joueurs
    private Location villageCenter;
    private Location pos1;                // coin 1 des limites de l'arène
    private Location pos2;                // coin 2 des limites de l'arène
    private Location shopChestLocation;   // coffre double = source du shop
    /** Prix par slot de coffre (clé = index dans le coffre, valeur = pièces). */
    private final Map<Integer, Long> shopItemPrices   = new HashMap<>();
    /** Prix de base par option d'enchantement (clé = index dans EnchantGUI.OPTIONS). */
    private final Map<Integer, Long> enchantPrices    = new HashMap<>();
    /** Coût de placement personnalisé par type de tour (null = valeur config.yml). */
    private final Map<TowerType, Integer> towerCostOverrides  = new EnumMap<>(TowerType.class);
    /** Niveau joueur minimum requis par type de tour (null = pas de restriction). */
    private final Map<TowerType, Integer> towerLevelRequired  = new EnumMap<>(TowerType.class);
    /** Tours désactivées dans cette arène. */
    private final Set<TowerType> disabledTowers = EnumSet.noneOf(TowerType.class);
    private final List<Location> mobSpawnPoints     = new ArrayList<>();
    private final List<Location> villagerSpawnPoints = new ArrayList<>();
    private final List<TowerSlot> towerSlots = new ArrayList<>();
    private final List<TrapSlot>  trapSlots  = new ArrayList<>();

    // Récompenses de fin de partie
    private boolean rewardVaultEnabled = false;
    private double  rewardVaultAmount  = 0.0;
    private final List<String> rewardCommands = new ArrayList<>();

    // Chemins de waypoints
    private final List<ArenaPath> paths = new ArrayList<>();
    /** key = LocationUtil.serialize(mobSpawnPoint), value = pathId */
    private final Map<String, String> spawnerPathAssignments = new LinkedHashMap<>();

    // Configuration de partie
    private int maxPlayers = 6;
    private int minPlayers = 1;
    private ArenaAvailability availability = ArenaAvailability.ACTIVE;
    private ArenaBoundaryMode boundaryMode = ArenaBoundaryMode.PUSH_BACK;
    /** Limite de vague (0 = désactivé — vagues infinies). */
    private int waveLimit = 0;

    /** Nombre maximal de tentatives par fenêtre (0 = désactivé). */
    private int attemptLimit = 0;
    /** Durée de la fenêtre de tentatives en heures. */
    private int attemptWindowHours = 12;

    // État de la partie
    private ArenaState state = ArenaState.WAITING;
    private final Set<Player> players    = new LinkedHashSet<>();
    private final Set<Player> spectators = new LinkedHashSet<>();
    private int currentWave = 0;
    private int villageLives;
    private final Set<UUID> activeMobs = new HashSet<>();
    private int waveTotalMobCount = 0;
    private int waveSpawnedMobCount = 0;
    private int waveResolvedMobCount = 0;

    // Villageois (cœur du village à défendre)
    private final Set<UUID> villagerUUIDs = new HashSet<>();

    // Gardiens du village (golems, atout VILLAGE_GUARD)
    private final Set<UUID> guardUUIDs = new HashSet<>();

    // Spawn séparé pour la partie (téléport en début de jeu et respawn)
    private Location gameSpawnLocation;

    // Villageois de service (shop et enchanteur) — ne sont PAS des habitants à défendre
    private Location shopVillagerLocation;
    private Location enchantVillagerLocation;

    // Panneau d'arène (affiche infos + rejoint l'arène au clic)
    private Location signLocation;
    private Location leaderboardLocation;      // classement global
    private Location arenaLeaderboardLocation; // classement par arène

    // Icône personnalisée dans le GUI d'administration (null = icône par défaut)
    private org.bukkit.Material customIcon = null;

    // Achat de villageois (atout BUY_VILLAGER) — global à l'arène
    private int villagersExtraPurchased = 0;
    private int lastVillagerPurchaseWave = -1;  // -1 = jamais acheté
    private int lastGuardPurchaseWave    = -1;  // -1 = jamais acheté

    // Compteurs de session
    private int countdownSeconds;
    private int betweenWaveSeconds;

    public Arena(String id) {
        this.id = id;
        this.displayName = id;
    }

    // --- Identité ---

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String name) { this.displayName = name; }

    // --- Géographie ---

    public Location getLobbyLocation() { return lobbyLocation; }
    public void setLobbyLocation(Location loc) { this.lobbyLocation = loc; }

    public Location getVillageCenter() { return villageCenter; }
    public void setVillageCenter(Location loc) { this.villageCenter = loc; }

    public Location getPos1() { return pos1; }
    public void setPos1(Location loc) { this.pos1 = loc; }
    public Location getPos2() { return pos2; }
    public void setPos2(Location loc) { this.pos2 = loc; }
    public boolean hasBounds() { return pos1 != null && pos2 != null; }

    public Location getShopChestLocation() { return shopChestLocation; }
    public void setShopChestLocation(Location loc) { this.shopChestLocation = loc; }

    /** Retourne le prix d'un slot de coffre, ou {@code defaultPrice} si non défini. */
    public long getShopItemPrice(int slotIndex, long defaultPrice) {
        return shopItemPrices.getOrDefault(slotIndex, defaultPrice);
    }
    public void setShopItemPrice(int slotIndex, long price) { shopItemPrices.put(slotIndex, price); }
    public Map<Integer, Long> getShopItemPrices() { return shopItemPrices; }

    /** Retourne le prix de base d'une option d'enchantement, ou {@code defaultPrice} si non défini. */
    public long getEnchantPrice(int optionIndex, long defaultPrice) {
        return enchantPrices.getOrDefault(optionIndex, defaultPrice);
    }
    public void setEnchantPrice(int optionIndex, long price) { enchantPrices.put(optionIndex, price); }
    public Map<Integer, Long> getEnchantPrices() { return enchantPrices; }

    // ── Config tours ──────────────────────────────────────────────────────────

    /** Coût de placement d'une tour, ou -1 si non surchargé (utiliser la valeur config). */
    public int getTowerCostOverride(TowerType type) {
        return towerCostOverrides.getOrDefault(type, -1);
    }
    public void setTowerCostOverride(TowerType type, int cost) { towerCostOverrides.put(type, cost); }
    public void removeTowerCostOverride(TowerType type)        { towerCostOverrides.remove(type); }
    public Map<TowerType, Integer> getTowerCostOverrides()     { return towerCostOverrides; }

    /** Niveau joueur minimum pour placer une tour, ou 1 si non surchargé. */
    public int getTowerLevelRequired(TowerType type) {
        return towerLevelRequired.getOrDefault(type, 1);
    }
    public void setTowerLevelRequired(TowerType type, int level) { towerLevelRequired.put(type, level); }
    public void removeTowerLevelRequired(TowerType type)         { towerLevelRequired.remove(type); }
    public Map<TowerType, Integer> getTowerLevelRequiredMap()    { return towerLevelRequired; }

    public boolean isTowerDisabled(TowerType type) { return disabledTowers.contains(type); }
    public void setTowerDisabled(TowerType type, boolean disabled) {
        if (disabled) disabledTowers.add(type); else disabledTowers.remove(type);
    }
    public Set<TowerType> getDisabledTowers() { return disabledTowers; }

    public List<Location> getMobSpawnPoints() { return Collections.unmodifiableList(mobSpawnPoints); }
    public void addMobSpawnPoint(Location loc) { mobSpawnPoints.add(loc.clone()); }
    public void removeMobSpawnPoint(int index) { mobSpawnPoints.remove(index); }
    public boolean removeMobSpawnPoint(Location loc) { return mobSpawnPoints.remove(loc); }
    public void clearMobSpawnPoints() { mobSpawnPoints.clear(); }

    // --- Spawns de villageois ---

    public List<Location> getVillagerSpawnPoints() { return Collections.unmodifiableList(villagerSpawnPoints); }
    public void addVillagerSpawnPoint(Location loc) { villagerSpawnPoints.add(loc.clone()); }
    public boolean removeVillagerSpawnPoint(Location loc) { return villagerSpawnPoints.remove(loc); }
    public void clearVillagerSpawnPoints() { villagerSpawnPoints.clear(); }

    /** Retourne le spawn villageois le plus proche dans un rayon maxDistSq, ou null. */
    public Location getNearestVillagerSpawn(Location loc, double maxDistSq) {
        Location nearest = null;
        double best = maxDistSq;
        for (Location spawn : villagerSpawnPoints) {
            if (spawn.getWorld() == null || !spawn.getWorld().equals(loc.getWorld())) continue;
            double d = spawn.distanceSquared(loc);
            if (d < best) { best = d; nearest = spawn; }
        }
        return nearest;
    }

    /** Trouve le spawn mob le plus proche d'une position (dans un rayon maxDistSq). */
    public Location getNearestMobSpawn(Location loc, double maxDistSq) {
        Location nearest = null;
        double best = maxDistSq;
        for (Location spawn : mobSpawnPoints) {
            if (spawn.getWorld() == null || !spawn.getWorld().equals(loc.getWorld())) continue;
            double d = spawn.distanceSquared(loc);
            if (d < best) { best = d; nearest = spawn; }
        }
        return nearest;
    }

    public List<TowerSlot> getTowerSlots() { return Collections.unmodifiableList(towerSlots); }
    public void addTowerSlot(TowerSlot slot) { towerSlots.add(slot); }
    public void removeTowerSlot(TowerSlot slot) { towerSlots.remove(slot); }
    public void clearTowerSlots() { towerSlots.clear(); }

    public List<TrapSlot> getTrapSlots() { return Collections.unmodifiableList(trapSlots); }
    public void addTrapSlot(TrapSlot slot) { trapSlots.add(slot); }
    public void removeTrapSlot(TrapSlot slot) { trapSlots.remove(slot); }
    public void clearTrapSlots() { trapSlots.clear(); }

    // --- Chemins de waypoints ---

    public List<ArenaPath> getPaths() { return Collections.unmodifiableList(paths); }
    public void addPath(ArenaPath path) { paths.add(path); }

    public boolean removePath(String pathId) {
        boolean removed = paths.removeIf(p -> p.getId().equals(pathId));
        if (removed) spawnerPathAssignments.values().removeIf(v -> v.equals(pathId));
        return removed;
    }

    public ArenaPath getPath(String pathId) {
        return paths.stream().filter(p -> p.getId().equals(pathId)).findFirst().orElse(null);
    }

    public boolean hasPaths() { return !paths.isEmpty(); }

    public Map<String, String> getSpawnerPathAssignments() { return spawnerPathAssignments; }
    public void setSpawnerPathAssignment(String serializedLoc, String pathId) { spawnerPathAssignments.put(serializedLoc, pathId); }
    public void removeSpawnerPathAssignment(String serializedLoc) { spawnerPathAssignments.remove(serializedLoc); }
    public String getSpawnerPathId(String serializedLoc) { return spawnerPathAssignments.get(serializedLoc); }

    /** Trouve l'emplacement de piège le plus proche d'une position (dans un rayon maxDistSq). */
    public TrapSlot getNearestTrapSlot(Location loc, double maxDistSq) {
        TrapSlot nearest = null;
        double best = maxDistSq;
        for (TrapSlot slot : trapSlots) {
            double d = slot.distanceSquaredFrom(loc);
            if (d < best) { best = d; nearest = slot; }
        }
        return nearest;
    }

    /** Trouve le slot le plus proche d'une position (dans un rayon de 3 blocs) */
    public TowerSlot getNearestSlot(Location loc, double maxDistSq) {
        TowerSlot nearest = null;
        double best = maxDistSq;
        for (TowerSlot slot : towerSlots) {
            double d = slot.distanceSquaredFrom(loc);
            if (d < best) {
                best = d;
                nearest = slot;
            }
        }
        return nearest;
    }

    // --- Config ---

    public int getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(int max) { this.maxPlayers = max; }
    public int getMinPlayers() { return minPlayers; }
    public void setMinPlayers(int min) { this.minPlayers = min; }
    public boolean isEnabled() { return availability != ArenaAvailability.DISABLED; }
    public void setEnabled(boolean enabled) {
        this.availability = enabled ? ArenaAvailability.ACTIVE : ArenaAvailability.DISABLED;
    }
    public ArenaAvailability getAvailability() { return availability; }
    public void setAvailability(ArenaAvailability availability) {
        this.availability = availability != null ? availability : ArenaAvailability.ACTIVE;
    }
    public boolean isTestMode() { return availability == ArenaAvailability.TEST; }
    public boolean isDisabled() { return availability == ArenaAvailability.DISABLED; }

    public ArenaBoundaryMode getBoundaryMode() { return boundaryMode; }
    public void setBoundaryMode(ArenaBoundaryMode mode) {
        this.boundaryMode = mode != null ? mode : ArenaBoundaryMode.PUSH_BACK;
    }

    public int getWaveLimit() { return waveLimit; }
    public void setWaveLimit(int limit) { this.waveLimit = Math.max(0, limit); }
    public boolean hasWaveLimit() { return waveLimit > 0; }

    public int getAttemptLimit() { return attemptLimit; }
    public void setAttemptLimit(int limit) { this.attemptLimit = Math.max(0, limit); }
    public boolean hasAttemptLimit() { return attemptLimit > 0; }

    public int getAttemptWindowHours() { return attemptWindowHours; }
    public void setAttemptWindowHours(int hours) { this.attemptWindowHours = Math.max(1, hours); }

    // --- Récompenses de fin de partie ---

    public boolean isRewardVaultEnabled() { return rewardVaultEnabled; }
    public void setRewardVaultEnabled(boolean enabled) { this.rewardVaultEnabled = enabled; }
    public double getRewardVaultAmount() { return rewardVaultAmount; }
    public void setRewardVaultAmount(double amount) { this.rewardVaultAmount = Math.max(0, amount); }
    public List<String> getRewardCommands() { return rewardCommands; }
    public void addRewardCommand(String command) { rewardCommands.add(command); }
    public void removeRewardCommand(int index) { if (index >= 0 && index < rewardCommands.size()) rewardCommands.remove(index); }
    public void clearRewardCommands() { rewardCommands.clear(); }
    public boolean hasRewards() { return rewardVaultEnabled || !rewardCommands.isEmpty(); }

    // --- État ---

    public ArenaState getState() { return state; }
    public void setState(ArenaState state) { this.state = state; }

    public boolean isJoinable() {
        return availability == ArenaAvailability.ACTIVE && isJoinWindowOpen();
    }

    public boolean isJoinWindowOpen() {
        return (state == ArenaState.WAITING || state == ArenaState.STARTING)
            && players.size() < maxPlayers;
    }

    public boolean isSpectatable() {
        return state == ArenaState.RUNNING || state == ArenaState.BETWEEN_WAVES;
    }

    public boolean isSetup() {
        return hasLobbySpawn()
            && hasGameSpawn()
            && hasVillageCenter()
            && hasBounds()
            && hasMobSpawns()
            && hasVillagerSpawns()
            && hasTowerSlots()
            && hasShopChest()
            && hasShopVillager()
            && hasEnchantVillager();
    }

    public boolean hasLobbySpawn() { return lobbyLocation != null; }
    public boolean hasGameSpawn() { return gameSpawnLocation != null; }
    public boolean hasVillageCenter() { return villageCenter != null; }
    public boolean hasMobSpawns() { return !mobSpawnPoints.isEmpty(); }
    public boolean hasVillagerSpawns() { return !villagerSpawnPoints.isEmpty(); }
    public boolean hasTowerSlots() { return !towerSlots.isEmpty(); }
    public boolean hasTrapSlots() { return !trapSlots.isEmpty(); }
    public boolean hasShopChest() { return shopChestLocation != null; }
    public boolean hasShopVillager() { return shopVillagerLocation != null; }
    public boolean hasEnchantVillager() { return enchantVillagerLocation != null; }
    public boolean hasCustomDisplayName() { return !displayName.equals(id); }
    public boolean hasJoinSign() { return signLocation != null; }
    public boolean hasLeaderboardDisplay() { return leaderboardLocation != null; }

    // --- Joueurs ---

    public Set<Player> getPlayers() { return Collections.unmodifiableSet(players); }
    public int getPlayerCount() { return players.size(); }

    public boolean addPlayer(Player player) {
        return players.add(player);
    }

    public boolean removePlayer(Player player) {
        return players.remove(player);
    }

    public boolean hasPlayer(Player player) {
        return players.contains(player);
    }

    // --- Spectateurs ---

    public Set<Player> getSpectators() { return Collections.unmodifiableSet(spectators); }
    public int getSpectatorCount() { return spectators.size(); }

    public boolean addSpectator(Player player) {
        return spectators.add(player);
    }

    public boolean removeSpectator(Player player) {
        return spectators.remove(player);
    }

    public boolean hasSpectator(Player player) {
        return spectators.contains(player);
    }

    /** Retourne tous les participants (joueurs + spectateurs). */
    public Set<Player> getAllParticipants() {
        Set<Player> all = new LinkedHashSet<>(players);
        all.addAll(spectators);
        return all;
    }

    // --- Vagues ---

    public int getCurrentWave() { return currentWave; }
    public void setCurrentWave(int wave) { this.currentWave = wave; }
    public void incrementWave() { this.currentWave++; }

    public int getVillageLives() { return villageLives; }
    public void setVillageLives(int lives) { this.villageLives = lives; }
    public boolean loseLife() {
        if (villageLives > 0) villageLives--;
        return villageLives <= 0;
    }

    // --- Villageois ---

    public Set<UUID> getVillagerUUIDs() { return villagerUUIDs; }
    public void addVillager(UUID uuid) { villagerUUIDs.add(uuid); }
    public void removeVillager(UUID uuid) { villagerUUIDs.remove(uuid); }
    public int getVillagerCount() { return villagerUUIDs.size(); }
    public void clearVillagers() { villagerUUIDs.clear(); }
    public boolean isVillager(UUID uuid) { return villagerUUIDs.contains(uuid); }

    // --- Gardiens ---

    public Set<UUID> getGuardUUIDs() { return guardUUIDs; }
    public void addGuard(UUID uuid) { guardUUIDs.add(uuid); }
    public void removeGuard(UUID uuid) { guardUUIDs.remove(uuid); }
    public void clearGuards() { guardUUIDs.clear(); }

    // --- Mobs actifs ---

    public Set<UUID> getActiveMobUUIDs() { return activeMobs; }
    public void addActiveMob(UUID uuid) { activeMobs.add(uuid); }
    public void removeActiveMob(UUID uuid) { activeMobs.remove(uuid); }
    public int getActiveMobCount() { return activeMobs.size(); }
    public void clearActiveMobs() { activeMobs.clear(); }

    public void startWaveProgress(int totalMobCount) {
        waveTotalMobCount = Math.max(0, totalMobCount);
        waveSpawnedMobCount = 0;
        waveResolvedMobCount = 0;
    }

    public void resetWaveProgress() {
        waveTotalMobCount = 0;
        waveSpawnedMobCount = 0;
        waveResolvedMobCount = 0;
    }

    public void markWaveMobSpawned(UUID uuid) {
        activeMobs.add(uuid);
        if (waveSpawnedMobCount < waveTotalMobCount) {
            waveSpawnedMobCount++;
        }
    }

    public boolean markWaveMobResolved(UUID uuid) {
        boolean removed = activeMobs.remove(uuid);
        if (removed && waveResolvedMobCount < waveTotalMobCount) {
            waveResolvedMobCount++;
        }
        return removed;
    }

    public void markWaveMobSkipped() {
        if (waveSpawnedMobCount < waveTotalMobCount) {
            waveSpawnedMobCount++;
        }
        if (waveResolvedMobCount < waveTotalMobCount) {
            waveResolvedMobCount++;
        }
    }

    public int getWaveTotalMobCount() { return waveTotalMobCount; }
    public int getWaveSpawnedMobCount() { return waveSpawnedMobCount; }
    public int getWaveResolvedMobCount() { return waveResolvedMobCount; }
    public int getWavePendingSpawnCount() { return Math.max(0, waveTotalMobCount - waveSpawnedMobCount); }
    public int getWaveRemainingMobCount() { return Math.max(0, waveTotalMobCount - waveResolvedMobCount); }
    public boolean isWaveSpawnComplete() { return getWavePendingSpawnCount() == 0; }
    public boolean isWaveResolved() { return getWaveRemainingMobCount() == 0; }

    // --- Compteurs ---

    public int getCountdownSeconds() { return countdownSeconds; }
    public void setCountdownSeconds(int s) { this.countdownSeconds = s; }
    public void decrementCountdown() { this.countdownSeconds--; }

    public int getBetweenWaveSeconds() { return betweenWaveSeconds; }
    public void setBetweenWaveSeconds(int s) { this.betweenWaveSeconds = s; }
    public void decrementBetweenWave() { this.betweenWaveSeconds--; }

    // --- Villageois supplémentaires (atout BUY_VILLAGER) ---

    public int getVillagersExtraPurchased() { return villagersExtraPurchased; }
    public void incrementVillagersExtraPurchased() { this.villagersExtraPurchased++; }
    public int getLastVillagerPurchaseWave() { return lastVillagerPurchaseWave; }
    public void setLastVillagerPurchaseWave(int wave) { this.lastVillagerPurchaseWave = wave; }

    public int getLastGuardPurchaseWave() { return lastGuardPurchaseWave; }
    public void setLastGuardPurchaseWave(int wave) { this.lastGuardPurchaseWave = wave; }

    // --- Spawn de partie ---

    /** Spawn utilisé au lancement de la partie et pour les respawns. Si null, utilise lobbyLocation. */
    public Location getGameSpawnLocation() { return gameSpawnLocation; }
    public void setGameSpawnLocation(Location loc) { this.gameSpawnLocation = loc; }

    /** Retourne le spawn effectif de partie (gameSpawnLocation sinon lobbyLocation). */
    public Location getEffectiveGameSpawn() {
        return gameSpawnLocation != null ? gameSpawnLocation : lobbyLocation;
    }

    // --- Villageois de service ---

    public Location getShopVillagerLocation()  { return shopVillagerLocation; }
    public void setShopVillagerLocation(Location loc)  { this.shopVillagerLocation = loc; }

    public Location getEnchantVillagerLocation()  { return enchantVillagerLocation; }
    public void setEnchantVillagerLocation(Location loc)  { this.enchantVillagerLocation = loc; }

    // --- Panneau d'arène ---

    public Location getSignLocation() { return signLocation; }
    public void setSignLocation(Location loc) { this.signLocation = loc; }

    public Location getLeaderboardLocation() { return leaderboardLocation; }
    public void setLeaderboardLocation(Location loc) { this.leaderboardLocation = loc; }

    public Location getArenaLeaderboardLocation() { return arenaLeaderboardLocation; }
    public void setArenaLeaderboardLocation(Location loc) { this.arenaLeaderboardLocation = loc; }
    public boolean hasArenaLeaderboardDisplay() { return arenaLeaderboardLocation != null; }

    public org.bukkit.Material getCustomIcon() { return customIcon; }
    public void setCustomIcon(org.bukkit.Material icon) { this.customIcon = icon; }

    /** Sélectionne un point de spawn aléatoire pour un mob (2 blocs au-dessus du sol du schematic). */
    public Location getRandomSpawnPoint() {
        if (mobSpawnPoints.isEmpty()) return null;
        return mobSpawnPoints.get(new Random().nextInt(mobSpawnPoints.size())).clone().add(0, 2, 0);
    }

    /** Reset de l'état de la partie (garde la config) */
    public void resetGame(int defaultLives) {
        currentWave = 0;
        villageLives = defaultLives;
        activeMobs.clear();
        resetWaveProgress();
        villagerUUIDs.clear();
        guardUUIDs.clear();
        villagersExtraPurchased = 0;
        lastVillagerPurchaseWave = -1;
        lastGuardPurchaseWave = -1;
        // Les joueurs ne sont pas effacés ici, ils restent dans l'arène
    }
}
