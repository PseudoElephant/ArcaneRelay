package com.pseudoelephant.arcanerelay.features.signal.systems;

import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.pseudoelephant.arcanerelay.features.signal.components.ArcaneSection;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;

import java.time.Instant;
import java.util.Set;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.builtin.blocktick.system.ChunkBlockTickSystem;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;

public class PreTickSignalPropagationSystem extends EntityTickingSystem<ChunkStore> {
        @Nonnull
        private static final Query<ChunkStore> QUERY = Query.and(ChunkSection.getComponentType(), ArcaneSection.getComponentType());
        
        @SuppressWarnings("null")
        @Nonnull
        private static final Set<Dependency<ChunkStore>> DEPENDENCIES = Set.of(
           new SystemDependency<>(Order.AFTER, ChunkBlockTickSystem.PreTick.class), new SystemDependency<>(Order.BEFORE, ChunkBlockTickSystem.Ticking.class)
        );

        @Nonnull
        @Override
        public Set<Dependency<ChunkStore>> getDependencies() {
            return DEPENDENCIES;
        }

        public PreTickSignalPropagationSystem() {
        }

        @Nonnull
        @Override
        public Query<ChunkStore> getQuery() {
            return QUERY;
        }

        @Override
        public boolean isParallel(int archetypeChunkSize, int taskCount) {
            return EntityTickingSystem.maybeUseParallel(archetypeChunkSize, taskCount);
        }

        @Override
        public void tick(float dt, int index, @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk, @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
            Instant time = commandBuffer.getExternalData().getWorld().getEntityStore().getStore().getResource(WorldTimeResource.getResourceType()).getGameTime();

            Ref<ChunkStore> sectionRef = archetypeChunk.getReferenceTo(index);
            if (sectionRef == null) return;
            
            ArcaneSection arcaneSection = commandBuffer.getComponent(sectionRef, ArcaneSection.getComponentType());
            if (arcaneSection == null) return;

            arcaneSection.preTick(time);
        }
    }
