package fr.obsidianlabs.plugins.villagedefense.listener;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.api.VillageDefenseApiIds;
import fr.obsidianlabs.plugins.villagedefense.api.VillageDefenseProgressType;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaState;
import fr.obsidianlabs.plugins.villagedefense.compat.VersionCompat;
import fr.obsidianlabs.plugins.villagedefense.game.GameManager;
import fr.obsidianlabs.plugins.villagedefense.player.VDPlayer;
import fr.obsidianlabs.plugins.villagedefense.wave.WaveManager;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;



public class ArenaListener implements Listener {

    private final VillageDefensePlugin plugin;

    public ArenaListener(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    // ---- Casser des blocs interdit en arène ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (plugin.getGameManager().isArenaObserver(player)) {
            event.setCancelled(true);
            return;
        }

        Arena arena = plugin.getArenaManager().getArenaOfPlayer(player);
        if (arena == null) return;
        if (arena.getState() == ArenaState.RUNNING || arena.getState() == ArenaState.BETWEEN_WAVES) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (plugin.getGameManager().isArenaObserver(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    // ---- Mort d'entité ----

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();

        // --- Mort d'un villageois du village ---
        if (entity instanceof Villager villager) {
            Arena arena = findArenaByVillager(villager);
            if (arena != null) {
                event.getDrops().clear();
                event.setDroppedExp(0);
                plugin.getGameManager().onVillagerDeath(arena, villager);
                return;
            }
        }

        // --- Mort d'un mob ennemi ---
        Arena arena = findArenaByMob(entity);
        if (arena == null) {
            // Fallback : entité dans les bornes d'une arène active (compagnon, mob parasite…)
            clearDropsIfInArena(event, entity);
            return;
        }

        event.getDrops().clear();
        event.setDroppedExp(0);

        // Récompenses au tueur
        Player killer = entity.getKiller();
        if (killer != null && arena.hasPlayer(killer)) {
            long reward = getMobReward(entity, arena.getCurrentWave());
            long xp     = getMobXp(entity);
            VDPlayer vdp = plugin.getPlayerManager().getPlayer(killer);
            if (vdp != null) {
                vdp.addCoins(reward);
                vdp.addKill();
                if (plugin.getDatabase() != null) plugin.getDatabase().recordArenaKill(killer.getUniqueId(), arena.getId());
                plugin.fireProgress(
                    killer,
                    arena,
                    VillageDefenseProgressType.MOB_KILL,
                    1,
                    vdp.getTotalKills(),
                    VillageDefenseApiIds.of(entity.getType())
                );
                plugin.sendActionBar(killer, plugin.getMessages().format(killer, "player.coins-gained", reward));
                plugin.getPlayerManager().awardXp(killer, xp);
                plugin.getPlayerManager().checkAchievements(killer, vdp);
            }
        }

        // Tenter de faire apparaître un power-up
        plugin.getPowerUpManager().tryDropPowerUp(arena, entity);

        // Notifier le wave manager → vague complète ?
        boolean waveComplete = plugin.getWaveManager().onMobDeath(arena, entity);
        if (waveComplete && arena.getState() == ArenaState.RUNNING) {
            plugin.getGameManager().onWaveCompleted(arena);
        }
    }

    private void clearDropsIfInArena(EntityDeathEvent event, LivingEntity entity) {
        for (Arena a : plugin.getArenaManager().getAllArenas()) {
            if ((a.getState() == ArenaState.RUNNING || a.getState() == ArenaState.BETWEEN_WAVES)
                    && a.hasBounds() && isInsideBounds(a, entity.getLocation())) {
                event.getDrops().clear();
                event.setDroppedExp(0);
                return;
            }
        }
    }

    // ---- Dégâts entre entités ----

    // HIGHEST : s'exécute après OCM (HIGH) pour que PvP/friendly-fire/power-ups
    // s'appuient sur les dégâts déjà ajustés par OldCombatMechanics.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Résoudre le vrai damager (flèches / projectiles → tireur)
        Entity effectiveDamager = event.getDamager();
        if (effectiveDamager instanceof org.bukkit.entity.Projectile proj
                && proj.getShooter() instanceof Entity src) {
            effectiveDamager = src;
        }

        // PvP désactivé (corps à corps ET projectiles)
        if (effectiveDamager instanceof Player damager && event.getEntity() instanceof Player victim) {
            Arena arena = plugin.getArenaManager().getArenaOfPlayer(damager);
            if (arena != null && arena.hasPlayer(victim)) {
                event.setCancelled(true);
                return;
            }
        }

        // Les joueurs ne peuvent pas blesser les compagnons (propres ou des coéquipiers)
        if (effectiveDamager instanceof Player attacker
                && event.getEntity().hasMetadata(fr.obsidianlabs.plugins.villagedefense.perk.PerkManager.COMPANION_META)) {
            Arena arena = plugin.getArenaManager().getArenaOfPlayer(attacker);
            if (arena != null) {
                event.setCancelled(true);
                return;
            }
        }

        // Alliés ne peuvent pas se blesser mutuellement
        // (compagnons ↔ compagnons, compagnons ↔ villageois, villageois ↔ villageois)
        if (isAllyEntity(effectiveDamager) && isAllyEntity(event.getEntity())) {
            event.setCancelled(true);
            return;
        }

        // Les flèches des tours ne blessent pas les alliés (joueurs, villageois, golems, compagnons)
        if (event.getDamager() instanceof Arrow arrow && arrow.hasMetadata("vd_tower_arrow")) {
            if (isFriendly(event.getEntity())) {
                event.setCancelled(true);
                return;
            }
        }

        // Le TNT du trébuchet ne blesse pas les alliés
        // (les dégâts aux mobs sont appliqués manuellement dans onEntityExplode)
        if (event.getDamager() instanceof TNTPrimed tnt && tnt.hasMetadata("vd_trebuchet")) {
            if (isFriendly(event.getEntity())) {
                event.setCancelled(true);
                return;
            }
        }

        if (event.getEntity() instanceof Player victim) {
            Arena arena = plugin.getArenaManager().getArenaOfPlayer(victim);
            if (arena != null && plugin.getKitAbilityManager() != null && isEnemyDamager(effectiveDamager, arena)) {
                event.setDamage(plugin.getKitAbilityManager().adjustIncomingDamage(victim, event.getDamage()));
            }
        }

        // Les joueurs ne peuvent pas blesser les villageois de leur arène
        if (event.getDamager() instanceof Player attacker && event.getEntity() instanceof Villager villager) {
            Arena arena = plugin.getArenaManager().getArenaOfPlayer(attacker);
            if (arena != null && arena.isVillager(villager.getUniqueId())) {
                event.setCancelled(true);
                attacker.sendMessage(plugin.getMessages().get("village.no-friendly-fire"));
                return;
            }
        }

        // Power-ups INSTANT_KILL et DOUBLE_DAMAGE
        if (event.getDamager() instanceof Player attacker
                && event.getEntity() instanceof LivingEntity target) {
            Arena arena = plugin.getArenaManager().getArenaOfPlayer(attacker);
            if (arena != null && arena.getActiveMobUUIDs().contains(target.getUniqueId())) {
                double mult = plugin.getPowerUpManager().getDamageMultiplier(attacker);
                if (mult >= 999.0) {
                    event.setDamage(target.getHealth() + 1000.0); // one-shot
                } else if (mult > 1.0) {
                    event.setDamage(event.getDamage() * mult);
                }
                if (plugin.getKitAbilityManager() != null) {
                    double adjusted = plugin.getKitAbilityManager()
                        .adjustOutgoingDamage(attacker, target, event.getDamager(), event.getDamage());
                    event.setDamage(adjusted);
                    plugin.getKitAbilityManager().afterPlayerHit(attacker, target, event.getDamager(), adjusted);
                }
            }
        }
    }

