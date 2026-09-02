package com.pseudoelephant.arcanerelay.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

public class ArcaneRelayCommandCollection extends AbstractCommandCollection {
    public ArcaneRelayCommandCollection() {
        super("arcanerelay", "Arcane Relay commands");
        addAliases("arcaner", "ar");

        this.registerSubCommands();
    }

    private void registerSubCommands() {
       addSubCommand(new EditSettingsCommand());
    }
} 
