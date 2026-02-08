package com.arcanerelay.systems;

import com.arcanerelay.ArcaneRelayPlugin;
import com.arcanerelay.asset.Activation;
import com.arcanerelay.asset.ActivationExecutor;
import com.arcanerelay.components.ArcaneTriggerBlock;
import com.arcanerelay.state.ArcaneMoveState;
import com.arcanerelay.state.ArcaneState;
import com.arcanerelay.state.TriggerEntry;
import com.arcanerelay.state.ArcaneMoveState.MoveEntry;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.DelayedSystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Arcane tick system: each tick interval, run one level of the graph.
 * Process current state (activate output for each block), collect next outputs,
 * then clear state and set state to next.
 * One level per tick; we always clear before adding.
 * 
 * s
 * Probably a better way to run tick system every x seonds for currently active
 * blocks. Another option would be using tick procedure
 * on the blocks.
 */
public class ArcaneTickSystem extends DelayedSystem<ChunkStore> {
   private static final float DEFAULT_TICK_INTERVAL_SECONDS = 0.5f;

   public ArcaneTickSystem() {
      super(DEFAULT_TICK_INTERVAL_SECONDS);
   }

   /**
    * Enqueue an arcane trigger at (x,y,z) for the next wave. Call from tick
    * processing (e.g. ActivationExecutor).
    */
   public static void requestSignal(@Nonnull World world, int x, int y, int z) {
      ArcaneState state = world.getChunkStore().getStore().getResource(ArcaneState.getResourceType());
      if (state != null) {
         state.addTrigger(x, y, z);
      }
   }

   /**
    * Enqueue an arcane trigger at (x,y,z) with source (sourceX, sourceY, sourceZ).
    */
   public static void requestSignal(@Nonnull World world, int x, int y, int z, int sourceX, int sourceY, int sourceZ) {
      ArcaneState state = world.getChunkStore().getStore().getResource(ArcaneState.getResourceType());
      if (state != null) {
         state.addTrigger(x, y, z, sourceX, sourceY, sourceZ);
      }
   }

   /**
    * Enqueue an arcane trigger for the next tick (from interactions).
    * Signals are flushed into the main queue at the start of each tick, ensuring
    * interaction-sourced signals are processed on the next tick, not the current
    * one.
    */
   public static void requestSignalNextTick(@Nonnull World world, int x, int y, int z) {
      requestSignalNextTick(world, x, y, z, x, y, z);
   }

   /**
    * Enqueue an arcane trigger for the next tick with source (sourceX, sourceY,
    * sourceZ).
    */
   public static void requestSignalNextTick(@Nonnull World world, int x, int y, int z, int sourceX, int sourceY,
         int sourceZ) {
      ArcaneState state = world.getChunkStore().getStore().getResource(ArcaneState.getResourceType());
      if (state != null) {
         state.addPendingNextTick(x, y, z, sourceX, sourceY, sourceZ);
      }
   }

   /**
    * Enqueue an arcane trigger for the next tick with a specific activator ID
    * (runs that activation in the tick system, no skip).
    */
   public static void requestSignalNextTick(@Nonnull World world, int x, int y, int z, int sourceX, int sourceY,
         int sourceZ, @Nullable String activatorId) {
      ArcaneState state = world.getChunkStore().getStore().getResource(ArcaneState.getResourceType());
      if (state != null) {
         state.addPendingNextTickWithActivator(x, y, z, sourceX, sourceY, sourceZ, activatorId);
      }
   }

   @Override
   public void delayedTick(float dt, int index, Store<ChunkStore> store) {
      World world = store.getExternalData().getWorld();

      Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
      ArcaneState state = chunkStore.getResource(ArcaneState.getResourceType());
      if (state == null)
         return;

      state.flushPendingToTriggers();
      if (!state.hasTriggers())
         return;

      // long worldTick = world.getTick();
      // long intervalTicks = Math.max(1L, Math.round(DEFAULT_TICK_INTERVAL_SECONDS *
      // world.getTps()));
      // if (!state.tryClaimRun(worldTick, intervalTicks)) {
      // return;
      // }

      runWave(world, chunkStore, state);
      world.execute(() -> {
         moveBlocks(world, chunkStore);
      });

      ArcaneMoveState arcaneMoveState = world.getChunkStore().getStore().getResource(ArcaneMoveState.getResourceType());
      if (arcaneMoveState == null) {
         ArcaneRelayPlugin.get().getLogger().atInfo().log("ArcaneTickSystem: no arcane move state");
         return;
      }
      arcaneMoveState.clear();
   }

