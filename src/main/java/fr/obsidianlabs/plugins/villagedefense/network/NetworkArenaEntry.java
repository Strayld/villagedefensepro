package fr.obsidianlabs.plugins.villagedefense.network;

/**
 * Représente l'état d'une arène exporté dans la table partagée {@code vd_network_arenas}.
 * Utilisé par le plugin Hub pour afficher et cibler les arènes disponibles sur le réseau.
 */
public final class NetworkArenaEntry {

    private final String serverId;
    private final String arenaId;
    private final String displayName;
    private final String state;
    private final String availability;
    private final int playerCount;
    private final int maxPlayers;
    private final int currentWave;
    private final String iconMaterial;
    private final long lastUpdated;

    public NetworkArenaEntry(
            String serverId,
            String arenaId,
            String displayName,
            String state,
            String availability,
            int playerCount,
            int maxPlayers,
            int currentWave,
            String iconMaterial,
            long lastUpdated) {
        this.serverId = serverId;
        this.arenaId = arenaId;
        this.displayName = displayName;
        this.state = state;
        this.availability = availability;
        this.playerCount = playerCount;
        this.maxPlayers = maxPlayers;
        this.currentWave = currentWave;
        this.iconMaterial = iconMaterial;
        this.lastUpdated = lastUpdated;
    }

    public String getServerId()     { return serverId; }
    public String getArenaId()      { return arenaId; }
    public String getDisplayName()  { return displayName; }
    public String getState()        { return state; }
    public String getAvailability() { return availability; }
    public int getPlayerCount()     { return playerCount; }
    public int getMaxPlayers()      { return maxPlayers; }
    public int getCurrentWave()     { return currentWave; }
    public String getIconMaterial() { return iconMaterial; }
    public long getLastUpdated()    { return lastUpdated; }
}
