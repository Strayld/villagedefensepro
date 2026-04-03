package fr.obsidianlabs.plugins.villagedefense.kit.impl;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.compat.VersionCompat;
import fr.obsidianlabs.plugins.villagedefense.kit.Kit;
import fr.obsidianlabs.plugins.villagedefense.kit.KitType;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;

import java.util.List;

public class MedicKit extends Kit {

    public MedicKit(VillageDefensePlugin plugin) {
        super(plugin, KitType.MEDIC);
    }

    @Override
    public ItemStack[] getInventoryItems() {
        ItemStack[] items = new ItemStack[9];
        ItemStack scepter = new ItemStack(Material.GOLDEN_HOE);
        scepter.addUnsafeEnchantment(VersionCompat.SHARPNESS, 1);
        items[0] = scepter;
        items[1] = createSplashPotion(VersionCompat.HEALING, 6);
        items[2] = createSplashPotion(VersionCompat.REGENERATION, 3);
        items[3] = abilityItem(Material.HONEY_BOTTLE, "medic_healing_pulse",
            "kit.ability.medic.healing-pulse.name", "kit.ability.medic.healing-pulse.lore");
        items[7] = new ItemStack(Material.GOLDEN_APPLE, 4);
        return items;
    }

    private ItemStack createSplashPotion(PotionType type, int amount) {
        ItemStack potion = new ItemStack(Material.SPLASH_POTION, amount);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        meta.setBasePotionData(new PotionData(type, false, type == VersionCompat.REGENERATION));
        potion.setItemMeta(meta);
        return potion;
    }

    @Override public ItemStack getHelmet()     { return new ItemStack(Material.CHAINMAIL_HELMET); }
    @Override public ItemStack getChestplate() { return new ItemStack(Material.IRON_CHESTPLATE); }
    @Override public ItemStack getLeggings()   { return new ItemStack(Material.CHAINMAIL_LEGGINGS); }
    @Override public ItemStack getBoots()      { return new ItemStack(Material.CHAINMAIL_BOOTS); }

    @Override
    public List<PotionEffect> getPotionEffects() {
        return List.of();
    }

    @Override
    public double getMaxHealth() { return 22.0; }
}