   private void moveBlocks(
         @Nonnull World world,
         @Nonnull Store<ChunkStore> store) {
      ArcaneRelayPlugin.get().getLogger().atInfo().log("ArcaneTickSystem: moving blocks");
      ArcaneMoveState arcaneMoveState = world.getChunkStore().getStore().getResource(ArcaneMoveState.getResourceType());
      if (arcaneMoveState == null) {
         ArcaneRelayPlugin.get().getLogger().atInfo().log("ArcaneTickSystem: no arcane move state");
         return;
      }

      HashMap<Vector3i, ArcaneMoveState.MoveEntry> moveEntries = arcaneMoveState.getMoveEntries();

      // edges are move direction, and represent a dependency between blocks
      // create a graph of blocks and their dependencies
      Map<Vector3i, List<Vector3i>> graph = new HashMap<>();
      for (Map.Entry<Vector3i, ArcaneMoveState.MoveEntry> entry : moveEntries.entrySet()) {
         ArcaneMoveState.MoveEntry moveEntry = entry.getValue();
         // only add position to graph if it is not already in the graph
         if (!graph.containsKey(moveEntry.blockPosition)) {
            graph.put(moveEntry.blockPosition, new ArrayList<>());
         }
      }

      Map<Vector3i, List<Vector3i>> targetPositionGraph = new HashMap<>();
      for (Map.Entry<Vector3i, ArcaneMoveState.MoveEntry> entry : moveEntries.entrySet()) {
         Vector3i blockPosition = entry.getKey();
         ArcaneMoveState.MoveEntry moveEntry = entry.getValue();
         // only add position to graph if it is not already in the graph
         Vector3i dependentBlockPosition = new Vector3i(blockPosition.x + moveEntry.moveDirection.x,
               blockPosition.y + moveEntry.moveDirection.y, blockPosition.z + moveEntry.moveDirection.z);
         if (!targetPositionGraph.containsKey(dependentBlockPosition)) {
            targetPositionGraph.put(dependentBlockPosition, new ArrayList<>());
         }
         targetPositionGraph.get(dependentBlockPosition).add(blockPosition);
      }

      // add dependencies to graph
      for (Map.Entry<Vector3i, ArcaneMoveState.MoveEntry> entry : moveEntries.entrySet()) {
         Vector3i blockPosition = entry.getKey();
         MoveEntry moveEntry = entry.getValue();

         Vector3i dependentBlockPosition = new Vector3i(blockPosition.x + moveEntry.moveDirection.x,
               blockPosition.y + moveEntry.moveDirection.y, blockPosition.z + moveEntry.moveDirection.z);

         if (!graph.containsKey(dependentBlockPosition)) {
            graph.put(dependentBlockPosition, new ArrayList<>());
         }
         graph.get(dependentBlockPosition).add(blockPosition);
      }

      List<List<Vector3i>> executionGraph = getExecutionGraph(graph, targetPositionGraph, moveEntries);

      ArcaneRelayPlugin.get().getLogger().atInfo()
            .log("ArcaneTickSystem: executionGraph size: " + executionGraph.size());

      for (List<Vector3i> executionStep : executionGraph) {
         for (Vector3i blockPosition : executionStep) {
            ArcaneRelayPlugin.get().getLogger().atInfo().log("ArcaneTickSystem: executing block: " + blockPosition.x
                  + ", " + blockPosition.y + ", " + blockPosition.z);
            ArcaneMoveState.MoveEntry moveEntry = arcaneMoveState.getMoveEntries().get(blockPosition);
            if (moveEntry == null) {
               continue;
            }
            WorldChunk futureChunk = world.getChunk(ChunkUtil.indexChunkFromBlock(blockPosition.x + moveEntry.moveDirection.x,
                  blockPosition.z + moveEntry.moveDirection.z));
            if (futureChunk == null) {
               continue;
            }

            WorldChunk fromChunk = world.getChunk(ChunkUtil.indexChunkFromBlock(blockPosition.x,
                  blockPosition.z));
            if (fromChunk == null) {
               continue;
            }

            // if (!chunk.testPlaceBlock(
            // blockPosition.x + moveEntry.moveDirection.x,
            // blockPosition.y + moveEntry.moveDirection.y,
            // blockPosition.z + moveEntry.moveDirection.z,
            // moveEntry.blockType,
            // moveEntry.blockRotation)) {
            // continue;
            // }

            if (targetPositionGraph.get(blockPosition) == null || targetPositionGraph.get(blockPosition).size() == 0) {
               fromChunk.breakBlock(
                     blockPosition.x,
                     blockPosition.y,
                     blockPosition.z,
                     moveEntry.blockFiller,
                     moveEntry.blockSettings);
            }

            world.execute(() -> {
               // if current position has no target block, break the block
               futureChunk.setBlock(
                     blockPosition.x + moveEntry.moveDirection.x,
                     blockPosition.y + moveEntry.moveDirection.y,
                     blockPosition.z + moveEntry.moveDirection.z,
                     moveEntry.blockId,
                     moveEntry.blockType,
                     moveEntry.blockRotation,
                     moveEntry.blockFiller,
                     moveEntry.blockSettings);

            });
         }
      }
      
      ArcaneRelayPlugin.get().getLogger().atInfo().log("ArcaneTickSystem: finished moving blocks");
   }

