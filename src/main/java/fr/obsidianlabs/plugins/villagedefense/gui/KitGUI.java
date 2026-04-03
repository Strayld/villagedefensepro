package fr.obsidianlabs.plugins.villagedefense.gui;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.kit.Kit;
import fr.obsidianlabs.plugins.villagedefense.kit.KitType;
import fr.obsidianlabs.plugins.villagedefense.player.VDPlayer;
import fr.obsidianlabs.plugins.villagedefense.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

public class KitGUI {

    public static final String TITLE_KEY = "gui.kit.title";
    private static final int SIZE = 27;

    private final VillageDefensePlugin plugin;

    public KitGUI(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    private String m(String key, Object... args) {
        return plugin.getMessages().format(key, args);
    }

    public void open(Player player) {
        VDPlayer vdPlayer = plugin.getPlayerManager().getPlayer(player);
        if (vdPlayer == null) return;

        Inventory inv = Bukkit.createInventory(null, SIZE, m(TITLE_KEY));

        // Remplir les bordures
        for (int i = 0; i < SIZE; i++) {
            inv.setItem(i, ItemBuilder.separator());
        }

        // Placer les kits
        int[] slots = {10, 12, 14, 16, 13};
        KitType[] types = KitType.values();

        for (int i = 0; i < types.length && i < slots.length; i++) {
            KitType type = types[i];
            Kit kit = plugin.getKitManager().getKit(type);
            if (kit == null) continue;

            boolean unlocked   = vdPlayer.hasKit(type);
            boolean selected   = vdPlayer.getSelectedKit() == type;
            int levelRequired  = plugin.getKitManager().getLevelRequired(type);
            boolean meetsLevel = vdPlayer.getLevel() >= levelRequired;

            List<String> lore = new ArrayList<>();
            lore.addAll(plugin.getMessages().lines(type.getLoreKey()));
            lore.add("");
            lore.add(m("gui.kit.level-required", meetsLevel ? "&a" : "&c", levelRequired));
            lore.add("");
            if (selected) {
                lore.add(m("gui.kit.selected"));
            } else if (unlocked) {
                lore.add(m("gui.kit.click-select"));
            } else if (!meetsLevel) {
                lore.add(m("gui.kit.level-too-low", levelRequired));
            } else {
                lore.add(m("gui.kit.locked"));
                lore.add(m("gui.kit.cost", kit.getCost()));
                lore.add(m("gui.kit.your-coins", vdPlayer.getCoins()));
                lore.add("");
                lore.add(m("gui.kit.click-unlock"));
            }

            Material icon = (unlocked || meetsLevel) ? type.getIcon() : Material.BARRIER;
            String nameColor = selected ? "&a" : (unlocked ? "&e" : (meetsLevel ? "&6" : "&c"));

            var item = new ItemBuilder(icon)
                    .name(nameColor + m(type.getNameKey()))
                    .lore(lore);

            if (selected) item.glowing();

            inv.setItem(slots[i], item.build());
        }

        player.openInventory(inv);
    }

    public static boolean isKitGUI(Inventory inv) {
        return inv.getSize() == SIZE && inv.getLocation() == null;
    }

    public void handleClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);

        if (event.getCurrentItem() == null ||
            event.getCurrentItem().getType() == Material.AIR ||
            event.getCurrentItem().getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        VDPlayer vdPlayer = plugin.getPlayerManager().getPlayer(player);
        if (vdPlayer == null) return;

        // Identifier le kit cliqué par le slot
        int[] slots = {10, 12, 14, 16, 13};
        KitType[] types = KitType.values();

        for (int i = 0; i < slots.length && i < types.length; i++) {
            if (event.getSlot() == slots[i]) {
                KitType type = types[i];
                Kit kit = plugin.getKitManager().getKit(type);
                if (kit == null) return;

                if (vdPlayer.hasKit(type)) {
                    // Sélectionner
                    vdPlayer.setSelectedKit(type);
                    plugin.getMessages().send(player, "kit.selected",
                        plugin.getMessages().format(player, type.getNameKey()));
                    open(player); // Rafraîchir
                } else if (vdPlayer.getLevel() < plugin.getKitManager().getLevelRequired(type)) {
                    plugin.getMessages().send(player, "kit.level-required",
                        plugin.getKitManager().getLevelRequired(type));
                } else {
                    // Débloquer
                    long cost = kit.getCost();
                    if (!vdPlayer.removeCoins(cost)) {
                        plugin.getMessages().send(player, "kit.not-enough-coins");
                        return;
                    }
                    vdPlayer.unlockKit(type);
                    vdPlayer.setSelectedKit(type);
                    plugin.getMessages().send(player, "kit.unlocked",
                        plugin.getMessages().format(player, type.getNameKey()), cost);
                    plugin.getPlayerManager().savePlayer(player);
                    open(player);
                }
                return;
            }
        }
    }
}
