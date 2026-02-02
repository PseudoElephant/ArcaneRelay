package com.arcanerelay.systems;

import com.arcanerelay.ArcaneRelayPlugin;
import com.arcanerelay.asset.Activation;
import com.arcanerelay.asset.ActivationExecutor;
import com.arcanerelay.components.ArcaneTriggerBlock;
import com.arcanerelay.state.ArcaneState;
import com.arcanerelay.state.TriggerEntry;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkColumn;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.arcanerelay.api.BlockActivationHandler;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Arcane tick system: each tick interval, run one level of the graph.
 * Process current state (activate output for each block), collect next outputs, then clear state and set state to next.
 * One level per tick; we always clear before adding.
 * 
 * 
 * Probably a better way to run tick system every x seonds for currently active blocks. Another option would be using tick procedure
 * on the blocks.
 */
public class ArcaneTickSystem extends EntityTickingSystem<ChunkStore> {

   private static final double DEFAULT_TICK_INTERVAL_SECONDS = 1.0;

   /** Enqueue an arcane trigger at (x,y,z) for the next wave. Call from tick processing (e.g. ActivationExecutor). */
   public static void requestSignal(@Nonnull World world, int x, int y, int z) {
      ArcaneState state = world.getChunkStore().getStore().getResource(ArcaneState.getResourceType());
      if (state != null) {
         state.addTrigger(x, y, z);
      }
   }

   /** Enqueue an arcane trigger at (x,y,z) with source (sourceX, sourceY, sourceZ). */
   public static void requestSignal(@Nonnull World world, int x, int y, int z, int sourceX, int sourceY, int sourceZ) {
      ArcaneState state = world.getChunkStore().getStore().getResource(ArcaneState.getResourceType());
      if (state != null) {
         state.addTrigger(x, y, z, sourceX, sourceY, sourceZ);
      }
   }

   /**
    * Enqueue an arcane trigger for the next tick (from interactions).
    * Signals are flushed into the main queue at the start of each tick, ensuring
    * interaction-sourced signals are processed on the next tick, not the current one.
    */
   public static void requestSignalNextTick(@Nonnull World world, int x, int y, int z) {
      requestSignalNextTick(world, x, y, z, x, y, z);
   }

   /** Enqueue an arcane trigger for the next tick with source (sourceX, sourceY, sourceZ). */
   public static void requestSignalNextTick(@Nonnull World world, int x, int y, int z, int sourceX, int sourceY, int sourceZ) {
      ArcaneState state = world.getChunkStore().getStore().getResource(ArcaneState.getResourceType());
      if (state != null) {
         state.addPendingNextTick(x, y, z, sourceX, sourceY, sourceZ);
      }
   }

   @Nonnull
   private static final Query<ChunkStore> QUERY = Query.and(
      ChunkColumn.getComponentType(),
      WorldChunk.getComponentType()
   );

   @Nonnull
   private static final Set<Dependency<ChunkStore>> DEPENDENCIES = Set.of(
      new SystemDependency<>(Order.AFTER, com.hypixel.hytale.builtin.blocktick.system.ChunkBlockTickSystem.PreTick.class),
      new SystemDependency<>(Order.BEFORE, com.hypixel.hytale.builtin.blocktick.system.ChunkBlockTickSystem.Ticking.class)
   );

   @Override
   public void tick(
      float dt,
      int index,
      @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk,
      @Nonnull Store<ChunkStore> store,
      @Nonnull CommandBuffer<ChunkStore> commandBuffer
   ) {
      World world = commandBuffer.getExternalData().getWorld();
      Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
      ArcaneState state = chunkStore.getResource(ArcaneState.getResourceType());
      if (state == null) return;

      state.flushPendingToTriggers();
      if (!state.hasTriggers()) return;

      long worldTick = world.getTick();
      long intervalTicks = Math.max(1L, Math.round(DEFAULT_TICK_INTERVAL_SECONDS * world.getTps()));
      if (!state.tryClaimRun(worldTick, intervalTicks)) {
         return;
      }
      runWave(world, chunkStore, commandBuffer, state);
   }

