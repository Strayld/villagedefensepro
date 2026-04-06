package fr.obsidianlabs.plugins.villagedefense.gameplay.persistence;

import fr.obsidianlabs.plugins.villagedefense.gameplay.model.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.*;

/**
 * Convertit ArenaGameplaySettings ↔ YamlConfiguration.
 */
public class GameplayConfigMapper {

    // ---- Désérialisation ----

    public ArenaGameplaySettings fromConfig(YamlConfiguration config) {
        ArenaGameplaySettings s = new ArenaGameplaySettings();

        ConfigurationSection gen = config.getConfigurationSection("general");
        if (gen != null) s.setGeneralSettings(readGeneral(gen));

        ConfigurationSection diff = config.getConfigurationSection("difficulties");
        if (diff != null) s.setDifficultySettings(readDifficulties(diff));

        ConfigurationSection scaling = config.getConfigurationSection("scaling");
        if (scaling != null) s.setWaveScalingSettings(readScaling(scaling));

        ConfigurationSection cats = config.getConfigurationSection("categories");
        if (cats != null) {
            for (String key : cats.getKeys(false)) {
                try {
                    WaveCategory cat = WaveCategory.valueOf(key.toUpperCase());
                    ConfigurationSection catSec = cats.getConfigurationSection(key);
                    if (catSec != null) s.getCategoryTemplates().put(cat, readTemplate(cat, catSec));
                } catch (IllegalArgumentException ignored) {}
            }
        }

        ConfigurationSection pools = config.getConfigurationSection("pools");
        if (pools != null) {
            for (String poolId : pools.getKeys(false)) {
                ConfigurationSection poolSec = pools.getConfigurationSection(poolId);
                if (poolSec != null) s.getMobPools().put(poolId, readPool(poolId, poolSec));
            }
        }

        ConfigurationSection overrides = config.getConfigurationSection("overrides");
        if (overrides != null) {
            for (String key : overrides.getKeys(false)) {
                try {
                    int waveIdx = Integer.parseInt(key);
                    ConfigurationSection oSec = overrides.getConfigurationSection(key);
                    if (oSec != null) s.getWaveOverrides().put(waveIdx, readOverride(waveIdx, oSec));
                } catch (NumberFormatException ignored) {}
            }
        }

        return s;
    }

    // ---- Sérialisation ----

    public void toConfig(ArenaGameplaySettings s, YamlConfiguration config) {
        writeGeneral(s.getGeneralSettings(), config.createSection("general"));
        writeDifficulties(s.getDifficultySettings(), config.createSection("difficulties"));
        writeScaling(s.getWaveScalingSettings(), config.createSection("scaling"));

        ConfigurationSection cats = config.createSection("categories");
        s.getCategoryTemplates().forEach((cat, t) -> writeTemplate(t, cats.createSection(cat.name())));

        ConfigurationSection pools = config.createSection("pools");
        s.getMobPools().forEach((id, pool) -> writePool(pool, pools.createSection(id)));

        ConfigurationSection overrides = config.createSection("overrides");
        s.getWaveOverrides().forEach((idx, o) -> writeOverride(o, overrides.createSection(String.valueOf(idx))));
    }

    // ---- General ----

    private GameplayGeneralSettings readGeneral(ConfigurationSection s) {
        GameplayGeneralSettings g = new GameplayGeneralSettings();
        g.setEnabled(s.getBoolean("enabled", true));
        g.setUseAdvancedWaveSystem(s.getBoolean("use-advanced-wave-system", true));
        g.setAllowWaveOverrides(s.getBoolean("allow-wave-overrides", true));
        g.setAllowMythicMobs(s.getBoolean("allow-mythicmobs", false));
        return g;
    }

    private void writeGeneral(GameplayGeneralSettings g, ConfigurationSection s) {
        s.set("enabled", g.isEnabled());
        s.set("use-advanced-wave-system", g.isUseAdvancedWaveSystem());
        s.set("allow-wave-overrides", g.isAllowWaveOverrides());
        s.set("allow-mythicmobs", g.isAllowMythicMobs());
    }

    // ---- Difficulties ----

    private DifficultySettings readDifficulties(ConfigurationSection s) {
        DifficultySettings d = new DifficultySettings();

        List<String> enabledRaw = s.getStringList("enabled");
        List<GameDifficulty> enabled = new ArrayList<>();
        for (String raw : enabledRaw) {
            try { enabled.add(GameDifficulty.valueOf(raw.toUpperCase())); } catch (IllegalArgumentException ignored) {}
        }
        if (!enabled.isEmpty()) d.setEnabledDifficulties(enabled);

        String defRaw = s.getString("default");
        if (defRaw != null) {
            try { d.setDefaultDifficulty(GameDifficulty.valueOf(defRaw.toUpperCase())); } catch (IllegalArgumentException ignored) {}
        }

        ConfigurationSection profiles = s.getConfigurationSection("profiles");
        if (profiles != null) {
            Map<GameDifficulty, DifficultyProfile> map = new EnumMap<>(GameDifficulty.class);
            for (String key : profiles.getKeys(false)) {
                try {
                    GameDifficulty diff = GameDifficulty.valueOf(key.toUpperCase());
                    ConfigurationSection ps = profiles.getConfigurationSection(key);
                    if (ps != null) {
                        map.put(diff, new DifficultyProfile(
                            ps.getDouble("health", 1.0),
                            ps.getDouble("damage", 1.0),
                            ps.getDouble("speed",  1.0),
                            ps.getDouble("reward", 1.0)
                        ));
                    }
                } catch (IllegalArgumentException ignored) {}
            }
            if (!map.isEmpty()) d.setProfiles(map);
        }
        return d;
    }

