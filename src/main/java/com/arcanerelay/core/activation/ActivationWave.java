package com.arcanerelay.core.activation;

import com.arcanerelay.ArcaneRelayPlugin;
import com.arcanerelay.components.ArcaneTriggerBlock;
import com.arcanerelay.config.Activation;
import com.arcanerelay.state.ArcaneState;
import com.arcanerelay.state.TriggerEntry;
import com.hypixel.hytale.assetstore.map.BlockTypeAssetMap;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class ActivationWave {

    public static void runWave(
        @Nonnull World world,
        @Nonnull Store<ChunkStore> store,
        @Nonnull ArcaneState state
    ) {
        Map<Vector3i, TargetInfo> targets = new HashMap<>();
        List<TriggerEntry> entries = state.copyTriggerEntries();
        state.clearTriggers();

        for (TriggerEntry entry : entries) {
            TargetInfo info = targets.computeIfAbsent(entry.target(), k -> new TargetInfo());
            info.sources.add(entry.source());

            if (entry.skip())
                info.skip = true;

            if (entry.activatorId() != null && info.activatorId == null)
                info.activatorId = entry.activatorId();
        }

        BlockTypeAssetMap<String, BlockType> blockTypeMap = BlockType.getAssetMap();
        for (Map.Entry<Vector3i, TargetInfo> entry : targets.entrySet()) {
            int x = entry.getKey().x;
            int y = entry.getKey().y;
            int z = entry.getKey().z;
            TargetInfo info = entry.getValue();

           
            long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
            WorldChunk chunk = world.getChunk(chunkIndex);
            Ref<ChunkStore> chunkRef = chunk.getReference();
            BlockComponentChunk blockComponentChunk = store.getComponent(chunkRef, BlockComponentChunk.getComponentType());
            if (blockComponentChunk == null) continue;

            int blockIndex = ChunkUtil.indexBlockInColumn(x, y, z);
            Ref<ChunkStore> blockRef = blockComponentChunk.getEntityReference(blockIndex);

            int blockId = chunk.getBlock(x, y, z);
            BlockType blockType = blockTypeMap.getAsset(blockId);
            if (blockType == null) {
                ArcaneRelayPlugin.get().getLogger().atWarning().log("ActivationWave: block type not found at " + x + ", " + y + ", " + z);
                continue;
            }

            if (info.skip) {
                if (blockRef != null) {
                    propagateOnly(world, store, chunk, blockRef, new Vector3i(x, y, z));
                }
                continue;
            } 

            List<int[]> sourcesAsInts = new ArrayList<>(info.sources.size());
            for (Vector3i s : info.sources) {
                sourcesAsInts.add(new int[] { s.getX(), s.getY(), s.getZ() });
            }

            activateOutput(world, store, chunk, x, y, z, blockId, blockType, sourcesAsInts, info.activatorId);
        }
    }

    public static void activateOutput(
        @Nonnull World world,
        @Nonnull Store<ChunkStore> store,
        @Nonnull WorldChunk chunk,
        int blockX,
        int blockY,
        int blockZ,
        int blockId,
        @Nonnull BlockType blockType,
        @Nonnull List<int[]> sources,
        @Nullable String activatorId
    ) {
        String blockTypeKey = blockType.getId();
        Activation activation = activatorId != null && !activatorId.isEmpty()
            ? ArcaneRelayPlugin.get().getActivationRegistry().getActivation(activatorId)
            : ArcaneRelayPlugin.get().getActivationRegistry().getActivationForBlock(blockTypeKey);
        
        if (activation != null) {
            ActivationExecutor.execute(world, store, chunk, blockX, blockY, blockZ, blockType, activation, sources);
            return;
        }

        ArcaneRelayPlugin.get().getLogger().atWarning().log("ActivationWave: no activation for block: "
            + blockX + ", " + blockY + ", " + blockZ + " key: " + blockTypeKey);
    }

    private static void propagateOnly(
        @Nonnull World world,
        @Nonnull Store<ChunkStore> store,
        @Nonnull WorldChunk chunk,
        @Nonnull Ref<ChunkStore> blockRef,
        @Nonnull Vector3i blockPos
    ) {
        ArcaneTriggerBlock trigger = store.getComponent(blockRef,
            ArcaneRelayPlugin.get().getArcaneTriggerBlockComponentType());
        if (trigger == null)
            return;

        ArcaneState state = world.getChunkStore().getStore().getResource(ArcaneState.getResourceType());
        if (state == null)
            return;

        for (Vector3i out : trigger.getOutputPositions()) {
                state.addTrigger(TriggerEntry.of(out, blockPos));
        }
    }

    private static final class TargetInfo {
        final List<Vector3i> sources = new ArrayList<>();
        boolean skip;
        @Nullable
        String activatorId;
    }
}
