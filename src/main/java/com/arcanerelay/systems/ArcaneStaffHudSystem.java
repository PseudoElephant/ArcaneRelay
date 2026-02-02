package com.arcanerelay.systems;

import com.arcanerelay.components.ArcaneStaffLegendVisible;
import com.arcanerelay.state.CustomHudRestoreState;
import com.arcanerelay.ui.ArcaneStaffHud;
import com.arcanerelay.ui.NoOpCustomHud;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Shows the Arcane Staff legend when holding the staff and hides it when switching items.
 * Saves the previous custom HUD when showing and restores it when hiding, so we never
 * call setCustomHud(ref, null), which can cause "Failed to Apply CustomHUD commands".
 */
public class ArcaneStaffHudSystem extends EntityTickingSystem<EntityStore> {

    /** Item id for the Arcane Staff (path from Item asset root; fallback short id for some loaders). */
    private static final String ARCANE_STAFF_ITEM_ID = "Items/Tool/Arcane_Relay/Pseudo_Arcane_Staff";
    private static final String ARCANE_STAFF_ITEM_ID_SHORT = "Pseudo_Arcane_Staff";

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
        Player player = archetypeChunk.getComponent(index, Player.getComponentType());
        PlayerRef playerRef = archetypeChunk.getComponent(index, PlayerRef.getComponentType());
        if (player == null || playerRef == null) return;

        HudManager hudManager = player.getHudManager();
        CustomHudRestoreState restoreState = store.getResource(CustomHudRestoreState.getResourceType());
        if (restoreState == null) return;

        boolean hasLegendVisible = store.getComponent(ref, ArcaneStaffLegendVisible.getComponentType()) != null;
        boolean holdingStaff = isHoldingArcaneStaff(player);

        if (holdingStaff) {
            if (!hasLegendVisible) {
                // Save current custom HUD (or NoOp so we never restore null)
                var current = hudManager.getCustomHud();
                restoreState.put(ref, current != null ? current : new NoOpCustomHud(playerRef));
                commandBuffer.addComponent(ref, ArcaneStaffLegendVisible.getComponentType(), new ArcaneStaffLegendVisible());
                hudManager.setCustomHud(playerRef, new ArcaneStaffHud(playerRef));
            }
        } else {
            if (hasLegendVisible) {
                var previous = restoreState.getAndRemove(ref);
                commandBuffer.removeComponent(ref, ArcaneStaffLegendVisible.getComponentType());
                // Restore previous HUD (or NoOp to clear), never setCustomHud(ref, null)
                hudManager.setCustomHud(playerRef, previous != null ? previous : new NoOpCustomHud(playerRef));
            }
        }
    }

    private static boolean isHoldingArcaneStaff(@Nonnull Player player) {
        ItemStack inHand = player.getInventory().getItemInHand();
        if (inHand == null || ItemStack.isEmpty(inHand)) return false;
        var item = inHand.getItem();
        if (item == null) return false;
        String id = item.getId();
        return ARCANE_STAFF_ITEM_ID.equals(id) || ARCANE_STAFF_ITEM_ID_SHORT.equals(id);
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }
}
