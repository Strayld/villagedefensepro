package fr.obsidianlabs.plugins.villagedefense.listener;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

/**
 * Délègue la fermeture d'inventaire au TowerGUI pour nettoyer le contexte de session.
 * Ne touche plus aux player-metadata (supprimés au profit d'une Map<UUID, Context> côté serveur).
 */
public class InventoryCloseListener implements Listener {

    private final VillageDefensePlugin plugin;

    public InventoryCloseListener(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        plugin.getTowerGui().onInventoryClose(player);
        plugin.getSpecializationGui().onInventoryClose(player);
        plugin.getTrapGui().onInventoryClose(player);
    }
}
