package com.arcanerelay.interactions;

import com.arcanerelay.ArcaneRelayPlugin;
import com.arcanerelay.asset.Activation;
import com.arcanerelay.asset.ActivationExecutor;
import com.arcanerelay.asset.ActivationRegistry;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

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

        int blockX, blockY, blockZ;
        BlockPosition targetRaw = context.getMetaStore().getMetaObject(Interaction.TARGET_BLOCK_RAW);
        if (targetRaw != null) {
            blockX = targetRaw.x;
            blockY = targetRaw.y;
            blockZ = targetRaw.z;
        } else {
            var target = TargetUtil.getTargetBlock(ref, TARGET_DISTANCE, cb);
            if (target == null) {
                NotificationUtil.sendNotification(player.getPlayerRef().getPacketHandler(), Message.raw("No block in range."), NotificationStyle.Warning);
                context.getState().state = InteractionState.Failed;
                return;
            }
            blockX = target.getX();
            blockY = target.getY();
            blockZ = target.getZ();
        }

        World world = cb.getExternalData().getWorld();
        var blockType = world.getBlockType(blockX, blockY, blockZ);
        if (blockType == null) {
            NotificationUtil.sendNotification(player.getPlayerRef().getPacketHandler(), Message.raw("No block at target."), NotificationStyle.Warning);
            context.getState().state = InteractionState.Failed;
            return;
        }

        ActivationRegistry registry = ArcaneRelayPlugin.get().getActivationRegistry();
        Activation activation = (activator != null && !activator.isEmpty())
                ? registry.getActivation(activator)
                : registry.getActivationForBlock(blockType.getId());
        if (activation == null) {
            context.getState().state = InteractionState.Finished;
            return;
        }

        var chunkStore = world.getChunkStore().getStore();
        long chunkIndex = ChunkUtil.indexChunkFromBlock(blockX, blockZ);
        WorldChunk chunk = world.getChunk(chunkIndex);
        if (chunk == null) {
            context.getState().state = InteractionState.Finished;
            return;
        }

        List<int[]> sources = Collections.singletonList(new int[]{blockX, blockY, blockZ});
        world.execute(() ->
                ActivationExecutor.execute(world, chunkStore, chunk, blockX, blockY, blockZ, blockType, activation, sources)
        );

        context.getState().state = InteractionState.Finished;
    }
}
