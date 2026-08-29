package com.arcanerelay.features.configurator.systems;

import org.joml.Vector3i;

import java.util.Map;

import com.arcanerelay.ArcaneRelayPlugin;
import com.arcanerelay.features.configurator.components.ArcaneConfiguratorComponent;
import com.arcanerelay.features.configurator.util.VisualsUtil;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class VisualSelectionSystem extends EntityTickingSystem<EntityStore> {

    @Override
    public Query<EntityStore> getQuery() {
        return ArcaneConfiguratorComponent.getComponentType();
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> archetypeChunk, Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer) {
        World world = store.getExternalData().getWorld();
        ArcaneConfiguratorComponent configurator = archetypeChunk.getComponent(index, ArcaneConfiguratorComponent.getComponentType());

        for (Map.Entry<Vector3i, Integer> selection : configurator.getSelectedBlocks().entrySet()) {
            VisualsUtil.displayTriggerConnections(world, selection.getKey(), selection.getValue());
        }
    }
}