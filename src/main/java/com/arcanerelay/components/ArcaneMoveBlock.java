package com.arcanerelay.components;

import javax.annotation.Nonnull;

import com.arcanerelay.ArcaneRelayPlugin;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public class ArcaneMoveBlock implements Component<ChunkStore> {
        public static final BuilderCodec<ArcaneMoveBlock> CODEC = BuilderCodec.builder(ArcaneMoveBlock.class, ArcaneMoveBlock::new)
        .build();   

    public Vector3i moveDirection;

    public ArcaneMoveBlock() {
        this.moveDirection = new Vector3i();
    }
    
    @Nonnull
    @Override
    public Component<ChunkStore> clone() {
        ArcaneMoveBlock clone = new ArcaneMoveBlock();
        clone.moveDirection = this.moveDirection;
        return clone;
    }

    public void addDirection(Vector3i direction) {
        this.moveDirection.x = Math.clamp(this.moveDirection.x + direction.x, -1, 1);
        this.moveDirection.y = Math.clamp(this.moveDirection.y + direction.y, -1, 1);
        this.moveDirection.z = Math.clamp(this.moveDirection.z + direction.z, -1, 1);
    }
    
    public Vector3i getMoveDirection() {
      return this.moveDirection;
    }

    public static ComponentType<ChunkStore, ArcaneMoveBlock> getComponentType() {
        return ArcaneRelayPlugin.get().getArcaneMoveBlockComponentType();
    }
}