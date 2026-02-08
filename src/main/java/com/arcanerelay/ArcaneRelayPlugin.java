package com.arcanerelay;

import com.arcanerelay.config.Activation;
import com.arcanerelay.config.ActivationBindingRegistry;
import com.arcanerelay.config.ActivationRegistry;
import com.arcanerelay.config.types.ArcaneDischargeActivation;
import com.arcanerelay.config.types.ChainActivation;
import com.arcanerelay.config.types.MoveBlockActivation;
import com.arcanerelay.config.types.SendSignalActivation;
import com.arcanerelay.config.types.ToggleDoorActivation;
import com.arcanerelay.config.types.ToggleStateActivation;
import com.arcanerelay.components.ArcaneConfiguratorComponent;
import com.arcanerelay.components.ArcaneMoveBlock;
import com.arcanerelay.components.ArcaneStaffLegendVisible;
import com.arcanerelay.components.ArcaneTriggerBlock;
import com.arcanerelay.interactions.AddOutputInteraction;
import com.arcanerelay.interactions.ArcaneActivatorInteraction;
import com.arcanerelay.interactions.SelectTriggerInteraction;
import com.arcanerelay.interactions.SendSignalInteraction;
import com.arcanerelay.state.ArcaneMoveState;
import com.arcanerelay.state.ArcaneState;
import com.arcanerelay.state.CustomHudRestoreState;
import com.arcanerelay.systems.ArcaneConfiguratorAddSystem;
import com.arcanerelay.systems.ArcaneOnPlaceSystem;
import com.arcanerelay.systems.ArcaneStaffHudSystem;
import com.arcanerelay.systems.ArcaneTickSystem;
import com.arcanerelay.ui.ArcaneTriggerPageSupplier;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.BootEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class ArcaneRelayPlugin extends JavaPlugin {

    private static ArcaneRelayPlugin instance;
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ActivationBindingRegistry activationBindingRegistry = new ActivationBindingRegistry();
    private final ActivationRegistry activationRegistry = new ActivationRegistry(activationBindingRegistry);

    private ComponentType<ChunkStore, ArcaneTriggerBlock> arcaneTriggerBlockComponentType;
    private ComponentType<ChunkStore, ArcaneMoveBlock> arcaneMoveBlockComponentType;
    private ComponentType<EntityStore, ArcaneConfiguratorComponent> arcaneConfiguratorComponentType;
    private ComponentType<EntityStore, ArcaneStaffLegendVisible> arcaneStaffLegendVisibleComponentType;
    private ResourceType<ChunkStore, ArcaneState> arcaneStateResourceType;
    private ResourceType<ChunkStore, ArcaneMoveState> arcaneMoveStateResourceType;
    private ResourceType<EntityStore, CustomHudRestoreState> customHudRestoreStateResourceType;

    public ArcaneRelayPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    public static ArcaneRelayPlugin get() {
        return instance;
    }

    @Override
    protected void setup() {
        instance = this;
        LOGGER.atInfo().log("ArcaneRelay setting up");

        ComponentRegistryProxy<ChunkStore> chunkRegistry = this.getChunkStoreRegistry();
        this.arcaneStateResourceType = chunkRegistry.registerResource(ArcaneState.class, ArcaneState::new);
        this.arcaneMoveStateResourceType = chunkRegistry.registerResource(ArcaneMoveState.class, ArcaneMoveState::new);
        this.arcaneTriggerBlockComponentType = chunkRegistry.registerComponent(ArcaneTriggerBlock.class,
                "ArcaneTrigger", ArcaneTriggerBlock.CODEC);
        this.arcaneMoveBlockComponentType = chunkRegistry.registerComponent(ArcaneMoveBlock.class, "ArcaneMove",
                ArcaneMoveBlock.CODEC);
        chunkRegistry.registerSystem(new ArcaneTickSystem());
        chunkRegistry.registerSystem(new ArcaneOnPlaceSystem());

        ComponentRegistryProxy<EntityStore> entityRegistry = this.getEntityStoreRegistry();
        this.arcaneConfiguratorComponentType = entityRegistry.registerComponent(ArcaneConfiguratorComponent.class,
                ArcaneConfiguratorComponent::new);
        this.arcaneStaffLegendVisibleComponentType = entityRegistry.registerComponent(ArcaneStaffLegendVisible.class,
                ArcaneStaffLegendVisible::new);
        this.customHudRestoreStateResourceType = entityRegistry.registerResource(CustomHudRestoreState.class,
                CustomHudRestoreState::new);
        entityRegistry.registerSystem(new ArcaneConfiguratorAddSystem());
        entityRegistry.registerSystem(new ArcaneStaffHudSystem());

        Interaction.CODEC.register("SelectTrigger", SelectTriggerInteraction.class, SelectTriggerInteraction.CODEC);
        Interaction.CODEC.register("AddOutput", AddOutputInteraction.class, AddOutputInteraction.CODEC);
        Interaction.CODEC.register("SendSignal", SendSignalInteraction.class, SendSignalInteraction.CODEC);
        Interaction.CODEC.register("ArcaneActivator", ArcaneActivatorInteraction.class,
                ArcaneActivatorInteraction.CODEC);

        this.getCodecRegistry(OpenCustomUIInteraction.PAGE_CODEC)
                .register("ArcaneTrigger", ArcaneTriggerPageSupplier.class, ArcaneTriggerPageSupplier.CODEC);

        ActivationRegistry.registerAssetStore();
        ActivationBindingRegistry.registerAssetStore();

        this.getCodecRegistry(Activation.CODEC)
                .register("ToggleState", ToggleStateActivation.class, ToggleStateActivation.CODEC)
                .register("SendSignal", SendSignalActivation.class, SendSignalActivation.CODEC)
                .register("ArcaneDischarge", ArcaneDischargeActivation.class, ArcaneDischargeActivation.CODEC)
                .register("MoveBlock", MoveBlockActivation.class, MoveBlockActivation.CODEC)
                .register("Chain", ChainActivation.class, ChainActivation.CODEC)
                .register("ToggleDoor", ToggleDoorActivation.class, ToggleDoorActivation.CODEC);

        this.getEventRegistry().registerGlobal(BootEvent.class, event -> {
            this.activationRegistry.onAssetsLoaded();
            this.activationBindingRegistry.onAssetsLoaded();
        });

        LOGGER.atInfo().log("ArcaneRelay setup complete");
    }

    @Nonnull
    public ActivationBindingRegistry getActivationBindingRegistry() {
        return activationBindingRegistry;
    }

    @Nonnull
    public ActivationRegistry getActivationRegistry() {
        return activationRegistry;
    }

    @Nonnull
    public ComponentType<EntityStore, ArcaneConfiguratorComponent> getArcaneConfiguratorComponentType() {
        return arcaneConfiguratorComponentType;
    }

    public ComponentType<ChunkStore, ArcaneMoveBlock> getArcaneMoveBlockComponentType() {
        return this.arcaneMoveBlockComponentType;
    }

    @Nonnull
    public ResourceType<ChunkStore, ArcaneMoveState> getArcaneMoveStateResourceType() {
        return this.arcaneMoveStateResourceType;
    }

    @Nonnull
    public ComponentType<EntityStore, ArcaneStaffLegendVisible> getArcaneStaffLegendVisibleComponentType() {
        return arcaneStaffLegendVisibleComponentType;
    }

    @Nonnull
    public ResourceType<ChunkStore, ArcaneState> getArcaneStateResourceType() {
        return this.arcaneStateResourceType;
    }

    public ComponentType<ChunkStore, ArcaneTriggerBlock> getArcaneTriggerBlockComponentType() {
        return this.arcaneTriggerBlockComponentType;
    }

    @Nonnull
    public ResourceType<EntityStore, CustomHudRestoreState> getCustomHudRestoreStateResourceType() {
        return customHudRestoreStateResourceType;
    }
}
