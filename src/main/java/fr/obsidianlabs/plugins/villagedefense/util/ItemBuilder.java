package fr.obsidianlabs.plugins.villagedefense.util;

import fr.obsidianlabs.plugins.villagedefense.compat.VersionCompat;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this(material, 1);
    }

    public ItemBuilder(Material material, int amount) {
        this.item = new ItemStack(material, amount);
        this.meta = item.getItemMeta();
    }

    public ItemBuilder name(String name) {
        meta.setDisplayName(ChatUtil.color("&r" + name));
        return this;
    }

    public ItemBuilder lore(String... lines) {
        meta.setLore(List.of(lines).stream()
            .map(line -> ChatUtil.color("&7" + line))
            .toList());
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        meta.setLore(lines.stream()
            .map(line -> ChatUtil.color("&7" + line))
            .toList());
        return this;
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(amount);
        return this;
    }

    public ItemBuilder enchant(Enchantment enchantment, int level) {
        meta.addEnchant(enchantment, level, true);
        return this;
    }

    public ItemBuilder glowing() {
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.addEnchant(VersionCompat.UNBREAKING, 1, true);
        return this;
    }

    public ItemBuilder flags(ItemFlag... flags) {
        meta.addItemFlags(flags);
        return this;
    }

    public ItemBuilder hideAll() {
        meta.addItemFlags(ItemFlag.values());
        return this;
    }

    public ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }

    /** Crée un séparateur vide pour les GUI */
    public static ItemStack separator() {
        return new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .name("&r")
                .build();
    }

    public static ItemStack separator(Material material) {
        return new ItemBuilder(material)
                .name("&r")
                .build();
    }
}