   private List<List<Vector3i>> getExecutionGraph(@Nonnull Map<Vector3i, List<Vector3i>> graph,
         @Nonnull Map<Vector3i, List<Vector3i>> targetPositionGraph,
         @Nonnull HashMap<Vector3i, ArcaneMoveState.MoveEntry> moveEntries) {

      // connected components topologically sorted
      List<List<Vector3i>> connectedComponents = getConnectedComponents(graph);

      // simplyfy graphs based on collisions from target position graph\
      List<List<Vector3i>> simplifiedGraph = new ArrayList<>();
      for (List<Vector3i> connectedComponent : connectedComponents) {
         List<Vector3i> simplifiedConnectedComponent = new ArrayList<>();
         for (Vector3i blockPosition : connectedComponent) {
            ArcaneMoveState.MoveEntry moveEntry = moveEntries.get(blockPosition);
            if (moveEntry == null) {
               continue;
            }

            Vector3i targetBlockPosition = new Vector3i(blockPosition.x + moveEntry.moveDirection.x,
                  blockPosition.y + moveEntry.moveDirection.y, blockPosition.z + moveEntry.moveDirection.z);

            List<Vector3i> targetBlocks = targetPositionGraph.get(targetBlockPosition);
            if (targetBlocks == null) {
               ArcaneRelayPlugin.get().getLogger().atInfo().log("ArcaneTickSystem: no target blocks at: "
                     + targetBlockPosition.x + ", " + targetBlockPosition.y + ", " + targetBlockPosition.z);
               continue;
            }

            int targetBlockCount = targetBlocks.size();
            if (targetBlockCount > 1) {
               ArcaneRelayPlugin.get().getLogger().atInfo()
                     .log("ArcaneTickSystem: detected collision at: " + blockPosition.x + ", " + blockPosition.y + ", "
                           + blockPosition.z + " has " + targetBlockCount + " target blocks");
               continue;
            }
            simplifiedConnectedComponent.add(blockPosition);
         }
         if (simplifiedConnectedComponent.size() > 0) {
            simplifiedGraph.add(simplifiedConnectedComponent);
         }
      }

      return simplifiedGraph;
   }

   private static List<List<Vector3i>> getConnectedComponents(Map<Vector3i, List<Vector3i>> graph) {
      List<List<Vector3i>> connectedGraphs = new ArrayList<>();
      Set<Vector3i> visited = new HashSet<>();

      for (Map.Entry<Vector3i, List<Vector3i>> entry : graph.entrySet()) {
         if (visited.contains(entry.getKey())) {
            continue;
         }

         List<Vector3i> connectedComponent = new ArrayList<>();

         topologicalSort(entry.getKey(), visited, graph, connectedComponent);

         connectedGraphs.add(connectedComponent);
      }

      return connectedGraphs;
   }

   private static void topologicalSort(Vector3i blockPosition, Set<Vector3i> visited,
         Map<Vector3i, List<Vector3i>> graph, List<Vector3i> sortedBlocks) {
      if (visited.contains(blockPosition)) {
         return;
      }

      visited.add(blockPosition);
      for (Vector3i dependentBlockPosition : graph.get(blockPosition)) {
         topologicalSort(dependentBlockPosition, visited, graph, sortedBlocks);
      }

      visited.add(blockPosition);
      sortedBlocks.add(blockPosition);
   }

