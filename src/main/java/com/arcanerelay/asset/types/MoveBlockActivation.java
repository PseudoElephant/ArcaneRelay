package com.arcanerelay.asset.types;

import com.arcanerelay.ArcaneRelayPlugin;
import com.arcanerelay.asset.Activation;
import com.arcanerelay.asset.ActivationContext;
import com.arcanerelay.asset.ActivationExecutor;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.VariantRotation;
import com.hypixel.hytale.protocol.ChangeVelocityType;
import com.hypixel.hytale.server.core.entity.knockback.KnockbackComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
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

/**
 * Activation that moves the block in the "up" direction (relative to this block)
 * one step towards the "front" direction. The pusher block stays in place.
 * Range is how many steps in the front direction the block is moved (default: 1).
 *
 * <p>Player interaction: if the player is at the end of the chain (destination cell), they get
 * pushed. If the player is on top of any block in the chain, they remain on top / get pushed.
 * When the block moves <b>up</b> (elevator): teleport so head rotation is preserved.
 * When the block moves in another direction: limited knockback (speed capped).
 */
public class MoveBlockActivation extends Activation {

    public static final BuilderCodec<MoveBlockActivation> CODEC =
        BuilderCodec.builder(
            MoveBlockActivation.class,
            MoveBlockActivation::new,
            Activation.ABSTRACT_CODEC
        )
        .documentation("Pushes blocks in front in the facing direction. Range limits max chain length.")
        .appendInherited(
            new KeyedCodec<>("Range", Codec.INTEGER),
            (a, r) -> a.range = r,
            a -> a.range,
            (a, p) -> a.range = p.range
        )
        .documentation("Maximum number of blocks to push in a chain (default: 1).")
        .add()
        .appendInherited(
            new KeyedCodec<>("IsWall", Codec.BOOLEAN),
            (a, w) -> a.isWall = w,
            a -> a.isWall,
            (a, p) -> a.isWall = p.isWall
        )
        .documentation("Whether the block is a wall (default: false).")
        .add()
        .build();

    private int range = 1;
    private int upAmount = 1;
    private boolean isWall = false;

