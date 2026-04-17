package com.arcanerelay.config.types;

import com.arcanerelay.ArcaneRelayPlugin;
import com.arcanerelay.components.ArcaneSection;
import com.arcanerelay.config.Activation;
import com.arcanerelay.config.ActivationEffects;
import com.arcanerelay.core.activation.ActivationExecutor;
import com.arcanerelay.core.activation.ArcaneActivationAccessor;
import com.arcanerelay.core.activation.ArcaneCachedAccessor;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.arcanerelay.core.activation.ChunkStoreCommandBufferLike;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public class ToggleStateActivation extends Activation {
    public static final BuilderCodec<ToggleStateActivation> CODEC = BuilderCodec.builder(
            ToggleStateActivation.class,
            ToggleStateActivation::new,
            Activation.ABSTRACT_CODEC
        )
        .documentation("Toggles the block between two states (e.g. On/Off).")
        .appendInherited(
            new KeyedCodec<>("OnState", Codec.STRING),
            (a, s) -> a.onState = s,
            a -> a.onState,
            (a, p) -> a.onState = p.onState
        )
        .documentation("State name when toggling to 'on' (default: On).")
        .add()
        .appendInherited(
            new KeyedCodec<>("OffState", Codec.STRING),
            (a, s) -> a.offState = s,
            a -> a.offState,
            (a, p) -> a.offState = p.offState
        )
        .documentation("State name when toggling to 'off' (default: Off).")
        .add()
        .<ActivationEffects>appendInherited(
            new KeyedCodec<>("OnEffects", ActivationEffects.CODEC),
            (a, o) -> a.onEffects = o,
            a -> a.onEffects,
            (a, p) -> a.onEffects = p.onEffects
        )
        .documentation("Optional effects when toggling to on state.")
        .add()
        .<ActivationEffects>appendInherited(
            new KeyedCodec<>("OffEffects", ActivationEffects.CODEC),
            (a, o) -> a.offEffects = o,
            a -> a.offEffects,
            (a, p) -> a.offEffects = p.offEffects
        )
        .documentation("Optional effects when toggling to off state.")
        .add()
        .appendInherited(
            new KeyedCodec<>("SendSignalWhen", Codec.STRING),
            (a, s) -> a.sendSignalWhen = s,
            a -> a.sendSignalWhen,
            (a, p) -> a.sendSignalWhen = p.sendSignalWhen
        )
        .documentation("When to send signals: Off (only when transitioning to off), On (only when transitioning to on), Both (always). Default: Off.")
        .add()
        .build();

    private String onState = "On";
    private String offState = "Off";
    private String sendSignalWhen = "Off";
    @Nullable
    private ActivationEffects onEffects;
    @Nullable
    private ActivationEffects offEffects;

    public ToggleStateActivation() {
    }

    public String getOnState() {
        return onState;
    }

    public void setOnState(String onState) {
        this.onState = onState;
    }

    public String getOffState() {
        return offState;
    }

    public void setOffState(String offState) {
        this.offState = offState;
    }

    @Nullable
    public ActivationEffects getOnEffects() {
        return onEffects;
    }

    public void setOnEffects(@Nullable ActivationEffects onEffects) {
        this.onEffects = onEffects;
    }

    @Nullable
    public ActivationEffects getOffEffects() {
        return offEffects;
    }

    public void setOffEffects(@Nullable ActivationEffects offEffects) {
        this.offEffects = offEffects;
    }

    @Override
    public ArcaneSection.BlockTickStrategy execute(
        @Nonnull ArcaneCachedAccessor accessor,
        @Nullable Ref<ChunkStore> sectionRef,
        @Nullable Ref<ChunkStore> blockRef,
        int worldX, int worldY, int worldZ,
        @Nonnull List<int[]> sources
    ) {
        ChunkStoreCommandBufferLike commandBuffer = accessor.getCommandBuffer();

        BlockType blockType = accessor.getBlockType(worldX, worldY, worldZ);
        if (blockType == null) {
            return ArcaneSection.BlockTickStrategy.PROCESSED;
        }

        String state = blockType.getStateForBlock(blockType);
        if (state == null || state.isEmpty() || "null".equals(state)) {
            state = onState;
        }

        boolean isCurrentlyOff = offState.equalsIgnoreCase(state);
        String newState = isCurrentlyOff ? onState : offState;

        // This ensures we are enqueing the block interaction state change on the correct thread
        commandBuffer.run((@Nonnull Store<ChunkStore> store) -> {
            World world = store.getExternalData().getWorld();
            world.setBlockInteractionState(new Vector3i(worldX, worldY, worldZ), blockType, newState);
        });
    
        var newBlockType = blockType.getBlockForState(newState);
        if (newBlockType != null) {
            commandBuffer.run((@Nonnull Store<ChunkStore> store) -> {
                World world = store.getExternalData().getWorld();
                ActivationExecutor.playBlockInteractionSound(world, worldX, worldY, worldZ, newBlockType);
            });
        }

        final ActivationEffects finalEffects = isCurrentlyOff ? onEffects : offEffects;

        if (finalEffects != null) {
        commandBuffer.run((@Nonnull Store<ChunkStore> store) -> {
            World world = store.getExternalData().getWorld();

            ActivationEffects effects = finalEffects != null ? finalEffects : getEffects();
            if (effects != null) {
                ActivationExecutor.playEffects(world, worldX, worldY, worldZ, effects);
            }
            });
        }


        if (shouldSendSignal(state, newState)) {
            // MIGHT need to make adjustments here to ensure we are sending the signals to the correct blocks
            commandBuffer.run((@Nonnull Store<ChunkStore> store) -> {
                ActivationExecutor.sendSignals(store, blockRef, worldX, worldY, worldZ);
            });
        }

        return ArcaneSection.BlockTickStrategy.PROCESSED;
    }

    private boolean shouldSendSignal(String currentState, String newState) {
        String when = sendSignalWhen != null ? sendSignalWhen.toLowerCase() : "off";
        return switch (when) {
            case "on" -> currentState.equalsIgnoreCase(offState) && newState.equalsIgnoreCase(onState);
            case "off" -> currentState.equalsIgnoreCase(onState) && newState.equalsIgnoreCase(offState);
            case "both" -> true;
            default -> currentState.equalsIgnoreCase(onState) && newState.equalsIgnoreCase(offState);
        };
    }
}
