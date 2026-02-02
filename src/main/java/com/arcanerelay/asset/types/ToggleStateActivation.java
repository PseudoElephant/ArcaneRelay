package com.arcanerelay.asset.types;

import com.arcanerelay.asset.Activation;
import com.arcanerelay.asset.ActivationContext;
import com.arcanerelay.asset.ActivationEffects;
import com.arcanerelay.asset.ActivationExecutor;
import com.hypixel.hytale.math.vector.Vector3i;

import javax.annotation.Nonnull;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nullable;

/**
 * Activation that toggles a block between two states (e.g. On/Off, Open/Close).
 */
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
    public void execute(@Nonnull ActivationContext ctx) {
        String state = ctx.blockType().getStateForBlock(ctx.blockType());
        if (state == null || state.isEmpty() || "null".equals(state)) {
            state = onState;
        }

        boolean isCurrentlyOff = offState.equalsIgnoreCase(state);
        String newState = isCurrentlyOff ? onState : offState;

        ctx.world().setBlockInteractionState(new Vector3i(ctx.blockX(), ctx.blockY(), ctx.blockZ()), ctx.blockType(), newState);

        var newBlockType = ctx.blockType().getBlockForState(newState);
        if (newBlockType != null) {
            ActivationExecutor.playBlockInteractionSound(ctx.world(), ctx.blockX(), ctx.blockY(), ctx.blockZ(), newBlockType);
        }

        ActivationEffects effects = isCurrentlyOff ? onEffects : offEffects;
        if (effects == null) effects = getEffects();
        ActivationExecutor.playEffects(ctx.world(), ctx.blockX(), ctx.blockY(), ctx.blockZ(), effects);
        ActivationExecutor.playBlockInteractionSound(ctx.world(), ctx.blockX(), ctx.blockY(), ctx.blockZ(), ctx.blockType());
        if (shouldSendSignal(state, newState)) {
            ActivationExecutor.sendSignals(ctx);
        }
    }

    private boolean shouldSendSignal(String currentState, String newState) {
        // if current signal is on and we are toggling to off, send signal
        if (currentState.equalsIgnoreCase(onState) && newState.equalsIgnoreCase(offState) && sendSignalWhen.equalsIgnoreCase("Off")) {
            return true;
        }
        // if current signal is off and we are toggling to on, send signal
        if (currentState.equalsIgnoreCase(offState) && newState.equalsIgnoreCase(onState) && sendSignalWhen.equalsIgnoreCase("On")) {
            return true;
        }
        if (sendSignalWhen.equalsIgnoreCase("Both")) {
            return true;
        }

        return false;
    }
}
