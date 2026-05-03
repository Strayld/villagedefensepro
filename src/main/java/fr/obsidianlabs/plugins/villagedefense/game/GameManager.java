package fr.obsidianlabs.plugins.villagedefense.game;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.api.VillageDefenseProgressType;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaState;
import fr.obsidianlabs.plugins.villagedefense.gameplay.model.ArenaGameplaySettings;
import fr.obsidianlabs.plugins.villagedefense.arena.RegionSnapshot;
import fr.obsidianlabs.plugins.villagedefense.compat.VersionCompat;
import fr.obsidianlabs.plugins.villagedefense.player.VDPlayer;
import fr.obsidianlabs.plugins.villagedefense.tower.Tower;
import fr.obsidianlabs.plugins.villagedefense.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.Tag;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Gère le cycle de vie des parties : countdown, vagues, inter-vague, fin.
 * Gère aussi : scoreboard lobby/partie, respawn joueur, portes cassables, reciblage zombies.
 */
public class GameManager {

    private final VillageDefensePlugin plugin;
    private final ScoreboardManager sbManager;

    // ─── Tâches périodiques ────────────────────────────────────────────────
    private final Map<String, BukkitTask> arenaTasks           = new HashMap<>();
    private final Map<String, BukkitTask> lobbyTasks           = new HashMap<>();
    private final Map<String, BukkitTask> scoreboardTasks      = new HashMap<>();
    private final Map<String, BukkitTask> villagerKeeperTasks  = new HashMap<>();
    private final Map<String, BukkitTask> retargetTasks        = new HashMap<>();
    private final Map<String, BukkitTask> doorTasks            = new HashMap<>();
    private final Map<String, BukkitTask> arrowCleanupTasks    = new HashMap<>();
    private final Map<String, BukkitTask> stuckMobTasks        = new HashMap<>();
    private final Map<String, BukkitTask> villagerLookTasks    = new HashMap<>();

    /** arenaId → timestamp (ms) quand le compteur de mobs est tombé à 1 */
    private final Map<String, Long> stuckMobSince = new HashMap<>();

    // ─── Boss bar ──────────────────────────────────────────────────────────
    private final Map<String, BossBar>  arenaBossBar    = new HashMap<>();
    private final Map<String, UUID>     arenaBossUUID   = new HashMap<>();
    private final Map<String, BukkitTask> bossBossBarTasks = new HashMap<>();

    // ─── Snapshots ────────────────────────────────────────────────────────
    private final Map<String, RegionSnapshot> arenaSnapshots = new HashMap<>();

    // ─── Respawn joueur ───────────────────────────────────────────────────
    private final Map<UUID, BukkitTask> pendingRespawns  = new HashMap<>();
    private final Map<UUID, Long>       respawnEndTimes  = new HashMap<>();

    // ─── Rejoin après déconnexion ─────────────────────────────────────────
    private static final long REJOIN_WINDOW_MS    = 5 * 60_000L;
    private static final long REJOIN_WINDOW_TICKS = 6_000L;
    private final Map<UUID, Long>   rejoinExpiries = new HashMap<>();
    private final Map<UUID, String> rejoinArenaIds = new HashMap<>();

    // ─── Join automatique depuis le Hub (BungeeCord) ──────────────────────
    /** Durée maximale de validité d'un pending hub join (5 minutes). */
    private static final long HUB_JOIN_TTL_MS = 5 * 60_000L;
    private final Map<UUID, String> pendingHubJoinArenas   = new HashMap<>();
    private final Map<UUID, Long>   pendingHubJoinExpiries = new HashMap<>();

    // ─── Portes cassables ─────────────────────────────────────────────────
    /** arenaId → { location de porte (demi-bas) → hits reçus } */
    private final Map<String, Map<Location, Integer>> arenaDoorHits = new HashMap<>();
    /** uuid zombie → timestamp (ms) du dernier coup sur une porte */
    private final Map<UUID, Long> zombieDoorCooldowns = new HashMap<>();

    public static final String VILLAGER_META        = "vd_villager";
    public static final String SHOP_VILLAGER_META   = "vd_shop_villager";
    public static final String ENCHANT_VILLAGER_META = "vd_enchant_villager";

    /** arenaId → UUID du villageois-marchand (shop) */
    private final Map<String, UUID> arenaShopVillagers    = new HashMap<>();
    /** arenaId → UUID du villageois-enchanteur */
    private final Map<String, UUID> arenaEnchantVillagers = new HashMap<>();

    private static final Villager.Profession[] PROFESSIONS = {
        Villager.Profession.FARMER,    Villager.Profession.SHEPHERD,
        Villager.Profession.FISHERMAN, Villager.Profession.TOOLSMITH,
        Villager.Profession.WEAPONSMITH, Villager.Profession.LEATHERWORKER,
        Villager.Profession.MASON,     Villager.Profession.LIBRARIAN,
        Villager.Profession.CLERIC,    Villager.Profession.BUTCHER
    };

    public GameManager(VillageDefensePlugin plugin) {
        this.plugin    = plugin;
        this.sbManager = new ScoreboardManager(plugin);
    }

