package com.arcanerelay.config.types;

import com.arcanerelay.components.ArcaneSection;
import com.arcanerelay.components.ArcaneSignalComponent;
import com.arcanerelay.config.Activation;
import com.arcanerelay.core.activation.ActivationExecutor;
import com.arcanerelay.core.activation.ArcaneActivationAccessor;
import com.arcanerelay.core.activation.ArcaneCachedAccessor;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.arcanerelay.core.activation.ChunkStoreCommandBufferLike;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public class SendSignalActivation extends Activation {
    public static final BuilderCodec<SendSignalActivation> CODEC =
        BuilderCodec.builder(
            SendSignalActivation.class,
            SendSignalActivation::new,
            Activation.ABSTRACT_CODEC
        )
        .documentation("Sends arcane signals to connected output blocks. No state change.")
        .build();

    public SendSignalActivation() {
    }

    @Override
    public ArcaneSection.BlockTickStrategy execute(
        @Nonnull ArcaneCachedAccessor accessor,
        @Nullable Ref<ChunkStore> sectionRef,
        @Nullable Ref<ChunkStore> blockRef,
        int worldX, int worldY, int worldZ,
        @Nonnull List<int[]> sources
    ) {
        ChunkStoreCommandBufferLike commandBuffer = accessor.getCommandBuffer();

        commandBuffer.run((@Nonnull Store<ChunkStore> store) -> {
            World world = store.getExternalData().getWorld();

            ActivationExecutor.playEffects(world, worldX, worldY, worldZ, getEffects());

            if (blockRef == null || !blockRef.isValid()) return;
            store.addComponent(blockRef, ArcaneSignalComponent.getComponentType());
        });

        return ArcaneSection.BlockTickStrategy.PROCESSED;
    }
}
