package com.arcanerelay.components;

import com.arcanerelay.ArcaneRelayPlugin;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/**
 * Marker component on a player while the Arcane Staff legend HUD is visible.
 * Used to toggle our custom HUD "component" without using HudManager.setCustomHud.
 */
public final class ArcaneStaffLegendVisible implements Component<EntityStore> {

    public static ComponentType<EntityStore, ArcaneStaffLegendVisible> getComponentType() {
        return ArcaneRelayPlugin.get().getArcaneStaffLegendVisibleComponentType();
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        return new ArcaneStaffLegendVisible();
    }
}
