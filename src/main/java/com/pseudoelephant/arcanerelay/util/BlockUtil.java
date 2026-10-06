package com.pseudoelephant.arcanerelay.util;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockMaterial;

import org.joml.Vector3d;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.modules.block.BlockEntity;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import com.hypixel.hytale.server.core.util.TargetUtil;

import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Utility for block-related operations, e.g. finding the main block for multi-block structures.
 */
public final class BlockUtil {

    private BlockUtil() { }

    /**
     * Resolves the block-entity reference stored for a block position via its section-level
     * {@link BlockComponentSection}.
     *
     * @param store chunk-store component accessor
     * @param x     block X (world coordinates required)
     * @param y     block Y (world)
     * @param z     block Z (world coordinates required)
     * @return the block-entity reference, or null if the section/component/reference does not exist
     */
    @Nullable
    public static Ref<ChunkStore> getBlockEntityReference(
            @Nonnull ComponentAccessor<ChunkStore> store, int x, int y, int z) {
        Ref<ChunkStore> sectionRef = store.getExternalData().getChunkSectionReferenceAtBlock(x, y, z);
        if (sectionRef == null || !sectionRef.isValid()) {
            return null;
        }

        BlockComponentSection blockComponentSection =
                store.getComponent(sectionRef, BlockComponentSection.getComponentType());
        if (blockComponentSection == null) {
            return null;
        }

        return blockComponentSection.getBlockReference(ChunkUtil.indexBlock(x, y, z));
    }

    /**
     * Resolves the {@link BlockSection} for a block position.
     *
     * @param store chunk-store component accessor
     * @param x     block X (world coordinates required)
     * @param y     block Y (world; out of range simply fails to resolve)
     * @param z     block Z (world coordinates required)
     * @return the block section, or null if it cannot be resolved (e.g. unloaded or y outside [0, 320))
     */
    @Nullable
    public static BlockSection getBlockSection(
            @Nonnull ComponentAccessor<ChunkStore> store, int x, int y, int z) {
        Ref<ChunkStore> sectionRef = store.getExternalData().getChunkSectionReferenceAtBlock(x, y, z);
        if (sectionRef == null || !sectionRef.isValid()) {
            return null;
        }

        return store.getComponent(sectionRef, BlockSection.getComponentType());
    }

    /**
     * Resolves a copy of the block-entity holder for a block position.
     *
     * @param store chunk-store component accessor
     * @param x     block X (world coordinates required)
     * @param y     block Y (world)
     * @param z     block Z (world coordinates required)
     * @return a copy of the holder, or null if the section/component/reference does not exist
     */
    @Nullable
    public static Holder<ChunkStore> getBlockComponentHolder(
            @Nonnull ComponentAccessor<ChunkStore> store, int x, int y, int z) {
        Ref<ChunkStore> sectionRef = store.getExternalData().getChunkSectionReferenceAtBlock(x, y, z);
        if (sectionRef == null || !sectionRef.isValid()) {
            return null;
        }

        BlockComponentSection blockComponentSection =
                store.getComponent(sectionRef, BlockComponentSection.getComponentType());
        return blockComponentSection != null
                ? blockComponentSection.copyBlockHolder(ChunkUtil.indexBlock(x, y, z))
                : null;
    }

    public static int getFiller(@Nonnull BlockSection section, int x, int y, int z) {
        return section.getFiller(x, y, z);
    }

    public static int getRotationIndex(@Nonnull BlockSection section, int x, int y, int z) {
        return section.getRotationIndex(x, y, z);
    }

    /**
     * Resolves the {@link RotationTuple} for a block position.
     *
     * @param store chunk-store component accessor
     * @param x     block X (world coordinates required)
     * @param y     block Y (world)
     * @param z     block Z (world coordinates required)
     * @return the rotation tuple, or null if the block section cannot be resolved
     */
    @Nullable
    public static RotationTuple getRotationTuple(
            @Nonnull ComponentAccessor<ChunkStore> store, int x, int y, int z
    ) {
        BlockSection section = getBlockSection(store, x, y, z);
        return section != null ? RotationTuple.get(getRotationIndex(section, x, y, z)) : null;
    }

