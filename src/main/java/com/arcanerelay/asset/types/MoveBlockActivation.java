package com.arcanerelay.asset.types;

import com.arcanerelay.ArcaneRelayPlugin;
import com.arcanerelay.asset.Activation;
import com.arcanerelay.asset.ActivationContext;
import com.arcanerelay.asset.ActivationExecutor;
import com.arcanerelay.components.ArcaneMoveBlock;
import com.arcanerelay.state.ArcaneMoveState;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.VariantRotation;
import com.hypixel.hytale.protocol.ChangeVelocityType;
import com.hypixel.hytale.server.core.entity.knockback.KnockbackComponent;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.splitvelocity.VelocityConfig;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

/**
 * Activation that moves the block in the "up" direction (relative to this
 * block)
 * one step towards the "front" direction. The pusher block stays in place.
 * Range is how many steps in the front direction the block is moved (default:
 * 1).
 *
 * <p>
 * Player interaction: if the player is at the end of the chain (destination
 * cell), they get
 * pushed. If the player is on top of any block in the chain, they remain on top
 * / get pushed.
 * When the block moves <b>up</b> (elevator): teleport so head rotation is
 * preserved.
 * When the block moves in another direction: limited knockback (speed capped).
 */
public class MoveBlockActivation extends Activation {
    private int range = 1;
    private int upAmount = 1;
    private boolean isWall = false;

    // Used when moving entities
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

    public int getRange() {
        return range;
    }

    public void setRange(int range) {
        this.range = range;
    }

    private static Vector3d getForwardFromBlockType(@Nonnull BlockType blockType, boolean isWall) {
        return switch (blockType.getVariantRotation()) {
            case UpDown -> new Vector3d(0, 1, 0);
            default -> isWall ? new Vector3d(0, -1, 0) : new Vector3d(0, 0, -1);
        };
    }

    private static Vector3d getUpFromBlockType(@Nonnull BlockType blockType, boolean isWall) {
        return switch (blockType.getVariantRotation()) {
            case UpDown -> new Vector3d(0, 1, 0);
            default -> isWall ? new Vector3d(0, 0, 1) : new Vector3d(0, 1, 0);
        };
    }

    private static boolean isPushable(@Nullable BlockType blockType, int blockId) {
        if (blockId == 0)
            return false;

        if (blockType == null)
            return false;

        return blockType.getMaterial() != BlockMaterial.Empty;
    }

    private static boolean isEmpty(@Nullable BlockType blockType, int blockId) {
        if (blockId == 0)
            return true;

        if (blockType == null)
            return true;

        return blockType.getMaterial() == BlockMaterial.Empty;
    }

    private boolean isWallPusherVariant(@Nonnull ActivationContext ctx) {
        BlockType blockType = ctx.blockType();

        if (isWall)
            return true;

        if (blockType.getVariantRotation() == VariantRotation.Wall)
            return true;

        String id = blockType.getId();
        return id != null && id.toLowerCase().contains("wall");
    }

    /**
     * Walks forward from (px,py,pz) through contiguous same-type
     * MoveBlockActivation pushers; returns [x,y,z] of the front pusher.
     */
    private static Vector3i findFrontPusherInChain(
            ActivationContext ctx,
            Vector3i pusherPosition,
            Vector3i globalForward
           ) {
        World world = ctx.world();
        BlockType pusherBlockType = ctx.blockType();

        Vector3i currentPosition = pusherPosition.clone();
        Vector3i nextPosition = currentPosition.clone().add(globalForward);
        String pusherId = pusherBlockType.getId();

        if (pusherId == null)
            return currentPosition;

        while (true) {
            WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(nextPosition.x, nextPosition.z));

            if (chunk == null)
                break;

            int blockId = chunk.getBlock(nextPosition.x, nextPosition.y, nextPosition.z);
            BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
            if (blockId == 0 || blockType == null)
                break;

            if (!pusherId.equals(blockType.getId()))
                break;

            Activation act = ArcaneRelayPlugin.get().getActivationRegistry().getActivationForBlock(blockType.getId());
            if (act == null || !(act instanceof MoveBlockActivation))
                break;

            currentPosition.assign(nextPosition);
            nextPosition.add(globalForward); // += intended
        }

