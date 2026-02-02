package com.arcanerelay.systems;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.arcanerelay.ArcaneRelayPlugin;
import com.arcanerelay.components.ArcaneTriggerBlock;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public class ArcaneOnPlaceSystem extends EntityEventSystem<ChunkStore, PlaceBlockEvent> {
    public ArcaneOnPlaceSystem() {
        super(PlaceBlockEvent.class);
    }

    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk,
        @Nonnull Store<ChunkStore> store,
        @Nonnull CommandBuffer<ChunkStore> commandBuffer,
        @Nonnull PlaceBlockEvent event
    ) {
        Vector3i target = event.getTargetBlock();
        ArcaneTickSystem.requestSignal(commandBuffer.getExternalData().getWorld(), target.getX(), target.getY(), target.getZ(), target.getX(), target.getY(), target.getZ());
    }

    @Override
    @Nullable
    public Query<ChunkStore> getQuery() {
        return Query.and(ArcaneRelayPlugin.get().getArcaneTriggerBlockComponentType());
    }
}