    /**
     * Resolves the {@link BlockType} for a block position.
     *
     * @param store chunk-store component accessor
     * @param x     block X (world coordinates required)
     * @param y     block Y (world)
     * @param z     block Z (world coordinates required)
     * @return the block type, or null if it cannot be resolved (unloaded, y outside [0, 320), unknown id)
     */
    @Nullable
    public static BlockType getBlockType(
            @Nonnull ComponentAccessor<ChunkStore> store, int x, int y, int z) {
        BlockSection section = getBlockSection(store, x, y, z);
        return section != null ? BlockType.getAssetMap().getAsset(section.get(x, y, z)) : null;
    }

    /**
     * Writes a block at a position, updating palette, heightmap, lighting and ticking.
     *
     * @param store     chunk-store component accessor
     * @param x         block X (world coordinates required)
     * @param y         block Y (world)
     * @param z         block Z (world coordinates required)
     * @param id        block asset id to write
     * @param blockType block type to write
     * @param rotation  rotation index
     * @param filler    packed filler value
     * @param settings  block interaction settings
     * @return true if the block was written
     */
    public static boolean setBlock(@Nonnull ComponentAccessor<ChunkStore> store,
            int x, int y, int z, int id, @Nonnull BlockType blockType,
            int rotation, int filler, int settings) {
        Ref<ChunkStore> sectionRef = store.getExternalData().getChunkSectionReferenceAtBlock(x, y, z);
        if (sectionRef == null || !sectionRef.isValid()) {
            return false;
        }

        return BlockOperations.setBlock(store.getExternalData(), sectionRef,
                x, y, z, id, blockType, rotation, filler, settings);
    }

    /**
     * Writes the block-entity holder for a block position.
     *
     * @param store     chunk-store component accessor
     * @param x         block X (world coordinates required)
     * @param y         block Y (world)
     * @param z         block Z (world coordinates required)
     * @param blockType block type the entity belongs to
     * @param rotation  rotation index
     * @param holder    holder to store, or null to clear
     */
    public static void setBlockEntity(@Nonnull ComponentAccessor<ChunkStore> store,
            int x, int y, int z, @Nonnull BlockType blockType, int rotation,
            @Nullable Holder<ChunkStore> holder) {
        Ref<ChunkStore> sectionRef = store.getExternalData().getChunkSectionReferenceAtBlock(x, y, z);
        if (sectionRef == null || !sectionRef.isValid()) {
            return;
        }

        BlockComponentSection blockComponentSection =
                store.getComponent(sectionRef, BlockComponentSection.getComponentType());
        if (blockComponentSection == null) {
            return;
        }

        BlockEntity.setBlockEntity(store, sectionRef, blockComponentSection,
                x, y, z, blockType, rotation, holder);
    }

    /**
     * Ticks the block at a position together with its neighbours.
     *
     * @param store chunk-store component accessor
     * @param x     block X (world coordinates required)
     * @param y     block Y (world)
     * @param z     block Z (world coordinates required)
     * @return true if the update was enqueued
     */
    public static boolean performBlockUpdate(@Nonnull ComponentAccessor<ChunkStore> store, int x, int y, int z) {
        Ref<ChunkStore> sectionRef = store.getExternalData().getChunkSectionReferenceAtBlock(x, y, z);
        if (sectionRef == null || !sectionRef.isValid()) {
            return false;
        }

        ChunkSection chunkSection = store.getComponent(sectionRef, ChunkSection.getComponentType());
        BlockSection blockSection = store.getComponent(sectionRef, BlockSection.getComponentType());
        if (chunkSection == null || blockSection == null) {
            return false;
        }

        BlockOperations.updateBlockArea(store.getExternalData(), chunkSection, blockSection, x, y, z);
        return true;
    }

    /**
     * Clears the block at a position by writing air, resolving filler links first.
     * <p>
     * While bit 16 of {@code settings} is clear, the linked main block is cleared instead of the
     * filler cell — unless that main block is already gone, in which case the filler cell is
     * cleared. Bit 16 set clears the given cell directly.
     *
     * @param store    chunk-store component accessor
     * @param x        block X (world coordinates required)
     * @param y        block Y (world)
     * @param z        block Z (world coordinates required)
     * @param filler   packed filler value of the cell to clear
     * @param settings block interaction settings
     * @return true if a block was cleared
     */
    public static boolean clearBlock(@Nonnull ComponentAccessor<ChunkStore> store,
            int x, int y, int z, int filler, int settings) {
        if ((settings & 16) == 0) {
            int mainX = x - FillerBlockUtil.unpackX(filler);
            int mainY = y - FillerBlockUtil.unpackY(filler);
            int mainZ = z - FillerBlockUtil.unpackZ(filler);

            BlockSection mainSection = getBlockSection(store, mainX, mainY, mainZ);
            if (mainSection != null && mainSection.get(mainX, mainY, mainZ) == 0) {
                // main already gone: clear the filler cell instead
                return setBlock(store, x, y, z, 0, BlockType.EMPTY, 0, 0, settings);
            }

            return setBlock(store, mainX, mainY, mainZ, 0, BlockType.EMPTY, 0, 0, settings);
        }

        return setBlock(store, x, y, z, 0, BlockType.EMPTY, 0, 0, settings);
    }

