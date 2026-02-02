package com.arcanerelay.asset.types;

import com.arcanerelay.ArcaneRelayPlugin;
import com.arcanerelay.asset.Activation;
import com.arcanerelay.asset.ActivationContext;
import com.arcanerelay.asset.ActivationExecutor;
import com.arcanerelay.components.ArcaneTriggerBlock;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Activation that cycles through charge states and sends signals when transitioning
 * from fully charged to off (discharge), not when reaching max charge. For blocks like Pseudo_Arcane_Discharge.
 */
public class ArcaneDischargeActivation extends Activation {

    public static final BuilderCodec<ArcaneDischargeActivation> CODEC = BuilderCodec.builder(
            ArcaneDischargeActivation.class,
            ArcaneDischargeActivation::new,
            Activation.ABSTRACT_CODEC
        )
        .documentation("Cycles charge states; sends signals when transitioning from fully charged to off.")
        .<Map<String, String>>appendInherited(
            new KeyedCodec<>("Changes", new MapCodec<>(Codec.STRING, HashMap::new)),
            (a, m) -> a.changes = m,
            a -> a.changes,
            (a, p) -> a.changes = p.changes
        )
        .documentation("State transition map: current state -> next state (e.g. Off->One, One->Two, ..., On->Off).")
        .add()
        .appendInherited(
            new KeyedCodec<>("MaxChargeState", Codec.STRING),
            (a, s) -> a.maxChargeState = s,
            a -> a.maxChargeState,
            (a, p) -> a.maxChargeState = p.maxChargeState
        )
        .documentation("Exact state that triggers signal (default: On). Ignored if MaxChargeStateSuffix is set.")
        .add()
        .appendInherited(
            new KeyedCodec<>("MaxChargeStateSuffix", Codec.STRING),
            (a, s) -> a.maxChargeStateSuffix = s,
            a -> a.maxChargeStateSuffix,
            (a, p) -> a.maxChargeStateSuffix = p.maxChargeStateSuffix
        )
        .documentation("If set (e.g. _All_On), any state ending with this suffix is considered max charge.")
        .add()
        .build();

    private Map<String, String> changes;
    private String maxChargeState = "On";
    @Nullable
    private String maxChargeStateSuffix;

    public ArcaneDischargeActivation() {
    }

    @Nullable
    public Map<String, String> getChanges() {
        return changes;
    }

    public void setChanges(@Nullable Map<String, String> changes) {
        this.changes = changes;
    }

    public String getMaxChargeState() {
        return maxChargeState;
    }

    public void setMaxChargeState(String maxChargeState) {
        this.maxChargeState = maxChargeState;
    }

    @Nullable
    public String getMaxChargeStateSuffix() {
        return maxChargeStateSuffix;
    }

    public void setMaxChargeStateSuffix(@Nullable String maxChargeStateSuffix) {
        this.maxChargeStateSuffix = maxChargeStateSuffix;
    }

    private boolean isMaxChargeState(String newState) {
        if (maxChargeStateSuffix != null && !maxChargeStateSuffix.isEmpty()) {
            return newState != null && newState.endsWith(maxChargeStateSuffix);
        }
        return maxChargeState != null && maxChargeState.equals(newState);
    }

    @Override
    public void execute(@Nonnull ActivationContext ctx) {
        var blockRef = ctx.chunk().getBlockComponentEntity(ctx.blockX(), ctx.blockY(), ctx.blockZ());
        if (blockRef == null) {
            blockRef = BlockModule.ensureBlockEntity(ctx.chunk(), ctx.blockX(), ctx.blockY(), ctx.blockZ());
        }
        if (blockRef == null) return;

        ArcaneTriggerBlock trigger = ctx.store().getComponent(blockRef, ArcaneRelayPlugin.get().getArcaneTriggerBlockComponentType());
        if (trigger == null) {
            trigger = new ArcaneTriggerBlock();
            ctx.store().putComponent(blockRef, ArcaneRelayPlugin.get().getArcaneTriggerBlockComponentType(), trigger);
        }

        int newUniqueSources = 0;
        for (int[] src : ctx.sources()) {
            if (trigger.addChargeFrom(src[0], src[1], src[2])) newUniqueSources++;
        }
        if (newUniqueSources == 0) return;

        Map<String, String> changes = getChanges();
        if (changes == null || changes.isEmpty()) return;

        String currentStateStr = ctx.blockType().getStateForBlock(ctx.blockType());
        if (currentStateStr == null) currentStateStr = "default";

        String newState = changes.get(currentStateStr);
        if (newState == null) newState = changes.get("default");
        if (newState == null) return;

        String suffix = getMaxChargeStateSuffix();
        boolean isResetState = "Off".equals(newState) || "default".equals(newState)
            || (suffix != null && newState != null && newState.endsWith("_All_Off"));
        if (isResetState) {
            trigger.clearCharges();
        }

        ctx.world().setBlockInteractionState(new Vector3i(ctx.blockX(), ctx.blockY(), ctx.blockZ()), ctx.blockType(), newState);

        var newBlockType = ctx.blockType().getBlockForState(newState);
        if (newBlockType != null) {
            ActivationExecutor.playBlockInteractionSound(ctx.world(), ctx.blockX(), ctx.blockY(), ctx.blockZ(), newBlockType);
        }
        ActivationExecutor.playEffects(ctx.world(), ctx.blockX(), ctx.blockY(), ctx.blockZ(), getEffects());

        // Send signal when transitioning from fully charged to off (discharge), not when reaching max charge
        if (isMaxChargeState(currentStateStr) && isResetState) {
            ActivationExecutor.sendSignals(ctx);
        }
    }
}
