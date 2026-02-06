package com.arcanerelay.systems;

import com.arcanerelay.components.ArcaneStaffLegendVisible;
import com.arcanerelay.state.CustomHudRestoreState;
import com.arcanerelay.ui.ArcaneStaffHud;
import com.arcanerelay.ui.EmptyHud;
import com.google.protobuf.Empty;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.packets.interface_.HudComponent;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Shows the Arcane Staff legend when holding the staff and hides it when switching items.
 * Saves the previous custom HUD when showing and restores it when hiding, so we never
 * call setCustomHud(ref, null), which can cause "Failed to Apply CustomHUD commands".
 */
public class ArcaneStaffHudSystem extends EntityTickingSystem<EntityStore> {
    private static final String ARCANE_STAFF_ITEM_ID = "Pseudo_Arcane_Staff";

    @Nonnull
    private static final Query<EntityStore> QUERY = Query.and(
        Player.getComponentType(),
        PlayerRef.getComponentType()
    );

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        PlayerRef playerRef = archetypeChunk.getComponent(index, PlayerRef.getComponentType());
        Player player = archetypeChunk.getComponent(index, Player.getComponentType());
        if (player == null || playerRef == null) return;

        HudManager hudManager = player.getHudManager();

        boolean hasLegendVisible = store.getComponent(ref, ArcaneStaffLegendVisible.getComponentType()) != null;
        boolean isHoldingStaff = isHoldingArcaneStaff(player);

        if (isHoldingStaff && !hasLegendVisible) {
            commandBuffer.addComponent(ref, ArcaneStaffLegendVisible.getComponentType(), new ArcaneStaffLegendVisible());
            hudManager.setCustomHud(playerRef, new ArcaneStaffHud(playerRef));
            return;
        }
        
        if (!isHoldingStaff && hasLegendVisible) {
            commandBuffer.removeComponent(ref, ArcaneStaffLegendVisible.getComponentType());
            hudManager.setCustomHud(playerRef, new EmptyHud(playerRef));
        }
    }

    private static boolean isHoldingArcaneStaff(@Nonnull Player player) {
        ItemStack itemInHand = player.getInventory().getItemInHand();
        if (ItemStack.isEmpty(itemInHand)) return false;

        String id = itemInHand.getItem().getId();
        return ARCANE_STAFF_ITEM_ID.equals(id);
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }
}
