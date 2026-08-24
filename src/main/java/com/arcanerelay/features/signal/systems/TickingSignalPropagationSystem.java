package com.arcanerelay.features.signal.systems;

import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.arcanerelay.ArcaneRelayPlugin;
import com.arcanerelay.core.activation.ArcaneCachedAccessor;
import com.arcanerelay.core.adapters.ChunkStoreCommandBufferAdapter;
import com.arcanerelay.features.activation.Activation;
import com.arcanerelay.features.signal.components.ArcaneSection;
import com.arcanerelay.features.signal.util.ArcaneUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.builtin.blocktick.system.ChunkBlockTickSystem;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;

import javax.annotation.Nonnull;

import java.util.List;
import java.util.Set;

public class TickingSignalPropagationSystem extends EntityTickingSystem<ChunkStore> {
    private final ArcaneCachedAccessor cachedAccessor = new ArcaneCachedAccessor();

    @Nonnull
    private static final Query<ChunkStore> QUERY = Query.and(ChunkSection.getComponentType(),
            BlockSection.getComponentType(), ArcaneSection.getComponentType());

    @SuppressWarnings("null")
    @Nonnull
    private static final Set<Dependency<ChunkStore>> DEPENDENCIES = Set.of(
            new SystemDependency<>(Order.BEFORE, ChunkBlockTickSystem.Ticking.class));

    @Nonnull
    @Override
    public Set<Dependency<ChunkStore>> getDependencies() {
        return DEPENDENCIES;
    }

    public TickingSignalPropagationSystem() {
    }

    /**
     * Single-threaded to avoid deadlock: activations use world/commandBuffer in
     * ways that are not safe from parallel workers.
     */
    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        // return EntityTickingSystem.useParallel(archetypeChunkSize, taskCount);
        return false;
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        Ref<ChunkStore> sectionRef = archetypeChunk.getReferenceTo(index);

        BlockSection blockSection = commandBuffer.getComponent(sectionRef, BlockSection.getComponentType());
        if (blockSection == null)
            return;

        ChunkSection chunkSection = commandBuffer.getComponent(sectionRef, ChunkSection.getComponentType());
        if (chunkSection == null)
            return;

        ArcaneSection arcaneSection = commandBuffer.getComponent(sectionRef, ArcaneSection.getComponentType());
        if (arcaneSection == null)
            return;

        BlockComponentSection blockComponentSection = commandBuffer.getComponent(sectionRef,
                BlockComponentSection.getComponentType());
        if (blockComponentSection == null)
            return;

        WorldChunk worldChunkComponent = commandBuffer.getComponent(chunkSection.getChunkColumnReference(),
                WorldChunk.getComponentType());
        if (worldChunkComponent == null)
            return;

        cachedAccessor.init(new ChunkStoreCommandBufferAdapter(commandBuffer), arcaneSection, blockSection,
                chunkSection, 1);

        var world = commandBuffer.getExternalData().getWorld();
        long tick = world.getTick();
        long rateLimitTicks = 10L; // process each block every 10 ticks

        int arcaneTicksProcessed = arcaneSection.forEachTicking(cachedAccessor, commandBuffer, blockSection,
                chunkSection.getY(),
                (commandBuffer1, arcaneSection1, x, y, z, blockId) -> {
                    int worldX = ChunkUtil.worldCoordFromLocalCoord(chunkSection.getX(), x);
                    int worldZ = ChunkUtil.worldCoordFromLocalCoord(chunkSection.getZ(), z);
                    // long hash = HashUtil.rehash(worldX, y, worldZ, 4030921250L);
                    BlockType blockType = cachedAccessor.getBlockType(worldX, y, worldZ);
                    if (blockType == null)
                        return ArcaneSection.BlockTickStrategy.PROCESSED;

                    Activation activation = ArcaneUtil.getActivationForBlock(blockType);
                    if (activation == null) {
                        return ArcaneSection.BlockTickStrategy.PROCESSED;
                    }

                    if (tick % rateLimitTicks != 0) {
                        return ArcaneSection.BlockTickStrategy.CONTINUE;
                    }

                    Ref<ChunkStore> blockRef = blockComponentSection
                            .getBlockReference(ChunkUtil.indexBlock(x, y, z));

                    int sectionStartY = chunkSection.getY() << 5;
                    int blockIndex = ChunkUtil.indexBlock(x, y - sectionStartY, z);
                    int[] lastSource = arcaneSection.getLastSource(blockIndex);
                    List<int[]> sources = lastSource != null ? List.of(lastSource) : List.of();

                    try {
                        ArcaneRelayPlugin.LOGGER.atInfo().log("Executing activation %s at %d,%d,%d", activation.getId(),
                                worldX, y, worldZ);
                        ArcaneSection.BlockTickStrategy strategy = activation.execute(
                                cachedAccessor, sectionRef, blockRef, worldX, y, worldZ,
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
