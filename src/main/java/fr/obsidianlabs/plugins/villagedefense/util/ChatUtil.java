package fr.obsidianlabs.plugins.villagedefense.util;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ChatUtil {

    private ChatUtil() {}

    public static String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public static void send(CommandSender sender, String message) {
        sender.sendMessage(color(message));
    }

    public static void sendRaw(CommandSender sender, String message) {
        sender.sendMessage(message);
    }

    public static void broadcast(Iterable<Player> players, String message) {
        String colored = color(message);
        for (Player p : players) {
            p.sendMessage(colored);
        }
    }

    public static void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(
            ChatMessageType.ACTION_BAR,
            TextComponent.fromLegacyText(color(message))
        );
    }

    public static void sendTitle(Player player, String title, String subtitle,
                                 int fadeInTicks, int stayTicks, int fadeOutTicks) {
        player.sendTitle(color(title), color(subtitle), fadeInTicks, stayTicks, fadeOutTicks);
    }

    /** Formate un message avec des paramètres positionnels ({0}, {1}, etc.) */
    public static String format(String message, Object... args) {
        for (int i = 0; i < args.length; i++) {
            message = message.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return message;
    }

    public static String stripColor(String message) {
        return ChatColor.stripColor(color(message));
    }
}
