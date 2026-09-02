package com.pseudoelephant.arcanerelay.core.activation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.pseudoelephant.arcanerelay.core.adapters.ChunkStoreCommandBufferLike;
import com.pseudoelephant.arcanerelay.features.signal.components.ArcaneSection;

/**
 * Accessor for activation execution. Same pattern as
 * {@link com.hypixel.hytale.server.core.asset.type.fluid.FluidTicker.Accessor}: section components
 * by chunk coords. Block-specific data (refs, coords, sources) is passed to
 * {@link com.pseudoelephant.arcanerelay.features.activation.Activation#execute} individually.
 */
public interface ArcaneActivationAccessor {
    @Nonnull ChunkStoreCommandBufferLike getCommandBuffer();
    
    @Nullable ArcaneSection getArcaneSection(int cx, int cy, int cz);
    @Nullable default ArcaneSection getArcaneSectionByBlock(int bx, int by, int bz) {
        return getArcaneSection(
            ChunkUtil.chunkCoordinate(bx),
            ChunkUtil.chunkCoordinate(by),
            ChunkUtil.chunkCoordinate(bz));
    }

    @Nullable BlockSection getBlockSection(int cx, int cy, int cz);
    @Nullable default BlockSection getBlockSectionByBlock(int bx, int by, int bz) {
        return getBlockSection(
            ChunkUtil.chunkCoordinate(bx),
            ChunkUtil.chunkCoordinate(by),
            ChunkUtil.chunkCoordinate(bz));
    }

    @Nullable ChunkSection getChunkSection(int cx, int cy, int cz);
    @Nullable default ChunkSection getChunkSectionByBlock(int bx, int by, int bz) {
        return getChunkSection(
            ChunkUtil.chunkCoordinate(bx),
            ChunkUtil.chunkCoordinate(by),
            ChunkUtil.chunkCoordinate(bz));
    }

    @Nullable default BlockType getBlockType(int worldX, int worldY, int worldZ) {
        BlockSection s = getBlockSectionByBlock(worldX, worldY, worldZ);
        if (s == null) return null;
        int blockId = s.get(worldX, worldY, worldZ);
        return BlockType.getAssetMap().getAsset(blockId);
    }
}
