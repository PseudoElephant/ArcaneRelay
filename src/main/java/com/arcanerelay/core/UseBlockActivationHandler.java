package com.arcanerelay.core;

import com.arcanerelay.api.BlockActivationHandler;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Default activation handler when no specific handler is registered for a block.
 * Logs an error and sends a failure notification to a nearby player (if any).
 */
public final class UseBlockActivationHandler implements BlockActivationHandler {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final double NOTIFY_RADIUS = 32.0;

    @Override
    public void onActivate(
        @Nonnull World world,
        @Nonnull Store<ChunkStore> store,
        @Nonnull CommandBuffer<ChunkStore> commandBuffer,
        @Nonnull WorldChunk chunk,
        int blockX, int blockY, int blockZ,
        @Nonnull BlockType blockType
    ) {
        String blockTypeKey = blockType.getId();
        LOGGER.atWarning().log("ArcaneRelay: no activation handler for block at ({}, {}, {}) type: {}",
            blockX, blockY, blockZ, blockTypeKey);

        world.execute(() -> {
            Store<EntityStore> entityStore = world.getEntityStore().getStore();
            Vector3d blockCenter = new Vector3d(blockX + 0.5, blockY + 0.5, blockZ + 0.5);
            for (Ref<EntityStore> ref : TargetUtil.getAllEntitiesInSphere(blockCenter, NOTIFY_RADIUS, entityStore)) {
                if (ref == null || !ref.isValid()) continue;
                PlayerRef playerRef = entityStore.getComponent(ref, PlayerRef.getComponentType());
                if (playerRef != null) {
                    NotificationUtil.sendNotification(
                        playerRef.getPacketHandler(),
                        Message.raw("Block activation not supported for this block."),
                        NotificationStyle.Danger
                    );
                    break;
                }
            }
        });
    }
}