   private static void runWave(
         @Nonnull World world,
         @Nonnull Store<ChunkStore> store,
         @Nonnull ArcaneState state) {
      List<TriggerEntry> entries = state.copyTriggerEntries();
      state.clearTriggers();
      Map<PosKey, TargetInfo> targets = new HashMap<>();
      for (TriggerEntry e : entries) {
         PosKey key = new PosKey(e.target().getX(), e.target().getY(), e.target().getZ());
         TargetInfo info = targets.computeIfAbsent(key, k -> new TargetInfo());
         info.sources.add(e.source());
         if (e.skip())
            info.skip = true;
         if (e.activatorId() != null && info.activatorId == null)
            info.activatorId = e.activatorId();
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
            ArcaneRelayPlugin.get().getLogger().atWarning()
                  .log("ArcaneTickSystem: block type not found at " + x + ", " + y + ", " + z);
            continue;
         }

         if (info.skip) {
            ArcaneRelayPlugin.get().getLogger().atFine()
                  .log(String.format("ArcaneTickSystem: propagateOnly at (%d,%d,%d)", x, y, z));
            Ref<ChunkStore> blockRef = chunk.getBlockComponentEntity(x, y, z);
            if (blockRef == null) {
               // blockRef = BlockModule.ensureBlockEntity(chunk, x, y, z);
            }
            propagateOnly(world, store, chunk, blockRef, new Vector3i(x, y, z));
         } else {
            List<int[]> sourcesAsInts = new ArrayList<>(info.sources.size());
            for (Vector3i s : info.sources) {
               sourcesAsInts.add(new int[] { s.getX(), s.getY(), s.getZ() });
            }
            ArcaneRelayPlugin.get().getLogger().atInfo()
                  .log(String.format("ArcaneTickSystem: activate block=(%d,%d,%d) blockType=%s activatorId=%s", x, y, z,
                        blockType.getId(), info.activatorId));
            activateOutput(world, store, chunk, x, y, z, blockId, blockType, sourcesAsInts, info.activatorId);
         }
      }
   }

   private static final class TargetInfo {
      final List<Vector3i> sources = new ArrayList<>();
      boolean skip;
      @Nullable
      String activatorId;
   }

   /** When skip=true: only propagate to outputs, do not activate the block. */
   private static void propagateOnly(
         @Nonnull World world,
         @Nonnull Store<ChunkStore> store,
         @Nonnull WorldChunk chunk,
         @Nonnull Ref<ChunkStore> blockRef,
         @Nonnull Vector3i blockPos) {
      ArcaneTriggerBlock trigger = store.getComponent(blockRef,
            ArcaneRelayPlugin.get().getArcaneTriggerBlockComponentType());
      if (trigger == null)
         return;
      ArcaneState state = world.getChunkStore().getStore().getResource(ArcaneState.getResourceType());
      if (state == null)
         return;
      for (Vector3i out : trigger.getOutputPositions()) {
         state.addTrigger(TriggerEntry.of(out, blockPos));
      }
   }

   /**
    * Activate the output of this arcane block.
    * When activatorId is non-null, uses that activation; otherwise uses block
    * binding from {@link com.arcanerelay.asset.ActivationRegistry}.
    * If no activation, dispatches to the handler registered via
    * {@link ArcaneRelayPlugin#registerBlockActivationHandler}.
    *
    * @param sources     list of [sx,sy,sz] positions that triggered this block
    *                    (for unique-source logic)
    * @param activatorId optional activation ID to run (e.g. from
    *                    ArcaneActivatorInteraction); when null, use block's
    *                    binding
    */
   protected static void activateOutput(
         @Nonnull World world,
         @Nonnull Store<ChunkStore> store,
         @Nonnull WorldChunk chunk,
         int blockX,
         int blockY,
         int blockZ,
         int blockId,
         @Nonnull BlockType blockType,
         @Nonnull List<int[]> sources,
         @Nullable String activatorId) {
      String blockTypeKey = blockType.getId();
      Activation activation = activatorId != null && !activatorId.isEmpty()
            ? ArcaneRelayPlugin.get().getActivationRegistry().getActivation(activatorId)
            : ArcaneRelayPlugin.get().getActivationRegistry().getActivationForBlock(blockTypeKey);
      if (activation != null) {
         ArcaneRelayPlugin.get().getLogger().atFine().log(String.format(
               "ArcaneTickSystem: scheduling activation %s at (%d,%d,%d)", activation.getId(), blockX, blockY, blockZ));
         ActivationExecutor.execute(world, store, chunk, blockX, blockY, blockZ, blockType, activation, sources);

         return;
      }

      BlockActivationHandler handler = ArcaneRelayPlugin.get().getBlockActivationHandler(blockId, blockTypeKey);
      if (handler != null) {
         ArcaneRelayPlugin.get().getLogger().atFine().log(String.format(
               "ArcaneTickSystem: executing block activation handler for block=(%d,%d,%d) blockType=%s activatorId=%s handler=%s",
               blockX, blockY, blockZ, blockType.getId(), activatorId, handler.getClass().getName()));
         handler.onActivate(world, store, chunk, blockX, blockY, blockZ, blockType);
      } else {
         ArcaneRelayPlugin.get().getLogger().atWarning().log("ArcaneTickSystem: no block activation handler for block: "
               + blockX + ", " + blockY + ", " + blockZ + " key: " + blockTypeKey);
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
         if (this == obj)
            return true;
         if (!(obj instanceof PosKey other))
            return false;
         return x == other.x && y == other.y && z == other.z;
      }
   }
}
