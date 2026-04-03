package fr.obsidianlabs.plugins.villagedefense.gui;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Capture le prochain message chat d'un joueur pour une saisie textuelle dans les GUI.
 * Usage :
 *   1. chatInput.prompt(player, "Tapez le nom:", value -> { ... });
 *   2. Dans l'event AsyncPlayerChatEvent : chatInput.intercept(player, message)
 *
 * IMPORTANT : intercept() est appelé depuis un thread async (AsyncPlayerChatEvent).
 * Le callback est toujours exécuté sur le thread principal via runTask().
 */
public class ChatInputManager {

    private final VillageDefensePlugin plugin;
    private final Map<UUID, PendingInput> pending = new HashMap<>();

    public record PendingInput(String prompt, Consumer<String> callback) {}

    public ChatInputManager(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    /** Demande une saisie au joueur et capture son prochain message. */
    public void prompt(Player player, String promptMessage, Consumer<String> callback) {
        pending.put(player.getUniqueId(), new PendingInput(promptMessage, callback));
        player.closeInventory();
        fr.obsidianlabs.plugins.villagedefense.util.ChatUtil.send(player,
            plugin.getMessages().format("general.input-prompt", promptMessage));
        fr.obsidianlabs.plugins.villagedefense.util.ChatUtil.send(player,
            plugin.getMessages().format("general.input-cancel-hint"));
    }

    /**
     * Appeler depuis AsyncPlayerChatEvent.
     * Le callback est dispatché sur le thread principal pour éviter les IllegalStateException
     * lors d'ouvertures d'inventaire ou autres opérations Bukkit non-thread-safe.
     *
     * @return true si le message a été intercepté (doit être annulé dans l'event)
     */
    public boolean intercept(Player player, String message) {
        PendingInput input = pending.remove(player.getUniqueId());
        if (input == null) return false;

        if (!message.equalsIgnoreCase("annuler")) {
            Bukkit.getScheduler().runTask(plugin, () -> input.callback().accept(message));
        } else {
            fr.obsidianlabs.plugins.villagedefense.util.ChatUtil.send(player,
                plugin.getMessages().format("general.input-cancelled"));
        }
        return true;
    }

    public boolean hasPending(Player player) {
        return pending.containsKey(player.getUniqueId());
    }

    public void cancel(Player player) {
        pending.remove(player.getUniqueId());
    }
}
