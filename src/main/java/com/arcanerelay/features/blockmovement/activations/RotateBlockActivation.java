package com.arcanerelay.features.blockmovement.activations;

import com.arcanerelay.ArcaneRelayPlugin;
import com.arcanerelay.core.activation.ArcaneCachedAccessor;
import com.arcanerelay.core.adapters.ChunkStoreCommandBufferLike;
import com.arcanerelay.util.BlockUtil;
import com.arcanerelay.features.activation.Activation;
import com.arcanerelay.features.blockmovement.util.BlockVectorUtil;
import com.arcanerelay.features.signal.components.ArcaneSection;
import com.arcanerelay.features.signal.util.ArcaneUtil;
import com.hypixel.hytale.assetstore.map.BlockTypeAssetMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;

import org.joml.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RotateBlockActivation extends Activation {
    private String[] RotTypeID = new String[0];
    public static final BuilderCodec<RotateBlockActivation> CODEC = BuilderCodec.builder(
                    RotateBlockActivation.class,
                    RotateBlockActivation::new,
                    Activation.ABSTRACT_CODEC)
            .documentation("Rotates the block On-top of rotator")
            .appendInherited(
                    new KeyedCodec<>("Activations", new ArrayCodec<>(Codec.STRING, String[]::new)),
                    (a, ids) -> a.RotTypeID = ids,
                    a -> a.RotTypeID,
                    (a, p) -> a.RotTypeID = p.RotTypeID
            )
            .documentation("Type of rotation either Clockwise or Counter-Clockwise")
            .add()
            .build();


    private boolean isClockWise(BlockType blockType) {
        if (blockType == null) return false;
        String id = blockType.getId();
        return id != null && id.toLowerCase().contains("rotatorl");
    }

    private void rotateEntities(World world, Vector3i rotatorPos, Vector3i targetPos, boolean isClockWise, Vector3i rotatorUp, boolean targetBlockRotated) {
        if (rotatorUp.x != 0 || rotatorUp.y != 1 || rotatorUp.z != 0) {
            return;
        }

        Store<EntityStore> entityStore = world.getEntityStore().getStore();
        if (entityStore == null) return;

        Set<Ref<EntityStore>> entitiesOnRotator = new HashSet<>();
        BlockUtil.collectEntitiesOnTopOfBlock(entityStore, rotatorPos, entitiesOnRotator);

        for (Ref<EntityStore> ref : entitiesOnRotator) {
            if (ref == null || !ref.isValid()) continue;

            TransformComponent transform = entityStore.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) continue;

            rotateEntity(world, entityStore, ref, transform, isClockWise);
        }

        if (targetBlockRotated) {
            Set<Ref<EntityStore>> entitiesOnTarget = new HashSet<>();
            BlockUtil.collectEntitiesOnTopOfBlock(entityStore, targetPos, entitiesOnTarget);

            for (Ref<EntityStore> ref : entitiesOnTarget) {
                if (ref == null || !ref.isValid()) continue;

                TransformComponent transform = entityStore.getComponent(ref, TransformComponent.getComponentType());
                if (transform == null) continue;

                rotateEntity(world, entityStore, ref, transform, isClockWise);
            }
        }
    }

    private void rotateEntity( @Nonnull World world,
            @Nonnull Store<EntityStore> entityStore,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull TransformComponent transform,
            boolean isClockWise) {
        PlayerRef playerRef = entityStore.getComponent(ref, PlayerRef.getComponentType());

        if (playerRef == null) {
            rotateEntityTransform(transform, isClockWise);
            return;
        }

        rotatePlayerWithTeleport(world, entityStore, ref, transform, isClockWise);
    }

    private void rotateEntityTransform(TransformComponent transform, boolean isClockWise) {
        Rotation3f rotation = transform.getRotation();
        float yawAdjustment = isClockWise ? (float) (-Math.PI / 2) : (float) (Math.PI / 2);
        rotation.addYaw(yawAdjustment);
    }

    private static void rotatePlayerWithTeleport(
            @Nonnull World world,
            @Nonnull Store<EntityStore> entityStore,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull TransformComponent transform,
            boolean isClockWise) {
        PlayerRef playerRef = entityStore.getComponent(ref, PlayerRef.getComponentType());

        if (playerRef == null) {
            return;
        }

        Rotation3f rotation = new Rotation3f(transform.getRotation());
        float yawAdjustment = isClockWise ? (float) (-Math.PI / 2) : (float) (Math.PI / 2);
        Rotation3f newRotation = new Rotation3f(
                rotation.x,
                rotation.y + yawAdjustment,
                rotation.z
        );

        HeadRotation headComp = entityStore.getComponent(ref, HeadRotation.getComponentType());
        Rotation3f headRot = headComp != null ? new Rotation3f(headComp.getRotation()) : new Rotation3f(transform.getRotation());
        Rotation3f newHeadRot = new Rotation3f(
                headRot.x,
                headRot.y + yawAdjustment,
                headRot.z
        );

        Teleport teleport = Teleport.createForPlayer(world, transform.getPosition(), newRotation).setHeadRotation(newHeadRot);
        entityStore.addComponent(ref, Teleport.getComponentType(), teleport);
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
            WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(worldX, worldZ));
            if (chunk == null) return;

            BlockTypeAssetMap<String, BlockType> assetMap = BlockType.getAssetMap();

            // Rotator info
            BlockType rotatorBlockType = chunk.getBlockType(worldX, worldY, worldZ);
            Vector3i rotatorPos = new Vector3i(worldX, worldY, worldZ);
            boolean isClockWise = isClockWise(rotatorBlockType);
            Vector3i rotatorUp = BlockVectorUtil.getUpVector(store, rotatorPos);

            // Target Info
            Vector3i tempUp = BlockVectorUtil.getUpVector(store, rotatorPos);
            Vector3i targetPos = new Vector3i (rotatorPos.x + tempUp.x, rotatorPos.y + tempUp.y, rotatorPos.z + tempUp.z);
            BlockType targetBlockType = chunk.getBlockType(targetPos.x, targetPos.y, targetPos.z);
            if (targetBlockType == null) return;
            
            String targetID = ArcaneUtil.getOriginalBlockTypeId(targetBlockType);
            BlockSection targetSection = BlockUtil.getBlockSection(store, targetPos.x, targetPos.y, targetPos.z);
            RotationTuple currenRotation = RotationTuple.get(targetSection != null ? BlockUtil.getRotationIndex(targetSection, targetPos.x, targetPos.y, targetPos.z) : 0);
            RotationTuple newRotation = BlockVectorUtil.rotateOverAxis90Degrees(currenRotation, rotatorUp, isClockWise);
            
            boolean blockWasRotated = BlockVectorUtil.isRotatable(targetBlockType);
            if (blockWasRotated) {
                chunk.setBlock(targetPos.x, targetPos.y, targetPos.z, assetMap.getIndex(targetID), targetBlockType, newRotation.index(), 0, 4);
                BlockVectorUtil.setTickingAround(chunk,targetPos,1);
            } else {
                ArcaneRelayPlugin.LOGGER.atInfo().log("Rotator: Block of type: '%s', is not allowed to be rotated", targetBlockType.getId());
            }

            rotateEntities(world, rotatorPos, targetPos, isClockWise, rotatorUp, blockWasRotated);
        });

        return ArcaneSection.BlockTickStrategy.PROCESSED;
    }
}

