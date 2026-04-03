package fr.obsidianlabs.plugins.villagedefense.gui;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.player.VDPlayer;
import fr.obsidianlabs.plugins.villagedefense.shop.ChestShopReader;
import fr.obsidianlabs.plugins.villagedefense.shop.ChestShopReader.ChestShopEntry;
import fr.obsidianlabs.plugins.villagedefense.shop.ShopItem;
import fr.obsidianlabs.plugins.villagedefense.util.ChatUtil;
import fr.obsidianlabs.plugins.villagedefense.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ShopGUI {

    public static final String TITLE = "shop.title";

    /** Slots d'affichage des articles pour le mode fallback (rangées 1 à 4, hors bordures) */
    private static final int[] DISPLAY_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };


    private final VillageDefensePlugin plugin;
    private final ChestShopReader chestReader;

    public ShopGUI(VillageDefensePlugin plugin) {
        this.plugin = plugin;
        this.chestReader = new ChestShopReader(plugin);
    }

    public ChestShopReader getChestReader() { return chestReader; }

    private String m(String key, Object... args) {
        return plugin.getMessages().format(key, args);
    }

    // ---- Ouverture ----

    public void open(Player player) {
        VDPlayer vdPlayer = plugin.getPlayerManager().getPlayer(player);
        if (vdPlayer == null) return;

        Arena arena = plugin.getArenaManager().getArenaOfPlayer(player);

        if (arena != null && arena.getShopChestLocation() != null) {
            openChestMode(player, vdPlayer, arena);
        } else {
            openFallbackMode(player, vdPlayer);
        }
    }

    // ---- Mode coffre : items aux positions exactes du coffre ----

    private void openChestMode(Player player, VDPlayer vdPlayer, Arena arena) {
        Inventory chestInv = chestReader.getChestInventory(arena.getShopChestLocation());
        if (chestInv == null) {
            openFallbackMode(player, vdPlayer);
            return;
        }

        int size = chestInv.getSize(); // 27 ou 54
        Inventory inv = Bukkit.createInventory(null, size, m(TITLE));
        for (int i = 0; i < size; i++) inv.setItem(i, ItemBuilder.separator());

        List<ChestShopEntry> entries = chestReader.readChest(arena.getShopChestLocation(), arena);
        for (ChestShopEntry entry : entries) {
            boolean canAfford = vdPlayer.getCoins() >= entry.price();
            ItemStack display = entry.item().clone();
            display.editMeta(meta -> {
                List<String> lore = new ArrayList<>();
                if (meta.hasLore() && meta.getLore() != null) lore.addAll(meta.getLore());
                lore.add("");
                lore.add(m("shop.price", canAfford ? "&6" : "&c", entry.price()));
                lore.add(canAfford ? m("shop.click-buy") : m("shop.no-coins-short"));
                meta.setLore(lore);
                if (!canAfford && meta.hasDisplayName()) {
                    meta.setDisplayName(ChatUtil.color("&c" + ChatUtil.stripColor(meta.getDisplayName())));
                }
            });
            inv.setItem(entry.slotIndex(), display);
        }

        // Placer balance + atouts dans des slots vides (priorité: premier slot libre rangée 0, dernier slot)
        placeInfoItems(inv, vdPlayer, chestInv);

        player.openInventory(inv);
    }

    private void placeInfoItems(Inventory inv, VDPlayer vdPlayer, Inventory chestInv) {
        ItemStack balance = new ItemBuilder(Material.GOLD_INGOT)
            .name(m("shop.coins-title", vdPlayer.getCoins()))
            .lore(m("shop.coins-lore1"))
            .build();
        ItemStack perks = new ItemBuilder(Material.NETHER_STAR)
            .name(m("shop.perks-title"))
            .lore(m("shop.perks-lore-level", vdPlayer.getLevel()), "", m("shop.perks-click"))
            .build();

        int size = chestInv.getSize();
        // Balance: premier slot libre dans la première rangée (0-8)
        for (int i = 0; i < 9; i++) {
            if (isEmpty(chestInv, i)) { inv.setItem(i, balance); break; }
        }
        // Atouts: dernier slot libre dans la dernière rangée
        for (int i = size - 1; i >= size - 9; i--) {
            if (isEmpty(chestInv, i)) { inv.setItem(i, perks); break; }
        }
    }

    private boolean isEmpty(Inventory chestInv, int slot) {
        ItemStack item = chestInv.getItem(slot);
        return item == null || item.getType().isAir();
    }

    // ---- Mode fallback : articles hardcodés ----

    private void openFallbackMode(Player player, VDPlayer vdPlayer) {
        Inventory inv = Bukkit.createInventory(null, 54, m(TITLE));
        for (int i = 0; i < 54; i++) inv.setItem(i, ItemBuilder.separator());

        inv.setItem(4, new ItemBuilder(Material.GOLD_INGOT)
            .name(m("shop.coins-title", vdPlayer.getCoins()))
            .lore(m("shop.coins-lore1"), m("shop.coins-lore2"))
            .build());

        List<DisplayEntry> entries = buildFallbackEntries(vdPlayer);
        for (int i = 0; i < entries.size() && i < DISPLAY_SLOTS.length; i++) {
            inv.setItem(DISPLAY_SLOTS[i], entries.get(i).display());
        }

        inv.setItem(49, new ItemBuilder(org.bukkit.Material.NETHER_STAR)
            .name(m("shop.perks-title"))
            .lore(m("shop.perks-lore1"),
                  "", m("shop.perks-lore2", vdPlayer.getLevel()), "", m("shop.perks-click"))
            .build());

        player.openInventory(inv);
    }

    // ---- Fallback : articles hardcodés si pas de coffre ----

    private List<DisplayEntry> buildFallbackEntries(VDPlayer vdp) {
        List<DisplayEntry> out = new ArrayList<>();
        for (ShopItem item : plugin.getShopManager().getItems()) {
            boolean canAfford = vdp.getCoins() >= item.getCost();
            ItemStack display = new ItemBuilder(item.getIcon())
                .name((canAfford ? "&e" : "&c") + m(item.getNameKey()))
                .lore(buildFallbackLore(item, vdp))
                .build();
            out.add(new DisplayEntry(display, item.getCost()));
        }
        return out;
    }

    private List<String> buildFallbackLore(ShopItem item, VDPlayer vdp) {
        List<String> lore = new ArrayList<>(plugin.getMessages().lines(item.getLoreKey()));
        lore.add("");
        boolean canAfford = vdp.getCoins() >= item.getCost();
        lore.add(m("shop.price", canAfford ? "&6" : "&c", item.getCost()));
        lore.add(canAfford ? m("shop.click-buy") : m("shop.no-coins-short"));
        return lore;
    }

    // ---- Gestion des clics ----

    public void handleClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR
         || event.getCurrentItem().getType() == Material.GRAY_STAINED_GLASS_PANE
         || event.getCurrentItem().getType() == Material.GOLD_INGOT) return;

        // Bouton Atouts (n'importe quel slot en mode coffre)
        if (event.getCurrentItem().getType() == Material.NETHER_STAR) {
            plugin.getPerksGui().open(player);
            return;
        }

        VDPlayer vdp = plugin.getPlayerManager().getPlayer(player);
        if (vdp == null) return;

        Arena arena = plugin.getArenaManager().getArenaOfPlayer(player);
        int clickedSlot = event.getSlot();

        if (arena != null && arena.getShopChestLocation() != null) {
            // Mode coffre : le slot cliqué EST le slot dans le coffre
            handleChestPurchase(player, vdp, arena, clickedSlot);
        } else {
            // Mode fallback : retrouver l'index dans DISPLAY_SLOTS
            int idx = -1;
            for (int i = 0; i < DISPLAY_SLOTS.length; i++) {
                if (DISPLAY_SLOTS[i] == clickedSlot) { idx = i; break; }
            }
            if (idx >= 0) handleFallbackPurchase(player, vdp, idx);
        }
    }

    private void handleChestPurchase(Player player, VDPlayer vdp, Arena arena, int chestSlot) {
        List<ChestShopEntry> entries = chestReader.readChest(arena.getShopChestLocation(), arena);
        ChestShopEntry entry = entries.stream()
            .filter(e -> e.slotIndex() == chestSlot)
            .findFirst().orElse(null);
        if (entry == null) return;

        if (!vdp.removeCoins(entry.price())) {
            ChatUtil.send(player, plugin.getMessages().get("shop.not-enough-coins", entry.price()));
            return;
        }

        var leftOver = player.getInventory().addItem(entry.item().clone());
        leftOver.values().forEach(i -> player.getWorld().dropItem(player.getLocation(), i));

        ChatUtil.send(player, plugin.getMessages().get("shop.bought", getItemName(entry.item()), entry.price()));

        open(player); // rafraîchir
    }

    private void handleFallbackPurchase(Player player, VDPlayer vdp, int idx) {
        List<ShopItem> items = plugin.getShopManager().getItems();
        if (idx >= items.size()) return;

        ShopItem item = items.get(idx);
        if (!plugin.getShopManager().purchase(player, item)) {
            ChatUtil.send(player, plugin.getMessages().get("shop.not-enough-coins", item.getCost()));
            return;
        }
        ChatUtil.send(player, plugin.getMessages().get("shop.bought", m(item.getNameKey()), item.getCost()));
        open(player);
    }

    private String getItemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return ChatUtil.stripColor(item.getItemMeta().getDisplayName());
        }
        return item.getType().name();
    }

    private record DisplayEntry(ItemStack display, long price) {}
}
