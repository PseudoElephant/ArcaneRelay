package com.arcanerelay.asset.types;

import com.arcanerelay.asset.Activation;
import com.arcanerelay.ArcaneRelayPlugin;
import com.arcanerelay.asset.ActivationContext;
import com.arcanerelay.asset.ActivationExecutor;
import com.arcanerelay.util.BlockUtil;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.math.util.TrigMathUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.VariantRotation;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Activation that toggles a door block in front of the arcane block.
 * Uses the same door state logic as {@link com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.DoorInteraction}:
 * states CLOSED, OPENED_IN, OPENED_OUT and interaction state strings OpenDoorIn, OpenDoorOut, CloseDoorIn, CloseDoorOut.
 */
public class ToggleDoorActivation extends Activation {

    public static final BuilderCodec<ToggleDoorActivation> CODEC =
        BuilderCodec.builder(
            ToggleDoorActivation.class,
            ToggleDoorActivation::new,
            Activation.ABSTRACT_CODEC
        )
        .documentation("Toggles a door block in front of this block. Uses same state logic as DoorInteraction.")
        .appendInherited(
            new KeyedCodec<>("Horizontal", Codec.BOOLEAN),
            (a, h) -> a.horizontal = h,
            a -> a.horizontal,
            (a, p) -> a.horizontal = p.horizontal
        )
        .documentation("Whether the door is horizontal (e.g. gate) or vertical (e.g. regular door). Affects forward direction. Default: false.")
        .add()
        .appendInherited(
            new KeyedCodec<>("OpenIn", Codec.BOOLEAN),
            (a, o) -> a.openIn = o,
            a -> a.openIn,
            (a, p) -> a.openIn = p.openIn
        )
        .documentation("When opening from closed, open inward (true) or outward (false). Default: true.")
        .add()
        .appendInherited(
            new KeyedCodec<>("IsWall", Codec.BOOLEAN),
            (a, w) -> a.isWall = w,
            a -> a.isWall,
            (a, p) -> a.isWall = p.isWall
        )
        .documentation("Whether this block is wall-mounted (affects forward direction). Default: false.")
        .add()
        .build();

    private boolean horizontal = false;
    private boolean openIn = true;
    private boolean isWall = false;

    public ToggleDoorActivation() {
    }

    /** Door states matching DoorInteraction.DoorState. */
    private enum DoorState {
        CLOSED,
        OPENED_IN,
        OPENED_OUT;

        @Nonnull
        static DoorState fromBlockState(@Nullable String state) {
            if (state == null) return CLOSED;
            return switch (state) {
                case "OpenDoorOut" -> OPENED_IN;
                case "OpenDoorIn" -> OPENED_OUT;
                default -> CLOSED;
            };
        }
    }

    /** Interaction state string for transition from -> to (same as DoorInteraction.getInteractionState). */
    @Nonnull
    private static String getInteractionState(@Nonnull DoorState fromState, @Nonnull DoorState doorState) {
        if (doorState == DoorState.CLOSED && fromState == DoorState.OPENED_IN) {
            return "CloseDoorOut";
        }
        if (doorState == DoorState.CLOSED && fromState == DoorState.OPENED_OUT) {
            return "CloseDoorIn";
        }
        if (doorState == DoorState.OPENED_IN) {
            return "OpenDoorOut";
        }
        return "OpenDoorIn";
    }

    @Nonnull
    private static DoorState getOppositeDoorState(@Nonnull DoorState doorState) {
        return doorState == DoorState.OPENED_OUT ? DoorState.OPENED_IN
            : (doorState == DoorState.OPENED_IN ? DoorState.OPENED_OUT : DoorState.CLOSED);
    }

    /**
     * True if the source (activator) is in front of the door (same logic as DoorInteraction.isInFrontOfDoor).
     * When true, door opens OUT; when false, door opens IN.
     */
    private static boolean isSourceInFrontOfDoor(
        @Nonnull Vector3i doorBlockPosition,
        @Nullable Rotation doorRotationYaw,
        int sourceX, int sourceY, int sourceZ
    ) {
        double doorRotationRad = Math.toRadians(doorRotationYaw != null ? doorRotationYaw.getDegrees() : 0.0);
        Vector3d doorRotationVector = new Vector3d(TrigMathUtil.sin(doorRotationRad), 0.0, TrigMathUtil.cos(doorRotationRad));
        Vector3d sourcePos = new Vector3d(sourceX + 0.5, sourceY + 0.5, sourceZ + 0.5);
        Vector3d direction = Vector3d.directionTo(doorBlockPosition, sourcePos);
        return direction.dot(doorRotationVector) < 0.0;
    }

