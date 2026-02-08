package com.arcanerelay;

import com.arcanerelay.api.BlockActivationHandler;
import com.arcanerelay.api.BlockTypeMatcher;
import com.arcanerelay.asset.Activation;
import com.arcanerelay.asset.ActivationBinding;
import com.arcanerelay.asset.ActivationBindingRegistry;
import com.arcanerelay.asset.ActivationRegistry;
import com.arcanerelay.asset.types.ArcaneDischargeActivation;
import com.arcanerelay.asset.types.ChainActivation;
import com.arcanerelay.asset.types.MoveBlockActivation;
import com.arcanerelay.asset.types.SendSignalActivation;
import com.arcanerelay.asset.types.ToggleDoorActivation;
import com.arcanerelay.asset.types.ToggleStateActivation;
import com.arcanerelay.core.UseBlockActivationHandler;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.AssetMap;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.event.events.BootEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.arcanerelay.components.ArcaneConfiguratorComponent;
import com.arcanerelay.components.ArcaneMoveBlock;
import com.arcanerelay.components.ArcaneStaffLegendVisible;
import com.arcanerelay.components.ArcaneTriggerBlock;
import com.arcanerelay.interactions.AddOutputInteraction;
import com.arcanerelay.interactions.ArcaneActivatorInteraction;
import com.arcanerelay.interactions.SelectTriggerInteraction;
import com.arcanerelay.interactions.SendSignalInteraction;
import com.arcanerelay.systems.ArcaneConfiguratorAddSystem;
import com.arcanerelay.systems.ArcaneStaffHudSystem;
import com.arcanerelay.ui.ArcaneTriggerPageSupplier;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.arcanerelay.state.ArcaneState;
import com.arcanerelay.state.ArcaneMoveState;
import com.arcanerelay.state.CustomHudRestoreState;
import com.arcanerelay.systems.ArcaneOnPlaceSystem;
import com.arcanerelay.systems.ArcaneTickSystem;
import com.hypixel.hytale.component.ResourceType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** ArcaneRelay - minimal Hytale mod with one example of each component type. */
public class ArcaneRelayPlugin extends JavaPlugin {

    private static ArcaneRelayPlugin instance;
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final Map<Integer, BlockActivationHandler> blockActivationHandlers = new ConcurrentHashMap<>();
    private final List<MatcherEntry> matcherHandlers = new CopyOnWriteArrayList<>();
    private ComponentType<ChunkStore, ArcaneTriggerBlock> arcaneTriggerBlockComponentType;
    private ComponentType<ChunkStore, ArcaneMoveBlock> arcaneMoveBlockComponentType;
    private ComponentType<EntityStore, ArcaneConfiguratorComponent> arcaneConfiguratorComponentType;
    private ComponentType<EntityStore, ArcaneStaffLegendVisible> arcaneStaffLegendVisibleComponentType;
    private ResourceType<ChunkStore, ArcaneState> arcaneStateResourceType;
    private ResourceType<EntityStore, CustomHudRestoreState> customHudRestoreStateResourceType;
    private ResourceType<ChunkStore, ArcaneMoveState> arcaneMoveStateResourceType;
    private BlockActivationHandler defaultBlockActivationHandler;
    private final ActivationBindingRegistry activationBindingRegistry = new ActivationBindingRegistry();
    private final ActivationRegistry activationRegistry = new ActivationRegistry(activationBindingRegistry);

    public static ArcaneRelayPlugin get() {
        return instance;
    }

    @Nonnull
    public ComponentType<EntityStore, ArcaneConfiguratorComponent> getArcaneConfiguratorComponentType() {
        return arcaneConfiguratorComponentType;
    }

    @Nonnull
    public ComponentType<EntityStore, ArcaneStaffLegendVisible> getArcaneStaffLegendVisibleComponentType() {
        return arcaneStaffLegendVisibleComponentType;
    }

    public ArcaneRelayPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        instance = this;
        LOGGER.atInfo().log("ArcaneRelay setting up");

        ComponentRegistryProxy<ChunkStore> chunkRegistry = this.getChunkStoreRegistry();
        this.arcaneStateResourceType = chunkRegistry.registerResource(ArcaneState.class, ArcaneState::new);
        this.arcaneMoveStateResourceType = chunkRegistry.registerResource(ArcaneMoveState.class, ArcaneMoveState::new);
        this.arcaneTriggerBlockComponentType = chunkRegistry.registerComponent(ArcaneTriggerBlock.class, "ArcaneTrigger", ArcaneTriggerBlock.CODEC);
        this.arcaneMoveBlockComponentType = chunkRegistry.registerComponent(ArcaneMoveBlock.class, "ArcaneMove", ArcaneMoveBlock.CODEC);
        chunkRegistry.registerSystem(new ArcaneTickSystem());
        chunkRegistry.registerSystem(new ArcaneOnPlaceSystem());

