package com.arcanerelay.components;

import com.arcanerelay.ArcaneRelayPlugin;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Player component: tracks which Arcane Trigger block is being configured. */
public class ArcaneConfiguratorComponent implements Component<EntityStore> {

    @Nullable
    private Vector3i configuredBlock;

    public static ComponentType<EntityStore, ArcaneConfiguratorComponent> getComponentType() {
        return ArcaneRelayPlugin.get().getArcaneConfiguratorComponentType();
    }

    @Nullable
    public Vector3i getConfiguredBlock() {
        return configuredBlock;
    }

    public void setConfiguredBlock(@Nullable Vector3i pos) {
        this.configuredBlock = pos != null ? pos.clone() : null;
    }

    public void clearConfiguredBlock() {
        this.configuredBlock = null;
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        ArcaneConfiguratorComponent clone = new ArcaneConfiguratorComponent();
        clone.configuredBlock = configuredBlock != null ? configuredBlock.clone() : null;
        return clone;
    }
}
