package fr.obsidianlabs.plugins.villagedefense.shop;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Lit les articles d'un coffre double pour les exposer dans le shop en partie.
 * Chaque ItemStack non-null du coffre devient un article achetable.
 * Le prix est déterminé par material dans config.yml (shop-prices.<MATERIAL>: <cost>).
 */
public class ChestShopReader {

    private final VillageDefensePlugin plugin;

    public ChestShopReader(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Lit le coffre avec les prix spécifiques à l'arène (priorité) puis config globale.
     */
    public List<ChestShopEntry> readChest(Location loc, Arena arena) {
        List<ChestShopEntry> entries = new ArrayList<>();
        if (loc == null) return entries;

        Block block = loc.getBlock();
        if (!(block.getState() instanceof Chest chestBlock)) return entries;

        Inventory inv = chestBlock.getInventory();
        if (inv.getHolder() instanceof DoubleChest dc) {
            inv = dc.getInventory();
        }

        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType().isAir()) continue;
            long price = arena != null
                ? arena.getShopItemPrice(i, getDefaultPriceForItem(item))
                : getDefaultPriceForItem(item);
            entries.add(new ChestShopEntry(item.clone(), price, i));
        }

        return entries;
    }

    /** Surcharge sans arène (fallback config uniquement). */
    public List<ChestShopEntry> readChest(Location loc) {
        return readChest(loc, null);
    }

    /** Retourne l'inventaire brut du coffre (pour l'affichage dans ShopPricesGUI). */
    public Inventory getChestInventory(Location loc) {
        if (loc == null) return null;
        Block block = loc.getBlock();
        if (!(block.getState() instanceof Chest chestBlock)) return null;
        Inventory inv = chestBlock.getInventory();
        if (inv.getHolder() instanceof DoubleChest dc) inv = dc.getInventory();
        return inv;
    }

    /** Prix par défaut : config material puis config default. */
    public long getDefaultPriceForItem(ItemStack item) {
        String materialKey = item.getType().name().toLowerCase();
        long configured = plugin.getConfig().getLong("shop-prices." + materialKey, -1);
        if (configured >= 0) return configured;
        return plugin.getConfig().getLong("shop-prices.default", 25);
    }

    public record ChestShopEntry(ItemStack item, long price, int slotIndex) {}
}
