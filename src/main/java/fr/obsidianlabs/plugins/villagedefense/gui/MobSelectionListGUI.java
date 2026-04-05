package fr.obsidianlabs.plugins.villagedefense.gui;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaSetup;
import fr.obsidianlabs.plugins.villagedefense.gameplay.model.*;
import fr.obsidianlabs.plugins.villagedefense.gameplay.provider.MobOption;
import fr.obsidianlabs.plugins.villagedefense.util.ChatUtil;
import fr.obsidianlabs.plugins.villagedefense.util.ItemBuilder;
import fr.obsidianlabs.plugins.villagedefense.util.Perm;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Sélecteur de mobs disponibles pour ajout dans un pool.
 * Pagination + filtre par source.
 */
public class MobSelectionListGUI {

    public static final String TITLE = "gui.gameplay.mob-selection.title";

    private static final int PAGE_SIZE    = 45;
    private static final int SLOT_PREV    = 45;
    private static final int SLOT_FILTER  = 49;
    private static final int SLOT_NEXT    = 53;
    private static final int SLOT_BACK    = 46;

    private final Map<UUID, String>     targetPool   = new HashMap<>();
    private final Map<UUID, Integer>    currentPage  = new HashMap<>();
    private final Map<UUID, MobSource>  filterSource = new HashMap<>();

    private final VillageDefensePlugin plugin;

    public MobSelectionListGUI(VillageDefensePlugin plugin) {
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

        targetPool.put(admin.getUniqueId(), poolId);
        currentPage.putIfAbsent(admin.getUniqueId(), 0);

        int page = currentPage.get(admin.getUniqueId());
        MobSource filter = filterSource.get(admin.getUniqueId());

        List<MobOption> allMobs = filter != null
            ? plugin.getMobRegistryService().getAvailableMobs(filter)
            : plugin.getMobRegistryService().getAllAvailableMobs();

        int totalPages = Math.max(1, (int) Math.ceil((double) allMobs.size() / PAGE_SIZE));
        page = Math.min(page, totalPages - 1);
        currentPage.put(admin.getUniqueId(), page);

        int start = page * PAGE_SIZE;
        List<MobOption> pageMobs = allMobs.subList(start, Math.min(start + PAGE_SIZE, allMobs.size()));

        Inventory inv = Bukkit.createInventory(null, 54, m(TITLE) + " (" + (page + 1) + "/" + totalPages + ")");
        for (int i = 0; i < 54; i++) inv.setItem(i, ItemBuilder.separator());
        for (int i = 45; i < 54; i++) inv.setItem(i, ItemBuilder.separator(Material.BLACK_STAINED_GLASS_PANE));

        for (int i = 0; i < pageMobs.size(); i++) {
            inv.setItem(i, buildMobItem(pageMobs.get(i)));
        }

        if (page > 0) {
            inv.setItem(SLOT_PREV, new ItemBuilder(Material.ARROW).name("&7← Page précédente").build());
        }
        if (page < totalPages - 1) {
            inv.setItem(SLOT_NEXT, new ItemBuilder(Material.ARROW).name("&7Page suivante →").build());
        }

        String filterName = filter != null ? filter.name() : "Tous";
        inv.setItem(SLOT_FILTER, new ItemBuilder(Material.HOPPER)
            .name("&eFiltre source: &f" + filterName)
            .lore("&7Clique pour changer (Tous → VANILLA → MYTHICMOBS)")
            .build());

        inv.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW).name(m("admin.common.back")).build());

        admin.openInventory(inv);
    }

    private ItemStack buildMobItem(MobOption mob) {
        return new ItemBuilder(mob.getIcon() != null ? mob.getIcon() : Material.ZOMBIE_SPAWN_EGG)
            .name("&f" + mob.getDisplayName())
            .lore("&7Source: &e" + mob.getSource(),
                  "&7ID: &7" + mob.getId(),
                  "", "&eClique pour ajouter au pool")
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

        String poolId = targetPool.get(admin.getUniqueId());
        if (poolId == null) return;

        int slot = event.getRawSlot();

        if (slot == SLOT_BACK) {
            targetPool.remove(admin.getUniqueId());
            currentPage.remove(admin.getUniqueId());
            plugin.getMobPoolEditorGui().open(admin, arena, poolId);
            return;
        }

        if (slot == SLOT_PREV) {
            currentPage.merge(admin.getUniqueId(), -1, Integer::sum);
            open(admin, arena, poolId);
            return;
        }

        if (slot == SLOT_NEXT) {
            currentPage.merge(admin.getUniqueId(), 1, Integer::sum);
            open(admin, arena, poolId);
            return;
        }

        if (slot == SLOT_FILTER) {
            MobSource current = filterSource.get(admin.getUniqueId());
            MobSource next = switch (current == null ? MobSource.MYTHICMOBS : current) {
                case VANILLA    -> MobSource.MYTHICMOBS;
                case MYTHICMOBS -> null;
                default         -> MobSource.VANILLA;
            };
            filterSource.put(admin.getUniqueId(), next);
            currentPage.put(admin.getUniqueId(), 0);
            open(admin, arena, poolId);
            return;
        }

        // Ajout d'un mob au pool
        int page = currentPage.getOrDefault(admin.getUniqueId(), 0);
        MobSource filter = filterSource.get(admin.getUniqueId());
        List<MobOption> allMobs = filter != null
            ? plugin.getMobRegistryService().getAvailableMobs(filter)
            : plugin.getMobRegistryService().getAllAvailableMobs();

        int mobIndex = page * PAGE_SIZE + slot;
        if (mobIndex < 0 || mobIndex >= allMobs.size()) return;

        MobOption option = allMobs.get(mobIndex);

        ArenaGameplaySettings settings = plugin.getArenaGameplayService().getSettings(arena.getId());
        MobPool pool = settings.getMobPools().get(poolId);
        if (pool == null) return;

        // Vérifier doublon
        boolean alreadyExists = pool.getEntries().stream().anyMatch(e -> e.getId().equals(option.getId()));
        if (alreadyExists) {
            ChatUtil.send(admin, "&c" + option.getDisplayName() + " est déjà dans ce pool.");
            return;
        }

        MobEntry newEntry = new MobEntry(option.getId(), option.getSource(), option.getDisplayName());
        pool.getEntries().add(newEntry);
        plugin.getArenaGameplayService().saveSettings(arena.getId());
        ChatUtil.send(admin, "&a" + option.getDisplayName() + " ajouté au pool.");
        plugin.getMobPoolEditorGui().open(admin, arena, poolId);
    }
}
