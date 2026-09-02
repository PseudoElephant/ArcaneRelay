package com.pseudoelephant.arcanerelay.features.config.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.pseudoelephant.arcanerelay.ArcaneRelayPlugin;
import com.pseudoelephant.arcanerelay.features.config.ArcaneRelayConfig;

import javax.annotation.Nonnull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Custom UI page for Arcane Relay Config: view and modify configuration
 * settings.
 * Opened when the player opens the ArcaneRelayConfig settings UI.
 */
public class ArcaneRelayConfigSettingsPage
        extends InteractiveCustomUIPage<ArcaneRelayConfigSettingsPage.PageEventData> {
    @Nonnull
    private ArcaneRelayConfig originalConfig;

    @Nonnull
    private ArcaneRelayConfig stagingConfig;

    private final Set<String> invalidFields = new HashSet<>();

    public ArcaneRelayConfigSettingsPage(@Nonnull PlayerRef playerRef, @Nonnull ArcaneRelayConfig config) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageEventData.CODEC);
        this.originalConfig = config != null ? config : new ArcaneRelayConfig();
        this.stagingConfig = new ArcaneRelayConfig(this.originalConfig);
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder commandBuilder,
            @Nonnull UIEventBuilder eventBuilder,
            @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/ArcaneRelayConfig.ui");

        // Set values for all numeric settings
        commandBuilder.set("#RelayDistanceInput.Value", String.valueOf(stagingConfig.getRelayDistance()));
        commandBuilder.set("#PusherRangeInput.Value", String.valueOf(stagingConfig.getPusherRange()));
        commandBuilder.set("#PullerRangeInput.Value", String.valueOf(stagingConfig.getPullerRange()));
        commandBuilder.set("#BreakerDamageInput.Value", String.format("%.2f", stagingConfig.getBreakerEntityDamage()));
        commandBuilder.set("#BreakerBlockDamageScalarInput.Value",
                String.format("%.2f", stagingConfig.getBreakerBlockDamageScalar()));
        commandBuilder.set("#NonMovableListInput.Value", String.join(", ", stagingConfig.getNoneMoveableBlocks()));
        commandBuilder.set("#NonRotatableBlacklistInput.Value",
                String.join(", ", stagingConfig.getNoneRotatableBlocks()));

        // Set up textbox event bindings
        EventData triggerData = EventData.of("Action", "UpdateRelayDistance");
        triggerData.put("@TextValue", "#RelayDistanceInput.Value");
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#RelayDistanceInput", triggerData, false);

        EventData pusherData = EventData.of("Action", "UpdatePusherRange");
        pusherData.put("@TextValue", "#PusherRangeInput.Value");
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#PusherRangeInput", pusherData, false);

        EventData pullerData = EventData.of("Action", "UpdatePullerRange");
        pullerData.put("@TextValue", "#PullerRangeInput.Value");
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#PullerRangeInput", pullerData, false);

        EventData breakerData = EventData.of("Action", "UpdateBreakerDamage");
        breakerData.put("@TextValue", "#BreakerDamageInput.Value");
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#BreakerDamageInput", breakerData, false);

        EventData breakerBlockData = EventData.of("Action", "UpdateBreakerBlockDamageScalar");
        breakerBlockData.put("@TextValue", "#BreakerBlockDamageScalarInput.Value");
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#BreakerBlockDamageScalarInput",
                breakerBlockData, false);

        EventData nonMovableData = EventData.of("Action", "UpdateNonMovableList");
        nonMovableData.put("@TextValue", "#NonMovableListInput.Value");
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#NonMovableListInput", nonMovableData,
                false);

        EventData nonRotatableData = EventData.of("Action", "UpdateNonRotatableList");
        nonRotatableData.put("@TextValue", "#NonRotatableBlacklistInput.Value");
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#NonRotatableBlacklistInput",
                nonRotatableData, false);

        // Set up button event bindings
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SaveButton",
                EventData.of("Action", "Save"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CancelButton",
                EventData.of("Action", "Cancel"));
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
            @Nonnull PageEventData data) {
        if (data.action == null || data.action.isEmpty())
            return;

        switch (data.action) {
            case "UpdateRelayDistance":
                handleIntInput(data, stagingConfig::setRelayDistance);
                return;
            case "UpdatePusherRange":
                handleIntInput(data, stagingConfig::setPusherRange);
                return;
            case "UpdatePullerRange":
                handleIntInput(data, stagingConfig::setPullerRange);
                return;
            case "UpdateBreakerDamage":
                handleFloatInput(data, val -> stagingConfig.setBreakerEntityDamage(val));
                return;
            case "UpdateBreakerBlockDamageScalar":
                handleFloatInput(data, val -> stagingConfig.setBreakerBlockDamageScalar(val));
                return;
            case "UpdateNonMovableList":
                handleSetInput(data, stagingConfig::setNoneMoveableBlocks);
                return;
            case "UpdateNonRotatableList":
                handleSetInput(data, stagingConfig::setNoneRotatableBlocks);
                return;
            case "Save":
                attemptSaveConfig();
                store.getExternalData().getWorld().execute(this::closePage);
                return;
            case "Cancel":
                NotificationUtil.sendNotification(playerRef.getPacketHandler(),
                        Message.translation("server.arcanerelay.notifications.SettingsNoChange"),
                        NotificationStyle.Warning);
                store.getExternalData().getWorld().execute(this::closePage);
                return;
        }
    }

    private void handleIntInput(PageEventData data, java.util.function.IntConsumer setter) {
        if (data.textValue == null || data.textValue.isEmpty())
            return;

        try {
            int value = Integer.parseInt(data.textValue);
            if (value >= 0) {
                setter.accept(value);
                invalidFields.remove(data.action);
            } else {
                invalidFields.add(data.action);
            }
        } catch (NumberFormatException ignored) {
            invalidFields.add(data.action);
        }
    }

    private void handleFloatInput(PageEventData data, java.util.function.Consumer<Float> setter) {
        if (data.textValue == null || data.textValue.isEmpty())
            return;

        try {
            float value = Float.parseFloat(data.textValue);
            if (value >= 0.0f) {
                setter.accept(value);
                invalidFields.remove(data.action);
            } else {
                invalidFields.add(data.action);
            }
        } catch (NumberFormatException ignored) {
            invalidFields.add(data.action);
        }
    }

    private void handleSetInput(PageEventData data, Consumer<Set<String>> setter) {
        if (data.textValue == null || data.textValue.isEmpty())
            return;

        Set<String> blocks = Arrays.stream(data.textValue.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        setter.accept(blocks);
    }

    private void attemptSaveConfig() {
        if (originalConfig.equals(stagingConfig)) {
            sendNoChangeNotification();
            return;
        }

        originalConfig.copyFrom(stagingConfig);
        ArcaneRelayPlugin.get().saveConfig();
        sendSavedNotification();
    }

    private void sendSavedNotification() {
        String translationKey = invalidFields.isEmpty()
                ? "server.arcanerelay.notifications.SettingsSucces"
                : "server.arcanerelay.notifications.SettingsSuccesWithInvalid";
        
        NotificationStyle style = invalidFields.isEmpty() 
                ? NotificationStyle.Success 
                : NotificationStyle.Warning;

        NotificationUtil.sendNotification(playerRef.getPacketHandler(),
                Message.translation(translationKey),
                style);
    }

    private void sendNoChangeNotification() {
        String translationKey = invalidFields.isEmpty()
                ? "server.arcanerelay.notifications.SettingsSuccesNoChange"
                : "server.arcanerelay.notifications.SettingsSuccesNoChangeWithInvalid";
        
        NotificationStyle style = invalidFields.isEmpty() 
                ? NotificationStyle.Success 
                : NotificationStyle.Warning;

        NotificationUtil.sendNotification(playerRef.getPacketHandler(),
                Message.translation(translationKey),
                style);
    }

    private void closePage() {
        Ref<EntityStore> playerRef = this.playerRef.getReference();
        if (playerRef == null || !playerRef.isValid())
            return;

        Player playerComponent = playerRef.getStore().getComponent(playerRef, Player.getComponentType());
        if (playerComponent == null)
            return;

        playerComponent.getPageManager().setPage(playerRef, playerRef.getStore(), Page.None);
    } 

    public static final class PageEventData {
        public String action;
        public String textValue;

        public static final BuilderCodec<PageEventData> CODEC = BuilderCodec
                .builder(PageEventData.class, PageEventData::new)
                .append(
                        new KeyedCodec<>("Action", Codec.STRING),
                        (d, v) -> d.action = v,
                        d -> d.action)
                .add()
                .append(
                        new KeyedCodec<>("@TextValue", Codec.STRING),
                        (d, v) -> d.textValue = v,
                        d -> d.textValue)
                .add()
                .build();
    }
}
