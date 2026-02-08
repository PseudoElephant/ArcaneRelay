package com.arcanerelay.ui;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

/**
 * Empty HUD, necessary because hudManager.resetHud() currently crashes the server for some reason. Instead replace custom HUD with this to remove.
 */
public final class EmptyHud extends CustomUIHud {
    public EmptyHud(@Nonnull PlayerRef playerRef) {
        super(playerRef);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder commandBuilder) { }
}
