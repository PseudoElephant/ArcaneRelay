package com.arcanerelay.ui;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

/**
 * Custom HUD that sends a clear packet (clear=true, empty commands) when shown.
 * Used when restoring "no custom HUD" so we never call setCustomHud(ref, null),
 * which can cause "Failed to Apply CustomHUD commands" on the client.
 */
public final class NoOpCustomHud extends CustomUIHud {

    public NoOpCustomHud(@Nonnull PlayerRef playerRef) {
        super(playerRef);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder commandBuilder) {
        // No commands – show() will send CustomHud(true, empty)
    }
}