    /** Info for a door at a position (same as DoorInteraction.DoorInfo). */
    private record DoorInfo(
        @Nonnull BlockType blockType,
        int filler,
        @Nonnull Vector3i blockPosition,
        @Nonnull DoorState doorState
    ) {}

    /** Door at (x,y,z) with given yaw to check; returns null if not a door or rotation doesn't match. */
    @Nullable
    private static DoorInfo getDoorAtPosition(
        @Nonnull World world,
        int x, int y, int z,
        @Nonnull Rotation rotationToCheck
    ) {
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) return null;
        BlockType blockType = chunk.getBlockType(x, y, z);
        if (blockType == null || !blockType.isDoor()) return null;
        int rotationIndex = chunk.getRotationIndex(x, y, z);
        RotationTuple blockRotation = RotationTuple.get(rotationIndex);
        String blockState = blockType.getStateForBlock(blockType);
        DoorState doorState = DoorState.fromBlockState(blockState);
        Rotation doorRotation = blockRotation.yaw();
        int filler = chunk.getFiller(x, y, z);
        if (doorRotation != rotationToCheck) return null;
        return new DoorInfo(blockType, filler, new Vector3i(x, y, z), doorState);
    }

    /** Finds the other half of a double door (same logic as DoorInteraction.getDoubleDoor). */
    @Nullable
    private static DoorInfo getDoubleDoor(
        @Nonnull World world,
        @Nonnull Vector3i worldPosition,
        @Nonnull BlockType blockType,
        int rotation,
        @Nonnull DoorState doorStateToCheck
    ) {
        if (blockType.getItem() == null) return null;
        BlockType baseBlockType = BlockType.getAssetMap().getAsset(blockType.getItem().getId());
        if (baseBlockType == null) return null;
        int hitboxTypeIndex = baseBlockType.getHitboxTypeIndex();
        BlockBoundingBoxes blockBoundingBoxes = BlockBoundingBoxes.getAssetMap().getAsset(hitboxTypeIndex);
        if (blockBoundingBoxes == null) return null;
        BlockBoundingBoxes.RotatedVariantBoxes baseBoxes = blockBoundingBoxes.get(Rotation.None, Rotation.None, Rotation.None);
        if (baseBoxes == null) return null;
        int offsetX = (int) baseBoxes.getBoundingBox().getMax().x * 2 - 1;
        Vector3i offset = new Vector3i(offsetX, 0, 0);
        Rotation rotationToCheck = RotationTuple.get(rotation).yaw();
        Vector3i otherPos = worldPosition.clone().add(MathUtil.rotateVectorYAxis(offset, rotationToCheck.getDegrees(), false));
        DoorInfo matchingDoor = getDoorAtPosition(world, otherPos.x, otherPos.y, otherPos.z, rotationToCheck.flip());
        if (matchingDoor == null || matchingDoor.doorState() != doorStateToCheck || matchingDoor.filler() != 0) return null;
        BlockType matchingBlockType = matchingDoor.blockType();
        if (matchingBlockType.getItem() == null) return null;
        int matchingHitboxIndex = BlockType.getAssetMap().getAsset(matchingBlockType.getItem().getId()).getHitboxTypeIndex();
        return matchingHitboxIndex == hitboxTypeIndex ? matchingDoor : null;
    }

    /** Activates a door to the new state (set state + block updates like DoorInteraction.activateDoor). */
    @Nullable
    private static BlockType activateDoor(
        @Nonnull World world,
        @Nonnull BlockType blockType,
        @Nonnull Vector3i blockPosition,
        @Nonnull DoorState fromState,
        @Nonnull DoorState doorState
    ) {
        WorldChunk chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(blockPosition.x, blockPosition.z));
        if (chunk == null) return null;
        int rotationIndex = chunk.getRotationIndex(blockPosition.x, blockPosition.y, blockPosition.z);
        BlockBoundingBoxes oldHitbox = BlockBoundingBoxes.getAssetMap().getAsset(blockType.getHitboxTypeIndex());
        String interactionStateToSend = getInteractionState(fromState, doorState);
        BlockType blockTypeForState = blockType;
        if (blockType.getBlockForState(interactionStateToSend) == null && blockType.getItem() != null) {
            BlockType base = BlockType.getAssetMap().getAsset(blockType.getItem().getId());
            if (base != null && base.getBlockForState(interactionStateToSend) != null) blockTypeForState = base;
        }
        world.setBlockInteractionState(blockPosition, blockTypeForState, interactionStateToSend);
        BlockType currentBlockType = world.getBlockType(blockPosition);
        if (currentBlockType == null) return null;
        BlockType newBlockType = currentBlockType.getBlockForState(interactionStateToSend);
        if (oldHitbox != null) {
            BlockBoundingBoxes.RotatedVariantBoxes oldRotated = oldHitbox.get(rotationIndex);
            if (oldRotated != null) {
                FillerBlockUtil.forEachFillerBlock(oldRotated, (bx, by, bz) ->
                    world.performBlockUpdate(blockPosition.x + bx, blockPosition.y + by, blockPosition.z + bz));
            }
        }
        if (newBlockType != null) {
            BlockBoundingBoxes newHitbox = BlockBoundingBoxes.getAssetMap().getAsset(newBlockType.getHitboxTypeIndex());
            if (newHitbox != null && newHitbox != oldHitbox) {
                BlockBoundingBoxes.RotatedVariantBoxes newRotated = newHitbox.get(rotationIndex);
                if (newRotated != null) {
                    FillerBlockUtil.forEachFillerBlock(newRotated, (bx, by, bz) ->
                        world.performBlockUpdate(blockPosition.x + bx, blockPosition.y + by, blockPosition.z + bz));
                }
            }
        }
        return newBlockType;
    }

    private static Vector3d getForwardFromBlockType(@Nonnull BlockType blockType, boolean horizontal, boolean isWall) {
        if (horizontal) {
            return isWall ? new Vector3d(0, -1, 0) : new Vector3d(0, 0, -1);
        }
        return switch (blockType.getVariantRotation()) {
            case UpDown -> new Vector3d(0, 1, 0);
            default -> isWall ? new Vector3d(0, -1, 0) : new Vector3d(0, 0, -1);
        };
    }

    @Override
    public void execute(@Nonnull ActivationContext ctx) {
        World world = ctx.world();
        int px = ctx.blockX();
        int py = ctx.blockY();
        int pz = ctx.blockZ();
        BlockType doorBlockType = ctx.blockType();

        WorldChunk doorChunk = ctx.chunk();
        int rotationIndex = doorChunk.getRotationIndex(px, py, pz);

        // Resolve main block (non-filler) like DoorInteraction / BlockUtil.findMainBlock
        int[] main = BlockUtil.findMainBlock(world, doorChunk, px, py, pz);
        if (main == null) return;
        int mainX = main[0], mainY = main[1], mainZ = main[2];
        WorldChunk mainChunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(mainX, mainZ));
        if (mainChunk == null) return;
        BlockType mainBlockType = mainChunk.getBlockType(mainX, mainY, mainZ);
        if (mainBlockType == null || !mainBlockType.isDoor()) return;

        Vector3i mainPos = new Vector3i(mainX, mainY, mainZ);
        String blockState = mainBlockType.getStateForBlock(mainBlockType);
        DoorState currentState = DoorState.fromBlockState(blockState);

        int rotation = mainChunk.getRotationIndex(mainX, mainY, mainZ);
        Rotation doorYaw = RotationTuple.get(rotation).yaw();

        DoorState newState;
        if (currentState == DoorState.CLOSED) {
            // Open direction from source position (like DoorInteraction: in front -> open out, else open in)
            if (horizontal) {
                newState = openIn ? DoorState.OPENED_IN : DoorState.OPENED_OUT;
            } else {
                int sourceX = px;
                int sourceY = py;
                int sourceZ = pz;
                if (ctx.sources() != null && !ctx.sources().isEmpty()) {
                    int[] src = ctx.sources().get(0);
                    if (src != null && src.length >= 3) {
                        sourceX = src[0];
                        sourceY = src[1];
                        sourceZ = src[2];
                    }
                }
                newState = isSourceInFrontOfDoor(mainPos, doorYaw, sourceX, sourceY, sourceZ)
                    ? DoorState.OPENED_OUT
                    : DoorState.OPENED_IN;
            }
        } else {
            newState = DoorState.CLOSED;
        }
        BlockType resultType = activateDoor(world, mainBlockType, mainPos, currentState, newState);
        if (resultType == null) return;

        // Double door: activate the other half (same logic as DoorInteraction.checkForDoubleDoor)
        DoorState stateDoubleDoor = getOppositeDoorState(currentState);
        DoorInfo doubleDoor = getDoubleDoor(world, mainPos, mainBlockType, rotation, stateDoubleDoor);
        if (doubleDoor != null) {
            DoorState stateForDoubleDoor = horizontal ? newState : getOppositeDoorState(newState);
            activateDoor(world, doubleDoor.blockType(), doubleDoor.blockPosition(), doubleDoor.doorState(), stateForDoubleDoor);
        }

        ActivationExecutor.playBlockInteractionSound(world, mainX, mainY, mainZ, resultType);
        ActivationExecutor.playEffects(world, mainX, mainY, mainZ, getEffects());
        ActivationExecutor.sendSignals(ctx);
    }
}
