package fr.obsidianlabs.plugins.villagedefense.gui;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaSetup;
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
 * GUI admin : affiche les 9 options d'enchantement avec leurs prix de base.
 * Cliquer sur une option ouvre une invite chat pour modifier son prix.
 * Les prix sont sauvegardés dans le fichier de l'arène.
 */
public class EnchantPricesGUI {

    public static final String TITLE = "gui.enchant-prices.title";

    // Slots d'affichage (mêmes positions que dans EnchantGUI)
    private static final int[] DISPLAY_SLOTS = EnchantGUI.DISPLAY_SLOTS;

    private static final int BACK_SLOT = 49;

    private final VillageDefensePlugin plugin;

    public EnchantPricesGUI(VillageDefensePlugin plugin) {
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

        Inventory inv = Bukkit.createInventory(null, 54, m(TITLE));
        for (int i = 0; i < 54; i++) inv.setItem(i, ItemBuilder.separator());
        for (int i = 36; i < 45; i++) inv.setItem(i, ItemBuilder.separator(Material.BLACK_STAINED_GLASS_PANE));

        EnchantGUI.EnchantOption[] options = EnchantGUI.OPTIONS;
        for (int i = 0; i < options.length && i < DISPLAY_SLOTS.length; i++) {
            EnchantGUI.EnchantOption opt = options[i];
            long price = arena.getEnchantPrice(i, opt.baseCost());
            inv.setItem(DISPLAY_SLOTS[i], buildOptionItem(opt, price, i));
        }

        inv.setItem(BACK_SLOT, new ItemBuilder(Material.ARROW)
            .name(m("admin.common.back"))
            .build());

        admin.openInventory(inv);
    }

    // ── Builder d'item ────────────────────────────────────────────────────────

    private ItemStack buildOptionItem(EnchantGUI.EnchantOption opt, long price, int index) {
        List<String> lore = new ArrayList<>();
        lore.add("&7" + m(opt.descKey()));
        lore.add("");
        lore.add(m("admin.enchant-prices.lore-default", opt.baseCost()));
        lore.add(m("admin.enchant-prices.lore-current", price));
        lore.add("");
        lore.add(m("admin.common.click-to-edit"));

        return new ItemBuilder(opt.icon())
            .name(m(opt.nameKey()) + " &8#" + (index + 1))
            .lore(lore)
            .build();
    }

    // ── Clics ────────────────────────────────────────────────────────────────

    public void handleClick(InventoryClickEvent event, Player admin) {
        event.setCancelled(true);

        int slot = event.getSlot();

        ArenaSetup setup = plugin.getArenaManager().getSetup(admin);
        if (setup == null) return;
        Arena arena = setup.getArena();

        if (slot == BACK_SLOT) {
            plugin.getArenaSetupGui().open(admin, arena);
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()
         || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE
         || clicked.getType() == Material.BLACK_STAINED_GLASS_PANE) return;

        // Retrouver l'index de l'option cliquée
        for (int i = 0; i < DISPLAY_SLOTS.length; i++) {
            if (DISPLAY_SLOTS[i] != slot) continue;

            EnchantGUI.EnchantOption opt = EnchantGUI.OPTIONS[i];
            final int optIndex = i;

            admin.closeInventory();
            plugin.getChatInputManager().prompt(
                admin,
                m("admin.enchant-prices.prompt", m(opt.nameKey()), arena.getEnchantPrice(i, opt.baseCost())),
                input -> {
                    long price;
                    try {
                        price = Long.parseLong(input.trim());
                        if (price < 0) throw new NumberFormatException();
                    } catch (NumberFormatException e) {
                        ChatUtil.send(admin, m("admin.enchant-prices.invalid"));
                        open(admin, arena);
                        return;
                    }
                    arena.setEnchantPrice(optIndex, price);
                    plugin.getArenaManager().saveArena(arena);
                    ChatUtil.send(admin, m("admin.enchant-prices.set", m(opt.nameKey()), price));
                    open(admin, arena);
                }
            );
            return;
        }
    }
}
