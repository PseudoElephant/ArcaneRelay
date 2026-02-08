package com.arcanerelay.config.types;

import com.arcanerelay.config.Activation;
import com.arcanerelay.config.ActivationContext;
import com.arcanerelay.core.activation.ActivationExecutor;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nonnull;

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
