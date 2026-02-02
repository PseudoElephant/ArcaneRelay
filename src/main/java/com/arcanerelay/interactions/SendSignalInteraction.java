package com.arcanerelay.interactions;

import com.arcanerelay.components.ArcaneTriggerBlock;
import com.arcanerelay.systems.ArcaneTickSystem;
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
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

/**
 * Targets an arcane block and adds that block into the trigger queue (ArcaneState).
 * The next wave will process that block and propagate along its outputs.
 */
public class SendSignalInteraction extends SimpleInstantInteraction {

   private static final double TARGET_DISTANCE = 20.0;

   @Nonnull
   public static final BuilderCodec<SendSignalInteraction> CODEC = BuilderCodec.builder(
      SendSignalInteraction.class, SendSignalInteraction::new, SimpleInstantInteraction.CODEC)
      .documentation("ArcaneRelay: target an Arcane Trigger block and send it into the trigger queue.")
      .build();

   public SendSignalInteraction() {
   }

   public SendSignalInteraction(String id) {
      super(id);
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

      Vector3i target = TargetUtil.getTargetBlock(ref, TARGET_DISTANCE, cb);
      if (target == null) {
         NotificationUtil.sendNotification(playerRef.getPacketHandler(), Message.raw("No block in range."), NotificationStyle.Warning);
         context.getState().state = InteractionState.Failed;
         return;
      }

      World world = cb.getExternalData().getWorld();
      if (BlockModule.get().getComponent(ArcaneTriggerBlock.getComponentType(), world, target.getX(), target.getY(), target.getZ()) == null) {
         NotificationUtil.sendNotification(playerRef.getPacketHandler(), Message.raw("Target must be an Arcane Trigger block."), NotificationStyle.Warning);
         context.getState().state = InteractionState.Failed;
         return;
      }

      ArcaneTriggerBlock trigger = BlockModule.get().getComponent(ArcaneTriggerBlock.getComponentType(), world, target.getX(), target.getY(), target.getZ());
      if (trigger == null) {
         NotificationUtil.sendNotification(playerRef.getPacketHandler(), Message.raw("Target must be an Arcane Trigger block."), NotificationStyle.Warning);
         context.getState().state = InteractionState.Failed;
         return;
      }
      final int tx = target.getX(), ty = target.getY(), tz = target.getZ();
      
      ArcaneTickSystem.requestSignalNextTick(world, tx, ty, tz, tx, ty, tz);
   
      NotificationUtil.sendNotification(playerRef.getPacketHandler(), Message.raw("Signal sent to trigger."), NotificationStyle.Success);
      context.getState().state = InteractionState.Finished;
   }
}
