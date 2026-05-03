package fr.obsidianlabs.plugins.villagedefense.gameplay.model;

import java.util.*;

/**
 * Objet racine du système Gameplay pour une arène.
 * Contient toute la configuration : difficultés, scaling, catégories, pools, overrides.
 */
public class ArenaGameplaySettings {

    private GameplayGeneralSettings generalSettings;
    private DifficultySettings difficultySettings;
    private WaveScalingSettings waveScalingSettings;
    private Map<WaveCategory, WaveCategoryTemplate> categoryTemplates;
    private Map<String, MobPool> mobPools;
    private Map<Integer, WaveOverride> waveOverrides;

    public ArenaGameplaySettings() {
        this.generalSettings     = new GameplayGeneralSettings();
        this.difficultySettings  = new DifficultySettings();
        this.waveScalingSettings = new WaveScalingSettings();
        this.categoryTemplates   = new EnumMap<>(WaveCategory.class);
        this.mobPools            = new LinkedHashMap<>();
        this.waveOverrides       = new TreeMap<>();
    }

    /** Crée une config par défaut avec un pool de zombies vanilla. */
    public static ArenaGameplaySettings createDefault() {
        ArenaGameplaySettings s = new ArenaGameplaySettings();

        // Pool normal par défaut
        MobPool defaultPool = new MobPool("normal_default", "Zombies");
        defaultPool.setDefaultCategory(WaveCategory.NORMAL);
        MobEntry zombie = new MobEntry("ZOMBIE", MobSource.VANILLA, "Zombie");
        zombie.setWeight(10);
        zombie.setThreat(5.0);
        zombie.setMinAmount(1);
        zombie.setMaxAmount(3);
        defaultPool.getEntries().add(zombie);
        s.mobPools.put(defaultPool.getId(), defaultPool);

        // Pool boss par défaut
        MobPool bossPool = new MobPool("boss_default", "Boss Zombies");
        bossPool.setDefaultCategory(WaveCategory.BOSS);
        MobEntry bossZombie = new MobEntry("HUSK", MobSource.VANILLA, "Boss Zombie");
        bossZombie.setWeight(10);
        bossZombie.setThreat(20.0);
        bossZombie.setMinAmount(3);
        bossZombie.setMaxAmount(3);
        bossPool.getEntries().add(bossZombie);
        s.mobPools.put(bossPool.getId(), bossPool);

        // Templates par catégorie
        for (WaveCategory cat : WaveCategory.values()) {
            WaveCategoryTemplate t = new WaveCategoryTemplate(cat);
            t.setMobPoolId(cat == WaveCategory.BOSS ? "boss_default" : "normal_default");
            switch (cat) {
                case ELITE   -> { t.setHealthMultiplier(1.5); t.setDamageMultiplier(1.2); t.setCategoryBudgetMultiplier(1.5); }
                case BOSS    -> { t.setHealthMultiplier(4.0); t.setDamageMultiplier(2.0); t.setCategoryBudgetMultiplier(3.0); t.setCompositionMode(CompositionMode.FIXED); }
                case SPECIAL -> { t.setHealthMultiplier(1.2); t.setCategoryBudgetMultiplier(1.2); }
                default -> {}
            }
            s.categoryTemplates.put(cat, t);
        }

        return s;
    }

    // ---- Getters / Setters ----

    public GameplayGeneralSettings getGeneralSettings()                                  { return generalSettings; }
    public void setGeneralSettings(GameplayGeneralSettings v)                            { this.generalSettings = v; }

    public DifficultySettings getDifficultySettings()                                    { return difficultySettings; }
    public void setDifficultySettings(DifficultySettings v)                              { this.difficultySettings = v; }

    public WaveScalingSettings getWaveScalingSettings()                                  { return waveScalingSettings; }
    public void setWaveScalingSettings(WaveScalingSettings v)                            { this.waveScalingSettings = v; }

    public Map<WaveCategory, WaveCategoryTemplate> getCategoryTemplates()                { return categoryTemplates; }
    public void setCategoryTemplates(Map<WaveCategory, WaveCategoryTemplate> v)          { this.categoryTemplates = v; }

    public Map<String, MobPool> getMobPools()                                            { return mobPools; }
    public void setMobPools(Map<String, MobPool> v)                                      { this.mobPools = v; }

    public Map<Integer, WaveOverride> getWaveOverrides()                                 { return waveOverrides; }
    public void setWaveOverrides(Map<Integer, WaveOverride> v)                           { this.waveOverrides = v; }
}
