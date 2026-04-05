package fr.obsidianlabs.plugins.villagedefense.wave;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaState;
import fr.obsidianlabs.plugins.villagedefense.compat.VersionCompat;
import fr.obsidianlabs.plugins.villagedefense.gameplay.model.ArenaGameplaySettings;
import fr.obsidianlabs.plugins.villagedefense.gameplay.model.GameDifficulty;
import fr.obsidianlabs.plugins.villagedefense.gameplay.model.WaveComposition;
import fr.obsidianlabs.plugins.villagedefense.gameplay.model.WaveDifficultyProfile;
import fr.obsidianlabs.plugins.villagedefense.util.ChatUtil;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.entity.Zombie;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.UUID;

public class WaveManager {

    public static final String MOB_ARENA_META = "vd_arena";
    public static final String MOB_DAMAGE_META = "vd_damage_mult";

    private final VillageDefensePlugin plugin;

    public WaveManager(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Démarre la prochaine vague pour une arène.
     * Appelé depuis startGame (premier démarrage) : affiche le décompte 5→1 en titre.
     */
    public void startNextWave(Arena arena) {
        startNextWave(arena, false);
    }

    /**
     * @param skipCountdown true quand le décompte a déjà été affiché dans l'inter-vague
     *                      (tickBetweenWaves) — spawn immédiat sans délai supplémentaire.
     */
    public void startNextWave(Arena arena, boolean skipCountdown) {
        int waveNum = arena.getCurrentWave() + 1;
        arena.setCurrentWave(waveNum);
        arena.setState(ArenaState.RUNNING);

        Wave wave = buildWave(waveNum);
        broadcastWaveStart(arena, wave);   // sons uniquement (pas de message chat)

        if (skipCountdown) {
            // Le compte-à-rebours était dans l'inter-vague — on spawn directement
            spawnWave(arena, wave);
        } else {
            // Premier lancement : décompte 5 → 1 en titre puis spawn
            for (int i = 5; i >= 1; i--) {
                final int count = i;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    for (Player p : arena.getPlayers()) {
                        ChatUtil.sendTitle(
                            p,
                            "&c&l" + count,
                            plugin.getMessages().format(p, "game.countdown-subtitle", waveNum),
                            2, 18, 2
                        );
                    }
                }, (long)(5 - i) * 20L);
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (arena.getState() == ArenaState.RUNNING) spawnWave(arena, wave);
            }, 5 * 20L);
        }
    }

    /**
     * Construit une vague selon son numéro.
     */
    public Wave buildWave(int waveNum) {
        int specialInterval = plugin.getConfig().getInt("waves.special-wave-interval", 5);
        int bossInterval = plugin.getConfig().getInt("waves.boss-wave-interval", 10);

        // Boss wave ?
        if (waveNum % bossInterval == 0) {
            return buildBossWave(waveNum);
        }

        // Vague spéciale ?
        if (waveNum % specialInterval == 0) {
            WaveType[] specials = {WaveType.WOLVES, WaveType.SPIDERS, WaveType.CREEPERS, WaveType.WITCHES};
            WaveType type = specials[(waveNum / specialInterval - 1) % specials.length];
            return buildSpecialWave(waveNum, type);
        }

        return buildNormalWave(waveNum);
    }

    private Wave buildNormalWave(int waveNum) {
        Wave wave = new Wave(waveNum, WaveType.NORMAL);
        double diffMult = plugin.getConfig().getDouble("game.difficulty-multiplier", 1.0);
        int baseMobs = plugin.getConfig().getInt("waves.base-mob-count", 5);
        int increment = plugin.getConfig().getInt("waves.mob-count-increment", 3);
        double baseHp = plugin.getConfig().getDouble("waves.base-zombie-hp", 20.0);
        double hpIncrement = plugin.getConfig().getDouble("waves.hp-increment-per-wave", 5.0);

        int count = (int)((baseMobs + (waveNum - 1) * increment) * diffMult);
        double hpMult = (baseHp + (waveNum - 1) * hpIncrement) / 20.0;

        // Manches normales : zombies uniquement
        wave.addEntry(EntityType.ZOMBIE, count, hpMult, 1.0 + (waveNum * 0.05));

        return wave;
    }

    private Wave buildSpecialWave(int waveNum, WaveType type) {
        Wave wave = new Wave(waveNum, type);
        double diffMult = plugin.getConfig().getDouble("game.difficulty-multiplier", 1.0);
        double hpMult = 1.0 + (waveNum * 0.3);
        int count = (int)((8 + waveNum * 2) * diffMult);

        switch (type) {
            case WOLVES -> wave.addEntry(EntityType.WOLF, count, hpMult, 1.5);
            case SPIDERS -> {
                wave.addEntry(EntityType.SPIDER, (int)(count * 0.6), hpMult, 1.2);
                wave.addEntry(EntityType.SKELETON, (int)(count * 0.4), hpMult * 0.8, 1.0);
            }
            case CREEPERS -> wave.addEntry(EntityType.CREEPER, count / 2, hpMult, 1.0);
            case WITCHES -> wave.addEntry(EntityType.WITCH, count / 3, hpMult * 1.5, 1.5);
            default -> wave.addEntry(EntityType.ZOMBIE, count, hpMult, 1.0);
        }

        return wave;
    }

    private Wave buildBossWave(int waveNum) {
        Wave wave = new Wave(waveNum, WaveType.BOSS);
        int bossInterval = plugin.getConfig().getInt("waves.boss-wave-interval", 10);
        double hpMult = 5.0 + waveNum * 0.5;
        wave.addEntry(EntityType.RAVAGER, 1, hpMult, 2.0);

        // Première vague de boss : boss seul pour un affrontement épique
        // Vagues de boss suivantes : escorte de zombies croissante
        int bossCount = waveNum / bossInterval; // 1 = 1er boss, 2 = 2e boss, ...
        if (bossCount > 1) {
            int zombieCount = (bossCount - 1) * 5; // 5 au 2e boss, 10 au 3e, 15 au 4e...
            double zombieHpMult = 2.0 + bossCount * 0.2;
            wave.addEntry(EntityType.ZOMBIE, zombieCount, zombieHpMult, 1.2);
        }

        return wave;
    }

    /**
     * Dispatche vers le système avancé ou le système legacy selon la config de l'arène.
     */
    private void spawnWave(Arena arena, Wave wave) {
        ArenaGameplaySettings gameplaySettings =
            plugin.getArenaGameplayService().getSettings(arena.getId());

        if (gameplaySettings.getGeneralSettings().isUseAdvancedWaveSystem()) {
            spawnWaveAdvanced(arena, wave.getWaveNumber());
            return;
        }
        spawnWaveLegacy(arena, wave);
    }

    /**
     * Nouveau système : génère la composition via ArenaGameplayService puis spawne.
     */
    private void spawnWaveAdvanced(Arena arena, int waveNum) {
        GameDifficulty difficulty = arena.getSelectedDifficulty();

        WaveComposition composition = plugin.getArenaGameplayService().buildWave(
            arena.getId(),
            waveNum,
            difficulty,
            plugin.getWaveCategoryResolver(),
            plugin.getWaveDifficultyCalculator(),
            plugin.getWaveComposer()
        );

        if (composition.getSelections().isEmpty()) {
            plugin.getLogger().warning("[WaveManager] Composition vide pour la vague "
                + waveNum + " (arène " + arena.getId() + "). Vague annulée.");
            arena.startWaveProgress(0);
            return;
        }

        WaveDifficultyProfile profile = plugin.getWaveDifficultyCalculator().calculate(
            waveNum,
            difficulty,
            composition.getCategory(),
            plugin.getArenaGameplayService().getSettings(arena.getId())
        );

        plugin.getMobSpawnService().spawnWaveComposition(arena, composition, profile);
    }

    /**
     * Spawne tous les monstres d'une vague avec un délai entre chaque (système legacy).
     */
    private void spawnWaveLegacy(Arena arena, Wave wave) {
        int spawnDelay = plugin.getConfig().getInt("waves.spawn-delay", 10);
        List<Wave.WaveEntry> entries = wave.getEntries();
        final int[] entryIndex = {0};
        final int[] countInEntry = {0};

        arena.startWaveProgress(wave.getTotalMobCount());

        BukkitTask[] taskHolder = new BukkitTask[1];

        taskHolder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Annuler dès que la vague est terminée (BETWEEN_WAVES) ou la partie finie (ENDING)
            if (arena.getState() == ArenaState.ENDING
                    || arena.getState() == ArenaState.BETWEEN_WAVES) {
                taskHolder[0].cancel();
                return;
            }

            if (entryIndex[0] >= entries.size()) {
                taskHolder[0].cancel();
                return;
            }

            Wave.WaveEntry entry = entries.get(entryIndex[0]);

            if (countInEntry[0] < entry.count()) {
                Location spawnLoc = arena.getRandomSpawnPoint();
                if (spawnLoc != null) {
                    if (!spawnMob(arena, entry, spawnLoc)) {
                        arena.markWaveMobSkipped();
                    }
                } else {
                    arena.markWaveMobSkipped();
                }
                countInEntry[0]++;
            } else {
                entryIndex[0]++;
                countInEntry[0] = 0;
            }
        }, 0L, spawnDelay);
    }

    private boolean spawnMob(Arena arena, Wave.WaveEntry entry, Location loc) {
        World world = loc.getWorld();
        if (world == null) return false;

        Entity entity = world.spawnEntity(loc, entry.entityType());
        if (!(entity instanceof LivingEntity mob)) return false;

        // Tag de l'arène
        mob.setMetadata(MOB_ARENA_META, new FixedMetadataValue(plugin, arena.getId()));
        mob.getPersistentDataContainer().set(
            plugin.getKey(MOB_ARENA_META), PersistentDataType.STRING, arena.getId()
        );

        // HP customisés (plafonné à 2048 — limite Minecraft)
        var maxHpAttr = mob.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHpAttr != null) {
            double newHp = Math.min(maxHpAttr.getBaseValue() * entry.hpMultiplier(), 2048.0);
            maxHpAttr.setBaseValue(newHp);
            mob.setHealth(newHp);
        }

        // Dégâts customisés
        var dmgAttr = mob.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (dmgAttr != null && entry.damageMultiplier() != 1.0) {
            dmgAttr.setBaseValue(dmgAttr.getBaseValue() * entry.damageMultiplier());
        }

        // Augmenter la portée de détection pour que les mobs "voient" partout dans l'arène
        var followRange = mob.getAttribute(Attribute.GENERIC_FOLLOW_RANGE);
        if (followRange != null) followRange.setBaseValue(128.0);

        // Vitesse progressive avec les vagues (cap à 0.45)
        var speedAttr = mob.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null && arena.getCurrentWave() > 1) {
            double speedBonus = Math.min(0.01 * (arena.getCurrentWave() - 1), 0.22);
            speedAttr.setBaseValue(speedAttr.getBaseValue() + speedBonus);
        }

        // Configuration spéciale par type
        configureSpecialMob(mob, entry.entityType(), arena);

        // Custom name — type only, no HP bar
        String typeName = entity.getType().name().charAt(0)
                + entity.getType().name().substring(1).toLowerCase();
        mob.setCustomName(ChatUtil.color("&c" + typeName));
        mob.setCustomNameVisible(true);

        arena.markWaveMobSpawned(entity.getUniqueId());

        // Boss bar : notifier le GameManager dès qu'un boss spawne
        if (entry.entityType() == org.bukkit.entity.EntityType.RAVAGER
                || entry.entityType() == org.bukkit.entity.EntityType.ELDER_GUARDIAN
                || entry.entityType() == org.bukkit.entity.EntityType.WITHER) {
            plugin.getGameManager().registerBossEntity(arena, mob);
        }

        // Enregistrement dans le guide waypoint si un chemin est assigné à ce spawner
        plugin.getWaypointGuideManager().registerMob(arena, mob, loc);

        // Forcer une cible initiale 2 ticks après le spawn (laisse l'IA s'initialiser)
        // Uniquement si le mob n'est pas déjà guidé par waypoints
        final LivingEntity spawnedMob = mob;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!spawnedMob.isValid() || spawnedMob.isDead()) return;
            if (!(spawnedMob instanceof org.bukkit.entity.Creature creature)) return;
            if (plugin.getWaypointGuideManager().isMobRegistered(spawnedMob.getUniqueId())) return;
            org.bukkit.entity.Villager v = plugin.getGameManager().findNearestVillager(arena, spawnedMob.getLocation());
            if (v != null) creature.setTarget(v);
        }, 2L);

        return true;
    }

    private void configureSpecialMob(LivingEntity mob, org.bukkit.entity.EntityType type, Arena arena) {
        switch (type) {
            case WOLF -> {
                Wolf wolf = (Wolf) mob;
                wolf.setAngry(true);
                // Target les joueurs de l'arène
                Player target = arena.getPlayers().stream().findFirst().orElse(null);
                if (target != null) wolf.setTarget(target);
            }
            case ZOMBIE -> applyZombieScaling((Zombie) mob, arena.getCurrentWave());
            case CREEPER -> {
                Creeper creeper = (Creeper) mob;
                if (arena.getCurrentWave() >= 15) creeper.setPowered(true);
            }
            default -> {}
        }
    }

    /**
     * Applique équipement et dégâts progressifs aux zombies selon le numéro de vague.
     * Vague  1-4  : pas d'équipement.
     * Vague  5-9  : casque cuir, 30 % chance d'épée en pierre.
     * Vague 10-14 : casque fer, épée en pierre.
     * Vague 15-19 : casque + plastron fer, épée en fer.
     * Vague 20+   : casque + plastron + jambières fer, épée en fer enchantée.
     */
    private void applyZombieScaling(Zombie zombie, int wave) {
        var eq = zombie.getEquipment();
        if (eq == null) return;

        // Drop chance nul pour tout l'équipement
        eq.setHelmetDropChance(0f);
        eq.setChestplateDropChance(0f);
        eq.setLeggingsDropChance(0f);
        eq.setItemInMainHandDropChance(0f);

        if (wave >= 5) {
            eq.setHelmet(new org.bukkit.inventory.ItemStack(
                wave >= 10 ? Material.IRON_HELMET : Material.LEATHER_HELMET));
        }
        if (wave >= 10 && Math.random() < 0.5) {
            eq.setItemInMainHand(new org.bukkit.inventory.ItemStack(Material.STONE_SWORD));
        }
        if (wave >= 15) {
            eq.setChestplate(new org.bukkit.inventory.ItemStack(Material.IRON_CHESTPLATE));
            eq.setItemInMainHand(new org.bukkit.inventory.ItemStack(Material.IRON_SWORD));
        }
        if (wave >= 20) {
            eq.setLeggings(new org.bukkit.inventory.ItemStack(Material.IRON_LEGGINGS));
            org.bukkit.inventory.ItemStack sword = new org.bukkit.inventory.ItemStack(Material.IRON_SWORD);
            sword.addEnchantment(VersionCompat.SHARPNESS, Math.min(1 + (wave - 20) / 5, 3));
            eq.setItemInMainHand(sword);
        }
    }

    private void broadcastWaveStart(Arena arena, Wave wave) {
        // Sons uniquement — pas de message chat (le scoreboard et les titres suffisent)
        for (Player p : arena.getPlayers()) {
            if (wave.getType() == WaveType.BOSS) {
                // Son grave mais pas agressif pour les boss
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_AMBIENT, 0.6f, 0.8f);
            } else if (wave.getType() != WaveType.NORMAL) {
                // Vague spéciale : son discret
                p.playSound(p.getLocation(), Sound.ENTITY_BLAZE_AMBIENT, 0.4f, 0.9f);
            } else {
                // Vague normale : son zombie très doux
                p.playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT, 0.4f, 0.8f);
            }
        }
    }

    /**
     * Appelé quand un mob de l'arène est tué. Retourne si tous les mobs sont morts.
     */
    public boolean onMobDeath(Arena arena, Entity entity) {
        plugin.getWaypointGuideManager().unregisterMob(entity.getUniqueId());
        arena.markWaveMobResolved(entity.getUniqueId());
        return arena.isWaveResolved();
    }

    /**
     * Nettoie tous les mobs actifs d'une arène (fin de partie).
     */
    public void killAllMobs(Arena arena) {
        World world = null;
        if (!arena.getMobSpawnPoints().isEmpty()) {
            world = arena.getMobSpawnPoints().get(0).getWorld();
        }
        if (world == null) return;

        for (UUID uid : List.copyOf(arena.getActiveMobUUIDs())) {
            Entity e = Bukkit.getEntity(uid);
            if (e != null && e.isValid()) e.remove();
            plugin.getWaypointGuideManager().unregisterMob(uid);
            arena.markWaveMobResolved(uid);
        }
    }
}
