package com.arcanerelay.features.configurator.listeners;

import com.arcanerelay.features.configurator.components.ArcaneConfiguratorComponent;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.InventorySetActiveSlotEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

public class InventorySetActiveSlotEventHandler extends EntityEventSystem<EntityStore, InventorySetActiveSlotEvent> {

    private static final String ARCANE_STAFF_ITEM_ID = "Pseudo_Arcane_Staff";

    public InventorySetActiveSlotEventHandler() {
        super(InventorySetActiveSlotEvent.class);
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull InventorySetActiveSlotEvent event
    ) {
        if (event.getInventorySectionId() != InventoryComponent.HOTBAR_SECTION_ID) {
            return;
        }

        InventoryComponent.Hotbar hotbar = archetypeChunk.getComponent(index, InventoryComponent.Hotbar.getComponentType());
        ArcaneConfiguratorComponent configurator = archetypeChunk.getComponent(index, ArcaneConfiguratorComponent.getComponentType());
        if (configurator == null) {
            return;
        }

        ItemStack activeItem = hotbar != null ? hotbar.getActiveItem() : null;
        boolean holdingStaff = !ItemStack.isEmpty(activeItem) && ARCANE_STAFF_ITEM_ID.equals(activeItem.getItem().getId());
        configurator.setConfiguring(holdingStaff);
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType(), InventoryComponent.Hotbar.getComponentType());
    }
}
