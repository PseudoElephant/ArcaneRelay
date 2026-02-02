package com.arcanerelay.components;

import com.arcanerelay.ArcaneRelayPlugin;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.codec.Vector3iArrayCodec;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
        .build();

    private HashSet<Vector3i> outputPositions = new HashSet<>();
    private HashSet<Vector3i> chargedSources = new HashSet<>();

    public static ComponentType<ChunkStore, ArcaneTriggerBlock> getComponentType() {
        return ArcaneRelayPlugin.get().getArcaneTriggerBlockComponentType();
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

    /** Positions that have contributed a charge this cycle (for arcane discharge unique-source logic). */
    @Nonnull
    public Set<Vector3i> getChargedSources() {
        return Collections.unmodifiableSet(chargedSources);
    }

    /** Number of unique sources that have charged this block this cycle. */
    public int getChargeCount() {
        return chargedSources.size();
    }

    /**
     * Records a charge from the given source position.
     *
     * @param sourceX source block X
     * @param sourceY source block Y
     * @param sourceZ source block Z
     * @return true if this was a new (unique) source and the charge should advance state; false if duplicate
     */
    public boolean addChargeFrom(int sourceX, int sourceY, int sourceZ) {
        Vector3i pos = new Vector3i(sourceX, sourceY, sourceZ);
        return chargedSources.add(pos);
    }

    /** Clears all charged sources (e.g. when discharge block resets to Off). */
    public void clearCharges() {
        chargedSources.clear();
    }

    @Nonnull
    @Override
    public Component<ChunkStore> clone() {
        ArcaneTriggerBlock clone = new ArcaneTriggerBlock();
        for (Vector3i p : outputPositions) {
            clone.outputPositions.add(p.clone());
        }
        for (Vector3i p : chargedSources) {
            clone.chargedSources.add(p.clone());
        }
        return clone;
    }
}
