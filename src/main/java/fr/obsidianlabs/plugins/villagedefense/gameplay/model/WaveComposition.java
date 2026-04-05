package fr.obsidianlabs.plugins.villagedefense.gameplay.model;

import java.util.List;

public class WaveComposition {

    private final int waveIndex;
    private final WaveCategory category;
    private final List<WaveMobSelection> selections;

    public WaveComposition(int waveIndex, WaveCategory category, List<WaveMobSelection> selections) {
        this.waveIndex  = waveIndex;
        this.category   = category;
        this.selections = selections;
    }

    public int getWaveIndex()                       { return waveIndex; }
    public WaveCategory getCategory()               { return category; }
    public List<WaveMobSelection> getSelections()   { return selections; }

    public int getTotalMobCount() {
        return selections.stream().mapToInt(WaveMobSelection::getAmount).sum();
    }
}
