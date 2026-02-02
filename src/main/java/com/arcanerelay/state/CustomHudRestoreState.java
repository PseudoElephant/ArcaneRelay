package com.arcanerelay.state;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-EntityStore resource that stores the previous CustomUIHud to restore when
 * we hide the Arcane Staff legend, so we never call setCustomHud(ref, null).
 */
public final class CustomHudRestoreState implements Resource<EntityStore> {

    private final Map<Ref<EntityStore>, CustomUIHud> previousHudByRef = new HashMap<>();

    public CustomHudRestoreState() {
    }

    public static ResourceType<EntityStore, CustomHudRestoreState> getResourceType() {
        return com.arcanerelay.ArcaneRelayPlugin.get().getCustomHudRestoreStateResourceType();
    }

    @Nullable
    public CustomUIHud getAndRemove(@Nonnull Ref<EntityStore> ref) {
        return previousHudByRef.remove(ref);
    }

    public void put(@Nonnull Ref<EntityStore> ref, @Nonnull CustomUIHud previousHud) {
        previousHudByRef.put(ref, previousHud);
    }

    @Nonnull
    @Override
    public Resource<EntityStore> clone() {
        CustomHudRestoreState clone = new CustomHudRestoreState();
        clone.previousHudByRef.putAll(this.previousHudByRef);
        return clone;
    }
}
