package com.arcanerelay.config;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class ActivationBinding implements JsonAssetWithMap<String, DefaultAssetMap<String, ActivationBinding>> {
    @Nullable
    private String pattern;
    private int priority = 0;
    private String activation;
    private AssetExtraInfo.Data data;
    private String id;

    public static final AssetBuilderCodec<String, ActivationBinding> CODEC =
        AssetBuilderCodec.builder(ActivationBinding.class, ActivationBinding::new, Codec.STRING,
            (obj, id) -> obj.id = id, obj -> obj.id,
            (obj, data) -> obj.data = data, obj -> obj.data)
        .append(
            new KeyedCodec<>("Pattern", Codec.STRING, false),
            (obj, pattern) -> obj.pattern = pattern,
            obj -> obj.pattern)
        .add()
        .append(
            new KeyedCodec<>("Activation", Codec.STRING, true),
            (obj, activation) -> obj.activation = activation,
            obj -> obj.activation)
        .add()
        .append(
            new KeyedCodec<>("Priority", Codec.INTEGER, false),
            (obj, pattern) -> obj.priority = pattern,
            obj -> obj.priority)
        .add()
        .build();

    @Nonnull
    @Override
    public String getId() {
        return id;
    }

    @Nullable
    public String getPattern() {
        return pattern;
    }

    @Nonnull
    public String getActivation() {
        return activation;
    }

    public boolean isDefaultBinding() {
        return "Default".equals(id) && (pattern == null || pattern.isBlank());
    }

    public int getPriority() {
        return priority;
    }
}
