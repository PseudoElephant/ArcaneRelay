package com.arcanerelay.asset.types;

import com.arcanerelay.asset.Activation;
import com.arcanerelay.asset.ActivationContext;
import com.arcanerelay.asset.ActivationExecutor;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nonnull;

/**
 * Activation that simply sends arcane signals to connected outputs.
 * No state change, no toggling - just propagates the signal.
 */
public class SendSignalActivation extends Activation {

    public static final BuilderCodec<SendSignalActivation> CODEC =
        BuilderCodec.builder(
            SendSignalActivation.class,
            SendSignalActivation::new,
            Activation.ABSTRACT_CODEC
        )
        .documentation("Sends arcane signals to connected output blocks. No state change.")
        .build();

    public SendSignalActivation() {
    }

    @Override
    public void execute(@Nonnull ActivationContext ctx) {
        ActivationExecutor.playEffects(ctx.world(), ctx.blockX(), ctx.blockY(), ctx.blockZ(), getEffects());
        ActivationExecutor.sendSignals(ctx);
    }
}
