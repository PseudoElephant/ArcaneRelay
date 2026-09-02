package com.pseudoelephant.arcanerelay.features.activation.types;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.pseudoelephant.arcanerelay.core.activation.ArcaneCachedAccessor;
import com.pseudoelephant.arcanerelay.features.activation.Activation;
import com.pseudoelephant.arcanerelay.features.signal.components.ArcaneSection;

public class ChainActivation extends Activation {
    public static final BuilderCodec<ChainActivation> CODEC =
        BuilderCodec.builder(
            ChainActivation.class,
            ChainActivation::new,
            Activation.ABSTRACT_CODEC
        )
        .documentation("Runs multiple activations in sequence.")
        .appendInherited(
            new KeyedCodec<>("Activations", new ArrayCodec<>(Codec.STRING, String[]::new)),
            (a, ids) -> a.activationIds = ids,
            a -> a.activationIds,
            (a, p) -> a.activationIds = p.activationIds
        )
        .documentation("List of activation asset IDs to run in order.")
        .add()
        .build();

    private String[] activationIds = new String[0];

    public ChainActivation() {
    }

    public String[] getActivationIds() {
        return activationIds != null ? activationIds : new String[0];
    }

    public void setActivationIds(String[] activationIds) {
        this.activationIds = activationIds != null ? activationIds : new String[0];
    }

    @Override
    public ArcaneSection.BlockTickStrategy execute(
        @Nonnull ArcaneCachedAccessor accessor,
        @Nullable Ref<ChunkStore> sectionRef,
        @Nullable Ref<ChunkStore> blockRef,
        int worldX, int worldY, int worldZ,
        @Nonnull List<int[]> sources
    ) {
        for (String id : getActivationIds()) {
            if (id == null || id.isEmpty()) continue;
            Activation activation = Activation.getActivation(id);
            if (activation != null) {
                activation.execute(accessor, sectionRef, blockRef, worldX, worldY, worldZ, sources);
            }
        }
        
        return ArcaneSection.BlockTickStrategy.PROCESSED;
    }
}
