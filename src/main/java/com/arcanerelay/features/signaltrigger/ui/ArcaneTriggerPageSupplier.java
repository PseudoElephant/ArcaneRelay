package com.arcanerelay.features.signaltrigger.ui;

import com.arcanerelay.features.signaltrigger.components.ArcaneTriggerBlock;
import com.arcanerelay.util.BlockUtil;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Supplies {@link ArcaneTriggerSettingsPage} when the player interacts with a block
 * using OpenCustomUI with Page "ArcaneTrigger". Resolves the target block entity;
 * creates the block entity with ArcaneTrigger component if it does not exist.
 */
public class ArcaneTriggerPageSupplier implements OpenCustomUIInteraction.CustomPageSupplier {

    public static final BuilderCodec<ArcaneTriggerPageSupplier> CODEC =
        BuilderCodec.builder(ArcaneTriggerPageSupplier.class, ArcaneTriggerPageSupplier::new).build();

    @Nullable
    @Override
    public CustomUIPage tryCreate(
            @Nonnull Ref<EntityStore> ref,
            ComponentAccessor<EntityStore> componentAccessor,
            @Nonnull PlayerRef playerRef,
            @Nonnull InteractionContext context) {
        BlockPosition targetBlock = context.getTargetBlock();
        if (targetBlock == null) return null; 

        ComponentAccessor<EntityStore> entityAccessor = ref.getStore();
        World world = entityAccessor.getExternalData().getWorld();
        ChunkStore chunkStore = world.getChunkStore();

        ComponentAccessor<ChunkStore> chunkAccesor = chunkStore.getStore();
        Ref<ChunkStore> blockRef = BlockUtil.getBlockEntityReference(chunkAccesor, targetBlock.x, targetBlock.y, targetBlock.z);
        if (blockRef == null || !blockRef.isValid()) return null;

        ArcaneTriggerBlock trigger = chunkAccesor.getComponent(blockRef, ArcaneTriggerBlock.getComponentType());
        if (trigger == null) return null;

        return new ArcaneTriggerSettingsPage(playerRef, blockRef);
    }
}
