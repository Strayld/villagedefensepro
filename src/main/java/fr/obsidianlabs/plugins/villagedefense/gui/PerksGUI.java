package fr.obsidianlabs.plugins.villagedefense.gui;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.perk.PerkManager;
import fr.obsidianlabs.plugins.villagedefense.perk.PerkType;
import fr.obsidianlabs.plugins.villagedefense.player.VDPlayer;
import fr.obsidianlabs.plugins.villagedefense.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

public class PerksGUI {

    public static final String TITLE = "gui.perks.title";

    /** Slots des 7 atouts dans la grille 6×9 */
    private static final int[] PERK_SLOTS = {10, 12, 14, 16, 22, 24, 30};

    private static final int BACK_SLOT    = 49;
    private static final int INFO_SLOT    = 4;

    private final VillageDefensePlugin plugin;

    public PerksGUI(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    private String m(String key, Object... args) {
        return plugin.getMessages().format(key, args);
    }

    // ── Ouverture ────────────────────────────────────────────────────────────

    public void open(Player player) {
        VDPlayer vdp = plugin.getPlayerManager().getPlayer(player);
        if (vdp == null) return;

        Inventory inv = Bukkit.createInventory(null, 54, m(TITLE));
        for (int i = 0; i < 54; i++) inv.setItem(i, ItemBuilder.separator());

        // En-tête : infos joueur
        inv.setItem(INFO_SLOT, new ItemBuilder(Material.EXPERIENCE_BOTTLE)
            .name(m("gui.perks.level-header", vdp.getLevel()))
            .lore(m("gui.perks.xp-label", vdp.getTotalXp()),
                  m("gui.perks.coins-label", vdp.getCoins()),
                  "",
                  m("gui.perks.next-level", xpForLevel(vdp.getLevel() + 1)))
            .build());

        PerkType[] types = PerkType.values();
        PerkManager pm = plugin.getPerkManager();
        Arena arena = plugin.getArenaManager().getArenaOfPlayer(player);

        for (int i = 0; i < types.length && i < PERK_SLOTS.length; i++) {
            PerkType type = types[i];
            inv.setItem(PERK_SLOTS[i], buildPerkItem(player, vdp, pm, type, arena));
        }

        // Bouton retour
        inv.setItem(BACK_SLOT, new ItemBuilder(Material.ARROW)
            .name(m("gui.perks.back-button"))
            .build());

        player.openInventory(inv);
    }

    private org.bukkit.inventory.ItemStack buildPerkItem(
            Player player, VDPlayer vdp, PerkManager pm, PerkType type, Arena arena) {

        int stacks    = pm.getStacks(player, type);
        long cost     = pm.getEffectiveCost(type, arena);
        boolean meetsLevel = vdp.getLevel() >= type.getLevelRequired();
        boolean canAfford  = vdp.getCoins() >= cost;

        int maxVillagers = plugin.getConfig().getInt("village.villager-count", 10);

        boolean maxed;
        if (type.isCompanion()) {
            maxed = pm.getCompanionCount(player) >= PerkManager.MAX_COMPANIONS;
        } else if (type == PerkType.VILLAGE_GUARD) {
            int lastWave = arena != null ? arena.getLastGuardPurchaseWave() : -1;
            int currentWave = arena != null ? arena.getCurrentWave() : 0;
            maxed = lastWave >= 0 && currentWave < lastWave + 5;
        } else if (type == PerkType.BUY_VILLAGER) {
            int villagerCount = arena != null ? arena.getVillagerCount() : maxVillagers;
            maxed = villagerCount >= maxVillagers;
        } else {
            maxed = type.getMaxStacks() > 0 && stacks >= type.getMaxStacks();
        }

        List<String> lore = new ArrayList<>();
        lore.add("&7" + m(type.getDescKey()));
        lore.add("");
        lore.add(m("gui.perks.level-required", meetsLevel ? "&a" : "&c", type.getLevelRequired()));
        lore.add(m("gui.perks.cost", cost) + (type == PerkType.WAVE_SKIP ? m("gui.perks.wave-cost") : ""));

        if (!type.isCompanion() && type != PerkType.BUY_VILLAGER && type.getMaxStacks() > 0) {
            lore.add(m("gui.perks.stacks", stacks, type.getMaxStacks()));
        }
        if (type.isCompanion()) {
            lore.add(m("gui.perks.companions", pm.getCompanionCount(player), PerkManager.MAX_COMPANIONS));
        }
        if (type == PerkType.BUY_VILLAGER) {
            int villagerCount = arena != null ? arena.getVillagerCount() : 0;
            lore.add(m("gui.perks.villagers", villagerCount, maxVillagers));
        }
        if (type == PerkType.VILLAGE_GUARD) {
            int lastWave = arena != null ? arena.getLastGuardPurchaseWave() : -1;
            int currentWave = arena != null ? arena.getCurrentWave() : 0;
            if (lastWave >= 0 && currentWave < lastWave + 5) {
                lore.add(m("gui.perks.cooldown-wave", lastWave + 5));
            } else {
                lore.add(m("gui.perks.available"));
            }
        }
        lore.add("");

        if (!meetsLevel) {
            lore.add(m("gui.perks.level-too-low", type.getLevelRequired()));
        } else if (maxed) {
            lore.add(m("gui.perks.maxed"));
        } else if (!canAfford) {
            lore.add(m("gui.perks.no-coins"));
        } else {
            lore.add(m("gui.perks.click-buy"));
        }

        Material icon = (!meetsLevel || maxed) ? Material.BARRIER : type.getIcon();
        String nameColor = (!meetsLevel || maxed) ? "&8" : (canAfford ? "&d" : "&c");

        return new ItemBuilder(icon)
            .name(nameColor + m(type.getNameKey()))
            .lore(lore)
            .build();
    }

    // ── Clics ────────────────────────────────────────────────────────────────

    public void handleClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        int slot = event.getSlot();

        if (slot == BACK_SLOT) {
            plugin.getShopGui().open(player);
            return;
        }

        PerkType[] types = PerkType.values();
        for (int i = 0; i < PERK_SLOTS.length && i < types.length; i++) {
            if (slot == PERK_SLOTS[i]) {
                Arena arena = plugin.getArenaManager().getArenaOfPlayer(player);
                if (plugin.getPerkManager().purchase(player, arena, types[i])) {
                    open(player); // rafraîchir
                }
                return;
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** XP total nécessaire pour atteindre le niveau {@code level}. */
    private static long xpForLevel(int level) {
        if (level <= 1) return 0;
        return (long) Math.pow(level - 1, 2) * 100;
    }
}
