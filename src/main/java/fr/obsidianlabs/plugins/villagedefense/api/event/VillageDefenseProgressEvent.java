package fr.obsidianlabs.plugins.villagedefense.api.event;

import fr.obsidianlabs.plugins.villagedefense.api.VillageDefenseProgressType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class VillageDefenseProgressEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String arenaId;
    private final VillageDefenseProgressType progressType;
    private final long amount;
    private final long total;
    private final int currentWave;
    private final String detail;

    public VillageDefenseProgressEvent(
        Player player,
        String arenaId,
        VillageDefenseProgressType progressType,
        long amount,
        long total,
        int currentWave,
        String detail
    ) {
        this.player = player;
        this.arenaId = arenaId;
        this.progressType = progressType;
        this.amount = amount;
        this.total = total;
        this.currentWave = currentWave;
        this.detail = detail;
    }

    public Player getPlayer() { return player; }
    public String getArenaId() { return arenaId; }
    public VillageDefenseProgressType getProgressType() { return progressType; }
    public long getAmount() { return amount; }
    public long getTotal() { return total; }
    public int getCurrentWave() { return currentWave; }
    public String getDetail() { return detail; }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
