package fr.obsidianlabs.plugins.villagedefense.gui;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.database.LeaderboardEntry;
import fr.obsidianlabs.plugins.villagedefense.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;

/**
 * GUI classement global — 45 slots.
 * Rangée du bas : 3 onglets (Kills | Vagues | Parties) + fermer.
 * Les 10 premiers joueurs sont affichés dans les 30 slots du haut.
 */
public class LeaderboardGUI {

    public static final String TITLE = "gui.leaderboard.title";

    // Onglets (rangée 4, slots 36-44)
    private static final int SLOT_TAB_KILLS   = 38;
    private static final int SLOT_TAB_WAVES   = 40;
    private static final int SLOT_TAB_GAMES   = 42;
    private static final int SLOT_CLOSE       = 44;

    // Les 10 entrées occupent les slots 0-9 (2 rangées de 5)
    private static final int[] ENTRY_SLOTS = {
        1, 2, 3, 4, 5,
        10, 11, 12, 13, 14
    };

    private final VillageDefensePlugin plugin;

    public LeaderboardGUI(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    private String m(String key, Object... args) {
        return plugin.getMessages().format(key, args);
    }

    // ── Ouverture ─────────────────────────────────────────────────────────────

    public void open(Player player, String category) {
        plugin.getDatabase().getTopPlayers(10, category).thenAccept(entries ->
            Bukkit.getScheduler().runTask(plugin, () -> buildAndOpen(player, category, entries))
        );
    }

    private void buildAndOpen(Player player, String category, List<LeaderboardEntry> entries) {
        Inventory inv = Bukkit.createInventory(null, 45, m(TITLE));
        for (int i = 0; i < 45; i++) inv.setItem(i, ItemBuilder.separator());

        // Entrées du classement
        for (int i = 0; i < entries.size() && i < ENTRY_SLOTS.length; i++) {
            inv.setItem(ENTRY_SLOTS[i], buildEntryItem(i + 1, entries.get(i), category));
        }

        // Titre du classement (slot central haut)
        inv.setItem(0, new ItemBuilder(Material.GOLD_BLOCK)
            .name(m("gui.leaderboard.header-name", categoryLabel(category)))
            .lore(m("gui.leaderboard.top-label"), m("gui.leaderboard.sorted-by", categoryLabel(category)))
            .build());

        // Onglets
        inv.setItem(SLOT_TAB_KILLS, tabItem(Material.IRON_SWORD,  m("gui.leaderboard.tab-kills"),  "total_kills",  category));
        inv.setItem(SLOT_TAB_WAVES, tabItem(Material.BLAZE_ROD,   m("gui.leaderboard.tab-waves"),  "total_waves",  category));
        inv.setItem(SLOT_TAB_GAMES, tabItem(Material.COMPASS,     m("gui.leaderboard.tab-games"),  "total_games",  category));
        inv.setItem(SLOT_CLOSE, new ItemBuilder(Material.BARRIER)
            .name(m("gui.leaderboard.close-button")).lore(m("gui.leaderboard.close-lore")).build());

        player.openInventory(inv);
    }

    // ── Clics ─────────────────────────────────────────────────────────────────

    public void handleClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        switch (event.getSlot()) {
            case SLOT_TAB_KILLS  -> open(player, "total_kills");
            case SLOT_TAB_WAVES  -> open(player, "total_waves");
            case SLOT_TAB_GAMES  -> open(player, "total_games");
            case SLOT_CLOSE      -> player.closeInventory();
        }
    }

    // ── Builders ──────────────────────────────────────────────────────────────

    private org.bukkit.inventory.ItemStack buildEntryItem(int rank, LeaderboardEntry e, String category) {
        String medal = switch (rank) {
            case 1  -> m("gui.leaderboard.medal-1");
            case 2  -> m("gui.leaderboard.medal-2");
            case 3  -> m("gui.leaderboard.medal-3");
            default -> m("gui.leaderboard.medal-other", rank);
        };

        String statLine = switch (category) {
            case "total_waves" -> m("gui.leaderboard.stat-waves", e.totalWaves());
            case "total_games" -> m("gui.leaderboard.stat-games", e.totalGames());
            default            -> m("gui.leaderboard.stat-kills", e.totalKills());
        };

        return new ItemBuilder(Material.PLAYER_HEAD)
            .name(medal + "&f" + e.name())
            .lore(statLine,
                  m("gui.leaderboard.stat-kills",  e.totalKills()),
                  m("gui.leaderboard.stat-waves",  e.totalWaves()),
                  m("gui.leaderboard.stat-games",  e.totalGames()))
            .build();
    }

    private org.bukkit.inventory.ItemStack tabItem(Material mat, String label,
                                                    String tabCategory, String activeCategory) {
        boolean active = tabCategory.equals(activeCategory);
        return new ItemBuilder(mat)
            .name((active ? "&a" : "&7") + label + (active ? " &a✓" : ""))
            .lore(active ? m("gui.leaderboard.tab-active") : m("gui.leaderboard.tab-click"))
            .build();
    }

    private String categoryLabel(String category) {
        return switch (category) {
            case "total_waves" -> m("gui.leaderboard.category-waves");
            case "total_games" -> m("gui.leaderboard.category-games");
            default            -> m("gui.leaderboard.category-kills");
        };
    }
}
