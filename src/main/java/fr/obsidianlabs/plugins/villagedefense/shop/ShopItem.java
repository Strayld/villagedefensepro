package fr.obsidianlabs.plugins.villagedefense.shop;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Un article disponible dans le shop en partie.
 */
public class ShopItem {

    public enum Category {
        WEAPONS, ARMOR, CONSUMABLES, UPGRADES, SPECIAL
    }

    private final String id;
    private final Material icon;
    private final long cost;
    private final Category category;
    private final ItemStack[] itemsToGive; // peut être null si c'est un effet/upgrade

    public ShopItem(String id, Material icon, long cost,
                    Category category, ItemStack... items) {
        this.id = id;
        this.icon = icon;
        this.cost = cost;
        this.category = category;
        this.itemsToGive = items;
    }

    public String getId() { return id; }
    public String getNameKey() { return "shop.item." + id + ".name"; }
    public String getLoreKey() { return "shop.item." + id + ".lore"; }
    public Material getIcon() { return icon; }
    public long getCost() { return cost; }
    public Category getCategory() { return category; }
    public ItemStack[] getItemsToGive() { return itemsToGive; }
}
