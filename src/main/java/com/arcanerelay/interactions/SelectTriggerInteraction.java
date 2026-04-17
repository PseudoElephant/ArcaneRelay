package com.arcanerelay.interactions;

import com.arcanerelay.components.ArcaneConfiguratorComponent;
import com.arcanerelay.components.ArcaneTriggerBlock;
import com.arcanerelay.util.VisualsUtil;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import org.joml.Vector3i;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.math.util.ChunkUtil;
import org.joml.Vector3d;
import org.joml.Vector3f;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

/**
 * Primary interaction: select an Arcane Trigger block as the one being configured.
 * Stores the block position in ArcaneConfiguratorComponent.
 */
public class SelectTriggerInteraction extends SimpleInstantInteraction {
    @Nonnull
    public static final BuilderCodec<SelectTriggerInteraction> CODEC = BuilderCodec.builder(
            SelectTriggerInteraction.class, SelectTriggerInteraction::new, SimpleInstantInteraction.CODEC)
            .documentation("ArcaneRelay: select an Arcane Trigger block to configure.")
            .build();

    public SelectTriggerInteraction() { }

    public SelectTriggerInteraction(String id) {
        super(id);
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
        CommandBuffer<EntityStore> cb = context.getCommandBuffer();
        if (cb == null) {
            context.getState().state = InteractionState.Failed; 
            return;
        };

        Ref<EntityStore> ref = context.getEntity();
        Player player = cb.getComponent(ref, Player.getComponentType());

        PlayerRef playerRef = cb.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) 
        {
            context.getState().state = InteractionState.Failed; 
            return;
        }

        ArcaneConfiguratorComponent configurator = cb.getComponent(ref, ArcaneConfiguratorComponent.getComponentType());
        if (player == null || configurator == null) {
            context.getState().state = InteractionState.Failed; 
            return;
        };

        BlockPosition targetPosition = context.getTargetBlock();
        if (targetPosition == null) {
            NotificationUtil.sendNotification(playerRef.getPacketHandler(), Message.translation("server.arcanerelay.notifications.noBlockInRange"), NotificationStyle.Warning);
            context.getState().state = InteractionState.Failed; 
            return;
        }

        Vector3i target = new Vector3i(targetPosition.x, targetPosition.y, targetPosition.z);
        
        World world = cb.getExternalData().getWorld();
        WorldChunk chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(target.x, target.z));
        if (chunk == null) {
            context.getState().state = InteractionState.Failed; 
            return;
        }

        Ref<ChunkStore> blockRef = chunk.getBlockComponentEntity(target.x, target.y, target.z);
        if (blockRef == null || !blockRef.isValid()) {
            NotificationUtil.sendNotification(playerRef.getPacketHandler(), Message.translation("server.arcanerelay.notifications.targetMustBeArcaneTrigger"), NotificationStyle.Warning);
            context.getState().state = InteractionState.Failed; 
            return;
        }

        Store<ChunkStore> store = world.getChunkStore().getStore();

        ArcaneTriggerBlock trigger = store.getComponent(blockRef, ArcaneTriggerBlock.getComponentType());
        if (trigger == null) {
            NotificationUtil.sendNotification(playerRef.getPacketHandler(), Message.translation("server.arcanerelay.notifications.targetMustBeArcaneTrigger"), NotificationStyle.Warning);
            context.getState().state = InteractionState.Failed; 
            return;
        }

        ArcaneConfiguratorComponent updated = (ArcaneConfiguratorComponent) configurator.clone();
        updated.setConfiguredBlock(target);
        cb.putComponent(ref, ArcaneConfiguratorComponent.getComponentType(), updated);

        boolean cycleColor = true;
        VisualsUtil.displayTriggerConnections(world, target, cycleColor);

        NotificationUtil.sendNotification(playerRef.getPacketHandler(), Message.translation("server.arcanerelay.notifications.triggerSelected"), NotificationStyle.Success);
        context.getState().state = InteractionState.Finished;
    }
}
