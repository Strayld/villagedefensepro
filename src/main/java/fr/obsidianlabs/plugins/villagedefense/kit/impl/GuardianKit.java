package fr.obsidianlabs.plugins.villagedefense.kit.impl;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.compat.VersionCompat;
import fr.obsidianlabs.plugins.villagedefense.kit.Kit;
import fr.obsidianlabs.plugins.villagedefense.kit.KitType;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.List;

public class GuardianKit extends Kit {

    public GuardianKit(VillageDefensePlugin plugin) {
        super(plugin, KitType.GUARDIAN);
    }

    @Override
    public ItemStack[] getInventoryItems() {
        ItemStack[] items = new ItemStack[9];

        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        sword.addUnsafeEnchantment(VersionCompat.SHARPNESS, 2);
        items[0] = sword;
        items[1] = new ItemStack(Material.GOLDEN_APPLE, 3);
        items[2] = abilityItem(Material.SHIELD, "guardian_shield_wall",
            "kit.ability.guardian.shield-wall.name", "kit.ability.guardian.shield-wall.lore");
        items[3] = abilityItem(Material.BELL, "guardian_taunt",
            "kit.ability.guardian.taunt.name", "kit.ability.guardian.taunt.lore");
        items[8] = new ItemStack(Material.COOKED_BEEF, 16);
        return items;
    }

    @Override public ItemStack getHelmet()     { return protection(new ItemStack(Material.IRON_HELMET), 2); }
    @Override public ItemStack getChestplate() { return protection(new ItemStack(Material.DIAMOND_CHESTPLATE), 2); }
    @Override public ItemStack getLeggings()   { return protection(new ItemStack(Material.IRON_LEGGINGS), 2); }
    @Override public ItemStack getBoots()      { return protection(new ItemStack(Material.IRON_BOOTS), 2); }

    @Override
    public List<PotionEffect> getPotionEffects() {
        return List.of();
    }

    @Override
    public double getMaxHealth() {
        return 30.0;
    }

    private ItemStack protection(ItemStack item, int level) {
        item.addUnsafeEnchantment(VersionCompat.PROTECTION, level);
        return item;
    }
}
