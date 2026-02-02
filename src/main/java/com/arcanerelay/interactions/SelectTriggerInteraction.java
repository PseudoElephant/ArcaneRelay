package com.arcanerelay.interactions;

import com.arcanerelay.components.ArcaneConfiguratorComponent;
import com.arcanerelay.components.ArcaneTriggerBlock;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

/**
 * Primary interaction: select an Arcane Trigger block as the one being configured.
 * Stores the block position in ArcaneConfiguratorComponent.
 */
public class SelectTriggerInteraction extends SimpleInstantInteraction {

    private static final double TARGET_DISTANCE = 20.0;

    @Nonnull
    public static final BuilderCodec<SelectTriggerInteraction> CODEC = BuilderCodec.builder(
            SelectTriggerInteraction.class, SelectTriggerInteraction::new, SimpleInstantInteraction.CODEC)
            .documentation("ArcaneRelay: select an Arcane Trigger block to configure.")
            .build();

    public SelectTriggerInteraction() {
    }

    public SelectTriggerInteraction(String id) {
        super(id);
    }

    @Override
       protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
        CommandBuffer<EntityStore> cb = context.getCommandBuffer();
        if (cb == null) return;

        Ref<EntityStore> ref = context.getEntity();
        Player player = cb.getComponent(ref, Player.getComponentType());


        PlayerRef playerRef = cb.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;

        ArcaneConfiguratorComponent configurator = cb.getComponent(ref, ArcaneConfiguratorComponent.getComponentType());
        if (player == null || configurator == null) return;

        Vector3i target = TargetUtil.getTargetBlock(ref, TARGET_DISTANCE, cb);
        if (target == null) {
            NotificationUtil.sendNotification(playerRef.getPacketHandler(), Message.raw("No block in range."), NotificationStyle.Warning);
            context.getState().state = InteractionState.Failed; 
            return;
        }
        

        World world = cb.getExternalData().getWorld();
        if (BlockModule.get().getComponent(ArcaneTriggerBlock.getComponentType(), world, target.x, target.y, target.z) == null) {
            NotificationUtil.sendNotification(playerRef.getPacketHandler(), Message.raw("Target must be an Arcane Trigger block."), NotificationStyle.Warning);
            context.getState().state = InteractionState.Failed; 
            return;
        }

        ArcaneConfiguratorComponent updated = (ArcaneConfiguratorComponent) configurator.clone();
        updated.setConfiguredBlock(target);
        cb.putComponent(ref, ArcaneConfiguratorComponent.getComponentType(), updated);

        String nameplateText = buildTriggerNameplateText(world, target);
        addTriggerToOutputArrows(world, target);

        NotificationUtil.sendNotification(playerRef.getPacketHandler(), Message.raw("Trigger has been selected."), NotificationStyle.Success);
        context.getState().state = InteractionState.Finished;
    }

    private static String buildTriggerNameplateText(World world, Vector3i triggerPos) {
        ArcaneTriggerBlock triggerBlock = BlockModule.get().getComponent(ArcaneTriggerBlock.getComponentType(), world, triggerPos.x, triggerPos.y, triggerPos.z);
        String text = "Trigger: " + triggerPos.x + "," + triggerPos.y + "," + triggerPos.z;
        if (triggerBlock != null && triggerBlock.hasOutputPositions()) {
            text += " | Outputs: " + triggerBlock.getOutputPositions().size();
        }
        return text;
    }

    /** Draw debug arrows from trigger to each output; call after updating trigger outputs (e.g. from AddOutputInteraction). */
    public static void addTriggerToOutputArrows(World world, Vector3i triggerPos) {
        ArcaneTriggerBlock triggerBlock = BlockModule.get().getComponent(ArcaneTriggerBlock.getComponentType(), world, triggerPos.x, triggerPos.y, triggerPos.z);
        if (triggerBlock == null || !triggerBlock.hasOutputPositions()) return;

        Vector3d from = new Vector3d(triggerPos.x + 0.5, triggerPos.y + 0.5, triggerPos.z + 0.5);
        Vector3f color = new Vector3f(0.2f, 0.8f, 1.0f);
        float time = 10.0f;

        for (Vector3i out : triggerBlock.getOutputPositions()) {
            Vector3d to = new Vector3d(out.x + 0.5, out.y + 0.5, out.z + 0.5);
            Vector3d direction = to.clone().subtract(from);
            if (direction.squaredLength() < 0.01) continue;
            DebugUtils.addArrow(world, from, direction, color, time, true);
        }
    }
}
