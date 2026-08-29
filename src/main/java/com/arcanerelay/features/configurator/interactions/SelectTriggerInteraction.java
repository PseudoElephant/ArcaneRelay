package com.arcanerelay.features.configurator.interactions;

import com.arcanerelay.features.configurator.components.ArcaneConfiguratorComponent;
import com.arcanerelay.features.configurator.util.VisualsUtil;
import com.arcanerelay.features.signaltrigger.components.ArcaneTriggerBlock;
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
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.math.util.ChunkUtil;
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
            setFailed(context);
            return;
        }

        Ref<EntityStore> ref = context.getEntity();
        Player player = cb.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = cb.getComponent(ref, PlayerRef.getComponentType());
        ArcaneConfiguratorComponent configurator = cb.getComponent(ref, ArcaneConfiguratorComponent.getComponentType());

        if (player == null || playerRef == null || configurator == null) {
            setFailed(context);
            return;
        }

        boolean crouching = isCrouching(cb, ref);
        BlockPosition targetPosition = context.getTargetBlock();

        if (targetPosition == null) {
            if (!configurator.getSelectedBlocks().isEmpty()) {
                if (!crouching) {
                    configurator.clearConfiguredBlocks();
                    sendRelayNotification(playerRef, "triggerDeselected", NotificationStyle.Default);
                }
                setFinished(context);
            } else {
                sendRelayNotification(playerRef, "noBlockInRange", NotificationStyle.Warning);
                setFailed(context);
            }
            return;
        }

        Vector3i target = new Vector3i(targetPosition.x, targetPosition.y, targetPosition.z);
        World world = cb.getExternalData().getWorld();
        WorldChunk chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(target.x, target.z));
        
        if (chunk == null) {
            setFailed(context);
            return;
        }

        if (!isValidArcaneTrigger(world, chunk, target)) {
            if (!configurator.getSelectedBlocks().isEmpty()) {
                configurator.clearConfiguredBlocks();
                sendRelayNotification(playerRef, "triggerDeselected", NotificationStyle.Default);
                setFinished(context);
            } else {
                sendRelayNotification(playerRef, "targetMustBeArcaneTrigger", NotificationStyle.Warning);
                setFailed(context);
            }
            return;
        }

        handleTriggerSelection(playerRef, configurator, target, crouching);
        setFinished(context);
    }

    private boolean isCrouching(CommandBuffer<EntityStore> cb, Ref<EntityStore> ref) {
        MovementStatesComponent states = cb.getComponent(ref, MovementStatesComponent.getComponentType());
        return states != null && states.getMovementStates().crouching;
    }

    private boolean isValidArcaneTrigger(World world, WorldChunk chunk, Vector3i target) {
        Ref<ChunkStore> blockRef = chunk.getBlockComponentEntity(target.x, target.y, target.z);
        if (blockRef == null || !blockRef.isValid()) {
            return false;
        }
        
        Store<ChunkStore> store = world.getChunkStore().getStore();
        return store.getComponent(blockRef, ArcaneTriggerBlock.getComponentType()) != null;
    }

    private void handleTriggerSelection(PlayerRef playerRef, ArcaneConfiguratorComponent configurator, Vector3i target, boolean crouching) {
        boolean wasPreviouslySelected = configurator.isSelected(target);
        boolean multipleSelected = configurator.getSelectedBlocks().size() > 1;

        if (crouching) {
            if (wasPreviouslySelected) {
                configurator.removeSelectedBlock(target);
                sendRelayNotification(playerRef, "triggerDeselected", NotificationStyle.Default);
                return;
            } 

            configurator.addSelectedBlock(target);
            sendRelayNotification(playerRef, "triggerSelected", NotificationStyle.Success);
            return;
        } 

        if (wasPreviouslySelected && !multipleSelected) {
            configurator.clearConfiguredBlocks();
            sendRelayNotification(playerRef, "triggerDeselected", NotificationStyle.Default);
            return;
        } 
            
        configurator.clearConfiguredBlocks();
        configurator.addSelectedBlock(target);
        sendRelayNotification(playerRef, "triggerSelected", NotificationStyle.Success); 
    }

    private void sendRelayNotification(PlayerRef playerRef, String messageKey, @Nonnull NotificationStyle style) {
        NotificationUtil.sendNotification(
            playerRef.getPacketHandler(), 
            Message.translation("server.arcanerelay.notifications." + messageKey), 
            style
        );
    }

    private void setFailed(InteractionContext context) {
        context.getState().state = InteractionState.Failed;
    }

    private void setFinished(InteractionContext context) {
        context.getState().state = InteractionState.Finished;
    }
}
