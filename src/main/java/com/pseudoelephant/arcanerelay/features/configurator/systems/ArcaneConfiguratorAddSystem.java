package com.pseudoelephant.arcanerelay.features.configurator.systems;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.pseudoelephant.arcanerelay.features.configurator.components.ArcaneConfiguratorComponent;

import javax.annotation.Nonnull;

/** Adds ArcaneConfiguratorComponent to players when they join the world. */
public class ArcaneConfiguratorAddSystem extends HolderSystem<EntityStore> {
    @Override
    public void onEntityAdd(@Nonnull Holder<EntityStore> holder, @Nonnull AddReason reason, @Nonnull Store<EntityStore> store) {
        ComponentType<EntityStore, ArcaneConfiguratorComponent> configuratorType = ArcaneConfiguratorComponent.getComponentType();
        if (configuratorType == null) {
            return;
        }

        holder.ensureComponent(configuratorType);
    }

    @Override
    public void onEntityRemoved(@Nonnull Holder<EntityStore> holder, @Nonnull RemoveReason reason, @Nonnull Store<EntityStore> store) { }

    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }
}
