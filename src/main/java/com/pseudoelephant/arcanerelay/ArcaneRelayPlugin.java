package com.pseudoelephant.arcanerelay;

import com.hypixel.hytale.builtin.triggervolumes.TriggerVolumesPlugin;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.command.system.CommandRegistry;
import com.hypixel.hytale.server.core.event.events.BootEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;
import com.pseudoelephant.arcanerelay.commands.ArcaneRelayCommandCollection;
import com.pseudoelephant.arcanerelay.features.activation.Activation;
import com.pseudoelephant.arcanerelay.features.activation.ActivationBinding;
import com.pseudoelephant.arcanerelay.features.activation.interactions.ArcaneActivatorInteraction;
import com.pseudoelephant.arcanerelay.features.activation.types.ChainActivation;
import com.pseudoelephant.arcanerelay.features.activation.types.ToggleStateActivation;
import com.pseudoelephant.arcanerelay.features.blockmovement.activations.MoveBlockActivation;
import com.pseudoelephant.arcanerelay.features.blockmovement.activations.RotateBlockActivation;
import com.pseudoelephant.arcanerelay.features.blockmovement.resources.ArcaneMoveState;
import com.pseudoelephant.arcanerelay.features.blockmovement.systems.BlockMovementSystem;
import com.pseudoelephant.arcanerelay.features.blocks.breaker.activation.HitActivation;
import com.pseudoelephant.arcanerelay.features.blocks.doors.activation.ToggleDoorActivation;
import com.pseudoelephant.arcanerelay.features.blocks.puller.activations.ArcanePullerActivation;
import com.pseudoelephant.arcanerelay.features.blocks.puller.components.ArcanePullerBlock;
import com.pseudoelephant.arcanerelay.features.config.ArcaneRelayConfig;
import com.pseudoelephant.arcanerelay.features.configurator.components.ArcaneConfiguratorComponent;
import com.pseudoelephant.arcanerelay.features.configurator.interactions.AddOutputInteraction;
import com.pseudoelephant.arcanerelay.features.configurator.interactions.SelectTriggerInteraction;
import com.pseudoelephant.arcanerelay.features.configurator.listeners.InventorySetActiveSlotEventHandler;
import com.pseudoelephant.arcanerelay.features.configurator.systems.ArcaneConfiguratorAddSystem;
import com.pseudoelephant.arcanerelay.features.configurator.systems.VisualSelectionSystem;
import com.pseudoelephant.arcanerelay.features.signal.components.ArcaneSection;
import com.pseudoelephant.arcanerelay.features.signal.systems.EnsureArcaneSectionSystem;
import com.pseudoelephant.arcanerelay.features.signal.systems.PreTickSignalPropagationSystem;
import com.pseudoelephant.arcanerelay.features.signal.systems.TickingSignalPropagationSystem;
import com.pseudoelephant.arcanerelay.features.signaltrigger.activation.SendSignalActivation;
import com.pseudoelephant.arcanerelay.features.signaltrigger.components.ArcaneTriggerBlock;
import com.pseudoelephant.arcanerelay.features.signaltrigger.interactions.SendSignalInteraction;
import com.pseudoelephant.arcanerelay.features.signaltrigger.ui.ArcaneTriggerPageSupplier;
import com.pseudoelephant.arcanerelay.features.triggervolume.ArcaneRelayEffect;

import javax.annotation.Nonnull;

public class ArcaneRelayPlugin extends JavaPlugin {
    private final Config<ArcaneRelayConfig> config = this.withConfig("ArcaneRelayConfig", ArcaneRelayConfig.CODEC);

    private static ArcaneRelayPlugin instance;
    public static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public ArcaneRelayPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    public static ArcaneRelayPlugin get() {
        return instance;
    }
    
    public void saveConfig() {
        config.save();
    }

    @Override
    protected void setup() {
        instance = this;

        config.save();

        registerCodecs();
        registerVolumeTriggers();
        registerInteractions();
        registerComponents();
        registerResources();
        registerSystems();
        registerCommands();
        registerEvents();
        Activation.registerAssetStore();
        ActivationBinding.registerAssetStore();
    }

    @Nonnull
    public ArcaneRelayConfig getConfig() {
        ArcaneRelayConfig config = this.config.get();
        if (config == null) {
            config = new ArcaneRelayConfig();
        }

        return config;
    }

    public void resetConfig() {
        ArcaneRelayConfig configValues = this.getConfig();
        configValues.resetToDefaults();
        this.config.save();
    }

    private void registerInteractions() {
        Interaction.CODEC.register("SelectTrigger", SelectTriggerInteraction.class, SelectTriggerInteraction.CODEC);
        Interaction.CODEC.register("AddOutput", AddOutputInteraction.class, AddOutputInteraction.CODEC);
        Interaction.CODEC.register("SendSignal", SendSignalInteraction.class, SendSignalInteraction.CODEC);
        Interaction.CODEC.register("ArcaneActivator", ArcaneActivatorInteraction.class, ArcaneActivatorInteraction.CODEC);
    }

