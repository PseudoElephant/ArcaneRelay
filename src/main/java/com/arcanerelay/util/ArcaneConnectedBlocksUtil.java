package com.arcanerelay.util;

import java.util.Optional;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.blocktype.component.BlockPhysics;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkColumn;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.connectedblocks.ConnectedBlocksUtil;
import com.hypixel.hytale.server.core.universe.world.connectedblocks.ConnectedBlocksUtil.ConnectedBlockResult;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public final class ArcaneConnectedBlocksUtil {
    private static final int SETTINGS = 132;

    private ArcaneConnectedBlocksUtil() { }

    /**
     * Updates connected-block state for the current block and up to 2 blocks behind it
     * in the extending direction. Preserves block state (holder) if a block type swap occurs.
     */
    public static void updateCurrentAndPrevious(
        @Nonnull Store<ChunkStore> store,
        @Nonnull World world,
        @Nonnull Vector3i blockPos,
        @Nonnull Vector3i extendDir,
        @Nonnull RotationTuple rotation
    ) {
        updateSingle(store, world, blockPos, extendDir, rotation);
        Vector3i back = new Vector3i(extendDir).mul(-1);
        Vector3i prev = new Vector3i(blockPos).add(back);

        WorldChunk prevChunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(prev.x, prev.z));
        if (prevChunk == null) return;
        int prevRotationIndex = prevChunk.getRotationIndex(prev.x, prev.y, prev.z);
        RotationTuple prevRotation = RotationTuple.get(prevRotationIndex);

        updateSingle(store, world, prev, extendDir, prevRotation);
        prev.add(back);
        WorldChunk prevChunk2 = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(prev.x, prev.z));
        if (prevChunk2 == null) return;
        int prevRotationIndex2 = prevChunk2.getRotationIndex(prev.x, prev.y, prev.z);
        RotationTuple prevRotation2 = RotationTuple.get(prevRotationIndex2);
        updateSingle(store, world, prev, extendDir, prevRotation2);
        // update 1 forward
        Vector3i forward = new Vector3i(extendDir);
        Vector3i next = new Vector3i(blockPos).add(forward);
        WorldChunk nextChunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(next.x, next.z));
        if (nextChunk == null) return;
        int nextRotationIndex = nextChunk.getRotationIndex(next.x, next.y, next.z);
        RotationTuple nextRotation = RotationTuple.get(nextRotationIndex);
        updateSingle(store, world, next, extendDir, nextRotation);
    }

    private static void updateSingle(
        @Nonnull Store<ChunkStore> store,
        @Nonnull World world,
        @Nonnull Vector3i blockPos,
        @Nonnull Vector3i extendDir,
        @Nonnull RotationTuple rotation
    ) {
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(blockPos.x, blockPos.z));
        if (chunk == null) return;

        BlockChunk blockChunk = store.getComponent(chunk.getReference(), BlockChunk.getComponentType());
        if (blockChunk == null) return;
        BlockSection section = blockChunk.getSectionAtBlockY(blockPos.y);
        if (section == null) return;

        int filler = section.getFiller(blockPos.x, blockPos.y, blockPos.z);
        if (filler != 0) return;

        int blockId = section.get(blockPos.x, blockPos.y, blockPos.z);
        BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
        if (blockType == null || blockType.getConnectedBlockRuleSet() == null) return;

        int rotationIndex = rotation.index();
        Optional<ConnectedBlockResult> desired = ConnectedBlocksUtil.getDesiredConnectedBlockType(
            world,
            new Vector3i(blockPos),
            blockType,
            rotationIndex,
            extendDir,
            true);
        if (desired.isEmpty()) return;

        ConnectedBlockResult result = desired.get();
        if (result.blockTypeKey().equals(blockType.getId()) && result.rotationIndex() == rotationIndex) return;

        int newId = BlockType.getAssetMap().getIndex(result.blockTypeKey());
        BlockType newType = BlockType.getAssetMap().getAsset(newId);
        Holder<ChunkStore> holder = chunk.getBlockComponentHolder(blockPos.x, blockPos.y, blockPos.z);
       
        ChunkColumn column = (ChunkColumn)store.getComponent(chunk.getReference(), ChunkColumn.getComponentType());
        Ref<ChunkStore> sectionRef = column.getSection(ChunkUtil.chunkCoordinate(blockPos.y));

    
        chunk.setBlock(blockPos.x, blockPos.y, blockPos.z, newId, newType, result.rotationIndex(), 0, SETTINGS);
        if (holder != null) {
            chunk.setState(blockPos.x, blockPos.y, blockPos.z, newType, result.rotationIndex(), holder);
        }

        if (blockType.hasSupport()) {
            BlockPhysics.reset(store, sectionRef, blockPos.x, blockPos.y, blockPos.z);
        } else {
            BlockPhysics.clear(store, sectionRef, blockPos.x, blockPos.y, blockPos.z);
        }
        world.performBlockUpdate(blockPos.x, blockPos.y, blockPos.z, true);
    }
}
