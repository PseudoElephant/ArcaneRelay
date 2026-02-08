package com.arcanerelay.config;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.codec.AssetCodecMapCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public abstract class Activation implements JsonAssetWithMap<String, DefaultAssetMap<String, Activation>> {
    public static final AssetCodecMapCodec<String, Activation> CODEC = new AssetCodecMapCodec<>(
        Codec.STRING,
        (t, k) -> t.id = k,
        t -> t.id,
        (t, data) -> t.data = data,
        t -> t.data
    );

    public static final BuilderCodec<Activation> ABSTRACT_CODEC = BuilderCodec.abstractBuilder(Activation.class)
        .<ActivationEffects>appendInherited(
            new KeyedCodec<>("Effects", ActivationEffects.CODEC),
            (a, o) -> a.effects = o,
            a -> a.effects,
            (a, parent) -> a.effects = parent.effects
        )
        .documentation("Effects to play when the activation runs (e.g. sound at block position).")
        .add()
        .build();

    protected String id;
    protected AssetExtraInfo.Data data;
    @Nullable
    protected ActivationEffects effects;

    @Nonnull
    @Override
    public String getId() {
        return id;
    }

    @Nullable
    public ActivationEffects getEffects() {
        return effects;
    }

    public void setEffects(@Nullable ActivationEffects effects) {
        this.effects = effects;
    }

    public abstract void execute(@Nonnull ActivationContext ctx);
}
