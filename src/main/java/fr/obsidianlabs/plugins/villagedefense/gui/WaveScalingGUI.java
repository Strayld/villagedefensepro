package fr.obsidianlabs.plugins.villagedefense.gui;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaSetup;
import fr.obsidianlabs.plugins.villagedefense.gameplay.model.WaveScalingSettings;
import fr.obsidianlabs.plugins.villagedefense.util.ChatUtil;
import fr.obsidianlabs.plugins.villagedefense.util.ItemBuilder;
import fr.obsidianlabs.plugins.villagedefense.util.Perm;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Édition du WaveScalingSettings.
 *
 * Layout 54 slots (6 rangées) :
 *  Rangée 0 : séparateurs
 *  Rangée 1 : BASE_BUDGET | LINEAR | EXPO | BASE_HP | BASE_DMG | BASE_SPD | - | - | -
 *  Rangée 2 : HP_WAVE | DMG_WAVE | SPD_WAVE | BOSS_INT | ELITE_INT | SPEC_INT | - | - | -
 *  Rangée 3 : EARLY_TYPES | MID_TYPES | LATE_TYPES | MID_WAVE | LATE_WAVE | - | - | - | -
 *  Rangée 4 : séparateurs
 *  Rangée 5 : BACK
 */
public class WaveScalingGUI {

    public static final String TITLE = "gui.gameplay.wave-scaling.title";

    // Row 1 (slots 9-17)
    private static final int SLOT_BASE_BUDGET    = 10;
    private static final int SLOT_LINEAR_GROWTH  = 11;
    private static final int SLOT_EXPO_GROWTH    = 12;
    private static final int SLOT_BASE_HEALTH    = 13;
    private static final int SLOT_BASE_DAMAGE    = 14;
    private static final int SLOT_BASE_SPEED     = 15;

    // Row 2 (slots 18-26)
    private static final int SLOT_HEALTH_WAVE    = 19;
    private static final int SLOT_DAMAGE_WAVE    = 20;
    private static final int SLOT_SPEED_WAVE     = 21;
    private static final int SLOT_BOSS_INTERVAL  = 22;
    private static final int SLOT_ELITE_INTERVAL = 23;
    private static final int SLOT_SPEC_INTERVAL  = 24;

    // Row 3 (slots 27-35)
    private static final int SLOT_EARLY_TYPES    = 28;
    private static final int SLOT_MID_TYPES      = 29;
    private static final int SLOT_LATE_TYPES     = 30;
    private static final int SLOT_MID_WAVE       = 31;
    private static final int SLOT_LATE_WAVE      = 32;

    // Row 5
    private static final int SLOT_BACK           = 46;

    private final VillageDefensePlugin plugin;