    public MoveBlockActivation() {
    }

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
        if (blockId == 0) return false;
        if (blockType == null) return false;
        return blockType.getMaterial() != BlockMaterial.Empty;
    }

    private static boolean isEmpty(@Nullable BlockType blockType, int blockId) {
        if (blockId == 0) return true;
        if (blockType == null) return true;
        return blockType.getMaterial() == BlockMaterial.Empty;
    }

    private boolean effectiveIsWall(@Nonnull ActivationContext ctx) {
        BlockType blockType = ctx.blockType();
        if (isWall) return true;
        if (blockType.getVariantRotation() == VariantRotation.Wall) return true;
        String id = blockType.getId();
        return id != null && id.toLowerCase().contains("wall");
    }

    private static boolean isWallForBlockType(@Nonnull BlockType blockType) {
        return blockType.getVariantRotation() == VariantRotation.Wall
            || (blockType.getId() != null && blockType.getId().toLowerCase().contains("wall"));
    }

    /**
     * Returns true if this block is the first pusher in a contiguous chain (no pusher behind it in the push direction).
     */
    public static boolean isFirstPusherInChain(
        @Nonnull World world,
        @Nonnull WorldChunk chunk,
        int blockX, int blockY, int blockZ,
        @Nonnull BlockType blockType
    ) {
        Activation activation = ArcaneRelayPlugin.get().getActivationRegistry().getActivationForBlock(blockType.getId());
        if (activation == null || !(activation instanceof MoveBlockActivation)) return true;
        boolean wall = isWallForBlockType(blockType);
        int rotationIndex = chunk.getRotationIndex(blockX, blockY, blockZ);
        Vector3d localForward = getForwardFromBlockType(blockType, wall);
        Vector3d forward = RotationTuple.get(rotationIndex).rotate(localForward.clone());
        int dx = (int) Math.round(forward.getX());
        int dy = (int) Math.round(forward.getY());
        int dz = (int) Math.round(forward.getZ());
        if (dx == 0 && dy == 0 && dz == 0) return true;
        int backX = blockX - dx, backY = blockY - dy, backZ = blockZ - dz;
        WorldChunk backChunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(backX, backZ));
        if (backChunk == null) return true;
        int backBlockId = backChunk.getBlock(backX, backY, backZ);
        BlockType backBlockType = BlockType.getAssetMap().getAsset(backBlockId);
        if (backBlockId == 0 || backBlockType == null) return true;
        if (!blockType.getId().equals(backBlockType.getId())) return true;
        Activation backActivation = ArcaneRelayPlugin.get().getActivationRegistry().getActivationForBlock(backBlockType.getId());
        if (backActivation == null || !(backActivation instanceof MoveBlockActivation)) return true;
        return false;
    }

    /** Walks forward from (px,py,pz) through contiguous same-type MoveBlockActivation pushers; returns [x,y,z] of the front pusher. */
    private static int[] findFrontPusherInChain(
        @Nonnull World world,
        int px, int py, int pz,
        int dx, int dy, int dz,
        @Nonnull BlockType pusherBlockType
    ) {
        int fx = px, fy = py, fz = pz;
        int nx = px + dx, ny = py + dy, nz = pz + dz;
        String pusherId = pusherBlockType.getId();
        if (pusherId == null) return new int[] { fx, fy, fz };
        while (true) {
            WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(nx, nz));
            if (chunk == null) break;
            int blockId = chunk.getBlock(nx, ny, nz);
            BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
            if (blockId == 0 || blockType == null) break;
            if (!pusherId.equals(blockType.getId())) break;
            Activation act = ArcaneRelayPlugin.get().getActivationRegistry().getActivationForBlock(blockType.getId());
            if (act == null || !(act instanceof MoveBlockActivation)) break;
            fx = nx; fy = ny; fz = nz;
            nx += dx; ny += dy; nz += dz;
        }
        return new int[] { fx, fy, fz };
    }

    /** Returns [dx, dy, dz] push direction for this block (world-space forward). For non-pushers returns [0,0,0]. */
    public static int[] getForwardDirection(
        @Nonnull WorldChunk chunk,
        int blockX, int blockY, int blockZ,
        @Nonnull BlockType blockType
    ) {
        Activation activation = ArcaneRelayPlugin.get().getActivationRegistry().getActivationForBlock(blockType.getId());
        if (activation == null || !(activation instanceof MoveBlockActivation)) return new int[] { 0, 0, 0 };
        boolean wall = isWallForBlockType(blockType);
        int rotationIndex = chunk.getRotationIndex(blockX, blockY, blockZ);
        Vector3d localForward = getForwardFromBlockType(blockType, wall);
        Vector3d forward = RotationTuple.get(rotationIndex).rotate(localForward.clone());
        int dx = (int) Math.round(forward.getX());
        int dy = (int) Math.round(forward.getY());
        int dz = (int) Math.round(forward.getZ());
        return new int[] { dx, dy, dz };
    }

    /** Returns [frontX, frontY, frontZ] of the front pusher in the chain containing (blockX, blockY, blockZ). For non-pushers or single pusher, returns the block position. */
    public static int[] getFrontPusherPosition(
        @Nonnull World world,
        @Nonnull WorldChunk chunk,
        int blockX, int blockY, int blockZ,
        @Nonnull BlockType blockType
    ) {
        Activation activation = ArcaneRelayPlugin.get().getActivationRegistry().getActivationForBlock(blockType.getId());
        if (activation == null || !(activation instanceof MoveBlockActivation)) return new int[] { blockX, blockY, blockZ };
        boolean wall = isWallForBlockType(blockType);
        int rotationIndex = chunk.getRotationIndex(blockX, blockY, blockZ);
        Vector3d localForward = getForwardFromBlockType(blockType, wall);
        Vector3d forward = RotationTuple.get(rotationIndex).rotate(localForward.clone());
        int dx = (int) Math.round(forward.getX());
        int dy = (int) Math.round(forward.getY());
        int dz = (int) Math.round(forward.getZ());
        if (dx == 0 && dy == 0 && dz == 0) return new int[] { blockX, blockY, blockZ };
        return findFrontPusherInChain(world, blockX, blockY, blockZ, dx, dy, dz, blockType);
    }

    @Override
    public void execute(@Nonnull ActivationContext ctx) {
        ArcaneRelayPlugin.get().getLogger().atInfo().log("MoveBlockActivation: executing move block activation at " + ctx.blockX() + ", " + ctx.blockY() + ", " + ctx.blockZ());
        World world = ctx.world();
        int px = ctx.blockX();
        int py = ctx.blockY();
        int pz = ctx.blockZ();
        BlockType pusherBlockType = ctx.blockType();
        boolean wall = effectiveIsWall(ctx);

        WorldChunk pusherChunk = ctx.chunk();
        int rotationIndex = pusherChunk.getRotationIndex(px, py, pz);

        Vector3d localForward = getForwardFromBlockType(pusherBlockType, wall);
        RotationTuple rotationTuple = RotationTuple.get(rotationIndex);
        Vector3d forward = rotationTuple.rotate(localForward.clone());
        int dx = (int) Math.round(forward.getX());
        int dy = (int) Math.round(forward.getY());
        int dz = (int) Math.round(forward.getZ());

        Vector3d up = getUpFromBlockType(pusherBlockType, wall);
        RotationTuple upRotationTuple = RotationTuple.get(rotationIndex);
        Vector3d upDirection = upRotationTuple.rotate(up.clone());
        int ux = (int) Math.round(upDirection.getX());
        int uy = (int) Math.round(upDirection.getY());
        int uz = (int) Math.round(upDirection.getZ());

        if (dx == 0 && dy == 0 && dz == 0) return;

        int maxRange = Math.max(1, range);
        int pux = upAmount * ux;
        int puy = upAmount * uy;
        int puz = upAmount * uz;

        int[] frontPos = findFrontPusherInChain(world, px, py, pz, dx, dy, dz, pusherBlockType);
        int frontPx = frontPos[0], frontPy = frontPos[1], frontPz = frontPos[2];

        int[] chainBlockIds = new int[maxRange];
        int[] chainRotations = new int[maxRange];
        int[] chainFillers = new int[maxRange];
        BlockType[] chainBlockTypes = new BlockType[maxRange];
        @SuppressWarnings("unchecked")
        Holder<ChunkStore>[] chainHolders = new Holder[maxRange];
        int chainLength = 0;

        for (int i = 0; i < maxRange; i++) {
            int cx = frontPx + i * dx + pux;
            int cy = frontPy + i * dy + puy;
            int cz = frontPz + i * dz + puz;

            WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(cx, cz));
            if (chunk == null) break;

            int blockId = chunk.getBlock(cx, cy, cz);
            BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
            if (!isPushable(blockType, blockId)) break;

            chainBlockIds[chainLength] = blockId;
            chainRotations[chainLength] = chunk.getRotationIndex(cx, cy, cz);
            chainFillers[chainLength] = chunk.getFiller(cx, cy, cz);
            chainBlockTypes[chainLength] = blockType;
            Holder<ChunkStore> stateHolder = chunk.getBlockComponentHolder(cx, cy, cz);
            chainHolders[chainLength] = stateHolder != null ? stateHolder.clone() : null;
            chainLength++;
        }

        if (chainLength == 0) return;

        int emptyX = frontPx + chainLength * dx + pux;
        int emptyY = frontPy + chainLength * dy + puy;
        int emptyZ = frontPz + chainLength * dz + puz;

        WorldChunk emptyChunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(emptyX, emptyZ));
        if (emptyChunk == null) return;

        int emptyBlockId = emptyChunk.getBlock(emptyX, emptyY, emptyZ);
        BlockType emptyBlockType = BlockType.getAssetMap().getAsset(emptyBlockId);
        if (!isEmpty(emptyBlockType, emptyBlockId)) return;

        final int len = chainLength;
        final int[] ids = chainBlockIds;
        final int[] rots = chainRotations;
        final int[] fills = chainFillers;
        final BlockType[] types = chainBlockTypes;
        final Holder<ChunkStore>[] holders = chainHolders;
        final int pdx = dx, pdy = dy, pdz = dz;

        // Collision check before move (same tick): collect player refs on top of empty block or any chain block
        Store<EntityStore> entityStoreForCheck = world.getEntityStore().getStore();
        Set<Ref<EntityStore>> playersOnTop = new HashSet<>();
        collectPlayersOnTopOfBlock(entityStoreForCheck, emptyX, emptyY, emptyZ, playersOnTop);
        for (int i = 0; i < len; i++) {
            int fromX = frontPx + i * pdx + pux;
            int fromY = frontPy + i * pdy + puy;
            int fromZ = frontPz + i * pdz + puz;
            collectPlayersOnTopOfBlock(entityStoreForCheck, fromX, fromY, fromZ, playersOnTop);
        }
        final List<Ref<EntityStore>> playersOnTopList = new ArrayList<>(playersOnTop);

        world.execute(() -> {
            Store<EntityStore> entityStore = world.getEntityStore().getStore();

            // 1) Move players we found on top (in chain direction), then move any others in boxes (excluding already moved)
            for (Ref<EntityStore> ref : playersOnTopList) {
                if (ref == null || !ref.isValid()) continue;
                TransformComponent transform = entityStore.getComponent(ref, TransformComponent.getComponentType());
                if (transform == null) continue;
                movePlayerWithBlock(world, entityStore, ref, transform, pdx, pdy, pdz);
            }
        
            // 2) Move blocks (back to front: block closest to pusher first)
            LongSet dirtyChunks = new LongOpenHashSet();
            for (int i = len - 1; i >= 0; i--) {
                int fromX = frontPx + i * pdx + pux;
                int fromY = frontPy + i * pdy + puy;
                int fromZ = frontPz + i * pdz + puz;
                int toX = frontPx + (i + 1) * pdx + pux;
                int toY = frontPy + (i + 1) * pdy + puy;
                int toZ = frontPz + (i + 1) * pdz + puz;

                WorldChunk fromChunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(fromX, fromZ));
                WorldChunk toChunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(toX, toZ));
                if (fromChunk == null || toChunk == null) continue;

                dirtyChunks.add(fromChunk.getIndex());
                dirtyChunks.add(toChunk.getIndex());

                world.breakBlock(fromX, fromY, fromZ, 0);
                toChunk.setBlock(toX, toY, toZ, ids[i], types[i], rots[i], fills[i], 0);
                if (holders[i] != null) {
                    toChunk.setState(toX, toY, toZ, holders[i]);
                }
            }

            world.execute(() -> {
            dirtyChunks.forEach(chunkIndex -> world.getNotificationHandler().updateChunk(chunkIndex));
                ActivationExecutor.playEffects(world, frontPx + pdx + pux, frontPy + pdy + puy, frontPz + pdz + puz, getEffects());
                ActivationExecutor.sendSignals(ctx);
            });
        });
    }

    private static final double FEET_Y_OFFSET = -0.5;

    private static void getFeetPosition(@Nonnull Vector3d out, @Nonnull TransformComponent transform, @Nullable BoundingBox boundingBox) {
        out.assign(transform.getPosition());
        if (boundingBox != null) {
            out.add(0, boundingBox.getBoundingBox().min.y, 0);
        } else {
            out.add(0, FEET_Y_OFFSET, 0);
        }
    }

    private static boolean isFeetOnTopOfBlock(double fx, double fy, double fz, int blockX, int blockY, int blockZ) {
        return fy >= blockY + 0.95 && fy <= blockY + 1.1
            && fx >= blockX && fx <= blockX + 1 && fz >= blockZ && fz <= blockZ + 1;
    }

    /** Collects player refs whose feet are on top of the block at (blockX, blockY, blockZ) into the given set. */
    private static void collectPlayersOnTopOfBlock(
        @Nonnull Store<EntityStore> entityStore,
        int blockX, int blockY, int blockZ,
        @Nonnull Set<Ref<EntityStore>> out
    ) {
        Vector3d min = new Vector3d(blockX - 0.1, blockY + 0.9, blockZ - 0.1);
        Vector3d max = new Vector3d(blockX + 1.1, blockY + 2.1, blockZ + 1.1);
        Vector3d feet = new Vector3d();
        for (var ref : TargetUtil.getAllEntitiesInBox(min, max, entityStore)) {
            if (ref == null || !ref.isValid()) continue;
            if (entityStore.getComponent(ref, PlayerRef.getComponentType()) == null) continue;
            TransformComponent transform = entityStore.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) continue;
            BoundingBox boundingBox = entityStore.getComponent(ref, BoundingBox.getComponentType());
            getFeetPosition(feet, transform, boundingBox);
            if (isFeetOnTopOfBlock(feet.getX(), feet.getY(), feet.getZ(), blockX, blockY, blockZ)) out.add(ref);
        }
    }

    private static boolean isFeetInsideBlock(double fx, double fy, double fz, int blockX, int blockY, int blockZ) {
        return fx >= blockX && fx < blockX + 1 && fy >= blockY && fy < blockY + 1 && fz >= blockZ && fz < blockZ + 1;
    }

    /** True when push direction is purely up (elevator). */
    private static boolean isPushUp(int dx, int dy, int dz) {
        return dy > 0 && dx == 0 && dz == 0;
    }

    /** Max knockback speed (blocks per tick) when block moves in non-up direction. */
    private static final double KNOCKBACK_MAX_SPEED = 0.35;
    private static final float KNOCKBACK_DURATION = 0.2f;

    /** Apply knockback in push direction (dx, dy, dz), speed limited to KNOCKBACK_MAX_SPEED. */
    private static void applyKnockbackWithLimit(
        @Nonnull Store<EntityStore> entityStore,
        @Nonnull Ref<EntityStore> ref,
        int dx, int dy, int dz
    ) {
        double lenSq = MathUtil.lengthSquared(dx, dy, dz);
        if (lenSq < 1e-6) return;
        double len = Math.sqrt(lenSq);
        Vector3d vel = new Vector3d(dx / len, dy / len, dz / len);
        vel.scale(KNOCKBACK_MAX_SPEED);
        entityStore.ensureAndGetComponent(ref, Velocity.getComponentType());
        KnockbackComponent knockback = entityStore.ensureAndGetComponent(ref, KnockbackComponent.getComponentType());
        knockback.setVelocity(vel);
        knockback.setVelocityType(ChangeVelocityType.Add);
        knockback.setVelocityConfig(null);
        knockback.setDuration(KNOCKBACK_DURATION);
    }

    /** Move player by (dx, dy, dz): teleport when push is up (preserve head), else limited knockback. */
    private static void movePlayerWithBlock(
        @Nonnull World world,
        @Nonnull Store<EntityStore> entityStore,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull TransformComponent transform,
        int dx, int dy, int dz
    ) {
        if (isPushUp(dx, dy, dz)) {
            teleportPlayerWithBlock(world, entityStore, ref, transform, dx, dy, dz);
        } else {
            applyKnockbackWithLimit(entityStore, ref, dx, dy, dz);
        }
    }

    /** Teleport player by (dx, dy, dz) blocks, preserving head rotation. */
    private static void teleportPlayerWithBlock(
        @Nonnull World world,
        @Nonnull Store<EntityStore> entityStore,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull TransformComponent transform,
        int dx, int dy, int dz
    ) {
        Vector3d pos = transform.getPosition();
        Vector3d newPos = new Vector3d(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);

        HeadRotation headComp = entityStore.getComponent(ref, HeadRotation.getComponentType());
        Vector3f headRot = headComp != null ? headComp.getRotation().clone() : transform.getRotation().clone();

        Teleport teleport = Teleport.createForPlayer(world, newPos, transform.getRotation()).setHeadRotation(headRot);
        entityStore.addComponent(ref, Teleport.getComponentType(), teleport);
    }

    /** Move players standing on top of block (bx, by, bz) by (dx, dy, dz), excluding refs in excludeRefs (already moved). */
    private static void movePlayersOnTopOfBlock(
        @Nonnull World world,
        @Nonnull Store<EntityStore> entityStore,
        int blockX, int blockY, int blockZ,
        int dx, int dy, int dz,
        @Nonnull List<Ref<EntityStore>> excludeRefs
    ) {
        Vector3d min = new Vector3d(blockX - 0.1, blockY + 0.9, blockZ - 0.1);
        Vector3d max = new Vector3d(blockX + 1.1, blockY + 2.1, blockZ + 1.1);
        Vector3d feet = new Vector3d();

        for (var ref : TargetUtil.getAllEntitiesInBox(min, max, entityStore)) {
            if (ref == null || !ref.isValid()) continue;
            if (excludeRefs.contains(ref)) continue;
            if (entityStore.getComponent(ref, PlayerRef.getComponentType()) == null) continue;

            TransformComponent transform = entityStore.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) continue;

            BoundingBox boundingBox = entityStore.getComponent(ref, BoundingBox.getComponentType());
            getFeetPosition(feet, transform, boundingBox);
            if (!isFeetOnTopOfBlock(feet.getX(), feet.getY(), feet.getZ(), blockX, blockY, blockZ)) continue;

            movePlayerWithBlock(world, entityStore, ref, transform, dx, dy, dz);
        }
    }

    /** Move players whose feet are inside block (bx, by, bz) by (dx, dy, dz), excluding refs in excludeRefs (already moved). */
    private static void movePlayersInBlock(
        @Nonnull World world,
        @Nonnull Store<EntityStore> entityStore,
        int blockX, int blockY, int blockZ,
        int dx, int dy, int dz,
        @Nonnull List<Ref<EntityStore>> excludeRefs
    ) {
        Vector3d min = new Vector3d(blockX - 0.1, blockY - 0.1, blockZ - 0.1);
        Vector3d max = new Vector3d(blockX + 1.1, blockY + 2.1, blockZ + 1.1);
        Vector3d feet = new Vector3d();

        for (var ref : TargetUtil.getAllEntitiesInBox(min, max, entityStore)) {
            if (ref == null || !ref.isValid()) continue;
            if (excludeRefs.contains(ref)) continue;
            if (entityStore.getComponent(ref, PlayerRef.getComponentType()) == null) continue;

            TransformComponent transform = entityStore.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) continue;

            BoundingBox boundingBox = entityStore.getComponent(ref, BoundingBox.getComponentType());
            getFeetPosition(feet, transform, boundingBox);
            if (!isFeetInsideBlock(feet.getX(), feet.getY(), feet.getZ(), blockX, blockY, blockZ)) continue;

            movePlayerWithBlock(world, entityStore, ref, transform, dx, dy, dz);
        }
    }
}
