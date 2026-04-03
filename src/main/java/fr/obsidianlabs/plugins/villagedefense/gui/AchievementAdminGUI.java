package fr.obsidianlabs.plugins.villagedefense.gui;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.achievement.AchievementCondition;
import fr.obsidianlabs.plugins.villagedefense.achievement.AchievementDefinition;
import fr.obsidianlabs.plugins.villagedefense.util.ChatUtil;
import fr.obsidianlabs.plugins.villagedefense.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;

public class AchievementAdminGUI {

    public static final String TITLE = "gui.achievement-admin.title";

    private static final int SLOT_NEW  = 0;
    private static final int SLOT_BACK = 8;

    private final VillageDefensePlugin plugin;

    public AchievementAdminGUI(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    private String m(String key, Object... args) {
        return plugin.getMessages().format(key, args);
    }

    public void open(Player admin) {
        Inventory inv = Bukkit.createInventory(null, 54, m(TITLE));

        // Row 0 separator
        for (int i = 0; i < 9; i++) inv.setItem(i, ItemBuilder.separator());
        // Row 5 separator
        for (int i = 45; i < 54; i++) inv.setItem(i, ItemBuilder.separator());

        // Bouton "Nouveau succès"
        inv.setItem(SLOT_NEW, new ItemBuilder(Material.NETHER_STAR)
            .name(m("admin.achievement-admin.new-item"))
            .lore(m("admin.achievement-admin.new-item-lore"))
            .build());

        // Bouton retour
        inv.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW)
            .name(m("admin.common.back"))
            .build());

        // Items des succès (slots 9-44, max 36)
        List<AchievementDefinition> defs = plugin.getAchievementManager().getAll();
        for (int i = 0; i < defs.size() && i < 36; i++) {
            AchievementDefinition def = defs.get(i);
            inv.setItem(9 + i, new ItemBuilder(def.getIcon())
                .name("&6" + def.getDisplayName())
                .lore(
                    def.getDescription(),
                    "",
                    "&7" + def.getCondition().name() + " / " + def.getTarget(),
                    "",
                    "&eCliquez pour modifier",
                    "&cShift+Clic pour supprimer"
                )
                .build());
        }

        admin.openInventory(inv);
    }

    public void handleClick(InventoryClickEvent event, Player admin) {
        event.setCancelled(true);
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlot() < 0 || event.getRawSlot() >= topSize) return;
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
        if (event.getCurrentItem().getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        int slot = event.getRawSlot();

        if (slot == SLOT_BACK) {
            plugin.getVdAdminGui().open(admin);
            return;
        }

        if (slot == SLOT_NEW) {
            plugin.getChatInputManager().prompt(admin, m("admin.achievement-admin.new-id-prompt"), id -> {
                id = id.trim().toUpperCase().replaceAll("[^A-Z0-9_]", "");
                if (id.isEmpty()) {
                    ChatUtil.send(admin, m("admin.achievement-admin.id-invalid"));
                    open(admin);
                    return;
                }
                if (plugin.getAchievementManager().getById(id) != null) {
                    ChatUtil.send(admin, m("admin.achievement-admin.id-exists", id));
                    open(admin);
                    return;
                }
                AchievementDefinition def = new AchievementDefinition(
                    id, id, "", Material.NETHER_STAR, AchievementCondition.KILLS, 1
                );
                plugin.getAchievementManager().add(def);
                ChatUtil.send(admin, m("admin.achievement-admin.created", id));
                plugin.getAchievementEditGui().open(admin, def);
            });
            return;
        }

        // Clic sur un succès existant
        int idx = slot - 9;
        List<AchievementDefinition> defs = plugin.getAchievementManager().getAll();
        if (idx < 0 || idx >= defs.size()) return;
        AchievementDefinition def = defs.get(idx);

        if (event.isShiftClick()) {
            plugin.getAchievementManager().remove(def.getId());
            ChatUtil.send(admin, m("admin.achievement-admin.deleted", def.getId()));
            open(admin);
        } else {
            plugin.getAchievementEditGui().open(admin, def);
        }
    }
}
