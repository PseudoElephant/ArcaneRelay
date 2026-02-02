package com.arcanerelay.state;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Per-world resource holding the arcane proxy entity ref.
 * The proxy is a dedicated NPC used to run Use-block interactions without
 * triggering player animations.
 */
public final class ArcaneProxyResource implements Resource<EntityStore> {

    @Nullable
    private Ref<EntityStore> proxyRef;

    public ArcaneProxyResource() {
    }

    public static ResourceType<EntityStore, ArcaneProxyResource> getResourceType() {
        return com.arcanerelay.ArcaneRelayPlugin.get().getArcaneProxyResourceType();
    }

    @Nullable
    public Ref<EntityStore> getProxyRef() {
        return proxyRef != null && proxyRef.isValid() ? proxyRef : null;
    }

    public void setProxyRef(@Nullable Ref<EntityStore> ref) {
        this.proxyRef = ref;
    }

    @Nonnull
    @Override
    public Resource<EntityStore> clone() {
        ArcaneProxyResource clone = new ArcaneProxyResource();
        clone.proxyRef = this.proxyRef;
        return clone;
    }
}
