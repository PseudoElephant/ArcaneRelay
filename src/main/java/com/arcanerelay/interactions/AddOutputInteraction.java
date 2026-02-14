package com.arcanerelay.interactions;

import com.arcanerelay.components.ArcaneConfiguratorComponent;
import com.arcanerelay.components.ArcaneTriggerBlock;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

/**
 * Secondary interaction: add target block as output connection to the currently configured trigger.
 * Uses ArcaneConfiguratorComponent to find the trigger block.
 */
public class AddOutputInteraction extends SimpleInstantInteraction {
    private static final double TRIGGER_DISTANCE = 10.0;

    @Nonnull
    public static final BuilderCodec<AddOutputInteraction> CODEC = BuilderCodec.builder(
            AddOutputInteraction.class, AddOutputInteraction::new, SimpleInstantInteraction.CODEC)
            .documentation("ArcaneRelay: add target block as output connection to the selected trigger.")
            .build();

    public AddOutputInteraction() {
    }

    public AddOutputInteraction(String id) {
        super(id);
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
        CommandBuffer<EntityStore> cb = context.getCommandBuffer();
        if (cb == null) return;

        Ref<EntityStore> ref = context.getEntity();
        Player player = cb.getComponent(ref, Player.getComponentType());

        PlayerRef playerRef = cb.getComponent(ref, PlayerRef.getComponentType());
        ArcaneConfiguratorComponent configurator = cb.getComponent(ref, ArcaneConfiguratorComponent.getComponentType());
        if (player == null || configurator == null) return;

        Vector3i triggerPos = configurator.getConfiguredBlock();
        if (triggerPos == null) {
            NotificationUtil.sendNotification(playerRef.getPacketHandler(), Message.translation("server.arcanerelay.notifications.selectTriggerFirst"), NotificationStyle.Warning);
            context.getState().state = InteractionState.Failed; 
            return;
        }

        BlockPosition targetPosition = context.getTargetBlock();
        if (targetPosition == null) {
            NotificationUtil.sendNotification(playerRef.getPacketHandler(), Message.translation("server.arcanerelay.notifications.noBlockInRange"), NotificationStyle.Warning);
            context.getState().state = InteractionState.Failed; 
            return;
        }

        Vector3i target = new Vector3i(targetPosition.x, targetPosition.y, targetPosition.z);
        if (triggerPos.equals(target)) {
            NotificationUtil.sendNotification(playerRef.getPacketHandler(), Message.translation("server.arcanerelay.notifications.targetSameAsTrigger"), NotificationStyle.Warning);
            context.getState().state = InteractionState.Failed; 
            return;
        }

        if (triggerPos.distanceTo(target) > TRIGGER_DISTANCE) {
            NotificationUtil.sendNotification(playerRef.getPacketHandler(), Message.translation("server.arcanerelay.notifications.targetTooFarFromTrigger"), NotificationStyle.Warning);
            context.getState().state = InteractionState.Failed; 
            return;
        }

        Vector3i outputPos = target.clone();
        World world = cb.getExternalData().getWorld();
        
        var chunkStore = world.getChunkStore();
        var store = chunkStore.getStore();
        Ref<ChunkStore> blockRef = BlockModule.getBlockEntity(world, triggerPos.x, triggerPos.y, triggerPos.z);
        if (blockRef == null || !blockRef.isValid()) return;

        ArcaneTriggerBlock comp = store.getComponent(blockRef, ArcaneTriggerBlock.getComponentType());
        if (comp == null) return;

        if (comp.getOutputPositions().contains(outputPos)) {
            world.execute(() -> {
                comp.removeOutputPosition(outputPos.x, outputPos.y, outputPos.z);
                store.putComponent(blockRef, ArcaneTriggerBlock.getComponentType(), comp);
            });

            NotificationUtil.sendNotification(playerRef.getPacketHandler(), Message.translation("server.arcanerelay.notifications.outputRemoved"), NotificationStyle.Warning);
            context.getState().state = InteractionState.Finished;
            return;
        }

        world.execute(() -> {
            comp.addOutputPosition(outputPos);
            store.putComponent(blockRef, ArcaneTriggerBlock.getComponentType(), comp);
            SelectTriggerInteraction.addTriggerToOutputArrows(world, triggerPos);
        });

        NotificationUtil.sendNotification(playerRef.getPacketHandler(), Message.translation("server.arcanerelay.notifications.outputAdded"), NotificationStyle.Success);
        context.getState().state = InteractionState.Finished;
        return;
    }
}
