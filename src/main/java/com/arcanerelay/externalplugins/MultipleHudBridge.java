package com.arcanerelay.externalplugins;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;

public class MultipleHudBridge extends ExternalPluginBridge {

    private MultipleHudBridge() {
    }

    @Nonnull
    private static PluginIdentifier id() {
        return new PluginIdentifier("Buuz135", "MultipleHUD");
    }

    public static boolean isAvailable() {
        return isPluginLoaded(id());
    }

    public static void setCustomHud(
        @Nonnull Player player,
        @Nonnull PlayerRef playerRef,
        @Nonnull String hudId,
        @Nonnull Object hud
    ) {
        Object plugin = PluginManager.get().getPlugin(id());
        if (plugin == null) return;

        try {
            Method getInstance = plugin.getClass().getMethod("getInstance");
            Object instance = getInstance.invoke(plugin);
            if (instance == null) return;

            Method setCustomHud = findSetCustomHud(instance, player, playerRef, hudId, hud);
            if (setCustomHud != null) {
                setCustomHud.invoke(instance, player, playerRef, hudId, hud);
            }
        } catch (ReflectiveOperationException ignored) {
            // Optional integration only; ignore when the plugin or API changes.
        }
    }

    @Nullable
    private static Method findSetCustomHud(
        @Nonnull Object instance,
        @Nonnull Player player,
        @Nonnull PlayerRef playerRef,
        @Nonnull String hudId,
        @Nonnull Object hud
    ) {
        for (Method method : instance.getClass().getMethods()) {
            if (!"setCustomHud".equals(method.getName())) continue;
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 4) continue;
            if (!params[0].isAssignableFrom(player.getClass())) continue;
            if (!params[1].isAssignableFrom(playerRef.getClass())) continue;
            if (!params[2].isAssignableFrom(hudId.getClass())) continue;
            if (!params[3].isAssignableFrom(hud.getClass())) continue;
            return method;
        }
        return null;
    }
}
