package com.arcanerelay.core.blockmovement;

import com.arcanerelay.ArcaneRelayPlugin;
import com.arcanerelay.components.ArcaneTriggerBlock;
import com.arcanerelay.resources.ArcaneMoveState.MoveEntry;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
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
                WorldChunk futureChunk = world.getChunk(futureChunkIndex);
                if (futureChunk == null)
                    continue;

                long fromChunkIndex = ChunkUtil.indexChunkFromBlock(blockPosition.x, blockPosition.z);
                WorldChunk fromChunk = world.getChunk(fromChunkIndex);
                if (fromChunk == null)
                    continue;

                world.execute(() -> {
                    MoveEntry updatedEntry = moveArcaneTriggerRelativeOutputs(world, fromChunk, blockPosition, moveEntry);

                    List<Vector3i> targetsAtSource = targetPositionGraph.get(blockPosition);
                    boolean noOneMovingHere = targetsAtSource == null || targetsAtSource.isEmpty();
                    if (noOneMovingHere) {

                        int settings = 0;
                        fromChunk.breakBlock(
                            blockPosition.x,
                            blockPosition.y,
                            blockPosition.z,
                            updatedEntry.blockFiller,
                            4 | 2048); // set empty // naturally removed? // drop item??
                        dirtyChunks.add(fromChunkIndex);
                    }

                    futureChunk.setBlock(
                        tx, ty, tz,
                        updatedEntry.blockId,
                        updatedEntry.blockType,
                        updatedEntry.blockRotation,
                        updatedEntry.blockFiller,
                        4
                    ); 

                    futureChunk.setState(tx, ty, tz, updatedEntry.blockType, updatedEntry.blockRotation, updatedEntry.componentHolder);

                    dirtyChunks.add(futureChunkIndex);

                    setBlockAndNeighboursTicking(world, fromChunk, blockPosition);
                });
            }
        }

        world.execute(() -> {
            dirtyChunks.forEach(idx -> {
                ChunkStore chunckStore = world.getChunkStore();
                WorldChunk worldChunk = world.getChunk(idx);
                world.getChunkLighting().invalidateLightInChunk(chunckStore, worldChunk.getX(), worldChunk.getZ());
            });

            dirtyChunks.forEach(idx -> world.getNotificationHandler().updateChunk(idx));
        });
    }

private static MoveEntry moveArcaneTriggerRelativeOutputs(@Nonnull World world, @Nonnull WorldChunk worldChunk, @Nonnull Vector3i blockPos, @Nonnull MoveEntry moveEntry) {
        ArcaneRelayPlugin.LOGGER.atInfo().log("Attempting to Update ArcanePullerBlock Relative Outputs at: " + blockPos.x + ", " + blockPos.y + ", " + blockPos.z);

        // Use the componentHolder stored in the move entry (captured during chain creation)
        // rather than looking up blockRef from chunk, which may not exist for blocks
        // in the middle of a chain
        Holder<ChunkStore> originalHolder = moveEntry.componentHolder;
        if (originalHolder == null) {
            ArcaneRelayPlugin.LOGGER.atInfo().log("No component holder in move entry");
            return moveEntry;
        }

        ArcaneTriggerBlock triggerBlock = originalHolder.getComponent(ArcaneTriggerBlock.getComponentType());
        if (triggerBlock == null || !triggerBlock.hasOutputPositions()) {
            ArcaneRelayPlugin.LOGGER.atInfo().log("Block is not a trigger block with outputs");
            return moveEntry;
        }

        ArcaneRelayPlugin.LOGGER.atInfo().log("Is trigger block with outputs");

        if (!triggerBlock.isUsingRelativeOutput() || !triggerBlock.hasOutputPositions()) {
            ArcaneRelayPlugin.LOGGER.atInfo().log("Not using relative outputs");
            return moveEntry;
        }

        ArcaneRelayPlugin.LOGGER.atInfo().log("Using relative outputs, moving them");
    
        // Clone the original holder to preserve other components
        Holder<ChunkStore> updatedHolder = originalHolder.clone();
        
        if (updatedHolder == null) return moveEntry;

        ArcaneRelayPlugin.LOGGER.atInfo().log("Holder cloned, updating outputs");

        // Modify the ArcaneTriggerBlock and put it back in the cloned holder
        ArcaneTriggerBlock updated = (ArcaneTriggerBlock) triggerBlock.clone();
        updated.moveOutputPositions(moveEntry.moveDirection);
        updatedHolder.putComponent(ArcaneTriggerBlock.getComponentType(), updated);

        ArcaneRelayPlugin.LOGGER.atInfo().log("Updated block outputs - moved by: " + moveEntry.moveDirection.x + ", " + moveEntry.moveDirection.y + ", " + moveEntry.moveDirection.z);

        return moveEntry.withComponentHolder(updatedHolder);
    }

    private static void setBlockAndNeighboursTicking(World world, WorldChunk chunk, Vector3i blockPosition) {
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
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
