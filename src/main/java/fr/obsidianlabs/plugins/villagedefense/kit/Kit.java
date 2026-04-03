package fr.obsidianlabs.plugins.villagedefense.kit;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.compat.VersionCompat;
import fr.obsidianlabs.plugins.villagedefense.util.ChatUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;

import java.util.List;

/**
 * Kit de joueur - définit l'équipement et les capacités d'un joueur en partie.
 */
public abstract class Kit {

    protected final VillageDefensePlugin plugin;
    protected final KitType type;

    public Kit(VillageDefensePlugin plugin, KitType type) {
        this.plugin = plugin;
        this.type = type;
    }

    /** Applique l'équipement et les effets du kit au joueur */
    public void apply(Player player) {
        player.getInventory().clear();
        player.getEquipment().clear();

        // Appliquer l'inventaire
        ItemStack[] items = getInventoryItems();
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null) player.getInventory().setItem(i, items[i]);
        }

        // Armure
        player.getEquipment().setHelmet(getHelmet());
        player.getEquipment().setChestplate(getChestplate());
        player.getEquipment().setLeggings(getLeggings());
        player.getEquipment().setBoots(getBoots());

        // Effets de potion
        for (PotionEffect effect : getPotionEffects()) {
            player.addPotionEffect(effect);
        }

        // HP max personnalisé
        double maxHp = getMaxHealth();
        if (maxHp > 0) {
            var attr = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
            if (attr != null) {
                attr.setBaseValue(maxHp);
                player.setHealth(maxHp);
            }
        }
    }

    /** Remet le joueur dans un état neutre */
    public static void reset(Player player) {
        player.getInventory().clear();
        player.getEquipment().clear();
        for (PotionEffect e : player.getActivePotionEffects()) {
            player.removePotionEffect(e.getType());
        }
        var maxHp = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
        if (maxHp != null) { maxHp.setBaseValue(20.0); player.setHealth(20.0); }
        // Réinitialiser les dégâts de base (peut être modifié par les perks)
        // Ne PAS toucher GENERIC_ATTACK_SPEED : OldCombatMechanics le gère globalement
        var atk = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE);
        if (atk != null) atk.setBaseValue(1.0);
        player.setFoodLevel(20);
        player.setSaturation(20f);
    }

    // --- A implémenter ---

    public abstract ItemStack[] getInventoryItems();
    public abstract ItemStack getHelmet();
    public abstract ItemStack getChestplate();
    public abstract ItemStack getLeggings();
    public abstract ItemStack getBoots();
    public abstract List<PotionEffect> getPotionEffects();
    public abstract double getMaxHealth();

    // --- Getters ---

    public KitType getType() { return type; }

    public int getCost() {
        return plugin.getConfig().getInt("kits." + type.getConfigKey() + ".cost", 0);
    }

    protected ItemStack abilityItem(Material material, String abilityId, String nameKey, String loreKey) {
        return abilityItem(material, 1, abilityId, nameKey, loreKey, true);
    }

    protected ItemStack abilityItem(Material material, int amount, String abilityId,
                                    String nameKey, String loreKey, boolean glowing) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName(ChatUtil.color("&r" + plugin.getMessages().format(nameKey)));
        meta.setLore(plugin.getMessages().lines(loreKey));
        meta.getPersistentDataContainer().set(
            plugin.getKey("kit_ability"), PersistentDataType.STRING, abilityId);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        if (glowing) {
            meta.addEnchant(VersionCompat.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        item.setItemMeta(meta);
        return item;
    }

    public static String getAbilityId(VillageDefensePlugin plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }

        return meta.getPersistentDataContainer().get(
            plugin.getKey("kit_ability"), PersistentDataType.STRING);
    }
}
