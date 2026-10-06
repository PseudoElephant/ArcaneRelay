package com.pseudoelephant.arcanerelay.features.signal.util;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.pseudoelephant.arcanerelay.ArcaneRelayPlugin;
import com.pseudoelephant.arcanerelay.features.activation.Activation;
import com.pseudoelephant.arcanerelay.features.activation.ActivationBinding;
import com.pseudoelephant.arcanerelay.features.signal.components.ArcaneSection;

public class ArcaneUtil {
    
    public static void setTicking(@Nonnull ComponentAccessor<ChunkStore> store, int worldX, int worldY, int worldZ) {
        World world = store.getExternalData().getWorld();
        Ref<ChunkStore> sectionRef = world.getChunkStore()
                .getChunkSectionReference(ChunkUtil.chunkCoordinate(worldX), ChunkUtil.chunkCoordinate(worldY), ChunkUtil.chunkCoordinate(worldZ));
        if (sectionRef == null) return;

        ArcaneSection arcaneSection = store.getComponent(sectionRef, ArcaneSection.getComponentType());
        if (arcaneSection == null) return;

        arcaneSection.setTicking(worldX, worldY, worldZ, true);
    }

    public static void clearTicking(@Nonnull ComponentAccessor<ChunkStore> store, int worldX, int worldY, int worldZ) {
        World world = store.getExternalData().getWorld();
        Ref<ChunkStore> sectionRef = world.getChunkStore()
                .getChunkSectionReference(ChunkUtil.chunkCoordinate(worldX), ChunkUtil.chunkCoordinate(worldY), ChunkUtil.chunkCoordinate(worldZ));
        if (sectionRef == null) return;

        ArcaneSection arcaneSection = store.getComponent(sectionRef, ArcaneSection.getComponentType());
        if (arcaneSection == null) return;

        ArcaneRelayPlugin.LOGGER.atInfo().log("Clearing ticking at %d,%d,%d", worldX, worldY, worldZ);
        arcaneSection.setTicking(worldX, worldY, worldZ, false);
    }

    /** Sets a block to ticking and records the source position for when it is processed. */
    public static void setTicking(@Nonnull ComponentAccessor<ChunkStore> accessor,
            int worldX, int worldY, int worldZ,
            int sourceX, int sourceY, int sourceZ) {
        World world = accessor.getExternalData().getWorld();
        Ref<ChunkStore> sectionRef = world.getChunkStore()
                .getChunkSectionReference(ChunkUtil.chunkCoordinate(worldX), ChunkUtil.chunkCoordinate(worldY), ChunkUtil.chunkCoordinate(worldZ));
        if (sectionRef == null) return;

        ArcaneSection arcaneSection = accessor.getComponent(sectionRef, ArcaneSection.getComponentType());
        if (arcaneSection == null) return;

        arcaneSection.setTicking(worldX, worldY, worldZ, true, sourceX, sourceY, sourceZ);
    }

    /**
     * Returns the original/base block type ID for binding and lookup, instead of the state-specific ID.
     * For blocks with an associated item (e.g. doors, gates), this is the item ID; otherwise the block's own ID.
     */
    @Nonnull
    public static String getOriginalBlockTypeId(@Nonnull BlockType blockType) {
        String id = blockType.getId();
        if (id == null) return "";

        var item = blockType.getItem();
        if (item != null) {
            String itemId = item.getId();
            if (itemId != null) {
                id = itemId;
            }
        }

        return id;
    }

    /** Returns the Activation for a block type, or null if none. */
    @Nullable
    public static Activation getActivationForBlock(@Nonnull BlockType blockType) {
        String blockTypeId = getOriginalBlockTypeId(blockType);
        return Activation.getActivation(ActivationBinding.getActivationId(blockTypeId));
    }
}
