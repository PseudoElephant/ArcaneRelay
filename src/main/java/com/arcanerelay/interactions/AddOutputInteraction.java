package com.arcanerelay.interactions;

import com.arcanerelay.components.ArcaneConfiguratorComponent;
import com.arcanerelay.components.ArcaneTriggerBlock;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import javax.annotation.Nonnull;

/**
 * Secondary interaction: add target block as output connection to the currently configured trigger.
 * Uses ArcaneConfiguratorComponent to find the trigger block.
 */
public class AddOutputInteraction extends SimpleInstantInteraction {

    private static final double TARGET_DISTANCE = 20.0;
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
        ArcaneConfiguratorComponent configurator = cb.getComponent(ref, ArcaneConfiguratorComponent.getComponentType());
        if (player == null || configurator == null) return;

        Vector3i triggerPos = configurator.getConfiguredBlock();
        if (triggerPos == null) {
            NotificationUtil.sendNotification(player.getPlayerRef().getPacketHandler(), Message.raw("Select a trigger first (primary click on Arcane Trigger block)."), NotificationStyle.Warning);
            return;
        }

        Vector3i target = TargetUtil.getTargetBlock(ref, TARGET_DISTANCE, cb);
        if (target == null) {
            NotificationUtil.sendNotification(player.getPlayerRef().getPacketHandler(), Message.raw("No block in range."), NotificationStyle.Warning);
            return;
        }

        if (triggerPos.equals(target)) {
            NotificationUtil.sendNotification(player.getPlayerRef().getPacketHandler(), Message.raw("Target cannot be the same as the trigger."), NotificationStyle.Warning);
            return;
        }

        if (triggerPos.distanceTo(target) > TRIGGER_DISTANCE) {
            NotificationUtil.sendNotification(player.getPlayerRef().getPacketHandler(), Message.raw("Target is too far from the trigger."), NotificationStyle.Warning);
            return;
        }

        Vector3i outputPos = target.clone();
        World world = cb.getExternalData().getWorld();
        boolean[] wasRemoved = new boolean[1];
        int[] outputCountAfter = new int[1];
        world.execute(() -> {
            var chunkStore = world.getChunkStore();
            var store = chunkStore.getStore();
            Ref<ChunkStore> blockRef = BlockModule.getBlockEntity(world, triggerPos.x, triggerPos.y, triggerPos.z);
            if (blockRef == null || !blockRef.isValid()) return;

            ArcaneTriggerBlock comp = store.getComponent(blockRef, ArcaneTriggerBlock.getComponentType());
            if (comp == null) return;

            ArcaneTriggerBlock updated = (ArcaneTriggerBlock) comp.clone();
            boolean alreadyOutput = updated.removeOutputPosition(outputPos.x, outputPos.y, outputPos.z);
            if (alreadyOutput) {
                wasRemoved[0] = true;
            } else {
                updated.addOutputPosition(outputPos);
            }
            store.putComponent(blockRef, ArcaneTriggerBlock.getComponentType(), updated);
            outputCountAfter[0] = updated.getOutputPositions().size();
            SelectTriggerInteraction.addTriggerToOutputArrows(world, triggerPos);
        });

        String nameplateText = "Trigger: " + triggerPos.x + "," + triggerPos.y + "," + triggerPos.z + " | Outputs: " + outputCountAfter[0];
        updatePlayerNameplate(cb, ref, nameplateText);

        if (wasRemoved[0]) {
            NotificationUtil.sendNotification(player.getPlayerRef().getPacketHandler(), Message.raw("Output removed."), NotificationStyle.Success);
        } else {
            NotificationUtil.sendNotification(player.getPlayerRef().getPacketHandler(), Message.raw("Output has been added."), NotificationStyle.Success);
        }
    }

    private static void updatePlayerNameplate(CommandBuffer<EntityStore> cb, Ref<EntityStore> playerRef, String text) {
        Nameplate nameplate = cb.getComponent(playerRef, Nameplate.getComponentType());
        if (nameplate != null) {
            Nameplate updated = (Nameplate) nameplate.clone();
            updated.setText(text);
            cb.putComponent(playerRef, Nameplate.getComponentType(), updated);
        } else {
            cb.putComponent(playerRef, Nameplate.getComponentType(), new Nameplate(text));
        }
    }
}
