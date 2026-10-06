package com.pseudoelephant.arcanerelay.features.blockmovement.systems;

import java.util.Set;

import javax.annotation.Nonnull;

import com.hypixel.hytale.builtin.blocktick.system.ChunkBlockTickSystem;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.pseudoelephant.arcanerelay.features.blockmovement.BlockMovementExecutor;
import com.pseudoelephant.arcanerelay.features.blockmovement.resources.ArcaneMoveState;

public class BlockMovementSystem extends TickingSystem<ChunkStore> {
    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull Store<ChunkStore> store
    ) {
        ArcaneMoveState moveState = store.getResource(ArcaneMoveState.getResourceType());
        if (moveState == null) return;

        var entries = moveState.getMoveEntries();
        if (entries.isEmpty()) return;

        var world = store.getExternalData().getWorld();
        if (world == null) return;

        // here it's safe to use world.execute() as we are not using the command buffer because it's running on the main thread
        BlockMovementExecutor.execute(world, entries);
        moveState.clear();
    }
    
    @SuppressWarnings("null")
    @Nonnull
    private static final Set<Dependency<ChunkStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.BEFORE, ChunkBlockTickSystem.Ticking.class)
    );

    @Nonnull
    @Override
    public Set<Dependency<ChunkStore>> getDependencies() {
        return DEPENDENCIES;
    }
}
