package com.pseudoelephant.arcanerelay.core.adapters;

import java.util.function.Consumer;
import javax.annotation.Nonnull;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Wraps Store so it can be used as ChunkStoreCommandBufferLike from interactions; run() executes immediately. */
public final class EntityStoreChunkStoreAdapter 
        extends AbstractComponentAccessorAdapter<ChunkStore> 
        implements ChunkStoreCommandBufferLike {

    private final CommandBuffer<EntityStore> entityStore;

    public EntityStoreChunkStoreAdapter(@Nonnull CommandBuffer<EntityStore> store) {
        super(store.getExternalData().getWorld().getChunkStore().getStore());
        this.entityStore = store;
    }

    @Override
    public void run(@Nonnull Consumer<Store<ChunkStore>> consumer) {
        entityStore.run((_) -> {
            consumer.accept((Store<ChunkStore>) delegate);
        });
    }
}