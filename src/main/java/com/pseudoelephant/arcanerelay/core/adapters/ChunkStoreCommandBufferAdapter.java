package com.pseudoelephant.arcanerelay.core.adapters;

import java.util.function.Consumer;
import javax.annotation.Nonnull;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public final class ChunkStoreCommandBufferAdapter 
        extends AbstractComponentAccessorAdapter<ChunkStore> 
        implements ChunkStoreCommandBufferLike {

    public ChunkStoreCommandBufferAdapter(@Nonnull CommandBuffer<ChunkStore> buffer) {
        super(buffer);
    }

    @Override
    public void run(@Nonnull Consumer<Store<ChunkStore>> consumer) {
        ((CommandBuffer<ChunkStore>) delegate).run(consumer);
    }
}