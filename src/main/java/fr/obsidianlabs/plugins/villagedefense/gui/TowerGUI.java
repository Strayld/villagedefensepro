package fr.obsidianlabs.plugins.villagedefense.gui;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.player.VDPlayer;
import fr.obsidianlabs.plugins.villagedefense.tower.Tower;
import fr.obsidianlabs.plugins.villagedefense.tower.TowerSlot;
import fr.obsidianlabs.plugins.villagedefense.tower.TowerType;
import fr.obsidianlabs.plugins.villagedefense.util.ChatUtil;
import fr.obsidianlabs.plugins.villagedefense.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.*;

/**
 * GUI pour les emplacements de tours.
 *
 * Contexte stocké dans Map<UUID, TowerContext> côté serveur (pas de player-metadata).
 *
 * RÈGLE CRITIQUE : contexts.put() se fait APRÈS player.openInventory().
 * Bukkit/Paper fire InventoryCloseEvent de façon synchrone pendant openInventory()
 * (pour fermer l'inventaire précédent). Si on mettait le contexte AVANT, il serait
 * immédiatement supprimé par InventoryCloseListener. En le mettant APRÈS, l'event
 * n'a rien à supprimer et le contexte reste valide.
 */
public class TowerGUI {

    public static final String TITLE_EMPTY   = "tower.gui.title-empty";
    public static final String TITLE_PLACED  = "tower.gui.title-placed";
    public static final String TITLE_REPLACE = "tower.gui.title-replace";

    private final VillageDefensePlugin plugin;

    private record TowerContext(String arenaId, String slotId, boolean replace) {}
    private final Map<UUID, TowerContext> contexts = new HashMap<>();

