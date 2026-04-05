package fr.obsidianlabs.plugins.villagedefense.gameplay.service;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.gameplay.model.*;
import fr.obsidianlabs.plugins.villagedefense.gameplay.provider.*;
import fr.obsidianlabs.plugins.villagedefense.util.ChatUtil;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Bukkit;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Spawne réellement les mobs d'une WaveComposition dans l'arène.
 * Réutilise le tracking existant (markWaveMobSpawned, waypointGuide, etc.)
 */
public class MobSpawnService {

    private final VillageDefensePlugin plugin;
    private final MobRegistryService registry;
    private final Logger logger;

    public MobSpawnService(VillageDefensePlugin plugin, MobRegistryService registry) {
        this.plugin   = plugin;
        this.registry = registry;
        this.logger   = plugin.getLogger();
    }

    public void spawnWaveComposition(Arena arena, WaveComposition composition, WaveDifficultyProfile profile) {
        List<WaveMobSelection> selections = composition.getSelections();
        if (selections.isEmpty()) {
            logger.warning("[MobSpawnService] Composition vide pour la vague " + composition.getWaveIndex());
            return;
        }

        int spawnDelay = plugin.getConfig().getInt("waves.spawn-delay", 10);

        // Aplatir toutes les sélections en une liste ordonnée de (entry, 1 mob)
        List<MobEntry> spawnQueue = selections.stream()
                .flatMap(sel -> java.util.Collections.nCopies(sel.getAmount(), sel.getMobEntry()).stream())
                .toList();

        arena.startWaveProgress(spawnQueue.size());

        WaveSpawnContext context = new WaveSpawnContext(
            arena.getId(),
            composition.getWaveIndex(),
            composition.getCategory(),
            profile.getHealthMultiplier(),
            profile.getDamageMultiplier(),
            profile.getSpeedMultiplier()
        );

        final int[] index = {0};
        BukkitTask[] taskHolder = new BukkitTask[1];

        taskHolder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            var state = arena.getState();
            if (state == fr.obsidianlabs.plugins.villagedefense.arena.ArenaState.ENDING
                    || state == fr.obsidianlabs.plugins.villagedefense.arena.ArenaState.BETWEEN_WAVES) {
                taskHolder[0].cancel();
                return;
            }

            if (index[0] >= spawnQueue.size()) {
                taskHolder[0].cancel();
                return;
            }

            MobEntry entry  = spawnQueue.get(index[0]);
            Location spawnLoc = arena.getRandomSpawnPoint();
            index[0]++;

            if (spawnLoc == null) {
                arena.markWaveMobSkipped();
                return;
            }

            MobSource source = entry.getSource();
            var providerOpt  = registry.getProvider(source);
            if (providerOpt.isEmpty()) {
                logger.warning("[MobSpawnService] Aucun provider pour la source " + source);
                arena.markWaveMobSkipped();
                return;
            }

            SpawnResult result = providerOpt.get().spawn(spawnLoc, entry, context);
            if (!result.isSuccess()) {
                logger.warning("[MobSpawnService] Spawn échoué : " + result.getErrorMessage());
                arena.markWaveMobSkipped();
                return;
            }

            UUID uid = result.getEntityId();
            arena.markWaveMobSpawned(uid);

            // Enregistrement waypoint + ciblage initial (délégué au WaveManager existant)
            var entity = Bukkit.getEntity(uid);
            if (entity instanceof LivingEntity mob) {
                // Nom custom
                String typeName = entry.getDisplayName() != null ? entry.getDisplayName() : entry.getId();
                mob.setCustomName(ChatUtil.color("&c" + typeName));
                mob.setCustomNameVisible(true);

                plugin.getWaypointGuideManager().registerMob(arena, mob, spawnLoc);

                // Boss bar
                if (composition.getCategory() == WaveCategory.BOSS) {
                    plugin.getGameManager().registerBossEntity(arena, mob);
                }

                // Ciblage différé
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!mob.isValid() || mob.isDead()) return;
                    if (!(mob instanceof org.bukkit.entity.Creature creature)) return;
                    if (plugin.getWaypointGuideManager().isMobRegistered(mob.getUniqueId())) return;
                    var villager = plugin.getGameManager().findNearestVillager(arena, mob.getLocation());
                    if (villager != null) creature.setTarget(villager);
                }, 2L);
            }
        }, 0L, spawnDelay);
    }
}
