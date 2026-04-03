package fr.obsidianlabs.plugins.villagedefense.wave;

import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;

/**
 * Représente une vague de monstres à spawner.
 */
public class Wave {

    private final int waveNumber;
    private final WaveType type;
    private final List<WaveEntry> entries = new ArrayList<>();

    public Wave(int waveNumber, WaveType type) {
        this.waveNumber = waveNumber;
        this.type = type;
    }

    public record WaveEntry(EntityType entityType, int count, double hpMultiplier, double damageMultiplier) {}

    public void addEntry(EntityType type, int count, double hpMult, double dmgMult) {
        entries.add(new WaveEntry(type, count, hpMult, dmgMult));
    }

    public int getWaveNumber() { return waveNumber; }
    public WaveType getType() { return type; }
    public List<WaveEntry> getEntries() { return entries; }

    public int getTotalMobCount() {
        return entries.stream().mapToInt(WaveEntry::count).sum();
    }
}
