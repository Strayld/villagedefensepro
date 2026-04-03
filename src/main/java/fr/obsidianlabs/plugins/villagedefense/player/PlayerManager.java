package fr.obsidianlabs.plugins.villagedefense.player;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.achievement.AchievementDefinition;
import fr.obsidianlabs.plugins.villagedefense.api.VillageDefenseApiIds;
import fr.obsidianlabs.plugins.villagedefense.api.VillageDefenseProgressType;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.kit.Kit;
import fr.obsidianlabs.plugins.villagedefense.kit.KitType;
import fr.obsidianlabs.plugins.villagedefense.perk.PerkType;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerManager {

    private final VillageDefensePlugin plugin;
    private final Map<UUID, VDPlayer> players = new ConcurrentHashMap<>();

    // Sauvegarde de l'état du joueur avant qu'il entre en partie
    private final Map<UUID, PlayerSnapshot> snapshots = new HashMap<>();

    public PlayerManager(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    // ---- Chargement / Sauvegarde ----

    public void loadPlayer(Player player) {
        plugin.getDatabase().loadPlayer(player.getUniqueId(), player.getName())
            .thenAccept(vdPlayer -> {
                players.put(player.getUniqueId(), vdPlayer);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    plugin.getHookManager().syncEconomyBalance(player, vdPlayer.getCoins());
                    grantEligibleKitUnlocks(player, vdPlayer);
                });
            });
    }

    public void savePlayer(Player player) {
        VDPlayer vdPlayer = players.get(player.getUniqueId());
        if (vdPlayer != null) {
            plugin.getHookManager().syncEconomyBalance(player, vdPlayer.getCoins());
            plugin.getDatabase().savePlayer(vdPlayer);
        }
    }

    public CompletableFuture<Void> savePlayerAsync(Player player) {
        VDPlayer vdPlayer = players.get(player.getUniqueId());
        if (vdPlayer != null) {
            plugin.getHookManager().syncEconomyBalance(player, vdPlayer.getCoins());
            return plugin.getDatabase().savePlayer(vdPlayer);
        }
        return CompletableFuture.completedFuture(null);
    }

    public void saveAndRemove(Player player) {
        VDPlayer vdPlayer = players.remove(player.getUniqueId());
        if (vdPlayer != null) {
            plugin.getHookManager().syncEconomyBalance(player, vdPlayer.getCoins());
            plugin.getDatabase().savePlayer(vdPlayer);
        }
    }

    // ---- Getters ----

    public VDPlayer getPlayer(UUID uuid) { return players.get(uuid); }
    public VDPlayer getPlayer(Player player) { return players.get(player.getUniqueId()); }

    // ---- Kits ----

    /** Applique le kit sélectionné à un joueur */
    public void applyKit(Player player) {
        VDPlayer vdPlayer = getPlayer(player);
        if (vdPlayer == null) return;

        KitType kitType = vdPlayer.getSelectedKit();
        Kit kit = plugin.getKitManager().getKit(kitType);
        if (kit == null) return;

        kit.apply(player);
        player.setGameMode(GameMode.SURVIVAL);
        var maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealth != null) {
            player.setHealth(Math.min(maxHealth.getValue(), maxHealth.getValue()));
        }
        player.setFoodLevel(20);
    }

    /** Prépare le joueur pour rejoindre le lobby (snapshot + clear) */
    public void prepareForLobby(Player player) {
        takeSnapshot(player);
        player.getInventory().clear();
        player.getEquipment().clear();
        for (PotionEffect e : player.getActivePotionEffects()) {
            player.removePotionEffect(e.getType());
        }
        player.setGameMode(GameMode.ADVENTURE);
        resetAttributes(player);

        var m = plugin.getMessages();

        // Livre de sélection de kit (slot 4)
        player.getInventory().setItem(4, new fr.obsidianlabs.plugins.villagedefense.util.ItemBuilder(org.bukkit.Material.BOOK)
            .name(m.format("lobby.kit-selector"))
            .lore(m.format("lobby.kit-selector-lore"))
            .glowing()
            .build());

        // Livre des succès (slot 7)
        player.getInventory().setItem(7, new fr.obsidianlabs.plugins.villagedefense.util.ItemBuilder(org.bukkit.Material.WRITTEN_BOOK)
            .name(m.format("lobby.achievements"))
            .lore(m.format("lobby.achievements-lore1"),
                  m.format("lobby.achievements-lore2"))
            .glowing()
            .build());

        // Item pour quitter l'arène (slot 8 — dernier slot à droite)
        player.getInventory().setItem(8, new fr.obsidianlabs.plugins.villagedefense.util.ItemBuilder(org.bukkit.Material.RED_BED)
            .name(m.format("lobby.leave"))
            .lore(m.format("lobby.leave-lore"))
            .build());
    }

    /** Restaure l'état du joueur après une partie */
    public void restorePlayer(Player player) {
        Kit.reset(player);
        PlayerSnapshot snapshot = snapshots.remove(player.getUniqueId());
        if (snapshot != null) snapshot.restore(player);
        else {
            player.setGameMode(GameMode.SURVIVAL);
        }
    }

    private void takeSnapshot(Player player) {
        snapshots.put(player.getUniqueId(), new PlayerSnapshot(player));
    }

    private void resetAttributes(Player player) {
        var attr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attr != null) { attr.setBaseValue(20.0); player.setHealth(20.0); }
        player.setFoodLevel(20);
        player.setSaturation(20f);
    }

    // ---- Coins ----

    public void addCoins(Player player, long amount) {
        addCoins(player, amount, true);
    }

    public void addCoins(Player player, long amount, boolean showActionBar) {
        VDPlayer vdPlayer = getPlayer(player);
        if (vdPlayer == null) return;
        vdPlayer.addCoins(amount);
        if (showActionBar) {
            plugin.sendActionBar(player, plugin.getMessages().format(player, "player.coins-gained", amount));
        }
    }

    // ---- XP ----

    /**
     * Accorde de l'XP au joueur, notifie en cas de montée de niveau
     * et débloque automatiquement les kits/atouts désormais accessibles.
     */
    public void awardXp(Player player, long amount) {
        VDPlayer vdPlayer = getPlayer(player);
        if (vdPlayer == null) return;
        int oldLevel = vdPlayer.getLevel();
        vdPlayer.addXp(amount);
        Arena arena = plugin.getArenaManager().getArenaOfParticipant(player);
        plugin.fireProgress(player, arena, VillageDefenseProgressType.XP_EARNED, amount, vdPlayer.getTotalXp(), "");
        int newLevel = vdPlayer.getLevel();
        if (newLevel > oldLevel) {
            plugin.getMessages().send(player, "player.level-up", newLevel);
            player.playSound(player.getLocation(),
                org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            plugin.fireProgress(player, arena, VillageDefenseProgressType.LEVEL_UP, newLevel - oldLevel, newLevel, "");
            checkAndGrantUnlocks(player, vdPlayer, newLevel);
        }
    }

    /**
     * Débloque automatiquement les kits dont le niveau requis vient d'être atteint
     * et notifie le joueur des nouvelles options disponibles.
     */
    public void checkAndGrantUnlocks(Player player, VDPlayer vdPlayer, int newLevel) {
        var msg = plugin.getMessages();
        grantEligibleKitUnlocks(player, vdPlayer);
        // Atouts : notifier ceux qui deviennent achetables à exactement ce niveau
        for (PerkType type : PerkType.values()) {
            if (type.getLevelRequired() == newLevel) {
                msg.send(player, "player.perk-unlocked", msg.format(player, type.getNameKey()));
            }
        }
        checkAchievements(player, vdPlayer);
        savePlayer(player);
    }

    public void grantEligibleKitUnlocks(Player player, VDPlayer vdPlayer) {
        var msg = plugin.getMessages();
        int playerLevel = vdPlayer.getLevel();
        boolean changed = false;

        for (KitType type : KitType.values()) {
            int levelRequired = plugin.getKitManager().getLevelRequired(type);
            if (!vdPlayer.hasKit(type) && levelRequired <= playerLevel) {
                vdPlayer.unlockKit(type);
                changed = true;
                msg.send(player, "player.kit-unlocked", msg.format(player, type.getNameKey()), levelRequired);
            }
        }

        if (changed) {
            savePlayer(player);
        }
    }

    public void refreshEligibleKitUnlocks() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            VDPlayer vdPlayer = getPlayer(player);
            if (vdPlayer != null) {
                grantEligibleKitUnlocks(player, vdPlayer);
            }
        }
    }

    // ---- Succès ----

    public void checkAchievements(Player player, VDPlayer vdPlayer) {
        for (AchievementDefinition def : plugin.getAchievementManager().getAll()) {
            if (vdPlayer.hasAchievementId(def.getId())) continue;
            long progress = switch (def.getCondition()) {
                case KILLS        -> vdPlayer.getTotalKills();
                case WAVES        -> vdPlayer.getTotalWavesCompleted();
                case GAMES        -> vdPlayer.getTotalGamesPlayed();
                case COINS_EARNED -> vdPlayer.getTotalCoinsEarned();
            };
            if (progress >= def.getTarget()) {
                unlockAchievement(player, def, true);
            }
        }
    }

    public boolean unlockAchievement(Player player, AchievementDefinition achievement, boolean notifyPlayer) {
        VDPlayer vdPlayer = getPlayer(player);
        if (vdPlayer == null || vdPlayer.hasAchievementId(achievement.getId())) {
            return false;
        }

        vdPlayer.unlockAchievementId(achievement.getId());

        if (notifyPlayer) {
            var msg = plugin.getMessages();
            msg.send(player, "player.achievement-unlocked",
                achievement.getDisplayName(),
                achievement.getDescription());
            player.playSound(player.getLocation(),
                org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }

        Arena arena = plugin.getArenaManager().getArenaOfParticipant(player);
        plugin.fireProgress(
            player,
            arena,
            VillageDefenseProgressType.ACHIEVEMENT_UNLOCKED,
            1,
            vdPlayer.getCompletedAchievements().size(),
            achievement.getId()
        );
        savePlayer(player);
        return true;
    }

    // ---- Snapshot interne ----

    private record PlayerSnapshot(
        GameMode gameMode,
        ItemStack[] inventory,
        ItemStack[] armor,
        double health,
        int food
    ) {
        PlayerSnapshot(Player p) {
            this(
                p.getGameMode(),
                p.getInventory().getContents().clone(),
                p.getEquipment().getArmorContents().clone(),
                Math.min(p.getHealth(), 20),
                p.getFoodLevel()
            );
        }

        void restore(Player p) {
            p.setGameMode(gameMode);
            p.getInventory().setContents(inventory);
            p.getEquipment().setArmorContents(armor);
            p.setHealth(health);
            p.setFoodLevel(food);
            for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        }
    }
}
