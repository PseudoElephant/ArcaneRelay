package com.arcanerelay.components;

import com.arcanerelay.ArcaneRelayPlugin;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.codecs.simple.BooleanCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.codec.Vector3iArrayCodec;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import javax.annotation.Nonnull;

/** Block component for Arcane Trigger: stores multiple output positions to activate, and tracks charge sources. */
public class ArcaneTriggerBlock implements Component<ChunkStore> {

    public static final BuilderCodec<ArcaneTriggerBlock> CODEC = BuilderCodec.builder(ArcaneTriggerBlock.class, ArcaneTriggerBlock::new)
        .append(
            new KeyedCodec<>("OutputPositions", new ArrayCodec<>(new Vector3iArrayCodec(), Vector3i[]::new)),
            (b, positions) -> {
                b.outputPositions = new HashSet<>();
                Collections.addAll(b.outputPositions, positions);
            },
            b -> b.getOutputPositions().toArray(Vector3i[]::new)
        )
        .add()
        .append(
            new KeyedCodec<>("UseRelativeOutputs", new BooleanCodec(), false),
            (b, useRelative) -> b.useRelativeOutputs = useRelative,
            b -> b.useRelativeOutputs
        )
        .add()
        .build();

    private HashSet<Vector3i> outputPositions = new HashSet<>();
    private boolean useRelativeOutputs = false;

    public static ComponentType<ChunkStore, ArcaneTriggerBlock> getComponentType() {
        return ArcaneRelayPlugin.get().getArcaneTriggerBlockComponentType();
    }

    /** Returns true if this trigger is using relative output positions. */
    public boolean isUsingRelativeOutput() {
        return useRelativeOutputs;
    }

    /** Sets whether this trigger should use relative output positions. */
    public void setUsingRelativeOutput(boolean useRelative) {
        this.useRelativeOutputs = useRelative;
    }

    public void moveOutputPositions(Vector3i direction) {
        HashSet<Vector3i> newPositions = new HashSet<>();
        for (Vector3i pos : outputPositions) {
            newPositions.add(new Vector3i(pos.x + direction.x, pos.y + direction.y, pos.z + direction.z));
        }
        
        this.outputPositions = newPositions;
    }

    /** Positions this trigger will attempt to activate when triggered. */
    @Nonnull
    public List<Vector3i> getOutputPositions() {
        return Collections.unmodifiableList(new ArrayList<>(outputPositions));
    }

    /** Add an output position (e.g. when using ArcaneRelay tool to connect). */
    public void addOutputPosition(@Nonnull Vector3i position) {
        outputPositions.add(position.clone());
    }

    /** Remove all output positions. */
    public void clearOutputPositions() {
        outputPositions.clear();
    }

    /** Remove output position by coordinates. Returns true if removed. */
    public boolean removeOutputPosition(int x, int y, int z) {
        for (Vector3i p : outputPositions) {
            if (p.getX() == x && p.getY() == y && p.getZ() == z) {
                outputPositions.remove(p);
                return true;
            }
        }
        return false;
    }

    public boolean hasOutputPositions() {
        return !outputPositions.isEmpty();
    }

    @Nonnull
    @Override
    public Component<ChunkStore> clone() {
        ArcaneTriggerBlock clone = new ArcaneTriggerBlock();
        for (Vector3i p : outputPositions) {
            clone.outputPositions.add(p.clone());
        }

        clone.useRelativeOutputs = this.useRelativeOutputs;
        
        return clone;
    }
}
