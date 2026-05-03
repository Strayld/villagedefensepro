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
 * Éditeur d'un WaveCategoryTemplate.
 *
 * Layout 36 slots :
 *  Rangée 1 : [.][ENABLED][POOL][MODE][BUDGET_MULT][HEALTH][DAMAGE][SPEED][.]
 *  Rangée 2 : [.][MAX_TYPES][DEFAULT_COUNT][.][.][.][.][.][.]
 *  Rangée 3 : [.][BACK][.][.][.][.][.][.][.]
 */
public class WaveCategoryEditorGUI {

    public static final String TITLE = "gui.gameplay.wave-category-editor.title";

    private static final int SLOT_ENABLED       = 10;
    private static final int SLOT_POOL          = 11;
    private static final int SLOT_MODE          = 12;
    private static final int SLOT_BUDGET_MULT   = 13;
    private static final int SLOT_HEALTH        = 14;
    private static final int SLOT_DAMAGE        = 15;
    private static final int SLOT_SPEED         = 16;
    private static final int SLOT_MAX_TYPES     = 19;
    private static final int SLOT_DEFAULT_COUNT = 20;
    private static final int SLOT_BACK          = 28;

    /** Stocke la catégorie en cours d'édition par joueur. */
    private final Map<UUID, WaveCategory> editingCategory = new HashMap<>();

    private final VillageDefensePlugin plugin;

    public WaveCategoryEditorGUI(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    private String m(String key, Object... args) {
        return plugin.getMessages().format(key, args);
    }

    public void open(Player admin, Arena arena, WaveCategory category) {
        if (!plugin.hasPermission(admin, Perm.ADMIN_SETUP)) {
            ChatUtil.send(admin, m("general.no-permission"));
            return;
        }

        editingCategory.put(admin.getUniqueId(), category);

        ArenaGameplaySettings settings = plugin.getArenaGameplayService().getSettings(arena.getId());
        WaveCategoryTemplate t = settings.getCategoryTemplates()
            .computeIfAbsent(category, WaveCategoryTemplate::new);

        Inventory inv = Bukkit.createInventory(null, 36, m(TITLE) + " - " + category.name());
        for (int i = 0; i < 36; i++) inv.setItem(i, ItemBuilder.separator());

        inv.setItem(SLOT_ENABLED, new ItemBuilder(t.isEnabled() ? Material.LIME_DYE : Material.RED_DYE)
            .name(m("gui.gameplay.wave-category-editor.enabled", m(t.isEnabled() ? "gui.gameplay.common.yes" : "gui.gameplay.common.no")))
            .lore("", m("gui.gameplay.common.click-toggle"))
            .build());

        inv.setItem(SLOT_POOL, new ItemBuilder(Material.ZOMBIE_SPAWN_EGG)
            .name(m("gui.gameplay.wave-category-editor.assigned-pool"))
            .lore(m("gui.gameplay.wave-category-editor.current", t.getMobPoolId() != null ? t.getMobPoolId() : m("gui.gameplay.common.none")),
                  "", m("gui.gameplay.common.click-select"))
            .build());

        CompositionMode[] modes = CompositionMode.values();
        CompositionMode nextMode = modes[(t.getCompositionMode().ordinal() + 1) % modes.length];
        inv.setItem(SLOT_MODE, new ItemBuilder(Material.REPEATER)
            .name(m("gui.gameplay.wave-category-editor.composition-mode"))
            .lore(m("gui.gameplay.wave-category-editor.current", t.getCompositionMode()),
                  m("gui.gameplay.wave-category-editor.next", nextMode),
                  "", m("gui.gameplay.common.click-change"))
            .build());

        inv.setItem(SLOT_BUDGET_MULT, multItem(m("gui.gameplay.wave-category-editor.budget-mult"), t.getCategoryBudgetMultiplier()));
        inv.setItem(SLOT_HEALTH,      multItem(m("gui.gameplay.wave-category-editor.health-mult"), t.getHealthMultiplier()));
        inv.setItem(SLOT_DAMAGE,      multItem(m("gui.gameplay.wave-category-editor.damage-mult"), t.getDamageMultiplier()));
        inv.setItem(SLOT_SPEED,       multItem(m("gui.gameplay.wave-category-editor.speed-mult"), t.getSpeedMultiplier()));

        inv.setItem(SLOT_MAX_TYPES, new ItemBuilder(Material.BOOK)
            .name(m("gui.gameplay.wave-category-editor.max-mob-types"))
            .lore(m("gui.gameplay.common.value", t.getMaxMobTypes()),
                  "", m("gui.gameplay.common.left-right-1"))
            .build());

        inv.setItem(SLOT_DEFAULT_COUNT, new ItemBuilder(Material.PAPER)
            .name(m("gui.gameplay.wave-category-editor.default-mob-count"))
            .lore(m("gui.gameplay.common.value", t.getDefaultMobCount()),
                  "", m("gui.gameplay.common.left-right-1"))
            .build());

        inv.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW).name(m("admin.common.back")).build());

