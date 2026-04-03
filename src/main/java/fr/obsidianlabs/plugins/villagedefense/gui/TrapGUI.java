package fr.obsidianlabs.plugins.villagedefense.gui;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.player.VDPlayer;
import fr.obsidianlabs.plugins.villagedefense.trap.TrapSlot;
import fr.obsidianlabs.plugins.villagedefense.trap.TrapType;
import fr.obsidianlabs.plugins.villagedefense.util.ChatUtil;
import fr.obsidianlabs.plugins.villagedefense.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GUI d'achat/gestion d'un emplacement de piège pendant la partie.
 * Layout 27 slots :
 *  Slot 4  : état de l'emplacement
 *  Slot 9  : SLOW_TRAP
 *  Slot 13 : DAMAGE_MINE
 *  Slot 17 : NET_TRAP
 *  Slot 22 : Retirer le piège actuel (si présent)
 */
public class TrapGUI {

    public static final String TITLE = "gui.trap.title";

    private static final int SLOT_INFO    = 4;
    private static final int SLOT_SLOW    = 9;
    private static final int SLOT_MINE    = 13;
    private static final int SLOT_NET     = 17;
    private static final int SLOT_REMOVE  = 22;

    private final VillageDefensePlugin plugin;

    private record TrapContext(String arenaId, String slotId) {}
    private final Map<UUID, TrapContext> contexts = new HashMap<>();

    public TrapGUI(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    private String m(String key, Object... args) {
        return plugin.getMessages().format(key, args);
    }

    // ── Ouverture ─────────────────────────────────────────────────────────────

    public void open(Player player, Arena arena, TrapSlot trapSlot) {
        VDPlayer vdp = plugin.getPlayerManager().getPlayer(player);
        if (vdp == null) return;

        Inventory inv = Bukkit.createInventory(null, 27, m(TITLE));
        for (int i = 0; i < 27; i++) inv.setItem(i, ItemBuilder.separator());

        inv.setItem(SLOT_INFO, buildInfoItem(trapSlot, vdp));
        inv.setItem(SLOT_SLOW, buildTrapItem(player, TrapType.SLOW_TRAP,    trapSlot, vdp));
        inv.setItem(SLOT_MINE, buildTrapItem(player, TrapType.DAMAGE_MINE,  trapSlot, vdp));
        inv.setItem(SLOT_NET,  buildTrapItem(player, TrapType.NET_TRAP,     trapSlot, vdp));

        if (!trapSlot.isEmpty()) {
            inv.setItem(SLOT_REMOVE, new ItemBuilder(Material.BARRIER)
                .name(m("trap.remove"))
                .lore(m("trap.remove-lore1"),
                      m("trap.remove-lore2"),
                      "",
                      m("trap.remove-lore-click"))
                .build());
        }

        // IMPORTANT : put context AFTER openInventory
        player.openInventory(inv);
        contexts.put(player.getUniqueId(), new TrapContext(arena.getId(), trapSlot.getId()));
    }

    /** Appelé par InventoryCloseListener. */
    public void onInventoryClose(Player player) {
        contexts.remove(player.getUniqueId());
    }

    // ── Clics ─────────────────────────────────────────────────────────────────

    public void handleClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);

