package com.arcanerelay.util;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Utility for block-related operations, e.g. finding the main block for multi-block structures.
 */
public final class BlockUtil {

    private BlockUtil() { }

    /**
     * For a position that may be part of a multi-block structure (e.g. door filler), finds the main block.
     * The main block is the non-filler block that has state (e.g. door part, or block with InteractionState).
     *
     * @param world the world
     * @param chunk the chunk containing the block
     * @param x     block X
     * @param y     block Y
     * @param z     block Z
     * @return [mainX, mainY, mainZ] or null if not found. Returns the same position if it is already the main block.
     */
    @Nullable
    public static int[] findMainBlock(@Nonnull World world, @Nonnull WorldChunk chunk, int x, int y, int z) {
        if (chunk.getFiller(x, y, z) == 0) {
            return new int[]{x, y, z};
        }

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;

                    int tx = x + dx;
                    int ty = y + dy;
                    int tz = z + dz;
                    WorldChunk tChunk = world.getChunk(ChunkUtil.indexChunkFromBlock(tx, tz));

                    if (tChunk == null) continue;
                    if (tChunk.getFiller(tx, ty, tz) != 0) continue;

                    BlockType tBt = tChunk.getBlockType(tx, ty, tz);
                    if (tBt == null) continue;

                    if (tBt.isDoor() || tBt.getStateForBlock(tBt) != null) {
                        return new int[]{tx, ty, tz};
                    }
                }
            }
        }

        return null;
    }
}