        ComponentRegistryProxy<EntityStore> entityRegistry = this.getEntityStoreRegistry();
        this.arcaneConfiguratorComponentType = entityRegistry.registerComponent(ArcaneConfiguratorComponent.class, ArcaneConfiguratorComponent::new);
        this.arcaneStaffLegendVisibleComponentType = entityRegistry.registerComponent(ArcaneStaffLegendVisible.class, ArcaneStaffLegendVisible::new);
        this.customHudRestoreStateResourceType = entityRegistry.registerResource(CustomHudRestoreState.class, CustomHudRestoreState::new);
        entityRegistry.registerSystem(new ArcaneConfiguratorAddSystem());
        entityRegistry.registerSystem(new ArcaneStaffHudSystem());

        Interaction.CODEC.register("SelectTrigger", SelectTriggerInteraction.class, SelectTriggerInteraction.CODEC);
        Interaction.CODEC.register("AddOutput", AddOutputInteraction.class, AddOutputInteraction.CODEC);
        Interaction.CODEC.register("SendSignal", SendSignalInteraction.class, SendSignalInteraction.CODEC);
        Interaction.CODEC.register("ArcaneActivator", ArcaneActivatorInteraction.class, ArcaneActivatorInteraction.CODEC);


        this.getCodecRegistry(OpenCustomUIInteraction.PAGE_CODEC)
            .register("ArcaneTrigger", ArcaneTriggerPageSupplier.class, ArcaneTriggerPageSupplier.CODEC);

        ActivationRegistry.registerAssetStore(this);
        registerActivationBindingsAssetStore();
        this.getCodecRegistry(Activation.CODEC)
            .register("ToggleState", ToggleStateActivation.class, ToggleStateActivation.CODEC)
            .register("SendSignal", SendSignalActivation.class, SendSignalActivation.CODEC)
            .register("ArcaneDischarge", ArcaneDischargeActivation.class, ArcaneDischargeActivation.CODEC)
            .register("MoveBlock", MoveBlockActivation.class, MoveBlockActivation.CODEC)
            .register("Chain", ChainActivation.class, ChainActivation.CODEC)
            .register("ToggleDoor", ToggleDoorActivation.class, ToggleDoorActivation.CODEC);

        this.getEventRegistry().registerGlobal(BootEvent.class, event -> {
            this.activationRegistry.onAssetsLoaded();
            this.setupActivationBindings();
            this.setupBlockActivationHandlers();
        });

