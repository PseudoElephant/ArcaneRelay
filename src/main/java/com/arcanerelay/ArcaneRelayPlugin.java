package com.arcanerelay;

import com.arcanerelay.config.Activation;
import com.arcanerelay.config.ActivationBinding;
import com.arcanerelay.config.types.ArcanePullerActivation;
import com.arcanerelay.config.types.ChainActivation;
import com.arcanerelay.config.types.MoveBlockActivation;
import com.arcanerelay.config.types.SendSignalActivation;
import com.arcanerelay.config.types.ToggleDoorActivation;
import com.arcanerelay.config.types.ToggleStateActivation;
import com.arcanerelay.components.ArcaneConfiguratorComponent;
import com.arcanerelay.components.ArcanePullerBlock;
import com.arcanerelay.components.ArcaneSection;
import com.arcanerelay.components.ArcaneStaffLegendVisible;
import com.arcanerelay.components.ArcaneTriggerBlock;
import com.arcanerelay.interactions.AddOutputInteraction;
import com.arcanerelay.interactions.ArcaneActivatorInteraction;
import com.arcanerelay.interactions.SelectTriggerInteraction;
import com.arcanerelay.interactions.SendSignalInteraction;
import com.arcanerelay.resources.ArcaneMoveState;
import com.arcanerelay.resources.CustomHudRestoreState;
import com.arcanerelay.systems.ArcaneConfiguratorAddSystem;
import com.arcanerelay.systems.ArcaneStaffHudSystem;
import com.arcanerelay.systems.ArcaneSystems;
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
    private ComponentType<EntityStore, ArcaneStaffLegendVisible> arcaneStaffLegendVisibleComponentType;
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
        mainThread = Thread.currentThread();

        ComponentRegistryProxy<ChunkStore> chunkRegistry = this.getChunkStoreRegistry();
        this.arcaneMoveStateResourceType = chunkRegistry.registerResource(ArcaneMoveState.class, ArcaneMoveState::new);
        this.arcaneTriggerBlockComponentType = chunkRegistry.registerComponent(ArcaneTriggerBlock.class,
                "ArcaneTrigger", ArcaneTriggerBlock.CODEC);
        this.arcaneSectionComponentType = chunkRegistry.registerComponent(ArcaneSection.class, "ArcaneSection",
                ArcaneSection.CODEC);
        this.arcanePullerBlockComponentType = chunkRegistry.registerComponent(ArcanePullerBlock.class,
                "ArcanePuller", ArcanePullerBlock.CODEC);

        chunkRegistry.registerSystem(new ArcaneSystems.EnsureArcaneSection());
        chunkRegistry.registerSystem(new ArcaneSystems.PreTick());
        chunkRegistry.registerSystem(new ArcaneSystems.Ticking());
        chunkRegistry.registerSystem(new ArcaneSystems.MoveBlock());

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

        Activation.registerAssetStore();
        ActivationBinding.registerAssetStore();

        this.getCodecRegistry(Activation.CODEC)
                .register("ToggleState", ToggleStateActivation.class, ToggleStateActivation.CODEC)
                .register("SendSignal", SendSignalActivation.class, SendSignalActivation.CODEC)
                .register("MoveBlock", MoveBlockActivation.class, MoveBlockActivation.CODEC)
                .register("ArcanePuller", ArcanePullerActivation.class, ArcanePullerActivation.CODEC)
                .register("Chain", ChainActivation.class, ChainActivation.CODEC)
                .register("ToggleDoor", ToggleDoorActivation.class, ToggleDoorActivation.CODEC);

        this.getEventRegistry().registerGlobal(BootEvent.class, event -> ActivationBinding.onBindingsLoaded());
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
    public ComponentType<EntityStore, ArcaneStaffLegendVisible> getArcaneStaffLegendVisibleComponentType() {
        return arcaneStaffLegendVisibleComponentType;
    }

    public ComponentType<ChunkStore, ArcaneTriggerBlock> getArcaneTriggerBlockComponentType() {
        return this.arcaneTriggerBlockComponentType;
    }

    @Nonnull
    public ResourceType<EntityStore, CustomHudRestoreState> getCustomHudRestoreStateResourceType() {
        return customHudRestoreStateResourceType;
    }

    @Nonnull
    public ComponentType<ChunkStore, ArcaneSection> getArcaneSectionComponentType() {
        return this.arcaneSectionComponentType;
    }

    @Nonnull
    public ComponentType<ChunkStore, ArcanePullerBlock> getArcanePullerBlockComponentType() {
        return this.arcanePullerBlockComponentType;
    }
}
