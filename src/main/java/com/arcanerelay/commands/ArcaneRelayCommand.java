package com.arcanerelay.commands;

import com.arcanerelay.components.ArcaneConfiguratorComponent;
import com.arcanerelay.components.ArcaneTriggerBlock;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import javax.annotation.Nonnull;

/**
 * Arcane relay commands - configure Arcane Trigger blocks.
 *
 * /arcanerelay lineset1 - Set trigger block (target must be ArcaneTriggerBlock)
 * /arcanerelay lineset2 - Add target block as output connection
 * /arcanerelay lineclear - Clear selection or clear targeted trigger's outputs
 */
public class ArcaneRelayCommand extends AbstractCommandCollection {

    public ArcaneRelayCommand() {
        super("arcanerelay", "ArcaneRelay mod commands");
        this.addSubCommand(new LineSet1Command());
        this.addSubCommand(new LineSet2Command());
        this.addSubCommand(new LineClearCommand());
    }

    /** /arcanerelay lineset1 - target must be ArcaneTriggerBlock */
    public static class LineSet1Command extends AbstractPlayerCommand {
        public LineSet1Command() {
            super("lineset1", "Set first point for line selection");
            setPermissionGroup(GameMode.Creative);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                              @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            Vector3i targetBlock = TargetUtil.getTargetBlock(ref, 15.0, store);
            if (targetBlock == null) {
                NotificationUtil.sendNotification(playerRef.getPacketHandler(), Message.raw("Could not get target block."), NotificationStyle.Warning);
                return;
            }
            if (BlockModule.get().getComponent(ArcaneTriggerBlock.getComponentType(), world, targetBlock.getX(), targetBlock.getY(), targetBlock.getZ()) == null) {
                NotificationUtil.sendNotification(playerRef.getPacketHandler(), Message.raw("First point must be an Arcane Trigger block."), NotificationStyle.Warning);
                return;
            }
            ArcaneConfiguratorComponent configurator = store.getComponent(ref, ArcaneConfiguratorComponent.getComponentType());
            if (configurator == null) {
                NotificationUtil.sendNotification(playerRef.getPacketHandler(), Message.raw("Could not get ArcaneConfiguratorComponent."), NotificationStyle.Warning);
                return;
            }
            ArcaneConfiguratorComponent updated = (ArcaneConfiguratorComponent) configurator.clone();
            updated.setConfiguredBlock(targetBlock);
            store.putComponent(ref, ArcaneConfiguratorComponent.getComponentType(), updated);
            NotificationUtil.sendNotification(playerRef.getPacketHandler(), Message.raw("Trigger set to " + targetBlock.getX() + ", " + targetBlock.getY() + ", " + targetBlock.getZ()), NotificationStyle.Success);
        }
    }

    /** /arcanerelay lineset2 - add target block as output to the trigger set by lineset1 */
    public static class LineSet2Command extends AbstractPlayerCommand {
        public LineSet2Command() {
            super("lineset2", "Add target block as output connection to the trigger (set with lineset1)");
            setPermissionGroup(GameMode.Creative);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                              @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            ArcaneConfiguratorComponent configurator = store.getComponent(ref, ArcaneConfiguratorComponent.getComponentType());
            if (configurator == null || !configurator.hasConfiguredBlock()) {
                NotificationUtil.sendNotification(playerRef.getPacketHandler(), Message.raw("Set trigger first with /arcanerelay lineset1 (target an Arcane Trigger block)."), NotificationStyle.Warning);
                return;
            }
            Vector3i targetBlock = TargetUtil.getTargetBlock(ref, 15.0, store);
            if (targetBlock == null) {
                NotificationUtil.sendNotification(playerRef.getPacketHandler(), Message.raw("Could not get target block."), NotificationStyle.Warning);
                return;
            }
            Vector3i triggerPos = configurator.getConfiguredBlock();
            world.execute(() -> {
                var chunkStore = world.getChunkStore();
                var chunkStoreStore = chunkStore.getStore();
                Ref<ChunkStore> blockRef = BlockModule.getBlockEntity(world, triggerPos.x, triggerPos.y, triggerPos.z);
                if (blockRef == null || !blockRef.isValid()) {
                    return;
                }
                ArcaneTriggerBlock comp = chunkStoreStore.getComponent(blockRef, ArcaneTriggerBlock.getComponentType());
                if (comp == null) {
                    return;
                }
                ArcaneTriggerBlock updated = (ArcaneTriggerBlock) comp.clone();
                updated.addOutputPosition(targetBlock);
                chunkStoreStore.putComponent(blockRef, ArcaneTriggerBlock.getComponentType(), updated);
            });
            NotificationUtil.sendNotification(playerRef.getPacketHandler(), Message.raw("Output added at " + targetBlock.getX() + ", " + targetBlock.getY() + ", " + targetBlock.getZ()), NotificationStyle.Success);
        }
    }

    /** /arcanerelay lineclear - clear per-player selection; if targeting an Arcane Trigger, clear its output connections */
    public static class LineClearCommand extends AbstractPlayerCommand {
        public LineClearCommand() {
            super("lineclear", "Clear line selection, or clear output connections of targeted Arcane Trigger block");
            setPermissionGroup(GameMode.Creative);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                              @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            Vector3i targetBlock = TargetUtil.getTargetBlock(ref, 15.0, store);
            if (targetBlock != null) {
                int tx = targetBlock.getX(), ty = targetBlock.getY(), tz = targetBlock.getZ();
                ArcaneTriggerBlock comp = BlockModule.get().getComponent(ArcaneTriggerBlock.getComponentType(), world, tx, ty, tz);
                if (comp != null && comp.hasOutputPositions()) {
                    world.execute(() -> {
                        var chunkStore = world.getChunkStore();
                        var chunkStoreStore = chunkStore.getStore();
                        Ref<ChunkStore> blockRef = BlockModule.getBlockEntity(world, tx, ty, tz);
                        if (blockRef != null && blockRef.isValid()) {
                            ArcaneTriggerBlock current = chunkStoreStore.getComponent(blockRef, ArcaneTriggerBlock.getComponentType());
                            if (current != null) {
                                ArcaneTriggerBlock updated = (ArcaneTriggerBlock) current.clone();
                                updated.clearOutputPositions();
                                chunkStoreStore.putComponent(blockRef, ArcaneTriggerBlock.getComponentType(), updated);
                            }
                        }
                    });
                    NotificationUtil.sendNotification(playerRef.getPacketHandler(), Message.raw("Arcane Trigger output connections cleared."), NotificationStyle.Success);
                    return;
                }
            }
            ArcaneConfiguratorComponent configurator = store.getComponent(ref, ArcaneConfiguratorComponent.getComponentType());
            if (configurator != null && configurator.hasConfiguredBlock()) {
                ArcaneConfiguratorComponent updated = (ArcaneConfiguratorComponent) configurator.clone();
                updated.clearConfiguredBlock();
                store.putComponent(ref, ArcaneConfiguratorComponent.getComponentType(), updated);
            }
            NotificationUtil.sendNotification(playerRef.getPacketHandler(), Message.raw("Selection cleared."), NotificationStyle.Default);
        }
    }
}
