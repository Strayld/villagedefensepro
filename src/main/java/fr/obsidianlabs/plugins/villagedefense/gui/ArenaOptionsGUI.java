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

/**
 * Sous-GUI « Options » — prix, configs tours, schématics, panneaux, leaderboard, limite de vague, limite de tentatives.
 *
 * Layout 36 slots :
 *  Rangée 1 : [sep][SHOP_PRICES][ENCHANT_PRICES][TOWER_CONFIG][SCHEMATICS][SIGN][LB_GLOBAL][LB_ARENA][sep]
 *  Rangée 2 : [sep][sep][WAVE_LIMIT][ATTEMPT_LIMIT][sep][sep][sep][sep][sep]
 *  Rangée 3 : [sep][sep][sep][sep][BACK][sep][sep][sep][sep]
 */
public class ArenaOptionsGUI {

    public static final String TITLE = "gui.arena-options.title";

    private static final int SLOT_SHOP_PRICES      = 10;
    private static final int SLOT_ENCHANT_PRICES   = 11;
    private static final int SLOT_TOWER_CONFIG     = 12;
    private static final int SLOT_SCHEMATICS       = 13;
    private static final int SLOT_SIGN             = 14;
    private static final int SLOT_LEADERBOARD      = 15;
    private static final int SLOT_LEADERBOARD_ARENA = 16;
    private static final int SLOT_WAVE_LIMIT        = 20;
    private static final int SLOT_ATTEMPT_LIMIT     = 21;
    private static final int SLOT_BACK             = 31;

    private final VillageDefensePlugin plugin;

