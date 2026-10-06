package com.pseudoelephant.arcanerelay.commands;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.FlagArg;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.pseudoelephant.arcanerelay.ArcaneRelayPlugin;
import com.pseudoelephant.arcanerelay.features.config.ui.ArcaneRelayConfigSettingsPage;

public class EditSettingsCommand extends AbstractPlayerCommand {
    private final FlagArg resetFlag;
    
    public EditSettingsCommand() {
        super("settings", "Edit Arcane Relay settings");
        addAliases("s");

        resetFlag = this.withFlagArg("reset", "Reset all settings to default values");
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) {
            return;
        }

        if (this.resetFlag.get(commandContext) == true) {
            ArcaneRelayPlugin.get().resetConfig();
            NotificationUtil.sendNotification(playerRef.getPacketHandler(), Message.translation("server.arcanerelay.notifications.ResetSucces"), NotificationStyle.Success);
            return;
        }

        playerComponent.getPageManager().openCustomPage(ref, store, new ArcaneRelayConfigSettingsPage(playerRef, ArcaneRelayPlugin.get().getConfig()));
    }
}
