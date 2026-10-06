package com.pseudoelephant.arcanerelay.features.activation.interactions;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.meta.MetaKey;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.pseudoelephant.arcanerelay.ArcaneRelayPlugin;
import com.pseudoelephant.arcanerelay.core.activation.ArcaneCachedAccessor;
import com.pseudoelephant.arcanerelay.core.adapters.EntityStoreChunkStoreAdapter;
import com.pseudoelephant.arcanerelay.features.activation.Activation;
import com.pseudoelephant.arcanerelay.features.signal.components.ArcaneSection;
import com.pseudoelephant.arcanerelay.features.signal.util.ArcaneUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3i;

/**
 * Runs the arcane Activation for the target block (from bindings).
 * When used in a block's Use chain, the target is the block being used.
 * Executes the activation immediately (e.g. Pusher_Chain for the pusher).
 */
public class ArcaneActivatorInteraction extends SimpleInstantInteraction {
    @Nonnull
    public static final BuilderCodec<ArcaneActivatorInteraction> CODEC = BuilderCodec.builder(
            ArcaneActivatorInteraction.class, ArcaneActivatorInteraction::new, SimpleInstantInteraction.CODEC)
            .documentation("ArcaneRelay: run the arcane Activation for the target block.")
            .append(new KeyedCodec<>("Activator", Codec.STRING, true), (i, a) -> i.activator = a, i -> i.activator)
            .add()
            .build();

    @Nullable
    private String activator;

    public ArcaneActivatorInteraction() {
    }

    public ArcaneActivatorInteraction(String id) {
        super(id);
    }

    /** Activation ID to run. When null/empty, uses the block's binding. */
    @Nullable
    public String getActivator() {
        return activator;
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
        CommandBuffer<EntityStore> cb = context.getCommandBuffer();
        if (cb == null) return;

        Ref<EntityStore> ref = context.getEntity();
        PlayerRef playerRef = cb.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;
 
        Vector3i coords = getTargetCoordinates(context, cb, ref, playerRef);
        if (coords == null) return;

        World world = cb.getExternalData().getWorld();
        Activation activation = resolveActivation(context, world, playerRef, coords);
        if (activation == null) return;

        executeActivation(cb, world, activation, coords);
        setFinished(context);
    }

    private Vector3i getTargetCoordinates(@Nonnull InteractionContext context, @Nonnull CommandBuffer<EntityStore> cb, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef) {
        MetaKey<BlockPosition> metaKey = Interaction.TARGET_BLOCK_RAW;
        BlockPosition targetRaw = metaKey != null ? context.getMetaStore().getMetaObject(metaKey) : null;
        if (targetRaw != null) {
            return new Vector3i(targetRaw.x, targetRaw.y, targetRaw.z);
        } 

        int interactionDistance = ArcaneRelayPlugin.get().getConfig().getRelayDistance();
        var target = TargetUtil.getTargetBlock(ref, interactionDistance, cb);

        if (target == null) {
            setFailed(context);
            return null;
        }

        return new Vector3i(target.x, target.y, target.z); 
    }

    private Activation resolveActivation(@Nonnull InteractionContext context, @Nonnull World world, @Nonnull PlayerRef playerRef, @Nonnull Vector3i coords) {
        var blockType = world.getBlockType(coords.x, coords.y, coords.z);
        if (blockType == null) {
            setFailed(context);
            return null;
        }

        String activator = this.activator;
        Activation activation = (activator != null && !activator.isEmpty())
                ? Activation.getActivation(activator)
                : ArcaneUtil.getActivationForBlock(blockType);
        
        if (activation == null) {
            setFailed(context);
            return null;
        }

        return activation;
    }

    private void executeActivation(@Nonnull CommandBuffer<EntityStore> cb, @Nonnull World world, @Nonnull Activation activation, @Nonnull Vector3i coordsBlock) {
        Store<ChunkStore> store = world.getChunkStore().getStore();
        Ref<ChunkStore> sectionRef = world.getChunkStore().getChunkSectionReference(
            ChunkUtil.chunkCoordinate(coordsBlock.x),
            ChunkUtil.chunkCoordinate(coordsBlock.y),
            ChunkUtil.chunkCoordinate(coordsBlock.z)
        );

        if (sectionRef == null) return;

        var chunkSectionComponent = ChunkSection.getComponentType();
        var arcaneSectionComponent = ArcaneSection.getComponentType();
        var blockSectionComponent = BlockSection.getComponentType();
        var blockComponentSectionComponent = BlockComponentSection.getComponentType();

        if (chunkSectionComponent == null || arcaneSectionComponent == null 
            || blockSectionComponent == null || blockComponentSectionComponent == null) {
            return;
        }

        ChunkSection chunkSection = store.getComponent(sectionRef, chunkSectionComponent);
        ArcaneSection arcaneSection = store.getComponent(sectionRef, arcaneSectionComponent);
        BlockSection blockSection = store.getComponent(sectionRef, blockSectionComponent);
        
        if (chunkSection == null || arcaneSection == null || blockSection == null) return;

        BlockComponentSection blockComponentSection = store.getComponent(sectionRef, blockComponentSectionComponent);
        Ref<ChunkStore> blockRef = blockComponentSection != null
            ? blockComponentSection.getBlockReference(ChunkUtil.indexBlock(coordsBlock.x, coordsBlock.y, coordsBlock.z))
            : null;

        ArcaneCachedAccessor accessor = new ArcaneCachedAccessor();
        accessor.init(new EntityStoreChunkStoreAdapter(cb), arcaneSection, blockSection, chunkSection, 1);

        List<int[]> sources = new ArrayList<>();
        activation.execute(accessor, sectionRef, blockRef, coordsBlock.x, coordsBlock.y, coordsBlock.z, sources);
    }

    private void setFailed(InteractionContext context) {
        context.getState().state = InteractionState.Failed;
    }

    private void setFinished(InteractionContext context) {
        context.getState().state = InteractionState.Finished;
    }
}
