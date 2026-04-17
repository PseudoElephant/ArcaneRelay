package com.arcanerelay.core.activation;

import com.arcanerelay.ArcaneRelayPlugin;
import com.arcanerelay.components.ArcaneTriggerBlock;
import com.arcanerelay.config.Activation;
import com.arcanerelay.config.ActivationEffects;
import com.arcanerelay.util.ArcaneUtil;
import com.arcanerelay.util.BlockUtil;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;

import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class ActivationExecutor {

    public static void execute(
        @Nonnull World world,
        @Nonnull Store<ChunkStore> store,
        @Nonnull WorldChunk chunk,
        int blockX,
        int blockY,
        int blockZ,
        @Nonnull BlockType blockType,
        @Nonnull Activation activation,
        @Nonnull List<int[]> sources
    ) {
        int[] main = BlockUtil.findMainBlock(world, chunk, blockX, blockY, blockZ);
        if (main == null) return;

        int mainX = main[0], mainY = main[1], mainZ = main[2];
        WorldChunk mainChunk = world.getChunk(ChunkUtil.indexChunkFromBlock(mainX, mainZ));
        if (mainChunk == null) return;

        BlockType mainBlockType = mainChunk.getBlockType(mainX, mainY, mainZ);
        if (mainBlockType == null) return;


        // activation.execute(commandBuffer, blockRef, sectionRef, mainX, mainY, mainZ);
    }

    /** Sets each output block to ticking with (worldX, worldY, worldZ) as the source. */
    public static void sendSignals(@Nonnull ComponentAccessor<ChunkStore> accessor, @Nullable Ref<ChunkStore> blockRef, int worldX, int worldY, int worldZ) {
        if (blockRef == null || !blockRef.isValid()) return;

        ArcaneTriggerBlock trigger = accessor.getComponent(blockRef, ArcaneRelayPlugin.get().getArcaneTriggerBlockComponentType());
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
        if (soundEventIndex != 0) {
            ComponentAccessor<EntityStore> accessor = world.getEntityStore().getStore();
            SoundUtil.playSoundEvent3d(soundEventIndex, SoundCategory.SFX, blockX + 0.5, blockY + 0.5, blockZ + 0.5, accessor);
        }
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
        SoundUtil.playSoundEvent3d(soundIndex, SoundCategory.SFX, x, y, z, accessor);
    }
}
