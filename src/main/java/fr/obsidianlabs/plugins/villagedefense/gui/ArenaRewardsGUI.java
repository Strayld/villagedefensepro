package fr.obsidianlabs.plugins.villagedefense.gui;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaSetup;
import fr.obsidianlabs.plugins.villagedefense.util.ChatUtil;
import fr.obsidianlabs.plugins.villagedefense.util.ItemBuilder;
import fr.obsidianlabs.plugins.villagedefense.util.Perm;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Sous-GUI « Récompenses » — récompenses données à chaque joueur en fin de partie.
 *
 * Layout 45 slots :
 *  Rangée 0 : séparateurs
 *  Rangée 1 : [sep][VAULT][sep][ADD_CMD][sep][CLEAR_CMDS][sep][sep][sep]
 *  Rangée 2 : séparateurs (en-tête liste commandes)
 *  Rangée 3 : slots commandes [0..8]
 *  Rangée 4 : [sep][sep][sep][sep][BACK][sep][sep][sep][sep]
 */
public class ArenaRewardsGUI {

    public static final String TITLE = "gui.arena-rewards.title";

    private static final int SLOT_VAULT       = 10;
    private static final int SLOT_ADD_CMD     = 12;
    private static final int SLOT_CLEAR_CMDS  = 14;
    private static final int CMD_ROW_START    = 27; // slots 27-35 pour les commandes
    private static final int SLOT_BACK        = 40;

    private final VillageDefensePlugin plugin;

    public ArenaRewardsGUI(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    private String m(String key, Object... args) {
        return plugin.getMessages().format(key, args);
    }

    public void open(Player admin, Arena arena) {
        if (!plugin.hasPermission(admin, Perm.ADMIN_SETUP)) {
            ChatUtil.send(admin, m("general.no-permission"));
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 45, m(TITLE));
        for (int i = 0; i < 45; i++) inv.setItem(i, ItemBuilder.separator());

        inv.setItem(SLOT_VAULT,      buildVaultItem(arena));
        inv.setItem(SLOT_ADD_CMD,    buildAddCommandItem());
        inv.setItem(SLOT_CLEAR_CMDS, buildClearCommandsItem(arena));

        // Commandes existantes (jusqu'à 9 dans la rangée 3)
        List<String> cmds = arena.getRewardCommands();
        for (int i = 0; i < Math.min(cmds.size(), 9); i++) {
            inv.setItem(CMD_ROW_START + i, buildCommandItem(i, cmds.get(i)));
        }

        inv.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW)
            .name(m("admin.common.back")).build());

        admin.openInventory(inv);
    }

    // ── Builders ─────────────────────────────────────────────────────────────

    private ItemStack buildVaultItem(Arena arena) {
        boolean enabled = arena.isRewardVaultEnabled();
        boolean vaultAvailable = plugin.getHookManager().isVaultEconomyEnabled();
        double amount = arena.getRewardVaultAmount();

        ItemBuilder b = new ItemBuilder(enabled ? Material.GOLD_INGOT : Material.IRON_INGOT)
            .name(m("admin.rewards.vault-item") + (enabled ? " &a✓" : " &c✗"));

        List<String> lore = new ArrayList<>();
        if (!vaultAvailable) {
            lore.add(m("admin.rewards.vault-unavailable"));
        } else {
            lore.add(enabled ? m("admin.rewards.vault-enabled") : m("admin.rewards.vault-disabled"));
            lore.add(m("admin.rewards.vault-amount", amount));
            lore.add("");
            lore.add(m("admin.rewards.vault-click-toggle"));
            lore.add(m("admin.rewards.vault-shift-amount"));
        }
        return b.lore(lore).build();
    }

    private ItemStack buildAddCommandItem() {
        return new ItemBuilder(Material.WRITABLE_BOOK)
            .name(m("admin.rewards.add-command"))
            .lore(m("admin.rewards.add-command-lore1"),
                  m("admin.rewards.add-command-lore2"),
                  "", m("admin.rewards.add-command-click"))
            .build();
    }

    private ItemStack buildClearCommandsItem(Arena arena) {
        int count = arena.getRewardCommands().size();
        return new ItemBuilder(count > 0 ? Material.TNT : Material.BARRIER)
            .name(m("admin.rewards.clear-commands"))
            .lore(m("admin.rewards.clear-commands-lore", count),
                  "", m("admin.rewards.clear-commands-shift"))
            .build();
    }

    private ItemStack buildCommandItem(int index, String command) {
        return new ItemBuilder(Material.PAPER)
            .name("&e#" + (index + 1) + " &7" + command)
            .lore(m("admin.rewards.command-lore"),
                  "", m("admin.rewards.command-click-remove"))
            .build();
    }

    // ── Clics ────────────────────────────────────────────────────────────────

    public void handleClick(InventoryClickEvent event, Player admin) {
        event.setCancelled(true);
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlot() < 0 || event.getRawSlot() >= topSize) return;
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
        if (event.getCurrentItem().getType() == Material.GRAY_STAINED_GLASS_PANE
         || event.getCurrentItem().getType() == Material.BLACK_STAINED_GLASS_PANE) return;

        ArenaSetup setup = plugin.getArenaManager().getSetup(admin);
        if (setup == null) return;
        Arena arena = setup.getArena();
        ClickType click = event.getClick();
        int slot = event.getRawSlot();

        if (slot == SLOT_VAULT) {
            if (!plugin.getHookManager().isVaultEconomyEnabled()) {
                ChatUtil.send(admin, m("admin.rewards.vault-unavailable"));
                return;
            }
            if (click.isShiftClick()) {
                plugin.getChatInputManager().prompt(admin, m("admin.rewards.prompt-amount"), input -> {
                    try {
                        double amount = Double.parseDouble(input.replace(",", "."));
                        arena.setRewardVaultAmount(amount);
                        ChatUtil.send(admin, m("admin.rewards.amount-set", amount));
                    } catch (NumberFormatException e) {
                        ChatUtil.send(admin, m("admin.rewards.invalid-amount"));
                    }
                    open(admin, arena);
                });
            } else {
                arena.setRewardVaultEnabled(!arena.isRewardVaultEnabled());
                open(admin, arena);
            }
            return;
        }

        if (slot == SLOT_ADD_CMD) {
            if (arena.getRewardCommands().size() >= 9) {
                ChatUtil.send(admin, m("admin.rewards.commands-full"));
                return;
            }
            plugin.getChatInputManager().prompt(admin, m("admin.rewards.prompt-command"), input -> {
                arena.addRewardCommand(input.trim());
                ChatUtil.send(admin, m("admin.rewards.command-added", input.trim()));
                open(admin, arena);
            });
            return;
        }

        if (slot == SLOT_CLEAR_CMDS) {
            if (click.isShiftClick()) {
                arena.clearRewardCommands();
                ChatUtil.send(admin, m("admin.rewards.commands-cleared"));
                open(admin, arena);
            }
            return;
        }

        // Suppression d'une commande par clic sur son item
        if (slot >= CMD_ROW_START && slot < CMD_ROW_START + 9) {
            int index = slot - CMD_ROW_START;
            if (index < arena.getRewardCommands().size()) {
                String removed = arena.getRewardCommands().get(index);
                arena.removeRewardCommand(index);
                ChatUtil.send(admin, m("admin.rewards.command-removed", removed));
                open(admin, arena);
            }
            return;
        }

        if (slot == SLOT_BACK) {
            plugin.getArenaSetupGui().open(admin, arena);
        }
    }
}
