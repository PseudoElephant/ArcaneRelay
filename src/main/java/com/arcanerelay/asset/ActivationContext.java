package com.arcanerelay.asset;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Context passed to {@link Activation#execute} when an arcane block is activated.
 */
public record ActivationContext(
    @Nonnull World world,
    @Nonnull Store<ChunkStore> store,
    @Nonnull WorldChunk chunk,
    int blockX,
    int blockY,
    int blockZ,
    @Nonnull BlockType blockType,
    @Nonnull List<int[]> sources
) {
    /** Each source is [sx, sy, sz] - the block position that sent the signal. */
}
