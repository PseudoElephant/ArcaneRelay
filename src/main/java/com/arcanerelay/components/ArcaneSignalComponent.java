package com.arcanerelay.components;

import javax.annotation.Nonnull;

import com.arcanerelay.ArcaneRelayPlugin;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public class ArcaneSignalComponent implements Component<ChunkStore> {
    public static final BuilderCodec<ArcaneSignalComponent> CODEC = BuilderCodec.builder(ArcaneSignalComponent.class, ArcaneSignalComponent::new)
        .build();
    
    public static ComponentType<ChunkStore, ArcaneSignalComponent> getComponentType() {
        return ArcaneRelayPlugin.get().getArcaneSignalComponentType();
    }

    @Nonnull
    @Override
    public Component<ChunkStore> clone() {
        ArcaneSignalComponent clone = new ArcaneSignalComponent();
        return clone;
    }
}