package fr.obsidianlabs.plugins.villagedefense.gui;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaSetup;
import fr.obsidianlabs.plugins.villagedefense.gameplay.model.*;
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
import java.util.Map;

/**
 * Liste et éditeur des WaveOverride.
 * Avancé / optionnel — présenté comme tel.
 *
 * Layout 54 slots : overrides en rangées 0-4, contrôles en rangée 5.
 */
public class WaveOverridesGUI {

    public static final String TITLE = "gui.gameplay.wave-overrides.title";

    private static final int SLOT_CREATE = 49;
    private static final int SLOT_BACK   = 45;

    private final VillageDefensePlugin plugin;

    public WaveOverridesGUI(VillageDefensePlugin plugin) {
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

        ArenaGameplaySettings settings = plugin.getArenaGameplayService().getSettings(arena.getId());
        Map<Integer, WaveOverride> overrides = settings.getWaveOverrides();

        Inventory inv = Bukkit.createInventory(null, 54, m(TITLE));
        for (int i = 0; i < 54; i++) inv.setItem(i, ItemBuilder.separator());
        for (int i = 45; i < 54; i++) inv.setItem(i, ItemBuilder.separator(Material.BLACK_STAINED_GLASS_PANE));

        int slot = 0;
        for (WaveOverride o : overrides.values()) {
            if (slot >= 45) break;
            inv.setItem(slot, buildOverrideItem(o));
            slot++;
        }

        inv.setItem(SLOT_CREATE, new ItemBuilder(Material.WRITABLE_BOOK)
            .name(m("gui.gameplay.wave-overrides.create"))
            .lore(m("gui.gameplay.wave-overrides.create-lore1"),
                  m("gui.gameplay.wave-overrides.create-lore2"),
                  m("gui.gameplay.wave-overrides.create-lore3"),
                  "", m("gui.gameplay.wave-overrides.create-click"))
            .build());

        inv.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW).name(m("admin.common.back")).build());

        admin.openInventory(inv);
    }

    private ItemStack buildOverrideItem(WaveOverride o) {
        List<String> lore = new ArrayList<>();
        lore.add(m("gui.gameplay.wave-overrides.status", m(o.isEnabled() ? "gui.gameplay.common.enabled-state" : "gui.gameplay.common.disabled-state")));
        if (o.getForcedCategory() != null)       lore.add(m("gui.gameplay.wave-overrides.category", o.getForcedCategory()));
        if (o.getForcedPoolId() != null)          lore.add(m("gui.gameplay.wave-overrides.pool", o.getForcedPoolId()));
        if (o.getForcedCompositionMode() != null) lore.add(m("gui.gameplay.wave-overrides.mode", o.getForcedCompositionMode()));
        if (o.getHealthMultiplierOverride() != null)
            lore.add(m("gui.gameplay.wave-overrides.health", String.format("%.2f", o.getHealthMultiplierOverride())));
        if (o.getDamageMultiplierOverride() != null)
            lore.add(m("gui.gameplay.wave-overrides.damage", String.format("%.2f", o.getDamageMultiplierOverride())));
        if (o.getSpeedMultiplierOverride() != null)
            lore.add(m("gui.gameplay.wave-overrides.speed", String.format("%.2f", o.getSpeedMultiplierOverride())));
        lore.add("");
        lore.add(m("gui.gameplay.wave-overrides.click-edit"));
        lore.add(m("gui.gameplay.wave-overrides.click-toggle"));
        lore.add(m("gui.gameplay.wave-overrides.click-delete"));

        return new ItemBuilder(o.isEnabled() ? Material.REPEATER : Material.COMPARATOR)
            .name(m("gui.gameplay.wave-overrides.wave-title", o.getWaveIndex()))
            .lore(lore)
            .build();
    }

    public void handleClick(InventoryClickEvent event, Player admin) {
        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;
        if (event.getCurrentItem().getType() == Material.GRAY_STAINED_GLASS_PANE
                || event.getCurrentItem().getType() == Material.BLACK_STAINED_GLASS_PANE) return;

        ArenaSetup setup = plugin.getArenaManager().getSetup(admin);
        if (setup == null) return;
        Arena arena = setup.getArena();

        int slot = event.getRawSlot();

        if (slot == SLOT_BACK) {
            plugin.getGameplayMainGui().open(admin, arena);
            return;
        }

        if (slot == SLOT_CREATE) {
            admin.closeInventory();
            plugin.getChatInputManager().prompt(admin, m("gui.gameplay.wave-overrides.wave-number-prompt"), input -> {
                try {
                    int waveIndex = Integer.parseInt(input.trim());
                    if (waveIndex < 1) throw new NumberFormatException();

                    ArenaGameplaySettings settings = plugin.getArenaGameplayService().getSettings(arena.getId());
                    if (settings.getWaveOverrides().containsKey(waveIndex)) {
                        ChatUtil.send(admin, m("gui.gameplay.wave-overrides.already-exists", waveIndex));
                        open(admin, arena);
                        return;
                    }

                    WaveOverride o = new WaveOverride(waveIndex);
                    settings.getWaveOverrides().put(waveIndex, o);
                    plugin.getArenaGameplayService().saveSettings(arena.getId());
                    ChatUtil.send(admin, m("gui.gameplay.wave-overrides.created", waveIndex));
                    openEditor(admin, arena, waveIndex);
                } catch (NumberFormatException e) {
                    ChatUtil.send(admin, m("gui.gameplay.wave-overrides.invalid-wave-number"));
                    open(admin, arena);
                }
            });
            return;
        }

        ArenaGameplaySettings settings = plugin.getArenaGameplayService().getSettings(arena.getId());
        List<Integer> keys = new ArrayList<>(settings.getWaveOverrides().keySet());
        if (slot < 0 || slot >= keys.size()) return;

        int waveIndex = keys.get(slot);
        WaveOverride o = settings.getWaveOverrides().get(waveIndex);
        if (o == null) return;

        ClickType click = event.getClick();
        if (click.isShiftClick() && click.isRightClick()) {
            settings.getWaveOverrides().remove(waveIndex);
            plugin.getArenaGameplayService().saveSettings(arena.getId());
            ChatUtil.send(admin, m("gui.gameplay.wave-overrides.deleted", waveIndex));
            open(admin, arena);
        } else if (click.isRightClick()) {
            o.setEnabled(!o.isEnabled());
            plugin.getArenaGameplayService().saveSettings(arena.getId());
            open(admin, arena);
        } else {
            openEditor(admin, arena, waveIndex);
        }
    }

    /** Ouvre un éditeur inline par saisie chat pour l'override. */
    private void openEditor(Player admin, Arena arena, int waveIndex) {
        ArenaGameplaySettings settings = plugin.getArenaGameplayService().getSettings(arena.getId());
        WaveOverride o = settings.getWaveOverrides().get(waveIndex);
        if (o == null) return;

        admin.closeInventory();
        ChatUtil.send(admin, m("gui.gameplay.wave-overrides.editor-title", waveIndex));
        ChatUtil.send(admin, m("gui.gameplay.wave-overrides.editor-format"));
        ChatUtil.send(admin, m("gui.gameplay.wave-overrides.editor-example"));
        ChatUtil.send(admin, m("gui.gameplay.wave-overrides.editor-values"));

        plugin.getChatInputManager().prompt(admin, m("gui.gameplay.wave-overrides.editor-prompt"), input -> {
            String[] parts = input.split(",");
            if (parts.length != 6) {
                ChatUtil.send(admin, m("gui.gameplay.wave-overrides.invalid-format"));
                open(admin, arena);
                return;
            }

            String cat    = parts[0].trim();
            String pool   = parts[1].trim();
            String mode   = parts[2].trim();
            String health = parts[3].trim();
            String damage = parts[4].trim();
            String speed  = parts[5].trim();

            if (!cat.equals("-")) {
                try { o.setForcedCategory(WaveCategory.valueOf(cat.toUpperCase())); }
                catch (IllegalArgumentException e) { ChatUtil.send(admin, m("gui.gameplay.wave-overrides.invalid-category", cat)); }
            }
            if (!pool.equals("-"))   o.setForcedPoolId(pool);
            if (!mode.equals("-")) {
                try { o.setForcedCompositionMode(CompositionMode.valueOf(mode.toUpperCase())); }
                catch (IllegalArgumentException e) { ChatUtil.send(admin, m("gui.gameplay.wave-overrides.invalid-mode", mode)); }
            }
            if (!health.equals("-")) { try { o.setHealthMultiplierOverride(Double.parseDouble(health)); } catch (NumberFormatException ignored) {} }
            if (!damage.equals("-")) { try { o.setDamageMultiplierOverride(Double.parseDouble(damage)); } catch (NumberFormatException ignored) {} }
            if (!speed.equals("-"))  { try { o.setSpeedMultiplierOverride(Double.parseDouble(speed));  } catch (NumberFormatException ignored) {} }

            plugin.getArenaGameplayService().saveSettings(arena.getId());
            ChatUtil.send(admin, m("gui.gameplay.wave-overrides.updated", waveIndex));
            open(admin, arena);
        });
    }
}