    private void registerCodecs() {
        this.getCodecRegistry(OpenCustomUIInteraction.PAGE_CODEC)
                .register("ArcaneTrigger", ArcaneTriggerPageSupplier.class, ArcaneTriggerPageSupplier.CODEC);

        this.getCodecRegistry(Activation.CODEC)
                .register("ToggleState", ToggleStateActivation.class, ToggleStateActivation.CODEC)
                .register("SendSignal", SendSignalActivation.class, SendSignalActivation.CODEC)
                .register("MoveBlock", MoveBlockActivation.class, MoveBlockActivation.CODEC)
                .register("RotateBlock", RotateBlockActivation.class, RotateBlockActivation.CODEC)
                .register("ArcanePuller", ArcanePullerActivation.class, ArcanePullerActivation.CODEC)
                .register("Chain", ChainActivation.class, ChainActivation.CODEC)
                .register("ToggleDoor", ToggleDoorActivation.class, ToggleDoorActivation.CODEC)
                .register("Hit", HitActivation.class, HitActivation.CODEC);
    }

    private void registerEvents() {
        EventRegistry registry = this.getEventRegistry();
        
        registry.registerGlobal(BootEvent.class, event -> ActivationBinding.onBindingsLoaded());
    }

    private void registerSystems() {
        registerEntitySystems(); 
        registerChunkSystems();
    }

    private void registerVolumeTriggers(){
        TriggerVolumesPlugin.get().registerEffectType("TriggerArcaneRelay", ArcaneRelayEffect.class, ArcaneRelayEffect.CODEC);
    }
  
    private void registerChunkSystems() {
        ComponentRegistryProxy<ChunkStore> chunkRegistry = this.getChunkStoreRegistry();

        chunkRegistry.registerSystem(new EnsureArcaneSectionSystem());
        chunkRegistry.registerSystem(new PreTickSignalPropagationSystem());
        chunkRegistry.registerSystem(new TickingSignalPropagationSystem());
        chunkRegistry.registerSystem(new BlockMovementSystem());
    }

    private void registerEntitySystems() {
        ComponentRegistryProxy<EntityStore> entityRegistry = this.getEntityStoreRegistry();

        entityRegistry.registerSystem(new ArcaneConfiguratorAddSystem());
        entityRegistry.registerSystem(new VisualSelectionSystem());
        entityRegistry.registerSystem(new InventorySetActiveSlotEventHandler());
    }

    private void registerResources() {
        registerEntityResources();
        registerChunkResources();
    }

    private void registerChunkResources() {
        ComponentRegistryProxy<ChunkStore> chunkRegistry = this.getChunkStoreRegistry();

        ResourceType<ChunkStore, ArcaneMoveState> arcaneMoveStateResourceType = chunkRegistry.registerResource(ArcaneMoveState.class, ArcaneMoveState::new);
        ArcaneMoveState.setResourceType(arcaneMoveStateResourceType);
    }

    private void registerEntityResources() {
        // ComponentRegistryProxy<EntityStore> entityRegistry = this.getEntityStoreRegistry();
    }

    private void registerComponents() {
        registerChunkComponents();
        registerEntityComponents();
    }

    private void registerChunkComponents() {
        ComponentRegistryProxy<ChunkStore> chunkRegistry = this.getChunkStoreRegistry();

        ComponentType<ChunkStore, ArcaneTriggerBlock> arcaneTriggerBlockComponentType = chunkRegistry.registerComponent(ArcaneTriggerBlock.class, "ArcaneTrigger", ArcaneTriggerBlock.CODEC);
        ArcaneTriggerBlock.setComponentType(arcaneTriggerBlockComponentType);

        ComponentType<ChunkStore, ArcaneSection> arcaneSectionComponentType = chunkRegistry.registerComponent(ArcaneSection.class, "ArcaneSection", ArcaneSection.CODEC);
        ArcaneSection.setComponentType(arcaneSectionComponentType);

        ComponentType<ChunkStore, ArcanePullerBlock> arcanePullerBlockComponentType = chunkRegistry.registerComponent(ArcanePullerBlock.class, "ArcanePuller", ArcanePullerBlock.CODEC);
        ArcanePullerBlock.setComponentType(arcanePullerBlockComponentType);
    }

    private void registerEntityComponents() {
        ComponentRegistryProxy<EntityStore> entityRegistry = this.getEntityStoreRegistry();

        ComponentType<EntityStore, ArcaneConfiguratorComponent> arcaneConfiguratorComponentType = entityRegistry.registerComponent(ArcaneConfiguratorComponent.class, ArcaneConfiguratorComponent::new);
        ArcaneConfiguratorComponent.setComponentType(arcaneConfiguratorComponentType);
    }

    private void registerCommands() {
        CommandRegistry registry = this.getCommandRegistry();
        
        registry.registerCommand(new ArcaneRelayCommandCollection());
    }
}