    /**
     * For a position that may be part of a multi-block structure (e.g. door filler), finds the main block.
     * The main block is the non-filler block that has state (e.g. door part, or block with InteractionState).
     *
     * @param world the world
     * @param x     block X (world coordinates required)
     * @param y     block Y (world)
     * @param z     block Z (world coordinates required)
     * @return [mainX, mainY, mainZ] or null if not found. Returns the same position if it is already the main block.
     */
    @Nullable
    public static int[] findMainBlock(@Nonnull World world, int x, int y, int z) {
        ComponentAccessor<ChunkStore> store = world.getChunkStore().getStore();
        BlockSection blockSection = getBlockSection(store, x, y, z);
        int filler = blockSection != null ? getFiller(blockSection, x, y, z) : 0;
        if (filler == 0) {
            return new int[]{x, y, z};
        }

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;

                    int tx = x + dx;
                    int ty = y + dy;
                    int tz = z + dz;
                    BlockSection tSection = getBlockSection(store, tx, ty, tz);
                    if (tSection == null) continue;

                    int tFiller = getFiller(tSection, tx, ty, tz);
                    if (tFiller != 0) continue;

                    BlockType tBt = BlockType.getAssetMap().getAsset(tSection.get(tx, ty, tz));
                    if (tBt == null) continue;

                    if (tBt.isDoor() || tBt.getStateForBlock(tBt) != null) {
                        return new int[]{tx, ty, tz};
                    }
                }
            }
        }

        return null;
    }

    private static final double FEET_Y_OFFSET = -0.5;
    private static Vector3d getFeetPosition(@Nonnull TransformComponent transform,
            @Nullable BoundingBox boundingBox) {
        Vector3d feetPosition = new Vector3d(transform.getPosition());

        if (boundingBox != null) {
            feetPosition.add(0, boundingBox.getBoundingBox().min.y, 0);
        } else {
            feetPosition.add(0, FEET_Y_OFFSET, 0);
        }

        return feetPosition;
    }

    private static boolean isFeetOnTopOfBlock(Vector3d feetPosition, Vector3i blockPosition) {
        return feetPosition.x >= blockPosition.x - 0.1 && feetPosition.x <= blockPosition.x + 1.1
            && feetPosition.y >= blockPosition.y + 0.95 && feetPosition.y <= blockPosition.y + 1.1
            && feetPosition.z >= blockPosition.z - 0.1 && feetPosition.z <= blockPosition.z + 1.1;
    }

    public static void collectEntitiesOnTopOfBlock(
            @Nonnull Store<EntityStore> entityStore,
            Vector3i blockPosition,
            @Nonnull Set<Ref<EntityStore>> out) {
        Vector3d min = new Vector3d(blockPosition.x - 0.1, blockPosition.y + 0.9, blockPosition.z - 0.1);
        Vector3d max = new Vector3d(blockPosition.x + 1.1, blockPosition.y + 2.1, blockPosition.z + 1.1);

        for (var ref : TargetUtil.getAllEntitiesInBox(min, max, entityStore)) {
            if (ref == null || !ref.isValid())
                continue;

            TransformComponent transform = entityStore.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null)
                continue;

            BoundingBox boundingBox = entityStore.getComponent(ref, BoundingBox.getComponentType());
            Vector3d feet = getFeetPosition(transform, boundingBox);

            if (isFeetOnTopOfBlock(feet, blockPosition))
                out.add(ref);
        }
    }

    public static boolean isEmpty(@Nullable BlockType blockType, int blockID) {
        if (blockID == 0) return true;
        return isEmpty(blockType);
    }

    public static boolean isEmpty(@Nullable BlockType blockType) {
        if (blockType == null) return true;
        return blockType.getMaterial() == BlockMaterial.Empty;
    }
}
