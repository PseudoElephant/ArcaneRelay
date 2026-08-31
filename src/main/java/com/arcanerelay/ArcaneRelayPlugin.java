package com.arcanerelay;

import com.arcanerelay.commands.ArcaneRelayCommandCollection;
import com.arcanerelay.features.activation.Activation;
import com.arcanerelay.features.activation.ActivationBinding;
import com.arcanerelay.features.activation.interactions.ArcaneActivatorInteraction;
import com.arcanerelay.features.activation.types.ChainActivation;
import com.arcanerelay.features.activation.types.ToggleStateActivation;
import com.arcanerelay.features.blockmovement.activations.MoveBlockActivation;
import com.arcanerelay.features.blockmovement.activations.RotateBlockActivation;
import com.arcanerelay.features.blockmovement.resources.ArcaneMoveState;
import com.arcanerelay.features.blockmovement.systems.BlockMovementSystem;
import com.arcanerelay.features.blocks.breaker.activation.HitActivation;
import com.arcanerelay.features.blocks.doors.activation.ToggleDoorActivation;
import com.arcanerelay.features.blocks.puller.activations.ArcanePullerActivation;
import com.arcanerelay.features.blocks.puller.components.ArcanePullerBlock;
import com.arcanerelay.features.config.ArcaneRelayConfig;
import com.arcanerelay.features.configurator.components.ArcaneConfiguratorComponent;
import com.arcanerelay.features.configurator.interactions.AddOutputInteraction;
import com.arcanerelay.features.configurator.interactions.SelectTriggerInteraction;
import com.arcanerelay.features.configurator.listeners.InventorySetActiveSlotEventHandler;
import com.arcanerelay.features.configurator.systems.ArcaneConfiguratorAddSystem;
import com.arcanerelay.features.configurator.systems.VisualSelectionSystem;
import com.arcanerelay.features.signal.components.ArcaneSection;
import com.arcanerelay.features.signal.systems.EnsureArcaneSectionSystem;
import com.arcanerelay.features.signal.systems.PreTickSignalPropagationSystem;
import com.arcanerelay.features.signal.systems.TickingSignalPropagationSystem;
import com.arcanerelay.features.signaltrigger.activation.SendSignalActivation;
import com.arcanerelay.features.signaltrigger.components.ArcaneTriggerBlock;
import com.arcanerelay.features.signaltrigger.interactions.SendSignalInteraction;
import com.arcanerelay.features.signaltrigger.ui.ArcaneTriggerPageSupplier;
import com.arcanerelay.features.triggervolume.ArcaneRelayEffect;
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

import javax.annotation.Nonnull;

public class ArcaneRelayPlugin extends JavaPlugin {
    private final Config<ArcaneRelayConfig> config = this.withConfig("ArcaneRelayConfig", ArcaneRelayConfig.CODEC);

    private static ArcaneRelayPlugin instance;
    /** Thread that ran plugin setup(); used to detect main thread for world.execute() etc. */
    private static Thread mainThread;
    public static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Returns the main/game thread (the one that ran plugin setup()). */
    public static Thread getMainThread() {
        return mainThread;
    }

    /** True if the current thread is the same one that ran plugin setup() (main/game thread). */
    public static boolean isMainThread() {
        return mainThread != null && Thread.currentThread() == mainThread;
    }

    private ComponentType<ChunkStore, ArcaneTriggerBlock> arcaneTriggerBlockComponentType;
    private ComponentType<ChunkStore, ArcaneSection> arcaneSectionComponentType;
    private ComponentType<ChunkStore, ArcanePullerBlock> arcanePullerBlockComponentType;
    private ComponentType<EntityStore, ArcaneConfiguratorComponent> arcaneConfiguratorComponentType;
    private ResourceType<ChunkStore, ArcaneMoveState> arcaneMoveStateResourceType;

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
        mainThread = Thread.currentThread();

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

    @Nonnull
    public ComponentType<EntityStore, ArcaneConfiguratorComponent> getArcaneConfiguratorComponentType() {
        return arcaneConfiguratorComponentType;
    }

    @Nonnull
    public ResourceType<ChunkStore, ArcaneMoveState> getArcaneMoveStateResourceType() {
        return this.arcaneMoveStateResourceType;
    }

    @Nonnull
    public ComponentType<ChunkStore, ArcaneTriggerBlock> getArcaneTriggerBlockComponentType() {
        return this.arcaneTriggerBlockComponentType;
    }

    @Nonnull
    public ComponentType<ChunkStore, ArcaneSection> getArcaneSectionComponentType() {
        return this.arcaneSectionComponentType;
    }

    @Nonnull
    public ComponentType<ChunkStore, ArcanePullerBlock> getArcanePullerBlockComponentType() {
        return this.arcanePullerBlockComponentType;
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

        this.arcaneMoveStateResourceType = chunkRegistry.registerResource(ArcaneMoveState.class, ArcaneMoveState::new);
    }

    private void registerEntityResources() {
        ComponentRegistryProxy<EntityStore> entityRegistry = this.getEntityStoreRegistry();
    }

    private void registerComponents() {
        registerChunkComponents();
        registerEntityComponents();
    }

    private void registerChunkComponents() {
        ComponentRegistryProxy<ChunkStore> chunkRegistry = this.getChunkStoreRegistry();

        this.arcaneTriggerBlockComponentType = chunkRegistry.registerComponent(ArcaneTriggerBlock.class, "ArcaneTrigger", ArcaneTriggerBlock.CODEC);
        this.arcaneSectionComponentType = chunkRegistry.registerComponent(ArcaneSection.class, "ArcaneSection", ArcaneSection.CODEC);
        this.arcanePullerBlockComponentType = chunkRegistry.registerComponent(ArcanePullerBlock.class, "ArcanePuller", ArcanePullerBlock.CODEC);
    }

    private void registerEntityComponents() {
        ComponentRegistryProxy<EntityStore> entityRegistry = this.getEntityStoreRegistry();

        this.arcaneConfiguratorComponentType = entityRegistry.registerComponent(ArcaneConfiguratorComponent.class, ArcaneConfiguratorComponent::new);
    }

    private void registerCommands() {
        CommandRegistry registry = this.getCommandRegistry();
        
        registry.registerCommand(new ArcaneRelayCommandCollection());
    }
}
