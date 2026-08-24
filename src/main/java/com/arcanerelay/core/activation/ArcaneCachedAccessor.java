package com.arcanerelay.core.activation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.arcanerelay.core.adapters.ChunkStoreCommandBufferLike;
import com.arcanerelay.features.signal.components.ArcaneSection;
import com.hypixel.hytale.server.core.universe.world.chunk.AbstractCachedAccessor;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;

/**
 * Cached accessor for activations. Mirrors
 * {@link com.hypixel.hytale.server.core.asset.type.fluid.FluidTicker.CachedAccessor}: inserts
 * section components (ArcaneSection, BlockSection), exposes self* and get*Section.
 */
public final class ArcaneCachedAccessor extends AbstractCachedAccessor implements ArcaneActivationAccessor {

    private static final Registry REGISTRY = new Registry();
    private static final Key<ArcaneSection> ARCANE = REGISTRY.forSection(ArcaneSection::getComponentType);
    private static final Key<BlockSection> BLOCK = REGISTRY.forSection(BlockSection::getComponentType);
    private static final Key<ChunkSection> CHUNK = REGISTRY.forSection(ChunkSection::getComponentType);
    protected ArcaneSection selfArcaneSection;
    protected BlockSection selfBlockSection;
    protected ChunkSection selfChunkSection;
    protected ChunkStoreCommandBufferLike selfCommandBuffer;

    public ArcaneCachedAccessor() {
        super(REGISTRY);
    }

    public void init(
        @Nonnull ChunkStoreCommandBufferLike commandBuffer,
        @Nonnull ArcaneSection section,
        @Nonnull BlockSection blockSection,
        @Nonnull ChunkSection chunkSection,
        int radius
    ) {
        this.selfArcaneSection = section;
        this.selfBlockSection  = blockSection;
        this.selfChunkSection  = chunkSection;
        this.selfCommandBuffer = commandBuffer;
        super.init(commandBuffer, chunkSection.getX(), chunkSection.getY(), chunkSection.getZ(), radius);
        insertSectionComponent(ARCANE, section, chunkSection.getX(), chunkSection.getY(), chunkSection.getZ());
        insertSectionComponent(BLOCK, blockSection, chunkSection.getX(), chunkSection.getY(), chunkSection.getZ());
        insertSectionComponent(CHUNK, chunkSection, chunkSection.getX(), chunkSection.getY(), chunkSection.getZ());
    }


    @Override
    @Nullable
    public ArcaneSection getArcaneSection(int cx, int cy, int cz) {
        return getComponentSection(cx, cy, cz, ARCANE);
    }

    @Override
    @Nullable
    public BlockSection getBlockSection(int cx, int cy, int cz) {
        return getComponentSection(cx, cy, cz, BLOCK);
    }

    @Override
    @Nullable
    public ChunkSection getChunkSection(int cx, int cy, int cz) {
        return getComponentSection(cx, cy, cz, CHUNK);
    }

    @Override
    @Nonnull
    public ChunkStoreCommandBufferLike getCommandBuffer() {
        return this.selfCommandBuffer;
    }
}
