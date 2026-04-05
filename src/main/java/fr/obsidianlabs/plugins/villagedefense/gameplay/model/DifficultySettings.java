package fr.obsidianlabs.plugins.villagedefense.gameplay.model;

import java.util.*;

public class DifficultySettings {

    private List<GameDifficulty> enabledDifficulties;
    private GameDifficulty defaultDifficulty;
    private Map<GameDifficulty, DifficultyProfile> profiles;

    public DifficultySettings() {
        this.enabledDifficulties = new ArrayList<>(List.of(GameDifficulty.EASY, GameDifficulty.NORMAL, GameDifficulty.HARD));
        this.defaultDifficulty = GameDifficulty.NORMAL;
        this.profiles = new EnumMap<>(GameDifficulty.class);
        for (GameDifficulty d : enabledDifficulties) {
            profiles.put(d, DifficultyProfile.defaultFor(d));
        }
    }

    public List<GameDifficulty> getEnabledDifficulties()                    { return enabledDifficulties; }
    public void setEnabledDifficulties(List<GameDifficulty> v)              { this.enabledDifficulties = v; }

    public GameDifficulty getDefaultDifficulty()                            { return defaultDifficulty; }
    public void setDefaultDifficulty(GameDifficulty v)                      { this.defaultDifficulty = v; }

    public Map<GameDifficulty, DifficultyProfile> getProfiles()             { return profiles; }
    public void setProfiles(Map<GameDifficulty, DifficultyProfile> v)       { this.profiles = v; }

    public DifficultyProfile getProfile(GameDifficulty difficulty) {
        return profiles.getOrDefault(difficulty, DifficultyProfile.defaultFor(difficulty));
    }
}
