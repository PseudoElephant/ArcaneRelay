package com.pseudoelephant.arcanerelay.features.signal.systems;

import java.util.Set;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.pseudoelephant.arcanerelay.features.signal.components.ArcaneSection;

public class EnsureArcaneSectionSystem extends HolderSystem<ChunkStore> {
    @Nonnull
    private static final Query<ChunkStore> QUERY = Query.and(ChunkSection.getComponentType(), Query.not(ArcaneSection.getComponentType()));

    @Override
    public void onEntityAdd(@Nonnull Holder<ChunkStore> holder, @Nonnull AddReason reason, @Nonnull Store<ChunkStore> store) {
        ComponentType<ChunkStore, ArcaneSection> arcaneSectionType = ArcaneSection.getComponentType();
        if (arcaneSectionType == null) return;

        holder.ensureComponent(arcaneSectionType);
    }

    @Override
    public void onEntityRemoved(@Nonnull Holder<ChunkStore> holder, @Nonnull RemoveReason reason, @Nonnull Store<ChunkStore> store) {
        ComponentType<ChunkStore, ArcaneSection> arcaneSectionType = ArcaneSection.getComponentType();
        if (arcaneSectionType == null) return;

        holder.removeComponent(arcaneSectionType);
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return QUERY;
    }

    @Nonnull
    @Override
    public Set<Dependency<ChunkStore>> getDependencies() {
        return RootDependency.firstSet();
    }
}