    private void writeDifficulties(DifficultySettings d, ConfigurationSection s) {
        List<String> enabledNames = d.getEnabledDifficulties().stream().map(Enum::name).toList();
        s.set("enabled", enabledNames);
        s.set("default", d.getDefaultDifficulty().name());
        ConfigurationSection profiles = s.createSection("profiles");
        d.getProfiles().forEach((diff, p) -> {
            ConfigurationSection ps = profiles.createSection(diff.name());
            ps.set("health", p.getHealthMultiplier());
            ps.set("damage", p.getDamageMultiplier());
            ps.set("speed",  p.getSpeedMultiplier());
            ps.set("reward", p.getRewardMultiplier());
        });
    }

    // ---- Scaling ----

    private WaveScalingSettings readScaling(ConfigurationSection s) {
        WaveScalingSettings w = new WaveScalingSettings();
        w.setBaseBudget(s.getDouble("base-budget", 20.0));
        w.setLinearGrowth(s.getDouble("linear-growth", 5.0));
        w.setExponentialGrowth(s.getDouble("exponential-growth", 0.1));
        w.setBaseHealthMultiplier(s.getDouble("base-health", 1.0));
        w.setBaseDamageMultiplier(s.getDouble("base-damage", 1.0));
        w.setBaseSpeedMultiplier(s.getDouble("base-speed", 1.0));
        w.setHealthPerWave(s.getDouble("health-per-wave", 0.05));
        w.setDamagePerWave(s.getDouble("damage-per-wave", 0.03));
        w.setSpeedPerWave(s.getDouble("speed-per-wave", 0.01));
        w.setBossInterval(s.getInt("boss-interval", 10));
        w.setEliteInterval(s.getInt("elite-interval", 5));
        w.setSpecialInterval(s.getInt("special-interval", 0));
        w.setEarlyGameMaxMobTypes(s.getInt("early-max-mob-types", 2));
        w.setMidGameMaxMobTypes(s.getInt("mid-max-mob-types", 3));
        w.setLateGameMaxMobTypes(s.getInt("late-max-mob-types", 4));
        w.setMidGameStartWave(s.getInt("mid-game-start-wave", 10));
        w.setLateGameStartWave(s.getInt("late-game-start-wave", 20));
        return w;
    }

    private void writeScaling(WaveScalingSettings w, ConfigurationSection s) {
        s.set("base-budget",            w.getBaseBudget());
        s.set("linear-growth",          w.getLinearGrowth());
        s.set("exponential-growth",     w.getExponentialGrowth());
        s.set("base-health",            w.getBaseHealthMultiplier());
        s.set("base-damage",            w.getBaseDamageMultiplier());
        s.set("base-speed",             w.getBaseSpeedMultiplier());
        s.set("health-per-wave",        w.getHealthPerWave());
        s.set("damage-per-wave",        w.getDamagePerWave());
        s.set("speed-per-wave",         w.getSpeedPerWave());
        s.set("boss-interval",          w.getBossInterval());
        s.set("elite-interval",         w.getEliteInterval());
        s.set("special-interval",       w.getSpecialInterval());
        s.set("early-max-mob-types",    w.getEarlyGameMaxMobTypes());
        s.set("mid-max-mob-types",      w.getMidGameMaxMobTypes());
        s.set("late-max-mob-types",     w.getLateGameMaxMobTypes());
        s.set("mid-game-start-wave",    w.getMidGameStartWave());
        s.set("late-game-start-wave",   w.getLateGameStartWave());
    }

    // ---- WaveCategoryTemplate ----

    private WaveCategoryTemplate readTemplate(WaveCategory cat, ConfigurationSection s) {
        WaveCategoryTemplate t = new WaveCategoryTemplate(cat);
        t.setMobPoolId(s.getString("pool-id"));
        String mode = s.getString("composition-mode");
        if (mode != null) { try { t.setCompositionMode(CompositionMode.valueOf(mode.toUpperCase())); } catch (IllegalArgumentException ignored) {} }
        t.setCategoryBudgetMultiplier(s.getDouble("budget-multiplier", 1.0));
        t.setHealthMultiplier(s.getDouble("health", 1.0));
        t.setDamageMultiplier(s.getDouble("damage", 1.0));
        t.setSpeedMultiplier(s.getDouble("speed", 1.0));
        t.setMaxMobTypes(s.getInt("max-mob-types", 3));
        t.setDefaultMobCount(s.getInt("default-mob-count", 10));
        t.setEnabled(s.getBoolean("enabled", true));
        return t;
    }

