package com.arcanerelay.features.blocks.breaker.activation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.arcanerelay.ArcaneRelayPlugin;
import com.arcanerelay.core.activation.ArcaneCachedAccessor;
import com.arcanerelay.features.activation.Activation;
import com.arcanerelay.features.blockmovement.util.BlockVectorUtil;
import com.arcanerelay.features.signal.components.ArcaneSection;
import com.arcanerelay.features.signal.components.ArcaneSection.BlockTickStrategy;
import com.arcanerelay.features.signal.util.ArcaneUtil;
import com.arcanerelay.util.BlockFlags;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3iUtil;
import org.joml.Vector3d;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemTool;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemTool.DurabilityLossBlockTypes;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemToolSpec;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage.EnvironmentSource;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;

public class HitActivation extends Activation {
    public float damage;
    public Vector3i direction;
    public ItemTool itemTool;

    public static final BuilderCodec<HitActivation> CODEC = BuilderCodec.builder(
            HitActivation.class,
            HitActivation::new,
            Activation.ABSTRACT_CODEC)
            .documentation(
                    "Hits a target on the specified direction (relative to block orientation) with the given damage.")
            .appendInherited(
                    new KeyedCodec<>("Damage", Codec.FLOAT),
                    (a, r) -> a.damage = r,
                    a -> a.damage,
                    (a, p) -> a.damage = p.damage)
            .documentation("Amount of damage to deal to the block (default: 0).")
            .add()
            .appendInherited(
                    new KeyedCodec<>("RelativeDirection", Vector3iUtil.CODEC),
                    (a, r) -> a.direction = r,
                    a -> a.direction,
                    (a, p) -> a.direction = p.direction)
            .documentation("Relative direction to hit the target in (default: [0, 1, 0]).")
            .add()
            .appendInherited(
                    new KeyedCodec<>("ItemTool", ItemTool.CODEC),
                    (a, r) -> a.itemTool = r,
                    a -> a.itemTool,
                    (a, p) -> a.itemTool = p.itemTool)
            .add()
            .build();

    @Override
    public BlockTickStrategy execute(
            @Nonnull ArcaneCachedAccessor accessor,
            @Nullable Ref<ChunkStore> sectionRef,
            @Nullable Ref<ChunkStore> blockRef,
            int worldX, int worldY, int worldZ,
            @Nonnull List<int[]> sources) {

        hitBlock(accessor, sectionRef, blockRef, worldX, worldY, worldZ, sources);
        hitEntity(accessor, sectionRef, blockRef, worldX, worldY, worldZ, sources);

        return ArcaneSection.BlockTickStrategy.PROCESSED;
    }

    private void hitEntity(@Nonnull ArcaneCachedAccessor accessor,
            @Nullable Ref<ChunkStore> sectionRef,
            @Nullable Ref<ChunkStore> blockRef,
            int worldX, int worldY, int worldZ,
            @Nonnull List<int[]> sources) {
        Vector3i currentPosition = new Vector3i(worldX, worldY, worldZ);

        World world = accessor.getCommandBuffer().getExternalData().getWorld();
        ChunkStore chunkStore = world.getChunkStore();
        long chunkIndex = ChunkUtil.indexChunkFromBlock(worldX, worldZ);
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIndex);
        WorldChunk worldChunkComponent = (WorldChunk) chunkStore.getStore().getComponent(chunkRef,
                WorldChunk.getComponentType());
        if (worldChunkComponent == null) {
            return;
        }

        BlockType currentBlockType = worldChunkComponent.getBlockType(worldX, worldY,
                worldZ);
        if (currentBlockType == null) {
            return;
        }

        Vector3i globalUp = BlockVectorUtil.getUpVector(chunkStore.getStore(), currentPosition);
        Vector3d targetDestination = new Vector3d(currentPosition).add(0.5, 0.5, 0.5);
        Store<EntityStore> store = world.getEntityStore().getStore();

        List<Ref<EntityStore>> targets = getTargetPlayersEntities(store, targetDestination, globalUp);
        targets.addAll(getTargetNonPlayerEntities(store, targetDestination, globalUp));

        // Remove duplicates across both lists
        Set<Ref<EntityStore>> seen = new HashSet<>();
        targets.removeIf(target -> !seen.add(target));

        if (targets.isEmpty()) {
            return;
        }