        if (event.getCurrentItem() == null
            || event.getCurrentItem().getType() == Material.AIR
            || event.getCurrentItem().getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        TrapContext ctx = contexts.get(player.getUniqueId());
        if (ctx == null) return;

        Arena arena = plugin.getArenaManager().getArena(ctx.arenaId());
        if (arena == null) return;

        TrapSlot trapSlot = arena.getTrapSlots().stream()
            .filter(s -> s.getId().equals(ctx.slotId()))
            .findFirst().orElse(null);
        if (trapSlot == null) return;

        VDPlayer vdp = plugin.getPlayerManager().getPlayer(player);
        if (vdp == null) return;

        int slot = event.getSlot();

        // Retirer le piège actuel
        if (slot == SLOT_REMOVE) {
            if (!trapSlot.isEmpty()) {
                trapSlot.clearTrap();
                plugin.getTrapManager().refreshTrapDisplay(trapSlot);
                ChatUtil.send(player, m("trap.removed"));
                open(player, arena, trapSlot);
            }
            return;
        }

        // Achat d'un piège
        TrapType type = switch (slot) {
            case SLOT_SLOW -> TrapType.SLOW_TRAP;
            case SLOT_MINE -> TrapType.DAMAGE_MINE;
            case SLOT_NET  -> TrapType.NET_TRAP;
            default        -> null;
        };
        if (type == null) return;

        // Vérifier si le joueur peut se permettre le piège
        int cost = plugin.getKitAbilityManager() != null
            ? plugin.getKitAbilityManager().applyStructureDiscount(player, type.getCost())
            : type.getCost();
        if (vdp.getCoins() < cost) {
            ChatUtil.send(player, m("trap.not-enough-coins", cost));
            return;
        }

        // Rembourser si remplacement
        if (!trapSlot.isEmpty()) {
            trapSlot.clearTrap();
        }

        vdp.removeCoins(cost);
        trapSlot.setPlacedTrap(type);
        if (plugin.getKitAbilityManager() != null) {
            plugin.getKitAbilityManager().onTrapPlaced(player, trapSlot);
        }
        plugin.getTrapManager().refreshTrapDisplay(trapSlot);
        ChatUtil.send(player, m("trap.placed", m(type.getNameKey())));

        contexts.remove(player.getUniqueId());
        player.closeInventory();
    }

    // ── Builders ──────────────────────────────────────────────────────────────

    private org.bukkit.inventory.ItemStack buildInfoItem(TrapSlot trapSlot, VDPlayer vdp) {
        if (trapSlot.isEmpty()) {
            return new ItemBuilder(Material.TRIPWIRE_HOOK)
                .name(m("trap.slot-empty-title"))
                .lore(m("trap.slot-empty-lore1"),
                      m("trap.coins", vdp.getCoins()))
                .build();
        }
        TrapType current = trapSlot.getPlacedTrap();
        List<String> lore = new ArrayList<>();
        lore.add(m(current.getDescKey()));
        lore.add("");
        if (trapSlot.isOnCooldown()) {
            lore.add(m("trap.cooldown", trapSlot.getRemainingCooldownSeconds()));
        } else {
            lore.add(m("trap.ready"));
        }
        lore.add("");
        lore.add(m("trap.coins", vdp.getCoins()));
        return new ItemBuilder(current.getIcon())
            .name(m("trap.active-title", m(current.getNameKey())))
            .lore(lore)
            .build();
    }

    private org.bukkit.inventory.ItemStack buildTrapItem(Player player, TrapType type, TrapSlot trapSlot, VDPlayer vdp) {
        boolean isActive  = trapSlot.getPlacedTrap() == type;
        int cost = plugin.getKitAbilityManager() != null
            ? plugin.getKitAbilityManager().applyStructureDiscount(player, type.getCost())
            : type.getCost();
        boolean canAfford = vdp.getCoins() >= cost;

        List<String> lore = new ArrayList<>();
        lore.add(m(type.getDescKey()));
        lore.add("");
        lore.add(m("trap.cost", canAfford ? "&6" : "&c", cost));
        if (type.isOneUse()) lore.add(m("trap.one-use"));
        else                  lore.add(m("trap.cooldown-info", type.getCooldownMs() / 1000));
        lore.add("");
        if (isActive) {
            lore.add(m("trap.already-placed"));
        } else {
            lore.add(canAfford ? m("trap.click-place") : m("trap.no-coins"));
        }

        return new ItemBuilder(type.getIcon())
            .name((isActive ? "&a" : canAfford ? "&e" : "&c") + m(type.getNameKey())
                + (isActive ? " &a✓" : ""))
            .lore(lore)
            .build();
    }
}
