package com.arcanerelay.ui;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

/**
 * Custom HUD overlay shown when the player is holding the Arcane Staff.
 * Displays usage instructions (e.g. Primary: Select trigger, Secondary: Add/remove output).
 */
public class ArcaneStaffHud extends CustomUIHud {

    public ArcaneStaffHud(@Nonnull PlayerRef playerRef) {
        super(playerRef);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.append("Pages/ArcaneStaffLegend.ui");
    }
}
