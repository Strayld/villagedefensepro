package fr.obsidianlabs.plugins.villagedefense.gui;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaAvailability;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaSetup;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaState;
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
 * GUI principal de configuration d'une arène.
 *
 * Layout 54 slots (6 rangées × 9 colonnes) :
 *
 * Rangée 0 : [.][ICN][.][.][.][STA][.][.][TPC]
 * Rangée 1 : [.][.][.][.][MIN][NOM][MAX][.][.]
 * Rangée 2 : séparateur gris
 * Rangée 3 : [.][.][POS][.][.][GAM][.][.][OPT]
 * Rangée 4 : séparateur noir
 * Rangée 5 : [.][BCK][.][.][DEL][AVA][SAV][.][.]
 */
public class ArenaSetupGUI {

    public static final String TITLE = "gui.arena-setup.title";

    // ── Rangée 0 ────────────────────────────────────────────────────────────
    private static final int SLOT_ICON = 0;
    private static final int SLOT_STATUS = 4;
    private static final int SLOT_TP_CENTER = 8;

    // ── Rangée 1 ────────────────────────────────────────────────────────────
    private static final int SLOT_MIN = 12;
    private static final int SLOT_NAME = 13;
    private static final int SLOT_MAX = 14;

    // ── Rangée 3 ────────────────────────────────────────────────────────────
    private static final int SLOT_POSITIONS        = 28;
    private static final int SLOT_GAMEPLAY         = 30;  // Gameplay Locations (spawns, tours…)
    private static final int SLOT_GAMEPLAY_SYSTEM  = 31;  // Nouveau : système Gameplay avancé
    private static final int SLOT_REWARDS          = 32;
    private static final int SLOT_OPTIONS          = 34;

    // ── Rangée 5 ────────────────────────────────────────────────────────────
    private static final int SLOT_BACK = 45;
    private static final int SLOT_DELETE = 48;
    private static final int SLOT_AVAILABILITY = 49;
    private static final int SLOT_SAVE = 50;

    private final VillageDefensePlugin plugin;

    public ArenaSetupGUI(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    private String m(String key, Object... args) {
        return plugin.getMessages().format(key, args);
    }

    // ── Ouverture ─────────────────────────────────────────────────────────────

    public void open(Player admin, Arena arena) {
        if (!plugin.hasPermission(admin, Perm.ADMIN_SETUP)) {
            ChatUtil.send(admin, m("general.no-permission"));
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 54, m(TITLE));

        // Fond gris
        for (int i = 0; i < 54; i++)
            inv.setItem(i, ItemBuilder.separator());
        // Séparateur noir rangée 4
        for (int i = 36; i < 45; i++)
            inv.setItem(i, ItemBuilder.separator(Material.BLACK_STAINED_GLASS_PANE));

        // Rangée 0 — Identité & TP
        inv.setItem(SLOT_ICON, buildIconItem(arena));
        inv.setItem(SLOT_STATUS, buildStatusItem(arena));
        inv.setItem(SLOT_TP_CENTER, buildTpCenterItem(arena));

        // Rangée 1 — Joueurs & Nom
        inv.setItem(SLOT_MIN, buildMinPlayersItem(arena));
        inv.setItem(SLOT_NAME, buildNameItem(arena));
        inv.setItem(SLOT_MAX, buildMaxPlayersItem(arena));

        // Rangée 3 — Sous-GUIs
        inv.setItem(SLOT_POSITIONS, buildPositionsButton(arena));
        inv.setItem(SLOT_REWARDS, buildRewardsButton(arena));
        inv.setItem(SLOT_GAMEPLAY, buildGameplayButton(arena));
        inv.setItem(SLOT_GAMEPLAY_SYSTEM, buildGameplaySystemButton(arena));
        inv.setItem(SLOT_OPTIONS, buildOptionsButton(arena));

        // Rangée 5 — Actions
        inv.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW)
                .name(m("admin.common.back")).build());
        inv.setItem(SLOT_DELETE, new ItemBuilder(Material.TNT)
                .name(m("admin.setup.delete-arena"))
                .lore(m("admin.setup.delete-arena-lore"), "", m("admin.setup.delete-arena-shift"))
                .build());
        inv.setItem(SLOT_AVAILABILITY, buildAvailabilityItem(arena));
        inv.setItem(SLOT_SAVE, new ItemBuilder(Material.EMERALD)
                .name(m("admin.setup.save-arena"))
                .lore(m("admin.setup.save-arena-lore"), "", m("admin.common.click-to-edit"))
                .build());

