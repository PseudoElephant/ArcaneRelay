package com.arcanerelay.core.blockmovement;

import com.arcanerelay.ArcaneRelayPlugin;
import com.arcanerelay.state.ArcaneMoveState.MoveEntry;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

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
                WorldChunk futureChunk = world.getChunk(futureChunkIndex);
                if (futureChunk == null)
                    continue;

                long fromChunkIndex = ChunkUtil.indexChunkFromBlock(blockPosition.x, blockPosition.z);
                WorldChunk fromChunk = world.getChunk(fromChunkIndex);
                if (fromChunk == null)
                    continue;

                world.execute(() -> {
                    List<Vector3i> targetsAtSource = targetPositionGraph.get(blockPosition);
                    boolean noOneMovingHere = targetsAtSource == null || targetsAtSource.isEmpty();
                    if (noOneMovingHere) {

                        int settings = 0;
                        fromChunk.breakBlock(
                            blockPosition.x,
                            blockPosition.y,
                            blockPosition.z,
                            moveEntry.blockFiller,
                            4 | 2048); // set empty // naturally removed? // drop item??
                        dirtyChunks.add(fromChunkIndex);
                    }

                    futureChunk.setBlock(
                        tx, ty, tz,
                        moveEntry.blockId,
                        moveEntry.blockType,
                        moveEntry.blockRotation,
                        moveEntry.blockFiller,
                        4); // 

                    futureChunk.setState(tx, ty, tz, moveEntry.componentHolder);

                    dirtyChunks.add(futureChunkIndex);

                    setBlockAndNeighboursTicking(world, fromChunk, blockPosition);
                });
            }
        }

        world.execute(() ->{
            dirtyChunks.forEach(idx -> world.getChunkLighting().invalidateLightInChunk(world.getChunk(idx)));
            dirtyChunks.forEach(idx -> world.getNotificationHandler().updateChunk(idx));
        });
                
        ArcaneRelayPlugin.get().getLogger().atInfo().log("BlockMovementExecutor: finished moving blocks");
    }

    private static void setBlockAndNeighboursTicking(World world, WorldChunk chunk, Vector3i blockPosition) {
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    ArcaneRelayPlugin.get().getLogger().atInfo().log("Set block ticking: (" + blockPosition.x + x + ", " + blockPosition.y + y + ", " + blockPosition.z + z + ")");

                    if (!ChunkUtil.isSameChunkSection(blockPosition.x, blockPosition.y, blockPosition.z, blockPosition.x + x,blockPosition.y + y, blockPosition.z + z)) {
                        Store<ChunkStore> store= world.getChunkStore().getStore();
                        long fromChunkIndex = ChunkUtil.indexChunkFromBlock(blockPosition.x, blockPosition.z);
                        WorldChunk newChunk = world.getChunk(fromChunkIndex);
                        BlockChunk blockChunkComponent = store.getComponent(newChunk.getReference(), BlockChunk.getComponentType());
                        BlockSection section = blockChunkComponent.getSectionAtBlockY(blockPosition.y + y);
                        section.setTicking(blockPosition.x + x, blockPosition.y + y, blockPosition.z + z, true);
                        continue;
                    }
                    
                    Store<ChunkStore> store= world.getChunkStore().getStore();
                    BlockChunk blockChunkComponent = store.getComponent(chunk.getReference(), BlockChunk.getComponentType());
                    BlockSection section = blockChunkComponent.getSectionAtBlockY(blockPosition.y + y);
                
                    section.setTicking(blockPosition.x + x, blockPosition.y + y, blockPosition.z + z, true);
                }
            }
        }
    }
}
