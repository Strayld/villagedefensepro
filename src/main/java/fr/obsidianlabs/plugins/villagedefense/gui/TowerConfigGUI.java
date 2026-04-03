package fr.obsidianlabs.plugins.villagedefense.gui;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaSetup;
import fr.obsidianlabs.plugins.villagedefense.tower.TowerType;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GUI admin : configuration des tours par arène.
 *
 * Écran principal (54 slots) : liste des 6 tours.
 * Écran détail (27 slots)    : coût / level requis / activer-désactiver pour une tour.
 */
public class TowerConfigGUI {

    public static final String TITLE_MAIN   = "gui.tower-config.title-main";
    public static final String TITLE_DETAIL = "gui.tower-config.title-detail";

    // Slots des 6 tours dans l'écran principal
    private static final int[] TOWER_SLOTS = { 10, 13, 16, 28, 31, 34 };
    private static final TowerType[] TOWER_ORDER = {
        TowerType.ARCHER, TowerType.TREBUCHET, TowerType.MAGE,
        TowerType.ICE,    TowerType.LIGHTNING,  TowerType.CANNON
    };

    // Slots de l'écran détail
    private static final int DETAIL_INFO      = 4;
    private static final int DETAIL_TOGGLE    = 10;
    private static final int DETAIL_COST      = 13;
    private static final int DETAIL_LEVEL_REQ = 16;
    private static final int DETAIL_BACK      = 22;

    private final VillageDefensePlugin plugin;

    // Contexte : quel type de tour l'admin est-il en train d'éditer ?
    private final Map<UUID, TowerType> editingType = new HashMap<>();

    public TowerConfigGUI(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    private String m(String key, Object... args) {
        return plugin.getMessages().format(key, args);
    }

    // ── Ouverture écran principal ─────────────────────────────────────────────

    public void openMain(Player admin, Arena arena) {
        if (!plugin.hasPermission(admin, Perm.ADMIN_SETUP)) {
            ChatUtil.send(admin, plugin.getMessages().format("general.no-permission"));
            return;
        }
        editingType.remove(admin.getUniqueId());

        Inventory inv = Bukkit.createInventory(null, 54, m(TITLE_MAIN));
        for (int i = 0; i < 54; i++) inv.setItem(i, ItemBuilder.separator());
        for (int i = 36; i < 45; i++) inv.setItem(i, ItemBuilder.separator(Material.BLACK_STAINED_GLASS_PANE));

        for (int i = 0; i < TOWER_ORDER.length; i++) {
            inv.setItem(TOWER_SLOTS[i], buildTowerItem(TOWER_ORDER[i], arena));
        }

        inv.setItem(49, new ItemBuilder(Material.ARROW).name(m("admin.common.back")).build());
        admin.openInventory(inv);
    }

    // ── Ouverture écran détail ────────────────────────────────────────────────

    public void openDetail(Player admin, Arena arena, TowerType type) {
        editingType.put(admin.getUniqueId(), type);

        Inventory inv = Bukkit.createInventory(null, 27,
            m(TITLE_DETAIL) + m(type.getNameKey()));
        for (int i = 0; i < 27; i++) inv.setItem(i, ItemBuilder.separator());

        // Info tour
        boolean disabled = arena.isTowerDisabled(type);
        int costOverride = arena.getTowerCostOverride(type);
        int costDefault  = plugin.getTowerManager().getTowerCost(type);
        int costEffective = costOverride >= 0 ? costOverride : costDefault;
        int levelReq = arena.getTowerLevelRequired(type);

        inv.setItem(DETAIL_INFO, new ItemBuilder(type.getTopMaterial())
            .name("&e" + m(type.getNameKey()))
            .lore(m(type.getDescKey()),
                  "",
                  disabled ? m("admin.tower-config.state-disabled") : m("admin.tower-config.state-enabled"),
                  m("admin.tower-config.detail-cost", costEffective,
                    costOverride >= 0 ? "" : m("admin.tower-config.suffix-default")),
                  m("admin.tower-config.detail-level", levelReq))
            .build());

        // Bouton activer/désactiver
        inv.setItem(DETAIL_TOGGLE, new ItemBuilder(disabled ? Material.RED_CONCRETE : Material.LIME_CONCRETE)
            .name(disabled ? m("admin.tower-config.enable") : m("admin.tower-config.disable"))
            .lore(m("admin.tower-config.toggle-lore"), "", m("admin.common.click-to-edit"))
            .build());

        // Bouton modifier le coût
        inv.setItem(DETAIL_COST, new ItemBuilder(Material.GOLD_INGOT)
            .name(m("admin.tower-config.edit-cost"))
            .lore(m("admin.tower-config.current-cost", costEffective,
                    costOverride >= 0 ? "" : m("admin.tower-config.suffix-default-value", costDefault)),
                  "",
                  m("admin.common.click-to-edit"),
                  costOverride >= 0 ? m("admin.tower-config.shift-reset-cost") : "")
            .build());

        // Bouton modifier le level requis
        inv.setItem(DETAIL_LEVEL_REQ, new ItemBuilder(Material.EXPERIENCE_BOTTLE)
            .name(m("admin.tower-config.edit-level-req"))
            .lore(m("admin.tower-config.current-level-req", levelReq),
                  "",
                  m("admin.common.click-to-edit"),
                  levelReq > 1 ? m("admin.tower-config.shift-reset-level") : "")
            .build());

        // Retour
        inv.setItem(DETAIL_BACK, new ItemBuilder(Material.ARROW).name(m("admin.common.back")).build());

        admin.openInventory(inv);
    }

    // ── Builders d'items (écran principal) ───────────────────────────────────

    private ItemStack buildTowerItem(TowerType type, Arena arena) {
        boolean disabled  = arena.isTowerDisabled(type);
        int costOverride  = arena.getTowerCostOverride(type);
        int costDefault   = plugin.getTowerManager().getTowerCost(type);
        int costEffective = costOverride >= 0 ? costOverride : costDefault;
        int levelReq      = arena.getTowerLevelRequired(type);

        List<String> lore = new ArrayList<>();
        lore.add(m(type.getDescKey()));
        lore.add("");
        lore.add(disabled ? m("admin.tower-config.state-disabled-arena") : m("admin.tower-config.state-enabled"));
        lore.add(m("admin.tower-config.detail-cost", costEffective,
                   costOverride >= 0 ? "" : m("admin.tower-config.suffix-default")));
        if (levelReq > 1)
            lore.add(m("admin.tower-config.detail-level", levelReq));
        lore.add("");
        lore.add(m("admin.common.click-to-edit"));

        return new ItemBuilder(disabled ? Material.BARRIER : type.getBaseMaterial())
            .name((disabled ? "&c" : "&e") + m(type.getNameKey())
                + (disabled ? m("admin.tower-config.name-disabled-suffix") : ""))
            .lore(lore)
            .build();
    }

    // ── Gestion des clics ─────────────────────────────────────────────────────

    public void handleClick(InventoryClickEvent event, Player admin) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType().isAir()
         || item.getType() == Material.GRAY_STAINED_GLASS_PANE
         || item.getType() == Material.BLACK_STAINED_GLASS_PANE) return;