        admin.openInventory(inv);
    }

    private org.bukkit.inventory.ItemStack multItem(String name, double value) {
        return new ItemBuilder(Material.COMPARATOR)
            .name(name)
            .lore(m("gui.gameplay.common.value", String.format("%.2f", value)),
                  "", m("gui.gameplay.wave-category-editor.mult-controls"))
            .build();
    }

    public void handleClick(InventoryClickEvent event, Player admin) {
        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;
        if (event.getCurrentItem().getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        ArenaSetup setup = plugin.getArenaManager().getSetup(admin);
        if (setup == null) return;
        Arena arena = setup.getArena();

        WaveCategory category = editingCategory.get(admin.getUniqueId());
        if (category == null) return;

        if (event.getRawSlot() == SLOT_BACK) {
            editingCategory.remove(admin.getUniqueId());
            plugin.getWaveCategoriesGui().open(admin, arena);
            return;
        }

        ArenaGameplaySettings settings = plugin.getArenaGameplayService().getSettings(arena.getId());
        WaveCategoryTemplate t = settings.getCategoryTemplates().get(category);
        if (t == null) return;

        ClickType click = event.getClick();
        boolean right = click.isRightClick();
        boolean shift = click.isShiftClick();
        double delta = shift ? 0.1 : 0.05;

        switch (event.getRawSlot()) {
            case SLOT_ENABLED -> t.setEnabled(!t.isEnabled());
            case SLOT_POOL -> {
                admin.closeInventory();
                ChatUtil.send(admin, m("gui.gameplay.wave-category-editor.enter-pool-id"));
                plugin.getChatInputManager().prompt(admin, m("gui.gameplay.wave-category-editor.pool-id-prompt"), input -> {
                    if (!input.equalsIgnoreCase("none")) {
                        t.setMobPoolId(input.trim());
                    } else {
                        t.setMobPoolId(null);
                    }
                    plugin.getArenaGameplayService().saveSettings(arena.getId());
                    open(admin, arena, category);
                });
                return;
            }
            case SLOT_MODE -> {
                CompositionMode[] modes = CompositionMode.values();
                t.setCompositionMode(modes[(t.getCompositionMode().ordinal() + 1) % modes.length]);
            }
            case SLOT_BUDGET_MULT   -> t.setCategoryBudgetMultiplier(Math.max(0.01, round(t.getCategoryBudgetMultiplier() + (right ? delta : -delta))));
            case SLOT_HEALTH        -> t.setHealthMultiplier(Math.max(0.01, round(t.getHealthMultiplier() + (right ? delta : -delta))));
            case SLOT_DAMAGE        -> t.setDamageMultiplier(Math.max(0.01, round(t.getDamageMultiplier() + (right ? delta : -delta))));
            case SLOT_SPEED         -> t.setSpeedMultiplier(Math.max(0.01, round(t.getSpeedMultiplier() + (right ? delta : -delta))));
            case SLOT_MAX_TYPES     -> t.setMaxMobTypes(Math.max(1, t.getMaxMobTypes() + (right ? 1 : -1)));
            case SLOT_DEFAULT_COUNT -> t.setDefaultMobCount(Math.max(1, t.getDefaultMobCount() + (right ? 1 : -1)));
            default -> { return; }
        }

        plugin.getArenaGameplayService().saveSettings(arena.getId());
        open(admin, arena, category);
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