        LOGGER.atInfo().log("ArcaneRelay setup complete");
    }

    @SuppressWarnings("unchecked")
    private void registerActivationBindingsAssetStore() {
        HytaleAssetStore.Builder<String, ActivationBinding, DefaultAssetMap<String, ActivationBinding>> b =
            (HytaleAssetStore.Builder<String, ActivationBinding, DefaultAssetMap<String, ActivationBinding>>)
                (Object) HytaleAssetStore.builder(ActivationBinding.class, new DefaultAssetMap<>());
        AssetRegistry.register(
            b.setPath("Item/ActivationBindings")
                .setCodec(ActivationBinding.CODEC)
                .setKeyFunction(ActivationBinding::getId)
                .loadsAfter(Activation.class)
                .build()
        );
    }

    private void setupActivationBindings() {
        ActivationBindingRegistry registry = this.activationBindingRegistry;
        AssetStore<String, ActivationBinding, ? extends AssetMap<String, ActivationBinding>> store =
            AssetRegistry.getAssetStore(ActivationBinding.class);
        if (store == null) return;
        AssetMap<String, ActivationBinding> map = store.getAssetMap();
        if (map == null) return;
        // Process default binding last; others sorted by priority (higher first), then by id for stability
        var bindings = new ArrayList<>(map.getAssetMap().values());
        bindings.sort((a, b) -> {
            if (a.isDefaultBinding()) return 1;
            if (b.isDefaultBinding()) return -1;
            int cmp = Integer.compare(b.getPriority(), a.getPriority());
            if (cmp != 0) return cmp;
            return a.getId().compareTo(b.getId());
        });
        for (ActivationBinding binding : bindings) {
            if (binding.isDefaultBinding()) {
                registry.setDefaultActivationId(binding.getActivation());
            } else if (binding.getPattern() != null && !binding.getPattern().isBlank()) {
                registry.registerBinding(binding.getPattern(), binding.getActivation());
            }
        }
    }

    private void setupBlockActivationHandlers() {
        this.defaultBlockActivationHandler = new UseBlockActivationHandler();
    }
    public ComponentType<ChunkStore, ArcaneTriggerBlock> getArcaneTriggerBlockComponentType() {
        return this.arcaneTriggerBlockComponentType;
    }

    public ComponentType<ChunkStore, ArcaneMoveBlock> getArcaneMoveBlockComponentType()  {
        return this.arcaneMoveBlockComponentType;
    }

    @Nonnull
    public ResourceType<ChunkStore, ArcaneState> getArcaneStateResourceType() {
        return this.arcaneStateResourceType;
    }

    @Nonnull
    public ResourceType<EntityStore, CustomHudRestoreState> getCustomHudRestoreStateResourceType() {
        return customHudRestoreStateResourceType;
    }

    @Nonnull
    public ResourceType<ChunkStore, ArcaneMoveState> getArcaneMoveStateResourceType() {
        return this.arcaneMoveStateResourceType;
    }

    // ─── Block Activation API (for other plugins) ───────────────────────────────────────────────

    /**
     * Registers a handler for arcane activation of a block type by ID.
     * When a signal reaches this block, the handler runs without requiring a player nearby.
     *
     * @param blockTypeId the block type ID (e.g. from {@link BlockType#getAssetMap()}{@code .getIndex(key)})
     * @param handler     the activation handler
     */
    public void registerBlockActivationHandler(int blockTypeId, @Nonnull BlockActivationHandler handler) {
        this.blockActivationHandlers.put(blockTypeId, handler);
    }

    /**
     * Registers a handler for arcane activation of a block type by string key.
     *
     * @param blockTypeKey the block type key (e.g. "Hytale:Redstone_Torch", {@link BlockType#getId()})
     * @param handler      the activation handler
     * @return true if the block type was found and registered, false if unknown
     */
    public boolean registerBlockActivationHandler(@Nonnull String blockTypeKey, @Nonnull BlockActivationHandler handler) {
        var assetMap = BlockType.getAssetMap();
        int blockTypeId = assetMap.getIndex(blockTypeKey);
        if (blockTypeId == Integer.MIN_VALUE) {
            LOGGER.atWarning().log("ArcaneRelay: unknown block type '%s', handler not registered", blockTypeKey);
            return false;
        }
        this.blockActivationHandlers.put(blockTypeId, handler);
        return true;
    }

    /**
     * Registers a handler for arcane activation of a block type.
     *
     * @param blockType the block type
     * @param handler   the activation handler
     */
    public void registerBlockActivationHandler(@Nonnull BlockType blockType, @Nonnull BlockActivationHandler handler) {
        int blockTypeId = BlockType.getAssetMap().getIndex(blockType.getId());
        if (blockTypeId != Integer.MIN_VALUE) {
            this.blockActivationHandlers.put(blockTypeId, handler);
        }
    }

    /**
     * Registers a handler for arcane activation using a pattern matcher.
     * This allows registering a single handler for multiple block types that match the pattern.
     * Pattern matchers are checked after exact ID matches.
     *
     * @param matcher the matcher to use for block type keys
     * @param handler the activation handler
     * @see BlockTypeMatcher for available matcher factory methods
     */
    public void registerBlockActivationHandler(@Nonnull BlockTypeMatcher matcher, @Nonnull BlockActivationHandler handler) {
        this.matcherHandlers.add(new MatcherEntry(matcher, handler));
    }

    /**
     * Convenience method to register a handler for all block types containing a substring.
     *
     * @param substring the substring to match (case-insensitive)
     * @param handler   the activation handler
     */
    public void registerBlockActivationHandlerContaining(@Nonnull String substring, @Nonnull BlockActivationHandler handler) {
        this.registerBlockActivationHandler(BlockTypeMatcher.contains(substring), handler);
    }

    /**
     * Convenience method to register a handler for all block types starting with a prefix.
     *
     * @param prefix  the prefix to match (case-insensitive)
     * @param handler the activation handler
     */
    public void registerBlockActivationHandlerStartingWith(@Nonnull String prefix, @Nonnull BlockActivationHandler handler) {
        this.registerBlockActivationHandler(BlockTypeMatcher.startsWith(prefix), handler);
        LOGGER.atInfo().log("ArcaneRelay: registered block activation handler for blocks starting with: " + prefix);
    }

    /**
     * Convenience method to register a handler for all block types ending with a suffix.
     *
     * @param suffix  the suffix to match (case-insensitive)
     * @param handler the activation handler
     */
    public void registerBlockActivationHandlerEndingWith(@Nonnull String suffix, @Nonnull BlockActivationHandler handler) {
        this.registerBlockActivationHandler(BlockTypeMatcher.endsWith(suffix), handler);
    }

    /**
     * Convenience method to register a handler for all block types matching a regex pattern.
     *
     * @param regex   the regex pattern (finds match anywhere in key)
     * @param handler the activation handler
     */
    public void registerBlockActivationHandlerMatching(@Nonnull String regex, @Nonnull BlockActivationHandler handler) {
        this.registerBlockActivationHandler(BlockTypeMatcher.regexFind(regex), handler);
    }

    /**
     * Returns the handler for a block type ID, or null if none registered.
     * Only checks exact ID matches - use {@link #getBlockActivationHandler(int, String)} for pattern matching.
     */
    @Nullable
    public BlockActivationHandler getBlockActivationHandler(int blockTypeId) {
        return this.blockActivationHandlers.get(blockTypeId);
    }

    /**
     * Returns the handler for a block type, checking both exact ID matches and pattern matchers.
     * Exact ID matches take priority over pattern matchers.
     *
     * @param blockTypeId  the block type ID
     * @param blockTypeKey the block type key for pattern matching
     * @return the handler, or null if none matches
     */
    @Nullable
    public BlockActivationHandler getBlockActivationHandler(int blockTypeId, @Nonnull String blockTypeKey) {
        // First check exact ID match
        BlockActivationHandler handler = this.blockActivationHandlers.get(blockTypeId);
        if (handler != null) {
            return handler;
        }

        // Then check pattern matchers
        for (MatcherEntry entry : this.matcherHandlers) {
            if (entry.matcher.matches(blockTypeKey)) {
                return entry.handler;
            }
        }

        // Default: run the block's Use interaction
        return this.defaultBlockActivationHandler;
    }

    @Nonnull
    public ActivationRegistry getActivationRegistry() {
        return activationRegistry;
    }

    /**
     * Returns the activation binding registry for adding bindings from plugins or config.
     */
    @Nonnull
    public ActivationBindingRegistry getActivationBindingRegistry() {
        return activationBindingRegistry;
    }

    /**
     * Returns all handlers that match a block type key (for debugging/inspection).
     *
     * @param blockTypeKey the block type key to check
     * @return list of matching handlers (may be empty)
     */
    @Nonnull
    public List<BlockActivationHandler> getAllMatchingHandlers(@Nonnull String blockTypeKey) {
        List<BlockActivationHandler> result = new ArrayList<>();
        
        // Check exact match by key
        var assetMap = BlockType.getAssetMap();
        int blockTypeId = assetMap.getIndex(blockTypeKey);
        if (blockTypeId != Integer.MIN_VALUE) {
            BlockActivationHandler exactHandler = this.blockActivationHandlers.get(blockTypeId);
            if (exactHandler != null) {
                result.add(exactHandler);
            }
        }

        // Check pattern matchers
        for (MatcherEntry entry : this.matcherHandlers) {
            if (entry.matcher.matches(blockTypeKey)) {
                result.add(entry.handler);
            }
        }

        return result;
    }

    /**
     * Unregisters the handler for a block type.
     *
     * @return the previous handler, or null if none was registered
     */
    @Nullable
    public BlockActivationHandler unregisterBlockActivationHandler(int blockTypeId) {
        return this.blockActivationHandlers.remove(blockTypeId);
    }

    /**
     * Clears all pattern-based handlers.
     */
    public void clearMatcherHandlers() {
        this.matcherHandlers.clear();
    }

    // ─── Inner Classes ───────────────────────────────────────────────────────────────────────────

    private static final class MatcherEntry {
        final BlockTypeMatcher matcher;
        final BlockActivationHandler handler;

        MatcherEntry(@Nonnull BlockTypeMatcher matcher, @Nonnull BlockActivationHandler handler) {
            this.matcher = matcher;
            this.handler = handler;
        }
    }
}
