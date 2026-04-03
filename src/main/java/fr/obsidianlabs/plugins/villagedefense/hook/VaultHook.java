package fr.obsidianlabs.plugins.villagedefense.hook;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;

final class VaultHook {

    private final VillageDefensePlugin plugin;
    private boolean available;
    private Object permissionProvider;
    private Object economyProvider;

    VaultHook(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    void enable() {
        available = false;
        permissionProvider = null;
        economyProvider = null;

        if (!plugin.getServer().getPluginManager().isPluginEnabled("Vault")) {
            return;
        }

        try {
            Class permissionClass = Class.forName("net.milkbowl.vault.permission.Permission");
            RegisteredServiceProvider<?> permissionRsp =
                plugin.getServer().getServicesManager().getRegistration(permissionClass);
            if (permissionRsp != null) {
                permissionProvider = permissionRsp.getProvider();
            }

            Class economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> economyRsp =
                plugin.getServer().getServicesManager().getRegistration(economyClass);
            if (economyRsp != null) {
                economyProvider = economyRsp.getProvider();
            }

            available = permissionProvider != null || economyProvider != null;
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("[Hooks] Vault détecté mais API inaccessible : " + ex.getMessage());
        }
    }

    boolean isAvailable() {
        return available;
    }

    boolean hasPermissionProvider() {
        return permissionProvider != null;
    }

    boolean hasEconomyProvider() {
        return economyProvider != null;
    }

    boolean hasPermission(CommandSender sender, String node) {
        if (sender == null) {
            return false;
        }
        if (!(sender instanceof Player player) || permissionProvider == null
            || !plugin.getConfig().getBoolean("hooks.vault.permissions", true)) {
            return sender.hasPermission(node);
        }

        Boolean result = invokePermissionCheck(player, node);
        return result != null ? result : sender.hasPermission(node);
    }

    void deposit(Player player, double amount) {
        if (economyProvider == null || player == null || amount <= 0) return;
        try {
            invokeBestMatch(economyProvider, "depositPlayer", player, player.getName(), null, amount);
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("[Hooks] Dépôt Vault échoué : " + ex.getMessage());
        }
    }

    void syncBalance(Player player, long coins) {
        if (player == null) {
            return;
        }
        syncBalanceInternal(player, player.getName(), coins);
    }

    void syncBalance(String playerName, long coins) {
        if (playerName == null || playerName.isBlank()) {
            return;
        }
        syncBalanceInternal(null, playerName, coins);
    }

    private void syncBalanceInternal(Player player, String playerName, long coins) {
        if (economyProvider == null || !plugin.getConfig().getBoolean("hooks.vault.economy-sync", false)) {
            return;
        }

        try {
            invokeBestMatch(economyProvider, "createPlayerAccount", player, playerName, null, null);
            Object balanceResult = invokeBestMatch(economyProvider, "getBalance", player, playerName, null, null);
            double currentBalance = balanceResult instanceof Number number ? number.doubleValue() : 0.0D;
            double targetBalance = Math.max(0, coins);
            double delta = targetBalance - currentBalance;
            if (Math.abs(delta) < 0.0001D) {
                return;
            }

            if (delta > 0.0D) {
                invokeBestMatch(economyProvider, "depositPlayer", player, playerName, null, delta);
            } else {
                invokeBestMatch(economyProvider, "withdrawPlayer", player, playerName, null, -delta);
            }
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("[Hooks] Sync économie Vault indisponible : " + ex.getMessage());
        }
    }

    private Boolean invokePermissionCheck(Player player, String node) {
        for (String methodName : new String[]{"has", "playerHas"}) {
            for (Method method : permissionProvider.getClass().getMethods()) {
                if (!method.getName().equals(methodName)) {
                    continue;
                }
                if (!(method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
                    continue;
                }
                Object[] args = buildPermissionArgs(method.getParameterTypes(), player, node);
                if (args == null) {
                    continue;
                }
                try {
                    Object result = method.invoke(permissionProvider, args);
                    if (result instanceof Boolean value) {
                        return value;
                    }
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }
        return null;
    }

    private Object[] buildPermissionArgs(Class<?>[] types, Player player, String node) {
        if (types.length == 2) {
            if (CommandSender.class.isAssignableFrom(types[0]) && types[1] == String.class) {
                return new Object[]{player, node};
            }
            if (types[0].isInstance(player) && types[1] == String.class) {
                return new Object[]{player, node};
            }
            if (types[0] == String.class && types[1] == String.class) {
                return new Object[]{player.getName(), node};
            }
        }
        if (types.length == 3 && types[2] == String.class) {
            Object worldArg = null;
            if (types[0] == String.class) {
                worldArg = player.getWorld().getName();
            } else if (World.class.isAssignableFrom(types[0])) {
                worldArg = player.getWorld();
            }
            if (worldArg == null) {
                return null;
            }
            Object playerArg;
            if (types[1].isInstance(player)) {
                playerArg = player;
            } else if (types[1] == String.class) {
                playerArg = player.getName();
            } else {
                return null;
            }
            return new Object[]{worldArg, playerArg, node};
        }
        return null;
    }

    private Object invokeBestMatch(Object provider, String methodName, Player player, String playerName,
                                   World world, Double amount) throws ReflectiveOperationException {
        for (Method method : provider.getClass().getMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            Object[] args = buildEconomyArgs(method.getParameterTypes(),
                player, playerName, world != null ? world : player != null ? player.getWorld() : null, amount);
            if (args == null) {
                continue;
            }
            return method.invoke(provider, args);
        }
        return null;
    }

    private Object[] buildEconomyArgs(Class<?>[] types, Player player, String playerName, World world, Double amount) {
        if (types.length == 1) {
            if (player != null && types[0].isInstance(player)) {
                return new Object[]{player};
            }
            if (types[0] == String.class) {
                return new Object[]{playerName};
            }
            return null;
        }

        if (types.length == 2) {
            if (amount == null) {
                return null;
            }
            if (player != null && types[0].isInstance(player) && isNumeric(types[1])) {
                return new Object[]{player, castAmount(types[1], amount)};
            }
            if (types[0] == String.class && isNumeric(types[1])) {
                return new Object[]{playerName, castAmount(types[1], amount)};
            }
            return null;
        }

        if (types.length == 3) {
            if (amount == null || world == null) {
                return null;
            }
            Object worldArg = null;
            if (types[0] == String.class) {
                worldArg = world.getName();
            } else if (World.class.isAssignableFrom(types[0])) {
                worldArg = world;
            }
            if (worldArg == null) {
                return null;
            }

            Object playerArg;
            if (player != null && types[1].isInstance(player)) {
                playerArg = player;
            } else if (types[1] == String.class) {
                playerArg = playerName;
            } else {
                return null;
            }

            if (!isNumeric(types[2])) {
                return null;
            }
            return new Object[]{worldArg, playerArg, castAmount(types[2], amount)};
        }

        return null;
    }

    private boolean isNumeric(Class<?> type) {
        return type == double.class || type == Double.class
            || type == int.class || type == Integer.class
            || type == long.class || type == Long.class
            || type == float.class || type == Float.class;
    }

    private Object castAmount(Class<?> type, double amount) {
        if (type == int.class || type == Integer.class) {
            return (int) Math.round(amount);
        }
        if (type == long.class || type == Long.class) {
            return Math.round(amount);
        }
        if (type == float.class || type == Float.class) {
            return (float) amount;
        }
        return amount;
    }
}
