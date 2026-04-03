package fr.obsidianlabs.plugins.villagedefense.arena;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Gestion des tentatives par joueur et par arène.
 *
 * <p>Architecture :
 * <ul>
 *   <li>Au login : {@link #loadPlayer(UUID)} charge les entrées depuis la DB en async.</li>
 *   <li>Lors du join : {@link #isBlocked(UUID, Arena)} vérifie synchronement en mémoire.</li>
 *   <li>Au lancement de la partie : {@link #incrementAttempt(UUID, String)} met à jour
 *       le cache et persiste en DB (fire-and-forget).</li>
 *   <li>Au logout : {@link #unloadPlayer(UUID)} libère la mémoire.</li>
 * </ul>
 */
public class ArenaAttemptManager {

    private final VillageDefensePlugin plugin;

    /** uuid → (arenaId → entry) */
    private final Map<UUID, Map<String, ArenaAttemptEntry>> cache = new HashMap<>();

    public ArenaAttemptManager(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    // ── Cycle de vie joueur ────────────────────────────────────────────────────

    /**
     * Charge les données de tentatives depuis la DB (async).
     * Appeler dans {@code PlayerListener.onJoin}.
     */
    public CompletableFuture<Void> loadPlayer(UUID uuid) {
        return plugin.getDatabase().loadAttemptEntries(uuid).thenAccept(entries -> {
            synchronized (cache) {
                cache.put(uuid, new HashMap<>(entries));
            }
        });
    }

    /**
     * Supprime les données du cache (sync, sur le thread principal).
     * Appeler dans {@code PlayerListener.onQuit}.
     */
    public void unloadPlayer(UUID uuid) {
        synchronized (cache) {
            cache.remove(uuid);
        }
    }

    // ── Requêtes synchrones ────────────────────────────────────────────────────

    /**
     * Retourne {@code true} si le joueur ne peut plus tenter l'arène
     * (limit atteinte ET fenêtre encore active).
     */
    public boolean isBlocked(UUID uuid, Arena arena) {
        if (!arena.hasAttemptLimit()) return false;
        ArenaAttemptEntry entry = getEntry(uuid, arena.getId());
        if (entry == null) return false;

        long nowSec    = System.currentTimeMillis() / 1000L;
        long windowSec = (long) arena.getAttemptWindowHours() * 3600L;

        // Fenêtre expirée → non bloqué
        if (nowSec >= entry.getWindowStart() + windowSec) return false;

        return entry.getAttemptsUsed() >= arena.getAttemptLimit();
    }

    /**
     * Nombre de tentatives restantes dans la fenêtre courante.
     * Retourne {@code Integer.MAX_VALUE} si pas de limite.
     */
    public int getRemainingAttempts(UUID uuid, Arena arena) {
        if (!arena.hasAttemptLimit()) return Integer.MAX_VALUE;
        ArenaAttemptEntry entry = getEntry(uuid, arena.getId());
        if (entry == null) return arena.getAttemptLimit();

        long nowSec    = System.currentTimeMillis() / 1000L;
        long windowSec = (long) arena.getAttemptWindowHours() * 3600L;
        if (nowSec >= entry.getWindowStart() + windowSec) return arena.getAttemptLimit();

        return Math.max(0, arena.getAttemptLimit() - entry.getAttemptsUsed());
    }

    /**
     * Secondes restantes avant que la fenêtre actuelle expire.
     * Retourne 0 si pas de fenêtre active.
     */
    public long getWindowRemainingSeconds(UUID uuid, Arena arena) {
        ArenaAttemptEntry entry = getEntry(uuid, arena.getId());
        if (entry == null) return 0L;
        long windowSec = (long) arena.getAttemptWindowHours() * 3600L;
        long elapsed   = System.currentTimeMillis() / 1000L - entry.getWindowStart();
        return Math.max(0L, windowSec - elapsed);
    }

    // ── Mutation ───────────────────────────────────────────────────────────────

    /**
     * Incrémente le compteur de tentatives pour ce joueur sur cette arène.
     * Si la fenêtre a expiré, en ouvre une nouvelle.
     * Persiste en DB de façon asynchrone.
     */
    public void incrementAttempt(UUID uuid, String arenaId) {
        Arena arena = plugin.getArenaManager().getArena(arenaId);
        if (arena == null || !arena.hasAttemptLimit()) return;

        long nowSec    = System.currentTimeMillis() / 1000L;
        long windowSec = (long) arena.getAttemptWindowHours() * 3600L;

        ArenaAttemptEntry entry;
        synchronized (cache) {
            Map<String, ArenaAttemptEntry> playerMap = cache.computeIfAbsent(uuid, k -> new HashMap<>());
            entry = playerMap.get(arenaId);

            if (entry == null) {
                entry = new ArenaAttemptEntry(1, nowSec);
                playerMap.put(arenaId, entry);
            } else if (nowSec >= entry.getWindowStart() + windowSec) {
                // Fenêtre expirée → nouvelle fenêtre
                entry.setAttemptsUsed(1);
                entry.setWindowStart(nowSec);
            } else {
                entry.increment();
            }
        }

        // Persistance asynchrone
        final int used  = entry.getAttemptsUsed();
        final long wStart = entry.getWindowStart();
        plugin.getDatabase().saveAttemptEntry(uuid, arenaId, used, wStart);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private ArenaAttemptEntry getEntry(UUID uuid, String arenaId) {
        synchronized (cache) {
            Map<String, ArenaAttemptEntry> playerMap = cache.get(uuid);
            if (playerMap == null) return null;
            return playerMap.get(arenaId);
        }
    }
}