    private void writeTemplate(WaveCategoryTemplate t, ConfigurationSection s) {
        s.set("pool-id",            t.getMobPoolId());
        s.set("composition-mode",   t.getCompositionMode().name());
        s.set("budget-multiplier",  t.getCategoryBudgetMultiplier());
        s.set("health",             t.getHealthMultiplier());
        s.set("damage",             t.getDamageMultiplier());
        s.set("speed",              t.getSpeedMultiplier());
        s.set("max-mob-types",      t.getMaxMobTypes());
        s.set("default-mob-count",  t.getDefaultMobCount());
        s.set("enabled",            t.isEnabled());
    }

    // ---- MobPool ----

    private MobPool readPool(String id, ConfigurationSection s) {
        MobPool pool = new MobPool(id, s.getString("display-name", id));
        String defCat = s.getString("default-category");
        if (defCat != null) { try { pool.setDefaultCategory(WaveCategory.valueOf(defCat.toUpperCase())); } catch (IllegalArgumentException ignored) {} }
        pool.setEnabled(s.getBoolean("enabled", true));

        ConfigurationSection entriesSec = s.getConfigurationSection("entries");
        if (entriesSec != null) {
            for (String entryId : entriesSec.getKeys(false)) {
                ConfigurationSection es = entriesSec.getConfigurationSection(entryId);
                if (es != null) pool.getEntries().add(readEntry(entryId, es));
            }
        }
        return pool;
    }

    private void writePool(MobPool pool, ConfigurationSection s) {
        s.set("display-name", pool.getDisplayName());
        if (pool.getDefaultCategory() != null) s.set("default-category", pool.getDefaultCategory().name());
        s.set("enabled", pool.isEnabled());
        ConfigurationSection entriesSec = s.createSection("entries");
        for (MobEntry e : pool.getEntries()) {
            writeEntry(e, entriesSec.createSection(e.getId()));
        }
    }

    // ---- MobEntry ----

    private MobEntry readEntry(String id, ConfigurationSection s) {
        MobEntry e = new MobEntry();
        e.setId(id);
        String src = s.getString("source", "VANILLA");
        try { e.setSource(MobSource.valueOf(src.toUpperCase())); } catch (IllegalArgumentException ex) { e.setSource(MobSource.VANILLA); }
        e.setDisplayName(s.getString("display-name", id));
        e.setWeight(s.getInt("weight", 10));
        e.setThreat(s.getDouble("threat", 5.0));
        e.setMinAmount(s.getInt("min-amount", 1));
        e.setMaxAmount(s.getInt("max-amount", 3));
        e.setMinWave(s.getInt("min-wave", 1));
        e.setMaxWave(s.getInt("max-wave", 0));
        e.setEnabled(s.getBoolean("enabled", true));
        return e;
    }

    private void writeEntry(MobEntry e, ConfigurationSection s) {
        s.set("source",       e.getSource().name());
        s.set("display-name", e.getDisplayName());
        s.set("weight",       e.getWeight());
        s.set("threat",       e.getThreat());
        s.set("min-amount",   e.getMinAmount());
        s.set("max-amount",   e.getMaxAmount());
        s.set("min-wave",     e.getMinWave());
        s.set("max-wave",     e.getMaxWave());
        s.set("enabled",      e.isEnabled());
    }

    // ---- WaveOverride ----

    private WaveOverride readOverride(int waveIndex, ConfigurationSection s) {
        WaveOverride o = new WaveOverride(waveIndex);
        o.setEnabled(s.getBoolean("enabled", true));
        String cat = s.getString("forced-category");
        if (cat != null) { try { o.setForcedCategory(WaveCategory.valueOf(cat.toUpperCase())); } catch (IllegalArgumentException ignored) {} }
        o.setForcedPoolId(s.getString("forced-pool-id"));
        String mode = s.getString("forced-composition-mode");
        if (mode != null) { try { o.setForcedCompositionMode(CompositionMode.valueOf(mode.toUpperCase())); } catch (IllegalArgumentException ignored) {} }
        if (s.contains("health-override")) o.setHealthMultiplierOverride(s.getDouble("health-override"));
        if (s.contains("damage-override")) o.setDamageMultiplierOverride(s.getDouble("damage-override"));
        if (s.contains("speed-override"))  o.setSpeedMultiplierOverride(s.getDouble("speed-override"));
        return o;
    }

    private void writeOverride(WaveOverride o, ConfigurationSection s) {
        s.set("enabled", o.isEnabled());
        if (o.getForcedCategory() != null)        s.set("forced-category", o.getForcedCategory().name());
        if (o.getForcedPoolId() != null)           s.set("forced-pool-id",  o.getForcedPoolId());
        if (o.getForcedCompositionMode() != null)  s.set("forced-composition-mode", o.getForcedCompositionMode().name());
        if (o.getHealthMultiplierOverride() != null) s.set("health-override", o.getHealthMultiplierOverride());
        if (o.getDamageMultiplierOverride() != null) s.set("damage-override", o.getDamageMultiplierOverride());
        if (o.getSpeedMultiplierOverride() != null)  s.set("speed-override",  o.getSpeedMultiplierOverride());
    }
}
