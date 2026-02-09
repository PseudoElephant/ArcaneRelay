package com.arcanerelay.state;

import com.arcanerelay.ArcaneRelayPlugin;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Per-world state for arcane block ticking: trigger positions with sources, and last run tick.
 * Uses {@link TriggerEntry} with optional skip flag (propagate only, do not activate).
 */
public class ArcaneState implements Resource<ChunkStore> {

   private final Deque<TriggerEntry> triggerEntries = new ArrayDeque<>();
   /** Signals from interactions (skip=true); flushed at start of tick, processed on next interval. */
   private final Deque<TriggerEntry> pendingNextTick = new ArrayDeque<>();
   private final AtomicLong lastRunTick = new AtomicLong(Long.MIN_VALUE);

   /** Adds a trigger for the next interval (from interactions). Has skip=true: propagate only, no activation. */
   public void addPendingNextTick(int x, int y, int z, int sourceX, int sourceY, int sourceZ) {
      synchronized (pendingNextTick) {
         pendingNextTick.addLast(TriggerEntry.of(x, y, z, sourceX, sourceY, sourceZ, true));
      }
   }

   /** Adds a trigger for the next interval with a specific activator (skip=false). Runs that activation in the tick system. */
   public void addPendingNextTick(int x, int y, int z, int sourceX, int sourceY, int sourceZ, @Nullable String activatorId) {
      synchronized (pendingNextTick) {
         pendingNextTick.addLast(TriggerEntry.of(x, y, z, sourceX, sourceY, sourceZ, false, activatorId));
      }
   }

   /**
    * Flushes pending triggers into the main queue. Call at start of each tick.
    * Pending entries are only processed when the next interval runs (at least 1 second).
    */
   public void flushPendingToTriggers() {
      List<TriggerEntry> toFlush;

      synchronized (pendingNextTick) {
         if (pendingNextTick.isEmpty()) return;

         toFlush = new ArrayList<>(pendingNextTick);
         pendingNextTick.clear();
      }

      synchronized (triggerEntries) {
         triggerEntries.addAll(toFlush);
      }
   }

   public static ResourceType<ChunkStore, ArcaneState> getResourceType() {
      return ArcaneRelayPlugin.get().getArcaneStateResourceType();
   }

   /** Adds a trigger (skip=false: normal activate + propagate). */
   public void addTrigger(int x, int y, int z, int sourceX, int sourceY, int sourceZ) {
      addTrigger(TriggerEntry.of(x, y, z, sourceX, sourceY, sourceZ, false));
   }

   public void addTrigger(int x, int y, int z) {
      addTrigger(x, y, z, x, y, z);
   }

   public void addTrigger(TriggerEntry entry) {
      synchronized (triggerEntries) {
         triggerEntries.addLast(entry);
      }
   }

   /** Removes a trigger at the given position. */
   public void removeTrigger(int x, int y, int z) {
      synchronized (triggerEntries) {
         triggerEntries.removeIf(e -> e.target().getX() == x && e.target().getY() == y && e.target().getZ() == z);
      }
   }

   public void clearTriggers() {
      synchronized (triggerEntries) {
         triggerEntries.clear();
      }
   }

   @Nonnull
   public List<TriggerEntry> copyTriggerEntries() {
      synchronized (triggerEntries) {
         return new ArrayList<>(triggerEntries);
      }
   }

   public boolean hasTriggers() {
      synchronized (triggerEntries) {
         return !triggerEntries.isEmpty();
      }
   }

   public long getLastRunTick() {
      return lastRunTick.get();
   }

   @Nonnull
   @Override
   public Resource<ChunkStore> clone() {
      ArcaneState clone = new ArcaneState();
      clone.lastRunTick.set(this.lastRunTick.get());
      
      synchronized (this.triggerEntries) {
         clone.triggerEntries.addAll(this.triggerEntries);
      }

      synchronized (this.pendingNextTick) {
         clone.pendingNextTick.addAll(this.pendingNextTick);
      }

      return clone;
   }
}
