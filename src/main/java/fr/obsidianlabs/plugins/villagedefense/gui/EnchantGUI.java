package fr.obsidianlabs.plugins.villagedefense.gui;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaState;
import fr.obsidianlabs.plugins.villagedefense.compat.VersionCompat;
import fr.obsidianlabs.plugins.villagedefense.player.VDPlayer;
import fr.obsidianlabs.plugins.villagedefense.util.ChatUtil;
import fr.obsidianlabs.plugins.villagedefense.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI d'enchantement personnalisé.
 *
 * Layout (54 slots, 6 rangées) :
 *  Rangée 0 : séparateurs + solde joueur (slot 4)
 *  Rangée 1 : enchantements arme     (slots 10, 12, 14)
 *  Rangée 2 : enchantements arc      (slots 19, 21, 23)
 *  Rangée 3 : enchantements armure   (slots 28, 30, 32)
 *  Rangée 4 : séparateurs noirs
 *  Rangée 5 : bouton retour (slot 49)
 */
public class EnchantGUI {

    public static final String TITLE = "gui.enchant.title";

    // ── Définition des options d'enchantement ─────────────────────────────────

    private enum TargetSlot { HAND, BOW, ALL_ARMOR, BOOTS, ANY }

    record EnchantOption(
        Material icon,
        String nameKey,
        String descKey,
        Enchantment enchantment,
        TargetSlot target,
        int maxLevel,
        long baseCost          // coût en pièces pour le niveau 1 ; niveau n = baseCost * n
    ) {}

    static final EnchantOption[] OPTIONS = {
        // ── Arme ──
        new EnchantOption(Material.IRON_SWORD,
            "gui.enchant.opt-name.sharpness",   "gui.enchant.opt-desc.sharpness",
            VersionCompat.SHARPNESS,        TargetSlot.HAND,      5,  300),
        new EnchantOption(Material.BLAZE_ROD,
            "gui.enchant.opt-name.fire-aspect", "gui.enchant.opt-desc.fire-aspect",
            Enchantment.FIRE_ASPECT,         TargetSlot.HAND,      2,  400),
        new EnchantOption(Material.BOOK,
            "gui.enchant.opt-name.durability",  "gui.enchant.opt-desc.durability",
            VersionCompat.UNBREAKING,       TargetSlot.ANY,       3,  150),
        // ── Arc ──
        new EnchantOption(Material.BOW,
            "gui.enchant.opt-name.power",       "gui.enchant.opt-desc.power",
            VersionCompat.POWER,             TargetSlot.BOW,       5,  300),
        new EnchantOption(Material.FIRE_CHARGE,
            "gui.enchant.opt-name.flame",       "gui.enchant.opt-desc.flame",
            VersionCompat.FLAME,             TargetSlot.BOW,       1,  400),
        new EnchantOption(Material.ARROW,
            "gui.enchant.opt-name.infinity",    "gui.enchant.opt-desc.infinity",
            VersionCompat.INFINITY,          TargetSlot.BOW,       1,  800),
        // ── Armure ──
        new EnchantOption(Material.IRON_CHESTPLATE,
            "gui.enchant.opt-name.protection",  "gui.enchant.opt-desc.protection",
            VersionCompat.PROTECTION,        TargetSlot.ALL_ARMOR, 4, 200),
        new EnchantOption(Material.FEATHER,
            "gui.enchant.opt-name.feather-falling", "gui.enchant.opt-desc.feather-falling",
            VersionCompat.FEATHER_FALLING,   TargetSlot.BOOTS,     4,  200),
        new EnchantOption(Material.EXPERIENCE_BOTTLE,
            "gui.enchant.opt-name.mending",     "gui.enchant.opt-desc.mending",
            Enchantment.MENDING,             TargetSlot.ANY,       1,  600),
    };

    // Slots d'affichage — indexés par position dans OPTIONS
    static final int[] DISPLAY_SLOTS = {
        10, 12, 14,   // arme
        19, 21, 23,   // arc
        28, 30, 32    // armure
    };