        ArenaSetup setup = plugin.getArenaManager().getSetup(admin);
        if (setup == null) return;
        Arena arena = setup.getArena();

        String title = event.getView().getTitle();
        int rawSlot = event.getRawSlot();

        if (title.contains(m(TITLE_MAIN))) {
            handleMainClick(rawSlot, admin, arena);
        } else {
            handleDetailClick(rawSlot, event.getClick(), admin, arena);
        }
    }

    private void handleMainClick(int slot, Player admin, Arena arena) {
        if (slot == 49) { plugin.getArenaSetupGui().open(admin, arena); return; }

        for (int i = 0; i < TOWER_SLOTS.length; i++) {
            if (TOWER_SLOTS[i] == slot) {
                openDetail(admin, arena, TOWER_ORDER[i]);
                return;
            }
        }
    }

    private void handleDetailClick(int slot, org.bukkit.event.inventory.ClickType click,
                                   Player admin, Arena arena) {
        TowerType type = editingType.get(admin.getUniqueId());
        if (type == null) return;

        switch (slot) {
            case DETAIL_BACK -> openMain(admin, arena);

            case DETAIL_TOGGLE -> {
                boolean wasDisabled = arena.isTowerDisabled(type);
                arena.setTowerDisabled(type, !wasDisabled);
                plugin.getArenaManager().saveArena(arena);
                ChatUtil.send(admin, m(wasDisabled
                    ? "admin.tower-config.enabled-msg"
                    : "admin.tower-config.disabled-msg", m(type.getNameKey())));
                openDetail(admin, arena, type);
            }

            case DETAIL_COST -> {
                if (click.isShiftClick() && arena.getTowerCostOverride(type) >= 0) {
                    arena.removeTowerCostOverride(type);
                    plugin.getArenaManager().saveArena(arena);
                    ChatUtil.send(admin, m("admin.tower-config.cost-reset", m(type.getNameKey())));
                    openDetail(admin, arena, type);
                    return;
                }
                admin.closeInventory();
                plugin.getChatInputManager().prompt(admin,
                    m("admin.tower-config.prompt-cost", m(type.getNameKey()),
                      plugin.getTowerManager().getTowerCost(type, arena)),
                    input -> {
                        try {
                            int val = Integer.parseInt(input.trim());
                            if (val < 0) throw new NumberFormatException();
                            arena.setTowerCostOverride(type, val);
                            plugin.getArenaManager().saveArena(arena);
                            ChatUtil.send(admin, m("admin.tower-config.cost-set",
                                m(type.getNameKey()), val));
                        } catch (NumberFormatException e) {
                            ChatUtil.send(admin, m("admin.tower-config.invalid"));
                        }
                        openDetail(admin, arena, type);
                    });
            }

            case DETAIL_LEVEL_REQ -> {
                if (click.isShiftClick() && arena.getTowerLevelRequired(type) > 1) {
                    arena.removeTowerLevelRequired(type);
                    plugin.getArenaManager().saveArena(arena);
                    ChatUtil.send(admin, m("admin.tower-config.level-reset", m(type.getNameKey())));
                    openDetail(admin, arena, type);
                    return;
                }
                admin.closeInventory();
                plugin.getChatInputManager().prompt(admin,
                    m("admin.tower-config.prompt-level", m(type.getNameKey()),
                      arena.getTowerLevelRequired(type)),
                    input -> {
                        try {
                            int val = Integer.parseInt(input.trim());
                            if (val < 1) throw new NumberFormatException();
                            arena.setTowerLevelRequired(type, val);
                            plugin.getArenaManager().saveArena(arena);
                            ChatUtil.send(admin, m("admin.tower-config.level-set",
                                m(type.getNameKey()), val));
                        } catch (NumberFormatException e) {
                            ChatUtil.send(admin, m("admin.tower-config.invalid"));
                        }
                        openDetail(admin, arena, type);
                    });
            }
        }
    }
}
