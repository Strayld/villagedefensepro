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
            .name("&fActivé: " + (t.isEnabled() ? "&aOui" : "&cNon"))
            .lore("", "&eClique pour basculer")
            .build());

        inv.setItem(SLOT_POOL, new ItemBuilder(Material.ZOMBIE_SPAWN_EGG)
            .name("&ePool assigné")
            .lore("&7Actuel: &f" + (t.getMobPoolId() != null ? t.getMobPoolId() : "&cAucun"),
                  "", "&eClique pour choisir")
            .build());

        CompositionMode[] modes = CompositionMode.values();
        CompositionMode nextMode = modes[(t.getCompositionMode().ordinal() + 1) % modes.length];
        inv.setItem(SLOT_MODE, new ItemBuilder(Material.REPEATER)
            .name("&eMode de composition")
            .lore("&7Actuel: &f" + t.getCompositionMode(),
                  "&7Suivant: &7" + nextMode,
                  "", "&eClique pour changer")
            .build());

        inv.setItem(SLOT_BUDGET_MULT, multItem("&6Multiplicateur budget", t.getCategoryBudgetMultiplier()));
        inv.setItem(SLOT_HEALTH,      multItem("&cMultiplicateur HP",     t.getHealthMultiplier()));
        inv.setItem(SLOT_DAMAGE,      multItem("&6Multiplicateur dégâts", t.getDamageMultiplier()));
        inv.setItem(SLOT_SPEED,       multItem("&eMultiplicateur vitesse",t.getSpeedMultiplier()));

        inv.setItem(SLOT_MAX_TYPES, new ItemBuilder(Material.BOOK)
            .name("&aMax types de mobs")
            .lore("&7Valeur: &e" + t.getMaxMobTypes(),
                  "", "&eG/D: -1/+1")
            .build());

        inv.setItem(SLOT_DEFAULT_COUNT, new ItemBuilder(Material.PAPER)
            .name("&7Nombre de mobs par défaut")
            .lore("&7Valeur: &e" + t.getDefaultMobCount(),
                  "", "&eG/D: -1/+1")
            .build());

        inv.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW).name(m("admin.common.back")).build());

        admin.openInventory(inv);
    }

    private org.bukkit.inventory.ItemStack multItem(String name, double value) {
        return new ItemBuilder(Material.COMPARATOR)
            .name(name)
            .lore("&7Valeur: &e" + String.format("%.2f", value),
                  "", "&eG/D: -0.05/+0.05  Shift: -0.1/+0.1")
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
                ChatUtil.send(admin, "&eEntrez l'ID du pool (ou 'none'):");
                plugin.getChatInputManager().prompt(admin, "&eID du pool:", input -> {
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
