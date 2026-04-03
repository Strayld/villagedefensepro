package fr.obsidianlabs.plugins.villagedefense.hook;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.util.ChatUtil;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class ProtocolLibHook {

    private final VillageDefensePlugin plugin;
    private boolean available;
    private boolean warned;
    private Object protocolManager;
    private Object actionBarPacketType;
    private Method createPacketMethod;
    private Method sendServerPacketMethod;
    private Method getChatComponentsMethod;
    private Method structureWriteMethod;
    private Method wrappedFromJsonMethod;

    ProtocolLibHook(VillageDefensePlugin plugin) {
        this.plugin = plugin;
    }

    void enable() {
        available = false;
        warned = false;
        protocolManager = null;
        actionBarPacketType = null;
        createPacketMethod = null;
        sendServerPacketMethod = null;
        getChatComponentsMethod = null;
        structureWriteMethod = null;
        wrappedFromJsonMethod = null;

        if (!plugin.getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
            return;
        }

        try {
            Class<?> protocolLibraryClass = Class.forName("com.comphenix.protocol.ProtocolLibrary");
            Class<?> packetTypeClass = Class.forName("com.comphenix.protocol.PacketType");
            Class<?> playServerClass = Class.forName("com.comphenix.protocol.PacketType$Play$Server");
            Class<?> packetContainerClass = Class.forName("com.comphenix.protocol.events.PacketContainer");
            Class<?> structureModifierClass = Class.forName("com.comphenix.protocol.reflect.StructureModifier");
            Class<?> wrappedChatComponentClass = Class.forName("com.comphenix.protocol.wrappers.WrappedChatComponent");

            Method getProtocolManager = protocolLibraryClass.getMethod("getProtocolManager");
            protocolManager = getProtocolManager.invoke(null);

            actionBarPacketType = resolvePacketType(playServerClass);
            if (actionBarPacketType == null) {
                return;
            }

            createPacketMethod = findMethod(protocolManager.getClass(), "createPacket", 1);
            sendServerPacketMethod = findMethod(protocolManager.getClass(), "sendServerPacket", 2);
            getChatComponentsMethod = packetContainerClass.getMethod("getChatComponents");
            structureWriteMethod = findMethod(structureModifierClass, "write", 2);
            wrappedFromJsonMethod = wrappedChatComponentClass.getMethod("fromJson", String.class);

            available = createPacketMethod != null
                && sendServerPacketMethod != null
                && structureWriteMethod != null;
        } catch (ReflectiveOperationException ex) {
            logFailure(ex);
        }
    }

    boolean isAvailable() {
        return available;
    }

    boolean sendActionBar(Player player, String legacyMessage) {
        if (!available || player == null || !player.isOnline()) {
            return false;
        }

        try {
            Object packet = createPacketMethod.invoke(protocolManager, actionBarPacketType);
            Object modifier = getChatComponentsMethod.invoke(packet);
            String json = ComponentSerializer.toString(
                TextComponent.fromLegacyText(ChatUtil.color(legacyMessage))
            );
            Object wrappedComponent = wrappedFromJsonMethod.invoke(null, json);
            structureWriteMethod.invoke(modifier, 0, wrappedComponent);
            sendServerPacketMethod.invoke(protocolManager, player, packet);
            return true;
        } catch (ReflectiveOperationException ex) {
            logFailure(ex);
            available = false;
            return false;
        }
    }

    private Object resolvePacketType(Class<?> playServerClass) throws ReflectiveOperationException {
        for (String fieldName : new String[]{"SET_ACTION_BAR_TEXT", "ACTION_BAR"}) {
            try {
                Field field = playServerClass.getField(fieldName);
                return field.get(null);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    private Method findMethod(Class<?> type, String name, int paramCount) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == paramCount) {
                return method;
            }
        }
        return null;
    }

    private void logFailure(Exception ex) {
        if (warned) {
            return;
        }
        warned = true;
        plugin.getLogger().warning("[Hooks] ProtocolLib détecté mais intégration désactivée : " + ex.getMessage());
    }
}
