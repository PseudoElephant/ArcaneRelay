package com.arcanerelay.asset;

import com.hypixel.hytale.assetstore.AssetMap;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Registry for Activation assets. Uses an {@link ActivationBindingRegistry} for
 * block-type-to-activation id resolution.
 */
public final class ActivationRegistry {

    public static final String DEFAULT_ACTIVATION_ID = "use_block";

    private final ActivationBindingRegistry bindingRegistry;
    private AssetMap<String, Activation> assetMap;
    private boolean assetsCached;

    public ActivationRegistry(@Nonnull ActivationBindingRegistry bindingRegistry) {
        this.bindingRegistry = bindingRegistry;
    }

    /**
     * Registers the Activation asset store with the game. Call from plugin setup().
     */
    @SuppressWarnings("unchecked")
    public static void registerAssetStore(@Nonnull JavaPlugin plugin) {
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

    /**
     * Call after assets are loaded (e.g. on BootEvent) to cache the asset map for lookups.
     */
    public void onAssetsLoaded() {
        AssetStore<String, Activation, ? extends AssetMap<String, Activation>> store =
            AssetRegistry.getAssetStore(Activation.class);
        if (store != null) {
            this.assetMap = store.getAssetMap();
        }
        this.assetsCached = true;
    }

    /**
     * Returns the underlying binding registry for adding bindings from plugins or config.
     */
    @Nonnull
    public ActivationBindingRegistry getBindingRegistry() {
        return bindingRegistry;
    }

    /**
     * Returns the Activation asset for the given id, or null if id is "use_block" or unknown.
     */
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

    /**
     * Resolves the activation id for the given block type key (delegates to binding registry).
     */
    @Nonnull
    public String getActivationId(@Nonnull String blockTypeKey) {
        return bindingRegistry.getActivationId(blockTypeKey);
    }

    /**
     * Resolves the Activation for the given block type key.
     * Returns null if the resolved id is "use_block" or asset not found.
     */
    @Nullable
    public Activation getActivationForBlock(@Nonnull String blockTypeKey) {
        String id = bindingRegistry.getActivationId(blockTypeKey);
        return getActivation(id);
    }

    public boolean isAssetsCached() {
        return assetsCached;
    }
}
