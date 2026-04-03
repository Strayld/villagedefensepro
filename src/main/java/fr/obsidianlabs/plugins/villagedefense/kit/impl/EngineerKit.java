package fr.obsidianlabs.plugins.villagedefense.kit.impl;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.compat.VersionCompat;
import fr.obsidianlabs.plugins.villagedefense.kit.Kit;
import fr.obsidianlabs.plugins.villagedefense.kit.KitType;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.List;

public class EngineerKit extends Kit {

    public EngineerKit(VillageDefensePlugin plugin) {
        super(plugin, KitType.ENGINEER);
    }

    @Override
    public ItemStack[] getInventoryItems() {
        ItemStack[] items = new ItemStack[9];

        ItemStack wrench = new ItemStack(Material.IRON_PICKAXE);
        wrench.addUnsafeEnchantment(VersionCompat.SHARPNESS, 1);
        items[0] = wrench;
        items[1] = abilityItem(Material.REDSTONE, "engineer_overclock",
            "kit.ability.engineer.overclock.name", "kit.ability.engineer.overclock.lore");
        items[2] = abilityItem(Material.COPPER_INGOT, "engineer_repair_pulse",
            "kit.ability.engineer.repair-pulse.name", "kit.ability.engineer.repair-pulse.lore");
        items[8] = new ItemStack(Material.BREAD, 16);
        return items;
    }

    @Override public ItemStack getHelmet()     { return new ItemStack(Material.IRON_HELMET); }
    @Override public ItemStack getChestplate() { return new ItemStack(Material.CHAINMAIL_CHESTPLATE); }
    @Override public ItemStack getLeggings()   { return new ItemStack(Material.CHAINMAIL_LEGGINGS); }
    @Override public ItemStack getBoots()      { return new ItemStack(Material.IRON_BOOTS); }

    @Override
    public List<PotionEffect> getPotionEffects() {
        return List.of();
    }

    @Override
    public double getMaxHealth() {
        return 22.0;
    }
}
