package fr.obsidianlabs.plugins.villagedefense.hook;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

final class PlaceholderHook {

    private final VillageDefensePlugin plugin;
    private boolean available;
    private Method setPlaceholdersMethod;

    PlaceholderHook(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    void enable() {
        available = false;
        setPlaceholdersMethod = null;
        if (!plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        try {
            Class<?> papiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            setPlaceholdersMethod = papiClass.getMethod("setPlaceholders", Player.class, String.class);
            available = true;
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("[Hooks] PlaceholderAPI détecté mais API inaccessible : " + ex.getMessage());
        }
    }

    boolean isAvailable() {
        return available;
    }

    String apply(CommandSender sender, String text) {
        if (!available || !(sender instanceof Player player) || text == null || text.isEmpty()) {
            return text;
        }
        try {
            Object result = setPlaceholdersMethod.invoke(null, player, text);
            return result instanceof String resolved ? resolved : text;
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("[Hooks] PlaceholderAPI indisponible pendant la résolution : " + ex.getMessage());
            available = false;
            return text;
        }
    }
}
