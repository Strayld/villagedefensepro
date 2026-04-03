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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AchievementEditGUI {

    public static final String TITLE = "gui.achievement-edit.title";

    private static final int SLOT_DISPLAY_NAME = 10;
    private static final int SLOT_DESCRIPTION  = 11;
    private static final int SLOT_ICON         = 12;
    private static final int SLOT_CONDITION    = 13;
    private static final int SLOT_TARGET       = 14;
    private static final int SLOT_BACK         = 22;

    private final VillageDefensePlugin plugin;
    private final Map<UUID, AchievementDefinition> editing = new HashMap<>();

    public AchievementEditGUI(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    private String m(String key, Object... args) {
        return plugin.getMessages().format(key, args);
    }

    public void open(Player admin, AchievementDefinition def) {
        editing.put(admin.getUniqueId(), def);

        Inventory inv = Bukkit.createInventory(null, 27, m(TITLE));
        for (int i = 0; i < 27; i++) inv.setItem(i, ItemBuilder.separator());

        inv.setItem(SLOT_DISPLAY_NAME, new ItemBuilder(Material.NAME_TAG)
            .name(m("admin.achievement-edit.display-name-item"))
            .lore(m("admin.achievement-edit.display-name-lore", def.getDisplayName()),
                  "",
                  "&eCliquez pour modifier")
            .build());

        inv.setItem(SLOT_DESCRIPTION, new ItemBuilder(Material.BOOK)
            .name(m("admin.achievement-edit.description-item"))
            .lore(m("admin.achievement-edit.description-lore", def.getDescription()),
                  "",
                  "&eCliquez pour modifier")
            .build());

        inv.setItem(SLOT_ICON, new ItemBuilder(def.getIcon())
            .name(m("admin.achievement-edit.icon-item"))
            .lore("&7Actuel : &f" + def.getIcon().name(),
                  "",
                  "&eCliquez pour modifier")
            .build());

        inv.setItem(SLOT_CONDITION, new ItemBuilder(Material.CLOCK)
            .name(m("admin.achievement-edit.condition-item"))
            .lore(m("admin.achievement-edit.condition-lore", def.getCondition().name()),
                  "",
                  "&eCliquez pour cycler")
            .build());

        inv.setItem(SLOT_TARGET, new ItemBuilder(Material.GOLD_NUGGET)
            .name(m("admin.achievement-edit.target-item"))
            .lore(m("admin.achievement-edit.target-lore", def.getTarget()),
                  "",
                  "&eCliquez pour modifier")
            .build());

        inv.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW)
            .name(m("admin.common.back"))
            .build());

        admin.openInventory(inv);
    }

    public void handleClick(InventoryClickEvent event, Player admin) {
        event.setCancelled(true);
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlot() < 0 || event.getRawSlot() >= topSize) return;
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
        if (event.getCurrentItem().getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        AchievementDefinition def = editing.get(admin.getUniqueId());
        if (def == null) return;

        switch (event.getRawSlot()) {
            case SLOT_DISPLAY_NAME -> plugin.getChatInputManager().prompt(admin,
                m("admin.achievement-edit.display-name-prompt"), value -> {
                    def.setDisplayName(value);
                    plugin.getAchievementManager().save();
                    open(admin, def);
                });

            case SLOT_DESCRIPTION -> plugin.getChatInputManager().prompt(admin,
                m("admin.achievement-edit.description-prompt"), value -> {
                    def.setDescription(value);
                    plugin.getAchievementManager().save();
                    open(admin, def);
                });

            case SLOT_ICON -> plugin.getChatInputManager().prompt(admin,
                m("admin.achievement-edit.icon-prompt"), value -> {
                    try {
                        Material mat = Material.valueOf(value.trim().toUpperCase());
                        def.setIcon(mat);
                        plugin.getAchievementManager().save();
                        open(admin, def);
                    } catch (IllegalArgumentException e) {
                        ChatUtil.send(admin, m("admin.achievement-edit.invalid-material", value));
                        open(admin, def);
                    }
                });

            case SLOT_CONDITION -> {
                AchievementCondition[] values = AchievementCondition.values();
                int next = (def.getCondition().ordinal() + 1) % values.length;
                def.setCondition(values[next]);
                plugin.getAchievementManager().save();
                open(admin, def);
            }

            case SLOT_TARGET -> plugin.getChatInputManager().prompt(admin,
                m("admin.achievement-edit.target-prompt"), value -> {
                    try {
                        long target = Long.parseLong(value.trim());
                        def.setTarget(target);
                        plugin.getAchievementManager().save();
                        open(admin, def);
                    } catch (NumberFormatException e) {
                        ChatUtil.send(admin, m("admin.achievement-edit.invalid-number"));
                        open(admin, def);
                    }
                });

            case SLOT_BACK -> {
                editing.remove(admin.getUniqueId());
                plugin.getAchievementAdminGui().open(admin);
            }
        }
    }
}
