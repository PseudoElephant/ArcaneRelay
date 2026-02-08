package com.arcanerelay.config;

import com.arcanerelay.api.BlockTypeMatcher;
import com.hypixel.hytale.assetstore.AssetMap;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ActivationBindingRegistry {

    public static final String DEFAULT_ACTIVATION_ID = "use_block";

    private final List<BindingEntry> bindings = new ArrayList<>();
    private String defaultActivationId = DEFAULT_ACTIVATION_ID;

    @SuppressWarnings("unchecked")
    public static void registerAssetStore() {
        HytaleAssetStore.Builder<String, ActivationBinding, DefaultAssetMap<String, ActivationBinding>> b = (HytaleAssetStore.Builder<String, ActivationBinding, DefaultAssetMap<String, ActivationBinding>>) (Object) HytaleAssetStore
            .builder(ActivationBinding.class, new DefaultAssetMap<>());
        AssetRegistry.register(
            b.setPath("Item/ActivationBindings")
                .setCodec(ActivationBinding.CODEC)
                .setKeyFunction(ActivationBinding::getId)
                .loadsAfter(Activation.class)
                .build());
    }

    public void onAssetsLoaded() {
        AssetStore<String, ActivationBinding, ? extends AssetMap<String, ActivationBinding>> store =
            AssetRegistry.getAssetStore(ActivationBinding.class);

        if (store == null) return;

        AssetMap<String, ActivationBinding> map = store.getAssetMap();
        if (map == null) return;

        var bindings = new ArrayList<>(map.getAssetMap().values());
        bindings.sort((a, b) -> {
            if (a.isDefaultBinding()) return 1;
            if (b.isDefaultBinding()) return -1;

            int cmp = Integer.compare(b.getPriority(), a.getPriority());

            if (cmp != 0) return cmp;

            return a.getId().compareTo(b.getId());
        });
        for (ActivationBinding binding : bindings) {
            if (binding.isDefaultBinding()) {
                this.setDefaultActivationId(binding.getActivation());
            } else if (binding.getPattern() != null && !binding.getPattern().isBlank()) {
                this.registerBinding(binding.getPattern(), binding.getActivation());
            }
        }
    }

    public void registerBinding(@Nonnull String pattern, @Nonnull String activationId) {
        bindings.add(new BindingEntry(matcherFromPattern(pattern), Objects.requireNonNull(activationId)));
    }

    public void registerBinding(@Nonnull BlockTypeMatcher matcher, @Nonnull String activationId) {
        bindings.add(new BindingEntry(Objects.requireNonNull(matcher), Objects.requireNonNull(activationId)));
    }

    public void registerBindingWithPriority(@Nonnull String pattern, @Nonnull String activationId) {
        bindings.addFirst(new BindingEntry(matcherFromPattern(pattern), Objects.requireNonNull(activationId)));
    }

    public void setDefaultActivationId(@Nonnull String activationId) {
        this.defaultActivationId = Objects.requireNonNull(activationId);
    }

    public static BlockTypeMatcher matcherFromPattern(@Nonnull String pattern) {
        String p = pattern.trim();
        if (p.isEmpty()) {
            return key -> false;
        }
        int colon = p.indexOf(':');
        if (colon <= 0 || colon == p.length() - 1) {
            return BlockTypeMatcher.contains(p);
        }
        String kind = p.substring(0, colon).trim().toLowerCase();
        String value = p.substring(colon + 1).trim();
        return switch (kind) {
            case "exact" -> key -> key.equals(value);
            case "contains" -> BlockTypeMatcher.contains(value);
            case "startswith" -> BlockTypeMatcher.startsWith(value);
            case "endswith" -> BlockTypeMatcher.endsWith(value);
            case "regex" -> BlockTypeMatcher.regex(value);
            default -> BlockTypeMatcher.contains(p);
        };
    }

    @Nonnull
    public String getActivationId(@Nonnull String blockTypeKey) {
        for (BindingEntry entry : bindings) {
            if (entry.matcher.matches(blockTypeKey)) {
                return entry.activationId;
            }
        }
        return defaultActivationId;
    }

    private static final class BindingEntry {
        final BlockTypeMatcher matcher;
        final String activationId;

        BindingEntry(BlockTypeMatcher matcher, String activationId) {
            this.matcher = matcher;
            this.activationId = activationId;
        }
    }
}
