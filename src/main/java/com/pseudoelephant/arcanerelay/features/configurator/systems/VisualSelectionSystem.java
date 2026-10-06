package com.pseudoelephant.arcanerelay.features.configurator.systems;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.pseudoelephant.arcanerelay.features.configurator.components.ArcaneConfiguratorComponent;
import com.pseudoelephant.arcanerelay.features.configurator.util.VisualsUtil;

public class VisualSelectionSystem extends EntityTickingSystem<EntityStore> {

    @Override
    public Query<EntityStore> getQuery() {
        return ArcaneConfiguratorComponent.getComponentType();
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk, @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        World world = store.getExternalData().getWorld();
        ArcaneConfiguratorComponent configurator = archetypeChunk.getComponent(index, ArcaneConfiguratorComponent.getComponentType());
        
        if (configurator == null || !configurator.isConfiguring()) {
            return;
        }
        
        VisualsUtil.displayTriggerConnections(world, configurator.getSelectedBlocks());
    }
}