   private static void runWave(
      @Nonnull World world,
      @Nonnull Store<ChunkStore> store,
      @Nonnull CommandBuffer<ChunkStore> commandBuffer,
      @Nonnull ArcaneState state
   ) {
      List<TriggerEntry> entries = state.copyTriggerEntries();
      state.clearTriggers();
      Map<PosKey, TargetInfo> targets = new HashMap<>();
      for (TriggerEntry e : entries) {
         PosKey key = new PosKey(e.target().getX(), e.target().getY(), e.target().getZ());
         TargetInfo info = targets.computeIfAbsent(key, k -> new TargetInfo());
         info.sources.add(e.source());
         if (e.skip()) info.skip = true;
      }

      for (Map.Entry<PosKey, TargetInfo> entry : targets.entrySet()) {
         int x = entry.getKey().x;
         int y = entry.getKey().y;
         int z = entry.getKey().z;
         TargetInfo info = entry.getValue();

         long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
         WorldChunk chunk = world.getChunk(chunkIndex);

         int blockId = chunk.getBlock(x, y, z);
         BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
         if (blockType == null) {
            ArcaneRelayPlugin.get().getLogger().atWarning().log("ArcaneTickSystem: block type not found at " + x + ", " + y + ", " + z);
            continue;
         };

         if (info.skip) {
            Ref<ChunkStore> blockRef = chunk.getBlockComponentEntity(x, y, z);
            if (blockRef == null) {
               blockRef = BlockModule.ensureBlockEntity(chunk, x, y, z);
            }

            propagateOnly(world, store, chunk, blockRef, new Vector3i(x, y, z));
         } else {
            List<int[]> sourcesAsInts = new ArrayList<>(info.sources.size());
            for (Vector3i s : info.sources) {
               sourcesAsInts.add(new int[]{s.getX(), s.getY(), s.getZ()});
            }
            activateOutput(world, store, commandBuffer, chunk, x, y, z, blockId, blockType, sourcesAsInts);
         }
      }
   }

   private static final class TargetInfo {
      final List<Vector3i> sources = new ArrayList<>();
      boolean skip;
   }

   /** When skip=true: only propagate to outputs, do not activate the block. */
   private static void propagateOnly(
      @Nonnull World world,
      @Nonnull Store<ChunkStore> store,
      @Nonnull WorldChunk chunk,
      @Nonnull Ref<ChunkStore> blockRef,
      @Nonnull Vector3i blockPos
   ) {
      ArcaneTriggerBlock trigger = store.getComponent(blockRef, ArcaneRelayPlugin.get().getArcaneTriggerBlockComponentType());
      if (trigger == null) return;
      ArcaneState state = world.getChunkStore().getStore().getResource(ArcaneState.getResourceType());
      if (state == null) return;
      for (Vector3i out : trigger.getOutputPositions()) {
         state.addTrigger(TriggerEntry.of(out, blockPos));
      }
   }

   /**
    * Activate the output of this arcane block.
    * First tries asset-based Activation from {@link com.arcanerelay.asset.ActivationRegistry};
    * if none, dispatches to the handler registered via
    * {@link ArcaneRelayPlugin#registerBlockActivationHandler}.
    *
    * @param sources list of [sx,sy,sz] positions that triggered this block (for unique-source logic)
    */
   protected static void activateOutput(
      @Nonnull World world,
      @Nonnull Store<ChunkStore> store,
      @Nonnull CommandBuffer<ChunkStore> commandBuffer,
      @Nonnull WorldChunk chunk,
      int blockX,
      int blockY,
      int blockZ,
      int blockId,
      @Nonnull BlockType blockType,
      @Nonnull List<int[]> sources
   ) {
      String blockTypeKey = blockType.getId();
      Activation activation = ArcaneRelayPlugin.get().getActivationRegistry().getActivationForBlock(blockTypeKey);
      if (activation != null) {
         world.execute(
            () -> ActivationExecutor.execute(world, store, chunk, blockX, blockY, blockZ, blockType, activation, sources)
         );
         return;
      }

      BlockActivationHandler handler = ArcaneRelayPlugin.get().getBlockActivationHandler(blockId, blockTypeKey);
      if (handler != null) {
         world.execute(
            () ->
               handler.onActivate(world, store, commandBuffer, chunk, blockX, blockY, blockZ, blockType)
         );
      } else {
         ArcaneRelayPlugin.get().getLogger().atWarning().log("ArcaneTickSystem: no block activation handler for block: " + blockX + ", " + blockY + ", " + blockZ + " key: " + blockTypeKey);
      }
   }

   private static final class PosKey {
      final int x, y, z;

      PosKey(int x, int y, int z) {
         this.x = x;
         this.y = y;
         this.z = z;
      }

      @Override
      public int hashCode() {
         return 31 * (31 * x + y) + z;
      }

      @Override
      public boolean equals(Object obj) {
         if (this == obj) return true;
         if (!(obj instanceof PosKey other)) return false;
         return x == other.x && y == other.y && z == other.z;
      }
   }

   @Nullable
   @Override
   public Query<ChunkStore> getQuery() {
      return QUERY;
   }

   @Nonnull
   @Override
   public Set<Dependency<ChunkStore>> getDependencies() {
      return DEPENDENCIES;
   }
}
