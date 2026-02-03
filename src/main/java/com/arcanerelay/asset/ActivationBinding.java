package com.arcanerelay.asset;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Asset for a single activation binding: maps a block pattern to an activation id.
 * Loaded from Server/Item/ActivationBindings/*.json.
 * Files with id "default" set the default activation; others register pattern → activation.
 */
public final class ActivationBinding implements JsonAssetWithMap<String, DefaultAssetMap<String, ActivationBinding>> {

    public static final AssetBuilderCodec<String, ActivationBinding> CODEC = AssetBuilderCodec.builder(
            ActivationBinding.class,
            ActivationBinding::new,
            Codec.STRING,
            (b, id) -> b.id = id,
            b -> b.id,
            (b, data) -> b.data = data,
            b -> b.data
        )
        .append(new KeyedCodec<>("Pattern", Codec.STRING, false), (b, p) -> b.pattern = p, b -> b.pattern)
        .add()
        .append(new KeyedCodec<>("Activation", Codec.STRING, true), (b, a) -> b.activation = a, b -> b.activation)
        .add()
        .append(new KeyedCodec<>("Priority", Codec.INTEGER, false), (b, p) -> b.priority = p, b -> b.priority)
        .add()
        .build();

    private String id;
    @Nullable
    private String pattern;
    private String activation;
    private AssetExtraInfo.Data data;
    private int priority = 0;
    public ActivationBinding() {
    }

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

    /** True if this binding sets the default (id is "Default" and has no pattern). */
    public boolean isDefaultBinding() {
        return "Default".equals(id) && (pattern == null || pattern.isBlank());
    }

    /** Priority for registration order: higher value = registered first = checked first. Default 0. */
    public int getPriority() {
        return priority;
    }
}