    public WaveScalingGUI(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    private String m(String key, Object... args) {
        return plugin.getMessages().format(key, args);
    }

    public void open(Player admin, Arena arena) {
        if (!plugin.hasPermission(admin, Perm.ADMIN_SETUP)) {
            ChatUtil.send(admin, m("general.no-permission"));
            return;
        }

        WaveScalingSettings s = plugin.getArenaGameplayService().getSettings(arena.getId()).getWaveScalingSettings();

        Inventory inv = Bukkit.createInventory(null, 54, m(TITLE));
        for (int i = 0; i < 54; i++) inv.setItem(i, ItemBuilder.separator());
        for (int i = 36; i < 45; i++) inv.setItem(i, ItemBuilder.separator(Material.BLACK_STAINED_GLASS_PANE));

        inv.setItem(SLOT_BASE_BUDGET,    numItem(Material.GOLD_INGOT,    m("gui.gameplay.wave-scaling.base-budget"),     s.getBaseBudget(),           m("gui.gameplay.wave-scaling.base-budget-desc")));
        inv.setItem(SLOT_LINEAR_GROWTH,  numItem(Material.IRON_INGOT,    m("gui.gameplay.wave-scaling.linear-growth"),   s.getLinearGrowth(),         m("gui.gameplay.wave-scaling.linear-growth-desc")));
        inv.setItem(SLOT_EXPO_GROWTH,    numItem(Material.DIAMOND,       m("gui.gameplay.wave-scaling.expo-growth"),     s.getExponentialGrowth(),    m("gui.gameplay.wave-scaling.expo-growth-desc")));
        inv.setItem(SLOT_BASE_HEALTH,    numItem(Material.RED_DYE,       m("gui.gameplay.wave-scaling.base-health"),     s.getBaseHealthMultiplier(), m("gui.gameplay.wave-scaling.base-health-desc")));
        inv.setItem(SLOT_BASE_DAMAGE,    numItem(Material.ORANGE_DYE,    m("gui.gameplay.wave-scaling.base-damage"),     s.getBaseDamageMultiplier(), m("gui.gameplay.wave-scaling.base-damage-desc")));
        inv.setItem(SLOT_BASE_SPEED,     numItem(Material.YELLOW_DYE,    m("gui.gameplay.wave-scaling.base-speed"),      s.getBaseSpeedMultiplier(),  m("gui.gameplay.wave-scaling.base-speed-desc")));

        inv.setItem(SLOT_HEALTH_WAVE,    numItem(Material.PINK_DYE,      m("gui.gameplay.wave-scaling.health-wave"),     s.getHealthPerWave(),        m("gui.gameplay.wave-scaling.health-wave-desc")));
        inv.setItem(SLOT_DAMAGE_WAVE,    numItem(Material.ORANGE_TULIP,  m("gui.gameplay.wave-scaling.damage-wave"),     s.getDamagePerWave(),        m("gui.gameplay.wave-scaling.damage-wave-desc")));
        inv.setItem(SLOT_SPEED_WAVE,     numItem(Material.DANDELION,     m("gui.gameplay.wave-scaling.speed-wave"),      s.getSpeedPerWave(),         m("gui.gameplay.wave-scaling.speed-wave-desc")));
        inv.setItem(SLOT_BOSS_INTERVAL,  intItem(Material.WITHER_SKELETON_SKULL, m("gui.gameplay.wave-scaling.boss-interval"),  s.getBossInterval(),    m("gui.gameplay.wave-scaling.interval-desc")));
        inv.setItem(SLOT_ELITE_INTERVAL, intItem(Material.IRON_CHESTPLATE,       m("gui.gameplay.wave-scaling.elite-interval"), s.getEliteInterval(),   m("gui.gameplay.wave-scaling.interval-desc")));
        inv.setItem(SLOT_SPEC_INTERVAL,  intItem(Material.NETHER_STAR,           m("gui.gameplay.wave-scaling.special-interval"), s.getSpecialInterval(), m("gui.gameplay.wave-scaling.interval-desc")));

        inv.setItem(SLOT_EARLY_TYPES,    intItem(Material.LIME_CONCRETE,   m("gui.gameplay.wave-scaling.early-types"), s.getEarlyGameMaxMobTypes(), m("gui.gameplay.wave-scaling.early-types-desc")));
        inv.setItem(SLOT_MID_TYPES,      intItem(Material.YELLOW_CONCRETE, m("gui.gameplay.wave-scaling.mid-types"),   s.getMidGameMaxMobTypes(),   m("gui.gameplay.wave-scaling.mid-types-desc")));
        inv.setItem(SLOT_LATE_TYPES,     intItem(Material.RED_CONCRETE,    m("gui.gameplay.wave-scaling.late-types"),  s.getLateGameMaxMobTypes(),  m("gui.gameplay.wave-scaling.late-types-desc")));
        inv.setItem(SLOT_MID_WAVE,       intItem(Material.LIME_DYE,        m("gui.gameplay.wave-scaling.mid-wave"),    s.getMidGameStartWave(),     m("gui.gameplay.wave-scaling.start-wave-desc")));
        inv.setItem(SLOT_LATE_WAVE,      intItem(Material.RED_DYE,         m("gui.gameplay.wave-scaling.late-wave"),   s.getLateGameStartWave(),    m("gui.gameplay.wave-scaling.start-wave-desc")));

        inv.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW).name(m("admin.common.back")).build());

