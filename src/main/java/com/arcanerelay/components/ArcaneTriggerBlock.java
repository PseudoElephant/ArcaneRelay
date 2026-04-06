package com.arcanerelay.components;

import com.arcanerelay.ArcaneRelayPlugin;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
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
            new KeyedCodec<>("OutputMode", new EnumCodec<>(OutputMode.class), false),
            (b, mode) -> b.outputMode = mode,
            b -> b.outputMode
        )
        .add()
        .build();

    private HashSet<Vector3i> outputPositions = new HashSet<>();
    private OutputMode outputMode = OutputMode.ABSOLUTE;

    public static ComponentType<ChunkStore, ArcaneTriggerBlock> getComponentType() {
        return ArcaneRelayPlugin.get().getArcaneTriggerBlockComponentType();
    }

    /** Returns true if this trigger is using relative output positions. */
    public boolean isUsingRelativeOutput() {
        return outputMode == OutputMode.RELATIVE;
    }

    /** Sets whether this trigger should use relative output positions. */
    public void setUsingRelativeOutput(boolean useRelative) {
        this.outputMode = useRelative ? OutputMode.RELATIVE : OutputMode.ABSOLUTE;
    }

    public void changeOutputMode(OutputMode mode, @Nonnull Vector3i currentGlobalPosition) {
        switch (mode) {
            case RELATIVE:
                if (isUsingRelativeOutput()) return;
                convertOutputPositionsToRelative(currentGlobalPosition);
                this.outputMode = OutputMode.RELATIVE;

                break;
            case ABSOLUTE:
                if (!isUsingRelativeOutput()) return;
                convertOutputPositionsToAbsolute(currentGlobalPosition);
                this.outputMode = OutputMode.ABSOLUTE;

                break;
        }
    }

    private void convertOutputPositionsToRelative(@Nonnull Vector3i currentGlobalPosition) {
        if (isUsingRelativeOutput()) return;

        HashSet<Vector3i> relativePositions = new HashSet<>();
        for (Vector3i pos : outputPositions) {
            relativePositions.add(pos.clone().subtract(currentGlobalPosition.clone()));
        }

        outputPositions = relativePositions;
    }

    private void convertOutputPositionsToAbsolute(@Nonnull Vector3i currentGlobalPosition) {
        if (!isUsingRelativeOutput()) return;

        HashSet<Vector3i> absolutePositions = new HashSet<>();
        for (Vector3i pos : outputPositions) {
            absolutePositions.add(pos.clone().add(currentGlobalPosition.clone()));
        }

        outputPositions = absolutePositions;
    }

    /** Positions this trigger will attempt to activate when triggered. */
    @Nonnull
    public List<Vector3i> getOutputPositions() {
        return Collections.unmodifiableList(new ArrayList<>(outputPositions));
    }

    public List<Vector3i> getGlobalOutputPositions(@Nonnull Vector3i currentGlobalPosition) {
        List<Vector3i> relativePositions = new ArrayList<>();
        for (Vector3i pos : outputPositions) {
            if (isUsingRelativeOutput()) {
                relativePositions.add(pos.clone().add(currentGlobalPosition.clone()));
            } else {
                relativePositions.add(pos.clone());
            }
        }
        
        return Collections.unmodifiableList(new ArrayList<>(relativePositions));
    }

    /** Add an output position (e.g. when using ArcaneRelay tool to connect). */
    public void addOutputPosition(@Nonnull Vector3i position, @Nonnull Vector3i currentGlobalPosition) {
        if (isUsingRelativeOutput()) {
            outputPositions.add(position.clone().subtract(currentGlobalPosition.clone()));
        } else {
            outputPositions.add(position.clone());
        }
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

        clone.outputMode = this.outputMode;
        
        return clone;
    }

    public enum OutputMode {
        RELATIVE,
        ABSOLUTE
    }
}