        float finalDamage = getEntityDamage(currentBlockType);
        for (Ref<EntityStore> target : targets) {
            if (target == null || !target.isValid()) {
                continue;
            }

            world.execute(() -> {
                Damage damageEvent = new Damage(
                        new EnvironmentSource("hit_activation"),
                        DamageCause.PHYSICAL,
                        finalDamage
                    );

                DamageSystems.executeDamage(target, store, damageEvent);
            });
        }
    }

    private float getEntityDamage(BlockType blockType) {
        if (blockType != null && ArcaneUtil.getOriginalBlockTypeId(blockType).contains("Pseudo_Arcane_Breaker")) {
            return ArcaneRelayPlugin.get().getConfig().getBreakerEntityDamage();
        }

        return damage;
    }

    // Hitbox for the player is odd, get entities in sphere will only return the player if the players feet are in it, so we are hacking the hitboxes a bit here to make it feel like it makes sense.
    private List<Ref<EntityStore>> getTargetPlayersEntities(Store<EntityStore> store, Vector3d targetDestination, Vector3i globalUp) {
        List<Ref<EntityStore>> targets;
        if (globalUp.equals(new Vector3i(0, 1, 0))) {
            Vector3d destination = new Vector3d(targetDestination).add(0, 0.7, 0);

            targets = TargetUtil.getAllEntitiesInSphere(destination, 1f, store);
        } else if (globalUp.equals(new Vector3i(0, -1, 0))) {
            Vector3d destination = new Vector3d(targetDestination).add(0, -0.5, 0);

            targets = TargetUtil.getAllEntitiesInSphere(destination, 0.85f, store);
            targets.addAll(TargetUtil.getAllEntitiesInSphere(destination.add(0, -1.7, 0), 0.85f, store));
        } else {
            Vector3d destination = new Vector3d(targetDestination).add(new Vector3d(globalUp).mul(0.5));

            targets = TargetUtil.getAllEntitiesInSphere(destination, 1f, store);
            targets.addAll(TargetUtil.getAllEntitiesInSphere(destination.add(0, -1, 0), 1f, store));
        } 

        targets.removeIf(target -> store.getComponent(target, Player.getComponentType()) == null);
        return new java.util.ArrayList<>(targets);
    }

    private List<Ref<EntityStore>> getTargetNonPlayerEntities(Store<EntityStore> store, Vector3d targetDestination, Vector3i globalUp) {
        Vector3d destination = new Vector3d(targetDestination).add(new Vector3d(globalUp).mul(0.5));
        List<Ref<EntityStore>> targets = TargetUtil.getAllEntitiesInSphere(destination, 1f, store);
        
        targets.removeIf(target -> store.getComponent(target, Player.getComponentType()) != null);
        return new java.util.ArrayList<>(targets);
    }


    private void hitBlock(@Nonnull ArcaneCachedAccessor accessor,
            @Nullable Ref<ChunkStore> sectionRef,
            @Nullable Ref<ChunkStore> blockRef,
            int worldX, int worldY, int worldZ,
            @Nonnull List<int[]> sources) {
        Vector3i currentPosition = new Vector3i(worldX, worldY, worldZ);
        World world = accessor.getCommandBuffer().getExternalData().getWorld();
        ChunkStore chunkStore = world.getChunkStore();
        long chunkIndex = ChunkUtil.indexChunkFromBlock(worldX, worldZ);
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIndex);
        WorldChunk worldChunkComponent = (WorldChunk) chunkStore.getStore().getComponent(chunkRef,
                WorldChunk.getComponentType());
        if (worldChunkComponent == null) {
            return;
        }

        BlockType currentBlockType = worldChunkComponent.getBlockType(worldX, worldY,
                worldZ);
        if (currentBlockType == null) {
            return;
        }

        Vector3i globalUp = BlockVectorUtil.getUpVector(chunkStore.getStore(), currentPosition);
        Vector3i targetPosition = new Vector3i(currentPosition).add(globalUp);

        EntityStore entityStore = world.getEntityStore();

        BlockFlags damageFlags = new BlockFlags(BlockFlags.BREAK_BLOCK_VFX)
                .add(BlockFlags.BREAK_BLOCK_SFX);

        ItemTool tool = getItemTool(currentBlockType);

        Ref<ChunkStore> targetSectionRef = chunkStore.getChunkSectionReferenceAtBlock(
                targetPosition.x, targetPosition.y, targetPosition.z);
        if (targetSectionRef == null || !targetSectionRef.isValid()) {
            return;
        }

        world.execute(() -> {
            BlockHarvestUtils.performBlockDamage(
                    (Ref<EntityStore>) null, null, targetPosition, null, tool, (String) null, false,
                    .4f, damageFlags.getValue(), false, targetSectionRef,
                    entityStore.getStore(), chunkStore.getStore());
        });
    }

    private ItemTool getItemTool(BlockType blockType) {
        if (blockType != null && ArcaneUtil.getOriginalBlockTypeId(blockType).contains("Pseudo_Arcane_Breaker")) {
            return getScaledItemTool(itemTool, ArcaneRelayPlugin.get().getConfig().getBreakerBlockDamageScalar());
        }

        return itemTool;
    }

    private ItemTool getScaledItemTool(@Nonnull ItemTool tool, float scalar) {
        final ItemToolSpec[] originalSpecs = tool.getSpecs();
        final float speed = tool.getSpeed();
        final DurabilityLossBlockTypes[] durabilityLossBlockTypes = tool.getDurabilityLossBlockTypes();
        
        ItemToolSpec[] newSpecs = new ItemToolSpec[originalSpecs.length];
        for (int i = 0; i < originalSpecs.length; i++) {
            ItemToolSpec spec = originalSpecs[i];
            String gatherType = spec.getGatherType();
            float power = spec.getPower();
            int quality = spec.getQuality();

            newSpecs[i] = new ItemToolSpec(gatherType, power * scalar, quality);
        }

        return new ItemTool(newSpecs, speed, durabilityLossBlockTypes);
    }
}
