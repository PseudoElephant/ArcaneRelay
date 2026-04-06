package com.arcanerelay.config.types;

import com.arcanerelay.ArcaneRelayPlugin;
import com.arcanerelay.components.ArcanePullerBlock;
import com.arcanerelay.components.ArcaneSection;
import com.arcanerelay.config.Activation;
import com.arcanerelay.core.activation.ActivationExecutor;
import com.arcanerelay.core.activation.ArcaneCachedAccessor;
import com.arcanerelay.core.activation.ChunkStoreCommandBufferLike;
import com.arcanerelay.util.ArcaneUtil;
import com.arcanerelay.util.ArcaneConnectedBlocksUtil;
import com.arcanerelay.resources.ArcaneMoveState;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.protocol.ChangeVelocityType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.entity.knockback.KnockbackComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.splitvelocity.VelocityConfig;
import com.hypixel.hytale.server.core.util.TargetUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ArcanePullerActivation extends Activation {
    private int range = 15;
    private static final double KNOCKBACK_MAX_SPEED = 30;
    private static final float KNOCKBACK_DURATION = 0.1f;
    private static final float KNOCKBACK_MIN_DURATION = 0.05f;

    public static final BuilderCodec<ArcanePullerActivation> CODEC = BuilderCodec.builder(
        ArcanePullerActivation.class,
        ArcanePullerActivation::new,
        Activation.ABSTRACT_CODEC)
        .documentation("Pulls blocks and entities toward the puller. Extends a chain, collides with blocks/entities, then pulls back.")
        .appendInherited(
            new KeyedCodec<>("Range", Codec.INTEGER),
            (a, r) -> a.range = r,
            a -> a.range,
            (a, p) -> a.range = p.range)
        .documentation("Maximum extension range (default: 15).")
        .add()
        .build();

    public int getRange() {
        return range;
    }

    public void setRange(int range) {
        this.range = range;
    }

    @Override
    public ArcaneSection.BlockTickStrategy execute(
        @Nonnull ArcaneCachedAccessor accessor,
        @Nullable Ref<ChunkStore> sectionRef,
        @Nullable Ref<ChunkStore> blockRef,
        int worldX, int worldY, int worldZ,
        @Nonnull List<int[]> sources
    ) {
        ChunkStoreCommandBufferLike commandBuffer = accessor.getCommandBuffer();
        ArcaneRelayPlugin.LOGGER.atInfo().log("Executing ArcanePullerActivation at %d,%d,%d", worldX, worldY, worldZ);

        if (blockRef == null || !blockRef.isValid()) {
            return ArcaneSection.BlockTickStrategy.PROCESSED;
        }

        ArcanePullerBlock puller = commandBuffer.ensureAndGetComponent(blockRef, ArcaneRelayPlugin.get().getArcanePullerBlockComponentType());

        World world = commandBuffer.getExternalData().getWorld();
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(worldX, worldZ));
        if (chunk == null) return ArcaneSection.BlockTickStrategy.WAIT_FOR_ADJACENT_CHUNK_LOAD;

        BlockType pullerBlockType = chunk.getBlockType(worldX, worldY, worldZ);
        if (pullerBlockType == null) return ArcaneSection.BlockTickStrategy.PROCESSED;
        

        Vector3i pullerPos = new Vector3i(worldX, worldY, worldZ);
        Vector3i globalUp = getGlobalUp(chunk, pullerBlockType, pullerPos);
        if (globalUp.length() == 0) return ArcaneSection.BlockTickStrategy.PROCESSED;
        int maxRange = getRange();

        int[] source = sources.isEmpty() ? null : sources.get(0);

        syncExtensionChain(commandBuffer, world, puller, pullerPos, globalUp, maxRange);

        if (puller.getExtensionLength() == 0) {
            puller.toEXTENDING();
        }

        if (puller.getPhase() == ArcanePullerBlock.Phase.EXTENDING) {
            handleExtending(commandBuffer, world, puller, pullerPos, chunk, globalUp, pullerBlockType, maxRange);
            return ArcaneSection.BlockTickStrategy.PROCESSED;
        }

        if (puller.getPhase() == ArcanePullerBlock.Phase.PULLING_BACK) {
            handlePullingBack(commandBuffer, world, puller, pullerPos, globalUp);
            return ArcaneSection.BlockTickStrategy.PROCESSED;
        }

        return ArcaneSection.BlockTickStrategy.PROCESSED;
    }

    private ArcaneSection.BlockTickStrategy handleExtending(
        ChunkStoreCommandBufferLike commandBuffer,
        World world,
        ArcanePullerBlock puller,
        Vector3i pullerPos,
        WorldChunk pullerChunk,
        Vector3i globalForward,
        BlockType pullerBlockType,
        int maxRange
    ) {
        int extLen = puller.getExtensionLength();
        int maxExtend = Math.max(0, maxRange - 1);

        int tipX = pullerPos.x + globalForward.x * (extLen + 1);
        int tipY = pullerPos.y + globalForward.y * (extLen + 1);
        int tipZ = pullerPos.z + globalForward.z * (extLen + 1);

        WorldChunk tipChunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(tipX, tipZ));
        if (tipChunk == null) return ArcaneSection.BlockTickStrategy.CONTINUE;

        int tipBlockId = tipChunk.getBlock(tipX, tipY, tipZ);
        BlockType tipBlockType = BlockType.getAssetMap().getAsset(tipBlockId);

        ArcaneRelayPlugin.LOGGER.atInfo().log(
            "Puller EXTENDING at %d,%d,%d: extLen=%d tip=%d,%d,%d blockId=%d",
            pullerPos.x, pullerPos.y, pullerPos.z, extLen, tipX, tipY, tipZ, tipBlockId);

        if (isPullable(tipBlockType, tipBlockId)) {
             if (extLen == 0) {
                puller.setIDLE();
                commandBuffer.run((Store<ChunkStore> s) -> {
                    int rotationIndex = pullerChunk.getRotationIndex(pullerPos.x, pullerPos.y, pullerPos.z);
                    ArcaneConnectedBlocksUtil.updateCurrentAndPrevious(s, world, pullerPos, globalForward, RotationTuple.get(rotationIndex));
                });
                return ArcaneSection.BlockTickStrategy.PROCESSED;
             }
            commandBuffer.run((Store<ChunkStore> s) -> {
                ActivationExecutor.playEffects(world, pullerPos.x, pullerPos.y, pullerPos.z,
                    this.getEffects());
            });
            puller.setPULLING_BACK();
            handlePullingBack(commandBuffer, world, puller, pullerPos, globalForward);

            ArcaneRelayPlugin.LOGGER.atInfo().log(
                "Puller hit block %d at %d,%d,%d; starting pull-back",
                tipBlockId, tipX, tipY, tipZ);
            return ArcaneSection.BlockTickStrategy.CONTINUE;
        }

        Store<EntityStore> entityStore = world.getEntityStore().getStore();
        if (entityStore != null) {
            Set<Ref<EntityStore>> entities = new HashSet<>();
            collectEntitiesInBlock(entityStore, new Vector3i(tipX, tipY, tipZ), entities);

            if (!entities.isEmpty()) {
                ArcaneRelayPlugin.LOGGER.atInfo().log(
                    "Puller hit %d entities at %d,%d,%d; starting pull-back",
                    entities.size(), tipX, tipY, tipZ);
                puller.setPULLING_BACK();
                handlePullingBack(commandBuffer, world, puller, pullerPos, globalForward);
            }
        }

        if (isEmpty(tipBlockType, tipBlockId)) {
            if (extLen >= maxExtend) {
                puller.setPULLING_BACK();
                handlePullingBack(commandBuffer, world, puller, pullerPos, globalForward);
                return ArcaneSection.BlockTickStrategy.CONTINUE;
            }
            commandBuffer.run((Store<ChunkStore> s) -> {
                boolean placed = puller.extend(world, pullerPos.x, pullerPos.y, pullerPos.z,
                    pullerBlockType, pullerChunk.getRotationIndex(pullerPos.x, pullerPos.y, pullerPos.z));
                if (!placed) return;

                ActivationExecutor.playEffects(world, pullerPos.x, pullerPos.y, pullerPos.z,
                    this.getEffects());
                ArcaneRelayPlugin.LOGGER.atInfo().log(
                    "Puller extended to %d,%d,%d (len=%d)",
                    tipX, tipY, tipZ, puller.getExtensionLength());

                int rotationIndex = tipChunk.getRotationIndex(tipX, tipY, tipZ);
                Holder<ChunkStore> pullerHolder = pullerChunk.getBlockComponentHolder(
                    pullerPos.x, pullerPos.y, pullerPos.z);
                
                puller.setPhase(ArcanePullerBlock.Phase.EXTENDING);
                ArcaneConnectedBlocksUtil.updateCurrentAndPrevious(
                    s,
                    world,
                    new Vector3i(tipX, tipY, tipZ),
                    globalForward,
                    RotationTuple.get(rotationIndex)
                );
    
                pullerHolder.putComponent(ArcaneRelayPlugin.get().getArcanePullerBlockComponentType(), puller);
                if (pullerHolder != null) {
                    BlockType blockType = pullerChunk.getBlockType(pullerPos.x, pullerPos.y, pullerPos.z);
                    pullerChunk.setState(pullerPos.x, pullerPos.y, pullerPos.z, blockType, rotationIndex, pullerHolder);
                }
            });
         
            return ArcaneSection.BlockTickStrategy.CONTINUE;
        }

        if (extLen == 0) { // No extension blocks, so we're done
            puller.setIDLE();
            commandBuffer.run((Store<ChunkStore> s) -> {
                int rotationIndex = pullerChunk.getRotationIndex(pullerPos.x, pullerPos.y, pullerPos.z);
                ArcaneConnectedBlocksUtil.updateCurrentAndPrevious(s, world, pullerPos, globalForward, RotationTuple.get(rotationIndex));
            });

            return ArcaneSection.BlockTickStrategy.PROCESSED;
        }

        puller.setPULLING_BACK();
        handlePullingBack(commandBuffer, world, puller, pullerPos, globalForward);

        return ArcaneSection.BlockTickStrategy.CONTINUE;
    }

    private ArcaneSection.BlockTickStrategy handlePullingBack(
        ChunkStoreCommandBufferLike commandBuffer,
        World world,
        ArcanePullerBlock puller,
        Vector3i pullerPos,
        Vector3i globalUp
    ) {
        int extLen = puller.getExtensionLength();
        ArcaneRelayPlugin.LOGGER.atInfo().log(
            "Puller PULLING_BACK at %d,%d,%d: extLen=%d",
            pullerPos.x, pullerPos.y, pullerPos.z, extLen);
        if (extLen <= 0) {
            puller.setIDLE();
            return ArcaneSection.BlockTickStrategy.PROCESSED;
        }

        Vector3i tipPos = new Vector3i(
            pullerPos.x + globalUp.x * (extLen + 1),
            pullerPos.y + globalUp.y * (extLen + 1),
            pullerPos.z + globalUp.z * (extLen + 1)
        );
        Vector3i lastPos = new Vector3i(
            pullerPos.x + globalUp.x * extLen,
            pullerPos.y + globalUp.y * extLen,
            pullerPos.z + globalUp.z * extLen
        );

        ArcaneRelayPlugin.LOGGER.atInfo().log(
            "Puller PULLING_BACK positions: tip=%d,%d,%d last=%d,%d,%d",
            tipPos.x, tipPos.y, tipPos.z,
            lastPos.x, lastPos.y, lastPos.z);

        WorldChunk tipChunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(tipPos.x, tipPos.z));
        if (tipChunk == null) return ArcaneSection.BlockTickStrategy.CONTINUE;

        int tipBlockId = tipChunk.getBlock(tipPos.x, tipPos.y, tipPos.z);
        BlockType tipBlockType = BlockType.getAssetMap().getAsset(tipBlockId);
        Holder<ChunkStore> holder = tipChunk.getBlockComponentHolder(tipPos.x, tipPos.y, tipPos.z);
        int rotation = tipChunk.getRotationIndex(tipPos.x, tipPos.y, tipPos.z);
        int filler = tipChunk.getFiller(tipPos.x, tipPos.y, tipPos.z);

        ArcaneRelayPlugin.LOGGER.atInfo().log(
            "Puller move-entry check: extLen=%d tip=%d,%d,%d blockId=%d pullable=%s",
            extLen, tipPos.x, tipPos.y, tipPos.z, tipBlockId, isPullable(tipBlockType, tipBlockId));

        commandBuffer.run((Store<ChunkStore> s) -> {
            WorldChunk lastChunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(lastPos.x, lastPos.z));
            if (lastChunk != null) {
                lastChunk.breakBlock(lastPos.x, lastPos.y, lastPos.z, lastChunk.getFiller(lastPos.x, lastPos.y, lastPos.z), 4);
            }
            
            int newLen = extLen - 1;
            updateExtensionConnectedBlocks(s, world, pullerPos, globalUp, newLen, puller.getExtensionBlockKey());
            
            if (isPullable(tipBlockType, tipBlockId)) {
                ArcaneMoveState moveState = s.getResource(ArcaneMoveState.getResourceType());
                if (moveState == null) return;

                moveState.addMoveEntry(tipPos, globalUp.clone().scale(-1), tipBlockType, tipBlockId,
                    rotation, filler, 0, holder);
            }
        });

        Store<EntityStore> entityStore = world.getEntityStore().getStore();
        if (entityStore != null) {
            Set<Ref<EntityStore>> entitiesInTip = new HashSet<>();
            collectEntitiesInBlock(entityStore, new Vector3i(tipPos.x, tipPos.y, tipPos.z), entitiesInTip);
           
            if (!entitiesInTip.isEmpty()) {
                Vector3i knockbackDir = globalUp.clone().scale(-1);
                for (Ref<EntityStore> ref : entitiesInTip) {
                    if (ref != null && ref.isValid()) {
                        float duration = computePullDuration(entityStore, ref, tipPos);
                        commandBuffer.run((Store<ChunkStore> s) -> {
                            applyKnockbackToward(entityStore, ref, knockbackDir, duration);
                        });
                    }
             
                ArcaneRelayPlugin.LOGGER.atInfo().log(
                    "Puller hit %d entities at %d,%d,%d; applied knockback",
                    entitiesInTip.size(), tipPos.x, tipPos.y, tipPos.z);
                }
            }
            
        }

        ActivationExecutor.playEffects(world, pullerPos.x, pullerPos.y, pullerPos.z,
            this.getEffects());
        puller.removeLastExtensionPosition();

        if (puller.getExtensionLength() == 0) {
            puller.setIDLE();
            return ArcaneSection.BlockTickStrategy.PROCESSED;
        }


        return ArcaneSection.BlockTickStrategy.PROCESSED;
    }

    public static Vector3i getGlobalUp(WorldChunk chunk, BlockType blockType, Vector3i pullerPos) {
        int rotationIndex = chunk.getRotationIndex(pullerPos.x, pullerPos.y, pullerPos.z);
        RotationTuple rotationTuple = RotationTuple.get(rotationIndex);
        Vector3d localUp = getLocalUp(blockType);
        Vector3d global = rotationTuple.rotatedVector(localUp);
        return new Vector3i((int) Math.round(global.getX()), (int) Math.round(global.getY()), (int) Math.round(global.getZ()));
    }

    private static Vector3d getLocalUp(BlockType blockType) {
        return switch (blockType.getVariantRotation()) {
            case UpDown -> new Vector3d(0, 1, 0);
            case Wall -> new Vector3d(0, 0, 1);
            default -> new Vector3d(0, 1, 0);
        };
    }

    private static boolean isEmpty(@Nullable BlockType blockType, int blockId) {
        if (blockId == 0) return true;
        if (blockType == null) return true;
        return blockType.getMaterial() == BlockMaterial.Empty;
    }

    private static boolean isPullable(@Nullable BlockType blockType, int blockId) {
        if (blockId == 0) return false;
        if (blockType == null) return false;

        if (isExtensionBlock(blockType)) return false;

        return blockType.getMaterial() == BlockMaterial.Solid;
    }

    public static boolean isExtensionBlock(@Nullable BlockType blockType) {
        if (blockType == null) return false;
        String id = blockType.getId();
        if (id == null) return false;
        String lower = id.toLowerCase();
        return lower.contains("puller") && lower.contains("extension");
    }

    private void syncExtensionChain(
        @Nonnull ChunkStoreCommandBufferLike commandBuffer,
        @Nonnull World world,
        @Nonnull ArcanePullerBlock puller,
        @Nonnull Vector3i pullerPos,
        @Nonnull Vector3i forward,
        int maxRange
    ) {
        int actualLen = Math.min(maxRange, computeActualExtensionLength(world, pullerPos, forward, puller, maxRange));
        
        int storedLen = puller.getExtensionLength();

        if (actualLen < storedLen) {
            puller.clearExtensionPositions();
            for (int i = 0; i < actualLen; i++) {
                puller.addExtensionPosition(i);
            }

            puller.setPULLING_BACK();
            return;
        }
        return;
    }

    private static int computeActualExtensionLength(
        @Nonnull World world,
        @Nonnull Vector3i pullerPos,
        @Nonnull Vector3i forward,
        @Nonnull ArcanePullerBlock puller,
        int maxRange
    ) {
        String extensionKey = puller.getExtensionBlockKey();
        if (extensionKey == null || extensionKey.isEmpty()) return 10;

        int length = 0;
        int limit = Math.max(0, maxRange - 1);
        for (int i = 1; i <= limit; i++) {
            int x = pullerPos.x + forward.x * i;
            int y = pullerPos.y + forward.y * i;
            int z = pullerPos.z + forward.z * i;
            WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
            if (chunk == null) break;

            int blockId = chunk.getBlock(x, y, z);
            BlockType blockType = BlockType.getAssetMap().getAsset(blockId);

            if (!matchesExtensionBlock(blockType, extensionKey)) break;
            length++;
        }

        return length;
    }

    private static boolean matchesExtensionBlock(@Nullable BlockType blockType, @Nonnull String extensionKey) {
        if (blockType == null || extensionKey.isEmpty()) return false;
        String id = blockType.getId();
        if (id != null) {
            if (id.equals(extensionKey)) return true;
            if (id.toLowerCase().contains(extensionKey.toLowerCase())) return true;
        }
        String originalBlockTypeId = ArcaneUtil.getOriginalBlockTypeId(blockType);
        return extensionKey.equals(originalBlockTypeId);
    }

    private static void updateExtensionConnectedBlocks(
        @Nonnull Store<ChunkStore> store,
        @Nonnull World world,
        @Nonnull Vector3i pullerPos,
        @Nonnull Vector3i forward,
        int newLen,
        @Nonnull String extensionKey
    ) {
        if (newLen < 0 || extensionKey.isEmpty()) return;

        int x = pullerPos.x + forward.x * newLen;
        int y = pullerPos.y + forward.y * newLen;
        int z = pullerPos.z + forward.z * newLen;
        WorldChunk pullerChunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(pullerPos.x, pullerPos.z));
        if (pullerChunk == null) return;

        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) return;
        int rotationIndex = chunk.getRotationIndex(x, y, z);

        Holder<ChunkStore> pullerHolder = pullerChunk.getBlockComponentHolder(
            pullerPos.x, pullerPos.y, pullerPos.z
        );

        ArcaneConnectedBlocksUtil.updateCurrentAndPrevious(
            store,
            world,
            new Vector3i(x, y, z),
            forward,
            RotationTuple.get(rotationIndex)
        );
        
        if (pullerHolder != null) {
            BlockType blockType = chunk.getBlockType(pullerPos.x, pullerPos.y, pullerPos.z);
            pullerChunk.setState(pullerPos.x, pullerPos.y, pullerPos.z, blockType, rotationIndex, pullerHolder);
        }
    }

    private static void applyKnockbackToward(Store<EntityStore> entityStore, Ref<EntityStore> ref, Vector3i direction, float duration) {
        Vector3d velocity = new Vector3d(direction.clone().normalize());
        velocity.scale(KNOCKBACK_MAX_SPEED);
        KnockbackComponent knockback = entityStore.ensureAndGetComponent(ref, KnockbackComponent.getComponentType());
        knockback.setVelocity(velocity);
        knockback.setVelocityType(ChangeVelocityType.Set);
        knockback.setVelocityConfig(new VelocityConfig());
        knockback.setDuration(duration);
    }

    private static float computePullDuration(Store<EntityStore> entityStore, Ref<EntityStore> ref, Vector3i targetPos) {
        TransformComponent transform = entityStore.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) return KNOCKBACK_DURATION;
        Vector3d current = transform.getPosition();
        Vector3d target = new Vector3d(targetPos.x + 0.5, targetPos.y + 0.5, targetPos.z + 0.5);
        double distance = current.distanceTo(target);
        float duration = (float) (distance / KNOCKBACK_MAX_SPEED);
        if (duration < KNOCKBACK_MIN_DURATION) duration = KNOCKBACK_MIN_DURATION;
        return duration;
    }

    private static void collectEntitiesInBlock(Store<EntityStore> entityStore, Vector3i blockPos, Set<Ref<EntityStore>> out) {
        Vector3d min = new Vector3d(blockPos.x - 0.5, blockPos.y - 0.5, blockPos.z - 0.5);
        Vector3d max = new Vector3d(blockPos.x + 0.5, blockPos.y + 0.5, blockPos.z + 0.5);
        for (Ref<EntityStore> ref : TargetUtil.getAllEntitiesInBox(min, max, entityStore)) {
            if (ref != null && ref.isValid()) out.add(ref);
        }
    }
}
