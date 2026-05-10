package com.arcanerelay.externalplugins;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.server.core.plugin.PluginManager;

import javax.annotation.Nonnull;

/**
 * Shared helpers for optional integrations with other mods (plugin id or classpath checks).
 * Plugin bridges define a static {@code id()} and use {@link #isPluginLoaded(PluginIdentifier)}.
 */
public abstract class ExternalPluginBridge {

    protected ExternalPluginBridge() {
    }

    /**
     * {@code true} if {@link PluginManager} has a loaded plugin with this identifier.
     */
    protected static boolean isPluginLoaded(@Nonnull PluginIdentifier pluginId) {
        return PluginManager.get().getPlugin(pluginId) != null;
    }

    /**
     * {@code true} if the binary class name can be loaded (typical pattern for optional mods
     * you only integrate with via reflection).
     */
    public static boolean isClassAvailable(@Nonnull String binaryName) {
        try {
            Class.forName(binaryName);
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }
}
