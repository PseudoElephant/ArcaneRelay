package com.arcanerelay.interactions;

import com.arcanerelay.components.ArcaneSection;
import com.arcanerelay.config.Activation;
import com.arcanerelay.core.activation.ArcaneCachedAccessor;
import com.arcanerelay.util.ArcaneUtil;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import org.joml.Vector3d;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.List;

import javax.annotation.Nonnull;

/**
 * Runs the arcane Activation for the target block (from bindings).
 * When used in a block's Use chain, the target is the block being used.
 * Executes the activation immediately (e.g. Pusher_Chain for the pusher).
 */
public class ArcaneActivatorInteraction extends SimpleInstantInteraction {
    private static final double TARGET_DISTANCE = 10.0;

    @Nonnull
    public static final BuilderCodec<ArcaneActivatorInteraction> CODEC = BuilderCodec.builder(
            ArcaneActivatorInteraction.class, ArcaneActivatorInteraction::new, SimpleInstantInteraction.CODEC)
            .documentation("ArcaneRelay: run the arcane Activation for the target block.")
            .append(new KeyedCodec<>("Activator", Codec.STRING, true), (i, a) -> i.activator = a, i -> i.activator)
            .add()
            .build();

    @javax.annotation.Nullable
    private String activator;

    public ArcaneActivatorInteraction() {
    }

    public ArcaneActivatorInteraction(String id) {
        super(id);
    }

    /** Activation ID to run. When null/empty, uses the block's binding. */
    @javax.annotation.Nullable
    public String getActivator() {
        return activator;
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
        CommandBuffer<EntityStore> cb = context.getCommandBuffer();
        if (cb == null) return;

        Ref<EntityStore> ref = context.getEntity();
        Player player = cb.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        PlayerRef playerRef = cb.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;

        TransformComponent playerTransform = cb.getComponent(ref, TransformComponent.getComponentType());
        if (playerTransform == null) return;

        Vector3d playerPosition = playerTransform.getPosition();
 
        int blockX, blockY, blockZ;
        BlockPosition targetRaw = context.getMetaStore().getMetaObject(Interaction.TARGET_BLOCK_RAW);
        if (targetRaw != null) {
            blockX = targetRaw.x;
            blockY = targetRaw.y;
            blockZ = targetRaw.z;
        } else {
            var target = TargetUtil.getTargetBlock(ref, TARGET_DISTANCE, cb);
            if (target == null) {
                NotificationUtil.sendNotification(playerRef.getPacketHandler(), Message.translation("server.arcanerelay.notifications.noBlockInRange"), NotificationStyle.Warning);
                context.getState().state = InteractionState.Failed;
                return;
            }
            blockX = target.x;
            blockY = target.y;
            blockZ = target.z;
        }

        World world = cb.getExternalData().getWorld();
        var blockType = world.getBlockType(blockX, blockY, blockZ);
        if (blockType == null) {
            NotificationUtil.sendNotification(playerRef.getPacketHandler(), Message.translation("server.arcanerelay.notifications.noBlockAtTarget"), NotificationStyle.Warning);
            context.getState().state = InteractionState.Failed;
            return;
        }

        Activation activation = (activator != null && !activator.isEmpty())
                ? Activation.getActivation(activator)
                : ArcaneUtil.getActivationForBlock(blockType);
        if (activation == null) {
            context.getState().state = InteractionState.Finished;
            return;
        }

        long chunkIndex = ChunkUtil.indexChunkFromBlock(blockX, blockZ);
        WorldChunk chunk = world.getChunk(chunkIndex);
        if (chunk == null) {
            context.getState().state = InteractionState.Finished;
            return;
        }

        Store<ChunkStore> store = world.getChunkStore().getStore();
        Ref<ChunkStore> sectionRef = world.getChunkStore().getChunkSectionReference(
            ChunkUtil.chunkCoordinate(blockX),
            ChunkUtil.chunkCoordinate(blockY),
            ChunkUtil.chunkCoordinate(blockZ));
        if (sectionRef == null) {
            context.getState().state = InteractionState.Finished;
            return;
        }

        ChunkSection chunkSection = store.getComponent(sectionRef, ChunkSection.getComponentType());
        if (chunkSection == null) {
            context.getState().state = InteractionState.Finished;
            return;
        }

        ArcaneSection arcaneSection = store.getComponent(sectionRef, ArcaneSection.getComponentType());
        if (arcaneSection == null) {
            context.getState().state = InteractionState.Finished;
            return;
        }

        BlockSection blockSection = store.getComponent(sectionRef, BlockSection.getComponentType());
        if (blockSection == null) {
            context.getState().state = InteractionState.Finished;
            return;
        }

        BlockComponentChunk blockComponentChunk = store.getComponent(chunkSection.getChunkColumnReference(), BlockComponentChunk.getComponentType());
        Ref<ChunkStore> blockRef = blockComponentChunk != null
            ? blockComponentChunk.getEntityReference(ChunkUtil.indexBlockInColumn(blockX, blockY, blockZ))
            : null;

        ArcaneCachedAccessor accessor = ArcaneCachedAccessor.ofForInteraction(cb, arcaneSection, blockSection, chunkSection, 1);
        activation.execute(accessor, sectionRef, blockRef, blockX, blockY, blockZ, List.of());

        context.getState().state = InteractionState.Finished;
    }
}
