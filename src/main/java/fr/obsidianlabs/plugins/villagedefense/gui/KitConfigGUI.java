package fr.obsidianlabs.plugins.villagedefense.gui;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.kit.KitType;
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

public class KitConfigGUI {

    public static final String TITLE_MAIN = "gui.kit-config.title-main";

    private static final int[] KIT_SLOTS = {10, 12, 14, 16, 13};
    private static final int SLOT_BACK = 22;

    private final VillageDefensePlugin plugin;

    public KitConfigGUI(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    private String m(String key, Object... args) {
        return plugin.getMessages().format(key, args);
    }

    public void open(Player admin) {
        if (!plugin.hasPermission(admin, Perm.ADMIN_PLAYER)) {
            ChatUtil.send(admin, plugin.getMessages().format("general.no-permission"));
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, m(TITLE_MAIN));
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, ItemBuilder.separator());
        }

        KitType[] types = KitType.values();
        for (int i = 0; i < types.length && i < KIT_SLOTS.length; i++) {
            inv.setItem(KIT_SLOTS[i], buildKitItem(types[i]));
        }

        inv.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW).name(m("admin.common.back")).build());
        admin.openInventory(inv);
    }

    private ItemStack buildKitItem(KitType type) {
        int levelRequired = plugin.getKitManager().getLevelRequired(type);
        int defaultLevel = type.getDefaultLevelRequired();
        boolean custom = plugin.getKitManager().hasCustomLevelRequired(type);

        List<String> lore = new ArrayList<>();
        lore.add("&7" + m(type.getDescKey()));
        lore.add("");
        lore.add(m("admin.kit-config.current-level-req",
            levelRequired,
            custom ? "" : m("admin.kit-config.suffix-default-value", defaultLevel)));
        lore.add("");
        lore.add(m("admin.common.click-to-edit"));
        if (custom) {
            lore.add(m("admin.kit-config.shift-reset-level"));
        }

        ItemBuilder item = new ItemBuilder(type.getIcon())
            .name(type.getColorCode() + m(type.getNameKey()))
            .lore(lore);

        if (custom) {
            item.glowing();
        }

        return item.build();
    }

    public void handleClick(InventoryClickEvent event, Player admin) {
        event.setCancelled(true);

        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType().isAir()
            || item.getType() == Material.GRAY_STAINED_GLASS_PANE
            || item.getType() == Material.BLACK_STAINED_GLASS_PANE) {
            return;
        }

        if (event.getSlot() == SLOT_BACK) {
            plugin.getVdAdminGui().open(admin);
            return;
        }

        KitType[] types = KitType.values();
        for (int i = 0; i < types.length && i < KIT_SLOTS.length; i++) {
            if (event.getSlot() == KIT_SLOTS[i]) {
                handleKitClick(admin, types[i], event.getClick().isShiftClick());
                return;
            }
        }
    }

    private void handleKitClick(Player admin, KitType type, boolean shiftClick) {
        if (shiftClick && plugin.getKitManager().hasCustomLevelRequired(type)) {
            plugin.getKitManager().resetLevelRequired(type);
            plugin.getPlayerManager().refreshEligibleKitUnlocks();
            ChatUtil.send(admin, m("admin.kit-config.level-reset", m(type.getNameKey()), type.getDefaultLevelRequired()));
            open(admin);
            return;
        }

        plugin.getChatInputManager().prompt(admin,
            m("admin.kit-config.prompt-level", m(type.getNameKey()),
                plugin.getKitManager().getLevelRequired(type)),
            input -> {
                try {
                    int value = Integer.parseInt(input.trim());
                    if (value < 1) {
                        throw new NumberFormatException();
                    }

                    plugin.getKitManager().setLevelRequired(type, value);
                    plugin.getPlayerManager().refreshEligibleKitUnlocks();
                    ChatUtil.send(admin, m("admin.kit-config.level-set", m(type.getNameKey()), value));
                } catch (NumberFormatException e) {
                    ChatUtil.send(admin, m("admin.kit-config.invalid"));
                }
                open(admin);
            });
    }
}
