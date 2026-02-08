package com.arcanerelay.ui;

import com.arcanerelay.components.ArcaneTriggerBlock;
import com.hypixel.hytale.assetstore.map.BlockTypeAssetMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import javax.annotation.Nonnull;
import java.util.List;

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
        
        boolean isTriggerBlock = trigger != null;
        commandBuilder.set("#ErrorScreen.Visible", !isTriggerBlock);
        commandBuilder.set("#MainContent.Visible", isTriggerBlock);

        if (!isTriggerBlock) return;
        
        List<Vector3i> outputs = trigger.getOutputPositions();
        AddOutputDestinationList(chunkStore, commandBuilder, eventBuilder, trigger, outputs);

        // Update connection count
        int count = outputs.size();
        String countText = count == 1 ? "1 connection" : count + " connections";
        commandBuilder.set("#ConnectionCount.Text", countText);
        
        // Show/hide clear button
        commandBuilder.set("#ClearButton.Visible", trigger.hasOutputPositions());
        
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ClearButton",
                EventData.of("Action", "Clear")
        );
    }

    @NonNullDecl
    private static void AddOutputDestinationList(@NonNullDecl Store<ChunkStore> chunkStore, @NonNullDecl UICommandBuilder commandBuilder, @NonNullDecl UIEventBuilder eventBuilder, ArcaneTriggerBlock trigger, List<Vector3i> outputs) {        
        boolean isEmpty = outputs.isEmpty();
        commandBuilder.set("#NoConnections.Visible", isEmpty);

        if (isEmpty) return;

        for (int i = 0; i < outputs.size(); i++) {
            Vector3i destination = outputs.get(i);
            commandBuilder.append("#OutputList", "Pages/ConnectionRow.ui");
            
            String selector = "#OutputList[" + i + "]";
            String blockName = getBlockName(chunkStore, destination);
        
            String displayText = blockName + ": " + destination.getX() + ", " + destination.getY() + ", " + destination.getZ();
            String posKey = destination.getX() + "," + destination.getY() + "," + destination.getZ();
            
            commandBuilder.set(selector + " #Position.Text", displayText);
            EventData eventData = EventData.of("RemovePosition", posKey);
            eventData.put("Selector", selector);

            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, selector + " #RemoveButton", eventData, false);
        }
    }

    private static String getBlockName(@NonNullDecl Store<ChunkStore> chunkStore, @NonNullDecl Vector3i destination) {
        BlockTypeAssetMap<String, BlockType> blockTypeMap = BlockType.getAssetMap();
        
        long chunkIndex = ChunkUtil.indexChunkFromBlock(destination.getX(), destination.getZ());
        World world = chunkStore.getExternalData().getWorld();
        WorldChunk chunk = world.getChunkIfInMemory(chunkIndex);
            
        int blockId = chunk.getBlock(destination.getX(), destination.getY(), destination.getZ());
        BlockType blockType = blockTypeMap.getAsset(blockId);

        Item item = blockType.getItem();
        if (item == null) {
            return blockType.getId();
        }

        return Message.translation(item.getTranslationProperties().getName()).getAnsiMessage();
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageEventData data) {
        if (!blockRef.isValid()) return;

        Store<ChunkStore> chunkStore = blockRef.getStore();
        ArcaneTriggerBlock comp = chunkStore.getComponent(blockRef, ArcaneTriggerBlock.getComponentType());
        if (comp == null) return;

        ArcaneTriggerBlock updated = (ArcaneTriggerBlock) comp.clone();

        if ("Clear".equals(data.action)) {
            updated.clearOutputPositions();
            chunkStore.putComponent(blockRef, ArcaneTriggerBlock.getComponentType(), updated);
            rebuild();
            
            return;
        } 
        
        if (data.removePosition == null || data.removePosition.isEmpty()) return;

        String[] parts = data.removePosition.split(",");
        if (parts.length != 3) return;

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

    public static final class PageEventData {
        public String action;
        public String removePosition;
        public String selector;

        public static final BuilderCodec<PageEventData> CODEC = 
            BuilderCodec.builder(PageEventData.class, PageEventData::new)
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
    }
}
