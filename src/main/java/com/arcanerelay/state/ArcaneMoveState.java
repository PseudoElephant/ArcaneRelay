package com.arcanerelay.state;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.math.vector.Vector3i;

public class ArcaneMoveState implements Resource<ChunkStore> {
    private ConcurrentHashMap<Vector3i, MoveEntry> moveEntries;

    public static ResourceType<ChunkStore, ArcaneMoveState> getResourceType() {
        return com.arcanerelay.ArcaneRelayPlugin.get().getArcaneMoveStateResourceType();
    }

    public ArcaneMoveState() {
        this.moveEntries = new ConcurrentHashMap<>();
    }

    public void addMoveEntry(Vector3i blockPosition, Vector3i moveDirection, BlockType blockType, int blockId, int blockRotation, int filler, int settings) {
        synchronized (this.moveEntries) {
        if (this.moveEntries.containsKey(blockPosition)) {
            this.moveEntries.get(blockPosition).updateDirection(moveDirection);
            return;
        }
        
            this.moveEntries.put(blockPosition, new MoveEntry(blockPosition, moveDirection, blockType, blockId, blockRotation, filler, settings));
        }
    }

    public HashMap<Vector3i, MoveEntry> getMoveEntries() {
        // clone the map to avoid concurrent modification exceptions
        synchronized (this.moveEntries) {
            HashMap<Vector3i, MoveEntry> clone = new HashMap<>(this.moveEntries);
            return clone;
        }
    }

    public void clear() {
        synchronized (this.moveEntries) {
            this.moveEntries.clear();
        }
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

        public MoveEntry(Vector3i blockPosition, Vector3i moveDirection, BlockType blockType, int blockId, int blockRotation, int filler, int settings) {
            this.blockPosition = blockPosition;
            this.moveDirection = moveDirection;
            this.blockType = blockType;
            this.blockId = blockId;
            this.blockRotation = blockRotation;
            this.blockFiller = filler;
            this.blockSettings = settings;
        }

        public void updateDirection(Vector3i moveDirection) {
            this.moveDirection.x = Math.clamp(this.moveDirection.x + moveDirection.x, -1, 1);
            this.moveDirection.y = Math.clamp(this.moveDirection.y + moveDirection.y, -1, 1);
            this.moveDirection.z = Math.clamp(this.moveDirection.z + moveDirection.z, -1, 1);
        }
    }
}