    private static final int INFO_SLOT = 4;
    private static final int BACK_SLOT = 49;

    // ── Constructeur ──────────────────────────────────────────────────────────

    private final VillageDefensePlugin plugin;

    public EnchantGUI(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    private String m(String key, Object... args) {
        return plugin.getMessages().format(key, args);
    }

    // ── Ouverture ─────────────────────────────────────────────────────────────

    public void open(Player player) {
        VDPlayer vdp = plugin.getPlayerManager().getPlayer(player);
        if (vdp == null) return;

        Inventory inv = Bukkit.createInventory(null, 54, m(TITLE));
        for (int i = 0; i < 54; i++) inv.setItem(i, ItemBuilder.separator());
        for (int i = 36; i < 45; i++) inv.setItem(i, ItemBuilder.separator(Material.BLACK_STAINED_GLASS_PANE));

        inv.setItem(INFO_SLOT, new ItemBuilder(Material.GOLD_INGOT)
            .name(m("gui.enchant.balance-title", vdp.getCoins()))
            .lore(m("gui.enchant.info-line1"),
                  m("gui.enchant.info-line2"))
            .build());

        Arena arena = plugin.getArenaManager().getArenaOfPlayer(player);
        for (int i = 0; i < OPTIONS.length && i < DISPLAY_SLOTS.length; i++) {
            long price = arena != null ? arena.getEnchantPrice(i, OPTIONS[i].baseCost()) : OPTIONS[i].baseCost();
            inv.setItem(DISPLAY_SLOTS[i], buildOptionItem(player, vdp, OPTIONS[i], price));
        }

        inv.setItem(BACK_SLOT, new ItemBuilder(Material.ARROW)
            .name(m("gui.enchant.back-button"))
            .build());

        player.openInventory(inv);
    }

    // ── Builder d'item ────────────────────────────────────────────────────────

    private ItemStack buildOptionItem(Player player, VDPlayer vdp, EnchantOption opt, long baseCost) {
        ItemStack target = findTargetItem(player, opt.target());
        int currentLevel = (target != null && target.hasItemMeta())
            ? target.getEnchantmentLevel(opt.enchantment()) : 0;

        boolean maxed    = currentLevel >= opt.maxLevel();
        long    cost     = baseCost * (currentLevel + 1);
        boolean canAfford = vdp.getCoins() >= cost;
        boolean hasItem  = target != null;

        List<String> lore = new ArrayList<>();
        lore.add("&7" + m(opt.descKey()));
        lore.add("");
        lore.add(m("gui.enchant.level-label",
                   currentLevel > 0 ? "&e" + currentLevel : m("gui.enchant.current-none"), opt.maxLevel()));
        lore.add(m("gui.enchant.target-label", targetLabel(opt.target())));
        lore.add("");

        if (maxed) {
            lore.add(m("gui.enchant.maxed"));
        } else if (!hasItem) {
            lore.add(m("gui.enchant.no-compatible"));
        } else {
            lore.add(m("gui.enchant.cost-label", canAfford ? "&6" : "&c", cost));
            lore.add(canAfford ? m("gui.enchant.click-apply") : m("gui.enchant.no-coins"));
        }

        Material icon = maxed ? Material.ENCHANTED_BOOK : opt.icon();
        ItemBuilder builder = new ItemBuilder(icon)
            .name(m(opt.nameKey()) + (maxed ? m("gui.enchant.max-suffix") : m("gui.enchant.level-suffix", currentLevel)))
            .lore(lore);

        if (maxed || (hasItem && canAfford)) builder.glowing();
        return builder.build();
    }

    // ── Gestion des clics ─────────────────────────────────────────────────────

    public void handleClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (clicked.getType() == Material.GRAY_STAINED_GLASS_PANE
         || clicked.getType() == Material.BLACK_STAINED_GLASS_PANE) return;

        int slot = event.getSlot();

        if (slot == BACK_SLOT) {
            plugin.getShopGui().open(player);
            return;
        }

        // Retrouver l'option correspondant au slot cliqué
        for (int i = 0; i < OPTIONS.length && i < DISPLAY_SLOTS.length; i++) {
            if (DISPLAY_SLOTS[i] != slot) continue;
            applyEnchant(player, OPTIONS[i], i);
            return;
        }
    }

