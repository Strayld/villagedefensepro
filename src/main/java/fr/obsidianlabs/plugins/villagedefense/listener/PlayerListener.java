package fr.obsidianlabs.plugins.villagedefense.listener;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaSetup;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaState;
import fr.obsidianlabs.plugins.villagedefense.gui.AchievementGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.IconPickerGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.SpecializationGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.TrapGUI;
import fr.obsidianlabs.plugins.villagedefense.trap.TrapSlot;
import fr.obsidianlabs.plugins.villagedefense.gui.DatabaseGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.LanguageGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.SchematicCategoryGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.SchematicGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.VDAdminGUI;
import fr.obsidianlabs.plugins.villagedefense.tower.SchematicCategory;
import fr.obsidianlabs.plugins.villagedefense.gui.TowerGUI;
import fr.obsidianlabs.plugins.villagedefense.tower.TowerSlot;
import fr.obsidianlabs.plugins.villagedefense.util.ChatUtil;
import fr.obsidianlabs.plugins.villagedefense.util.Perm;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;


public class PlayerListener implements Listener {

    private final VillageDefensePlugin plugin;
    public PlayerListener(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    private String m(String key, Object... args) {
        return plugin.getMessages().format(key, args);
    }

    private boolean guiTitle(String title, String key) {
        return title.contains(plugin.getMessages().format(key));
    }

    private boolean isArenaObserver(Player player) {
        return plugin.getGameManager().isArenaObserver(player);
    }

    // ---- Join / Quit ----

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getPlayerManager().loadPlayer(player);
        if (plugin.getAttemptManager() != null) {
            plugin.getAttemptManager().loadPlayer(player.getUniqueId());
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getUniqueId().equals(player.getUniqueId())) continue;
                if (plugin.getGameManager().isArenaObserver(online)) {
                    player.hidePlayer(plugin, online);
                }
            }
        }, 1L);
        // Hub join automatique — deux chemins :
        // 1. BungeeCord Forward (message reçu en mémoire via GameManager)
        // 2. MySQL vd_hub_pending (compatible Velocity + BungeeCord sans Forward)
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // Lecture async de vd_hub_pending (prioritaire sur le Forward en mémoire)
            fr.obsidianlabs.plugins.villagedefense.database.Database.HubPendingEntry dbPending =
                plugin.getDatabase().consumeHubPending(player.getUniqueId());

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;

                // Priorité 1 : entrée MySQL (Velocity ou BungeeCord)
                if (dbPending != null) {
                    Arena hubArena = plugin.getArenaManager().getArena(dbPending.arenaId());
                    if (hubArena != null) {
                        if ("SPECTATE".equalsIgnoreCase(dbPending.action())) {
                            plugin.getPerkManager().prepareForArenaEntry(player, hubArena, false);
                            plugin.getPlayerManager().prepareForLobby(player);
                            plugin.getArenaManager().joinAsSpectator(player, hubArena);
                        } else if (hubArena.isSetup() && hubArena.isJoinWindowOpen()
                                && plugin.getArenaManager().canPlayerAccessArena(player, hubArena)) {
                            plugin.getPerkManager().prepareForArenaEntry(player, hubArena, false);
                            plugin.getPlayerManager().prepareForLobby(player);
                            var vdp = plugin.getPlayerManager().getPlayer(player);
                            if (vdp != null) {
                                vdp.setReturnLocation(player.getLocation().clone());
                                vdp.setCurrentArenaId(hubArena.getId());
                            }
                            plugin.getArenaManager().joinArena(player, hubArena);
                            plugin.getGameManager().checkAndStart(hubArena);
                        }
                    }
                    return;
                }

                // Priorité 2 : Forward BungeeCord en mémoire (legacy)
                String hubArenaId = plugin.getGameManager().consumeHubJoin(player.getUniqueId());
                if (hubArenaId != null) {
                    Arena hubArena = plugin.getArenaManager().getArena(hubArenaId);
                    if (hubArena != null && hubArena.isSetup() && hubArena.isJoinWindowOpen()
                            && plugin.getArenaManager().canPlayerAccessArena(player, hubArena)) {
                        plugin.getPerkManager().prepareForArenaEntry(player, hubArena, false);
                        plugin.getPlayerManager().prepareForLobby(player);
                        var vdp = plugin.getPlayerManager().getPlayer(player);
                        if (vdp != null) {
                            vdp.setReturnLocation(player.getLocation().clone());
                            vdp.setCurrentArenaId(hubArena.getId());
                        }
                        plugin.getArenaManager().joinArena(player, hubArena);
                        plugin.getGameManager().checkAndStart(hubArena);
                    }
                    return;
                }

                // Priorité 3 : token de rejoin valide
                if (!plugin.getGameManager().hasValidRejoinToken(player)) return;
                String arenaId = plugin.getGameManager().getRejoinArenaId(player);
                Arena arena = plugin.getArenaManager().getArena(arenaId);
                if (arena == null || arena.getState() != ArenaState.RUNNING) {
                    plugin.getGameManager().clearRejoinToken(player);
                    return;
                }
                long remaining = plugin.getGameManager().getRejoinRemainingSeconds(player);
                long mins = remaining / 60, secs = remaining % 60;
                ChatUtil.send(player, plugin.getMessages().format("arena.rejoin-available",
                    arena.getDisplayName(), mins, secs));
            }, 2L);
        });
    }

    // ---- Saisie chat (nom d'arène etc.) ----

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (plugin.getChatInputManager().intercept(player, event.getMessage())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.getArenaManager().getArenaOfParticipant(player) != null) {
            plugin.getGameManager().cancelRespawn(player);
            plugin.getArenaManager().leaveArena(player, false);
        }
        plugin.getPlayerManager().saveAndRemove(player);
        if (plugin.getAttemptManager() != null) {
            plugin.getAttemptManager().unloadPlayer(player.getUniqueId());
        }
    }

    // ---- Mort / Respawn ----

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Arena arena = plugin.getArenaManager().getArenaOfPlayer(player);
        if (arena == null) return;

        event.getDrops().clear();
        event.setDeathMessage(null);

        plugin.getGameManager().scheduleRespawn(player, arena);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Arena arena = plugin.getArenaManager().getArenaOfPlayer(player);
        if (arena == null) {
            return;
        }

        if (plugin.getGameManager().isRespawning(player)) {
            if (arena.getEffectiveGameSpawn() != null) {
                event.setRespawnLocation(arena.getEffectiveGameSpawn());
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                Arena current = plugin.getArenaManager().getArenaOfPlayer(player);
                if (current == null) {
                    return;
                }
                plugin.getGameManager().enterRespawnObserverMode(player, current);
            }, 1L);
            return;
        }

        if (arena.getLobbyLocation() != null) {
            event.setRespawnLocation(arena.getLobbyLocation());
        }
    }

    // ---- Dégâts de chute en lobby ----

    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Arena arena = plugin.getArenaManager().getArenaOfPlayer(player);
        if (arena == null) return;
        if ((arena.getState() == ArenaState.WAITING || arena.getState() == ArenaState.STARTING) &&
            event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
        }
    }

    // ---- Clic droit sur un villageois de service → shop ou enchanteur ----

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onObserverInteractEntity(PlayerInteractEntityEvent event) {
        if (isArenaObserver(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onObserverInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (isArenaObserver(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractVillager(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager)) return;

        if (!isProtectedVillager(villager)) {
            return;
        }

        event.setCancelled(true);

        Player player = event.getPlayer();
        Arena arena = plugin.getArenaManager().getArenaOfPlayer(player);
        if (arena == null) {
            return;
        }

        // Disponible uniquement pendant la partie (vague ou inter-vague)
        if (arena.getState() != ArenaState.RUNNING && arena.getState() != ArenaState.BETWEEN_WAVES) {
            return;
        }

        if (villager.hasMetadata(fr.obsidianlabs.plugins.villagedefense.game.GameManager.SHOP_VILLAGER_META)) {
            plugin.getShopGui().open(player);
            return;
        }

        if (villager.hasMetadata(fr.obsidianlabs.plugins.villagedefense.game.GameManager.ENCHANT_VILLAGER_META)) {
            plugin.getEnchantGui().open(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMerchantInventoryOpen(InventoryOpenEvent event) {
        if (event.getInventory().getType() != InventoryType.MERCHANT) {
            return;
        }

        if (!(event.getInventory() instanceof MerchantInventory merchantInventory)) {
            return;
        }

        Merchant merchant = merchantInventory.getMerchant();
        if (!(merchant instanceof Villager villager)) {
            return;
        }

        if (!isProtectedVillager(villager)) {
            return;
        }

        event.setCancelled(true);
    }

    private boolean isProtectedVillager(Villager villager) {
        return villager.hasMetadata(fr.obsidianlabs.plugins.villagedefense.game.GameManager.VILLAGER_META)
            || villager.hasMetadata(fr.obsidianlabs.plugins.villagedefense.game.GameManager.SHOP_VILLAGER_META)
            || villager.hasMetadata(fr.obsidianlabs.plugins.villagedefense.game.GameManager.ENCHANT_VILLAGER_META);
    }

    // ---- Interaction unifiée (baguette setup + livre + slots de tours) ----

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        // Ignorer l'événement de la main secondaire — il double chaque clic
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        var heldItem = player.getInventory().getItemInMainHand();

        if (isArenaObserver(player)) {
            if ((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)
                    && heldItem.getType() == Material.RED_BED) {
                Arena observerArena = plugin.getArenaManager().getArenaOfParticipant(player);
                if (observerArena != null) {
                    event.setCancelled(true);
                    plugin.getArenaManager().leaveArena(player);
                    return;
                }
            }

            event.setCancelled(true);
            return;
        }

        // --- Bloquer l'ender chest en arène ---
        if ((event.getAction() == Action.RIGHT_CLICK_BLOCK) && event.getClickedBlock() != null
                && event.getClickedBlock().getType() == Material.ENDER_CHEST) {
            if (plugin.getArenaManager().getArenaOfParticipant(player) != null) {
                event.setCancelled(true);
                ChatUtil.send(player, m("arena.ender-chest-blocked"));
                return;
            }
        }

        // --- Hotbar schematic admin (priorité sur la baguette) ---
        {
            String schemAction = ArenaSetup.getSchemAction(plugin, player.getInventory().getItemInMainHand());
            if (schemAction != null && plugin.getArenaManager().isInSetup(player)) {
                ArenaSetup schemSetup = plugin.getArenaManager().getSetup(player);
                if (schemSetup != null) {
                    event.setCancelled(true);
                    schemSetup.onSchemItemClick(schemAction);
                    return;
                }
            }
        }

        // --- Baguette de setup admin ---
        if (plugin.getArenaManager().isInSetup(player) &&
            ArenaSetup.isWand(plugin, player.getInventory().getItemInMainHand())) {
            if (!plugin.hasPermission(player, Perm.ADMIN_SETUP)) {
                event.setCancelled(true);
                ChatUtil.send(player, plugin.getMessages().format("general.no-permission"));
                return;
            }
            event.setCancelled(true);
            handleSetupWand(event);
            return;
        }

        // --- Livres dans le lobby ---
        if ((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            var item = player.getInventory().getItemInMainHand();
            if (item.hasItemMeta()) {
                // Item quitter pour les spectateurs
                if (item.getType() == Material.RED_BED) {
                    Arena specArena = plugin.getArenaManager().getArenaOfSpectator(player);
                    if (specArena != null) {
                        event.setCancelled(true);
                        plugin.getArenaManager().leaveArena(player);
                        return;
                    }
                }
                Arena arena = plugin.getArenaManager().getArenaOfPlayer(player);
                if (arena != null && (arena.getState() == ArenaState.WAITING ||
                                      arena.getState() == ArenaState.STARTING)) {
                    if (item.getType() == Material.BOOK) {
                        event.setCancelled(true);
                        plugin.getKitGui().open(player);
                        return;
                    }
                    if (item.getType() == Material.WRITTEN_BOOK) {
                        event.setCancelled(true);
                        plugin.getAchievementGui().open(player);
                        return;
                    }
                    if (item.getType() == Material.RED_BED) {
                        event.setCancelled(true);
                        plugin.getArenaManager().leaveArena(player);
                        return;
                    }
                }
            }
        }

        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (plugin.getKitAbilityManager() != null
                    && plugin.getKitAbilityManager().handleAbilityUse(
                        player,
                        player.getInventory().getItemInMainHand(),
                        resolveAbilityTarget(player, event))) {
                event.setCancelled(true);
                return;
            }
        }

        // --- Clic droit sur un bloc en arène : vérifier les slots de tours ---
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Arena arena = plugin.getArenaManager().getArenaOfPlayer(player);
            if (arena == null) return;
            if (arena.getState() != ArenaState.RUNNING && arena.getState() != ArenaState.BETWEEN_WAVES) return;

            Location clickLoc = event.getClickedBlock().getLocation();

            // Vérifier d'abord les emplacements de tours
            TowerSlot slot = arena.getNearestSlot(clickLoc, 4.0);
            if (slot != null) {
                event.setCancelled(true);
                if (slot.isEmpty()) {
                    plugin.getTowerGui().openEmpty(player, arena, slot);
                } else {
                    plugin.getTowerGui().openPlaced(player, arena, slot);
                }
                return;
            }

            // Ensuite les emplacements de pièges
            TrapSlot trapSlot = arena.getNearestTrapSlot(clickLoc, 4.0);
            if (trapSlot != null) {
                event.setCancelled(true);
                plugin.getTrapGui().open(player, arena, trapSlot);
            }
        }
    }

    private Location resolveAbilityTarget(Player player, PlayerInteractEvent event) {
        if (event.getClickedBlock() != null) {
            return event.getClickedBlock().getLocation().add(0.5, 1.0, 0.5);
        }

        var result = player.getWorld().rayTraceBlocks(
            player.getEyeLocation(),
            player.getEyeLocation().getDirection(),
            25.0,
            FluidCollisionMode.NEVER,
            true
        );
        if (result != null && result.getHitPosition() != null) {
            return result.getHitPosition().toLocation(player.getWorld()).add(0.0, 0.05, 0.0);
        }
        return null;
    }

    private void handleSetupWand(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ArenaSetup setup = plugin.getArenaManager().getSetup(player);
        if (setup == null) return;
        // Si le hotbar schematic est actif, la baguette ne doit pas interférer
        if (setup.isSchematicHotbarActive()) return;

        boolean left  = event.getAction() == Action.LEFT_CLICK_AIR  || event.getAction() == Action.LEFT_CLICK_BLOCK;
        boolean right = event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK;

        // Clic droit sans SHIFT + pas en mode spécial → ouvrir le GUI de config
        if (right && !player.isSneaking()
                && setup.getMode() != ArenaSetup.WandMode.CHEST
                && setup.getMode() != ArenaSetup.WandMode.SHOP_VILLAGER
                && setup.getMode() != ArenaSetup.WandMode.ENCHANT_VILLAGER) {
            if (setup.getArena() != null) {
                // Session normale : ouvrir le GUI de config de l'arène
                plugin.getArenaSetupGui().open(player, setup.getArena());
            } else {
                // Session standalone (capture schematic sans arène) : ouvrir le GUI catégories
                plugin.getSchematicCategoryGui().open(player, null);
            }
            return;
        }

        // Mode CHEST : clic droit sur un coffre
        if (setup.getMode() == ArenaSetup.WandMode.CHEST && right && event.getClickedBlock() != null) {
            Block block = event.getClickedBlock();
            if (block.getState() instanceof Chest) {
                setup.onChestClick(block.getLocation());
            } else {
                ChatUtil.send(player, m("admin.setup.not-a-chest"));
            }
            return;
        }

        // Mode SIGN : clic droit sur un panneau
        if (setup.getMode() == ArenaSetup.WandMode.SIGN && right && event.getClickedBlock() != null) {
            event.setCancelled(true);
            Block block = event.getClickedBlock();
            if (block.getState() instanceof org.bukkit.block.Sign) {
                setup.onSignClick(block.getLocation());
            } else {
                ChatUtil.send(player, m("admin.setup.not-a-sign"));
            }
            return;
        }

        // Mode WAYPOINT : clic pour placer/supprimer des waypoints
        if (setup.getMode() == ArenaSetup.WandMode.WAYPOINT) {
            event.setCancelled(true);
            String pathId = setup.getEditingPathId();
            if (pathId == null) return;
            fr.obsidianlabs.plugins.villagedefense.arena.ArenaPath path = setup.getArena().getPath(pathId);
            if (path == null) return;
            if (left) {
                if (path.removeLastWaypoint()) {
                    ChatUtil.send(player, m("admin.paths.waypoint-removed", path.size()));
                } else {
                    ChatUtil.send(player, m("admin.paths.no-waypoints"));
                }
            } else if (right) {
                path.addWaypoint(player.getLocation());
                String coords = String.format("%.0f, %.0f, %.0f",
                    player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ());
                ChatUtil.send(player, m("admin.paths.waypoint-added", path.size(), coords));
            }
            return;
        }

        // Mode DEFAULT / BOUNDARY : déléguer à ArenaSetup
        org.bukkit.Location loc = event.getClickedBlock() != null
            ? event.getClickedBlock().getLocation()
            : player.getLocation();

        if (left)  setup.onLeftClick(loc);
        if (right) setup.onRightClick(loc);
    }

    // ---- Inventaire ----

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Bloquer toute interaction d'inventaire pendant qu'un hotbar schematic est actif
        {
            ArenaSetup schemSetup = plugin.getArenaManager().getSetup(player);
            if (schemSetup != null && schemSetup.isSchematicHotbarActive()) {
                event.setCancelled(true);
                return;
            }
        }

        // On n'intercepte que les inventaires custom (pas de location = pas un block)
        if (event.getInventory().getLocation() != null) return;

        // Récupérer le titre de l'inventaire ouvert
        String title = getOpenInventoryTitle(player);
        if (title == null) return;

        if (guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.ShopPricesGUI.TITLE)) {
            plugin.getShopPricesGui().handleClick(event, player);
        } else if (guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.ArenaSetupGUI.TITLE)) {
            plugin.getArenaSetupGui().handleClick(event, player);
        } else if (guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.ArenaPositionsGUI.TITLE)) {
            plugin.getArenaPositionsGui().handleClick(event, player);
        } else if (guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.ArenaGameplayLocationsGUI.TITLE)) {
            plugin.getArenaGameplayLocationsGui().handleClick(event, player);
        } else if (guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.ArenaOptionsGUI.TITLE)) {
            plugin.getArenaOptionsGui().handleClick(event, player);
        } else if (guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.KitGUI.TITLE_KEY)) {
            plugin.getKitGui().handleClick(event, player);
        } else if (guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.ShopGUI.TITLE)) {
            plugin.getShopGui().handleClick(event, player);
        } else if (guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.PerksGUI.TITLE)) {
            plugin.getPerksGui().handleClick(event, player);
        } else if (guiTitle(title, AchievementGUI.TITLE)) {
            plugin.getAchievementGui().handleClick(event, player);
        } else if (guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.AchievementAdminGUI.TITLE)) {
            plugin.getAchievementAdminGui().handleClick(event, player);
        } else if (guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.AchievementEditGUI.TITLE)) {
            plugin.getAchievementEditGui().handleClick(event, player);
        } else if (guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.LeaderboardGUI.TITLE)) {
            plugin.getLeaderboardGui().handleClick(event, player);
        } else if (guiTitle(title, TowerGUI.TITLE_EMPTY) || guiTitle(title, TowerGUI.TITLE_REPLACE)) {
            plugin.getTowerGui().handleClick(event, player, true);
        } else if (guiTitle(title, TowerGUI.TITLE_PLACED)) {
            plugin.getTowerGui().handleClick(event, player, false);
        } else if (guiTitle(title, VDAdminGUI.TITLE)) {
            plugin.getVdAdminGui().handleClick(event, player);
        } else if (guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.KitConfigGUI.TITLE_MAIN)) {
            plugin.getKitConfigGui().handleClick(event, player);
        } else if (guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.BungeeConfigGUI.TITLE)) {
            plugin.getBungeeConfigGui().handleClick(event, player);
        } else if (guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.NetworkConfigGUI.TITLE)) {
            plugin.getNetworkConfigGui().handleClick(event, player);
        } else if (guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.SettingsGUI.TITLE)) {
            plugin.getSettingsGui().handleClick(event, player);
        } else if (guiTitle(title, LanguageGUI.TITLE)) {
            plugin.getLanguageGui().handleClick(event, player);
        } else if (guiTitle(title, SchematicCategoryGUI.TITLE)) {
            plugin.getSchematicCategoryGui().handleClick(event, player);
        } else if (isSchematicLevelGuiTitle(title)) {
            plugin.getSchematicGui().handleClick(event, player);
        } else if (guiTitle(title, IconPickerGUI.TITLE_KEY)) {
            plugin.getIconPickerGui().handleClick(event, player);
        } else if (guiTitle(title, SpecializationGUI.TITLE)) {
            plugin.getSpecializationGui().handleClick(event, player);
        } else if (guiTitle(title, TrapGUI.TITLE)) {
            plugin.getTrapGui().handleClick(event, player);
        } else if (guiTitle(title, DatabaseGUI.TITLE)) {
            plugin.getDatabaseGui().handleClick(event, player);
        } else if (guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.EnchantPricesGUI.TITLE)) {
            plugin.getEnchantPricesGui().handleClick(event, player);
        } else if (guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.TowerConfigGUI.TITLE_MAIN)
                || guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.TowerConfigGUI.TITLE_DETAIL)) {
            plugin.getTowerConfigGui().handleClick(event, player);
        } else if (guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.EnchantGUI.TITLE)) {
            plugin.getEnchantGui().handleClick(event, player);
        } else if (guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.ArenaPathsGUI.TITLE)) {
            plugin.getArenaPathsGui().handleClick(event, player);
        } else if (guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.ArenaPathEditGUI.TITLE)) {
            plugin.getArenaPathEditGui().handleClick(event, player);
        } else if (guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.ArenaPathSpawnersGUI.TITLE)) {
            plugin.getArenaPathSpawnersGui().handleClick(event, player);
        } else if (guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.ArenaRewardsGUI.TITLE)) {
            plugin.getArenaRewardsGui().handleClick(event, player);
        }
    }

    // ---- Limites de l'arène ----

    /**
     * Empêche les joueurs de sortir des bornes pos1/pos2 pendant une partie.
     * Seule l'entrée dans un nouveau bloc est vérifiée (ignoreCancelled = optimisation).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Ignorer les simples rotations de caméra (aucun bloc traversé)
        if (!event.hasChangedBlock()) return;

        Player player = event.getPlayer();
        Arena arena = plugin.getArenaManager().getArenaOfParticipant(player);
        if (arena == null || !arena.hasBounds()) return;
        if (arena.getState() != ArenaState.RUNNING && arena.getState() != ArenaState.BETWEEN_WAVES) return;

        Location to   = event.getTo();
        Location pos1 = arena.getPos1();
        Location pos2 = arena.getPos2();

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        if (to.getX() < minX || to.getX() > maxX + 1
         || to.getY() < minY || to.getY() > maxY + 1
         || to.getZ() < minZ || to.getZ() > maxZ + 1) {
            event.setCancelled(true);
            if (arena.getBoundaryMode() == fr.obsidianlabs.plugins.villagedefense.arena.ArenaBoundaryMode.TP_CENTER
                    && arena.getVillageCenter() != null) {
                player.teleport(arena.getVillageCenter());
                ChatUtil.send(player, m("arena.boundary-tp"));
            } else {
                ChatUtil.send(player, m("arena.boundary"));
            }
        }
    }

    /**
     * Empêche le glisser-déposer d'items dans n'importe quel GUI custom du plugin.
     * Couvre les cas non interceptés par InventoryClickEvent (drag multi-slots).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getInventory().getLocation() != null) return; // inventaire physique, pas custom

        String title = getOpenInventoryTitle(player);
        if (title == null) return;

        // Annuler si le drag touche la partie supérieure (GUI custom)
        int topSize = event.getInventory().getSize();
        boolean touchesTop = event.getRawSlots().stream().anyMatch(s -> s < topSize);
        if (touchesTop && isCustomGuiTitle(title)) {
            event.setCancelled(true);
        }
    }

    /** Vérifie si le titre correspond à un GUI de niveaux de schematic (une catégorie). */
    private boolean isSchematicLevelGuiTitle(String title) {
        for (SchematicCategory cat : SchematicCategory.values()) {
            if (title.equals(m(fr.obsidianlabs.plugins.villagedefense.gui.SchematicGUI.TITLE,
                m(cat.getNameKey())))) return true;
        }
        return false;
    }

    private boolean isCustomGuiTitle(String title) {
        return guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.ShopGUI.TITLE)
            || guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.PerksGUI.TITLE)
            || guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.ShopPricesGUI.TITLE)
            || guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.KitGUI.TITLE_KEY)
            || guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.KitConfigGUI.TITLE_MAIN)
            || guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.BungeeConfigGUI.TITLE)
            || guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.AchievementGUI.TITLE)
            || guiTitle(title, VDAdminGUI.TITLE)
            || guiTitle(title, LanguageGUI.TITLE)
            || guiTitle(title, SchematicCategoryGUI.TITLE)
            || guiTitle(title, TowerGUI.TITLE_EMPTY)
            || guiTitle(title, TowerGUI.TITLE_PLACED)
            || guiTitle(title, TowerGUI.TITLE_REPLACE)
            || guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.TowerConfigGUI.TITLE_MAIN)
            || guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.TowerConfigGUI.TITLE_DETAIL)
            || guiTitle(title, SpecializationGUI.TITLE)
            || guiTitle(title, TrapGUI.TITLE)
            || guiTitle(title, DatabaseGUI.TITLE)
            || guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.ArenaPositionsGUI.TITLE)
            || guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.ArenaGameplayLocationsGUI.TITLE)
            || guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.ArenaOptionsGUI.TITLE)
            || guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.AchievementAdminGUI.TITLE)
            || guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.AchievementEditGUI.TITLE)
            || guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.ArenaPathsGUI.TITLE)
            || guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.ArenaPathEditGUI.TITLE)
            || guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.ArenaPathSpawnersGUI.TITLE)
            || guiTitle(title, fr.obsidianlabs.plugins.villagedefense.gui.ArenaRewardsGUI.TITLE)
            || isSchematicLevelGuiTitle(title);
    }

    // InventoryCloseEvent → géré par InventoryCloseListener (délègue à TowerGUI.onInventoryClose)

    // ---- Protection des bordures du coffre-shop ----

    /**
     * Bloque tout clic sur un slot bordure du coffre-shop d'arène (clic direct ou shift-clic).
     * Les bordures sont réservées aux séparateurs dans le ShopGUI et ne doivent pas contenir
     * d'articles pour ne pas perturber la mise en page du shop.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onShopChestClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        org.bukkit.inventory.Inventory topInv = event.getInventory();
        if (topInv.getLocation() == null) return; // GUI custom, pas un coffre physique
        if (!isArenaShopChest(topInv)) return;

        org.bukkit.inventory.Inventory clicked = event.getClickedInventory();

        // Clic direct sur un slot bordure dans le coffre
        if (clicked != null && clicked.equals(topInv) && isBorderSlot(event.getSlot(), topInv.getSize())) {
            event.setCancelled(true);
            ChatUtil.send(player, m("admin.setup.shop-chest-border-blocked"));
            return;
        }

        // Shift-clic depuis l'inventaire joueur : destination inconnue, bloquer par sécurité
        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                && clicked != null && !clicked.equals(topInv)) {
            event.setCancelled(true);
            ChatUtil.send(player, m("admin.setup.shop-chest-border-blocked"));
        }
    }

    /** Bloque le drag d'un item sur un slot bordure du coffre-shop. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onShopChestDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        org.bukkit.inventory.Inventory topInv = event.getInventory();
        if (topInv.getLocation() == null) return;
        if (!isArenaShopChest(topInv)) return;

        int topSize = topInv.getSize();
        boolean touchesBorder = event.getRawSlots().stream()
            .filter(s -> s < topSize)
            .anyMatch(s -> isBorderSlot(s, topSize));
        if (touchesBorder) {
            event.setCancelled(true);
            ChatUtil.send(player, m("admin.setup.shop-chest-border-blocked"));
        }
    }

    /** Retourne true si cet inventaire est le coffre-shop d'une arène. */
    private boolean isArenaShopChest(org.bukkit.inventory.Inventory inv) {
        var holder = inv.getHolder();
        java.util.List<Location> locs = new java.util.ArrayList<>();
        if (holder instanceof Chest chest)
            locs.add(chest.getLocation());
        else if (holder instanceof DoubleChest dc) {
            if (dc.getLeftSide()  instanceof Chest l) locs.add(l.getLocation());
            if (dc.getRightSide() instanceof Chest r) locs.add(r.getLocation());
        }
        for (Location loc : locs) {
            for (fr.obsidianlabs.plugins.villagedefense.arena.Arena arena : plugin.getArenaManager().getAllArenas()) {
                Location shop = arena.getShopChestLocation();
                if (shop == null) continue;
                if (loc.getWorld().equals(shop.getWorld())
                        && loc.getBlockX() == shop.getBlockX()
                        && loc.getBlockY() == shop.getBlockY()
                        && loc.getBlockZ() == shop.getBlockZ()) return true;
            }
        }
        return false;
    }

    /** Retourne true si le slot est sur la bordure (première/dernière rangée ou première/dernière colonne). */
    private boolean isBorderSlot(int slot, int invSize) {
        int rows = invSize / 9;
        int col  = slot % 9;
        int row  = slot / 9;
        return row == 0 || row == rows - 1 || col == 0 || col == 8;
    }

    // ---- Helpers ----

    private String getOpenInventoryTitle(Player player) {
        try {
            return player.getOpenInventory().getTitle();
        } catch (Exception e) {
            return null;
        }
    }
}
