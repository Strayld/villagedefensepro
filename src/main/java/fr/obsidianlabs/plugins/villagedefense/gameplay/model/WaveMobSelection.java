package fr.obsidianlabs.plugins.villagedefense.gameplay.model;

public class WaveMobSelection {

    private final MobEntry mobEntry;
    private final int amount;

    public WaveMobSelection(MobEntry mobEntry, int amount) {
        this.mobEntry = mobEntry;
        this.amount   = amount;
    }

    public MobEntry getMobEntry() { return mobEntry; }
    public int getAmount()        { return amount; }
}
