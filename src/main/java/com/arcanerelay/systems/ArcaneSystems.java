package com.arcanerelay.systems;

import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkFlag;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.arcanerelay.ArcaneRelayPlugin;
import com.arcanerelay.components.ArcaneSection;
import com.arcanerelay.components.ArcaneSignalComponent;
import com.arcanerelay.components.ArcaneTriggerBlock;
import com.arcanerelay.core.activation.ActivationExecutor;
import com.arcanerelay.core.activation.ArcaneCachedAccessor;
import com.arcanerelay.core.activation.ChunkStoreCommandBufferAdapter;
import com.arcanerelay.core.blockmovement.BlockMovementExecutor;
import com.arcanerelay.resources.ArcaneMoveState;
import com.arcanerelay.util.ArcaneUtil;
import com.arcanerelay.config.Activation;
import com.arcanerelay.config.types.ArcanePullerActivation;
import com.arcanerelay.components.ArcanePullerBlock;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.interaction.system.InteractionSystems;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.builtin.blocktick.system.ChunkBlockTickSystem;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public class ArcaneSystems {

    
    public static class EnsureArcaneSection extends HolderSystem<ChunkStore> {
        @Nonnull
        private static final Query<ChunkStore> QUERY = Query.and(ChunkSection.getComponentType(), Query.not(ArcaneSection.getComponentType()));

        @Override
        public void onEntityAdd(@Nonnull Holder<ChunkStore> holder, @Nonnull AddReason reason, @Nonnull Store<ChunkStore> store) {
            // ArcaneRelayPlugin.LOGGER.atInfo().log("Ensuring ArcaneSection component on chunk store");
            holder.ensureComponent(ArcaneSection.getComponentType());
        }

        @Override
        public void onEntityRemoved(@Nonnull Holder<ChunkStore> holder, @Nonnull RemoveReason reason, @Nonnull Store<ChunkStore> store) {
            holder.removeComponent(ArcaneSection.getComponentType());
        }

        @Override
        public Query<ChunkStore> getQuery() {
            return QUERY;
        }

        @Nonnull
        @Override
        public Set<Dependency<ChunkStore>> getDependencies() {
           return RootDependency.firstSet();
        }
    }

    public static class PreTick extends EntityTickingSystem<ChunkStore> {
        @Nonnull
        private static final Query<ChunkStore> QUERY = Query.and(ChunkSection.getComponentType(), ArcaneSection.getComponentType());
        

        @SuppressWarnings("null")
        @Nonnull
        private static final Set<Dependency<ChunkStore>> DEPENDENCIES = Set.of(
           new SystemDependency<>(Order.AFTER, ChunkBlockTickSystem.PreTick.class), 
           new SystemDependency<>(Order.BEFORE, ChunkBlockTickSystem.Ticking.class)
        );

        @Nonnull
        @Override
        public Set<Dependency<ChunkStore>> getDependencies() {
            return DEPENDENCIES;
        }

        public PreTick() {
        }

        @Nonnull
        @Override
        public Query<ChunkStore> getQuery() {
            return QUERY;
        }

        @Override
        public boolean isParallel(int archetypeChunkSize, int taskCount) {
            return EntityTickingSystem.maybeUseParallel(archetypeChunkSize, taskCount);
        }

        @Override
        public void tick(float dt, int index, @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk, @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
            Instant time = commandBuffer.getExternalData().getWorld().getEntityStore().getStore().getResource(WorldTimeResource.getResourceType()).getGameTime();

            Ref<ChunkStore> sectionRef = archetypeChunk.getReferenceTo(index);
            if (sectionRef == null) return;
            
            ArcaneSection arcaneSection = commandBuffer.getComponent(sectionRef, ArcaneSection.getComponentType());
            if (arcaneSection == null) return;

            arcaneSection.preTick(time);
        }
    }

    public static class Ticking extends EntityTickingSystem<ChunkStore> {
        @Nonnull
        private static final Query<ChunkStore> QUERY = Query.and(ChunkSection.getComponentType(), BlockSection.getComponentType(), ArcaneSection.getComponentType());
        
        @SuppressWarnings("null")
        @Nonnull
        private static final Set<Dependency<ChunkStore>> DEPENDENCIES = Set.of(
            new SystemDependency<>(Order.BEFORE, ChunkBlockTickSystem.Ticking.class)
        );

        @Nonnull
        @Override
        public Set<Dependency<ChunkStore>> getDependencies() {
            return DEPENDENCIES;
        }

        public Ticking() {
        }

        /** Single-threaded to avoid deadlock: activations use world/commandBuffer in ways that are not safe from parallel workers. */
        @Override
        public boolean isParallel(int archetypeChunkSize, int taskCount) {
        //    return EntityTickingSystem.useParallel(archetypeChunkSize, taskCount);
            return false;
        }

        @Override
        public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer
        ) {
            Ref<ChunkStore> sectionRef = archetypeChunk.getReferenceTo(index);

            BlockSection blockSection = commandBuffer.getComponent(sectionRef, BlockSection.getComponentType());
            if (blockSection == null) return;

            ChunkSection chunkSection = commandBuffer.getComponent(sectionRef, ChunkSection.getComponentType());
            if (chunkSection == null) return;

            ArcaneSection arcaneSection = commandBuffer.getComponent(sectionRef, ArcaneSection.getComponentType());
            if (arcaneSection == null) return;

            BlockComponentChunk blockComponentChunk = commandBuffer.getComponent(chunkSection.getChunkColumnReference(), BlockComponentChunk.getComponentType());
            if (blockComponentChunk == null) return;

            WorldChunk worldChunkComponent = commandBuffer.getComponent(chunkSection.getChunkColumnReference(), WorldChunk.getComponentType());
            if (worldChunkComponent == null) return;

            ArcaneCachedAccessor accessor = ArcaneCachedAccessor.of(
                commandBuffer, 
                arcaneSection, 
                blockSection, 
                chunkSection, 
                1);

            var world = commandBuffer.getExternalData().getWorld();
            long tick = world.getTick();
            long rateLimitTicks = 10L; // process each block every 10 ticks

            int arcaneTicksProcessed = arcaneSection.forEachTicking(accessor, commandBuffer, blockSection, chunkSection.getY(),
                (commandBuffer1, arcaneSection1, x, y, z, blockId) -> {
                    int worldX = ChunkUtil.worldCoordFromLocalCoord(chunkSection.getX(), x);
                    int worldZ = ChunkUtil.worldCoordFromLocalCoord(chunkSection.getZ(), z);
                    // long hash = HashUtil.rehash(worldX, y, worldZ, 4030921250L);
                    BlockType blockType = accessor.getBlockType(worldX, y, worldZ);
                    if (blockType == null) return ArcaneSection.BlockTickStrategy.PROCESSED;

                    Activation activation = ArcaneUtil.getActivationForBlock(blockType);
                    if (activation == null) {
                        return ArcaneSection.BlockTickStrategy.PROCESSED;
                    }

                    if (tick % rateLimitTicks != 0) {
                        return ArcaneSection.BlockTickStrategy.CONTINUE;
                    }

                    Ref<ChunkStore> blockRef = blockComponentChunk.getEntityReference(ChunkUtil.indexBlockInColumn(x, y, z));

                    int sectionStartY = chunkSection.getY() << 5;
                    int blockIndex = ChunkUtil.indexBlock(x, y - sectionStartY, z);
                    int[] lastSource = arcaneSection.getLastSource(blockIndex);
                    List<int[]> sources = lastSource != null ? List.of(lastSource) : List.of();

                    try {
                        ArcaneRelayPlugin.LOGGER.atInfo().log("Executing activation %s at %d,%d,%d", activation.getId(), worldX, y, worldZ);
                        ArcaneSection.BlockTickStrategy strategy = activation.execute(
                            accessor, sectionRef, blockRef, worldX, y, worldZ,
                            sources);
                        return strategy != null ? strategy : ArcaneSection.BlockTickStrategy.PROCESSED;
                    } catch (Throwable t) {
                        ArcaneRelayPlugin.LOGGER.atSevere().withCause(t).log("Activation %s failed at %d,%d,%d",
                            activation.getId(), worldX, y, worldZ);
                        return ArcaneSection.BlockTickStrategy.PROCESSED;
                    }
                });
        }

        @Nonnull
        @Override
        public Query<ChunkStore> getQuery() {
           return QUERY;
        }
    }

    public static class MoveBlock extends TickingSystem<ChunkStore> {
        @Override
        public void tick(
            float dt,
            int index,
            @Nonnull Store<ChunkStore> store
        ) {
            ArcaneMoveState moveState = store.getResource(ArcaneMoveState.getResourceType());
            if (moveState == null) return;

            var entries = moveState.getMoveEntries();
            if (entries.isEmpty()) return;

            var world = store.getExternalData().getWorld();
            if (world == null) return;

            // here it's safe to use world.execute() as we are not using the command buffer because it's running on the main thread
            BlockMovementExecutor.execute(world, entries);
            moveState.clear();
        }
        
        @SuppressWarnings("null")
        @Nonnull
        private static final Set<Dependency<ChunkStore>> DEPENDENCIES = Set.of(
            new SystemDependency<>(Order.BEFORE, ChunkBlockTickSystem.Ticking.class)
        );

        @Nonnull
        @Override
        public Set<Dependency<ChunkStore>> getDependencies() {
           return DEPENDENCIES;
        }
    }

    public static class SendSignal extends RefChangeSystem<ChunkStore, ArcaneSignalComponent> {
        @Nonnull
        private static final Query<ChunkStore> QUERY = Query.and(ChunkSection.getComponentType(), ArcaneSignalComponent.getComponentType(), ArcaneTriggerBlock.getComponentType());
        
        @SuppressWarnings("null")
        @Nonnull
        private static final Set<Dependency<ChunkStore>> DEPENDENCIES = Set.of(
            new SystemDependency<>(Order.BEFORE, ChunkBlockTickSystem.Ticking.class),
            new SystemDependency<>(Order.AFTER, ArcaneSystems.MoveBlock.class)
        );

        @Nonnull
        @Override
        public Set<Dependency<ChunkStore>> getDependencies() {
           return DEPENDENCIES;
        }

        @Override
        @Nullable
        public Query<ChunkStore> getQuery() {
            return QUERY;
        }

        @Override
        @Nonnull
        public ComponentType<ChunkStore, ArcaneSignalComponent> componentType() {
            return ArcaneSignalComponent.getComponentType();
        }

        @Override
        public void onComponentAdded(@Nonnull Ref<ChunkStore> ref, @Nonnull ArcaneSignalComponent component,
                @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
            // Get the ChunkSection to determine which section this block belongs to
            ChunkSection chunkSection = commandBuffer.getComponent(ref, ChunkSection.getComponentType());
            if (chunkSection == null) return;

            // Get the block index directly from the ref
            int blockIndexInColumn = ref.getIndex();

            // Extract local block coordinates from the block index
            int blockX = ChunkUtil.xFromIndex(blockIndexInColumn);
            int blockY = ChunkUtil.yFromIndex(blockIndexInColumn);
            int blockZ = ChunkUtil.zFromIndex(blockIndexInColumn);

            // Convert to world coordinates
            int worldX = ChunkUtil.worldCoordFromLocalCoord(chunkSection.getX(), blockX);
            int worldY = blockY;
            int worldZ = ChunkUtil.worldCoordFromLocalCoord(chunkSection.getZ(), blockZ);

            ActivationExecutor.sendSignals(store, ref, worldX, worldY, worldZ);
        }

        @Override
        public void onComponentRemoved(@Nonnull Ref<ChunkStore> ref, @Nonnull ArcaneSignalComponent component,
                @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> commandBuffer) { }

        @Override
        public void onComponentSet(@Nonnull Ref<ChunkStore> ref, @Nullable ArcaneSignalComponent component,
                @Nonnull ArcaneSignalComponent newComponent, @Nonnull Store<ChunkStore> store,
                @Nonnull CommandBuffer<ChunkStore> commandBuffer) { }
    }
}
