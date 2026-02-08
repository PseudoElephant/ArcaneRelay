package com.arcanerelay.systems;

import com.arcanerelay.ArcaneRelayPlugin;
import com.arcanerelay.core.activation.ActivationWave;
import com.arcanerelay.core.blockmovement.BlockMovementExecutor;
import com.arcanerelay.state.ArcaneMoveState;
import com.arcanerelay.state.ArcaneMoveState.MoveEntry;
import com.arcanerelay.state.ArcaneState;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.DelayedSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import java.util.HashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ArcaneTickSystem extends DelayedSystem<ChunkStore> {
    private static final float DEFAULT_TICK_INTERVAL_SECONDS = 0.25f;

    public ArcaneTickSystem() {
        super(DEFAULT_TICK_INTERVAL_SECONDS);
    }

    public static void requestSignal(@Nonnull World world, int x, int y, int z) {
        ArcaneState state = world.getChunkStore().getStore().getResource(ArcaneState.getResourceType());
        if (state != null) {
            state.addTrigger(x, y, z);
        }
    }

    public static void requestSignal(@Nonnull World world, int x, int y, int z, int sourceX, int sourceY, int sourceZ) {
        ArcaneState state = world.getChunkStore().getStore().getResource(ArcaneState.getResourceType());
        if (state != null) {
            state.addTrigger(x, y, z, sourceX, sourceY, sourceZ);
        }
    }

    public static void requestSignalNextTick(@Nonnull World world, int x, int y, int z) {
        requestSignalNextTick(world, x, y, z, x, y, z);
    }

    public static void requestSignalNextTick(@Nonnull World world, int x, int y, int z, int sourceX, int sourceY,
            int sourceZ) {
        ArcaneState state = world.getChunkStore().getStore().getResource(ArcaneState.getResourceType());
        if (state != null) {
            state.addPendingNextTick(x, y, z, sourceX, sourceY, sourceZ);
        }
    }

    public static void requestSignalNextTick(@Nonnull World world, int x, int y, int z, int sourceX, int sourceY,
            int sourceZ, @Nullable String activatorId) {
        ArcaneState state = world.getChunkStore().getStore().getResource(ArcaneState.getResourceType());
        if (state != null) {
            state.addPendingNextTickWithActivator(x, y, z, sourceX, sourceY, sourceZ, activatorId);
        }
    }

    @Override
    public void delayedTick(float dt, int index, Store<ChunkStore> store) {
        World world = store.getExternalData().getWorld();

        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        ArcaneState state = chunkStore.getResource(ArcaneState.getResourceType());
        if (state == null)
            return;

        state.flushPendingToTriggers();
        if (!state.hasTriggers())
            return;

        ArcaneMoveState arcaneMoveState = world.getChunkStore().getStore().getResource(ArcaneMoveState.getResourceType());
        if (arcaneMoveState == null) {
            ArcaneRelayPlugin.get().getLogger().atInfo().log("ArcaneTickSystem: no arcane move state");
            return;
        }

        HashMap<Vector3i, MoveEntry> moveEntries = arcaneMoveState.getMoveEntries();

        ActivationWave.runWave(world, chunkStore, state);
        world.execute(() -> {
            BlockMovementExecutor.execute(world, moveEntries);
        }); 

        arcaneMoveState.clear();
    }
}
