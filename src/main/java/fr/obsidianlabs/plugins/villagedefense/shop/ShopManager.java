package fr.obsidianlabs.plugins.villagedefense.shop;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.compat.VersionCompat;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

public class ShopManager {

    private final VillageDefensePlugin plugin;
    private final List<ShopItem> items = new ArrayList<>();

    public ShopManager(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        registerItems();
        plugin.getLogger().info("[ShopManager] " + items.size() + " articles enregistrés.");
    }

    private void registerItems() {
        // --- Consommables ---
        register("bread_stack", Material.BREAD, 10,
            ShopItem.Category.CONSUMABLES,
            new ItemStack(Material.BREAD, 16));

        register("golden_apple", Material.GOLDEN_APPLE, 50,
            ShopItem.Category.CONSUMABLES,
            new ItemStack(Material.GOLDEN_APPLE, 1));

        register("heal_potion", Material.POTION, 40,
            ShopItem.Category.CONSUMABLES,
            createPotion(Material.POTION, VersionCompat.HEALING, true));

        register("strength_potion", Material.POTION, 60,
            ShopItem.Category.CONSUMABLES,
            createPotion(Material.POTION, org.bukkit.potion.PotionType.STRENGTH, false));

        register("speed_potion", Material.POTION, 40,
            ShopItem.Category.CONSUMABLES,
            createPotion(Material.POTION, VersionCompat.SWIFTNESS, true));

        register("regen_potion", Material.POTION, 55,
            ShopItem.Category.CONSUMABLES,
            createPotion(Material.POTION, VersionCompat.REGENERATION, true));

        // --- Armes ---
        register("iron_sword_upgrade", Material.DIAMOND_SWORD, 150,
            ShopItem.Category.WEAPONS,
            createEnchanted(new ItemStack(Material.DIAMOND_SWORD), VersionCompat.SHARPNESS, 1));

        register("power_bow", Material.BOW, 120,
            ShopItem.Category.WEAPONS,
            createEnchanted(new ItemStack(Material.BOW), VersionCompat.POWER, 3));

        // --- Armures ---
        register("diamond_helmet", Material.DIAMOND_HELMET, 100,
            ShopItem.Category.ARMOR,
            new ItemStack(Material.DIAMOND_HELMET));

        register("diamond_chestplate", Material.DIAMOND_CHESTPLATE, 180,
            ShopItem.Category.ARMOR,
            new ItemStack(Material.DIAMOND_CHESTPLATE));

        // --- Spéciaux ---
        register("tnt", Material.TNT, 80,
            ShopItem.Category.SPECIAL,
            new ItemStack(Material.TNT, 1));

        register("cobweb_bundle", Material.COBWEB, 30,
            ShopItem.Category.SPECIAL,
            new ItemStack(Material.COBWEB, 8));
    }

    private void register(String id, Material icon, long cost,
                          ShopItem.Category cat, ItemStack... items) {
        this.items.add(new ShopItem(id, icon, cost, cat, items));
    }

    private ItemStack createPotion(Material type, org.bukkit.potion.PotionType potionType, boolean strong) {
        ItemStack potion = new ItemStack(type);
        org.bukkit.inventory.meta.PotionMeta meta = (org.bukkit.inventory.meta.PotionMeta) potion.getItemMeta();
        meta.setBasePotionData(new org.bukkit.potion.PotionData(potionType, false, strong));
        potion.setItemMeta(meta);
        return potion;
    }

    private ItemStack createEnchanted(ItemStack item, org.bukkit.enchantments.Enchantment ench, int level) {
        item.addEnchantment(ench, level);
        return item;
    }

    public List<ShopItem> getItems() { return items; }

    public List<ShopItem> getByCategory(ShopItem.Category category) {
        return items.stream().filter(i -> i.getCategory() == category).toList();
    }

    public ShopItem getById(String id) {
        return items.stream().filter(i -> i.getId().equals(id)).findFirst().orElse(null);
    }

    /**
     * Achète un article pour un joueur.
     * @return true si l'achat a réussi
     */
    public boolean purchase(org.bukkit.entity.Player player, ShopItem item) {
        var vdPlayer = plugin.getPlayerManager().getPlayer(player);
        if (vdPlayer == null) return false;
        if (!vdPlayer.removeCoins(item.getCost())) return false;

        if (item.getItemsToGive() != null) {
            for (ItemStack stack : item.getItemsToGive()) {
                if (stack != null) {
                    java.util.HashMap<Integer, ItemStack> leftOver = player.getInventory().addItem(stack.clone());
                    // Si inventaire plein, drop au sol
                    leftOver.values().forEach(i -> player.getWorld().dropItem(player.getLocation(), i));
                }
            }
        }

        return true;
    }
}
