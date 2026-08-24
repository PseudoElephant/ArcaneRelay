package com.arcanerelay.features.triggervolume;

import com.arcanerelay.ArcaneRelayPlugin;
import com.arcanerelay.features.signal.util.ArcaneUtil;
import com.arcanerelay.features.signaltrigger.components.ArcaneTriggerBlock;
import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerContext;
import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerEffect;
import com.hypixel.hytale.builtin.triggervolumes.manager.VolumeEntry;
import com.hypixel.hytale.builtin.triggervolumes.shape.TriggerVolumeShape;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.block.BlockUtil;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.util.MathUtil;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import javax.annotation.Nonnull;

import org.joml.Vector3d;
import org.joml.Vector3i;

public class ArcaneRelayEffect extends TriggerEffect {

    @Nonnull
    public static final BuilderCodec<ArcaneRelayEffect> CODEC =BuilderCodec.builder(ArcaneRelayEffect.class, ArcaneRelayEffect::new, BASE_CODEC)
        .append(new KeyedCodec<>("TriggerTarget", new EnumCodec<>(TriggerTarget.class)),
                (e, v) -> e.triggerTarget = v, 
                (e) -> e.triggerTarget)
        .add()
        .build();

    @Nonnull
    private TriggerTarget triggerTarget;

    public enum TriggerTarget {
        ALL_ARCANE_BLOCKS_IN_VOLUME,
        ALL_RELAYS_IN_VOLUME;
    }

    public ArcaneRelayEffect() {
        this.triggerTarget = TriggerTarget.ALL_ARCANE_BLOCKS_IN_VOLUME;
    }

    @Override
    public void execute(@Nonnull TriggerContext context) {
        Store<EntityStore> store = context.getStore();
        World world = ((EntityStore) store.getExternalData()).getWorld();
        if (world != null) {
            Vector3d min = new Vector3d(); Vector3d max = new Vector3d();
            LongOpenHashSet processedBlocks = new LongOpenHashSet();

            for(VolumeEntry volume : context.getSpatialVolumes()) {
                TriggerVolumeShape shape = volume.getShape();
                Vector3d origin = volume.getPosition();
                shape.getWorldAABB(origin, min, max);
                int minX = MathUtil.floor(min.x()); int minY = MathUtil.floor(min.y()); int minZ = MathUtil.floor(min.z());
                int maxX = MathUtil.floor(max.x()); int maxY = MathUtil.floor(max.y()); int maxZ = MathUtil.floor(max.z());

                for(int x = minX; x <= maxX; ++x) {
                    for(int y = minY; y <= maxY; ++y) {
                        for(int z = minZ; z <= maxZ; ++z) {
                            WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
                            if (chunk == null) {
                                continue;
                            }

                            BlockType blockType = chunk.getBlockType(x, y, z);
                            if (blockType == null) {
                                continue;
                            }

                            Vector3i anchor = anchorForCell(world, x, y, z);
                            if (!processedBlocks.add(BlockUtil.pack(anchor.x, anchor.y, anchor.z))) {
                                continue;
                            }

                            WorldChunk chunkAtAnchor = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(anchor.x, anchor.z));
                            if (chunkAtAnchor == null) {
                                continue;
                            }

                            BlockType typeAtAnchor = chunkAtAnchor.getBlockType(anchor.x, anchor.y, anchor.z);
                            if (typeAtAnchor == null || !typeAtAnchor.getId().contains("Pseudo")) {
                               continue;
                            }

                            this.sendTrigger(context,world, chunkAtAnchor, typeAtAnchor, anchor.x, anchor.y, anchor.z);
                        }
                    }
                }
            }
        }
    } 

    @Nonnull
    private static Vector3i anchorForCell(@Nonnull World world, int x, int y, int z) {
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return new Vector3i(x, y, z);
        }

        BlockSection section = com.arcanerelay.util.BlockUtil.getBlockSection(world.getChunkStore().getStore(), x, y, z);
        int filler = section != null ? com.arcanerelay.util.BlockUtil.getFiller(section, x, y, z) : 0;
        return filler == 0 ? new Vector3i(x, y, z) : new Vector3i(x - FillerBlockUtil.unpackX(filler), y - FillerBlockUtil.unpackY(filler), z - FillerBlockUtil.unpackZ(filler));
    }
   
    private void sendTrigger(@Nonnull TriggerContext context, @Nonnull World world, @Nonnull WorldChunk chunk, @Nonnull BlockType blockType, int x, int y, int z) {
        Ref<ChunkStore> chunkRef = chunk.getReference();
        Store<ChunkStore> chunkStore = chunkRef.getStore();

        switch(this.triggerTarget) {
            case ALL_ARCANE_BLOCKS_IN_VOLUME:
                ArcaneRelayPlugin.LOGGER.atInfo().log("VolumeTrigger: Trigger All on block: %s at: %d, %d, %d ", blockType.getId(), x, y, z);
                ArcaneUtil.setTicking(chunkStore, x, y, z);
                break;
            
            case ALL_RELAYS_IN_VOLUME:
                Ref<ChunkStore> blockRef = com.arcanerelay.util.BlockUtil.getBlockEntityReference(chunkStore, x, y, z);
                if (blockRef == null || !blockRef.isValid()) break;

                ArcaneTriggerBlock trigger = chunkStore.getComponent(blockRef, ArcaneTriggerBlock.getComponentType());
                if (trigger != null) {
                    ArcaneUtil.setTicking(chunkStore, x, y, z);
                    ArcaneRelayPlugin.LOGGER.atInfo().log("VolumeTrigger: Trigger Relays on block: %s at: %d, %d, %d ", blockType.getId(), x, y, z);
                }
                break;
        }
    }
}


