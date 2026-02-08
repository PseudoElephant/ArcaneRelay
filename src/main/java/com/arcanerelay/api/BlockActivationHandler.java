package com.arcanerelay.api;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;

/**
 * Handler for arcane block activation. Called by the Arcane Tick System when a signal reaches a block.
 * Unlike player interactions, these run independently without requiring a nearby player.
 * <p>
 * Register via {@link com.arcanerelay.ArcaneRelayPlugin#registerBlockActivationHandler}.
 */
@FunctionalInterface
public interface BlockActivationHandler {

   /**
    * Called when an arcane signal activates this block.
    *
    * @param world       the world
    * @param store       the chunk store for component access
    * @param commandBuffer command buffer for deferred operations
    * @param chunk       the chunk containing the block
    * @param blockX      block X coordinate
    * @param blockY      block Y coordinate
    * @param blockZ      block Z coordinate
    * @param blockType   the block type at this position
    */
   void onActivate(
      @Nonnull World world,
      @Nonnull Store<ChunkStore> store,
      @Nonnull WorldChunk chunk,
      int blockX,
      int blockY,
      int blockZ,
      @Nonnull BlockType blockType
   );
}
