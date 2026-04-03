package fr.obsidianlabs.plugins.villagedefense.kit.impl;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.compat.VersionCompat;
import fr.obsidianlabs.plugins.villagedefense.kit.Kit;
import fr.obsidianlabs.plugins.villagedefense.kit.KitType;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.List;

public class BerserkerKit extends Kit {

    public BerserkerKit(VillageDefensePlugin plugin) {
        super(plugin, KitType.BERSERKER);
    }

    @Override
    public ItemStack[] getInventoryItems() {
        ItemStack[] items = new ItemStack[9];

        ItemStack axe = new ItemStack(Material.IRON_AXE);
        axe.addUnsafeEnchantment(VersionCompat.SHARPNESS, 2);
        items[0] = axe;
        items[1] = new ItemStack(Material.STONE_SWORD);
        items[2] = abilityItem(Material.RED_DYE, "berserker_rage",
            "kit.ability.berserker.rage.name", "kit.ability.berserker.rage.lore");
        items[3] = abilityItem(Material.GHAST_TEAR, "berserker_life_steal",
            "kit.ability.berserker.life-steal.name", "kit.ability.berserker.life-steal.lore");
        items[8] = new ItemStack(Material.COOKED_BEEF, 16);
        return items;
    }

    @Override public ItemStack getHelmet()     { return new ItemStack(Material.IRON_HELMET); }
    @Override public ItemStack getChestplate() { return new ItemStack(Material.CHAINMAIL_CHESTPLATE); }
    @Override public ItemStack getLeggings()   { return new ItemStack(Material.IRON_LEGGINGS); }
    @Override public ItemStack getBoots()      { return new ItemStack(Material.CHAINMAIL_BOOTS); }

    @Override
    public List<PotionEffect> getPotionEffects() {
        return List.of();
    }

    @Override
    public double getMaxHealth() {
        return 22.0;
    }
}
