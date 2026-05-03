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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Vue de la liste des catégories de vagues.
 * Clique sur une catégorie → WaveCategoryEditorGUI.
 *
 * Layout 36 slots :
 *  Rangée 1 : [.][NORMAL][ELITE][BOSS][SPECIAL][.][.][.][.]
 *  Rangée 3 : [.][BACK][.][.][.][.][.][.][.]
 */
public class WaveCategoriesGUI {

    public static final String TITLE = "gui.gameplay.wave-categories.title";

    private static final int SLOT_NORMAL  = 10;
    private static final int SLOT_ELITE   = 11;
    private static final int SLOT_BOSS    = 12;
    private static final int SLOT_SPECIAL = 13;
    private static final int SLOT_BACK    = 28;

    private final VillageDefensePlugin plugin;

    public WaveCategoriesGUI(VillageDefensePlugin plugin) {
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

        ArenaGameplaySettings settings = plugin.getArenaGameplayService().getSettings(arena.getId());

        Inventory inv = Bukkit.createInventory(null, 36, m(TITLE));
        for (int i = 0; i < 36; i++) inv.setItem(i, ItemBuilder.separator());

        inv.setItem(SLOT_NORMAL,  buildCategoryItem(WaveCategory.NORMAL,  settings, Material.ZOMBIE_HEAD));
        inv.setItem(SLOT_ELITE,   buildCategoryItem(WaveCategory.ELITE,   settings, Material.SKELETON_SKULL));
        inv.setItem(SLOT_BOSS,    buildCategoryItem(WaveCategory.BOSS,    settings, Material.WITHER_SKELETON_SKULL));
        inv.setItem(SLOT_SPECIAL, buildCategoryItem(WaveCategory.SPECIAL, settings, Material.NETHER_STAR));

        inv.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW).name(m("admin.common.back")).build());

        admin.openInventory(inv);
    }

    private ItemStack buildCategoryItem(WaveCategory cat, ArenaGameplaySettings settings, Material mat) {
        WaveCategoryTemplate t = settings.getCategoryTemplates().get(cat);

        List<String> lore = new ArrayList<>();
        if (t == null) {
            lore.add(m("gui.gameplay.wave-categories.template-missing"));
            lore.add(m("gui.gameplay.wave-categories.click-create"));
        } else {
            lore.add(m("gui.gameplay.wave-categories.status", m(t.isEnabled() ? "gui.gameplay.common.enabled-state" : "gui.gameplay.common.disabled-state")));
            lore.add(m("gui.gameplay.wave-categories.pool", t.getMobPoolId() != null ? t.getMobPoolId() : m("gui.gameplay.common.none")));
            lore.add(m("gui.gameplay.wave-categories.mode", t.getCompositionMode()));
            lore.add(m("gui.gameplay.wave-categories.budget", String.format("%.2f", t.getCategoryBudgetMultiplier())));
            lore.add(m("gui.gameplay.wave-categories.stats",
                String.format("%.2f", t.getHealthMultiplier()),
                String.format("%.2f", t.getDamageMultiplier()),
                String.format("%.2f", t.getSpeedMultiplier())));
            lore.add(m("gui.gameplay.wave-categories.max-types", t.getMaxMobTypes()));
            lore.add("");
            lore.add(m("gui.gameplay.wave-categories.click-edit"));
        }

        return new ItemBuilder(t != null && t.isEnabled() ? mat : Material.GRAY_DYE)
            .name("&f" + cat.name())
            .lore(lore)
            .build();
    }

    public void handleClick(InventoryClickEvent event, Player admin) {
        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;
        if (event.getCurrentItem().getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        ArenaSetup setup = plugin.getArenaManager().getSetup(admin);
        if (setup == null) return;
        Arena arena = setup.getArena();

        WaveCategory target = switch (event.getRawSlot()) {
            case SLOT_NORMAL  -> WaveCategory.NORMAL;
            case SLOT_ELITE   -> WaveCategory.ELITE;
            case SLOT_BOSS    -> WaveCategory.BOSS;
            case SLOT_SPECIAL -> WaveCategory.SPECIAL;
            case SLOT_BACK    -> { plugin.getGameplayMainGui().open(admin, arena); yield null; }
            default           -> null;
        };
        if (target == null) return;

        plugin.getWaveCategoryEditorGui().open(admin, arena, target);
    }
}
