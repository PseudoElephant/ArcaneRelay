package com.pseudoelephant.arcanerelay.core.activation;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.pseudoelephant.arcanerelay.features.activation.ActivationEffects;
import com.pseudoelephant.arcanerelay.features.signal.util.ArcaneUtil;
import com.pseudoelephant.arcanerelay.features.signaltrigger.components.ArcaneTriggerBlock;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class ActivationExecutor {
    /** Sets each output block to ticking with (worldX, worldY, worldZ) as the source. */
    public static void sendSignals(@Nonnull ComponentAccessor<ChunkStore> accessor, @Nullable Ref<ChunkStore> blockRef, int worldX, int worldY, int worldZ) {
        if (blockRef == null || !blockRef.isValid()) return;

        ComponentType<ChunkStore, ArcaneTriggerBlock> triggerBlockComponent = ArcaneTriggerBlock.getComponentType();
        if (triggerBlockComponent == null) return;

        ArcaneTriggerBlock trigger = accessor.getComponent(blockRef, triggerBlockComponent);
        if (trigger == null) return;

        for (Vector3i out : trigger.getOutputPositions()) {
            ArcaneUtil.setTicking(accessor, out.x, out.y, out.z, worldX, worldY, worldZ);
        }
    }

    public static void playBlockInteractionSound(
        @Nonnull World world,
        int blockX,
        int blockY,
        int blockZ,
        @Nonnull BlockType blockType
    ) {
        int soundEventIndex = blockType.getInteractionSoundEventIndex();
        if (soundEventIndex == 0) return;

        ComponentAccessor<EntityStore> accessor = world.getEntityStore().getStore();
        if (accessor == null) return;

        SoundUtil.playSoundEvent3d(soundEventIndex, SoundCategory.SFX, blockX + 0.5, blockY + 0.5, blockZ + 0.5, accessor);
    }

    public static void playEffects(
        @Nonnull World world,
        int blockX,
        int blockY,
        int blockZ,
        @Nullable ActivationEffects effects
    ) {
        if (effects == null) return;

        String soundId = effects.getWorldSoundEventId();
        if (soundId == null || soundId.isEmpty()) return;

        int soundIndex = SoundEvent.getAssetMap().getIndex(soundId);
        if (soundIndex == Integer.MIN_VALUE || soundIndex == 0) return;

        double x = blockX + 0.5, y = blockY + 0.5, z = blockZ + 0.5;
        ComponentAccessor<EntityStore> accessor = world.getEntityStore().getStore();
        if (accessor == null) return;

        SoundUtil.playSoundEvent3d(soundIndex, SoundCategory.SFX, x, y, z, accessor);
    }
}
