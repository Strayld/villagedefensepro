package fr.obsidianlabs.plugins.villagedefense.config;

import fr.obsidianlabs.plugins.villagedefense.util.ChatUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class Messages {

    private final fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin plugin;
    private FileConfiguration config;
    private String prefix;

    public Messages(fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        String lang = plugin.getConfig().getString("language", "fr");
        String resourcePath = "lang/" + lang + ".yml";

        // Si la langue demandée n'existe pas en ressource, retomber sur le français
        if (plugin.getResource(resourcePath) == null) {
            lang = "fr";
            resourcePath = "lang/fr.yml";
        }

        // 1. Charger la BASE depuis le JAR (toujours à jour, toutes les clés)
        InputStream jarStream = plugin.getResource(resourcePath);
        if (jarStream != null) {
            config = YamlConfiguration.loadConfiguration(
                new InputStreamReader(jarStream, StandardCharsets.UTF_8));
        } else {
            config = new YamlConfiguration();
        }

        // 2. Appliquer par-dessus les personnalisations du fichier disque
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            plugin.saveResource(resourcePath, false);
        } else {
            // Écraser uniquement les clés présentes dans le fichier disque
            YamlConfiguration diskConfig = YamlConfiguration.loadConfiguration(file);
            for (String key : diskConfig.getKeys(true)) {
                if (!diskConfig.isConfigurationSection(key)) {
                    config.set(key, diskConfig.get(key));
                }
            }
        }

        prefix = ChatUtil.color(config.getString("prefix", "&8[&6VD&8] &r"));
    }

    /**
     * Récupère un message formaté avec le préfixe.
     */
    public String get(String key, Object... args) {
        return buildMessage(null, key, true, args);
    }

    public String get(CommandSender sender, String key, Object... args) {
        return buildMessage(sender, key, true, args);
    }

    /**
     * Récupère un message formaté SANS le préfixe.
     */
    public String format(String key, Object... args) {
        return buildMessage(null, key, false, args);
    }

    public String format(CommandSender sender, String key, Object... args) {
        return buildMessage(sender, key, false, args);
    }

    /**
     * Récupère un message brut (pas de couleur, pas de préfixe).
     */
    public String raw(String key) {
        return config.getString(key, key);
    }

    /**
     * Récupère une liste de lignes formatées SANS le préfixe.
     * Si la clé n'est pas une liste, retombe sur un message simple.
     */
    public List<String> lines(String key, Object... args) {
        return lines(null, key, args);
    }

    public List<String> lines(CommandSender sender, String key, Object... args) {
        List<String> lines = config.getStringList(key);
        if (!lines.isEmpty()) {
            return lines.stream()
                .map(line -> colorize(sender, ChatUtil.format(line, args)))
                .toList();
        }

        String msg = config.getString(key);
        if (msg != null) {
            return List.of(colorize(sender, ChatUtil.format(msg, args)));
        }

        return List.of(missingMessage(sender, key));
    }

    /**
     * Envoie un message formaté au destinataire.
     */
    public void send(CommandSender sender, String key, Object... args) {
        sender.sendMessage(get(sender, key, args));
    }

    public String getPrefix() { return prefix; }

    private String buildMessage(CommandSender sender, String key, boolean withPrefix, Object... args) {
        String msg = config.getString(key);
        if (msg == null) {
            msg = missingMessage(sender, key);
        } else {
            msg = ChatUtil.format(msg, args);
            msg = colorize(sender, msg);
        }
        return withPrefix ? prefix + msg : msg;
    }

    private String colorize(CommandSender sender, String message) {
        if (sender != null && plugin.getHookManager() != null) {
            message = plugin.getHookManager().applyPlaceholders(sender, message);
        }
        return ChatUtil.color(message);
    }

    private String missingMessage(CommandSender sender, String key) {
        String template = config.getString("general.missing-message");
        if (template == null) {
            return key;
        }
        return colorize(sender, ChatUtil.format(template, key));
    }
}
