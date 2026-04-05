package fr.obsidianlabs.plugins.villagedefense.gui;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaSetup;
import fr.obsidianlabs.plugins.villagedefense.gameplay.model.*;
import fr.obsidianlabs.plugins.villagedefense.util.ChatUtil;
import fr.obsidianlabs.plugins.villagedefense.util.ItemBuilder;
import fr.obsidianlabs.plugins.villagedefense.util.Perm;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Éditeur d'un MobEntry (weight, threat, amounts, wave range, enabled).
 *
 * Layout 36 slots :
 *  Rangée 1 : [.][ENABLED][WEIGHT][THREAT][MIN_AMT][MAX_AMT][MIN_WAVE][MAX_WAVE][.]
 *  Rangée 3 : [.][BACK][.][.][.][.][.][.][.]
 */
public class MobEntryEditorGUI {

    public static final String TITLE = "gui.gameplay.mob-entry-editor.title";

    private static final int SLOT_ENABLED   = 10;
    private static final int SLOT_WEIGHT    = 11;
    private static final int SLOT_THREAT    = 12;
    private static final int SLOT_MIN_AMT   = 13;
    private static final int SLOT_MAX_AMT   = 14;
    private static final int SLOT_MIN_WAVE  = 15;
    private static final int SLOT_MAX_WAVE  = 16;
    private static final int SLOT_BACK      = 28;

    /** Contexte d'édition par joueur : poolId|entryIndex */
    private final Map<UUID, long[]> context = new HashMap<>();

    private final VillageDefensePlugin plugin;

    public MobEntryEditorGUI(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    private String m(String key, Object... args) {
        return plugin.getMessages().format(key, args);
    }

    public void open(Player admin, Arena arena, String poolId, int entryIndex) {
        if (!plugin.hasPermission(admin, Perm.ADMIN_SETUP)) {
            ChatUtil.send(admin, m("general.no-permission"));
            return;
        }

        ArenaGameplaySettings settings = plugin.getArenaGameplayService().getSettings(arena.getId());
        MobPool pool = settings.getMobPools().get(poolId);
        if (pool == null || entryIndex >= pool.getEntries().size()) return;

        MobEntry e = pool.getEntries().get(entryIndex);
        context.put(admin.getUniqueId(), new long[]{poolId.hashCode(), entryIndex});

        Inventory inv = Bukkit.createInventory(null, 36, m(TITLE) + " - " + e.getDisplayName());
        for (int i = 0; i < 36; i++) inv.setItem(i, ItemBuilder.separator());

        inv.setItem(SLOT_ENABLED, new ItemBuilder(e.isEnabled() ? Material.LIME_DYE : Material.RED_DYE)
            .name("&fActivé: " + (e.isEnabled() ? "&aOui" : "&cNon"))
            .lore("", "&eClique pour basculer").build());

        inv.setItem(SLOT_WEIGHT, new ItemBuilder(Material.GOLD_NUGGET)
            .name("&6Poids (weight)")
            .lore("&7Plus le poids est élevé,", "&7plus le mob est souvent sélectionné.",
                  "", "&fValeur: &e" + e.getWeight(), "", "&eG/D: -1/+1  Shift: -5/+5").build());

        inv.setItem(SLOT_THREAT, new ItemBuilder(Material.BLAZE_POWDER)
            .name("&cThreat (coût budget)")
            .lore("&7Coût en budget quand ce mob spawne.",
                  "", "&fValeur: &e" + e.getThreat(), "", "&eG/D: -0.5/+0.5  Shift: -1/+1").build());

        inv.setItem(SLOT_MIN_AMT, new ItemBuilder(Material.LIME_CONCRETE)
            .name("&aQuantité min")
            .lore("&fValeur: &e" + e.getMinAmount(), "", "&eG/D: -1/+1").build());

        inv.setItem(SLOT_MAX_AMT, new ItemBuilder(Material.RED_CONCRETE)
            .name("&cQuantité max")
            .lore("&fValeur: &e" + e.getMaxAmount(), "", "&eG/D: -1/+1").build());

        inv.setItem(SLOT_MIN_WAVE, new ItemBuilder(Material.PAPER)
            .name("&7Vague min (apparition)")
            .lore("&fValeur: &e" + e.getMinWave(), "", "&eG/D: -1/+1  Shift: -5/+5").build());

        inv.setItem(SLOT_MAX_WAVE, new ItemBuilder(Material.BOOK)
            .name("&7Vague max (0 = illimité)")
            .lore("&fValeur: &e" + (e.getMaxWave() == 0 ? "Illimité" : e.getMaxWave()),
                  "", "&eG/D: -1/+1  Shift: -5/+5").build());

        inv.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW).name(m("admin.common.back")).build());

        // Store poolId as metadata for retrieval
        admin.openInventory(inv);
        context.put(admin.getUniqueId(), new long[]{entryIndex});
        // Re-store full context as string in a secondary map
        entryContext.put(admin.getUniqueId(), poolId + "|" + entryIndex);
    }

    private final Map<UUID, String> entryContext = new HashMap<>();

    public void handleClick(InventoryClickEvent event, Player admin) {
        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;
        if (event.getCurrentItem().getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        ArenaSetup setup = plugin.getArenaManager().getSetup(admin);
        if (setup == null) return;
        Arena arena = setup.getArena();

        String ctx = entryContext.get(admin.getUniqueId());
        if (ctx == null) return;
        String[] parts = ctx.split("\\|");
        if (parts.length != 2) return;
        String poolId = parts[0];
        int entryIndex;
        try { entryIndex = Integer.parseInt(parts[1]); } catch (NumberFormatException ex) { return; }

        if (event.getRawSlot() == SLOT_BACK) {
            entryContext.remove(admin.getUniqueId());
            plugin.getMobPoolEditorGui().open(admin, arena, poolId);
            return;
        }

        ArenaGameplaySettings settings = plugin.getArenaGameplayService().getSettings(arena.getId());
        MobPool pool = settings.getMobPools().get(poolId);
        if (pool == null || entryIndex >= pool.getEntries().size()) return;
        MobEntry e = pool.getEntries().get(entryIndex);

        ClickType click = event.getClick();
        boolean right = click.isRightClick();
        boolean shift = click.isShiftClick();

        switch (event.getRawSlot()) {
            case SLOT_ENABLED  -> e.setEnabled(!e.isEnabled());
            case SLOT_WEIGHT   -> e.setWeight(Math.max(1, e.getWeight() + (right ? 1 : -1) * (shift ? 5 : 1)));
            case SLOT_THREAT   -> e.setThreat(Math.max(0.1, round(e.getThreat() + (right ? 1 : -1) * (shift ? 1.0 : 0.5))));
            case SLOT_MIN_AMT  -> { int v = Math.max(1, e.getMinAmount() + (right ? 1 : -1)); e.setMinAmount(v); if (e.getMaxAmount() < v) e.setMaxAmount(v); }
            case SLOT_MAX_AMT  -> e.setMaxAmount(Math.max(e.getMinAmount(), e.getMaxAmount() + (right ? 1 : -1)));
            case SLOT_MIN_WAVE -> { int v = Math.max(1, e.getMinWave() + (right ? 1 : -1) * (shift ? 5 : 1)); e.setMinWave(v); }
            case SLOT_MAX_WAVE -> e.setMaxWave(Math.max(0, e.getMaxWave() + (right ? 1 : -1) * (shift ? 5 : 1)));
            default -> { return; }
        }

        plugin.getArenaGameplayService().saveSettings(arena.getId());
        open(admin, arena, poolId, entryIndex);
    }

    private double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
