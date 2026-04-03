package fr.obsidianlabs.plugins.villagedefense.gui;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.tower.Tower;
import fr.obsidianlabs.plugins.villagedefense.tower.TowerSlot;
import fr.obsidianlabs.plugins.villagedefense.tower.TowerSpecialization;
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
 * GUI pour choisir/changer la spécialisation d'une tour au niveau maximum.
 * 27 slots — les options sont disposées selon le nombre de spécialisations disponibles.
 */
public class SpecializationGUI {

    public static final String TITLE = "gui.spec.title";

    private final VillageDefensePlugin plugin;

    private record SpecContext(String arenaId, String slotId) {}
    private final Map<UUID, SpecContext> contexts = new HashMap<>();

    // Slots selon le nombre d'options (2 ou 3)
    private static final int[] SLOTS_2 = {11, 15};
    private static final int[] SLOTS_3 = {10, 13, 16};

    public SpecializationGUI(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    private String m(String key, Object... args) {
        return plugin.getMessages().format(key, args);
    }

    // ── Ouverture ─────────────────────────────────────────────────────────────

    public void open(Player player, Arena arena, TowerSlot slot) {
        Tower tower = slot.getPlacedTower();
        if (tower == null) return;

        List<TowerSpecialization> specs = TowerSpecialization.forType(tower.getType());
        if (specs.isEmpty()) return;

        Inventory inv = Bukkit.createInventory(null, 27, m(TITLE));
        for (int i = 0; i < 27; i++) inv.setItem(i, ItemBuilder.separator());

        // Info de la tour actuelle
        inv.setItem(4, new ItemBuilder(tower.getType().getTopMaterial())
            .name(m("gui.spec.tower-header", m(tower.getType().getNameKey()), tower.getLevel()))
            .lore(tower.hasSpecialization()
                ? m("gui.spec.current", m(tower.getSpecialization().getNameKey()))
                : m("gui.spec.none-active"),
                  m("gui.spec.choose-below"))
            .build());

        // Options de spécialisation
        int[] slotLayout = (specs.size() >= 3) ? SLOTS_3 : SLOTS_2;
        for (int i = 0; i < specs.size() && i < slotLayout.length; i++) {
            inv.setItem(slotLayout[i], buildSpecItem(specs.get(i), tower));
        }

        // Bouton retour
        inv.setItem(22, new ItemBuilder(Material.ARROW)
            .name(m("gui.spec.back-button"))
            .lore(m("gui.spec.back-lore"))
            .build());

        // IMPORTANT : put context AFTER openInventory to avoid InventoryCloseEvent race
        player.openInventory(inv);
        contexts.put(player.getUniqueId(), new SpecContext(arena.getId(), slot.getId()));
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

        SpecContext ctx = contexts.get(player.getUniqueId());
        if (ctx == null) return;

        Arena arena = plugin.getArenaManager().getArena(ctx.arenaId());
        if (arena == null) return;

        TowerSlot towerSlot = arena.getTowerSlots().stream()
            .filter(s -> s.getId().equals(ctx.slotId()))
            .findFirst().orElse(null);
        if (towerSlot == null) return;

        Tower tower = towerSlot.getPlacedTower();
        if (tower == null) return;

        int clickedSlot = event.getSlot();

        // Bouton retour
        if (clickedSlot == 22) {
            contexts.remove(player.getUniqueId());
            plugin.getTowerGui().openPlaced(player, arena, towerSlot);
            return;
        }

        // Vérifier si le slot correspond à une spécialisation
        List<TowerSpecialization> specs = TowerSpecialization.forType(tower.getType());
        int[] slotLayout = (specs.size() >= 3) ? SLOTS_3 : SLOTS_2;

        for (int i = 0; i < specs.size() && i < slotLayout.length; i++) {
            if (clickedSlot == slotLayout[i]) {
                TowerSpecialization chosen = specs.get(i);
                tower.setSpecialization(chosen);
                plugin.getTowerManager().refreshDisplay(tower);
                fr.obsidianlabs.plugins.villagedefense.util.ChatUtil.send(player,
                    m("gui.spec.activated", m(chosen.getNameKey())));
                contexts.remove(player.getUniqueId());
                player.closeInventory();
                return;
            }
        }
    }

    // ── Builders ──────────────────────────────────────────────────────────────

    private org.bukkit.inventory.ItemStack buildSpecItem(TowerSpecialization spec, Tower tower) {
        boolean isActive = tower.getSpecialization() == spec;

        List<String> lore = new ArrayList<>();
        lore.add(m(spec.getDescKey()));
        lore.add("");
        if (isActive) {
            lore.add(m("gui.spec.active"));
            lore.add(m("gui.spec.click-disable"));
        } else {
            lore.add(m("gui.spec.click-choose"));
        }

        return new ItemBuilder(spec.getIcon())
            .name((isActive ? "&a" : "&6") + m(spec.getNameKey()) + (isActive ? " &a✓" : ""))
            .lore(lore)
            .build();
    }
}
