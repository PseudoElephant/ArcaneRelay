package com.arcanerelay.config.types;

import com.arcanerelay.ArcaneRelayPlugin;
import com.arcanerelay.config.Activation;
import com.arcanerelay.config.ActivationContext;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;

import javax.annotation.Nonnull;

public class ChainActivation extends Activation {
    public static final BuilderCodec<ChainActivation> CODEC =
        BuilderCodec.builder(
            ChainActivation.class,
            ChainActivation::new,
            Activation.ABSTRACT_CODEC
        )
        .documentation("Runs multiple activations in sequence.")
        .appendInherited(
            new KeyedCodec<>("Activations", new ArrayCodec<>(Codec.STRING, String[]::new)),
            (a, ids) -> a.activationIds = ids,
            a -> a.activationIds,
            (a, p) -> a.activationIds = p.activationIds
        )
        .documentation("List of activation asset IDs to run in order.")
        .add()
        .build();

    private String[] activationIds = new String[0];

    public ChainActivation() {
    }

    public String[] getActivationIds() {
        return activationIds != null ? activationIds : new String[0];
    }

    public void setActivationIds(String[] activationIds) {
        this.activationIds = activationIds != null ? activationIds : new String[0];
    }

    @Override
    public void execute(@Nonnull ActivationContext ctx) {
        var registry = ArcaneRelayPlugin.get().getActivationRegistry();
        for (String id : getActivationIds()) {
            if (id == null || id.isEmpty()) continue;
            Activation activation = registry.getActivation(id);
            if (activation != null) {
                activation.execute(ctx);
            }
        }
    }
}
