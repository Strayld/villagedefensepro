package fr.obsidianlabs.plugins.villagedefense.gui;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaSetup;
import fr.obsidianlabs.plugins.villagedefense.shop.ChestShopReader;
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
 * GUI admin : affiche le contenu du coffre shop avec les prix actuels.
 * Cliquer sur un article ouvre une invite chat pour modifier son prix.
 * Les prix sont sauvegardés dans le fichier de l'arène.
 */
public class ShopPricesGUI {

    public static final String TITLE = "gui.shop-prices.title";

    private final VillageDefensePlugin plugin;

    public ShopPricesGUI(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    private String m(String key, Object... args) {
        return plugin.getMessages().format(key, args);
    }

    // ── Ouverture ────────────────────────────────────────────────────────────

    public void open(Player admin, Arena arena) {
        if (!plugin.hasPermission(admin, Perm.ADMIN_PRICES)) {
            ChatUtil.send(admin, plugin.getMessages().format("general.no-permission"));
            return;
        }
        ChestShopReader chestReader = plugin.getShopGui().getChestReader();
        Inventory chestInv = chestReader.getChestInventory(arena.getShopChestLocation());
        if (chestInv == null) {
            ChatUtil.send(admin, m("admin.prices.chest-not-found"));
            return;
        }

        int chestSize = chestInv.getSize(); // 27 ou 54
        // On ajoute une rangée de navigation sous les items (max 54 slots au total)
        int invSize = Math.min(chestSize + 9, 54);
        int itemSlots = invSize - 9; // nombre de slots du coffre affichés

        Inventory inv = Bukkit.createInventory(null, invSize, m(TITLE));

        for (int i = 0; i < invSize; i++) inv.setItem(i, ItemBuilder.separator());

        for (int i = 0; i < itemSlots; i++) {
            ItemStack item = chestInv.getItem(i);
            if (item == null || item.getType().isAir()) continue;

            long price = arena.getShopItemPrice(i, chestReader.getDefaultPriceForItem(item));
            ItemStack display = item.clone();
            display.editMeta(meta -> {
                List<String> lore = new ArrayList<>();
                if (meta.hasLore() && meta.getLore() != null) lore.addAll(meta.getLore());
                lore.add("");
                lore.add(m("admin.prices.lore-current", price));
                lore.add(m("admin.common.click-to-edit"));
                meta.setLore(lore);
            });
            inv.setItem(i, display);
        }

        // Rangée de navigation
        int navRow = invSize - 9;
        inv.setItem(navRow + 4, new ItemBuilder(Material.ARROW)
            .name(m("admin.common.back"))
            .build());

        admin.openInventory(inv);
    }

    // ── Clics ────────────────────────────────────────────────────────────────

    public void handleClick(InventoryClickEvent event, Player admin) {
        event.setCancelled(true);

        int slot = event.getSlot();
        int invSize = event.getInventory().getSize();
        int navRow  = invSize - 9;

        ArenaSetup setup = plugin.getArenaManager().getSetup(admin);
        if (setup == null) return;
        Arena arena = setup.getArena();

        // Bouton retour (centre de la rangée nav)
        if (slot == navRow + 4) {
            plugin.getArenaSetupGui().open(admin, arena);
            return;
        }

        // Ignorer les autres clics dans la rangée de navigation
        if (slot >= navRow) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()
         || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        // Trouver le nom de l'item pour l'invite
        String itemName = clicked.hasItemMeta() && clicked.getItemMeta().hasDisplayName()
            ? ChatUtil.stripColor(clicked.getItemMeta().getDisplayName())
            : clicked.getType().name();

        final int targetSlot = slot;

        admin.closeInventory();
        plugin.getChatInputManager().prompt(
            admin,
            m("admin.prices.prompt", itemName, slot),
            input -> {
                long price;
                try {
                    price = Long.parseLong(input.trim());
                    if (price < 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    ChatUtil.send(admin, m("admin.prices.invalid"));
                    open(admin, arena);
                    return;
                }

                arena.setShopItemPrice(targetSlot, price);
                plugin.getArenaManager().saveArena(arena);
                ChatUtil.send(admin, m("admin.prices.set", itemName, price));
                open(admin, arena);
            }
        );
    }
}
