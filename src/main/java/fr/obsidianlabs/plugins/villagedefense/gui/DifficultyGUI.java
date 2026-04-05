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

import java.util.ArrayList;
import java.util.List;

/**
 * Gestion des difficultés : activer/désactiver, choisir la default, éditer les multiplicateurs.
 *
 * Layout 36 slots :
 *  Rangée 1 : [.][EASY][NORMAL][HARD][CUSTOM][.][.][.][.]
 *  Rangée 3 : [.][BACK][.][.][.][.][.][.][.]
 */
public class DifficultyGUI {

    public static final String TITLE = "gui.gameplay.difficulty.title";

    private static final int SLOT_EASY   = 10;
    private static final int SLOT_NORMAL = 11;
    private static final int SLOT_HARD   = 12;
    private static final int SLOT_CUSTOM = 13;
    private static final int SLOT_BACK   = 28;

    private final VillageDefensePlugin plugin;

    public DifficultyGUI(VillageDefensePlugin plugin) {
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

        DifficultySettings settings = plugin.getArenaGameplayService().getSettings(arena.getId()).getDifficultySettings();

        Inventory inv = Bukkit.createInventory(null, 36, m(TITLE));
        for (int i = 0; i < 36; i++) inv.setItem(i, ItemBuilder.separator());

        inv.setItem(SLOT_EASY,   buildDifficultyItem(GameDifficulty.EASY,   settings, Material.LIME_DYE));
        inv.setItem(SLOT_NORMAL, buildDifficultyItem(GameDifficulty.NORMAL, settings, Material.YELLOW_DYE));
        inv.setItem(SLOT_HARD,   buildDifficultyItem(GameDifficulty.HARD,   settings, Material.RED_DYE));
        inv.setItem(SLOT_CUSTOM, buildDifficultyItem(GameDifficulty.CUSTOM, settings, Material.PURPLE_DYE));

        inv.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW).name(m("admin.common.back")).build());

        admin.openInventory(inv);
    }

    private org.bukkit.inventory.ItemStack buildDifficultyItem(GameDifficulty diff, DifficultySettings settings, Material material) {
        boolean enabled = settings.getEnabledDifficulties().contains(diff);
        boolean isDefault = settings.getDefaultDifficulty() == diff;
        DifficultyProfile profile = settings.getProfile(diff);

        List<String> lore = new ArrayList<>();
        lore.add("&7Statut: " + (enabled ? "&aActivée" : "&cDésactivée"));
        lore.add("&7Défaut: " + (isDefault ? "&aOui" : "&7Non"));
        lore.add("");
        lore.add("&7❤ Santé ×" + String.format("%.2f", profile.getHealthMultiplier()));
        lore.add("&7⚔ Dégâts ×" + String.format("%.2f", profile.getDamageMultiplier()));
        lore.add("&7⚡ Vitesse ×" + String.format("%.2f", profile.getSpeedMultiplier()));
        lore.add("&7💰 Récompenses ×" + String.format("%.2f", profile.getRewardMultiplier()));
        lore.add("");
        lore.add("&eClique gauche &7→ activer/désactiver");
        lore.add("&eClique droit &7→ définir comme défaut");
        lore.add("&eShift+clique &7→ éditer les multiplicateurs");

        String status = isDefault ? " &6[Défaut]" : (enabled ? " &a[Activée]" : " &c[Désactivée]");
        return new ItemBuilder(enabled ? material : Material.GRAY_DYE)
            .name("&f" + diff.name() + status)
            .lore(lore)
            .build();
    }

    public void handleClick(InventoryClickEvent event, Player admin) {
        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;
        if (event.getCurrentItem().getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        ArenaSetup setup = plugin.getArenaManager().getSetup(admin);
        if (setup == null) return;
        Arena arena = setup.getArena();

        ArenaGameplaySettings settings = plugin.getArenaGameplayService().getSettings(arena.getId());
        DifficultySettings diff = settings.getDifficultySettings();

        GameDifficulty target = switch (event.getRawSlot()) {
            case SLOT_EASY   -> GameDifficulty.EASY;
            case SLOT_NORMAL -> GameDifficulty.NORMAL;
            case SLOT_HARD   -> GameDifficulty.HARD;
            case SLOT_CUSTOM -> GameDifficulty.CUSTOM;
            case SLOT_BACK   -> { plugin.getGameplayMainGui().open(admin, arena); yield null; }
            default          -> null;
        };
        if (target == null) return;

        ClickType click = event.getClick();

        if (click.isShiftClick()) {
            // Éditer les multiplicateurs via saisie chat
            DifficultyProfile profile = diff.getProfile(target);
            admin.closeInventory();
            ChatUtil.send(admin, "&eEditing &b" + target.name() + "&e — Entrez: &fhealth,damage,speed,reward &e(ex: &f1.2,1.15,1.05,1.25&e)");
            plugin.getChatInputManager().prompt(admin, "&eValeurs (health,damage,speed,reward):", input -> {
                String[] parts = input.split(",");
                if (parts.length == 4) {
                    try {
                        profile.setHealthMultiplier(Double.parseDouble(parts[0].trim()));
                        profile.setDamageMultiplier(Double.parseDouble(parts[1].trim()));
                        profile.setSpeedMultiplier(Double.parseDouble(parts[2].trim()));
                        profile.setRewardMultiplier(Double.parseDouble(parts[3].trim()));
                        plugin.getArenaGameplayService().saveSettings(arena.getId());
                        ChatUtil.send(admin, "&aProfil &b" + target.name() + " &amis à jour.");
                    } catch (NumberFormatException e) {
                        ChatUtil.send(admin, "&cFormat invalide. Ex: 1.2,1.15,1.05,1.25");
                    }
                } else {
                    ChatUtil.send(admin, "&cIl faut exactement 4 valeurs séparées par des virgules.");
                }
                open(admin, arena);
            });
        } else if (click.isRightClick()) {
            // Définir comme défaut
            if (!diff.getEnabledDifficulties().contains(target)) {
                diff.getEnabledDifficulties().add(target);
            }
            diff.setDefaultDifficulty(target);
            plugin.getArenaGameplayService().saveSettings(arena.getId());
            open(admin, arena);
        } else {
            // Activer / désactiver
            List<GameDifficulty> enabled = diff.getEnabledDifficulties();
            if (enabled.contains(target)) {
                if (diff.getDefaultDifficulty() == target && enabled.size() == 1) {
                    ChatUtil.send(admin, "&cImpossible de désactiver la seule difficulté active.");
                    return;
                }
                enabled.remove(target);
                if (diff.getDefaultDifficulty() == target) {
                    diff.setDefaultDifficulty(enabled.get(0));
                }
            } else {
                enabled.add(target);
            }
            plugin.getArenaGameplayService().saveSettings(arena.getId());
            open(admin, arena);
        }
    }
}
