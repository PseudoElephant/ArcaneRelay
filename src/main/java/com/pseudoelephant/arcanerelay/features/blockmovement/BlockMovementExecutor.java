package com.pseudoelephant.arcanerelay.features.blockmovement;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.pseudoelephant.arcanerelay.features.blockmovement.resources.ArcaneMoveState.MoveEntry;
import com.pseudoelephant.arcanerelay.util.BlockUtil;

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

    private BlockMovementExecutor() { }

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

        List<List<Vector3i>> executionOrder = BlockMovementGraph.getExecutionOrder(moveEntries);
        Map<Vector3i, List<Vector3i>> targetPositionGraph = BlockMovementGraph.buildTargetPositionGraph(moveEntries);

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
                if (world.getChunkStore().getChunkReference(futureChunkIndex) == null)
                    continue;

                long fromChunkIndex = ChunkUtil.indexChunkFromBlock(blockPosition.x, blockPosition.z);
                if (world.getChunkStore().getChunkReference(fromChunkIndex) == null)
                    continue;

                world.execute(() -> {
                    Store<ChunkStore> store = world.getChunkStore().getStore();
                    List<Vector3i> targetsAtSource = targetPositionGraph.get(blockPosition);
                    boolean noOneMovingHere = targetsAtSource == null || targetsAtSource.isEmpty();
                    if (noOneMovingHere) {

                        BlockUtil.clearBlock(
                            store,
                            blockPosition.x,
                            blockPosition.y,
                            blockPosition.z,
                            moveEntry.blockFiller,
                            4 | 2048); // set empty // naturally removed? // drop item??
                        dirtyChunks.add(fromChunkIndex);
                    }

                    BlockUtil.setBlock(
                        store,
                        tx, ty, tz,
                        moveEntry.blockId,
                        moveEntry.blockType,
                        moveEntry.blockRotation,
                        moveEntry.blockFiller,
                        4
                    );

                    BlockUtil.setBlockEntity(store, tx, ty, tz, moveEntry.blockType, moveEntry.blockRotation, moveEntry.componentHolder);

                    dirtyChunks.add(futureChunkIndex);

                    BlockUtil.performBlockUpdate(store, blockPosition.x, blockPosition.y, blockPosition.z);
                });
            }
        }

        world.execute(() -> {
            dirtyChunks.forEach(idx -> {
                ChunkStore chunckStore = world.getChunkStore();
                world.getChunkLighting().invalidateLightInChunkSections(chunckStore, ChunkUtil.xOfChunkIndex(idx), ChunkUtil.zOfChunkIndex(idx), ChunkUtil.MIN_SECTION, ChunkUtil.MIN_SECTION + ChunkUtil.HEIGHT_SECTIONS);
            });

            dirtyChunks.forEach(idx -> world.getNotificationHandler().updateChunk(idx));
        });
    }
}
