package com.arcanerelay.features.configurator.interactions;

import com.arcanerelay.ArcaneRelayPlugin;
import com.arcanerelay.features.configurator.components.ArcaneConfiguratorComponent;
import com.arcanerelay.features.configurator.util.VisualsUtil;
import com.arcanerelay.features.signaltrigger.components.ArcaneTriggerBlock;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;

import org.joml.Vector3i;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

/**
 * Secondary interaction: add target block as output connection to the currently configured trigger.
 * Uses ArcaneConfiguratorComponent to find the trigger block.
 */
public class AddOutputInteraction extends SimpleInstantInteraction {
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

        if (player == null || playerRef == null || configurator == null) return;

        Set<Vector3i> selectedTriggers = configurator.getSelectedBlocks().keySet();
        if (selectedTriggers.isEmpty()) {
            sendRelayNotification(playerRef, "selectTriggerFirst", NotificationStyle.Warning);
            setFailed(context);
            return;
        }

        BlockPosition targetPosition = context.getTargetBlock();
        if (targetPosition == null) {
            sendRelayNotification(playerRef, "noBlockInRange", NotificationStyle.Warning);
            setFailed(context);
            return;
        }

        Vector3i target = new Vector3i(targetPosition.x, targetPosition.y, targetPosition.z);
        if (!areTriggersInRange(selectedTriggers, target, playerRef, context)) {
            return; 
        }

        World world = cb.getExternalData().getWorld();
        boolean successfullyProcessed = processTriggerConnections(world, selectedTriggers, target, playerRef);
        
        if (successfullyProcessed) {
            setFinished(context);
        }
    }

    private boolean areTriggersInRange(Set<Vector3i> triggers, Vector3i target, PlayerRef playerRef, InteractionContext context) {
        int maxRelayDistance = ArcaneRelayPlugin.get().getConfig().getRelayDistance();
        boolean isMulti = triggers.size() > 1;

        for (Vector3i triggerPos : triggers) {
            if (triggerPos.equals(target)) {
                sendRelayNotification(playerRef, "targetSameAsTrigger", NotificationStyle.Warning);
                setFailed(context);
                return false;
            }
            if (triggerPos.distance(target) > maxRelayDistance) {
                String errorKey = isMulti ? "someTriggersTooFar" : "targetTooFarFromTrigger";
                sendRelayNotification(playerRef, errorKey, NotificationStyle.Warning);
                setFailed(context);
                return false;
            }
        }
        return true;
    }

    private boolean processTriggerConnections(World world, Set<Vector3i> triggers, @Nonnull Vector3i target, PlayerRef playerRef) {
        Store<ChunkStore> store = world.getChunkStore().getStore();
        List<ArcaneTriggerBlock> validatedTriggers = new ArrayList<>();

        for (Vector3i triggerPos : triggers) {
            Ref<ChunkStore> blockRef = BlockModule.getBlockEntity(world, triggerPos.x, triggerPos.y, triggerPos.z);
            if (blockRef == null || !blockRef.isValid()) return false; 
            
            ArcaneTriggerBlock comp = store.getComponent(blockRef, ArcaneTriggerBlock.getComponentType());
            if (comp == null) return false;
            
            validatedTriggers.add(comp);
        }

        // Instead of toggling output connections if multi-selecting triggers, we first ensure the new output is added to all selections.
        // Then it's either all added or all removed.
        int existingConnections = 0;
        for (ArcaneTriggerBlock comp : validatedTriggers) {
            if (comp.getOutputPositions().contains(target)) {
                existingConnections++;
            }
        }

        boolean removeMode = (existingConnections == validatedTriggers.size());
        boolean isMulti = validatedTriggers.size() > 1;

        for (ArcaneTriggerBlock comp : validatedTriggers) {
            boolean hasOutput = comp.getOutputPositions().contains(target);

            if (removeMode) {
                comp.removeOutputPosition(target);
            } else if (!hasOutput) {
                comp.addOutputPosition(target);
            }
        }

        if (removeMode) {
            String msgKey = isMulti ? "multipleOutputsRemoved" : "outputRemoved";
            sendRelayNotification(playerRef, msgKey, NotificationStyle.Default);
        } else {
            String msgKey = isMulti ? "multipleOutputsAdded" : "outputAdded";
            sendRelayNotification(playerRef, msgKey, NotificationStyle.Success);
        }

        return true;
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
