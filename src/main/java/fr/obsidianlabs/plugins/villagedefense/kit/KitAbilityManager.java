package fr.obsidianlabs.plugins.villagedefense.kit;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaState;
import fr.obsidianlabs.plugins.villagedefense.compat.VersionCompat;
import fr.obsidianlabs.plugins.villagedefense.player.VDPlayer;
import fr.obsidianlabs.plugins.villagedefense.tower.Tower;
import fr.obsidianlabs.plugins.villagedefense.tower.TowerSlot;
import fr.obsidianlabs.plugins.villagedefense.trap.TrapSlot;
import fr.obsidianlabs.plugins.villagedefense.util.ChatUtil;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class KitAbilityManager {

    private static final double GUARDIAN_PASSIVE_REDUCTION = 0.85;
    private static final double GUARDIAN_SHIELD_WALL_REDUCTION = 0.55;
    private static final long GUARDIAN_SHIELD_WALL_DURATION_MS = 6_000L;
    private static final long GUARDIAN_SHIELD_WALL_COOLDOWN_MS = 24_000L;
    private static final long GUARDIAN_TAUNT_DURATION_MS = 6_000L;
    private static final long GUARDIAN_TAUNT_COOLDOWN_MS = 20_000L;
    private static final double GUARDIAN_TAUNT_RADIUS = 12.0;

    private static final long RANGER_SHOT_ARM_DURATION_MS = 8_000L;
    private static final long RANGER_CHARGED_COOLDOWN_MS = 16_000L;
    private static final long RANGER_PIERCING_COOLDOWN_MS = 20_000L;
    private static final double RANGER_CHARGED_MULTIPLIER = 2.0;
    private static final double RANGER_HEADSHOT_MULTIPLIER = 1.35;
    private static final double RANGER_PIERCING_DAMAGE_MULTIPLIER = 0.7;
    private static final double RANGER_PIERCING_DISTANCE = 10.0;
    private static final double RANGER_PIERCING_WIDTH = 1.6;

    private static final long BERSERKER_RAGE_DURATION_MS = 8_000L;
    private static final long BERSERKER_RAGE_COOLDOWN_MS = 18_000L;
    private static final long BERSERKER_LIFE_STEAL_DURATION_MS = 8_000L;
    private static final long BERSERKER_LIFE_STEAL_COOLDOWN_MS = 20_000L;
    private static final double BERSERKER_RAGE_MULTIPLIER = 1.45;
    private static final double BERSERKER_LOW_HP_BONUS = 0.40;
    private static final double BERSERKER_LIFE_STEAL_RATIO = 0.35;

    private static final double MEDIC_AURA_RADIUS = 8.0;
    private static final double MEDIC_HEAL_FIELD_RADIUS = 4.5;
    private static final double MEDIC_HEAL_FIELD_HEAL = 1.5;
    private static final long MEDIC_HEAL_FIELD_DURATION_MS = 15_000L;
    private static final long MEDIC_HEAL_PULSE_COOLDOWN_MS = 20_000L;
    private static final int BASE_RESPAWN_SECONDS = 30;

    private static final double ENGINEER_STRUCTURE_RADIUS = 10.0;
    private static final long ENGINEER_OVERCLOCK_DURATION_MS = 7_000L;
    private static final long ENGINEER_OVERCLOCK_COOLDOWN_MS = 24_000L;
    private static final long ENGINEER_REPAIR_PULSE_COOLDOWN_MS = 18_000L;
    private static final double ENGINEER_REPAIR_HEAL = 4.0;
    private static final double ENGINEER_DISCOUNT_MULTIPLIER = 0.80;
    private static final double ENGINEER_REINFORCED_DAMAGE_MULTIPLIER = 1.10;
    private static final double ENGINEER_REINFORCED_INTERVAL_MULTIPLIER = 0.95;
    private static final double ENGINEER_REINFORCED_TRAP_COOLDOWN_MULTIPLIER = 0.85;
    private static final double ENGINEER_OVERCLOCK_DAMAGE_MULTIPLIER = 1.30;
    private static final double ENGINEER_OVERCLOCK_INTERVAL_MULTIPLIER = 1.0 / 1.5;
    private static final double ENGINEER_OVERCLOCK_TRAP_COOLDOWN_MULTIPLIER = 0.70;

    private final VillageDefensePlugin plugin;
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Long> shieldWallEnds = new HashMap<>();
    private final Map<UUID, Long> rageEnds = new HashMap<>();
    private final Map<UUID, Long> lifeStealEnds = new HashMap<>();
    private final Map<UUID, Long> chargedShotArmed = new HashMap<>();
    private final Map<UUID, Long> piercingArrowArmed = new HashMap<>();
    private final Map<TowerSlot, Long> overclockedTowers = new HashMap<>();
    private final Map<TrapSlot, Long> overclockedTraps = new HashMap<>();
    private final Map<UUID, TauntState> tauntedMobs = new HashMap<>();
    private final List<MedicField> medicFields = new ArrayList<>();
    private BukkitTask passiveTask;

    private record TauntState(UUID playerId, long endAt) {}
    private record MedicField(UUID ownerId, String arenaId, Location center, long endAt) {}

    public KitAbilityManager(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        passiveTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickPassives, 20L, 20L);
    }

    public void shutdown() {
        if (passiveTask != null) {
            passiveTask.cancel();
        }
        cooldowns.clear();
        shieldWallEnds.clear();
        rageEnds.clear();
        lifeStealEnds.clear();
        chargedShotArmed.clear();
        piercingArrowArmed.clear();
        overclockedTowers.clear();
        overclockedTraps.clear();
        tauntedMobs.clear();
        medicFields.clear();
    }

    public boolean handleAbilityUse(Player player, org.bukkit.inventory.ItemStack item, Location targetLocation) {
        String abilityId = Kit.getAbilityId(plugin, item);
        if (abilityId == null) {
            return false;
        }

        Arena arena = plugin.getArenaManager().getArenaOfPlayer(player);
        if (!isCombatArena(arena)) {
            ChatUtil.send(player, plugin.getMessages().get("kit.ability.only-in-game"));
            return true;
        }

        return switch (abilityId) {
            case "guardian_shield_wall" -> activateGuardianShieldWall(player);
            case "guardian_taunt" -> activateGuardianTaunt(player, arena);
            case "ranger_charged_shot" -> armRangerShot(player, chargedShotArmed,
                "ranger_charged_shot", "kit.ability.ranger.charged-shot.name");
            case "ranger_piercing_arrow" -> armRangerShot(player, piercingArrowArmed,
                "ranger_piercing_arrow", "kit.ability.ranger.piercing-arrow.name");
            case "berserker_rage" -> activateBerserkerBuff(player, rageEnds,
                BERSERKER_RAGE_DURATION_MS, BERSERKER_RAGE_COOLDOWN_MS,
                "berserker_rage", "kit.ability.berserker.rage.name");
            case "berserker_life_steal" -> activateBerserkerBuff(player, lifeStealEnds,
                BERSERKER_LIFE_STEAL_DURATION_MS, BERSERKER_LIFE_STEAL_COOLDOWN_MS,
                "berserker_life_steal", "kit.ability.berserker.life-steal.name");
            case "medic_healing_pulse" -> activateMedicHealingPulse(player, arena, targetLocation);
            case "engineer_overclock" -> activateEngineerOverclock(player, arena);
            case "engineer_repair_pulse" -> activateEngineerRepairPulse(player, arena);
            default -> false;
        };
    }

    public double adjustIncomingDamage(Player player, double damage) {
        if (!hasKit(player, KitType.GUARDIAN)) {
            return damage;
        }

        double adjusted = damage * GUARDIAN_PASSIVE_REDUCTION;
        if (isActive(shieldWallEnds, player.getUniqueId())) {
            adjusted *= GUARDIAN_SHIELD_WALL_REDUCTION;
        }
        return adjusted;
    }

    public double adjustOutgoingDamage(Player player, LivingEntity target, Entity directDamager, double damage) {
        double adjusted = damage;

        if (hasKit(player, KitType.RANGER) && directDamager instanceof AbstractArrow arrow) {
            if (arrow.hasMetadata("vd_ranger_charged")) {
                adjusted *= RANGER_CHARGED_MULTIPLIER;
            }
            if (isHeadshot(target, arrow)) {
                adjusted *= RANGER_HEADSHOT_MULTIPLIER;
            }
        }

        if (hasKit(player, KitType.BERSERKER)) {
            if (isActive(rageEnds, player.getUniqueId())) {
                adjusted *= BERSERKER_RAGE_MULTIPLIER;
            }
            adjusted *= lowHealthDamageMultiplier(player);
        }

        return adjusted;
    }

    public void afterPlayerHit(Player player, LivingEntity target, Entity directDamager, double appliedDamage) {
        if (hasKit(player, KitType.BERSERKER) && isActive(lifeStealEnds, player.getUniqueId())) {
            heal(player, Math.min(target.getHealth(), appliedDamage) * BERSERKER_LIFE_STEAL_RATIO);
        }

        if (hasKit(player, KitType.RANGER)
                && directDamager instanceof AbstractArrow arrow
                && arrow.hasMetadata("vd_ranger_piercing")) {
            applyPiercingDamage(player, target, arrow, appliedDamage * RANGER_PIERCING_DAMAGE_MULTIPLIER);
        }
    }

    public void prepareShot(Player player, Entity projectile) {
        if (!(projectile instanceof AbstractArrow arrow)) {
            return;
        }

        UUID playerId = player.getUniqueId();
        Long chargedUntil = chargedShotArmed.remove(playerId);
        if (chargedUntil != null && chargedUntil > System.currentTimeMillis()) {
            arrow.setMetadata("vd_ranger_charged", new FixedMetadataValue(plugin, true));
            arrow.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.0f, 0.7f);
        }

        Long piercingUntil = piercingArrowArmed.remove(playerId);
        if (piercingUntil != null && piercingUntil > System.currentTimeMillis()) {
            arrow.setMetadata("vd_ranger_piercing", new FixedMetadataValue(plugin, true));
            arrow.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.0f, 1.3f);
        }
    }

    public Player getTauntTarget(Arena arena, Creature creature) {
        TauntState state = tauntedMobs.get(creature.getUniqueId());
        if (state == null || state.endAt() <= System.currentTimeMillis()) {
            tauntedMobs.remove(creature.getUniqueId());
            return null;
        }

        Player player = plugin.getServer().getPlayer(state.playerId());
        if (player == null || !player.isOnline() || player.isDead()
                || player.getGameMode() != GameMode.SURVIVAL || !arena.hasPlayer(player)) {
            tauntedMobs.remove(creature.getUniqueId());
            return null;
        }
        return player;
    }

    public int getRespawnDelaySeconds(Arena arena, Player deadPlayer) {
        if (arena == null) {
            return BASE_RESPAWN_SECONDS;
        }

        int medicsAlive = 0;
        for (Player player : arena.getPlayers()) {
            if (player.getUniqueId().equals(deadPlayer.getUniqueId())) {
                continue;
            }
            if (isActiveArenaPlayer(player) && hasKit(player, KitType.MEDIC)) {
                medicsAlive++;
            }
        }
        return Math.max(18, BASE_RESPAWN_SECONDS - (medicsAlive * 4));
    }

    public int applyStructureDiscount(Player player, int baseCost) {
        if (!hasKit(player, KitType.ENGINEER)) {
            return baseCost;
        }
        return Math.max(0, (int) Math.round(baseCost * ENGINEER_DISCOUNT_MULTIPLIER));
    }

    public void onTowerPlaced(Player player, Tower tower) {
        tower.setOwner(player.getUniqueId());
        tower.setReinforced(hasKit(player, KitType.ENGINEER));
    }

    public void onTrapPlaced(Player player, TrapSlot slot) {
        slot.setOwner(player.getUniqueId());
        slot.setReinforced(hasKit(player, KitType.ENGINEER));
    }

    public double adjustTowerDamage(Tower tower, double baseDamage) {
        double multiplier = 1.0;
        if (tower.isReinforced()) {
            multiplier *= ENGINEER_REINFORCED_DAMAGE_MULTIPLIER;
        }
        if (isActive(overclockedTowers, tower.getSlot())) {
            multiplier *= ENGINEER_OVERCLOCK_DAMAGE_MULTIPLIER;
        }
        return baseDamage * multiplier;
    }

    public double adjustTowerAttackInterval(Tower tower, double baseInterval) {
        double multiplier = 1.0;
        if (tower.isReinforced()) {
            multiplier *= ENGINEER_REINFORCED_INTERVAL_MULTIPLIER;
        }
        if (isActive(overclockedTowers, tower.getSlot())) {
            multiplier *= ENGINEER_OVERCLOCK_INTERVAL_MULTIPLIER;
        }
        return Math.max(1.0, baseInterval * multiplier);
    }

    public long adjustTrapCooldown(TrapSlot slot, long baseCooldownMs) {
        double multiplier = 1.0;
        if (slot.isReinforced()) {
            multiplier *= ENGINEER_REINFORCED_TRAP_COOLDOWN_MULTIPLIER;
        }
        if (isActive(overclockedTraps, slot)) {
            multiplier *= ENGINEER_OVERCLOCK_TRAP_COOLDOWN_MULTIPLIER;
        }
        return Math.max(1_000L, Math.round(baseCooldownMs * multiplier));
    }

    private void tickPassives() {
        long now = System.currentTimeMillis();
        cleanupExpiringEntries(cooldowns, now);
        cleanupExpiringEntries(shieldWallEnds, now);
        cleanupExpiringEntries(rageEnds, now);
        cleanupExpiringEntries(lifeStealEnds, now);
        cleanupExpiringEntries(chargedShotArmed, now);
        cleanupExpiringEntries(piercingArrowArmed, now);
        cleanupExpiringEntries(overclockedTowers, now);
        cleanupExpiringEntries(overclockedTraps, now);
        cleanupTaunts(now);
        applyMedicAura();
        tickMedicFields(now);
    }

    private void applyMedicAura() {
        for (Arena arena : plugin.getArenaManager().getAllArenas()) {
            if (!isCombatArena(arena)) {
                continue;
            }

            for (Player medic : arena.getPlayers()) {
                if (!isActiveArenaPlayer(medic) || !hasKit(medic, KitType.MEDIC)) {
                    continue;
                }

                Location center = medic.getLocation();
                for (Player ally : arena.getPlayers()) {
                    if (ally.getUniqueId().equals(medic.getUniqueId())) {
                        continue;
                    }
                    if (!isActiveArenaPlayer(ally)) {
                        continue;
                    }
                    if (!sameWorld(center, ally.getLocation())) {
                        continue;
                    }
                    if (center.distanceSquared(ally.getLocation()) > MEDIC_AURA_RADIUS * MEDIC_AURA_RADIUS) {
                        continue;
                    }
                    ally.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.REGENERATION, 50, 0, false, false));
                }
            }
        }
    }

    private boolean activateGuardianShieldWall(Player player) {
        String name = plugin.getMessages().format("kit.ability.guardian.shield-wall.name");
        if (!checkCooldown(player, "guardian_shield_wall", name)) {
            return true;
        }

        shieldWallEnds.put(player.getUniqueId(), System.currentTimeMillis() + GUARDIAN_SHIELD_WALL_DURATION_MS);
        setCooldown(player, "guardian_shield_wall", GUARDIAN_SHIELD_WALL_COOLDOWN_MS);
        player.getWorld().spawnParticle(VersionCompat.TOTEM_OF_UNDYING,
            player.getLocation().add(0, 1.2, 0), 20, 0.6, 0.8, 0.6, 0.1);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 0.8f);
        ChatUtil.send(player, plugin.getMessages().get("kit.ability.activated", name));
        return true;
    }

    private boolean activateGuardianTaunt(Player player, Arena arena) {
        String name = plugin.getMessages().format("kit.ability.guardian.taunt.name");
        if (!checkCooldown(player, "guardian_taunt", name)) {
            return true;
        }

        int affected = 0;
        double radiusSq = GUARDIAN_TAUNT_RADIUS * GUARDIAN_TAUNT_RADIUS;
        for (UUID mobId : new ArrayList<>(arena.getActiveMobUUIDs())) {
            Entity entity = plugin.getServer().getEntity(mobId);
            if (!(entity instanceof Creature creature) || !entity.isValid() || entity.isDead()) {
                continue;
            }
            if (!sameWorld(player.getLocation(), creature.getLocation())) {
                continue;
            }
            if (player.getLocation().distanceSquared(creature.getLocation()) > radiusSq) {
                continue;
            }
            creature.setTarget(player);
            tauntedMobs.put(creature.getUniqueId(),
                new TauntState(player.getUniqueId(), System.currentTimeMillis() + GUARDIAN_TAUNT_DURATION_MS));
            affected++;
        }

        setCooldown(player, "guardian_taunt", GUARDIAN_TAUNT_COOLDOWN_MS);
        player.getWorld().spawnParticle(Particle.CRIT_MAGIC,
            player.getLocation().add(0, 1.0, 0), 25, 1.2, 0.8, 1.2, 0.2);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 1.0f);
        ChatUtil.send(player, plugin.getMessages().get("kit.ability.taunt-success", name, affected));
        return true;
    }

    private boolean armRangerShot(Player player, Map<UUID, Long> armedMap, String cooldownKey, String nameKey) {
        String name = plugin.getMessages().format(nameKey);
        if (!checkCooldown(player, cooldownKey, name)) {
            return true;
        }

        armedMap.put(player.getUniqueId(), System.currentTimeMillis() + RANGER_SHOT_ARM_DURATION_MS);
        setCooldown(player, cooldownKey,
            "ranger_charged_shot".equals(cooldownKey) ? RANGER_CHARGED_COOLDOWN_MS : RANGER_PIERCING_COOLDOWN_MS);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.4f);
        ChatUtil.send(player, plugin.getMessages().get("kit.ability.shot-ready", name));
        return true;
    }

    private boolean activateBerserkerBuff(Player player, Map<UUID, Long> activeMap, long durationMs,
                                          long cooldownMs, String cooldownKey, String nameKey) {
        String name = plugin.getMessages().format(nameKey);
        if (!checkCooldown(player, cooldownKey, name)) {
            return true;
        }

        activeMap.put(player.getUniqueId(), System.currentTimeMillis() + durationMs);
        setCooldown(player, cooldownKey, cooldownMs);
        player.getWorld().spawnParticle(Particle.CRIT,
            player.getLocation().add(0, 1.0, 0), 25, 0.7, 0.8, 0.7, 0.2);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.3f, 1.8f);
        ChatUtil.send(player, plugin.getMessages().get("kit.ability.activated", name));
        return true;
    }

    private boolean activateMedicHealingPulse(Player player, Arena arena, Location targetLocation) {
        String name = plugin.getMessages().format("kit.ability.medic.healing-pulse.name");
        if (!checkCooldown(player, "medic_healing_pulse", name)) {
            return true;
        }

        Location center = normalizeMedicFieldLocation(player, targetLocation);
        if (center == null || !sameWorld(player.getLocation(), center)) {
            ChatUtil.send(player, plugin.getMessages().get("kit.ability.invalid-target"));
            return true;
        }

        medicFields.add(new MedicField(
            player.getUniqueId(),
            arena.getId(),
            center,
            System.currentTimeMillis() + MEDIC_HEAL_FIELD_DURATION_MS
        ));

        setCooldown(player, "medic_healing_pulse", MEDIC_HEAL_PULSE_COOLDOWN_MS);
        center.getWorld().playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.6f);
        spawnMedicFieldParticles(center);
        ChatUtil.send(player, plugin.getMessages().get("kit.ability.field-created", name, 15));
        return true;
    }

    private boolean activateEngineerOverclock(Player player, Arena arena) {
        String name = plugin.getMessages().format("kit.ability.engineer.overclock.name");
        if (!checkCooldown(player, "engineer_overclock", name)) {
            return true;
        }

        int affected = 0;
        long endAt = System.currentTimeMillis() + ENGINEER_OVERCLOCK_DURATION_MS;

        for (Tower tower : plugin.getTowerManager().getTowersInArena(arena)) {
            if (sameWorld(player.getLocation(), tower.getLocation())
                    && player.getLocation().distanceSquared(tower.getLocation()) <= ENGINEER_STRUCTURE_RADIUS * ENGINEER_STRUCTURE_RADIUS) {
                overclockedTowers.put(tower.getSlot(), endAt);
                affected++;
                tower.getLocation().getWorld().spawnParticle(Particle.CRIT_MAGIC,
                    tower.getLocation().add(0.5, 1.5, 0.5), 10, 0.6, 0.8, 0.6, 0.15);
            }
        }

        for (TrapSlot slot : arena.getTrapSlots()) {
            if (slot.isEmpty() || !sameWorld(player.getLocation(), slot.getLocation())) {
                continue;
            }
            if (player.getLocation().distanceSquared(slot.getLocation()) > ENGINEER_STRUCTURE_RADIUS * ENGINEER_STRUCTURE_RADIUS) {
                continue;
            }
            overclockedTraps.put(slot, endAt);
            plugin.getTrapManager().refreshTrapDisplay(slot);
            affected++;
            slot.getLocation().getWorld().spawnParticle(Particle.CRIT_MAGIC,
                slot.getLocation().add(0.5, 1.0, 0.5), 10, 0.5, 0.3, 0.5, 0.0);
        }

        if (affected == 0) {
            ChatUtil.send(player, plugin.getMessages().get("kit.ability.no-structures", name));
            return true;
        }

        setCooldown(player, "engineer_overclock", ENGINEER_OVERCLOCK_COOLDOWN_MS);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.9f, 1.5f);
        ChatUtil.send(player, plugin.getMessages().get("kit.ability.overclock-success", name, affected));
        return true;
    }

    private boolean activateEngineerRepairPulse(Player player, Arena arena) {
        String name = plugin.getMessages().format("kit.ability.engineer.repair-pulse.name");
        if (!checkCooldown(player, "engineer_repair_pulse", name)) {
            return true;
        }

        int affectedStructures = 0;
        int healedPlayers = 0;
        double radiusSq = ENGINEER_STRUCTURE_RADIUS * ENGINEER_STRUCTURE_RADIUS;

        for (Tower tower : plugin.getTowerManager().getTowersInArena(arena)) {
            if (!sameWorld(player.getLocation(), tower.getLocation())
                    || player.getLocation().distanceSquared(tower.getLocation()) > radiusSq) {
                continue;
            }
            tower.readyAttack();
            tower.getLocation().getWorld().spawnParticle(VersionCompat.HAPPY_VILLAGER,
                tower.getLocation().add(0.5, 1.5, 0.5), 8, 0.5, 0.5, 0.5, 0.0);
            affectedStructures++;
        }

        for (TrapSlot slot : arena.getTrapSlots()) {
            if (slot.isEmpty() || !sameWorld(player.getLocation(), slot.getLocation())
                    || player.getLocation().distanceSquared(slot.getLocation()) > radiusSq) {
                continue;
            }
            slot.clearCooldown();
            plugin.getTrapManager().refreshTrapDisplay(slot);
            slot.getLocation().getWorld().spawnParticle(VersionCompat.HAPPY_VILLAGER,
                slot.getLocation().add(0.5, 1.0, 0.5), 8, 0.4, 0.2, 0.4, 0.0);
            affectedStructures++;
        }

        for (Player ally : arena.getPlayers()) {
            if (!ally.isOnline() || ally.isDead() || !sameWorld(player.getLocation(), ally.getLocation())) {
                continue;
            }
            if (player.getLocation().distanceSquared(ally.getLocation()) > radiusSq) {
                continue;
            }
            heal(ally, ENGINEER_REPAIR_HEAL);
            healedPlayers++;
        }

        if (affectedStructures == 0) {
            ChatUtil.send(player, plugin.getMessages().get("kit.ability.no-structures", name));
            return true;
        }

        setCooldown(player, "engineer_repair_pulse", ENGINEER_REPAIR_PULSE_COOLDOWN_MS);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.5f);
        ChatUtil.send(player, plugin.getMessages().get("kit.ability.repair-success", name, affectedStructures, healedPlayers));
        return true;
    }

    private void applyPiercingDamage(Player shooter, LivingEntity primaryTarget, AbstractArrow arrow, double damage) {
        Arena arena = plugin.getArenaManager().getArenaOfPlayer(shooter);
        if (arena == null) {
            return;
        }

        Vector direction = arrow.getVelocity().clone();
        if (direction.lengthSquared() < 0.01) {
            return;
        }
        direction.normalize();

        Location start = primaryTarget.getLocation().clone().add(0, primaryTarget.getHeight() * 0.5, 0);
        for (UUID mobId : new ArrayList<>(arena.getActiveMobUUIDs())) {
            Entity entity = plugin.getServer().getEntity(mobId);
            if (!(entity instanceof LivingEntity mob) || !entity.isValid() || entity.isDead()
                    || mob.getUniqueId().equals(primaryTarget.getUniqueId())) {
                continue;
            }

            Vector toMob = mob.getLocation().clone().add(0, mob.getHeight() * 0.5, 0).toVector().subtract(start.toVector());
            double forward = toMob.dot(direction);
            if (forward <= 0 || forward > RANGER_PIERCING_DISTANCE) {
                continue;
            }

            Vector lateral = toMob.clone().subtract(direction.clone().multiply(forward));
            if (lateral.lengthSquared() > RANGER_PIERCING_WIDTH * RANGER_PIERCING_WIDTH) {
                continue;
            }

            mob.damage(damage, shooter);
            mob.getWorld().spawnParticle(Particle.CRIT,
                mob.getLocation().add(0, 1.0, 0), 6, 0.4, 0.4, 0.4, 0.1);
        }
    }

    private void tickMedicFields(long now) {
        Iterator<MedicField> iterator = medicFields.iterator();
        while (iterator.hasNext()) {
            MedicField field = iterator.next();
            if (field.endAt() <= now || field.center().getWorld() == null) {
                iterator.remove();
                continue;
            }

            spawnMedicFieldParticles(field.center());

            for (Player player : field.center().getWorld().getPlayers()) {
                if (!player.isOnline() || player.isDead()) {
                    continue;
                }
                Arena arena = plugin.getArenaManager().getArenaOfPlayer(player);
                if (!isCombatArena(arena)
                        || !field.arenaId().equals(arena.getId())
                        || !sameWorld(field.center(), player.getLocation())) {
                    continue;
                }
                if (field.center().distanceSquared(player.getLocation()) > MEDIC_HEAL_FIELD_RADIUS * MEDIC_HEAL_FIELD_RADIUS) {
                    continue;
                }

                heal(player, MEDIC_HEAL_FIELD_HEAL);
                player.getWorld().spawnParticle(Particle.HEART,
                    player.getLocation().add(0, 1.0, 0), 2, 0.25, 0.35, 0.25, 0.0);
            }
        }
    }

    private void spawnMedicFieldParticles(Location center) {
        if (center.getWorld() == null) {
            return;
        }

        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(80, 255, 120), 1.3f);
        for (int i = 0; i < 28; i++) {
            double angle = (Math.PI * 2 * i) / 28.0;
            double x = center.getX() + Math.cos(angle) * MEDIC_HEAL_FIELD_RADIUS;
            double z = center.getZ() + Math.sin(angle) * MEDIC_HEAL_FIELD_RADIUS;
            center.getWorld().spawnParticle(Particle.REDSTONE,
                x, center.getY(), z, 1, 0.0, 0.0, 0.0, 0.0, dust);
        }

        center.getWorld().spawnParticle(VersionCompat.HAPPY_VILLAGER,
            center.clone().add(0, 0.25, 0), 12, 1.4, 0.2, 1.4, 0.0);
    }

    private Location normalizeMedicFieldLocation(Player player, Location targetLocation) {
        if (targetLocation == null) {
            return null;
        }

        Location normalized = targetLocation.clone();
        if (normalized.getWorld() == null) {
            normalized.setWorld(player.getWorld());
        }
        normalized.setY(normalized.getY() + 0.15);
        return normalized;
    }

    private boolean checkCooldown(Player player, String abilityId, String displayName) {
        long remaining = cooldowns.getOrDefault(cooldownKey(player, abilityId), 0L) - System.currentTimeMillis();
        if (remaining <= 0) {
            return true;
        }
        ChatUtil.send(player, plugin.getMessages().get(
            "kit.ability.cooldown", displayName, Math.max(1, (int) Math.ceil(remaining / 1000.0))));
        return false;
    }

    private void setCooldown(Player player, String abilityId, long durationMs) {
        cooldowns.put(cooldownKey(player, abilityId), System.currentTimeMillis() + durationMs);
    }

    private String cooldownKey(Player player, String abilityId) {
        return player.getUniqueId() + ":" + abilityId;
    }

    private boolean hasKit(Player player, KitType type) {
        VDPlayer vdPlayer = plugin.getPlayerManager().getPlayer(player);
        return vdPlayer != null && vdPlayer.getSelectedKit() == type;
    }

    private double lowHealthDamageMultiplier(Player player) {
        double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH) == null
            ? 20.0
            : player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
        if (maxHealth <= 0) {
            return 1.0;
        }

        double ratio = player.getHealth() / maxHealth;
        if (ratio >= 0.5) {
            return 1.0;
        }
        double missingRatio = (0.5 - ratio) / 0.5;
        return 1.0 + (missingRatio * BERSERKER_LOW_HP_BONUS);
    }

    private boolean isHeadshot(LivingEntity target, AbstractArrow arrow) {
        return arrow.getLocation().getY() >= target.getEyeLocation().getY() - 0.25;
    }

    private void heal(Player player, double amount) {
        double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH) == null
            ? 20.0
            : player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
        player.setHealth(Math.min(maxHealth, player.getHealth() + amount));
    }

    private boolean isCombatArena(Arena arena) {
        return arena != null
            && (arena.getState() == ArenaState.RUNNING || arena.getState() == ArenaState.BETWEEN_WAVES);
    }

    private boolean isActiveArenaPlayer(Player player) {
        return player.isOnline() && !player.isDead() && player.getGameMode() == GameMode.SURVIVAL;
    }

    private boolean sameWorld(Location first, Location second) {
        return first.getWorld() != null && first.getWorld().equals(second.getWorld());
    }

    private <T> boolean isActive(Map<T, Long> map, T key) {
        return map.getOrDefault(key, 0L) > System.currentTimeMillis();
    }

    private <T> void cleanupExpiringEntries(Map<T, Long> map, long now) {
        Iterator<Map.Entry<T, Long>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() <= now) {
                iterator.remove();
            }
        }
    }

    private void cleanupTaunts(long now) {
        Iterator<Map.Entry<UUID, TauntState>> iterator = tauntedMobs.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().endAt() <= now) {
                iterator.remove();
            }
        }
    }
}
