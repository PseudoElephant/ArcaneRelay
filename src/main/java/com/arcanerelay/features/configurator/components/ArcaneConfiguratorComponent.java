package com.arcanerelay.features.configurator.components;

import com.arcanerelay.ArcaneRelayPlugin;
import com.arcanerelay.features.configurator.util.VisualsUtil;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;

import org.joml.Vector3f;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Player component: tracks which Arcane Trigger block is being configured. */
public class ArcaneConfiguratorComponent implements Component<EntityStore> {

    @Nullable
    private Vector3i configuredBlock;
    private Vector3f displayColor = VisualsUtil.getNextColor();

    public static ComponentType<EntityStore, ArcaneConfiguratorComponent> getComponentType() {
        return ArcaneRelayPlugin.get().getArcaneConfiguratorComponentType();
    }

    @Nullable
    public Vector3i getConfiguredBlock() {
        return configuredBlock;
    }

    public void setConfiguredBlock(@Nullable Vector3i pos) {
        this.configuredBlock = pos != null ? new Vector3i(pos) : null;
        if (this.configuredBlock != null) {
            this.displayColor = VisualsUtil.getNextColor();
        }
    }

    public void clearConfiguredBlock() {
        this.configuredBlock = null;
    }

    public Vector3f getDisplayColor() {
        return this.displayColor;
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        ArcaneConfiguratorComponent clone = new ArcaneConfiguratorComponent();
        clone.configuredBlock = configuredBlock != null ? new Vector3i(configuredBlock) : null;
        return clone;
    }
}
