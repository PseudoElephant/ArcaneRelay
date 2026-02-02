package com.arcanerelay.asset.types;

import com.arcanerelay.ArcaneRelayPlugin;
import com.arcanerelay.asset.Activation;
import com.arcanerelay.asset.ActivationContext;
import com.arcanerelay.asset.ActivationExecutor;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;

import javax.annotation.Nonnull;

/**
 * Activation that runs a sequence of other activations in order.
 * Used e.g. for the pusher: ToggleState (extend) then MoveBlock (push).
 */
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