    public TowerGUI(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    private String m(String key, Object... args) {
        return plugin.getMessages().format(key, args);
    }

    // ── Ouverture ─────────────────────────────────────────────────────────────

    public void openEmpty(Player player, Arena arena, TowerSlot slot) {
        VDPlayer vdp = plugin.getPlayerManager().getPlayer(player);
        if (vdp == null) return;

        Inventory inv = buildChooseInventory(TITLE_EMPTY, player, vdp, arena, null);

        // IMPORTANT : openInventory() AVANT contexts.put()
        // Bukkit fire InventoryCloseEvent pendant openInventory() → onInventoryClose() supprime
        // le contexte. En le mettant après, le remove() ne trouve rien à supprimer.
        player.openInventory(inv);
        contexts.put(player.getUniqueId(), new TowerContext(arena.getId(), slot.getId(), false));
    }

    public void openPlaced(Player player, Arena arena, TowerSlot slot) {
        Tower tower = slot.getPlacedTower();
        if (tower == null) { openEmpty(player, arena, slot); return; }

        VDPlayer vdp = plugin.getPlayerManager().getPlayer(player);
        if (vdp == null) return;

        Inventory inv = buildManageInventory(tower, player, vdp);

        player.openInventory(inv);
        contexts.put(player.getUniqueId(), new TowerContext(arena.getId(), slot.getId(), false));
    }

    /**
     * Flow Remplacer :
     * 1. Ferme le GUI de gestion → InventoryCloseEvent → contexts.remove()
     * 2. 1 tick plus tard : aucun inventaire ouvert → openInventory() ne fire pas de close event
     * 3. Context replace=true inséré APRÈS openInventory()
     */
    public void openReplace(Player player, Arena arena, TowerSlot slot) {
        player.closeInventory(); // fire close event → remove context proprement

        final String arenaId = arena.getId();
        final String slotId  = slot.getId();

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            VDPlayer vdp = plugin.getPlayerManager().getPlayer(player);
            if (vdp == null) return;

            Tower existing = slot.getPlacedTower();
            int refund = (existing != null) ? existing.getSellValue() : 0;

            Arena a = plugin.getArenaManager().getArena(arenaId);
            Inventory inv = buildChooseInventory(TITLE_REPLACE, player, vdp, a, refund);
            player.openInventory(inv);
            // Aucun inventaire précédent → pas de close event → on peut mettre après sans risque
            contexts.put(player.getUniqueId(), new TowerContext(arenaId, slotId, true));
        }, 1L);
    }

    /** Appelé par InventoryCloseListener à chaque fermeture. */
    public void onInventoryClose(Player player) {
        contexts.remove(player.getUniqueId());
    }

    // ── Builders ─────────────────────────────────────────────────────────────

    private Inventory buildChooseInventory(String title, Player player, VDPlayer vdp, Arena arena, Integer refundOrNull) {
        Inventory inv = Bukkit.createInventory(null, 54, m(title));
        for (int i = 0; i < 54; i++) inv.setItem(i, ItemBuilder.separator());

        inv.setItem(4, new ItemBuilder(Material.GOLD_INGOT)
            .name(m("tower.gui.coins-title", vdp.getCoins()))
            .build());

        // Séparateur visuel entre les deux rangées
        for (int i = 18; i < 27; i++)
            inv.setItem(i, ItemBuilder.separator(Material.BLACK_STAINED_GLASS_PANE));

        boolean isReplace = refundOrNull != null;
        // Rangée 1 : tours classiques
        inv.setItem(10, buildChoiceItem(TowerType.ARCHER,    player, vdp, arena, isReplace, refundOrNull));
        inv.setItem(13, buildChoiceItem(TowerType.TREBUCHET, player, vdp, arena, isReplace, refundOrNull));
        inv.setItem(16, buildChoiceItem(TowerType.MAGE,      player, vdp, arena, isReplace, refundOrNull));
        // Rangée 2 : nouvelles tours
        inv.setItem(28, buildChoiceItem(TowerType.ICE,       player, vdp, arena, isReplace, refundOrNull));
        inv.setItem(31, buildChoiceItem(TowerType.LIGHTNING, player, vdp, arena, isReplace, refundOrNull));
        inv.setItem(34, buildChoiceItem(TowerType.CANNON,    player, vdp, arena, isReplace, refundOrNull));
        return inv;
    }

    private Inventory buildManageInventory(Tower tower, Player player, VDPlayer vdp) {
        Inventory inv = Bukkit.createInventory(null, 27, m(TITLE_PLACED));
        for (int i = 0; i < 27; i++) inv.setItem(i, ItemBuilder.separator());

        inv.setItem(4, new ItemBuilder(tower.getType().getTopMaterial())
            .name(m("tower.gui.info-title", m(tower.getType().getNameKey()), tower.getLevel()))
            .lore(List.of(
                m("tower.gui.type",   m(tower.getType().getNameKey())),
                m("tower.gui.level",  tower.getLevel(), tower.getMaxLevel()),
                m("tower.gui.range",  fmt(tower.getRange())),
                m("tower.gui.damage", fmt(tower.getDamage()))
            ))
            .build());

        // Améliorer
        if (tower.canUpgrade()) {
            int nextLevel = tower.getLevel() + 1;
            int cost = plugin.getTowerManager().getEffectiveUpgradeCost(tower, player);
            boolean canAfford = vdp.getCoins() >= cost;

            double curDmg   = tower.getDamage();
            double nextDmg  = tower.getDamageAtLevel(nextLevel);
            double curRange = tower.getRange();
            double nextRange = tower.getRangeAtLevel(nextLevel);

            inv.setItem(10, new ItemBuilder(Material.ANVIL)
                .name(m("tower.gui.upgrade", canAfford ? "&a" : "&c", nextLevel))
                .lore(
                    m("tower.gui.upgrade-cost", canAfford ? "&6" : "&c", cost),
                    "",
                    m("tower.gui.damage-stats", fmt(curDmg), fmt(nextDmg), fmt(nextDmg - curDmg)),
                    m("tower.gui.range-stats",  fmt(curRange), fmt(nextRange), fmt(nextRange - curRange)),
                    "",
                    canAfford ? m("tower.gui.click-upgrade") : m("tower.gui.no-coins")
                )
                .build());
        } else if (!tower.hasSpecialization()) {
            List<fr.obsidianlabs.plugins.villagedefense.tower.TowerSpecialization> specs =
                fr.obsidianlabs.plugins.villagedefense.tower.TowerSpecialization.forType(tower.getType());
            String specsHint = specs.stream()
                .map(s -> "&e" + m(s.getNameKey()))
                .collect(java.util.stream.Collectors.joining("&7 ou "));
            inv.setItem(10, new ItemBuilder(Material.NETHER_STAR)
                .name(m("tower.gui.spec-choose"))
                .lore(m("tower.gui.spec-max"),
                      m("tower.gui.spec-unlock"),
                      "",
                      m("tower.gui.spec-choices", specsHint),
                      "",
                      m("tower.gui.spec-click"))
                .build());
        } else {
            fr.obsidianlabs.plugins.villagedefense.tower.TowerSpecialization spec = tower.getSpecialization();
            inv.setItem(10, new ItemBuilder(spec.getIcon())
                .name(m("tower.gui.spec-active", m(spec.getNameKey())))
                .lore(m(spec.getDescKey()),
                      "",
                      m("tower.gui.spec-active-lore"),
                      m("tower.gui.spec-change"))
                .build());
        }

        // Remplacer
        inv.setItem(13, new ItemBuilder(Material.COMPARATOR)
            .name(m("tower.gui.replace"))
            .lore(m("tower.gui.replace-lore1"),
                  m("tower.gui.replace-lore2"),
                  "", m("tower.gui.replace-click"))
            .build());

        // Vendre
        inv.setItem(16, new ItemBuilder(Material.RED_CONCRETE)
            .name(m("tower.gui.sell"))
            .lore(m("tower.gui.sell-refund", tower.getSellValue()),
                  m("tower.gui.sell-lore"),
                  "", m("tower.gui.sell-click"))
            .build());

        return inv;
    }

    private org.bukkit.inventory.ItemStack buildChoiceItem(
            TowerType type, Player player, VDPlayer vdp, Arena arena, boolean isReplace, Integer refund) {

        if (arena != null && arena.isTowerDisabled(type)) {
            return new ItemBuilder(Material.BARRIER)
                .name("&c" + m(type.getNameKey()))
                .lore(m(type.getDescKey()), "", m("tower.gui.disabled"))
                .build();
        }

        int baseCost = arena != null
            ? plugin.getTowerManager().getTowerCost(type, arena)
            : plugin.getTowerManager().getTowerCost(type);
        int cost = plugin.getKitAbilityManager() != null
            ? plugin.getKitAbilityManager().applyStructureDiscount(player, baseCost)
            : baseCost;
        long effective = isReplace ? Math.max(0L, (long) cost - (refund != null ? refund : 0)) : cost;
        boolean canAfford = vdp.getCoins() >= effective;

        int levelRequired = arena != null ? plugin.getTowerManager().getTowerLevelRequired(type, arena) : 1;
        boolean levelOk = vdp.getLevel() >= levelRequired;

        List<String> lore = new ArrayList<>();
        lore.add(m(type.getDescKey()));
        lore.add("");
        lore.add(m("tower.gui.range", plugin.getConfig()
            .getDouble("towers." + type.getConfigKey() + ".base-range", 15)));
        if (levelRequired > 1)
            lore.add(m("tower.gui.level-required", levelOk ? "&a" : "&c", levelRequired, vdp.getLevel()));
        if (isReplace && refund != null && refund > 0) {
            lore.add(m("tower.gui.refund", refund));
            lore.add(m("tower.gui.net-cost", canAfford ? "&6" : "&c", effective));
        } else {
            lore.add(m("tower.gui.cost", canAfford ? "&6" : "&c", cost));
        }
        lore.add("");
        if (!levelOk) lore.add(m("tower.gui.level-too-low"));
        else lore.add(canAfford ? m("tower.gui.click-choose") : m("tower.gui.no-coins"));

        boolean available = canAfford && levelOk;
        return new ItemBuilder(type.getTopMaterial())
            .name((available ? "&e" : "&c") + m(type.getNameKey()))
            .lore(lore)
            .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Formate un double : affiche un entier si la valeur est ronde, sinon 1 décimale. */
    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v)
                                  : String.format("%.1f", v);
    }

    // ── Gestion des clics ─────────────────────────────────────────────────────

    public void handleClick(InventoryClickEvent event, Player player, boolean isEmptySlot) {
        event.setCancelled(true);

        if (event.getCurrentItem() == null
            || event.getCurrentItem().getType() == Material.AIR
            || event.getCurrentItem().getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        TowerContext ctx = contexts.get(player.getUniqueId());
        if (ctx == null) return;

        Arena arena = plugin.getArenaManager().getArena(ctx.arenaId());
        if (arena == null) return;

        TowerSlot towerSlot = arena.getTowerSlots().stream()
            .filter(s -> s.getId().equals(ctx.slotId()))
            .findFirst().orElse(null);
        if (towerSlot == null) return;

        VDPlayer vdp = plugin.getPlayerManager().getPlayer(player);
        if (vdp == null) return;

        if (isEmptySlot) {
            handleChooseClick(event.getSlot(), player, arena, towerSlot, vdp, ctx.replace());
        } else {
            handleManageClick(event.getSlot(), player, arena, towerSlot, vdp);
        }
    }

    private void handleChooseClick(int slot, Player player, Arena arena,
                                   TowerSlot towerSlot, VDPlayer vdp, boolean isReplace) {
        TowerType type = switch (slot) {
            case 10 -> TowerType.ARCHER;
            case 13 -> TowerType.TREBUCHET;
            case 16 -> TowerType.MAGE;
            case 28 -> TowerType.ICE;
            case 31 -> TowerType.LIGHTNING;
            case 34 -> TowerType.CANNON;
            default -> null;
        };
        if (type == null) return;

        // Vérifications arena : tour désactivée ou level insuffisant
        if (arena.isTowerDisabled(type)) return;
        int levelRequired = plugin.getTowerManager().getTowerLevelRequired(type, arena);
        if (vdp.getLevel() < levelRequired) {
            ChatUtil.send(player, plugin.getMessages().get("tower.level-too-low", levelRequired, vdp.getLevel()));
            return;
        }

        if (isReplace) {
            Tower existing = towerSlot.getPlacedTower();
            int refund = (existing != null) ? existing.getSellValue() : 0;
            int cost   = plugin.getKitAbilityManager() != null
                ? plugin.getKitAbilityManager().applyStructureDiscount(player, plugin.getTowerManager().getTowerCost(type, arena))
                : plugin.getTowerManager().getTowerCost(type, arena);
            long net   = (long) cost - refund;

            if (net > 0 && vdp.getCoins() < net) {
                ChatUtil.send(player, plugin.getMessages().get("tower.not-enough-coins", cost));
                return;
            }
            if (existing != null) plugin.getTowerManager().removeTowerAndClear(arena, towerSlot);
            if (net > 0)      vdp.removeCoins((int) net);
            else if (net < 0) vdp.addCoins((int) -net);
            plugin.getTowerManager().placeTower(arena, towerSlot, type, player);
            ChatUtil.send(player, plugin.getMessages().get("tower.replaced", m(type.getNameKey())));
        } else {
            int cost = plugin.getKitAbilityManager() != null
                ? plugin.getKitAbilityManager().applyStructureDiscount(player, plugin.getTowerManager().getTowerCost(type, arena))
                : plugin.getTowerManager().getTowerCost(type, arena);
            if (!vdp.removeCoins(cost)) {
                ChatUtil.send(player, plugin.getMessages().get("tower.not-enough-coins", cost));
                return;
            }
            plugin.getTowerManager().placeTower(arena, towerSlot, type, player);
            ChatUtil.send(player, plugin.getMessages().get("tower.placed", m(type.getNameKey()), cost));
        }

        contexts.remove(player.getUniqueId());
        player.closeInventory();
    }

    private void handleManageClick(int slot, Player player, Arena arena,
                                   TowerSlot towerSlot, VDPlayer vdp) {
        Tower tower = towerSlot.getPlacedTower();
        if (tower == null) return;

        switch (slot) {
            case 10 -> {
                if (tower.canUpgrade()) {
                    int spent = plugin.getTowerManager().getEffectiveUpgradeCost(tower, player);
                    if (plugin.getTowerManager().upgradeTower(tower, player)) {
                        ChatUtil.send(player, plugin.getMessages().get("tower.upgraded",
                            tower.getLevel(), spent));
                        contexts.remove(player.getUniqueId());
                        player.closeInventory();
                    } else {
                        ChatUtil.send(player, plugin.getMessages().get("tower.not-enough-coins",
                            spent));
                    }
                } else {
                    // Tour au niveau max → ouvrir le GUI de spécialisation
                    final Arena finalArena = arena;
                    final TowerSlot finalSlot = towerSlot;
                    contexts.remove(player.getUniqueId());
                    player.closeInventory();
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline())
                            plugin.getSpecializationGui().open(player, finalArena, finalSlot);
                    }, 1L);
                }
            }
            case 13 -> openReplace(player, arena, towerSlot);
            case 16 -> {
                int refund = plugin.getTowerManager().sellTower(arena, towerSlot, player);
                if (refund >= 0) {
                    ChatUtil.send(player, plugin.getMessages().get("tower.sold", refund));
                    contexts.remove(player.getUniqueId());
                    player.closeInventory();
                }
            }
        }
    }
}