    // ---- Explosions : pas de dégâts aux blocs en arène ----

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();

        // TNT du Trébuchet : dégâts manuels, aucun bloc détruit
        if (entity.hasMetadata("vd_trebuchet")) {
            event.blockList().clear();

            double damage = entity.getMetadata("vd_tower_damage").isEmpty() ? 30.0
                : entity.getMetadata("vd_tower_damage").get(0).asDouble();

            String arenaId = entity.getMetadata("vd_trebuchet").get(0).asString();
            Arena arena = plugin.getArenaManager().getArena(arenaId);
            if (arena == null) return;

            Location impactLoc = entity.getLocation().clone();
            boolean isIncendiary = entity.hasMetadata("vd_incendiary_tnt");
            boolean isCluster    = entity.hasMetadata("vd_cluster_tnt");

            double radiusSq = 25.0;
            for (UUID uid : new java.util.ArrayList<>(arena.getActiveMobUUIDs())) {
                Entity mob = org.bukkit.Bukkit.getEntity(uid);
                if (mob instanceof LivingEntity living && mob.isValid()) {
                    if (living.getLocation().distanceSquared(impactLoc) <= radiusSq) {
                        living.damage(damage);
                        if (isIncendiary) living.setFireTicks(4 * 20);
                    }
                }
            }

            // CLUSTER : 3 mini-explosions décalées autour de l'impact
            if (isCluster) {
                java.util.Random rng = new java.util.Random();
                for (int i = 1; i <= 3; i++) {
                    double ox = (rng.nextDouble() - 0.5) * 6;
                    double oz = (rng.nextDouble() - 0.5) * 6;
                    Location miniLoc = impactLoc.clone().add(ox, 0, oz);
                    double miniDmg   = damage * 0.35;
                    double miniRadSq = 9.0; // 3 blocs
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        miniLoc.getWorld().spawnParticle(
                            VersionCompat.EXPLOSION, miniLoc, 5, 0.3, 0.3, 0.3, 0.1);
                        for (UUID uid2 : new java.util.ArrayList<>(arena.getActiveMobUUIDs())) {
                            Entity mob2 = org.bukkit.Bukkit.getEntity(uid2);
                            if (mob2 instanceof LivingEntity living2 && mob2.isValid()) {
                                if (living2.getLocation().distanceSquared(miniLoc) <= miniRadSq)
                                    living2.damage(miniDmg);
                            }
                        }
                    }, (long) i * 5);
                }
            }
            return;
        }

        // Creepers ou autre entité explosive dans une arène active → pas de dégâts aux blocs
        for (Arena arena : plugin.getArenaManager().getAllArenas()) {
            if (isInActiveArena(arena, entity)) {
                event.blockList().clear();
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(org.bukkit.event.block.BlockExplodeEvent event) {
        for (Arena arena : plugin.getArenaManager().getAllArenas()) {
            if ((arena.getState() == ArenaState.RUNNING || arena.getState() == ArenaState.BETWEEN_WAVES)
                && arena.getLobbyLocation() != null
                && arena.getLobbyLocation().getWorld().equals(event.getBlock().getWorld())) {
                event.blockList().clear();
                return;
            }
        }
    }

    // ---- Dégâts de chute dans le lobby ----

    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (plugin.getGameManager().isArenaObserver(player)) {
            event.setCancelled(true);
            return;
        }
        Arena arena = plugin.getArenaManager().getArenaOfPlayer(player);
        if (arena == null) return;
        if ((arena.getState() == ArenaState.WAITING || arena.getState() == ArenaState.STARTING)
            && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onObserverFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (plugin.getGameManager().isArenaObserver(player)) {
            event.setCancelled(true);
        }
    }

    // ---- Bloquer les spawns naturels dans l'arène ----

    /**
     * Annule tout spawn non-custom (naturel, chunk, œuf…) à l'intérieur des bornes
     * d'une arène active, pour éviter que des mobs parasites perturbent la partie.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) return;

        org.bukkit.Location loc = event.getLocation();
        for (Arena arena : plugin.getArenaManager().getAllArenas()) {
            if (arena.getState() != ArenaState.RUNNING && arena.getState() != ArenaState.BETWEEN_WAVES) continue;
            if (!arena.hasBounds()) continue;
            if (isInsideBounds(arena, loc)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ---- Villageois : ciblage par les mobs ----

    /**
     * À chaque événement de ciblage d'un mob de l'arène :
     * 85 % → villageois le plus proche, 15 % → joueur le plus proche.
     * La tâche de reciblage dans GameManager complète ce mécanisme pour les mobs
     * dont l'IA ne déclenche pas cet événement (pathfinding bloqué, portée dépassée).
     */
    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        Entity mob = event.getEntity();

        // Ne jamais cibler les villageois de service (shop / enchanteur)
        Entity target = event.getTarget();
        if (target instanceof Villager v
                && (v.hasMetadata(fr.obsidianlabs.plugins.villagedefense.game.GameManager.SHOP_VILLAGER_META)
                 || v.hasMetadata(fr.obsidianlabs.plugins.villagedefense.game.GameManager.ENCHANT_VILLAGER_META))) {
            event.setCancelled(true);
            return;
        }

        Arena arena = findArenaByMob(mob);
        if (arena == null) return;

        if (mob instanceof Creature creature && plugin.getKitAbilityManager() != null) {
            Player tauntTarget = plugin.getKitAbilityManager().getTauntTarget(arena, creature);
            if (tauntTarget != null) {
                event.setTarget(tauntTarget);
                return;
            }
        }

        boolean toVillager = ThreadLocalRandom.current().nextInt(100) < 85;

        if (toVillager) {
            Villager v = plugin.getGameManager().findNearestVillager(arena, mob.getLocation());
            if (v != null) { event.setTarget(v); return; }
        }

        Player p = plugin.getGameManager().findNearestPlayer(arena, mob.getLocation());
        if (p != null) event.setTarget(p);
    }

    // ---- Flèches incendiaires (spécialisation ArcherTower) ----

    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) return;
        if (!arrow.hasMetadata("vd_fire_arrow")) return;

        Entity hitEntity = event.getHitEntity();
        if (hitEntity instanceof LivingEntity mob) {
            mob.setFireTicks(4 * 20); // 4 secondes de feu
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (plugin.getKitAbilityManager() != null) {
            plugin.getKitAbilityManager().prepareShot(player, event.getProjectile());
        }
    }

    // ---- Ramassage de power-up ----

    @EventHandler(priority = EventPriority.HIGH)
    public void onPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (plugin.getGameManager().isArenaObserver(player)) {
            event.setCancelled(true);
            return;
        }
        Arena arena = plugin.getArenaManager().getArenaOfPlayer(player);
        if (arena == null) return;

        boolean wasPowerUp = plugin.getPowerUpManager()
            .handlePickup(player, event.getItem().getItemStack());
        if (wasPowerUp) {
            event.setCancelled(true);
            event.getItem().remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onObserverDropItem(PlayerDropItemEvent event) {
        if (plugin.getGameManager().isArenaObserver(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    // ---- Helpers ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobCombust(EntityCombustEvent event) {
        if (event instanceof EntityCombustByEntityEvent) return; // flèches enflammées / Aspect de Feu
        Entity entity = event.getEntity();
        if (entity.hasMetadata(WaveManager.MOB_ARENA_META)) {
            event.setCancelled(true);
        }
    }

    private Arena findArenaByMob(Entity entity) {
        if (!entity.hasMetadata(WaveManager.MOB_ARENA_META)) return null;
        String arenaId = entity.getMetadata(WaveManager.MOB_ARENA_META).get(0).asString();
        return plugin.getArenaManager().getArena(arenaId);
    }

    private Arena findArenaByVillager(Villager villager) {
        if (!villager.hasMetadata(GameManager.VILLAGER_META)) return null;
        String arenaId = villager.getMetadata(GameManager.VILLAGER_META).get(0).asString();
        Arena arena = plugin.getArenaManager().getArena(arenaId);
        if (arena == null || !arena.isVillager(villager.getUniqueId())) return null;
        return arena;
    }

    private boolean isInsideBounds(Arena arena, org.bukkit.Location loc) {
        org.bukkit.Location p1 = arena.getPos1(), p2 = arena.getPos2();
        if (!loc.getWorld().equals(p1.getWorld())) return false;
        int minX = Math.min(p1.getBlockX(), p2.getBlockX()), maxX = Math.max(p1.getBlockX(), p2.getBlockX());
        int minY = Math.min(p1.getBlockY(), p2.getBlockY()), maxY = Math.max(p1.getBlockY(), p2.getBlockY());
        int minZ = Math.min(p1.getBlockZ(), p2.getBlockZ()), maxZ = Math.max(p1.getBlockZ(), p2.getBlockZ());
        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();
        return bx >= minX && bx <= maxX && by >= minY && by <= maxY && bz >= minZ && bz <= maxZ;
    }

    /**
     * Retourne true si l'entité est une entité alliée qui ne doit pas être
     * blessée par les attaques des tours (flèches, TNT) :
     * joueurs, villageois, golems de fer et compagnons (loups / golems d'atout).
     */
    private boolean isFriendly(Entity entity) {
        if (entity instanceof Player || entity instanceof Villager || entity instanceof IronGolem) return true;
        if (entity.hasMetadata(fr.obsidianlabs.plugins.villagedefense.perk.PerkManager.COMPANION_META)) return true;
        return false;
    }

    /**
     * Retourne true si l'entité est un allié au sens "peut être source de friendly fire"
     * (compagnons et villageois — les joueurs sont gérés séparément par le PvP check).
     */
    private boolean isAllyEntity(Entity entity) {
        return entity.hasMetadata(fr.obsidianlabs.plugins.villagedefense.perk.PerkManager.COMPANION_META)
            || entity.hasMetadata(fr.obsidianlabs.plugins.villagedefense.game.GameManager.VILLAGER_META)
            || entity.hasMetadata(fr.obsidianlabs.plugins.villagedefense.game.GameManager.SHOP_VILLAGER_META)
            || entity.hasMetadata(fr.obsidianlabs.plugins.villagedefense.game.GameManager.ENCHANT_VILLAGER_META);
    }

    private boolean isInActiveArena(Arena arena, Entity entity) {
        if (arena.getState() != ArenaState.RUNNING && arena.getState() != ArenaState.BETWEEN_WAVES) return false;
        if (arena.getLobbyLocation() == null) return false;
        return arena.getLobbyLocation().getWorld().equals(entity.getWorld());
    }

    private boolean isEnemyDamager(Entity entity, Arena arena) {
        return entity != null && arena.getActiveMobUUIDs().contains(entity.getUniqueId());
    }

    private long getMobReward(LivingEntity mob, int wave) {
        String key = switch (mob.getType()) {
            case ZOMBIE -> "zombie";
            case SKELETON -> "skeleton";
            case SPIDER -> "spider";
            case WOLF -> "wolf";
            case CREEPER -> "creeper";
            case WITCH -> "witch";
            case RAVAGER, ELDER_GUARDIAN, WITHER -> "boss";
            default -> "zombie";
        };
        long base = plugin.getConfig().getLong("coins." + key, 5);
        return Math.round(base * (1.0 + (wave - 1) * 0.10));
    }

    private long getMobXp(LivingEntity mob) {
        return switch (mob.getType()) {
            case ZOMBIE                           ->  8;
            case SKELETON, SPIDER                -> 12;
            case WOLF, CREEPER, WITCH            -> 15;
            case RAVAGER, ELDER_GUARDIAN, WITHER -> 60;
            default                              ->  8;
        };
    }
}
