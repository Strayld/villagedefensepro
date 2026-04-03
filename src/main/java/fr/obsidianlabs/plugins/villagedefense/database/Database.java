package fr.obsidianlabs.plugins.villagedefense.database;

import fr.obsidianlabs.plugins.villagedefense.arena.ArenaAttemptEntry;
import fr.obsidianlabs.plugins.villagedefense.network.NetworkArenaEntry;
import fr.obsidianlabs.plugins.villagedefense.player.VDPlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface Database {

    /** Initialise la connexion et crée les tables si nécessaire */
    void init();

    /** Ferme la connexion */
    void close();

    /** Charge les données d'un joueur (async) */
    CompletableFuture<VDPlayer> loadPlayer(UUID uuid, String name);

    /** Sauvegarde les données d'un joueur (async) */
    CompletableFuture<Void> savePlayer(VDPlayer player);

    /** Vérifie si la connexion est active */
    boolean isConnected();

    /**
     * Retourne le top {@code limit} joueurs triés par la colonne {@code orderBy}
     * (valeurs acceptées : "total_kills", "total_waves", "total_games", "total_coins_earned").
     */
    CompletableFuture<List<LeaderboardEntry>> getTopPlayers(int limit, String orderBy);

    /**
     * Retourne le top {@code limit} joueurs pour l'arène {@code arenaId}
     * (colonne : "kills", "waves", "games").
     */
    default CompletableFuture<List<LeaderboardEntry>> getTopPlayersByArena(int limit, String orderBy, String arenaId) {
        return CompletableFuture.completedFuture(List.of());
    }

    /** Incrémente les kills de ce joueur pour cette arène (async, fire-and-forget). */
    default void recordArenaKill(UUID uuid, String arenaId) {}

    /** Incrémente les vagues de ce joueur pour cette arène (async, fire-and-forget). */
    default void recordArenaWave(UUID uuid, String arenaId) {}

    /** Incrémente les parties de ce joueur pour cette arène (async, fire-and-forget). */
    default void recordArenaGame(UUID uuid, String arenaId) {}

    // ── Sync réseau (optionnel – no-op sur H2, implémenté sur MySQL) ──────────

    /** Crée la table {@code vd_network_arenas} si elle n'existe pas encore. */
    default void initNetworkTable() {}

    /** Insère ou met à jour l'entrée d'une arène dans la table réseau partagée. */
    default void upsertNetworkArena(NetworkArenaEntry entry) {}

    /** Supprime toutes les entrées appartenant à {@code serverId} (appelé au shutdown). */
    default void removeNetworkArenas(String serverId) {}

    // ── Tokens de rejoin réseau (optionnel – no-op sur H2, implémenté sur MySQL) ─

    /** Crée la table {@code vd_player_rejoin} si elle n'existe pas encore. */
    default void initRejoinTable() {}

    /** Insère ou remplace le token de rejoin d'un joueur. */
    default void saveRejoinToken(UUID playerUuid, String playerName,
                                 String serverId, String arenaId,
                                 String arenaDisplayName, long expiryTimestamp) {}

    /** Supprime le token de rejoin d'un joueur (rejoin effectué ou expiré). */
    default void deleteRejoinToken(UUID playerUuid) {}

    // ── Joins en attente depuis le Hub (Velocity/BungeeCord) ──────────────────

    /** Crée la table {@code vd_hub_pending} si elle n'existe pas encore. */
    default void initHubPendingTable() {}

    /**
     * Insère ou remplace un join en attente pour un joueur venant du Hub.
     * @param action "JOIN" ou "SPECTATE"
     */
    default void writeHubPending(UUID playerUuid, String arenaId,
                                 String action, long expiryTimestamp) {}

    /**
     * Lit et supprime atomiquement l'entrée en attente d'un joueur.
     * Retourne {@code null} si aucune entrée valide (expirée ou absente).
     */
    default HubPendingEntry consumeHubPending(UUID playerUuid) { return null; }

    record HubPendingEntry(String arenaId, String action) {}

    // ── Tentatives par arène (optionnel – no-op par défaut) ───────────────────

    /** Crée la table {@code vd_arena_attempts} si elle n'existe pas encore. */
    default void initAttemptTable() {}

    /**
     * Charge toutes les entrées de tentatives d'un joueur.
     * @return map arenaId → entry
     */
    default CompletableFuture<Map<String, ArenaAttemptEntry>> loadAttemptEntries(UUID uuid) {
        return CompletableFuture.completedFuture(new java.util.HashMap<>());
    }

    /** Insère ou met à jour l'entrée de tentatives d'un joueur pour une arène. */
    default void saveAttemptEntry(UUID uuid, String arenaId, int attemptsUsed, long windowStart) {}
}
