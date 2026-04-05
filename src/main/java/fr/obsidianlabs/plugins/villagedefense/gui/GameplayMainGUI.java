package fr.obsidianlabs.plugins.villagedefense.gui;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaSetup;
import fr.obsidianlabs.plugins.villagedefense.gameplay.model.ArenaGameplaySettings;
import fr.obsidianlabs.plugins.villagedefense.gameplay.model.GameplayGeneralSettings;
import fr.obsidianlabs.plugins.villagedefense.util.ChatUtil;
import fr.obsidianlabs.plugins.villagedefense.util.ItemBuilder;
import fr.obsidianlabs.plugins.villagedefense.util.Perm;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

/**
 * Point d'entrée principal du système Gameplay.
 *
 * Layout 36 slots :
 *  Rangée 1 : [.][GENERAL][DIFFICULTY][SCALING][CATEGORIES][MOB_POOLS][OVERRIDES][.][.]
 *  Rangée 3 : [.][BACK][.][.][.][.][.][.][.]
 */
public class GameplayMainGUI {

    public static final String TITLE = "gui.gameplay.main.title";

    private static final int SLOT_GENERAL    = 10;
    private static final int SLOT_DIFFICULTY = 11;
    private static final int SLOT_SCALING    = 12;
    private static final int SLOT_CATEGORIES = 13;
    private static final int SLOT_MOB_POOLS  = 14;
    private static final int SLOT_OVERRIDES  = 15;
    private static final int SLOT_BACK       = 28;

    private final VillageDefensePlugin plugin;

    public GameplayMainGUI(VillageDefensePlugin plugin) {
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
        GameplayGeneralSettings gen    = settings.getGeneralSettings();

        Inventory inv = Bukkit.createInventory(null, 36, m(TITLE));
        for (int i = 0; i < 36; i++) inv.setItem(i, ItemBuilder.separator());

        inv.setItem(SLOT_GENERAL, new ItemBuilder(gen.isEnabled() ? Material.LIME_DYE : Material.RED_DYE)
            .name("&6Général")
            .lore("&7Statut: " + (gen.isEnabled() ? "&aActivé" : "&cDésactivé"),
                  "&7Système avancé: " + (gen.isUseAdvancedWaveSystem() ? "&aOui" : "&cNon"),
                  "", "&eClique pour activer/désactiver")
            .build());

        inv.setItem(SLOT_DIFFICULTY, new ItemBuilder(Material.ENCHANTED_BOOK)
            .name("&bDifficulté")
            .lore("&7Configure les niveaux de difficulté",
                  "&7et leurs multiplicateurs.",
                  "", "&eClique pour ouvrir")
            .build());

        inv.setItem(SLOT_SCALING, new ItemBuilder(Material.EXPERIENCE_BOTTLE)
            .name("&aScaling des vagues")
            .lore("&7Budget, croissance linéaire/expo,",
                  "&7intervalles boss/elite/spécial.",
                  "", "&eClique pour ouvrir")
            .build());

        inv.setItem(SLOT_CATEGORIES, new ItemBuilder(Material.BOOK)
            .name("&dCatégories de vagues")
            .lore("&7NORMAL, ELITE, BOSS, SPECIAL",
                  "&7Pools et multiplicateurs par catégorie.",
                  "", "&eClique pour ouvrir")
            .build());

        inv.setItem(SLOT_MOB_POOLS, new ItemBuilder(Material.ZOMBIE_SPAWN_EGG)
            .name("&cPools de mobs")
            .lore("&7Définit quels mobs peuvent spawner",
                  "&7et leurs paramètres.",
                  "", "&eClique pour ouvrir")
            .build());

        inv.setItem(SLOT_OVERRIDES, new ItemBuilder(Material.REPEATER)
            .name("&eOverrides de vague")
            .lore("&7Exceptions sur des vagues précises.",
                  "&7Avancé — optionnel.",
                  "", "&eClique pour ouvrir")
            .build());

        inv.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW)
            .name(m("admin.common.back"))
            .build());

        admin.openInventory(inv);
    }

    public void handleClick(InventoryClickEvent event, Player admin) {
        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;
        if (event.getCurrentItem().getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        ArenaSetup setup = plugin.getArenaManager().getSetup(admin);
        if (setup == null) return;
        Arena arena = setup.getArena();

        switch (event.getRawSlot()) {
            case SLOT_GENERAL -> {
                ArenaGameplaySettings s = plugin.getArenaGameplayService().getSettings(arena.getId());
                GameplayGeneralSettings gen = s.getGeneralSettings();
                gen.setEnabled(!gen.isEnabled());
                plugin.getArenaGameplayService().saveSettings(arena.getId());
                open(admin, arena);
            }
            case SLOT_DIFFICULTY  -> plugin.getDifficultyGui().open(admin, arena);
            case SLOT_SCALING     -> plugin.getWaveScalingGui().open(admin, arena);
            case SLOT_CATEGORIES  -> plugin.getWaveCategoriesGui().open(admin, arena);
            case SLOT_MOB_POOLS   -> plugin.getMobPoolsGui().open(admin, arena);
            case SLOT_OVERRIDES   -> plugin.getWaveOverridesGui().open(admin, arena);
            case SLOT_BACK        -> plugin.getArenaSetupGui().open(admin, arena);
        }
    }
}
