package com.pseudoelephant.arcanerelay.features.signaltrigger.activation;

import com.hypixel.hytale.codec.builder.BuilderCodec;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.pseudoelephant.arcanerelay.core.activation.ActivationExecutor;
import com.pseudoelephant.arcanerelay.core.activation.ArcaneCachedAccessor;
import com.pseudoelephant.arcanerelay.core.adapters.ChunkStoreCommandBufferLike;
import com.pseudoelephant.arcanerelay.features.activation.Activation;
import com.pseudoelephant.arcanerelay.features.signal.components.ArcaneSection;

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
            ActivationExecutor.sendSignals(store, blockRef, worldX, worldY, worldZ);
        });

        return ArcaneSection.BlockTickStrategy.PROCESSED;
    }
}
