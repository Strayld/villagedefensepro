package fr.obsidianlabs.plugins.villagedefense.kit.impl;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.compat.VersionCompat;
import fr.obsidianlabs.plugins.villagedefense.kit.Kit;
import fr.obsidianlabs.plugins.villagedefense.kit.KitType;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public class RangerKit extends Kit {

    public RangerKit(VillageDefensePlugin plugin) {
        super(plugin, KitType.RANGER);
    }

    @Override
    public ItemStack[] getInventoryItems() {
        ItemStack[] items = new ItemStack[9];

        ItemStack bow = new ItemStack(Material.BOW);
        bow.addUnsafeEnchantment(VersionCompat.INFINITY, 1);
        bow.addUnsafeEnchantment(VersionCompat.POWER, 2);
        items[0] = bow;
        items[1] = new ItemStack(Material.STONE_SWORD);
        items[2] = abilityItem(Material.FEATHER, "ranger_charged_shot",
            "kit.ability.ranger.charged-shot.name", "kit.ability.ranger.charged-shot.lore");
        items[3] = abilityItem(Material.SPECTRAL_ARROW, "ranger_piercing_arrow",
            "kit.ability.ranger.piercing-arrow.name", "kit.ability.ranger.piercing-arrow.lore");
        items[7] = new ItemStack(Material.ARROW, 1);
        items[8] = new ItemStack(Material.COOKED_CHICKEN, 16);
        return items;
    }

    @Override public ItemStack getHelmet()     { return new ItemStack(Material.CHAINMAIL_HELMET); }
    @Override public ItemStack getChestplate() { return new ItemStack(Material.CHAINMAIL_CHESTPLATE); }
    @Override public ItemStack getLeggings()   { return new ItemStack(Material.CHAINMAIL_LEGGINGS); }
    @Override public ItemStack getBoots()      { return new ItemStack(Material.LEATHER_BOOTS); }

    @Override
    public List<PotionEffect> getPotionEffects() {
        return List.of(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false));
    }

    @Override
    public double getMaxHealth() {
        return 20.0;
    }
}
