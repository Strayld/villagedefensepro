package fr.obsidianlabs.plugins.villagedefense.gui;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.achievement.AchievementDefinition;
import fr.obsidianlabs.plugins.villagedefense.player.VDPlayer;
import fr.obsidianlabs.plugins.villagedefense.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

public class AchievementGUI {

    public static final String TITLE = "gui.achievements.title";

    private final VillageDefensePlugin plugin;

    public AchievementGUI(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    private String m(String key, Object... args) {
        return plugin.getMessages().format(key, args);
    }

    public void open(Player player) {
        VDPlayer vdp = plugin.getPlayerManager().getPlayer(player);
        if (vdp == null) return;

        List<AchievementDefinition> all = plugin.getAchievementManager().getAll();

        Inventory inv = Bukkit.createInventory(null, 54, m(TITLE));
        for (int i = 0; i < 54; i++) inv.setItem(i, ItemBuilder.separator());

        // Progress item at slot 4
        int completed = vdp.getCompletedAchievements().size();
        inv.setItem(4, new ItemBuilder(Material.NETHER_STAR)
            .name(m("gui.achievements.progress-header", completed, all.size()))
            .lore(m("gui.achievements.info-line1"),
                  m("gui.achievements.info-line2"))
            .build());

        // Achievement items starting at slot 9
        for (int i = 0; i < all.size() && i < 36; i++) {
            AchievementDefinition def = all.get(i);
            boolean done = vdp.hasAchievementId(def.getId());
            long progress = getProgress(vdp, def);

            List<String> lore = new ArrayList<>();
            lore.add("&7" + def.getDescription());
            lore.add("");
            if (done) {
                lore.add(m("gui.achievements.unlocked"));
            } else {
                lore.add(m("gui.achievements.progress",
                           Math.min(progress, def.getTarget()), def.getTarget()));
                lore.add(m("gui.achievements.locked"));
            }

            Material icon = done ? def.getIcon() : Material.GRAY_DYE;
            String nameColor = done ? "&a" : "&8";

            inv.setItem(9 + i, new ItemBuilder(icon)
                .name(nameColor + def.getDisplayName())
                .lore(lore)
                .build());
        }

        player.openInventory(inv);
    }

    private long getProgress(VDPlayer vdp, AchievementDefinition def) {
        return switch (def.getCondition()) {
            case KILLS        -> vdp.getTotalKills();
            case WAVES        -> vdp.getTotalWavesCompleted();
            case GAMES        -> vdp.getTotalGamesPlayed();
            case COINS_EARNED -> vdp.getTotalCoinsEarned();
        };
    }

    public void handleClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        // Read-only GUI — no click actions
    }
}