    private void applyEnchant(Player player, EnchantOption opt, int optionIndex) {
        VDPlayer vdp = plugin.getPlayerManager().getPlayer(player);
        if (vdp == null) return;

        Arena arena = plugin.getArenaManager().getArenaOfPlayer(player);
        if (arena == null || (arena.getState() != ArenaState.RUNNING
                && arena.getState() != ArenaState.BETWEEN_WAVES)) return;

        List<ItemStack> targets = findAllTargetItems(player, opt.target());
        if (targets.isEmpty()) {
            ChatUtil.send(player, m("gui.enchant.no-compatible-found"));
            return;
        }

        // Déterminer le niveau actuel (maximum parmi les items cibles)
        int currentLevel = targets.stream()
            .mapToInt(it -> it.getEnchantmentLevel(opt.enchantment()))
            .max().orElse(0);

        if (currentLevel >= opt.maxLevel()) {
            ChatUtil.send(player, m("gui.enchant.already-max"));
            return;
        }

        long baseCost = arena.getEnchantPrice(optionIndex, opt.baseCost());
        long cost = baseCost * (currentLevel + 1);
        if (vdp.getCoins() < cost) {
            ChatUtil.send(player, m("gui.enchant.not-enough-coins", cost));
            return;
        }

        int nextLevel = currentLevel + 1;
        for (ItemStack it : targets) {
            it.addUnsafeEnchantment(opt.enchantment(), nextLevel);
        }

        vdp.removeCoins(cost);
        ChatUtil.send(player, m("gui.enchant.applied", m(opt.nameKey()), nextLevel, cost));
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.2f);

        open(player); // rafraîchir le GUI
    }

    // ── Utilitaires ───────────────────────────────────────────────────────────

    /** Retourne le premier item compatible pour l'affichage (peut être null). */
    private ItemStack findTargetItem(Player player, TargetSlot target) {
        List<ItemStack> all = findAllTargetItems(player, target);
        return all.isEmpty() ? null : all.get(0);
    }

    /** Retourne tous les items de l'équipement sur lesquels l'enchantement peut s'appliquer. */
    private List<ItemStack> findAllTargetItems(Player player, TargetSlot target) {
        List<ItemStack> result = new ArrayList<>();
        switch (target) {
            case HAND -> {
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (!hand.getType().isAir()) result.add(hand);
            }
            case BOW -> {
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType() == Material.BOW || hand.getType() == Material.CROSSBOW)
                    result.add(hand);
                // Chercher aussi dans l'inventaire
                else {
                    for (ItemStack it : player.getInventory().getContents()) {
                        if (it != null && (it.getType() == Material.BOW || it.getType() == Material.CROSSBOW)) {
                            result.add(it);
                            break;
                        }
                    }
                }
            }
            case ALL_ARMOR -> {
                addIfNotNull(result, player.getInventory().getHelmet());
                addIfNotNull(result, player.getInventory().getChestplate());
                addIfNotNull(result, player.getInventory().getLeggings());
                addIfNotNull(result, player.getInventory().getBoots());
            }
            case BOOTS -> {
                addIfNotNull(result, player.getInventory().getBoots());
            }
            case ANY -> {
                // Main ou toute l'armure
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (!hand.getType().isAir()) result.add(hand);
                addIfNotNull(result, player.getInventory().getHelmet());
                addIfNotNull(result, player.getInventory().getChestplate());
                addIfNotNull(result, player.getInventory().getLeggings());
                addIfNotNull(result, player.getInventory().getBoots());
            }
        }
        // Filtrer les items qui peuvent réellement recevoir l'enchantement
        return result;
    }

    private void addIfNotNull(List<ItemStack> list, ItemStack item) {
        if (item != null && !item.getType().isAir()) list.add(item);
    }

    private String targetLabel(TargetSlot target) {
        return m("gui.enchant.target." + target.name().toLowerCase());
    }
}