        admin.openInventory(inv);
    }

    private ItemStack numItem(Material mat, String name, double value, String desc) {
        return new ItemBuilder(mat)
            .name(name)
            .lore("&7" + desc, "", m("gui.gameplay.common.value", String.format("%.4f", value)),
                  "", m("gui.gameplay.wave-scaling.double-controls-1"),
                  m("gui.gameplay.wave-scaling.double-controls-2"),
                  m("gui.gameplay.wave-scaling.double-controls-3"))
            .build();
    }

    private ItemStack intItem(Material mat, String name, int value, String desc) {
        return new ItemBuilder(mat)
            .name(name)
            .lore("&7" + desc, "", m("gui.gameplay.common.value", value),
                  "", m("gui.gameplay.wave-scaling.int-controls-1"),
                  m("gui.gameplay.wave-scaling.int-controls-2"))
            .build();
    }

    public void handleClick(InventoryClickEvent event, Player admin) {
        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;
        if (event.getCurrentItem().getType() == Material.GRAY_STAINED_GLASS_PANE
                || event.getCurrentItem().getType() == Material.BLACK_STAINED_GLASS_PANE) return;

        ArenaSetup setup = plugin.getArenaManager().getSetup(admin);
        if (setup == null) return;
        Arena arena = setup.getArena();

        if (event.getRawSlot() == SLOT_BACK) {
            plugin.getGameplayMainGui().open(admin, arena);
            return;
        }

        WaveScalingSettings s = plugin.getArenaGameplayService().getSettings(arena.getId()).getWaveScalingSettings();
        ClickType click = event.getClick();
        boolean shift = click.isShiftClick();
        boolean right = click.isRightClick();
        boolean middle = click == ClickType.MIDDLE;

        switch (event.getRawSlot()) {
            case SLOT_BASE_BUDGET    -> { if (middle) promptDouble(admin, arena, "baseBudget", s); else s.setBaseBudget(adj(s.getBaseBudget(), right, shift, 0.01, 0.1)); }
            case SLOT_LINEAR_GROWTH  -> { if (middle) promptDouble(admin, arena, "linearGrowth", s); else s.setLinearGrowth(adj(s.getLinearGrowth(), right, shift, 0.01, 0.1)); }
            case SLOT_EXPO_GROWTH    -> { if (middle) promptDouble(admin, arena, "exponentialGrowth", s); else s.setExponentialGrowth(adj(s.getExponentialGrowth(), right, shift, 0.001, 0.01)); }
            case SLOT_BASE_HEALTH    -> { if (middle) promptDouble(admin, arena, "baseHealth", s); else s.setBaseHealthMultiplier(adj(s.getBaseHealthMultiplier(), right, shift, 0.01, 0.1)); }
            case SLOT_BASE_DAMAGE    -> { if (middle) promptDouble(admin, arena, "baseDamage", s); else s.setBaseDamageMultiplier(adj(s.getBaseDamageMultiplier(), right, shift, 0.01, 0.1)); }
            case SLOT_BASE_SPEED     -> { if (middle) promptDouble(admin, arena, "baseSpeed", s); else s.setBaseSpeedMultiplier(adj(s.getBaseSpeedMultiplier(), right, shift, 0.01, 0.1)); }
            case SLOT_HEALTH_WAVE    -> s.setHealthPerWave(adj(s.getHealthPerWave(), right, shift, 0.001, 0.01));
            case SLOT_DAMAGE_WAVE    -> s.setDamagePerWave(adj(s.getDamagePerWave(), right, shift, 0.001, 0.01));
            case SLOT_SPEED_WAVE     -> s.setSpeedPerWave(adj(s.getSpeedPerWave(), right, shift, 0.001, 0.01));
            case SLOT_BOSS_INTERVAL  -> s.setBossInterval(Math.max(0, s.getBossInterval() + (right ? 1 : -1) * (shift ? 5 : 1)));
            case SLOT_ELITE_INTERVAL -> s.setEliteInterval(Math.max(0, s.getEliteInterval() + (right ? 1 : -1) * (shift ? 5 : 1)));
            case SLOT_SPEC_INTERVAL  -> s.setSpecialInterval(Math.max(0, s.getSpecialInterval() + (right ? 1 : -1) * (shift ? 5 : 1)));
            case SLOT_EARLY_TYPES    -> s.setEarlyGameMaxMobTypes(Math.max(1, s.getEarlyGameMaxMobTypes() + (right ? 1 : -1)));
            case SLOT_MID_TYPES      -> s.setMidGameMaxMobTypes(Math.max(1, s.getMidGameMaxMobTypes() + (right ? 1 : -1)));
            case SLOT_LATE_TYPES     -> s.setLateGameMaxMobTypes(Math.max(1, s.getLateGameMaxMobTypes() + (right ? 1 : -1)));
            case SLOT_MID_WAVE       -> s.setMidGameStartWave(Math.max(1, s.getMidGameStartWave() + (right ? 1 : -1) * (shift ? 5 : 1)));
            case SLOT_LATE_WAVE      -> s.setLateGameStartWave(Math.max(s.getMidGameStartWave(), s.getLateGameStartWave() + (right ? 1 : -1) * (shift ? 5 : 1)));
            default -> { return; }
        }

        if (!middle) {
            plugin.getArenaGameplayService().saveSettings(arena.getId());
            open(admin, arena);
        }
    }

    private double adj(double current, boolean increase, boolean shift, double small, double large) {
        double delta = shift ? large : small;
        double result = increase ? current + delta : current - delta;
        return Math.max(0.0, Math.round(result * 10000.0) / 10000.0);
    }

    private void promptDouble(Player admin, Arena arena, String field, WaveScalingSettings s) {
        admin.closeInventory();
        plugin.getChatInputManager().prompt(admin, m("gui.gameplay.wave-scaling.prompt", field), input -> {
            try {
                double val = Double.parseDouble(input.trim());
                applyDouble(field, val, s);
                plugin.getArenaGameplayService().saveSettings(arena.getId());
                ChatUtil.send(admin, m("gui.gameplay.wave-scaling.value-set", field, val));
            } catch (NumberFormatException e) {
                ChatUtil.send(admin, m("gui.gameplay.common.invalid-number"));
            }
            open(admin, arena);
        });
    }

    private void applyDouble(String field, double val, WaveScalingSettings s) {
        switch (field) {
            case "baseBudget"          -> s.setBaseBudget(val);
            case "linearGrowth"        -> s.setLinearGrowth(val);
            case "exponentialGrowth"   -> s.setExponentialGrowth(val);
            case "baseHealth"          -> s.setBaseHealthMultiplier(val);
            case "baseDamage"          -> s.setBaseDamageMultiplier(val);
            case "baseSpeed"           -> s.setBaseSpeedMultiplier(val);
        }
    }
}
