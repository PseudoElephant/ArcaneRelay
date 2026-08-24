package com.arcanerelay.features.blockmovement.activations;

import com.arcanerelay.ArcaneRelayPlugin;
import com.arcanerelay.core.activation.ActivationExecutor;
import com.arcanerelay.core.activation.ArcaneCachedAccessor;
import com.arcanerelay.core.adapters.ChunkStoreCommandBufferLike;
import com.arcanerelay.util.BlockUtil;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.arcanerelay.features.activation.Activation;
import com.arcanerelay.features.blockmovement.resources.ArcaneMoveState;
import com.arcanerelay.features.blockmovement.util.BlockVectorUtil;
import com.arcanerelay.features.signal.components.ArcaneSection;
import com.arcanerelay.features.signal.util.ArcaneUtil;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;

import org.joml.Vector3d;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.VariantRotation;
import com.hypixel.hytale.protocol.ChangeVelocityType;
import com.hypixel.hytale.server.core.entity.knockback.KnockbackComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.splitvelocity.VelocityConfig;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MoveBlockActivation extends Activation {
    private int range = ArcaneRelayPlugin.get().getConfig().getPusherRange();
    private int upAmount = 1;
    private boolean isWall = false;

    private static final double KNOCKBACK_MAX_SPEED = 4.5f;
    private static final float KNOCKBACK_DURATION = 0.2f;

    public static final BuilderCodec<MoveBlockActivation> CODEC = BuilderCodec.builder(
        MoveBlockActivation.class,
        MoveBlockActivation::new,
        Activation.ABSTRACT_CODEC)
        .documentation("Pushes blocks in front in the facing direction. Range limits max chain length.")
        .appendInherited(
            new KeyedCodec<>("Range", Codec.INTEGER),
            (a, r) -> a.range = r,
            a -> a.range,
            (a, p) -> a.range = p.range)
        .documentation("Maximum number of blocks to push in a chain (default: 1).")
        .add()
        .appendInherited(
            new KeyedCodec<>("IsWall", Codec.BOOLEAN),
            (a, w) -> a.isWall = w,
            a -> a.isWall,
            (a, p) -> a.isWall = p.isWall)
        .documentation("Whether the block is a wall (default: false).")
        .add()
        .build();

    private boolean isWallPusherVariant(@Nonnull ComponentAccessor<ChunkStore> commandBuffer, @Nonnull Ref<ChunkStore> blockRef, @Nonnull Ref<ChunkStore> sectionRef, int worldX, int worldY, int worldZ) {
        World world = commandBuffer.getExternalData().getWorld();
        WorldChunk chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(worldX, worldZ));
        if (chunk == null) return false;

        BlockType blockType = chunk.getBlockType(worldX, worldY, worldZ);
        if (blockType == null) return false;

        if (isWall)
            return true;

        if (blockType.getVariantRotation() == VariantRotation.Wall)
            return true;
        
        String id = blockType.getId();
        return id != null && id.toLowerCase().contains("wall");
    }

    private Vector3i getGlobalForwardVector(@Nonnull ComponentAccessor<ChunkStore> commandBuffer, @Nonnull Ref<ChunkStore> blockRef, @Nonnull Ref<ChunkStore> sectionRef, int worldX, int worldY, int worldZ, Vector3i pusherPosition) {
        boolean isWallPusher = isWallPusherVariant(commandBuffer, blockRef, sectionRef, worldX, worldY, worldZ);
        
        WorldChunk pusherChunk = commandBuffer.getExternalData().getWorld().getChunk(ChunkUtil.indexChunkFromBlock(worldX, worldZ));
        if (pusherChunk == null) return new Vector3i(0, 0, 0);

        return BlockVectorUtil.getForwardVector(commandBuffer, pusherPosition, isWallPusher);
    }

    private Vector3i getGlobalUpVector(@Nonnull ComponentAccessor<ChunkStore> commandBuffer, @Nonnull Ref<ChunkStore> blockRef, @Nonnull Ref<ChunkStore> sectionRef, int worldX, int worldY, int worldZ, Vector3i pusherPosition) {
        boolean isWallPusher = isWallPusherVariant(commandBuffer, blockRef, sectionRef, worldX, worldY, worldZ);
                
        WorldChunk pusherChunk = commandBuffer.getExternalData().getWorld().getChunk(ChunkUtil.indexChunkFromBlock(worldX, worldZ));
        if (pusherChunk == null) return new Vector3i(0, 0, 0);

        return BlockVectorUtil.getUpVector(commandBuffer, pusherPosition, isWallPusher);
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
        commandBuffer.run((@Nonnull Store<ChunkStore> store) -> {
            World world = store.getExternalData().getWorld();
            Vector3i pusherPosition = new Vector3i(worldX, worldY, worldZ);

            Vector3i globalForward = getGlobalForwardVector(store, blockRef, sectionRef,
                worldX, worldY, worldZ, pusherPosition);
            if (globalForward.length() == 0) {
                return;
            }

            Vector3i scaledGlobalUpVector = getGlobalUpVector(accessor.getCommandBuffer(), blockRef, sectionRef,
                worldX, worldY, worldZ, pusherPosition).mul(this.upAmount);
            if (scaledGlobalUpVector.length() == 0)
                return;

            Vector3i frontPusherPosition = new Vector3i(pusherPosition);
           
            WorldChunk blockChunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(worldX, worldZ));
            int pusherBlockId = blockChunk.getBlock(worldX, worldY, worldZ);
            BlockType pusherBlockType = BlockType.getAssetMap().getAsset(pusherBlockId);

            int maxRange = getMaxRange(pusherBlockType);

            int[] chainBlockIds               = new int[maxRange];
            int[] chainRotations              = new int[maxRange];
            int[] chainFillers                = new int[maxRange];
            BlockType[] chainBlockTypes       = new BlockType[maxRange];
            Holder<ChunkStore>[] chainHolders = new Holder[maxRange];

            int chainLength = 0;
            for (int i = 0; i < maxRange; i++) {
                Vector3i c = new Vector3i(frontPusherPosition).add(new Vector3i(globalForward).mul(i).add(scaledGlobalUpVector));

                WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(c.x, c.z));
                if (chunk == null)
                    break;

                int blockId = chunk.getBlock(c.x, c.y, c.z);
                BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
                if (!BlockVectorUtil.isMoveable(blockType,blockId))
                    break;

                chainBlockIds[chainLength]     = blockId;
                BlockSection cSection = BlockUtil.getBlockSection(store, c.x, c.y, c.z);
                chainRotations[chainLength]    = cSection != null ? BlockUtil.getRotationIndex(cSection, c.x, c.y, c.z) : 0;
                chainFillers[chainLength]      = cSection != null ? BlockUtil.getFiller(cSection, c.x, c.y, c.z) : 0;
                chainBlockTypes[chainLength]   = blockType;

                Holder<ChunkStore> stateHolder = chunk.getBlockComponentHolder(c.x, c.y, c.z);
                chainHolders[chainLength]      = stateHolder != null ? stateHolder.clone() : null;

                chainLength++;
            }

            Vector3i nextEmptyPosition = new Vector3i(frontPusherPosition).add(new Vector3i(globalForward).mul(chainLength).add(scaledGlobalUpVector));

            WorldChunk emptyChunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(nextEmptyPosition.x, nextEmptyPosition.z));
            if (emptyChunk == null)
                return;

            int emptyBlockId = emptyChunk.getBlock(nextEmptyPosition.x, nextEmptyPosition.y, nextEmptyPosition.z);
            BlockType emptyBlockType = BlockType.getAssetMap().getAsset(emptyBlockId);
            if (!BlockUtil.isEmpty(emptyBlockType, emptyBlockId))
                return;

            movePlayers(world, globalForward, scaledGlobalUpVector, frontPusherPosition, nextEmptyPosition, chainLength);

            if (chainLength == 0)
                return;

            for (int j = chainLength - 1; j >= 0; j--) {
                Vector3i fromPosition = new Vector3i(frontPusherPosition).add(new Vector3i(globalForward).mul(j).add(scaledGlobalUpVector));
                Vector3i toPosition = new Vector3i(frontPusherPosition).add(new Vector3i(globalForward).mul(j + 1).add(scaledGlobalUpVector));

                WorldChunk fromChunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(fromPosition.x, fromPosition.z));
                WorldChunk toChunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(toPosition.x, toPosition.z));
                if (fromChunk == null || toChunk == null)
                    continue;

                ArcaneMoveState arcaneMoveState = store.getResource(ArcaneMoveState.getResourceType());

                arcaneMoveState.addMoveEntry(fromPosition,
                    new Vector3i(toPosition).sub(fromPosition), chainBlockTypes[j], chainBlockIds[j],
                    chainRotations[j], chainFillers[j], 0, chainHolders[j]);

                
                Vector3i destinationPosition = new Vector3i(frontPusherPosition).add(globalForward).add(scaledGlobalUpVector);
                ActivationExecutor.playEffects(world, destinationPosition.x, destinationPosition.y, destinationPosition.z,
                    getEffects());
            }
        });

        return ArcaneSection.BlockTickStrategy.PROCESSED;
    }

    private int getMaxRange(BlockType blockType) {
        if (blockType != null && ArcaneUtil.getOriginalBlockTypeId(blockType).contains("Pseudo_Arcane_Pusher")) {
            return ArcaneRelayPlugin.get().getConfig().getPusherRange();
        }

        return range;
    }

    private void movePlayers(World world, Vector3i globalForward, Vector3i scaledGlobalUpVector,
            Vector3i frontPusherPosition, Vector3i nextEmptyPosition, final int len) {
        Store<EntityStore> entityStore = world.getEntityStore().getStore();
        if (entityStore == null) return;

        Set<Ref<EntityStore>> entitiesOnTop = new HashSet<>();
        BlockUtil.collectEntitiesOnTopOfBlock(entityStore, nextEmptyPosition, entitiesOnTop);
        BlockUtil.collectEntitiesOnTopOfBlock(entityStore, new Vector3i(frontPusherPosition), entitiesOnTop);

        for (int i = 0; i < len; i++) {
            Vector3i fromPosition = new Vector3i(frontPusherPosition).add(new Vector3i(globalForward).mul(i).add(scaledGlobalUpVector));
            BlockUtil.collectEntitiesOnTopOfBlock(entityStore, fromPosition, entitiesOnTop);
        }

        final List<Ref<EntityStore>> entitiesOnTopList = new ArrayList<>(entitiesOnTop);

        for (Ref<EntityStore> ref : entitiesOnTopList) {
            if (ref == null || !ref.isValid())
                continue;

            TransformComponent transform = entityStore.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null)
                continue;

            moveEntityWithBlock(world, entityStore, ref, transform, globalForward);
        }
    }

    private static boolean isPushUp(Vector3i direction) {
        return direction.y > 0 && direction.x == 0 && direction.z == 0;
    }

    private static void applyKnockbackWithLimit(
            @Nonnull Store<EntityStore> entityStore,
            @Nonnull Ref<EntityStore> ref,
            Vector3i direction) {
        Vector3d velocity = new Vector3d(direction).normalize();
        velocity.mul(KNOCKBACK_MAX_SPEED);

        KnockbackComponent knockback = entityStore.ensureAndGetComponent(ref, KnockbackComponent.getComponentType());
        knockback.setVelocity(velocity);
        knockback.setVelocityType(ChangeVelocityType.Set);
        knockback.setVelocityConfig(new VelocityConfig());
        knockback.setDuration(KNOCKBACK_DURATION);
    }

    private static void moveEntityWithBlock(
            @Nonnull World world,
            @Nonnull Store<EntityStore> entityStore,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull TransformComponent transform,
            Vector3i direction) {
        PlayerRef playerRef = entityStore.getComponent(ref, PlayerRef.getComponentType());

        if (isPushUp(direction) && playerRef != null) {
            teleportPlayerWithBlock(world, entityStore, ref, transform, direction);
        } else {
            applyKnockbackWithLimit(entityStore, ref, direction);
        }
    }

    private static void teleportPlayerWithBlock(
            @Nonnull World world,
            @Nonnull Store<EntityStore> entityStore,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull TransformComponent transform,
            Vector3i direction) {
        Vector3d pos = new Vector3d(transform.getPosition());
        Vector3d newPos = pos.add(new Vector3d(direction));

        HeadRotation headComp = entityStore.getComponent(ref, HeadRotation.getComponentType());
        Rotation3f headRot = headComp != null ? new Rotation3f(headComp.getRotation()) : new Rotation3f(transform.getRotation());

        Teleport teleport = Teleport.createForPlayer(world, newPos, transform.getRotation()).setHeadRotation(headRot);
        entityStore.addComponent(ref, Teleport.getComponentType(), teleport);
    }
}