        admin.openInventory(inv);
    }

    // ── Builders ─────────────────────────────────────────────────────────────

    private ItemStack buildIconItem(Arena arena) {
        Material current = arena.getCustomIcon() != null ? arena.getCustomIcon() : Material.ITEM_FRAME;
        List<String> lore = new ArrayList<>();
        lore.add(arena.getCustomIcon() != null ? m("admin.setup.icon-item-current")
                : m("admin.setup.icon-item-default"));
        lore.add("");
        lore.add(m("admin.setup.icon-item-click"));
        return new ItemBuilder(current).name(m("admin.setup.icon-item")).lore(lore).build();
    }

    private ItemStack buildStatusItem(Arena arena) {
        boolean spawnOk = arena.hasLobbySpawn();
        boolean centerOk = arena.hasVillageCenter();
        boolean boundsOk = arena.hasBounds();
        boolean mobSpawnsOk = arena.hasMobSpawns();
        boolean towersOk = arena.hasTowerSlots();
        boolean shopVillagerOk = arena.hasShopVillager();
        boolean enchantVillagerOk = arena.hasEnchantVillager();
        boolean gameSpawnOk = arena.hasGameSpawn();
        boolean villagerSpawnsOk = arena.hasVillagerSpawns();
        boolean shopOk = arena.hasShopChest();
        boolean allDone = arena.isSetup();

        String opt = " " + m("admin.setup.status-optional");
        List<String> lore = new ArrayList<>();
        lore.add(m("admin.setup.status-required-header"));
        lore.add(checkLine(m("admin.setup.status-spawn"), spawnOk));
        lore.add(checkLine(m("admin.setup.status-game-spawn"), gameSpawnOk));
        lore.add(checkLine(m("admin.setup.status-center"), centerOk));
        lore.add(checkLine(m("admin.setup.status-bounds"), boundsOk));
        lore.add(checkLine(m("admin.setup.status-mob-spawns"), mobSpawnsOk));
        lore.add(checkLine(m("admin.setup.status-villager-spawns"), villagerSpawnsOk));
        lore.add(checkLine(m("admin.setup.status-towers"), towersOk));
        lore.add(checkLine(m("admin.setup.status-shop"), shopOk));
        lore.add(checkLine(m("admin.setup.status-shop-villager"), shopVillagerOk));
        lore.add(checkLine(m("admin.setup.status-enchant-villager"), enchantVillagerOk));
        lore.add("");
        lore.add(m("admin.setup.status-optional-header"));
        lore.add(checkLine(m("admin.setup.status-name"), arena.hasCustomDisplayName()) + opt);
        lore.add(checkLine(m("admin.setup.status-sign"), arena.hasJoinSign()) + opt);
        lore.add(checkLine(m("admin.setup.status-leaderboard"), arena.hasLeaderboardDisplay()) + opt);
        lore.add(checkLine(m("admin.setup.status-traps"), arena.hasTrapSlots()) + opt);
        lore.add(checkLine(m("admin.setup.status-paths"), arena.hasPaths()) + opt);
        lore.add(checkLine(m("admin.setup.status-wave-limit"), arena.hasWaveLimit()) + opt);
        lore.add("");
        lore.add(m("admin.setup.status-availability", m(getAvailabilityKey(arena.getAvailability()))));
        lore.add("");
        lore.add(allDone ? m("admin.setup.status-ok") : m("admin.setup.status-missing"));

        return new ItemBuilder(allDone ? Material.LIME_DYE : Material.ORANGE_DYE)
                .name(allDone ? m("admin.setup.status-ready") : m("admin.setup.status-incomplete"))
                .lore(lore).build();
    }

    private ItemStack buildTpCenterItem(Arena arena) {
        boolean set = arena.getVillageCenter() != null;
        return new ItemBuilder(Material.ENDER_PEARL)
                .name(m("admin.setup.tp-center"))
                .lore(set ? m("admin.setup.tp-center-set", formatLoc(arena.getVillageCenter()))
                        : m("admin.setup.tp-center-unset"),
                        "", set ? m("admin.setup.tp-center-click") : m("admin.setup.tp-center-no-center"))
                .build();
    }

    private ItemStack buildNameItem(Arena arena) {
        return new ItemBuilder(Material.NAME_TAG)
                .name(m("admin.setup.name-item"))
                .lore(m("admin.setup.name-item-lore", arena.getDisplayName()),
                        "", m("admin.setup.name-item-click"))
                .build();
    }

    private ItemStack buildMinPlayersItem(Arena arena) {
        return new ItemBuilder(Material.LIME_CONCRETE)
                .name(m("admin.setup.min-players"))
                .lore(m("admin.setup.name-item-lore", arena.getMinPlayers()),
                        "", m("admin.setup.min-players-lore"))
                .build();
    }

    private ItemStack buildMaxPlayersItem(Arena arena) {
        return new ItemBuilder(Material.RED_CONCRETE)
                .name(m("admin.setup.max-players"))
                .lore(m("admin.setup.name-item-lore", arena.getMaxPlayers()),
                        "", m("admin.setup.max-players-lore"))
                .build();
    }

    private ItemStack buildRewardsButton(Arena arena) {
        boolean hasRewards = arena.hasRewards();
        return new ItemBuilder(hasRewards ? Material.DIAMOND : Material.EMERALD)
                .name(m("admin.setup.rewards"))
                .lore(m("admin.setup.rewards-lore1"),
                        m("admin.setup.rewards-lore2"),
                        "", m("admin.common.click-to-edit"))
                .build();
    }

    private ItemStack buildPositionsButton(Arena arena) {
        boolean complete = arena.hasLobbySpawn() && arena.hasBounds() && arena.hasVillageCenter()
                && arena.hasGameSpawn() && arena.hasShopChest();
        return new ItemBuilder(complete ? Material.FILLED_MAP : Material.MAP)
                .name(m("admin.setup.arena-positions"))
                .lore(m("admin.setup.arena-positions-lore1"),
                        m("admin.setup.arena-positions-lore2"),
                        "", m("admin.common.click-to-edit"))
                .build();
    }

    private ItemStack buildGameplayButton(Arena arena) {
        boolean complete = arena.hasMobSpawns() && arena.hasTowerSlots()
                && arena.hasShopVillager() && arena.hasEnchantVillager();
        return new ItemBuilder(complete ? Material.ZOMBIE_SPAWN_EGG : Material.EGG)
                .name(m("admin.setup.gameplay-locations"))
                .lore(m("admin.setup.gameplay-locations-lore1"),
                        m("admin.setup.gameplay-locations-lore2"),
                        "", m("admin.common.click-to-edit"))
                .build();
    }

    private ItemStack buildGameplaySystemButton(Arena arena) {
        return new ItemBuilder(Material.IRON_SWORD)
            .name(m("admin.setup.gameplay-system"))
            .lore(m("admin.setup.gameplay-system-lore1"),
                  m("admin.setup.gameplay-system-lore2"),
                  "", m("admin.common.click-to-edit"))
            .build();
    }

    private ItemStack buildOptionsButton(Arena arena) {
        return new ItemBuilder(Material.COMPARATOR)
                .name(m("admin.setup.options"))
                .lore(m("admin.setup.options-lore1"),
                        m("admin.setup.options-lore2"),
                        "", m("admin.common.click-to-edit"))
                .build();
    }

    private ItemStack buildAvailabilityItem(Arena arena) {
        ArenaAvailability availability = arena.getAvailability();
        Material material = switch (availability) {
            case ACTIVE -> Material.LIME_DYE;
            case TEST -> Material.CLOCK;
            case DISABLED -> Material.RED_DYE;
        };
        return new ItemBuilder(material)
                .name(m("admin.setup.availability-item"))
                .lore(m("admin.setup.availability-current", m(getAvailabilityKey(availability))),
                        "", m("admin.setup.availability-lore-active"),
                        m("admin.setup.availability-lore-test"),
                        m("admin.setup.availability-lore-disabled"),
                        "", m("admin.setup.availability-click"))
                .build();
    }

    // ── Gestion des clics ─────────────────────────────────────────────────────

    public void handleClick(InventoryClickEvent event, Player admin) {
        event.setCancelled(true);
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlot() < 0 || event.getRawSlot() >= topSize)
            return;
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR)
            return;
        if (event.getCurrentItem().getType() == Material.GRAY_STAINED_GLASS_PANE
                || event.getCurrentItem().getType() == Material.BLACK_STAINED_GLASS_PANE)
            return;

        ArenaSetup setup = plugin.getArenaManager().getSetup(admin);
        if (setup == null)
            return;
        Arena arena = setup.getArena();
        ClickType click = event.getClick();

        switch (event.getRawSlot()) {
            case SLOT_ICON -> plugin.getIconPickerGui().open(admin, arena);

            case SLOT_TP_CENTER -> {
                if (arena.getVillageCenter() == null) {
                    ChatUtil.send(admin, m("admin.setup.center-not-defined"));
                    return;
                }
                admin.closeInventory();
                admin.teleport(arena.getVillageCenter());
                ChatUtil.send(admin, m("admin.setup.teleported-center"));
            }

            case SLOT_NAME -> {
                plugin.getChatInputManager().prompt(admin, m("admin.setup.prompt-name"), name -> {
                    arena.setDisplayName(name);
                    ChatUtil.send(admin, m("admin.setup.name-set", name));
                    open(admin, arena);
                });
            }

            case SLOT_MIN -> {
                int min = arena.getMinPlayers();
                if (click.isLeftClick())
                    min = Math.max(1, min - 1);
                if (click.isRightClick())
                    min = Math.min(arena.getMaxPlayers(), min + 1);
                arena.setMinPlayers(min);
                open(admin, arena);
            }

            case SLOT_MAX -> {
                int max = arena.getMaxPlayers();
                if (click.isLeftClick())
                    max = Math.max(arena.getMinPlayers(), max - 1);
                if (click.isRightClick())
                    max = Math.min(20, max + 1);
                arena.setMaxPlayers(max);
                open(admin, arena);
            }

            case SLOT_POSITIONS -> plugin.getArenaPositionsGui().open(admin, arena);

            case SLOT_REWARDS -> plugin.getArenaRewardsGui().open(admin, arena);

            case SLOT_GAMEPLAY -> plugin.getArenaGameplayLocationsGui().open(admin, arena);

            case SLOT_GAMEPLAY_SYSTEM -> plugin.getGameplayMainGui().open(admin, arena);

            case SLOT_OPTIONS -> plugin.getArenaOptionsGui().open(admin, arena);

            case SLOT_BACK -> plugin.getVdAdminGui().open(admin);

            case SLOT_DELETE -> {
                if (!click.isShiftClick()) {
                    ChatUtil.send(admin, m("admin.setup.delete-confirm", arena.getId()));
                    return;
                }
                String arenaId = arena.getId();
                plugin.getArenaManager().endSetup(admin);
                if (ArenaSetup.isWand(plugin, admin.getInventory().getItem(8)))
                    admin.getInventory().setItem(8, null);
                admin.closeInventory();
                plugin.getArenaManager().deleteArena(arenaId);
                ChatUtil.send(admin, plugin.getMessages().get("arena.deleted", arenaId));
            }

            case SLOT_AVAILABILITY -> {
                ArenaAvailability next = arena.getAvailability().next();
                arena.setAvailability(next);
                ChatUtil.send(admin, m("admin.setup.availability-set", m(getAvailabilityKey(next))));
                open(admin, arena);
            }

            case SLOT_SAVE -> {
                if (!setup.validate())
                    return;
                plugin.getArenaManager().saveArena(arena);
                arena.setState(ArenaState.WAITING);
                plugin.getArenaManager().endSetup(admin);
                if (ArenaSetup.isWand(plugin, admin.getInventory().getItem(8))) {
                    admin.getInventory().setItem(8, null);
                }
                admin.closeInventory();
                ChatUtil.send(admin, plugin.getMessages().get("setup.saved", arena.getId()));
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String checkLine(String label, boolean done) {
        return (done ? "&a✓" : "&c✗") + " &7" + label;
    }

    private String getAvailabilityKey(ArenaAvailability availability) {
        return switch (availability) {
            case ACTIVE -> "admin.setup.availability-mode-active";
            case TEST -> "admin.setup.availability-mode-test";
            case DISABLED -> "admin.setup.availability-mode-disabled";
        };
    }

    private String formatLoc(org.bukkit.Location loc) {
        if (loc == null)
            return "?";
        return String.format("%.0f,%.0f,%.0f", loc.getX(), loc.getY(), loc.getZ());
    }
}
