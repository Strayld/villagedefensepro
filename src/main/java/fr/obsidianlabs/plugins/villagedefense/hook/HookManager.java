package fr.obsidianlabs.plugins.villagedefense.hook;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.util.ChatUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class HookManager {

    private final VillageDefensePlugin plugin;
    private final VaultHook vaultHook;
    private final PlaceholderHook placeholderHook;
    private final ProtocolLibHook protocolLibHook;
    private final BungeeCordHook bungeeCordHook;
    private VillageDefensePlaceholderExpansion villageDefensePlaceholderExpansion;

    public HookManager(VillageDefensePlugin plugin) {
        this.plugin = plugin;
        this.vaultHook = new VaultHook(plugin);
        this.placeholderHook = new PlaceholderHook(plugin);
        this.protocolLibHook = new ProtocolLibHook(plugin);
        this.bungeeCordHook = new BungeeCordHook(plugin);
    }

    public void enable() {
        vaultHook.enable();
        placeholderHook.enable();
        protocolLibHook.enable();
        bungeeCordHook.enable();
        registerPlaceholderExpansion();
    }

    public void disable() {
        unregisterPlaceholderExpansion();
        bungeeCordHook.disable();
    }

    public boolean hasPermission(CommandSender sender, String node) {
        return vaultHook.hasPermission(sender, node);
    }

    public String applyPlaceholders(CommandSender sender, String text) {
        return placeholderHook.apply(sender, text);
    }

    public void syncEconomyBalance(Player player, long coins) {
        vaultHook.syncBalance(player, coins);
    }

    public void syncEconomyBalance(UUID uuid, String name, long coins) {
        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null) {
            syncEconomyBalance(player, coins);
            return;
        }
        vaultHook.syncBalance(name, coins);
    }

    public void sendActionBar(Player player, String legacyMessage) {
        if (!protocolLibHook.sendActionBar(player, legacyMessage)) {
            ChatUtil.sendActionBar(player, legacyMessage);
        }
    }

    public boolean sendPlayerToReturnServer(Player player) {
        return bungeeCordHook.sendPlayerToReturnServer(player);
    }

    public boolean isVaultEnabled() {
        return vaultHook.isAvailable();
    }

    public boolean isVaultPermissionEnabled() {
        return vaultHook.hasPermissionProvider();
    }

    public boolean isVaultEconomyEnabled() {
        return vaultHook.hasEconomyProvider();
    }

    /** Dépose {@code amount} dans le compte Vault du joueur (récompense directe, non liée aux coins). */
    public void depositVaultMoney(Player player, double amount) {
        if (amount <= 0 || !vaultHook.hasEconomyProvider()) return;
        vaultHook.deposit(player, amount);
    }

    public boolean isPlaceholderApiEnabled() {
        return placeholderHook.isAvailable();
    }

    public boolean isProtocolLibEnabled() {
        return protocolLibHook.isAvailable();
    }

    public boolean isBungeeCordEnabled() {
        return bungeeCordHook.isAvailable();
    }

    private void registerPlaceholderExpansion() {
        unregisterPlaceholderExpansion();
        if (!placeholderHook.isAvailable()) {
            return;
        }

        villageDefensePlaceholderExpansion = new VillageDefensePlaceholderExpansion(plugin);
        if (!villageDefensePlaceholderExpansion.register()) {
            plugin.getLogger().warning("[Hooks] Impossible d'enregistrer l'expansion PlaceholderAPI interne.");
            villageDefensePlaceholderExpansion = null;
        }
    }

    private void unregisterPlaceholderExpansion() {
        if (villageDefensePlaceholderExpansion == null) {
            return;
        }
        villageDefensePlaceholderExpansion.unregister();
        villageDefensePlaceholderExpansion = null;
    }
}
