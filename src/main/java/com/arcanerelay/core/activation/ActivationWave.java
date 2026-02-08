package com.arcanerelay.core.activation;

import com.arcanerelay.ArcaneRelayPlugin;
import com.arcanerelay.components.ArcaneTriggerBlock;
import com.arcanerelay.config.Activation;
import com.arcanerelay.state.ArcaneState;
import com.arcanerelay.state.TriggerEntry;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;

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
        List<TriggerEntry> entries = state.copyTriggerEntries();
        state.clearTriggers();
        Map<PosKey, TargetInfo> targets = new HashMap<>();
        for (TriggerEntry e : entries) {
            PosKey key = new PosKey(e.target().getX(), e.target().getY(), e.target().getZ());
            TargetInfo info = targets.computeIfAbsent(key, k -> new TargetInfo());
            info.sources.add(e.source());
            if (e.skip())
                info.skip = true;
            if (e.activatorId() != null && info.activatorId == null)
                info.activatorId = e.activatorId();
        }

        for (Map.Entry<PosKey, TargetInfo> entry : targets.entrySet()) {
            int x = entry.getKey().x;
            int y = entry.getKey().y;
            int z = entry.getKey().z;
            TargetInfo info = entry.getValue();

            long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
            WorldChunk chunk = world.getChunk(chunkIndex);

            int blockId = chunk.getBlock(x, y, z);
            BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
            if (blockType == null) {
                ArcaneRelayPlugin.get().getLogger().atWarning()
                    .log("ActivationWave: block type not found at " + x + ", " + y + ", " + z);
                continue;
            }

            if (info.skip) {
                ArcaneRelayPlugin.get().getLogger().atFine()
                    .log(String.format("ActivationWave: propagateOnly at (%d,%d,%d)", x, y, z));
                Ref<ChunkStore> blockRef = chunk.getBlockComponentEntity(x, y, z);
                if (blockRef != null) {
                    propagateOnly(world, store, chunk, blockRef, new Vector3i(x, y, z));
                }
            } else {
                List<int[]> sourcesAsInts = new ArrayList<>(info.sources.size());
                for (Vector3i s : info.sources) {
                    sourcesAsInts.add(new int[] { s.getX(), s.getY(), s.getZ() });
                }
                ArcaneRelayPlugin.get().getLogger().atInfo()
                    .log(String.format("ActivationWave: activate block=(%d,%d,%d) blockType=%s activatorId=%s", x, y, z,
                        blockType.getId(), info.activatorId));
                activateOutput(world, store, chunk, x, y, z, blockId, blockType, sourcesAsInts, info.activatorId);
            }
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
            ArcaneRelayPlugin.get().getLogger().atFine().log(String.format(
                "ActivationWave: scheduling activation %s at (%d,%d,%d)", activation.getId(), blockX, blockY, blockZ));
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

    private static final class PosKey {
        final int x, y, z;

        PosKey(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public int hashCode() {
            return 31 * (31 * x + y) + z;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (!(obj instanceof PosKey other))
                return false;
            return x == other.x && y == other.y && z == other.z;
        }
    }
}
