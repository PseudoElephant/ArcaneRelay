package com.arcanerelay.core.blockmovement;

import com.arcanerelay.ArcaneRelayPlugin;
import com.arcanerelay.state.ArcaneMoveState.MoveEntry;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

/**
 * Executes block moves from a map of move entries: builds execution order via
 * {@link BlockMovementGraph}, then breaks/sets blocks and invalidates lighting.
 */
public final class BlockMovementExecutor {

    private BlockMovementExecutor() {
    }

    /**
     * Runs all moves for the given move entries: computes order, breaks source
     * blocks and sets destination blocks, then invalidates light and notifies
     * chunks for all affected chunks.
     */
    public static void execute(
            @Nonnull World world,
            @Nonnull Map<Vector3i, MoveEntry> moveEntries) {
        if (moveEntries.isEmpty())
            return;

        ArcaneRelayPlugin.get().getLogger().atInfo().log("BlockMovementExecutor: moving blocks");
        List<List<Vector3i>> executionOrder = BlockMovementGraph.getExecutionOrder(moveEntries);
        Map<Vector3i, List<Vector3i>> targetPositionGraph = BlockMovementGraph.buildTargetPositionGraph(moveEntries);

        ArcaneRelayPlugin.get().getLogger().atInfo()
                .log("BlockMovementExecutor: execution steps: " + executionOrder.size());

        LongSet dirtyChunks = new LongOpenHashSet();
        for (List<Vector3i> step : executionOrder) {
            for (Vector3i blockPosition : step) {
                MoveEntry moveEntry = moveEntries.get(blockPosition);
                if (moveEntry == null)
                    continue;

                int tx = blockPosition.x + moveEntry.moveDirection.x;
                int ty = blockPosition.y + moveEntry.moveDirection.y;
                int tz = blockPosition.z + moveEntry.moveDirection.z;

                long futureChunkIndex = ChunkUtil.indexChunkFromBlock(tx, tz);
                WorldChunk futureChunk = world.getChunkIfInMemory(futureChunkIndex);
                if (futureChunk == null)
                    continue;

                long fromChunkIndex = ChunkUtil.indexChunkFromBlock(blockPosition.x, blockPosition.z);
                WorldChunk fromChunk = world.getChunkIfInMemory(fromChunkIndex);
                if (fromChunk == null)
                    continue;

                world.execute(() -> {
                    List<Vector3i> targetsAtSource = targetPositionGraph.get(blockPosition);
                    boolean noOneMovingHere = targetsAtSource == null || targetsAtSource.isEmpty();
                    if (noOneMovingHere) {
                        fromChunk.breakBlock(
                            blockPosition.x,
                            blockPosition.y,
                            blockPosition.z,
                            moveEntry.blockFiller,
                            moveEntry.blockSettings);
                        dirtyChunks.add(fromChunkIndex);
                    }

                    futureChunk.setBlock(
                        tx, ty, tz,
                        moveEntry.blockId,
                        moveEntry.blockType,
                        moveEntry.blockRotation,
                        moveEntry.blockFiller,
                        moveEntry.blockSettings);
                    dirtyChunks.add(futureChunkIndex);
                });
            }
        }

        world.execute(() ->{
            ArcaneRelayPlugin.get().getLogger().atInfo()
            .log("BlockMovementExecutor: invalidating light for " + dirtyChunks.size() + " chunks");
            dirtyChunks.forEach(idx -> world.getChunkLighting().invalidateLightInChunk(world.getChunkIfInMemory(idx)));
            dirtyChunks.forEach(idx -> world.getNotificationHandler().updateChunk(idx));
        });
                
        ArcaneRelayPlugin.get().getLogger().atInfo().log("BlockMovementExecutor: finished moving blocks");
    }
}
