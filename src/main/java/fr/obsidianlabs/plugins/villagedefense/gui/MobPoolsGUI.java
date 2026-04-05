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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Liste des MobPools + bouton créer.
 * Layout 54 slots — pools affichés sur les rangées 0-4, boutons de contrôle en rangée 5.
 */
public class MobPoolsGUI {

    public static final String TITLE = "gui.gameplay.mob-pools.title";

    private static final int SLOT_CREATE = 49;
    private static final int SLOT_BACK   = 45;

    private final VillageDefensePlugin plugin;

    public MobPoolsGUI(VillageDefensePlugin plugin) {
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
        Map<String, MobPool> pools = settings.getMobPools();

        Inventory inv = Bukkit.createInventory(null, 54, m(TITLE));
        for (int i = 0; i < 54; i++) inv.setItem(i, ItemBuilder.separator());
        for (int i = 45; i < 54; i++) inv.setItem(i, ItemBuilder.separator(Material.BLACK_STAINED_GLASS_PANE));

        int slot = 0;
        for (MobPool pool : pools.values()) {
            if (slot >= 45) break;
            inv.setItem(slot, buildPoolItem(pool));
            slot++;
        }

        inv.setItem(SLOT_CREATE, new ItemBuilder(Material.EMERALD)
            .name("&a+ Créer un pool")
            .lore("&7Ajoute un nouveau pool de mobs.", "", "&eClique pour créer")
            .build());

        inv.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW).name(m("admin.common.back")).build());

        admin.openInventory(inv);
    }

    private ItemStack buildPoolItem(MobPool pool) {
        List<String> lore = new ArrayList<>();
        lore.add("&7Statut: " + (pool.isEnabled() ? "&aActivé" : "&cDésactivé"));
        lore.add("&7Catégorie par défaut: &e" + (pool.getDefaultCategory() != null ? pool.getDefaultCategory() : "Aucune"));
        lore.add("&7Entrées: &e" + pool.getEntries().size());
        lore.add("");
        lore.add("&eClique gauche &7→ éditer");
        lore.add("&eClique droit &7→ activer/désactiver");
        lore.add("&eShift+clique droit &7→ supprimer");

        return new ItemBuilder(pool.isEnabled() ? Material.ZOMBIE_SPAWN_EGG : Material.EGG)
            .name("&f" + pool.getDisplayName() + " &7[" + pool.getId() + "]")
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

        int slot = event.getRawSlot();

        if (slot == SLOT_BACK) {
            plugin.getGameplayMainGui().open(admin, arena);
            return;
        }

        if (slot == SLOT_CREATE) {
            admin.closeInventory();
            plugin.getChatInputManager().prompt(admin, "&eNom du pool (identifiant, sans espaces):", input -> {
                String poolId = input.trim().toLowerCase().replace(" ", "_");
                if (poolId.isEmpty()) {
                    ChatUtil.send(admin, "&cNom invalide.");
                    open(admin, arena);
                    return;
                }
                ArenaGameplaySettings settings = plugin.getArenaGameplayService().getSettings(arena.getId());
                if (settings.getMobPools().containsKey(poolId)) {
                    ChatUtil.send(admin, "&cUn pool avec cet id existe déjà.");
                    open(admin, arena);
                    return;
                }
                MobPool pool = new MobPool(poolId, input.trim());
                settings.getMobPools().put(poolId, pool);
                plugin.getArenaGameplayService().saveSettings(arena.getId());
                ChatUtil.send(admin, "&aPool &f" + poolId + " &acréé.");
                plugin.getMobPoolEditorGui().open(admin, arena, poolId);
            });
            return;
        }

        // Clic sur un pool existant
        ArenaGameplaySettings settings = plugin.getArenaGameplayService().getSettings(arena.getId());
        List<String> poolIds = new ArrayList<>(settings.getMobPools().keySet());
        if (slot < 0 || slot >= poolIds.size()) return;

        String poolId = poolIds.get(slot);
        MobPool pool = settings.getMobPools().get(poolId);
        if (pool == null) return;

        ClickType click = event.getClick();

        if (click.isShiftClick() && click.isRightClick()) {
            settings.getMobPools().remove(poolId);
            plugin.getArenaGameplayService().saveSettings(arena.getId());
            ChatUtil.send(admin, "&cPool &f" + poolId + " &csupprimé.");
            open(admin, arena);
        } else if (click.isRightClick()) {
            pool.setEnabled(!pool.isEnabled());
            plugin.getArenaGameplayService().saveSettings(arena.getId());
            open(admin, arena);
        } else {
            plugin.getMobPoolEditorGui().open(admin, arena, poolId);
        }
    }
}