    /** Appelé à chaque /vda reload pour rafraîchir les valeurs de config mises en cache. */
    public void reloadConfig() {
        sbManager.reloadConfig();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // LOBBY
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Appelé par ArenaManager.joinArena APRÈS arena.addPlayer().
     * Assigne le scoreboard lobby et met à jour tout le monde.
     */
    public void onPlayerJoinArena(Player player, Arena arena) {
        sbManager.assignLobby(player, arena);
        // Rafraîchir le scoreboard de tous les joueurs (le count vient de changer)
        for (Player p : arena.getPlayers()) {
            sbManager.updateLobby(p, arena);
        }
        startLobbyTicker(arena);
    }

    /**
     * Appelé par ArenaManager.leaveArena APRÈS arena.removePlayer().
     * Retire le scoreboard et met à jour les joueurs restants.
     */
    public void onPlayerLeaveArena(Player player, Arena arena) {
        sbManager.remove(player);
        cancelRespawn(player);
        clearObserverMode(player);
        // Stocker un token de rejoin si la partie est en cours
        if (arena.getState() == ArenaState.RUNNING) {
            UUID uid = player.getUniqueId();
            long expiry = System.currentTimeMillis() + REJOIN_WINDOW_MS;
            rejoinExpiries.put(uid, expiry);
            rejoinArenaIds.put(uid, arena.getId());
            // Persister en DB si la sync réseau est active (pour le plugin Hub)
            if (isNetworkSyncActive()) {
                String serverId = plugin.getNetworkSyncManager().getServerId();
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                    plugin.getDatabase().saveRejoinToken(
                        uid, player.getName(), serverId,
                        arena.getId(), arena.getDisplayName(), expiry));
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Long stored = rejoinExpiries.get(uid);
                if (stored != null && stored == expiry) {
                    rejoinExpiries.remove(uid);
                    rejoinArenaIds.remove(uid);
                    // Nettoyer en DB aussi si pas déjà consommé
                    if (isNetworkSyncActive()) {
                        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                            plugin.getDatabase().deleteRejoinToken(uid));
                    }
                }
            }, REJOIN_WINDOW_TICKS);
        }
        // Auto-stop : plus aucun joueur actif pendant une partie
        if ((arena.getState() == ArenaState.RUNNING || arena.getState() == ArenaState.BETWEEN_WAVES)
                && arena.getPlayerCount() == 0) {
            broadcast(arena, plugin.getMessages().format("arena.abandoned"));
            endGame(arena, false);
            return;
        }
        // Mettre à jour les joueurs qui restent
        if (arena.getState() == ArenaState.WAITING || arena.getState() == ArenaState.STARTING) {
            for (Player p : arena.getPlayers()) {
                sbManager.updateLobby(p, arena);
            }
        }
        if (arena.getPlayerCount() == 0) {
            cancelLobbyTicker(arena);
        }
    }

    public boolean hasValidRejoinToken(Player player) {
        Long expiry = rejoinExpiries.get(player.getUniqueId());
        return expiry != null && System.currentTimeMillis() < expiry;
    }

    public String getRejoinArenaId(Player player) {
        return rejoinArenaIds.get(player.getUniqueId());
    }

    public long getRejoinRemainingSeconds(Player player) {
        Long expiry = rejoinExpiries.get(player.getUniqueId());
        if (expiry == null) return 0;
        return Math.max(0, (expiry - System.currentTimeMillis()) / 1000);
    }

    public void clearRejoinToken(Player player) {
        UUID uid = player.getUniqueId();
        rejoinExpiries.remove(uid);
        rejoinArenaIds.remove(uid);
        if (isNetworkSyncActive()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                plugin.getDatabase().deleteRejoinToken(uid));
        }
    }

    // ─── Hub join (BungeeCord) ────────────────────────────────────────────

    /** Enregistre un join automatique demandé par le Hub pour que le joueur rejoigne
     *  {@code arenaId} dès qu'il se connecte à ce serveur. */
    public void queueHubJoin(UUID playerUuid, String arenaId) {
        pendingHubJoinArenas.put(playerUuid, arenaId);
        pendingHubJoinExpiries.put(playerUuid, System.currentTimeMillis() + HUB_JOIN_TTL_MS);
    }

    /** Consomme et retourne l'arenaId en attente pour ce joueur, ou {@code null} si absent/expiré. */
    public String consumeHubJoin(UUID playerUuid) {
        Long expiry  = pendingHubJoinExpiries.remove(playerUuid);
        String arena = pendingHubJoinArenas.remove(playerUuid);
        if (expiry == null || System.currentTimeMillis() > expiry) return null;
        // Le joueur va rejoindre : le token rejoin DB n'est plus nécessaire
        if (isNetworkSyncActive()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                plugin.getDatabase().deleteRejoinToken(playerUuid));
        }
        return arena;
    }

    private boolean isNetworkSyncActive() {
        return plugin.getNetworkSyncManager() != null && plugin.getNetworkSyncManager().isEnabled();
    }

    /**
     * Remet un joueur en jeu après un rejoin : applique le kit, téléporte
     * au spawn de partie et assigne le scoreboard.
     * Doit être appelé APRÈS arena.addPlayer() et prepareForLobby().
     */
    public void onPlayerRejoinArena(Player player, Arena arena) {
        plugin.getPlayerManager().applyKit(player);
        Location spawn = arena.getEffectiveGameSpawn();
        if (spawn != null) player.teleport(spawn);
        player.setHealth(20.0);
        sbManager.assign(player, arena);
    }

    // ── Spectateurs ───────────────────────────────────────────────────────────

    public void onSpectatorJoinArena(Player player, Arena arena) {
        applyObserverMode(player, arena, true);
        sbManager.assignSpectator(player, arena);
    }

    public void onSpectatorLeaveArena(Player player, Arena arena) {
        sbManager.remove(player);
        clearObserverMode(player);
    }

    /** Retourne true si l'entité appartient au plugin (ne doit pas être supprimée). */
    private boolean isPluginEntity(Entity entity) {
        if (entity instanceof Player) return true;
        if (entity.hasMetadata(fr.obsidianlabs.plugins.villagedefense.wave.WaveManager.MOB_ARENA_META)) return true;
        if (entity.hasMetadata(VILLAGER_META)) return true;
        if (entity.hasMetadata(SHOP_VILLAGER_META)) return true;
        if (entity.hasMetadata(ENCHANT_VILLAGER_META)) return true;
        if (entity.hasMetadata(fr.obsidianlabs.plugins.villagedefense.perk.PerkManager.COMPANION_META)) return true;
        // Fallback PDC — le Bukkit metadata peut être perdu sur les entités MythicMobs
        // (wrapper CraftEntity différent entre spawn et getNearbyEntities).
        // La PDC est stockée en NBT côté NMS et reste toujours cohérente.
        if (entity instanceof LivingEntity le
                && le.getPersistentDataContainer().has(
                    plugin.getKey(fr.obsidianlabs.plugins.villagedefense.wave.WaveManager.MOB_ARENA_META),
                    org.bukkit.persistence.PersistentDataType.STRING)) return true;
        return false;
    }

    /**
     * Supprime toutes les entités Mob non-plugin dans les bornes de l'arène.
     * Utilisé au démarrage (nettoie les mobs naturels) et en cours de partie (mobs intrus).
     * @param onlyUntagged si true, supprime uniquement les mobs sans tag plugin (mode démarrage).
     *                     si false, supprime aussi les mobs taggés d'autres arènes.
     */
    private void cleanArenaEntities(Arena arena) {
        Location p1 = arena.getPos1(), p2 = arena.getPos2();
        org.bukkit.World world = p1.getWorld();
        if (world == null) return;

        double minX = Math.min(p1.getX(), p2.getX()), maxX = Math.max(p1.getX(), p2.getX());
        double minY = Math.min(p1.getY(), p2.getY()), maxY = Math.max(p1.getY(), p2.getY());
        double minZ = Math.min(p1.getZ(), p2.getZ()), maxZ = Math.max(p1.getZ(), p2.getZ());

        world.getNearbyEntities(new org.bukkit.util.BoundingBox(minX, minY, minZ, maxX, maxY, maxZ))
            .forEach(entity -> {
                if (isPluginEntity(entity)) return;
                if (!(entity instanceof Mob)) return; // garder les items/projectiles/bateaux…
                entity.remove();
            });
    }

    private void startLobbyTicker(Arena arena) {
        if (lobbyTasks.containsKey(arena.getId())) return;
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (arena.getState() != ArenaState.WAITING && arena.getState() != ArenaState.STARTING) {
                cancelLobbyTicker(arena);
                return;
            }
            for (Player p : arena.getPlayers()) {
                sbManager.updateLobby(p, arena);
            }
        }, 20L, 20L);
        lobbyTasks.put(arena.getId(), task);
    }

    private void cancelLobbyTicker(Arena arena) {
        BukkitTask t = lobbyTasks.remove(arena.getId());
        if (t != null) t.cancel();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // COUNTDOWN
    // ═══════════════════════════════════════════════════════════════════════

    public void startCountdown(Arena arena) {
        if (arena.getState() != ArenaState.WAITING) return;
        arena.setState(ArenaState.STARTING);
        int delay = plugin.getConfig().getInt("game.lobby-countdown", 30);
        arena.setCountdownSeconds(delay);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () ->
            tickCountdown(arena), 0L, 20L);
        registerTask(arena, task);
    }

    private void tickCountdown(Arena arena) {
        int remaining = arena.getCountdownSeconds();

        if (arena.getPlayerCount() < plugin.getConfig().getInt("game.min-players", 1)) {
            cancelTask(arena);
            arena.setState(ArenaState.WAITING);
            broadcast(arena, m("arena.countdown-cancelled"));
            return;
        }

        if (remaining <= 0) {
            cancelTask(arena);
            startGame(arena);
            return;
        }

        if (remaining <= 5 || remaining % 10 == 0) {
            broadcast(arena, plugin.getMessages().format("arena.countdown", remaining));
            for (Player p : arena.getPlayers()) {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, remaining <= 3 ? 2f : 1f);
            }
        }

        arena.decrementCountdown();
        // Lobby scoreboard ticker gère le rafraîchissement
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DÉMARRAGE DE PARTIE
    // ═══════════════════════════════════════════════════════════════════════

    private void startGame(Arena arena) {
        arena.setState(ArenaState.RUNNING);
        cancelLobbyTicker(arena);

        if (arena.hasBounds()) {
            cleanArenaEntities(arena);
            RegionSnapshot snapshot = RegionSnapshot.capture(arena.getPos1(), arena.getPos2());
            arenaSnapshots.put(arena.getId(), snapshot);
            plugin.getLogger().info("[GameManager] Snapshot capturé pour '" + arena.getId()
                + "' : " + snapshot.getBlockCount() + " blocs.");
        } else {
            plugin.getLogger().warning("[GameManager] Arène '" + arena.getId()
                + "' sans limites — la map NE sera PAS restaurée !");
        }

        int villagerCount = plugin.getConfig().getInt("village.villager-count", 10);
        arena.resetGame(villagerCount);

        ArenaGameplaySettings gameplaySettings = plugin.getArenaGameplayService().getSettings(arena.getId());
        if (gameplaySettings.getGeneralSettings().isEnabled()
                && gameplaySettings.getGeneralSettings().isUseAdvancedWaveSystem()) {
            arena.setSelectedDifficulty(gameplaySettings.getDifficultySettings().getDefaultDifficulty());
        }

        broadcast(arena, plugin.getMessages().format("arena.starting"));

        long startCoins = plugin.getConfig().getLong("game.starting-coins", 500);
        for (Player p : arena.getPlayers()) {
            plugin.getPlayerManager().applyKit(p);
            p.teleport(arena.getEffectiveGameSpawn());
            sbManager.assign(p, arena);
        }
        for (Player p : arena.getPlayers()) {
            VDPlayer vdp = plugin.getPlayerManager().getPlayer(p);
            if (vdp != null) {
                vdp.setCoins(0);
                vdp.addGame();
                if (plugin.getDatabase() != null) plugin.getDatabase().recordArenaGame(p.getUniqueId(), arena.getId());
                if (plugin.getAttemptManager() != null) plugin.getAttemptManager().incrementAttempt(p.getUniqueId(), arena.getId());
                plugin.fireProgress(p, arena, VillageDefenseProgressType.GAME_START, 1, vdp.getTotalGamesPlayed(), "");
                if (startCoins > 0) vdp.addCoins(startCoins);
                plugin.getPlayerManager().checkAchievements(p, vdp);
            }
        }
        if (startCoins > 0) {
            broadcast(arena, m("game.start-coins", startCoins));
        }

        spawnVillagers(arena);
        spawnShopVillager(arena);
        spawnEnchantVillager(arena);
        startVillagerLookTask(arena);
        plugin.getTowerManager().initSlotDisplays(arena);
        plugin.getTrapManager().initTrapDisplays(arena);
        startScoreboardTicker(arena);
        startRetargetTask(arena);
        startArrowCleanup(arena);
        startStuckMobChecker(arena);
        scanAndStartDoors(arena);

        int preWaveDelay = plugin.getConfig().getInt("game.pre-wave-delay", 30);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (arena.getState() == ArenaState.RUNNING) {
                plugin.getPerkManager().resetWaveSkips(arena);
                plugin.getWaveManager().startNextWave(arena);
            }
        }, preWaveDelay * 20L);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // VILLAGEOIS
    // ═══════════════════════════════════════════════════════════════════════

    public void spawnVillagers(Arena arena) {
        Location center = arena.getVillageCenter();
        if (center == null || center.getWorld() == null) return;

        int count = plugin.getConfig().getInt("village.villager-count", 10);
        List<Location> vSpawns = arena.getVillagerSpawnPoints();
        double spawnRadius = plugin.getConfig().getDouble("village.spawn-radius", 8.0);
        Random rng = new Random();

        for (int i = 0; i < count; i++) {
            Location spawnLoc;
            if (!vSpawns.isEmpty()) {
                // Répartir équitablement entre les spawns définis par l'admin
                spawnLoc = vSpawns.get(i % vSpawns.size()).clone().add(
                    rng.nextDouble() * 0.6 - 0.3, 0, rng.nextDouble() * 0.6 - 0.3);
            } else {
                spawnLoc = findSpawnLocation(center, spawnRadius, rng);
                if (spawnLoc == null)
                    spawnLoc = center.clone().add(rng.nextDouble() * 4 - 2, 1, rng.nextDouble() * 4 - 2);
            }
            spawnLoc = groundSnap(spawnLoc);

            final Villager.Profession profession = PROFESSIONS[i % PROFESSIONS.length];
            final int villagerLevel = 1 + (i % 5);

            Villager villager = spawnLoc.getWorld().spawn(spawnLoc, Villager.class, v -> {
                v.setProfession(profession);
                v.setVillagerLevel(villagerLevel);
                v.setCustomName(ChatUtil.color(m("game.villager-name")));
                v.setCustomNameVisible(true);
                v.setAI(true);
                v.setInvulnerable(false);
                v.setSilent(false);
                v.setPersistent(true);
                v.setMetadata(VILLAGER_META, new FixedMetadataValue(plugin, arena.getId()));
                v.getPersistentDataContainer().set(
                    plugin.getKey(VILLAGER_META),
                    org.bukkit.persistence.PersistentDataType.STRING,
                    arena.getId()
                );
            });

            arena.addVillager(villager.getUniqueId());
        }

        broadcast(arena, m("game.villager-count", count));
        startVillagerKeeper(arena);
    }

    /**
     * Invoque 5 golems de fer autour du centre du village (atout VILLAGE_GUARD).
     * Les golems reciblnt automatiquement les mobs ennemis toutes les 2 secondes.
     */
    public void spawnVillageGuards(Arena arena, Player buyer) {
        Location center = arena.getVillageCenter();
        if (center == null || center.getWorld() == null) return;

        for (int i = 0; i < 5; i++) {
            double angle = (2 * Math.PI / 5) * i;
            Location spawnLoc = center.clone().add(
                Math.cos(angle) * 4, 1, Math.sin(angle) * 4);

            IronGolem golem = center.getWorld().spawn(spawnLoc, IronGolem.class, g -> {
                g.setPlayerCreated(true);
                g.setCustomName(ChatUtil.color(m("game.guard-name")));
                g.setCustomNameVisible(true);
                g.setInvulnerable(false);
                g.setPersistent(false);
                g.setMetadata(fr.obsidianlabs.plugins.villagedefense.perk.PerkManager.COMPANION_META,
                    new FixedMetadataValue(plugin, buyer.getUniqueId().toString()));
            });

            arena.addGuard(golem.getUniqueId());

            // Reciblage périodique sur le mob le plus proche — s'auto-cancelle quand le golem meurt
            new org.bukkit.scheduler.BukkitRunnable() {
                @Override public void run() {
                    if (golem.isDead() || !golem.isValid()) { cancel(); return; }
                    LivingEntity nearest = null;
                    double best = Double.MAX_VALUE;
                    for (java.util.UUID uid : new java.util.ArrayList<>(arena.getActiveMobUUIDs())) {
                        Entity mob = Bukkit.getEntity(uid);
                        if (mob == null || !mob.isValid() || mob.isDead()) continue;
                        double d = mob.getLocation().distanceSquared(golem.getLocation());
                        if (d < best) { best = d; nearest = (LivingEntity) mob; }
                    }
                    if (nearest != null) golem.setTarget(nearest);
                }
            }.runTaskTimer(plugin, 20L, 40L);
        }

        broadcast(arena, m("game.guards-deployed", buyer.getName()));
    }

    /** Invoque un villageois supplémentaire au centre du village (atout BUY_VILLAGER). */
    public void spawnExtraVillager(Arena arena, Player buyer) {
        Location center = arena.getVillageCenter();
        if (center == null || center.getWorld() == null) {
            ChatUtil.send(buyer, m("game.villager-no-center"));
            return;
        }

        Random rng = new Random();
        Location spawnLoc = findSpawnLocation(center, 8.0, rng);
        if (spawnLoc == null) {
            // Fallback : légèrement au-dessus du centre pour éviter les blocs solides
            spawnLoc = center.clone().add(rng.nextDouble() * 4 - 2, 1, rng.nextDouble() * 4 - 2);
        }

        final Location finalSpawnLoc = spawnLoc;
        Villager villager = center.getWorld().spawn(finalSpawnLoc, Villager.class, v -> {
            v.setProfession(Villager.Profession.FARMER);
            v.setVillagerLevel(1);
            v.setCustomName(ChatUtil.color(plugin.getMessages().format("game.inhabitant-name")));
            v.setCustomNameVisible(true);
            v.setAI(true);
            v.setInvulnerable(false);
            v.setSilent(false);
            v.setPersistent(true);
            v.setMetadata(VILLAGER_META, new FixedMetadataValue(plugin, arena.getId()));
            v.getPersistentDataContainer().set(
                plugin.getKey(VILLAGER_META),
                org.bukkit.persistence.PersistentDataType.STRING,
                arena.getId()
            );
        });

        arena.addVillager(villager.getUniqueId());
        // Synchroniser le compteur de vies avec le nouveau villageois
        arena.setVillageLives(arena.getVillageLives() + 1);
        arena.incrementVillagersExtraPurchased();
        arena.setLastVillagerPurchaseWave(arena.getCurrentWave());
        updateScoreboard(arena);
        broadcast(arena, m("game.villager-summoned", buyer.getName()));
    }

    private void startVillagerKeeper(Arena arena) {
        int interval = plugin.getConfig().getInt("village.keeper-interval", 40);
        double maxRadiusSq = Math.pow(plugin.getConfig().getDouble("village.villager-radius", 12.0), 2);
        double returnRadius = plugin.getConfig().getDouble("village.spawn-radius", 8.0);
        Location center = arena.getVillageCenter();
        Random rng = new Random();

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (arena.getState() == ArenaState.ENDING || arena.getState() == ArenaState.WAITING) {
                cancelVillagerKeeper(arena);
                return;
            }
            for (UUID uid : new HashSet<>(arena.getVillagerUUIDs())) {
                Entity e = Bukkit.getEntity(uid);
                if (e == null || !e.isValid() || e.isDead()) continue;
                if (e.getLocation().distanceSquared(center) > maxRadiusSq) {
                    Location ret = findSpawnLocation(center, returnRadius, rng);
                    if (ret == null) ret = center.clone().add(0, 1, 0);
                    e.teleport(ret);
                    center.getWorld().playSound(ret, Sound.ENTITY_VILLAGER_AMBIENT, 0.5f, 1.0f);
                }
            }
        }, interval, interval);

        BukkitTask old = villagerKeeperTasks.put(arena.getId(), task);
        if (old != null) old.cancel();
    }

    private void cancelVillagerKeeper(Arena arena) {
        BukkitTask t = villagerKeeperTasks.remove(arena.getId());
        if (t != null) t.cancel();
    }

    public void onVillagerDeath(Arena arena, Villager villager) {
        // Ignorer si ce villageois n'est pas (ou plus) enregistré dans l'arène
        if (!arena.isVillager(villager.getUniqueId())) return;

        // Éclair purement décoratif
        if (villager.getLocation().getWorld() != null) {
            villager.getLocation().getWorld().strikeLightningEffect(villager.getLocation());
        }

        arena.removeVillager(villager.getUniqueId());
        int remaining = arena.getVillageLives() - 1; // vies après la perte imminente

        for (Player p : arena.getPlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_DEATH, 1f, 0.8f);
        }

        boolean gameOver = arena.loseLife();
        if (gameOver) {
            broadcast(arena, plugin.getMessages().format("village.all-dead"));
            endGame(arena, false);
        } else {
            // total = vies actuelles (après perte) — reflète les achats supplémentaires
            int total = arena.getVillageLives() + 1; // valeur avant la perte
            broadcast(arena, plugin.getMessages().format("village.villager-death", remaining, total));
        }
        updateScoreboard(arena);
    }

    public void removeVillagers(Arena arena) {
        cancelVillagerKeeper(arena);
        for (UUID uid : new HashSet<>(arena.getVillagerUUIDs())) {
            Entity e = Bukkit.getEntity(uid);
            if (e != null && e.isValid()) e.remove();
        }
        arena.clearVillagers();
    }

    public void removeGuards(Arena arena) {
        for (UUID uid : new HashSet<>(arena.getGuardUUIDs())) {
            Entity e = Bukkit.getEntity(uid);
            if (e != null && e.isValid()) e.remove();
        }
        arena.clearGuards();
    }

    // ─── Villageois de service (shop & enchanteur) ────────────────────────────

    /** Invoque le villageois-marchand (shop) à l'emplacement configuré. */
    private void spawnShopVillager(Arena arena) {
        Location loc = arena.getShopVillagerLocation();
        if (loc == null || loc.getWorld() == null) return;
        loc = groundSnap(loc);

        Villager v = loc.getWorld().spawn(loc, Villager.class, villager -> {
            villager.setProfession(Villager.Profession.CARTOGRAPHER);
            villager.setVillagerLevel(5);
            villager.setCustomName(ChatUtil.color(plugin.getMessages().format("game.merchant-name")));
            villager.setCustomNameVisible(true);
            villager.setAI(false);
            villager.setInvulnerable(true);
            villager.setSilent(true);
            villager.setPersistent(true);
            villager.setMetadata(SHOP_VILLAGER_META, new FixedMetadataValue(plugin, arena.getId()));
        });
        arenaShopVillagers.put(arena.getId(), v.getUniqueId());
    }

    /** Invoque le villageois-enchanteur à l'emplacement configuré. */
    private void spawnEnchantVillager(Arena arena) {
        Location loc = arena.getEnchantVillagerLocation();
        if (loc == null || loc.getWorld() == null) return;
        loc = groundSnap(loc);

        Villager v = loc.getWorld().spawn(loc, Villager.class, villager -> {
            villager.setProfession(Villager.Profession.LIBRARIAN);
            villager.setVillagerLevel(5);
            villager.setCustomName(ChatUtil.color(plugin.getMessages().format("game.enchanter-name")));
            villager.setCustomNameVisible(true);
            villager.setAI(false);
            villager.setInvulnerable(true);
            villager.setSilent(true);
            villager.setPersistent(true);
            villager.setMetadata(ENCHANT_VILLAGER_META, new FixedMetadataValue(plugin, arena.getId()));
        });
        arenaEnchantVillagers.put(arena.getId(), v.getUniqueId());
    }

    private void startVillagerLookTask(Arena arena) {
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            UUID shopUid    = arenaShopVillagers.get(arena.getId());
            UUID enchantUid = arenaEnchantVillagers.get(arena.getId());
            for (UUID uid : new java.util.ArrayList<>(java.util.Arrays.asList(shopUid, enchantUid))) {
                if (uid == null) continue;
                Entity e = Bukkit.getEntity(uid);
                if (!(e instanceof Villager villager) || !villager.isValid()) continue;
                Player nearest = null;
                double bestDist = Double.MAX_VALUE;
                for (Player p : arena.getPlayers()) {
                    if (!p.isOnline() || !p.getWorld().equals(villager.getWorld())) continue;
                    double d = p.getLocation().distanceSquared(villager.getLocation());
                    if (d < bestDist) { bestDist = d; nearest = p; }
                }
                if (nearest != null) {
                    Location vLoc = villager.getLocation();
                    Location pLoc = nearest.getEyeLocation();
                    double dx = pLoc.getX() - vLoc.getX();
                    double dy = pLoc.getY() - (vLoc.getY() + villager.getEyeHeight());
                    double dz = pLoc.getZ() - vLoc.getZ();
                    float yaw   = (float) Math.toDegrees(Math.atan2(-dx, dz));
                    float pitch = (float) Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz)));
                    villager.setRotation(yaw, pitch);
                }
            }
        }, 0L, 10L);
        BukkitTask old = villagerLookTasks.put(arena.getId(), task);
        if (old != null) old.cancel();
    }

    /** Supprime les villageois de service (shop & enchanteur) de l'arène. */
    private void removeServiceVillagers(Arena arena) {
        UUID shopUid = arenaShopVillagers.remove(arena.getId());
        if (shopUid != null) { Entity e = Bukkit.getEntity(shopUid); if (e != null) e.remove(); }

        UUID enchantUid = arenaEnchantVillagers.remove(arena.getId());
        if (enchantUid != null) { Entity e = Bukkit.getEntity(enchantUid); if (e != null) e.remove(); }
    }

    /**
     * Corrige la position Y pour que l'entité pose les pieds sur la surface du sol,
     * et non à l'intérieur d'un bloc.
     *
     * Cas typique : l'admin clique sur un bloc au sol → getClickedBlock().getLocation()
     * renvoie le Y du bloc (ex. 64), mais l'entité doit avoir les pieds à Y=65.
     * Si le bloc à la position est solide, on monte d'un bloc.
     */
    private Location groundSnap(Location loc) {
        Location l = loc.clone();
        // Aligner sur le centre du bloc (X/Z) pour éviter les chevauchements aux coins
        l.setX(l.getBlockX() + 0.5);
        l.setZ(l.getBlockZ() + 0.5);
        // Si les pieds sont dans un bloc solide, remonter
        if (l.getBlock().getType().isSolid()) {
            l.add(0, 1, 0);
        }
        return l;
    }

    private Location findSpawnLocation(Location center, double radius, Random rng) {
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = rng.nextDouble() * 2 * Math.PI;
            double dist  = radius * 0.3 + rng.nextDouble() * radius * 0.7;
            Location candidate = new Location(center.getWorld(),
                center.getX() + Math.cos(angle) * dist,
                center.getY() + 5,
                center.getZ() + Math.sin(angle) * dist);
            for (int dy = 0; dy < 10; dy++) {
                candidate.subtract(0, 1, 0);
                if (candidate.getBlock().getType().isSolid() &&
                    !candidate.clone().add(0, 1, 0).getBlock().getType().isSolid()) {
                    return candidate.clone().add(0, 1, 0);
                }
            }
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // INTER-VAGUE
    // ═══════════════════════════════════════════════════════════════════════

    public void startBetweenWaves(Arena arena) {
        arena.setState(ArenaState.BETWEEN_WAVES);
        arena.resetWaveProgress();

        // Durée : configurable, 45 s après une vague de boss, 15 s sinon
        int bossInterval = plugin.getConfig().getInt("waves.boss-wave-interval", 10);
        boolean wasBossWave = arena.getCurrentWave() % bossInterval == 0;
        int duration = wasBossWave
            ? plugin.getConfig().getInt("game.between-boss-waves-duration", 45)
            : plugin.getConfig().getInt("game.between-waves-duration", 15);
        arena.setBetweenWaveSeconds(duration);

        long waveCoins = plugin.getConfig().getLong("coins.wave-bonus", 25) * arena.getCurrentWave();
        long waveXp   = (long) arena.getCurrentWave() * 10;
        for (Player p : arena.getPlayers()) {
            VDPlayer vdp = plugin.getPlayerManager().getPlayer(p);
            if (vdp != null) {
                vdp.addCoins(waveCoins);
                vdp.addWaveCompleted();
                if (plugin.getDatabase() != null) plugin.getDatabase().recordArenaWave(p.getUniqueId(), arena.getId());
                plugin.fireProgress(p, arena, VillageDefenseProgressType.WAVE_COMPLETE, 1, vdp.getTotalWavesCompleted(), "");
                plugin.getPlayerManager().checkAchievements(p, vdp);
            }
            plugin.getPlayerManager().awardXp(p, waveXp);
        }
        broadcast(arena, plugin.getMessages().format("waves.completed", arena.getCurrentWave(), waveCoins));

        // Limite de vague : fin de partie victorieuse si atteinte
        if (arena.hasWaveLimit() && arena.getCurrentWave() >= arena.getWaveLimit()) {
            endGame(arena, true);
            return;
        }

        // Vague bonus rare (~8 %) — uniquement sur vague normale, minimum vague 3
        if (!wasBossWave && arena.getCurrentWave() >= 3
                && ThreadLocalRandom.current().nextInt(100) < 8) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (arena.getState() == ArenaState.BETWEEN_WAVES) triggerBonusWave(arena);
            }, 40L);
        }

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () ->
            tickBetweenWaves(arena), 0L, 20L);
        registerTask(arena, task);
    }

    private void tickBetweenWaves(Arena arena) {
        int remaining = arena.getBetweenWaveSeconds();
        if (remaining <= 0) {
            cancelTask(arena);
            plugin.getPerkManager().resetWaveSkips(arena);
            // Le décompte 5→1 a déjà été affiché en titre — spawn immédiat
            plugin.getWaveManager().startNextWave(arena, true);
            return;
        }

        // Titre compte-à-rebours pendant les 5 dernières secondes
        if (remaining <= 5 && remaining >= 1) {
            for (Player p : arena.getPlayers()) {
                ChatUtil.sendTitle(
                    p,
                    m("game.countdown-title", remaining),
                    m("game.countdown-subtitle", arena.getCurrentWave() + 1),
                    2, 18, 2
                );
            }
        }

        // Messages d'invasion — adaptés selon durée (boss = 45s, normal = 15s)
        int bossInterval = plugin.getConfig().getInt("waves.boss-wave-interval", 10);
        boolean wasBossWave = arena.getCurrentWave() % bossInterval == 0;
        String invasionMsg = wasBossWave
            ? switch (remaining) {
                case 30 -> m("game.invasion-boss-30");
                case 15 -> m("game.invasion-boss-15");
                case 10 -> m("game.invasion-boss-10");
                default  -> null;
              }
            : switch (remaining) {
                case 10 -> m("game.invasion-10");
                default  -> null;
              };

        if (invasionMsg != null) {
            broadcast(arena, invasionMsg);
            // Son doux, jamais agressif
            Sound snd = remaining <= 10 ? Sound.ENTITY_BLAZE_AMBIENT : Sound.ENTITY_ZOMBIE_AMBIENT;
            for (Player p : arena.getPlayers()) p.playSound(p.getLocation(), snd, 0.35f, 0.75f);
        }

        arena.decrementBetweenWave();
        updateScoreboard(arena);
    }

    // ─── Vague Bonus ──────────────────────────────────────────────────────────

    /**
     * Événement rare déclenché entre deux vagues normales.
     * Améliore gratuitement une tour aléatoire, ou enchante l'équipement d'un joueur.
     */
    private void triggerBonusWave(Arena arena) {
        boolean upgradeBonus = ThreadLocalRandom.current().nextBoolean();

        if (upgradeBonus) {
            List<Tower> upgradeable = plugin.getTowerManager().getTowersInArena(arena).stream()
                .filter(Tower::canUpgrade)
                .collect(Collectors.toList());

            if (upgradeable.isEmpty()) {
                giveCoinsBonus(arena);
                return;
            }
            Tower chosen = upgradeable.get(ThreadLocalRandom.current().nextInt(upgradeable.size()));
            plugin.getTowerManager().upgradeFree(arena, chosen);
            broadcastBonus(arena, m("game.bonus-upgrade", m(chosen.getType().getNameKey()), chosen.getLevel()));
        } else {
            List<Player> players = new ArrayList<>(arena.getPlayers());
            if (players.isEmpty()) return;
            boolean anyEnchanted = false;
            for (Player p : players) {
                if (applyRandomEnchant(p)) {
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
                    anyEnchanted = true;
                }
            }
            if (anyEnchanted) {
                broadcastBonus(arena, m("game.bonus-enchant"));
            } else {
                giveCoinsBonus(arena);
            }
        }
    }

    private void giveCoinsBonus(Arena arena) {
        int bonus = 100;
        for (Player p : arena.getPlayers()) {
            VDPlayer vdp = plugin.getPlayerManager().getPlayer(p);
            if (vdp != null) vdp.addCoins(bonus);
        }
        broadcastBonus(arena, m("game.bonus-coins", bonus));
    }

    private void broadcastBonus(Arena arena, String msg) {
        for (Player p : arena.getPlayers())
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.3f);
        broadcast(arena, msg);
    }

    private boolean applyRandomEnchant(Player player) {
        List<org.bukkit.inventory.ItemStack> candidates = new ArrayList<>();
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        for (var item : inv.getArmorContents()) {
            if (item != null && item.getType() != Material.AIR) candidates.add(item);
        }
        org.bukkit.inventory.ItemStack hand = inv.getItemInMainHand();
        if (hand.getType() != Material.AIR) candidates.add(hand);
        if (candidates.isEmpty()) return false;

        org.bukkit.inventory.ItemStack chosen =
            candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        org.bukkit.enchantments.Enchantment enchant = pickEnchantFor(chosen);
        if (enchant == null) return false;

        int current = chosen.getEnchantmentLevel(enchant);
        if (current >= enchant.getMaxLevel()) return false;
        chosen.addUnsafeEnchantment(enchant, current + 1);
        return true;
    }

    private org.bukkit.enchantments.Enchantment pickEnchantFor(org.bukkit.inventory.ItemStack item) {
        String n = item.getType().name();
        if (n.contains("SWORD") || n.contains("_AXE"))
            return VersionCompat.SHARPNESS;
        if (n.contains("BOW"))
            return VersionCompat.POWER;
        if (n.contains("HELMET") || n.contains("CHESTPLATE")
                || n.contains("LEGGINGS") || n.contains("BOOTS")) {
            org.bukkit.enchantments.Enchantment[] opts = {
                VersionCompat.PROTECTION,
                VersionCompat.FIRE_PROTECTION,
                VersionCompat.PROJECTILE_PROTECTION
            };
            return opts[ThreadLocalRandom.current().nextInt(opts.length)];
        }
        return null;
    }

    public void onWaveCompleted(Arena arena) {
        if (arena.getState() != ArenaState.RUNNING) return;
        cancelTask(arena);
        startBetweenWaves(arena);
    }

    /**
     * Élimine tous les mobs de la vague en cours et passe directement à l'inter-vague.
     * Déclenché par l'atout Saut de vague.
     */
    public void skipWave(Arena arena, Player initiator) {
        if (arena.getState() != ArenaState.RUNNING) return;
        plugin.getWaveManager().killAllMobs(arena);
        broadcast(arena, m("game.skip-wave", initiator.getName()));
        onWaveCompleted(arena);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MORT / RESPAWN JOUEUR
    // ═══════════════════════════════════════════════════════════════════════

    public void scheduleRespawn(Player player, Arena arena) {
        cancelRespawn(player);

        int respawnSeconds = plugin.getKitAbilityManager() != null
            ? plugin.getKitAbilityManager().getRespawnDelaySeconds(arena, player)
            : 30;

        long endTime = System.currentTimeMillis() + (respawnSeconds * 1000L);
        respawnEndTimes.put(player.getUniqueId(), endTime);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !isRespawning(player)) {
                return;
            }
            if (player.isDead()) {
                player.spigot().respawn();
            }
        }, 1L);

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingRespawns.remove(player.getUniqueId());
            respawnEndTimes.remove(player.getUniqueId());
            if (!player.isOnline()) return;
            Arena current = plugin.getArenaManager().getArenaOfPlayer(player);
            if (current == null) return;
            if (player.isDead()) {
                player.spigot().respawn();
                Bukkit.getScheduler().runTaskLater(plugin, () -> completeRespawn(player, current), 1L);
                return;
            }
            completeRespawn(player, current);
        }, respawnSeconds * 20L);

        BukkitTask old = pendingRespawns.put(player.getUniqueId(), task);
        if (old != null) old.cancel();
    }

    public void cancelRespawn(Player player) {
        respawnEndTimes.remove(player.getUniqueId());
        BukkitTask t = pendingRespawns.remove(player.getUniqueId());
        if (t != null) t.cancel();
    }

    public boolean isRespawning(Player player) {
        return pendingRespawns.containsKey(player.getUniqueId());
    }

    public boolean isArenaObserver(Player player) {
        return plugin.getArenaManager().getArenaOfSpectator(player) != null || isRespawning(player);
    }

    public void enterRespawnObserverMode(Player player, Arena arena) {
        if (player == null || arena == null || !player.isOnline() || !isRespawning(player)) {
            return;
        }
        applyObserverMode(player, arena, true);
        int remaining = getRespawnRemainingSeconds(player);
        ChatUtil.send(player, m("game.death-respawn", remaining));
        sbManager.update(player, arena, remaining);
    }

    private void cancelAllRespawns(Arena arena) {
        for (Player p : arena.getPlayers()) cancelRespawn(p);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // VIES VILLAGE
    // ═══════════════════════════════════════════════════════════════════════

    public void onVillageHit(Arena arena) {
        boolean gameOver = arena.loseLife();
        int lives = arena.getVillageLives();
        if (gameOver) {
            endGame(arena, false);
        } else {
            broadcast(arena, plugin.getMessages().format("arena.lives-lost", lives,
                plugin.getConfig().getInt("game.village-lives", 10)));
            for (Player p : arena.getPlayers()) {
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_HURT, 1f, 0.8f);
            }
        }
        updateScoreboard(arena);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FIN DE PARTIE
    // ═══════════════════════════════════════════════════════════════════════

    public void endGame(Arena arena, boolean victory) {
        if (arena.getState() == ArenaState.ENDING) return;
        arena.setState(ArenaState.ENDING);
        cancelTask(arena);
        cancelScoreboardTicker(arena);
        cancelRetargetTask(arena);
        cancelDoorTask(arena);

        cancelArrowCleanup(arena);
        cancelStuckMobChecker(arena);
        removeBossBar(arena);
        plugin.getWaveManager().killAllMobs(arena);
        removeVillagers(arena);
        removeServiceVillagers(arena);
        removeGuards(arena);
        plugin.getTowerManager().clearArena(arena);
        plugin.getTrapManager().clearTraps(arena);
        plugin.getWaypointGuideManager().clearArena(arena);
        plugin.getPerkManager().clearArenaSession(arena);

        if (victory) {
            if (arena.hasWaveLimit() && arena.getCurrentWave() >= arena.getWaveLimit()) {
                broadcast(arena, plugin.getMessages().format("arena.wave-limit-victory", arena.getWaveLimit()));
            } else {
                broadcast(arena, plugin.getMessages().format("arena.victory"));
            }
            for (Player p : arena.getPlayers()) p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        } else {
            broadcast(arena, plugin.getMessages().format("arena.game-over", arena.getCurrentWave()));
            for (Player p : arena.getPlayers()) p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.5f, 0.8f);
        }

        List<Player> finishingPlayers = new ArrayList<>(arena.getPlayers());
        List<String> rewardCmds = victory ? List.copyOf(arena.getRewardCommands()) : List.of();
        boolean vaultReward = victory && arena.isRewardVaultEnabled() && arena.getRewardVaultAmount() > 0;
        if (vaultReward) {
            for (Player p : finishingPlayers) {
                plugin.getHookManager().depositVaultMoney(p, arena.getRewardVaultAmount());
            }
        }

        for (Player p : finishingPlayers) {
            VDPlayer vdp = plugin.getPlayerManager().getPlayer(p);
            if (vdp != null) {
                plugin.fireProgress(p, arena, VillageDefenseProgressType.GAME_COMPLETE, 1, vdp.getTotalGamesPlayed(), victory ? "victory" : "defeat");
                plugin.fireProgress(
                    p,
                    arena,
                    victory ? VillageDefenseProgressType.GAME_WIN : VillageDefenseProgressType.GAME_LOSS,
                    1,
                    vdp.getTotalGamesPlayed(),
                    ""
                );
            }
            plugin.getPlayerManager().savePlayer(p);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            arena.setState(ArenaState.RESETTING);
            broadcast(arena, plugin.getMessages().format("arena.resetting"));

            cancelAllRespawns(arena);
            arenaDoorHits.remove(arena.getId());

            for (Player p : new ArrayList<>(arena.getPlayers())) {
                sbManager.remove(p);
                plugin.getArenaManager().leaveArena(p);
            }
            for (Player p : new ArrayList<>(arena.getSpectators())) {
                plugin.getArenaManager().leaveArena(p);
            }

            if (!rewardCmds.isEmpty()) {
                for (Player p : finishingPlayers) {
                    if (!p.isOnline()) continue;
                    for (String cmd : rewardCmds) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", p.getName()));
                    }
                }
            }

            RegionSnapshot snapshot = arenaSnapshots.remove(arena.getId());
            if (snapshot != null) {
                plugin.getLogger().info("[GameManager] Restauration de la map '" + arena.getId()
                    + "' (" + snapshot.getBlockCount() + " blocs)...");
                snapshot.restore(plugin, () -> {
                    plugin.getLogger().info("[GameManager] Map '" + arena.getId() + "' restaurée.");
                    arena.setState(ArenaState.WAITING);
                });
            } else {
                plugin.getLogger().warning("[GameManager] Aucun snapshot pour '" + arena.getId() + "' — map non restaurée.");
                arena.setState(ArenaState.WAITING);
            }
        }, 100L);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // RECIBLAGE DES ZOMBIES
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Tâche périodique (toutes les 40 ticks / 2 s) qui force les mobs sans cible valide
     * à cibler un villageois (85 %) ou un joueur (15 %).
     * Compense le FOLLOW_RANGE limité et les blocages de pathfinding (portes, terrain).
     */
    private void startRetargetTask(Arena arena) {
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (arena.getState() != ArenaState.RUNNING && arena.getState() != ArenaState.BETWEEN_WAVES) return;
            cleanupForeignMobsInsideArena(arena);
            for (UUID uid : new ArrayList<>(arena.getActiveMobUUIDs())) {
                Entity e = Bukkit.getEntity(uid);
                if (!(e instanceof Creature creature) || !e.isValid() || e.isDead()) continue;

                if (plugin.getKitAbilityManager() != null) {
                    Player tauntTarget = plugin.getKitAbilityManager().getTauntTarget(arena, creature);
                    if (tauntTarget != null) {
                        creature.setTarget(tauntTarget);
                        continue;
                    }
                }

                if (tryHandleVillageHit(arena, creature)) {
                    continue;
                }

                LivingEntity target = creature.getTarget();
                if (target != null && target.isValid() && !target.isDead()) continue;

                boolean toVillager = ThreadLocalRandom.current().nextInt(100) < 85;
                if (toVillager) {
                    Villager v = findNearestVillager(arena, creature.getLocation());
                    if (v != null) { creature.setTarget(v); continue; }
                }
                Player p = findNearestPlayer(arena, creature.getLocation());
                if (p != null) creature.setTarget(p);
            }
        }, 40L, 40L);

        BukkitTask old = retargetTasks.put(arena.getId(), task);
        if (old != null) old.cancel();
    }

    private void cancelRetargetTask(Arena arena) {
        BukkitTask t = retargetTasks.remove(arena.getId());
        if (t != null) t.cancel();
    }

    private boolean tryHandleVillageHit(Arena arena, Creature creature) {
        if (arena.getVillagerCount() > 0 || arena.getVillageCenter() == null) {
            return false;
        }
        if (creature.getLocation().distanceSquared(arena.getVillageCenter()) > 25.0) {
            return false;
        }
        UUID uuid = creature.getUniqueId();
        creature.remove();
        arena.markWaveMobResolved(uuid);
        onVillageHit(arena);
        if (arena.getState() == ArenaState.RUNNING && arena.isWaveResolved()) {
            onWaveCompleted(arena);
        }
        return true;
    }

    private void cleanupForeignMobsInsideArena(Arena arena) {
        if (!arena.hasBounds()) {
            return;
        }
        Location p1 = arena.getPos1();
        Location p2 = arena.getPos2();
        if (p1 == null || p2 == null || p1.getWorld() == null || !p1.getWorld().equals(p2.getWorld())) {
            return;
        }

        double minX = Math.min(p1.getX(), p2.getX());
        double minY = Math.min(p1.getY(), p2.getY());
        double minZ = Math.min(p1.getZ(), p2.getZ());
        double maxX = Math.max(p1.getX(), p2.getX());
        double maxY = Math.max(p1.getY(), p2.getY());
        double maxZ = Math.max(p1.getZ(), p2.getZ());

        p1.getWorld().getNearbyEntities(new org.bukkit.util.BoundingBox(minX, minY, minZ, maxX, maxY, maxZ))
            .forEach(entity -> {
                if (!(entity instanceof Mob)) return;
                if (isPluginEntity(entity)) return;
                entity.remove();
            });
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PORTES CASSABLES
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Scanne les bornes de l'arène pour trouver toutes les portes (demi-bas),
     * puis démarre une tâche qui détecte les zombies proches et applique des
     * « hits » de porte. La porte cède après 3 coups (1 coup/2 s par zombie).
     * Les blocs sont restaurés automatiquement par le RegionSnapshot en fin de partie.
     */
    private void scanAndStartDoors(Arena arena) {
        if (!arena.hasBounds()) return;

        Map<Location, Integer> doorHits = scanDoors(arena);
        if (doorHits.isEmpty()) return;

        arenaDoorHits.put(arena.getId(), doorHits);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (arena.getState() == ArenaState.ENDING || arena.getState() == ArenaState.WAITING) {
                cancelDoorTask(arena);
                return;
            }
            tickDoors(arena);
            tickDoorVillagerAttraction(arena);
        }, 10L, 10L);

        BukkitTask old = doorTasks.put(arena.getId(), task);
        if (old != null) old.cancel();
    }

    private Map<Location, Integer> scanDoors(Arena arena) {
        Map<Location, Integer> doors = new HashMap<>();
        Location p1 = arena.getPos1(), p2 = arena.getPos2();
        if (p1.getWorld() == null) return doors;

        int minX = Math.min(p1.getBlockX(), p2.getBlockX());
        int maxX = Math.max(p1.getBlockX(), p2.getBlockX());
        int minY = Math.min(p1.getBlockY(), p2.getBlockY());
        int maxY = Math.max(p1.getBlockY(), p2.getBlockY());
        int minZ = Math.min(p1.getBlockZ(), p2.getBlockZ());
        int maxZ = Math.max(p1.getBlockZ(), p2.getBlockZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = p1.getWorld().getBlockAt(x, y, z);
                    if (isDoorLowerHalf(block)) {
                        doors.put(block.getLocation(), 0);
                    }
                }
            }
        }
        plugin.getLogger().info("[GameManager] " + doors.size() + " porte(s) trouvée(s) dans '" + arena.getId() + "'.");
        return doors;
    }

    private boolean isDoorLowerHalf(Block block) {
        if (!Tag.DOORS.isTagged(block.getType())) return false;
        if (!(block.getBlockData() instanceof Bisected bisected)) return false;
        return bisected.getHalf() == Bisected.Half.BOTTOM;
    }

    private void tickDoors(Arena arena) {
        Map<Location, Integer> doorHits = arenaDoorHits.get(arena.getId());
        if (doorHits == null || doorHits.isEmpty()) return;

        long now = System.currentTimeMillis();

        for (UUID uid : new ArrayList<>(arena.getActiveMobUUIDs())) {
            Entity e = Bukkit.getEntity(uid);
            if (!(e instanceof Zombie zombie) || !zombie.isValid() || zombie.isDead()) continue;

            // Un zombie peut taper une porte au maximum toutes les 2 secondes
            Long lastHit = zombieDoorCooldowns.get(zombie.getUniqueId());
            if (lastHit != null && now - lastHit < 2_000L) continue;

            Location zLoc = zombie.getLocation();

            for (Map.Entry<Location, Integer> entry : new ArrayList<>(doorHits.entrySet())) {
                Location doorLoc = entry.getKey();
                if (!doorLoc.getWorld().equals(zLoc.getWorld())) continue;
                if (doorLoc.distanceSquared(zLoc) > 4.0) continue; // dans un rayon de 2 blocs

                Block block = doorLoc.getBlock();
                if (!isDoorLowerHalf(block)) {
                    doorHits.remove(doorLoc);
                    continue;
                }

                int hits = entry.getValue() + 1;
                zombieDoorCooldowns.put(zombie.getUniqueId(), now);

                if (hits >= 3) {
                    // Casser les deux demi-blocs
                    Material mat = block.getType();
                    block.getRelative(0, 1, 0).setType(Material.AIR);
                    block.setType(Material.AIR);
                    doorHits.remove(doorLoc);
                    doorLoc.getWorld().playSound(doorLoc, Sound.BLOCK_WOOD_BREAK, 1.2f, 0.9f);
                    doorLoc.getWorld().spawnParticle(VersionCompat.BLOCK,
                        doorLoc.clone().add(0.5, 1, 0.5), 25, 0.4, 0.5, 0.4,
                        mat.createBlockData());
                } else {
                    doorHits.put(doorLoc, hits);
                    doorLoc.getWorld().playSound(doorLoc, Sound.BLOCK_WOOD_HIT, 0.8f, 0.7f + hits * 0.1f);
                }
                break; // un zombie ne tape qu'une porte par cycle
            }
        }
    }

    /**
     * Si un villageois se trouve à ≤5 blocs d'une porte fermée (= il est dans une maison),
     * les zombies à ≤15 blocs sont redirigés vers cette porte afin de l'enfoncer.
     */
    private void tickDoorVillagerAttraction(Arena arena) {
        Map<Location, Integer> doorHits = arenaDoorHits.get(arena.getId());
        if (doorHits == null || doorHits.isEmpty()) return;

        for (Location doorLoc : new ArrayList<>(doorHits.keySet())) {
            if (!isDoorLowerHalf(doorLoc.getBlock())) continue; // porte déjà cassée

            // Vérifier si un villageois est proche de cette porte (dans la maison)
            boolean villagerInHouse = false;
            for (UUID vuid : arena.getVillagerUUIDs()) {
                Entity ve = Bukkit.getEntity(vuid);
                if (!(ve instanceof Villager v) || !v.isValid() || v.isDead()) continue;
                if (v.getLocation().distanceSquared(doorLoc) <= 25.0) { // rayon 5 blocs
                    villagerInHouse = true;
                    break;
                }
            }
            if (!villagerInHouse) continue;

            // Diriger les zombies proches (rayon 15 blocs) vers cette porte
            Location target = doorLoc.clone().add(0.5, 0, 0.5);
            for (UUID uid : new ArrayList<>(arena.getActiveMobUUIDs())) {
                Entity e = Bukkit.getEntity(uid);
                if (!(e instanceof Zombie zombie) || !zombie.isValid() || zombie.isDead()) continue;
                if (zombie.getLocation().distanceSquared(doorLoc) > 225.0) continue; // rayon 15 blocs
                zombie.getPathfinder().moveTo(target, 1.0);
            }
        }
    }

    private void cancelDoorTask(Arena arena) {
        BukkitTask t = doorTasks.remove(arena.getId());
        if (t != null) t.cancel();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SCOREBOARD (PARTIE)
    // ═══════════════════════════════════════════════════════════════════════

    private void startScoreboardTicker(Arena arena) {
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (arena.getState() == ArenaState.ENDING || arena.getState() == ArenaState.WAITING) {
                cancelScoreboardTicker(arena);
                return;
            }
            updateScoreboard(arena);
        }, 0L, 20L);
        BukkitTask old = scoreboardTasks.put(arena.getId(), task);
        if (old != null) old.cancel();
    }

    private void cancelScoreboardTicker(Arena arena) {
        BukkitTask t = scoreboardTasks.remove(arena.getId());
        if (t != null) t.cancel();
    }

    private void updateScoreboard(Arena arena) {
        for (Player p : arena.getPlayers()) {
            Long endTime = respawnEndTimes.get(p.getUniqueId());
            int remaining = endTime != null
                ? (int) Math.max(0, (endTime - System.currentTimeMillis()) / 1000) : -1;
            sbManager.update(p, arena, remaining);
        }
        for (Player p : arena.getSpectators()) {
            sbManager.updateSpectator(p, arena);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // UTILITAIRES INTERNES
    // ═══════════════════════════════════════════════════════════════════════

    /** Trouve le villageois le plus proche de {@code from} dans l'arène. */
    public Villager findNearestVillager(Arena arena, Location from) {
        Villager nearest = null;
        double best = Double.MAX_VALUE;
        for (UUID uid : arena.getVillagerUUIDs()) {
            Entity e = Bukkit.getEntity(uid);
            if (!(e instanceof Villager v) || !v.isValid() || v.isDead()) continue;
            double d = v.getLocation().distanceSquared(from);
            if (d < best) { best = d; nearest = v; }
        }
        return nearest;
    }

    /** Trouve le joueur le plus proche de {@code from} dans l'arène. */
    public Player findNearestPlayer(Arena arena, Location from) {
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (Player p : arena.getPlayers()) {
            if (p.isDead() || !p.isOnline()) continue;
            double d = p.getLocation().distanceSquared(from);
            if (d < best) { best = d; nearest = p; }
        }
        return nearest;
    }

    private String m(String key, Object... args) {
        return plugin.getMessages().format(key, args);
    }

    private String bossName(LivingEntity boss) {
        return switch (boss.getType()) {
            case WITHER -> m("game.boss-name.wither");
            case ELDER_GUARDIAN -> m("game.boss-name.elder-guardian");
            case RAVAGER -> m("game.boss-name.ravager");
            default -> boss.getType().name();
        };
    }

    private void broadcast(Arena arena, String message) {
        ChatUtil.broadcast(arena.getAllParticipants(), plugin.getMessages().getPrefix() + message);
    }

    private int getRespawnRemainingSeconds(Player player) {
        Long endTime = respawnEndTimes.get(player.getUniqueId());
        if (endTime == null) {
            return -1;
        }
        return (int) Math.max(0, Math.ceil((endTime - System.currentTimeMillis()) / 1000.0));
    }

    private void completeRespawn(Player player, Arena arena) {
        if (player == null || arena == null || !player.isOnline()) {
            return;
        }
        clearObserverMode(player);
        player.setGameMode(GameMode.SURVIVAL);
        if (arena.getEffectiveGameSpawn() != null) {
            player.teleport(arena.getEffectiveGameSpawn());
        }
        player.setHealth(20.0);
        plugin.getPlayerManager().applyKit(player);
        ChatUtil.send(player, m("game.respawned"));
        sbManager.update(player, arena, -1);
    }

    private void applyObserverMode(Player player, Arena arena, boolean giveLeaveItem) {
        clearObserverMode(player);

        player.closeInventory();
        player.setGameMode(GameMode.SURVIVAL);
        player.setInvulnerable(true);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setCollidable(false);
        player.setCanPickupItems(false);
        player.setFireTicks(0);
        player.setFallDistance(0f);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));

        Location spawn = arena.getEffectiveGameSpawn();
        if (spawn != null) {
            player.teleport(spawn);
        }

        if (giveLeaveItem) {
            player.getInventory().clear();
            if (player.getEquipment() != null) {
                player.getEquipment().clear();
            }

            org.bukkit.inventory.ItemStack leaveItem =
                new fr.obsidianlabs.plugins.villagedefense.util.ItemBuilder(Material.RED_BED)
                    .name(plugin.getMessages().format("spectator.leave-item"))
                    .build();
            player.getInventory().setItem(4, leaveItem);
            player.getInventory().setHeldItemSlot(4);
        }

        player.updateInventory();

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.getUniqueId().equals(player.getUniqueId())) {
                viewer.hidePlayer(plugin, player);
            }
        }
    }

    public void clearObserverMode(Player player) {
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.setInvulnerable(false);
        player.setFlying(false);
        player.setAllowFlight(false);
        player.setCollidable(true);
        player.setCanPickupItems(true);
        player.setFallDistance(0f);

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            viewer.showPlayer(plugin, player);
        }
    }

    private void registerTask(Arena arena, BukkitTask task) {
        BukkitTask old = arenaTasks.put(arena.getId(), task);
        if (old != null) old.cancel();
    }

    private void cancelTask(Arena arena) {
        BukkitTask t = arenaTasks.remove(arena.getId());
        if (t != null) t.cancel();
    }

    public void shutdown() {
        arenaTasks.values().forEach(BukkitTask::cancel);          arenaTasks.clear();
        lobbyTasks.values().forEach(BukkitTask::cancel);           lobbyTasks.clear();
        scoreboardTasks.values().forEach(BukkitTask::cancel);      scoreboardTasks.clear();
        villagerKeeperTasks.values().forEach(BukkitTask::cancel);  villagerKeeperTasks.clear();
        retargetTasks.values().forEach(BukkitTask::cancel);        retargetTasks.clear();
        doorTasks.values().forEach(BukkitTask::cancel);            doorTasks.clear();
        arrowCleanupTasks.values().forEach(BukkitTask::cancel);    arrowCleanupTasks.clear();
        stuckMobTasks.values().forEach(BukkitTask::cancel);        stuckMobTasks.clear();
        villagerLookTasks.values().forEach(BukkitTask::cancel);    villagerLookTasks.clear();
        bossBossBarTasks.values().forEach(BukkitTask::cancel);     bossBossBarTasks.clear();
        arenaBossBar.values().forEach(BossBar::removeAll);         arenaBossBar.clear();
        arenaBossUUID.clear();
        pendingRespawns.values().forEach(BukkitTask::cancel);      pendingRespawns.clear();
        rejoinExpiries.clear();
        rejoinArenaIds.clear();
        pendingHubJoinArenas.clear();
        pendingHubJoinExpiries.clear();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // NETTOYAGE DES FLÈCHES
    // ═══════════════════════════════════════════════════════════════════════

    /** Supprime les flèches posées sur le sol dans l'arène toutes les 10 secondes. */
    private void startArrowCleanup(Arena arena) {
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (arena.getState() == ArenaState.ENDING || arena.getState() == ArenaState.WAITING) {
                cancelArrowCleanup(arena);
                return;
            }
            if (!arena.hasBounds()) return;
            Location p1 = arena.getPos1(), p2 = arena.getPos2();
            if (p1 == null || p2 == null || p1.getWorld() == null) return;

            int minX = Math.min(p1.getBlockX(), p2.getBlockX());
            int maxX = Math.max(p1.getBlockX(), p2.getBlockX());
            int minZ = Math.min(p1.getBlockZ(), p2.getBlockZ());
            int maxZ = Math.max(p1.getBlockZ(), p2.getBlockZ());

            p1.getWorld().getEntitiesByClass(org.bukkit.entity.Arrow.class).stream()
                .filter(a -> !a.hasMetadata("vd_tower_arrow") && a.isOnGround())
                .filter(a -> {
                    Location l = a.getLocation();
                    return l.getBlockX() >= minX && l.getBlockX() <= maxX
                        && l.getBlockZ() >= minZ && l.getBlockZ() <= maxZ;
                })
                .forEach(org.bukkit.entity.Entity::remove);
        }, 200L, 200L); // toutes les 10 secondes

        BukkitTask old = arrowCleanupTasks.put(arena.getId(), task);
        if (old != null) old.cancel();
    }

    private void cancelArrowCleanup(Arena arena) {
        BukkitTask t = arrowCleanupTasks.remove(arena.getId());
        if (t != null) t.cancel();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MOB COINCÉ — TIMEOUT DE VAGUE
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Si 1 seul mob reste dans l'arène depuis plus de 3 minutes, la vague est
     * forcée complète (le mob est tué et la vague se termine normalement).
     */
    private void startStuckMobChecker(Arena arena) {
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (arena.getState() != ArenaState.RUNNING) return;

            String id = arena.getId();

            // --- Nettoyage des mobs fantômes (UUID tracké mais entité morte/disparue) ---
            int cleaned = 0;
            for (UUID uid : new ArrayList<>(arena.getActiveMobUUIDs())) {
                Entity e = Bukkit.getEntity(uid);
                if (e == null || !e.isValid() || e.isDead()) {
                    arena.markWaveMobResolved(uid);
                    cleaned++;
                }
            }
            if (cleaned > 0) {
                plugin.getLogger().info("[WaveCheck] " + cleaned + " mob(s) fantôme(s) nettoyé(s) dans '" + id + "'.");
            }

            int activeMobs = arena.getActiveMobCount();
            int pendingSpawns = arena.getWavePendingSpawnCount();
            int remainingWaveMobs = arena.getWaveRemainingMobCount();

            // --- Vague bloquée avec 0 mobs actifs (spawn rate issue ou mobs disparus) ---
            if (activeMobs == 0) {
                if (remainingWaveMobs == 0) {
                    stuckMobSince.remove(id + "_empty");
                    onWaveCompleted(arena);
                    return;
                }

                if (pendingSpawns > 0) {
                    stuckMobSince.remove(id + "_empty");
                    return;
                }

                // Ignorer si aucune vague n'a encore été lancée (délai pré-vague)
                if (arena.getCurrentWave() == 0) {
                    stuckMobSince.remove(id + "_empty");
                    return;
                }
                long now = System.currentTimeMillis();
                stuckMobSince.putIfAbsent(id + "_empty", now);
                long elapsed = now - stuckMobSince.get(id + "_empty");
                if (elapsed >= 15_000L) { // 15 secondes de vague vide → forcer la complétion
                    stuckMobSince.remove(id + "_empty");
                    broadcast(arena, m("game.auto-wave-empty"));
                    onWaveCompleted(arena);
                }
                return;
            }
            stuckMobSince.remove(id + "_empty");

            // --- Mob coincé (1 seul mob depuis trop longtemps) ---
            if (activeMobs == 1) {
                long now = System.currentTimeMillis();
                stuckMobSince.putIfAbsent(id, now);
                long elapsed = now - stuckMobSince.get(id);
                long timeoutMs = plugin.getConfig().getLong("game.stuck-mob-timeout-seconds", 180) * 1000L;
                if (elapsed >= timeoutMs) {
                    stuckMobSince.remove(id);
                    broadcast(arena, m("game.auto-wave-stuck"));
                    plugin.getWaveManager().killAllMobs(arena);
                    onWaveCompleted(arena);
                }
            } else {
                stuckMobSince.remove(id);
            }
        }, 200L, 200L); // vérifie toutes les 10 secondes

        BukkitTask old = stuckMobTasks.put(arena.getId(), task);
        if (old != null) old.cancel();
    }

    private void cancelStuckMobChecker(Arena arena) {
        stuckMobSince.remove(arena.getId());
        BukkitTask t = stuckMobTasks.remove(arena.getId());
        if (t != null) t.cancel();
    }

    public void checkAndStart(Arena arena) {
        if (arena.getState() != ArenaState.WAITING) return;
        int minPlayers = plugin.getConfig().getInt("game.min-players", 1);
        if (arena.getPlayerCount() >= minPlayers) startCountdown(arena);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // BOSS BAR
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Appelé par WaveManager dès qu'un mob boss (Ravager, Elder Guardian, Wither)
     * spawne. Crée une bossbar et démarre sa mise à jour périodique.
     */
    public void registerBossEntity(Arena arena, LivingEntity boss) {
        removeBossBar(arena); // nettoie une éventuelle barre précédente

        BarColor color = switch (boss.getType()) {
            case WITHER -> BarColor.PURPLE;
            case ELDER_GUARDIAN -> BarColor.BLUE;
            default -> BarColor.RED;
        };

        BossBar bar = Bukkit.createBossBar(
            m("game.bossbar", bossName(boss)), color, BarStyle.SEGMENTED_10);

        for (Player p : arena.getPlayers()) bar.addPlayer(p);
        arenaBossBar.put(arena.getId(), bar);
        arenaBossUUID.put(arena.getId(), boss.getUniqueId());

        var maxHpAttr = boss.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double maxHp = maxHpAttr != null ? maxHpAttr.getValue() : boss.getHealth();

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Entity e = Bukkit.getEntity(boss.getUniqueId());
            if (e == null || !e.isValid() || e.isDead()) {
                removeBossBar(arena);
                return;
            }
            LivingEntity living = (LivingEntity) e;
            bar.setProgress(Math.max(0.0, Math.min(1.0, living.getHealth() / maxHp)));
        }, 5L, 5L);

        BukkitTask old = bossBossBarTasks.put(arena.getId(), task);
        if (old != null) old.cancel();
    }

    /** Ajoute un joueur à la bossbar de l'arène (si elle existe). */
    public void addPlayerToBossBar(Arena arena, Player player) {
        BossBar bar = arenaBossBar.get(arena.getId());
        if (bar != null) bar.addPlayer(player);
    }

    public boolean isBossEntity(Arena arena, UUID entityUuid) {
        UUID bossUuid = arenaBossUUID.get(arena.getId());
        return bossUuid != null && bossUuid.equals(entityUuid);
    }

    public void removeBossBar(Arena arena) {
        BukkitTask t = bossBossBarTasks.remove(arena.getId());
        if (t != null) t.cancel();
        BossBar bar = arenaBossBar.remove(arena.getId());
        if (bar != null) bar.removeAll();
        arenaBossUUID.remove(arena.getId());
    }
}
