package com.arcanerelay.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nullable;

public final class ActivationEffects {
    public static final BuilderCodec<ActivationEffects> CODEC = BuilderCodec.builder(ActivationEffects.class, ActivationEffects::new)
        .appendInherited(
            new KeyedCodec<>("WorldSoundEventId", Codec.STRING),
            (e, s) -> e.worldSoundEventId = s,
            e -> e.worldSoundEventId,
            (e, p) -> e.worldSoundEventId = p.worldSoundEventId
        )
        .documentation("3D sound to play at the block position when the activation runs.")
        .add()
        .build();

    @Nullable
    private String worldSoundEventId;

    public ActivationEffects() {
    }

    @Nullable
    public String getWorldSoundEventId() {
        return worldSoundEventId;
    }

    public void setWorldSoundEventId(@Nullable String worldSoundEventId) {
        this.worldSoundEventId = worldSoundEventId;
    }
}