        return currentPosition;
    }

    private Vector3i getGlobalForwardVector(ActivationContext ctx, Vector3i pusherPosition) {
        boolean isWallPusher = isWallPusherVariant(ctx);
        BlockType pusherBlockType = ctx.blockType();
        
        WorldChunk pusherChunk = ctx.chunk();
        int pusherRotationIndex = pusherChunk.getRotationIndex(pusherPosition.x, pusherPosition.y, pusherPosition.z);

        RotationTuple pusherRotationTuple = RotationTuple.get(pusherRotationIndex);
        Vector3d localForward = getForwardFromBlockType(pusherBlockType, isWallPusher);
        Vector3d globalForwardDouble = pusherRotationTuple.rotate(localForward.clone());
        
        return new Vector3i(
            (int) Math.round(globalForwardDouble.getX()),
            (int) Math.round(globalForwardDouble.getY()),
            (int) Math.round(globalForwardDouble.getZ())
        );
    }
    
    private Vector3i getGlobalUpVector(ActivationContext ctx, Vector3i pusherPosition) {
        boolean isWallPusher = isWallPusherVariant(ctx);
        BlockType pusherBlockType = ctx.blockType();

        WorldChunk pusherChunk = ctx.chunk();
        int pusherRotationIndex = pusherChunk.getRotationIndex(pusherPosition.x, pusherPosition.y, pusherPosition.z);

        Vector3d localUp = getUpFromBlockType(pusherBlockType, isWallPusher);
        RotationTuple upRotationTuple = RotationTuple.get(pusherRotationIndex);
        Vector3d upDirection = upRotationTuple.rotate(localUp.clone());
        
        return new Vector3i(
            (int) Math.round(upDirection.getX()),
            (int) Math.round(upDirection.getY()),
            (int) Math.round(upDirection.getZ())
        );
    }

    @Override
    public void execute(@Nonnull ActivationContext ctx) {
        ArcaneRelayPlugin.get().getLogger().atInfo().log("MoveBlockActivation: executing move block activation at "
                + ctx.blockX() + ", " + ctx.blockY() + ", " + ctx.blockZ());

        World world = ctx.world();
        Vector3i pusherPosition = new Vector3i(ctx.blockX(), ctx.blockY(), ctx.blockZ());

        // dx, dy, dz is globalForward.xyz
        // pdx, pdy, pdz
        Vector3i globalForward = getGlobalForwardVector(ctx, pusherPosition);
        if (globalForward.length() == 0)
            return;

        // ux, uy, yz is globalUpVector.xyz
        // pux, puy, puz
        Vector3i scaledGlobalUpVector = getGlobalUpVector(ctx, pusherPosition).scale(this.upAmount);
        if (scaledGlobalUpVector.length() == 0)
            return;

        // Vector3i frontPusherPosition = findFrontPusherInChain(ctx, pusherPosition, globalForward);
        Vector3i frontPusherPosition = pusherPosition.clone();
        int maxRange = Math.max(1, range);

        int[] chainBlockIds               = new int[maxRange];
        int[] chainRotations              = new int[maxRange];
        int[] chainFillers                = new int[maxRange];
        BlockType[] chainBlockTypes       = new BlockType[maxRange];
        Holder<ChunkStore>[] chainHolders = new Holder[maxRange];

        int chainLength = 0;
        for (int i = 0; i < maxRange; i++) {
            Vector3i c = frontPusherPosition.clone().add(globalForward.clone().scale(i).add(scaledGlobalUpVector));

            WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(c.x, c.z));
            if (chunk == null)
                break;

            int blockId = chunk.getBlock(c.x, c.y, c.z);
            BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
            if (!isPushable(blockType, blockId))
                break;

            chainBlockIds[chainLength]     = blockId;
            chainRotations[chainLength]    = chunk.getRotationIndex(c.x, c.y, c.z);
            chainFillers[chainLength]      = chunk.getFiller(c.x, c.y, c.z);
            chainBlockTypes[chainLength]   = blockType;

            Holder<ChunkStore> stateHolder = chunk.getBlockComponentHolder(c.x, c.y, c.z);
            chainHolders[chainLength]      = stateHolder != null ? stateHolder.clone() : null;
            
            chainLength++;
        }

        Vector3i nextEmptyPosition = frontPusherPosition.clone().add(globalForward.clone().scale(chainLength).add(scaledGlobalUpVector));
        WorldChunk emptyChunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(nextEmptyPosition.x, nextEmptyPosition.z));
        if (emptyChunk == null)
            return;

        int emptyBlockId = emptyChunk.getBlock(nextEmptyPosition.x, nextEmptyPosition.y, nextEmptyPosition.z);
        BlockType emptyBlockType = BlockType.getAssetMap().getAsset(emptyBlockId);
        if (!isEmpty(emptyBlockType, emptyBlockId))
            return;

        movePlayers(world, globalForward, scaledGlobalUpVector, frontPusherPosition, nextEmptyPosition, chainLength);

        if (chainLength == 0)
            return;

        // Move Blocks
        for (int j = chainLength - 1; j >= 0; j--) {
            Vector3i fromPosition = frontPusherPosition.clone().add(globalForward.clone().scale(j).add(scaledGlobalUpVector));
            Vector3i toPosition = frontPusherPosition.clone().add(globalForward.clone().scale(j + 1).add(scaledGlobalUpVector));
    
            WorldChunk fromChunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(fromPosition.x, fromPosition.z));
            WorldChunk toChunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(toPosition.x, toPosition.y));
            if (fromChunk == null || toChunk == null)
                continue;

            final int idx = j;
            world.execute(() -> {
                ArcaneMoveState arcaneMoveState = world.getChunkStore().getStore().getResource(ArcaneMoveState.getResourceType());

                if (arcaneMoveState == null) {
                    ArcaneRelayPlugin.get().getLogger().atInfo().log("ArcaneTickSystem: no arcane move state");
                    return;
                }

                arcaneMoveState.addMoveEntry(fromPosition,
                    toPosition.clone().subtract(fromPosition), chainBlockTypes[idx], chainBlockIds[idx],
                    chainRotations[idx], chainFillers[idx], 0, chainHolders[idx]);
                    
                Vector3i destinationPosition = frontPusherPosition.clone().add(globalForward).add(scaledGlobalUpVector);
                ActivationExecutor.playEffects(world, destinationPosition.x, destinationPosition.y, destinationPosition.z,
                    getEffects());
            });
        }
    }

    private void movePlayers(World world, Vector3i globalForward, Vector3i scaledGlobalUpVector,
            Vector3i frontPusherPosition, Vector3i nextEmptyPosition, final int len) {
        // Collision check before move (same tick): collect player refs on top of empty
        // block or any chain block
        Store<EntityStore> entityStore = world.getEntityStore().getStore();
        Set<Ref<EntityStore>> entitiesOnTop = new HashSet<>();
        collectEntitiesOnTopOfBlock(entityStore, nextEmptyPosition, entitiesOnTop);
        collectEntitiesOnTopOfBlock(entityStore, frontPusherPosition.clone(), entitiesOnTop);
        
        for (int i = 0; i < len; i++) {
            Vector3i fromPosition = frontPusherPosition.clone().add(globalForward.clone().scale(i).add(scaledGlobalUpVector));
            collectEntitiesOnTopOfBlock(entityStore, fromPosition, entitiesOnTop);
        }

        final List<Ref<EntityStore>> entitiesOnTopList = new ArrayList<>(entitiesOnTop);

        // 1) Move players we found on top (in chain direction), then move any others in
        // boxes (excluding already moved)
        for (Ref<EntityStore> ref : entitiesOnTopList) {
            if (ref == null || !ref.isValid())
                continue;

            TransformComponent transform = entityStore.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null)
                continue;
                
            moveEntityWithBlock(world, entityStore, ref, transform, globalForward);
        }
    }

    private static final double FEET_Y_OFFSET = -0.5;

    private static Vector3d getFeetPosition(@Nonnull TransformComponent transform,
            @Nullable BoundingBox boundingBox) {
        Vector3d feetPosition = transform.getPosition().clone();

        if (boundingBox != null) {
            feetPosition.add(0, boundingBox.getBoundingBox().min.y, 0);
        } else {
            feetPosition.add(0, FEET_Y_OFFSET, 0);
        }

        return feetPosition;
    }

    private static boolean isFeetOnTopOfBlock(Vector3d feetPosition, Vector3i blockPosition) {
        return feetPosition.x >= blockPosition.x - 0.1 && feetPosition.x <= blockPosition.x + 1.1 
            && feetPosition.y >= blockPosition.y + 0.95 && feetPosition.y <= blockPosition.y + 1.1 
            && feetPosition.z >= blockPosition.z - 0.1 && feetPosition.z <= blockPosition.z + 1.1;
    }

    /**
     * Collects player refs whose feet are on top of the block at (blockX, blockY,
     * blockZ) into the given set.
     */
    private static void collectEntitiesOnTopOfBlock(
            @Nonnull Store<EntityStore> entityStore,
            Vector3i blockPosition,
            @Nonnull Set<Ref<EntityStore>> out) {
        Vector3d min = new Vector3d(blockPosition.x - 0.1, blockPosition.y + 0.9, blockPosition.z - 0.1);
        Vector3d max = new Vector3d(blockPosition.x + 1.1, blockPosition.y + 2.1, blockPosition.z + 1.1);
        
        for (var ref : TargetUtil.getAllEntitiesInBox(min, max, entityStore)) {
            if (ref == null || !ref.isValid())
                continue;

            TransformComponent transform = entityStore.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null)
                continue;
                
            BoundingBox boundingBox = entityStore.getComponent(ref, BoundingBox.getComponentType());
            Vector3d feet = getFeetPosition(transform, boundingBox);
      
            if (isFeetOnTopOfBlock(feet, blockPosition))
                out.add(ref);
        }
    }

    private static boolean isFeetInsideBlock(Vector3d feetPosition, Vector3i blockPosition) {
        return feetPosition.x >= blockPosition.x && feetPosition.x < blockPosition.x + 1 
            && feetPosition.y >= blockPosition.y && feetPosition.y < blockPosition.y + 1 
            && feetPosition.z >= blockPosition.z && feetPosition.z < blockPosition.z + 1;
    }

    /** True when push direction is purely up (elevator). */
    private static boolean isPushUp(Vector3i direction) {
        return direction.y > 0 && direction.x == 0 && direction.z == 0;
    }

    /**
     * Apply knockback in push direction (dx, dy, dz), speed limited to
     * KNOCKBACK_MAX_SPEED.
     */
    private static void applyKnockbackWithLimit(
            @Nonnull Store<EntityStore> entityStore,
            @Nonnull Ref<EntityStore> ref,
            Vector3i direction) {
        Vector3d velocity = new Vector3d(direction.clone().normalize());
        velocity.scale(KNOCKBACK_MAX_SPEED);

        KnockbackComponent knockback = entityStore.ensureAndGetComponent(ref, KnockbackComponent.getComponentType());
        knockback.setVelocity(velocity);
        knockback.setVelocityType(ChangeVelocityType.Set);
        knockback.setVelocityConfig(new VelocityConfig());
        knockback.setDuration(KNOCKBACK_DURATION);
    }

    /**
     * Move player by (dx, dy, dz): teleport when push is up (preserve head), else
     * limited knockback.
     */
    private static void moveEntityWithBlock(
            @Nonnull World world,
            @Nonnull Store<EntityStore> entityStore,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull TransformComponent transform,
            Vector3i direction) {
        PlayerRef playerRef = entityStore.getComponent(ref, PlayerRef.getComponentType());

        if (isPushUp(direction) && playerRef == null) {
            teleportPlayerWithBlock(world, entityStore, ref, transform, direction);
        } else {
            applyKnockbackWithLimit(entityStore, ref, direction);
        }
    }

    /** Teleport player by (dx, dy, dz) blocks, preserving head rotation. */
    private static void teleportPlayerWithBlock(
            @Nonnull World world,
            @Nonnull Store<EntityStore> entityStore,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull TransformComponent transform,
            Vector3i direction) {
        Vector3d pos = transform.getPosition().clone();
        Vector3d newPos = pos.add(direction);

        HeadRotation headComp = entityStore.getComponent(ref, HeadRotation.getComponentType());
        Vector3f headRot = headComp != null ? headComp.getRotation().clone() : transform.getRotation().clone();

        Teleport teleport = Teleport.createForPlayer(world, newPos, transform.getRotation()).setHeadRotation(headRot);
        entityStore.addComponent(ref, Teleport.getComponentType(), teleport);
    }
}