    public ArenaOptionsGUI(VillageDefensePlugin plugin) {
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
        Inventory inv = Bukkit.createInventory(null, 36, m(TITLE));
        for (int i = 0; i < 36; i++) inv.setItem(i, ItemBuilder.separator());

        inv.setItem(SLOT_SHOP_PRICES,       buildShopPricesItem(arena));
        inv.setItem(SLOT_ENCHANT_PRICES,    buildEnchantPricesItem(arena));
        inv.setItem(SLOT_TOWER_CONFIG,      buildTowerConfigItem(arena));
        inv.setItem(SLOT_SCHEMATICS,        buildSchematicsItem());
        inv.setItem(SLOT_SIGN,              buildSignItem(arena));
        inv.setItem(SLOT_LEADERBOARD,       buildLeaderboardItem(arena));
        inv.setItem(SLOT_LEADERBOARD_ARENA, buildArenaLeaderboardItem(arena));
        inv.setItem(SLOT_WAVE_LIMIT,        buildWaveLimitItem(arena));
        inv.setItem(SLOT_ATTEMPT_LIMIT,     buildAttemptLimitItem(arena));
        inv.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW)
            .name(m("admin.common.back")).build());

        admin.openInventory(inv);
    }

    // ── Builders ─────────────────────────────────────────────────────────────

    private ItemStack buildShopPricesItem(Arena arena) {
        boolean chestSet = arena.getShopChestLocation() != null;
        return new ItemBuilder(Material.GOLD_NUGGET)
            .name(m("admin.setup.shop-prices-item"))
            .lore(chestSet ? m("admin.setup.shop-prices-lore-set")
                           : m("admin.setup.shop-prices-lore-no-chest"),
                  "", m("admin.common.click-to-edit"))
            .build();
    }

    private ItemStack buildEnchantPricesItem(Arena arena) {
        long customCount = arena.getEnchantPrices().size();
        return new ItemBuilder(Material.EXPERIENCE_BOTTLE)
            .name(m("admin.enchant-prices.item"))
            .lore(customCount > 0
                ? m("admin.enchant-prices.item-lore-custom", customCount)
                : m("admin.enchant-prices.item-lore-default"),
                  "", m("admin.common.click-to-edit"))
            .build();
    }

    private ItemStack buildTowerConfigItem(Arena arena) {
        long disabled = arena.getDisabledTowers().size();
        long custom   = arena.getTowerCostOverrides().size() + arena.getTowerLevelRequiredMap().size();
        return new ItemBuilder(Material.IRON_BLOCK)
            .name(m("admin.tower-config.item"))
            .lore(disabled > 0 ? m("admin.tower-config.item-lore-disabled", disabled)
                               : m("admin.tower-config.item-lore-all-enabled"),
                  custom > 0   ? m("admin.tower-config.item-lore-custom", custom)
                               : m("admin.tower-config.item-lore-default"),
                  "", m("admin.common.click-to-edit"))
            .build();
    }

    private ItemStack buildSchematicsItem() {
        return new ItemBuilder(Material.AMETHYST_SHARD)
            .name(m("admin.setup.schematics-item"))
            .lore(m("admin.setup.schematics-item-lore1"), m("admin.setup.schematics-item-lore2"),
                  "", m("admin.setup.schematics-item-lore3"), m("admin.setup.schematics-item-lore4"),
                  "", m("admin.setup.schematics-item-click"))
            .build();
    }

    private ItemStack buildSignItem(Arena arena) {
        boolean set = arena.getSignLocation() != null;
        return new ItemBuilder(Material.OAK_SIGN)
            .name(m("admin.setup.sign-item"))
            .lore(set ? m("admin.setup.sign-item-set", formatLoc(arena.getSignLocation()))
                      : m("admin.setup.sign-item-unset"),
                  "", m("admin.setup.sign-item-lore1"), m("admin.setup.sign-item-lore2"),
                  "", m("admin.setup.sign-item-click"), m("admin.setup.sign-item-click2"))
            .build();
    }

    private ItemStack buildLeaderboardItem(Arena arena) {
        boolean set = arena.getLeaderboardLocation() != null;
        return new ItemBuilder(Material.ARMOR_STAND)
            .name(m("admin.setup.leaderboard-item") + (set ? " &a✓" : " &8•"))
            .lore(m("admin.setup.leaderboard-global-lore"),
                  set ? m("admin.setup.leaderboard-set", formatLoc(arena.getLeaderboardLocation()))
                      : m("admin.setup.leaderboard-unset"),
                  "", m("admin.setup.leaderboard-lore1"), m("admin.setup.leaderboard-lore2"),
                  "", m("admin.setup.leaderboard-click"), m("admin.setup.leaderboard-shift"))
            .build();
    }

    private ItemStack buildArenaLeaderboardItem(Arena arena) {
        boolean set = arena.getArenaLeaderboardLocation() != null;
        return new ItemBuilder(Material.PLAYER_HEAD)
            .name(m("admin.setup.leaderboard-arena-item") + (set ? " &a✓" : " &8•"))
            .lore(m("admin.setup.leaderboard-arena-lore"),
                  set ? m("admin.setup.leaderboard-set", formatLoc(arena.getArenaLeaderboardLocation()))
                      : m("admin.setup.leaderboard-unset"),
                  "", m("admin.setup.leaderboard-lore1"), m("admin.setup.leaderboard-lore2"),
                  "", m("admin.setup.leaderboard-click"), m("admin.setup.leaderboard-shift"))
            .build();
    }

    private ItemStack buildWaveLimitItem(Arena arena) {
        boolean active = arena.hasWaveLimit();
        return new ItemBuilder(active ? Material.CLOCK : Material.COMPASS)
            .name(m("admin.options.wave-limit-item") + (active ? " &a✓" : " &8•"))
            .lore(active ? m("admin.options.wave-limit-current", arena.getWaveLimit())
                         : m("admin.options.wave-limit-disabled"),
                  "", m("admin.setup.status-optional"),
                  "", m("admin.options.wave-limit-click"),
                  active ? m("admin.options.wave-limit-shift-disable") : "")
            .build();
    }

    private ItemStack buildAttemptLimitItem(Arena arena) {
        boolean active = arena.hasAttemptLimit();
        return new ItemBuilder(active ? Material.HOPPER : Material.BARREL)
            .name(m("admin.options.attempt-limit-item") + (active ? " &a✓" : " &8•"))
            .lore(active ? m("admin.options.attempt-limit-current", arena.getAttemptLimit(), arena.getAttemptWindowHours())
                         : m("admin.options.attempt-limit-disabled"),
                  "", m("admin.setup.status-optional"),
                  "", m("admin.options.attempt-limit-click"),
                  active ? m("admin.options.attempt-limit-shift-window", arena.getAttemptWindowHours()) : "",
                  active ? m("admin.options.attempt-limit-shift-disable") : "")
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

        switch (event.getRawSlot()) {
            case SLOT_SHOP_PRICES -> plugin.getShopPricesGui().open(admin, arena);

            case SLOT_ENCHANT_PRICES -> plugin.getEnchantPricesGui().open(admin, arena);

            case SLOT_TOWER_CONFIG -> plugin.getTowerConfigGui().openMain(admin, arena);

            case SLOT_SCHEMATICS -> plugin.getSchematicCategoryGui().open(admin, arena);

            case SLOT_SIGN -> {
                setup.setMode(ArenaSetup.WandMode.SIGN);
                admin.closeInventory();
                ChatUtil.send(admin, m("admin.setup.sign-mode"));
            }

            case SLOT_LEADERBOARD -> {
                if (click.isShiftClick()) {
                    arena.setLeaderboardLocation(null);
                    ChatUtil.send(admin, m("admin.setup.leaderboard-cleared"));
                } else {
                    org.bukkit.Location anchor = leaderboardAnchor(admin.getLocation());
                    arena.setLeaderboardLocation(anchor);
                    ChatUtil.send(admin, m("admin.setup.leaderboard-set-msg", formatLoc(anchor)));
                }
                if (plugin.getLeaderboardDisplayManager() != null) plugin.getLeaderboardDisplayManager().refreshNow();
                open(admin, arena);
            }

            case SLOT_LEADERBOARD_ARENA -> {
                if (click.isShiftClick()) {
                    arena.setArenaLeaderboardLocation(null);
                    ChatUtil.send(admin, m("admin.setup.leaderboard-cleared"));
                } else {
                    org.bukkit.Location anchor = leaderboardAnchor(admin.getLocation());
                    arena.setArenaLeaderboardLocation(anchor);
                    ChatUtil.send(admin, m("admin.setup.leaderboard-set-msg", formatLoc(anchor)));
                }
                if (plugin.getLeaderboardDisplayManager() != null) plugin.getLeaderboardDisplayManager().refreshNow();
                open(admin, arena);
            }

            case SLOT_WAVE_LIMIT -> {
                if (click.isShiftClick() && arena.hasWaveLimit()) {
                    arena.setWaveLimit(0);
                    ChatUtil.send(admin, m("admin.options.wave-limit-set-disabled"));
                    open(admin, arena);
                } else if (!click.isShiftClick()) {
                    admin.closeInventory();
                    plugin.getChatInputManager().prompt(admin, m("admin.options.wave-limit-prompt"), input -> {
                        try {
                            int limit = Integer.parseInt(input.trim());
                            if (limit <= 0) throw new NumberFormatException();
                            arena.setWaveLimit(limit);
                            ChatUtil.send(admin, m("admin.options.wave-limit-set", limit));
                        } catch (NumberFormatException e) {
                            ChatUtil.send(admin, m("admin.options.wave-limit-invalid"));
                        }
                        open(admin, arena);
                    });
                }
            }

            case SLOT_ATTEMPT_LIMIT -> {
                if (click.isShiftClick() && arena.hasAttemptLimit()) {
                    // Shift+clic gauche → désactiver
                    // Shift+clic droit → changer la fenêtre
                    if (click == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT) {
                        admin.closeInventory();
                        plugin.getChatInputManager().prompt(admin, m("admin.options.attempt-limit-window-prompt"), input -> {
                            try {
                                int hours = Integer.parseInt(input.trim());
                                if (hours <= 0) throw new NumberFormatException();
                                arena.setAttemptWindowHours(hours);
                                ChatUtil.send(admin, m("admin.options.attempt-limit-window-set", hours));
                            } catch (NumberFormatException e) {
                                ChatUtil.send(admin, m("admin.options.attempt-limit-invalid"));
                            }
                            open(admin, arena);
                        });
                    } else {
                        arena.setAttemptLimit(0);
                        ChatUtil.send(admin, m("admin.options.attempt-limit-set-disabled"));
                        open(admin, arena);
                    }
                } else if (!click.isShiftClick()) {
                    admin.closeInventory();
                    plugin.getChatInputManager().prompt(admin, m("admin.options.attempt-limit-prompt"), input -> {
                        try {
                            int limit = Integer.parseInt(input.trim());
                            if (limit <= 0) throw new NumberFormatException();
                            arena.setAttemptLimit(limit);
                            ChatUtil.send(admin, m("admin.options.attempt-limit-set", limit, arena.getAttemptWindowHours()));
                        } catch (NumberFormatException e) {
                            ChatUtil.send(admin, m("admin.options.attempt-limit-invalid"));
                        }
                        open(admin, arena);
                    });
                }
            }

            case SLOT_BACK -> plugin.getArenaSetupGui().open(admin, arena);
        }
    }

    private org.bukkit.Location leaderboardAnchor(org.bukkit.Location source) {
        return source.getBlock().getLocation().add(0.5D, 2.4D, 0.5D);
    }

    private String formatLoc(org.bukkit.Location loc) {
        if (loc == null) return "?";
        return String.format("%.0f,%.0f,%.0f", loc.getX(), loc.getY(), loc.getZ());
    }
}
