package com.arcanerelay.features.activation;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.arcanerelay.core.activation.ArcaneActivationAccessor;
import com.arcanerelay.core.activation.ArcaneCachedAccessor;
import com.arcanerelay.features.signal.components.ArcaneSection;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.AssetMap;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.codec.AssetCodecMapCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;

public abstract class Activation implements JsonAssetWithMap<String, DefaultAssetMap<String, Activation>> {

    public static final String DEFAULT_ACTIVATION_ID = "use_block";

    @Nonnull
    public static final AssetCodecMapCodec<String, Activation> CODEC = new AssetCodecMapCodec<>(
        Codec.STRING,
        (t, k) -> t.id = k,
        t -> t.id,
        (t, data) -> t.data = data,
        t -> t.data
    );

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

    public Activation() {
    }
     
    public Activation(String id) {
        this.id = id;
    }
    
    protected String id;
    protected AssetExtraInfo.Data data;
    @Nullable
    protected ActivationEffects effects;
    private static AssetStore<String, Activation, DefaultAssetMap<String, Activation>> ASSET_STORE;

    public static AssetStore<String, Activation, DefaultAssetMap<String, Activation>> getAssetStore() {
        if (ASSET_STORE == null) {
           ASSET_STORE = AssetRegistry.getAssetStore(Activation.class);
        }
  
        return ASSET_STORE;
     }

    public static DefaultAssetMap<String, Activation> getAssetMap() {
        return (DefaultAssetMap<String, Activation>)getAssetStore().getAssetMap();
     }

    /** Returns the Activation for an id, or null if "use_block" or not found. */
    @Nullable
    public static Activation getActivation(@Nonnull String id) {
        if (DEFAULT_ACTIVATION_ID.equals(id)) return null;
        AssetStore<String, Activation, ?> store = getAssetStore();
        if (store == null) return null;
        AssetMap<String, Activation> map = store.getAssetMap();
        if (map == null) return null;
        return map.getAsset(id);
    }

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

    /**
     * Execute this activation for a ticking block. Block-specific data is passed as parameters;
     * use the accessor for section/chunk lookups ({@link ArcaneActivationAccessor#getBlockSection},
     * {@link ArcaneActivationAccessor#getWorldChunk}, {@link ArcaneActivationAccessor#getBlockType}).
     */
    public abstract ArcaneSection.BlockTickStrategy execute(
        @Nonnull ArcaneCachedAccessor accessor,
        @Nullable Ref<ChunkStore> sectionRef,
        @Nullable Ref<ChunkStore> blockRef,
        int worldX, int worldY, int worldZ,
        @Nonnull List<int[]> sources
    );
}
