package com.arcanerelay.systems;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.arcanerelay.ArcaneRelayPlugin;
import com.arcanerelay.components.ArcaneMoveBlock;
import com.arcanerelay.state.ArcaneMoveState;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.ModelTransform;
import com.hypixel.hytale.protocol.packets.player.ClientTeleport;
import com.hypixel.hytale.server.core.asset.type.blocktick.BlockTickStrategy;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.CollisionResultComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.PositionUtil;

public class ArcaneMoveBlockResolutionSystem extends EntityTickingSystem<ChunkStore> {
  public void tick(float dt, int index, @Nonnull ArchetypeChunk archetypeChunk, @Nonnull Store store, @Nonnull CommandBuffer commandBuffer) {
    BlockSection blocks = (BlockSection) archetypeChunk.getComponent(index, BlockSection.getComponentType());
    assert blocks != null;

    if (blocks.getTickingBlocksCountCopy() == 0) return;
    
    ChunkSection section = (ChunkSection) archetypeChunk.getComponent(index, ChunkSection.getComponentType());
    assert section != null;

    BlockComponentChunk blockComponentChunk = (BlockComponentChunk) commandBuffer.getComponent(section.getChunkColumnReference(), BlockComponentChunk.getComponentType());
    assert blockComponentChunk != null;
    
    blocks.forEachTicking(blockComponentChunk, commandBuffer, section.getY(), (blockComponentChunk1, commandBuffer1, localX, localY, localZ, blockId) -> {
      Ref<ChunkStore> blockRef = blockComponentChunk.getEntityReference(ChunkUtil.indexBlockInColumn(localX, localY, localZ));
      if (blockRef == null) {
        return BlockTickStrategy.IGNORED;
      } 

      ArcaneMoveBlock arcaneMoveBlock = (ArcaneMoveBlock) commandBuffer1.getComponent(blockRef, ArcaneMoveBlock.getComponentType());
      if (arcaneMoveBlock == null) {
        return BlockTickStrategy.IGNORED;
      }

      ArcaneRelayPlugin.get().getLogger().atInfo().log("HANDLING TICKING MOVE BLOCKS");

      WorldChunk worldChunk = (WorldChunk) commandBuffer.getComponent(section.getChunkColumnReference(), WorldChunk.getComponentType());
      World world = worldChunk.getWorld();
      int globalX = localX + (worldChunk.getX() * 32);
      int globalZ = localZ + (worldChunk.getZ() * 32);
      
      // We need to execute setBlock on the world thread as you cannot call store functions from a system
      // This is because of the architecture of the server, depending on your needs you can also use the CommandBuffer
    
      BlockType blockType = BlockType.getAssetMap().getAsset(blockId);


      world.execute(() -> {
        ArcaneRelayPlugin.get().getLogger().atInfo().log("MOVING BLOCK");

      
        // world.setBlock(globalX - arcaneMoveBlock.moveDirection.x, localY - arcaneMoveBlock.moveDirection.y, globalZ - arcaneMoveBlock.moveDirection.z, blockType.getId());
        // worldChunk.hol(globalX, localY, globalZ);
        // world.breakBlock(globalX, localY, globalZ, 0);
        // commandBuffer1.removeComponent(blockRef, ArcaneMoveBlock.getComponentType());
      });

      return BlockTickStrategy.CONTINUE;
    });
        
    // Check move dir

    // If move dir block is empty then:
    // Check other block neighbors (excluding origin of movement) to see if anything is schedule to move here. 
      // If yes: block
      // If no: move block

    // Else, check if block in block move direction is itself an ArcaneMoveBlock
      // If not: block this ArcaneBlock from moving in direction is not itslef an ArcaneMoveBlock
      // If yes: resolve that block move first and see if space is free for movement
  }
  
  @Nullable
  public Query getQuery() {
      return Query.and(BlockSection.getComponentType(), ChunkSection.getComponentType());
  }
}
