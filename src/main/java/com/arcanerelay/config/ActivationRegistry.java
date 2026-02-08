package com.arcanerelay.config;

import com.hypixel.hytale.assetstore.AssetMap;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class ActivationRegistry {

    public static final String DEFAULT_ACTIVATION_ID = "use_block";

    private final ActivationBindingRegistry bindingRegistry;
    private AssetMap<String, Activation> assetMap;
    private boolean assetsCached;

    public ActivationRegistry(@Nonnull ActivationBindingRegistry bindingRegistry) {
        this.bindingRegistry = bindingRegistry;
    }

    @SuppressWarnings("unchecked")
    public static void registerAssetStore() {
        HytaleAssetStore.Builder<String, Activation, DefaultAssetMap<String, Activation>> b =
            (HytaleAssetStore.Builder<String, Activation, DefaultAssetMap<String, Activation>>)
                (Object) HytaleAssetStore.builder(Activation.class, new DefaultAssetMap<String, Activation>());
        AssetRegistry.register(
            b.setPath("Item/Activations")
                .setCodec(Activation.CODEC)
                .setKeyFunction(Activation::getId)
                .loadsAfter(SoundEvent.class)
                .loadsAfter(BlockType.class)
                .build()
        );
    }

    public void onAssetsLoaded() {
        AssetStore<String, Activation, ? extends AssetMap<String, Activation>> store =
            AssetRegistry.getAssetStore(Activation.class);
        if (store != null) {
            this.assetMap = store.getAssetMap();
        }
        this.assetsCached = true;
    }

    @Nonnull
    public ActivationBindingRegistry getBindingRegistry() {
        return bindingRegistry;
    }

    @Nullable
    public Activation getActivation(@Nonnull String activationId) {
        if (DEFAULT_ACTIVATION_ID.equals(activationId)) {
            return null;
        }
        if (assetMap == null) {
            return null;
        }
        return assetMap.getAsset(activationId);
    }

    @Nonnull
    public String getActivationId(@Nonnull String blockTypeKey) {
        return bindingRegistry.getActivationId(blockTypeKey);
    }

    @Nullable
    public Activation getActivationForBlock(@Nonnull String blockTypeKey) {
        String id = bindingRegistry.getActivationId(blockTypeKey);
        return getActivation(id);
    }

    public boolean isAssetsCached() {
        return assetsCached;
    }
}
