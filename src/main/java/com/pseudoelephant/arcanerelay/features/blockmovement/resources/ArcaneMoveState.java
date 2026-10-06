package com.pseudoelephant.arcanerelay.features.blockmovement.resources;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import org.joml.Vector3i;

public class ArcaneMoveState implements Resource<ChunkStore> {
    private static ResourceType<ChunkStore, ArcaneMoveState> resourceType;
    private ConcurrentHashMap<Vector3i, MoveEntry> moveEntries;

    public ArcaneMoveState() {
        this.moveEntries = new ConcurrentHashMap<>();
    }

    public static void setResourceType(ResourceType<ChunkStore, ArcaneMoveState> resourceType) {
        ArcaneMoveState.resourceType = resourceType;
    }

    public static ResourceType<ChunkStore, ArcaneMoveState> getResourceType() {
        return ArcaneMoveState.resourceType;
    }

    public void addMoveEntry(Vector3i blockPosition, Vector3i moveDirection, BlockType blockType, int blockId, int blockRotation, int filler, int settings, Holder<ChunkStore> componentHolder) {
        this.moveEntries.compute(blockPosition, (key, existingEntry) -> {
            if (existingEntry != null) {
                existingEntry.updateDirection(moveDirection);
                return existingEntry;
            }
            return new MoveEntry(blockPosition, moveDirection, blockType, blockId, blockRotation, filler, settings, componentHolder);
        });
    }

    public HashMap<Vector3i, MoveEntry> getMoveEntries() {
        return new HashMap<>(this.moveEntries); // This could return entires that are being added by other threads while this is being copied.
    }

    public void clear() {
        this.moveEntries.clear();
    }

    @Override
    public Resource<ChunkStore> clone() {
        return new ArcaneMoveState();
    }

    public class MoveEntry {
        public final Vector3i blockPosition;
        public final Vector3i moveDirection;
        public final BlockType blockType;
        public final int blockId;
        public final int blockRotation;
        public final int blockFiller;
        public final int blockSettings;
        public final Holder<ChunkStore> componentHolder;

        public MoveEntry(Vector3i blockPosition, Vector3i moveDirection, BlockType blockType, int blockId, int blockRotation, int filler, int settings, Holder<ChunkStore> componentHolder) {
            this.blockPosition = blockPosition;
            this.moveDirection = moveDirection;
            this.blockType = blockType;
            this.blockId = blockId;
            this.blockRotation = blockRotation;
            this.blockFiller = filler;
            this.blockSettings = settings;
            this.componentHolder = componentHolder;
        }

        public void updateDirection(Vector3i moveDirection) {
            this.moveDirection.x = Math.clamp(this.moveDirection.x + moveDirection.x, -1, 1);
            this.moveDirection.y = Math.clamp(this.moveDirection.y + moveDirection.y, -1, 1);
            this.moveDirection.z = Math.clamp(this.moveDirection.z + moveDirection.z, -1, 1);
        }
    }
}
