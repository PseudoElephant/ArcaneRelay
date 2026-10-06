package com.pseudoelephant.arcanerelay.features.signaltrigger.interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.pseudoelephant.arcanerelay.ArcaneRelayPlugin;
import com.pseudoelephant.arcanerelay.features.signal.util.ArcaneUtil;
import com.pseudoelephant.arcanerelay.features.signaltrigger.components.ArcaneTriggerBlock;
import com.pseudoelephant.arcanerelay.util.BlockUtil;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

/**
 * Targets an arcane block and adds that block into the trigger queue (ArcaneState).
 * The next wave will process that block and propagate along its outputs.
 */
public class SendSignalInteraction extends SimpleInstantInteraction {
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

      BlockPosition target = context.getMetaStore().getIfPresentMetaObject(TARGET_BLOCK_RAW);
      if (target == null) {
         NotificationUtil.sendNotification(playerRef.getPacketHandler(), Message.translation("server.arcanerelay.notifications.noBlockInRange"), NotificationStyle.Warning);
         context.getState().state = InteractionState.Failed;
         return;
      }

      cb.run((@Nonnull Store<EntityStore> store) -> {
         World world = store.getExternalData().getWorld();

         Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
         Ref<ChunkStore> blockRef = BlockUtil.getBlockEntityReference(chunkStore, target.x, target.y, target.z);
         if (blockRef == null) return;

         ArcaneTriggerBlock trigger = chunkStore.getComponent(blockRef, ArcaneTriggerBlock.getComponentType());
         if (trigger == null) return;

         ArcaneUtil.setTicking(chunkStore, target.x, target.y, target.z);
      
         NotificationUtil.sendNotification(playerRef.getPacketHandler(), Message.translation("server.arcanerelay.notifications.signalSentToTrigger"), NotificationStyle.Success);
      });

      context.getState().state = InteractionState.Finished;
   }
}
