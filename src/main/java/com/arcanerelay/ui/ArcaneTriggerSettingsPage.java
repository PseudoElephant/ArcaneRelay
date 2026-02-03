package com.arcanerelay.ui;

import com.arcanerelay.components.ArcaneTriggerBlock;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/**
 * Custom UI page for Arcane Trigger block: view and clear output connection positions.
 * Opened by interacting with an Arcane Trigger block (e.g. Secondary click) when
 * the block uses the OpenCustomUI interaction with Page "ArcaneTrigger".
 */
public class ArcaneTriggerSettingsPage extends InteractiveCustomUIPage<ArcaneTriggerSettingsPage.PageEventData> {    
    @Nonnull
    private final Ref<ChunkStore> blockRef;

    public ArcaneTriggerSettingsPage(@Nonnull PlayerRef playerRef, @Nonnull Ref<ChunkStore> blockRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageEventData.CODEC);
        this.blockRef = blockRef;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder commandBuilder,
            @Nonnull UIEventBuilder eventBuilder,
            @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/ArcaneTrigger.ui");
        
        Store<ChunkStore> chunkStore = blockRef.getStore();
        ArcaneTriggerBlock trigger = chunkStore.getComponent(blockRef, ArcaneTriggerBlock.getComponentType());
        
        if (trigger == null) {
            commandBuilder.set("#ErrorScreen.Visible", true);
            commandBuilder.set("#MainContent.Visible", false);
            return;
        }
        
        commandBuilder.set("#ErrorScreen.Visible", false);
        commandBuilder.set("#MainContent.Visible", true);
        
        // Build output positions list with per-row remove buttons
        java.util.List<Vector3i> outputs = trigger.getOutputPositions();
        commandBuilder.clear("#OutputList");
        if (outputs.isEmpty()) {
            commandBuilder.appendInline("#OutputList",
                    "Label { Text: \"(no connections)\"; Style: (FontSize: 14, TextColor: #afc2c3); }");
        } else {
            // Append all rows first so client has stable indices, then set properties and bind events
            for (int i = 0; i < outputs.size(); i++) {
                commandBuilder.append("#OutputList", "Pages/ConnectionRow.ui");
            }
            for (int i = 0; i < outputs.size(); i++) {
                Vector3i p = outputs.get(i);
                String selector = "#OutputList[" + i + "]";
                String posText = p.getX() + ", " + p.getY() + ", " + p.getZ();
                String posKey = p.getX() + "," + p.getY() + "," + p.getZ();
                commandBuilder.set(selector + " #Position.Text", posText);

                EventData eventData = EventData.of("RemovePosition", posKey);
                eventData.put("Selector", selector);

                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, selector + " #RemoveButton", eventData, false);
            }
        }
        
        // Update connection count
        int count = outputs.size();
        String countText = count == 1 ? "1 connection" : count + " connections";
        commandBuilder.set("#ConnectionCount.Text", countText);
        
        // Show/hide clear button based on whether there are connections
        commandBuilder.set("#ClearButton.Visible", trigger.hasOutputPositions());
        
        // Bind clear button event (use "Action" key, not "@Action" - literal value)
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ClearButton",
                EventData.of("Action", "Clear")
        );
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageEventData data) {
        if (!blockRef.isValid()) {
            return;
        }

        Store<ChunkStore> chunkStore = blockRef.getStore();
        ArcaneTriggerBlock comp = chunkStore.getComponent(blockRef, ArcaneTriggerBlock.getComponentType());
        if (comp == null) return;

        ArcaneTriggerBlock updated = (ArcaneTriggerBlock) comp.clone();

        if ("Clear".equals(data.action)) {
            updated.clearOutputPositions();
            chunkStore.putComponent(blockRef, ArcaneTriggerBlock.getComponentType(), updated);
            java.util.List<Vector3i> outputs = updated.getOutputPositions();

            // Refresh page so list and count update (do not close)
            UICommandBuilder commandBuilder = new UICommandBuilder();
            commandBuilder.clear("#OutputList");
            commandBuilder.set("#ConnectionCount.Text", outputs.size() + " connections");
            sendUpdate(commandBuilder);
        } else if (data.removePosition != null && !data.removePosition.isEmpty()) {
            String[] parts = data.removePosition.split(",");
            if (parts.length == 3) {
                try {
                    int x = Integer.parseInt(parts[0].trim());
                    int y = Integer.parseInt(parts[1].trim());
                    int z = Integer.parseInt(parts[2].trim());
                    
                    if (updated.removeOutputPosition(x, y, z)) {
                        chunkStore.putComponent(blockRef, ArcaneTriggerBlock.getComponentType(), updated);
                        rebuild();
                    }
                } catch (NumberFormatException ignored) { }
            }
        }
    }

    public static final class PageEventData {
        public static final BuilderCodec<PageEventData> CODEC = BuilderCodec.builder(
                        PageEventData.class, PageEventData::new)
                .append(
                        new KeyedCodec<>("Action", Codec.STRING),
                        (d, v) -> d.action = v,
                        d -> d.action)
                .add()
                .append(
                        new KeyedCodec<>("RemovePosition", Codec.STRING),
                        (d, v) -> d.removePosition = v,
                        d -> d.removePosition)
                .add()
                .append(
                        new KeyedCodec<>("Selector", Codec.STRING),
                        (d, v) -> d.selector = v,
                        d -> d.selector)
                .add()
                .build();
        public String action;
        public String removePosition;
        public String selector;

        public PageEventData() {
        }
    }
}
