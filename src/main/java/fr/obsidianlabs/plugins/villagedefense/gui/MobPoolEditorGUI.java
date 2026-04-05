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
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Éditeur d'un MobPool — liste des entrées + bouton d'ajout.
 */
public class MobPoolEditorGUI {

    public static final String TITLE = "gui.gameplay.mob-pool-editor.title";

    private static final int SLOT_ADD_MOB = 49;
    private static final int SLOT_BACK    = 45;

    /** Pool en cours d'édition par joueur. */
    private final Map<UUID, String> editingPool = new HashMap<>();

    private final VillageDefensePlugin plugin;

    public MobPoolEditorGUI(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    private String m(String key, Object... args) {
        return plugin.getMessages().format(key, args);
    }

    public void open(Player admin, Arena arena, String poolId) {
        if (!plugin.hasPermission(admin, Perm.ADMIN_SETUP)) {
            ChatUtil.send(admin, m("general.no-permission"));
            return;
        }

        editingPool.put(admin.getUniqueId(), poolId);

        ArenaGameplaySettings settings = plugin.getArenaGameplayService().getSettings(arena.getId());
        MobPool pool = settings.getMobPools().get(poolId);
        if (pool == null) {
            ChatUtil.send(admin, "&cPool introuvable: " + poolId);
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 54, m(TITLE) + " - " + pool.getDisplayName());
        for (int i = 0; i < 54; i++) inv.setItem(i, ItemBuilder.separator());
        for (int i = 45; i < 54; i++) inv.setItem(i, ItemBuilder.separator(Material.BLACK_STAINED_GLASS_PANE));

        List<MobEntry> entries = pool.getEntries();
        for (int i = 0; i < entries.size() && i < 45; i++) {
            inv.setItem(i, buildEntryItem(entries.get(i)));
        }

        inv.setItem(SLOT_ADD_MOB, new ItemBuilder(Material.EMERALD)
            .name("&a+ Ajouter un mob")
            .lore("&7Ouvre le sélecteur de mobs.", "", "&eClique pour ajouter")
            .build());

        inv.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW).name(m("admin.common.back")).build());

        admin.openInventory(inv);
    }

    private ItemStack buildEntryItem(MobEntry e) {
        List<String> lore = new ArrayList<>();
        lore.add("&7Source: &e" + e.getSource());
        lore.add("&7Poids: &e" + e.getWeight() + "  &7Threat: &e" + e.getThreat());
        lore.add("&7Quantité: &e" + e.getMinAmount() + "–" + e.getMaxAmount());
        lore.add("&7Vagues: &e" + e.getMinWave() + "–" + (e.getMaxWave() == 0 ? "∞" : e.getMaxWave()));
        lore.add("&7Statut: " + (e.isEnabled() ? "&aActivé" : "&cDésactivé"));
        lore.add("");
        lore.add("&eClique gauche &7→ éditer");
        lore.add("&eClique droit &7→ activer/désactiver");
        lore.add("&eShift+D &7→ supprimer");

        Material mat = e.getSource() == MobSource.VANILLA ? Material.ZOMBIE_SPAWN_EGG : Material.COMMAND_BLOCK;
        return new ItemBuilder(e.isEnabled() ? mat : Material.BARRIER)
            .name("&f" + e.getDisplayName() + " &7[" + e.getId() + "]")
            .lore(lore)
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

        String poolId = editingPool.get(admin.getUniqueId());
        if (poolId == null) return;

        int slot = event.getRawSlot();

        if (slot == SLOT_BACK) {
            editingPool.remove(admin.getUniqueId());
            plugin.getMobPoolsGui().open(admin, arena);
            return;
        }

        if (slot == SLOT_ADD_MOB) {
            plugin.getMobSelectionListGui().open(admin, arena, poolId);
            return;
        }

        ArenaGameplaySettings settings = plugin.getArenaGameplayService().getSettings(arena.getId());
        MobPool pool = settings.getMobPools().get(poolId);
        if (pool == null) return;

        List<MobEntry> entries = pool.getEntries();
        if (slot < 0 || slot >= entries.size()) return;

        MobEntry entry = entries.get(slot);
        ClickType click = event.getClick();

        if (click.isShiftClick() && click.isRightClick()) {
            entries.remove(slot);
            plugin.getArenaGameplayService().saveSettings(arena.getId());
            ChatUtil.send(admin, "&cMob &f" + entry.getId() + " &csupprimé du pool.");
            open(admin, arena, poolId);
        } else if (click.isRightClick()) {
            entry.setEnabled(!entry.isEnabled());
            plugin.getArenaGameplayService().saveSettings(arena.getId());
            open(admin, arena, poolId);
        } else {
            plugin.getMobEntryEditorGui().open(admin, arena, poolId, slot);
        }
    }
}
