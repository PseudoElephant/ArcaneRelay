package com.pseudoelephant.arcanerelay.features.signal.components;

import java.time.Instant;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.StampedLock;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.common.util.BitSetUtil;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.function.predicate.ObjectPositionBlockFunction;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.pseudoelephant.arcanerelay.ArcaneRelayPlugin;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.ObjectHeapPriorityQueue;

/**
 * Tracks ticking blocks and their sources. Source is stored as an index into a section-local pool
 * (latest only per block). Snapshot at preTick for read during forEachTicking.
 */
public class ArcaneSection implements Component<ChunkStore> {
    private static ComponentType<ChunkStore, ArcaneSection> componentType;
    
    public static final int VERSION = 1;
    private static final int MAX_SOURCE_POOL_SIZE = 256;

    @Nonnull 
    public static final BuilderCodec<ArcaneSection> CODEC = BuilderCodec.builder(ArcaneSection.class, ArcaneSection::new)
        .versioned()
        .codecVersion(VERSION)
        .append(new KeyedCodec<>("Data", Codec.BYTE_ARRAY), ArcaneSection::deserialize, ArcaneSection::serialize)
        .add()
        .build();

    private final StampedLock arcaneSectionLock;
    private final ObjectHeapPriorityQueue<ArcaneSection.TickRequest> tickRequests;
    private static final Comparator<ArcaneSection.TickRequest> TICK_REQUEST_COMPARATOR = Comparator.comparing(t -> t.requestedGameTime);
    BitSet tickingBlocks;
    BitSet lastTickingBlocks;

    /** Source positions for this tick; block index -> pool index (byte). */
    private final List<int[]> pendingSourcePool = new ArrayList<>();
    private final Map<Integer, Byte> pendingBlockToSourceIndex = new HashMap<>();
    /** Snapshot at preTick; read-only during forEachTicking. */
    private List<int[]> lastSourcePool = new ArrayList<>();
    private Map<Integer, Byte> lastBlockToSourceIndex = new HashMap<>();

    public ArcaneSection() {
        this.arcaneSectionLock = new StampedLock();
        this.tickingBlocks = new BitSet();
        this.lastTickingBlocks = new BitSet();
        this.tickRequests = new ObjectHeapPriorityQueue<>(TICK_REQUEST_COMPARATOR);
    }
    
    @Nonnull
    public static ComponentType<ChunkStore, ArcaneSection> getComponentType() {
        return ArcaneSection.componentType;
    }

    public static void setComponentType(ComponentType<ChunkStore, ArcaneSection> componentType) {
        ArcaneSection.componentType = componentType;
    }

    public void scheduleTick(int index, @Nonnull Instant gameTime) {
        if (gameTime != null) {
            this.tickRequests.enqueue(new TickRequest(index, gameTime));
        }
    }

    public void preTick(@Nonnull Instant gameTime) {
        ArcaneSection.TickRequest request;
        while (!this.tickRequests.isEmpty() && (request = this.tickRequests.first()).requestedGameTime.isBefore(gameTime)) {
            this.tickRequests.dequeue();
            this.setTicking(request.index, true);
        }

        long writeStamp = this.arcaneSectionLock.writeLock();
        try {
            if (this.tickingBlocks.isEmpty() && this.lastTickingBlocks.isEmpty() && this.pendingBlockToSourceIndex.isEmpty()) {
                return;
            }
            BitSetUtil.copyValues(this.tickingBlocks, this.lastTickingBlocks);
            this.tickingBlocks.clear();
            this.lastSourcePool = new ArrayList<>(this.pendingSourcePool);
            this.lastBlockToSourceIndex = new HashMap<>(this.pendingBlockToSourceIndex);
            this.pendingSourcePool.clear();
            this.pendingBlockToSourceIndex.clear();
        } finally {
            this.arcaneSectionLock.unlockWrite(writeStamp);
        }
    }

    public <T, V> int forEachTicking(T t, V v, BlockSection section, int sectionIndex, @Nonnull ObjectPositionBlockFunction<T, V, BlockTickStrategy> acceptor) {
        int sectionStartYBlock = sectionIndex << 5;
        int ticked = 0;

        for(int index = this.lastTickingBlocks.nextSetBit(0); index >= 0; index = this.lastTickingBlocks.nextSetBit(index + 1)) {
            int x = ChunkUtil.xFromIndex(index);
            int y = ChunkUtil.yFromIndex(index);
            int z = ChunkUtil.zFromIndex(index);

            BlockTickStrategy strategy = acceptor.accept(t, v, x, y | sectionStartYBlock, z, section.get(index));
            switch (strategy) {
                case PROCESSED:
                    ticked++;
                    continue;
                case WAIT_FOR_ADJACENT_CHUNK_LOAD:
                case CONTINUE:
                        this.setTicking(index, true);
                        continue;
                default:
                    continue;
            }
        }
        return ticked;
    }

    public void setTicking(int x, int y, int z, boolean ticking) {
         this.setTicking(ChunkUtil.indexBlock(x, y, z), ticking);
    }

    /** Sets a block to ticking and records the source position (latest only) for when it is processed. */
    public void setTicking(int x, int y, int z, boolean ticking, int sourceX, int sourceY, int sourceZ) {
        int blockIndex = ChunkUtil.indexBlock(x, y, z);
        if (!setTicking(blockIndex, ticking)) return;
        int[] source = new int[] { sourceX, sourceY, sourceZ };
        long writeStamp = this.arcaneSectionLock.writeLock();
        try {
            int poolIndex = findOrAddSource(source);
            if (poolIndex >= 0 && poolIndex < MAX_SOURCE_POOL_SIZE) {
                this.pendingBlockToSourceIndex.put(blockIndex, (byte) poolIndex);
            }
        } finally {
            this.arcaneSectionLock.unlockWrite(writeStamp);
        }
    }

    private int findOrAddSource(int[] source) {
        for (int i = 0; i < pendingSourcePool.size(); i++) {
            int[] s = pendingSourcePool.get(i);
            if (s[0] == source[0] && s[1] == source[1] && s[2] == source[2]) return i;
        }
        if (pendingSourcePool.size() >= MAX_SOURCE_POOL_SIZE) return -1;
        pendingSourcePool.add(source);
        return pendingSourcePool.size() - 1;
    }

    /** Returns the latest source position for a block index (from preTick snapshot), or null. */
    @Nullable
    public int[] getLastSource(int blockIndex) {
        long readStamp = this.arcaneSectionLock.readLock();
        try {
            Byte idx = this.lastBlockToSourceIndex.get(blockIndex);
            if (idx == null) return null;
            int i = idx & 0xFF;
            if (i >= lastSourcePool.size()) return null;
            return lastSourcePool.get(i);
        } finally {
            this.arcaneSectionLock.unlockRead(readStamp);
        }
    }

    public boolean setTicking(int blockIndex, boolean ticking) {
        long stamp = this.arcaneSectionLock.readLock();
        try {
            if (this.tickingBlocks.get(blockIndex) == ticking) {
                return false;
            }
            
            long writeStamp = this.arcaneSectionLock.tryConvertToWriteLock(stamp);
            
            if (writeStamp != 0L) {
                stamp = writeStamp;
                this.tickingBlocks.set(blockIndex, ticking);
                return true;
            } else { 
                this.arcaneSectionLock.unlockRead(stamp);
                stamp = this.arcaneSectionLock.writeLock();
                
                if (this.tickingBlocks.get(blockIndex) == ticking) {
                    return false;
                }
                
                this.tickingBlocks.set(blockIndex, ticking);
                return true;
            }
        } finally {
            this.arcaneSectionLock.unlock(stamp);
        }
    }

    /** Serializes ticking state to the buffer (under read lock). */
    private void serialize(@Nonnull ByteBuf buf) {
        long lock = this.arcaneSectionLock.readLock();
        try {
            BitSet combined = (BitSet) this.tickingBlocks.clone();
            combined.or(this.lastTickingBlocks);
            buf.writeShort(combined.cardinality());
            long[] data = combined.toLongArray();
            buf.writeShort(data.length);
            for (long l : data) {
                buf.writeLong(l);
            }
        } finally {
            this.arcaneSectionLock.unlockRead(lock);
        }
    }

    /** Returns serialized payload for the codec. */
    public byte[] serialize(@Nonnull ExtraInfo extraInfo) {
        ByteBuf buf = ByteBufAllocator.DEFAULT.buffer();
        try {
            serialize(buf);
            return io.netty.buffer.ByteBufUtil.getBytes(buf, 0, buf.writerIndex(), false);
        } finally {
            buf.release();
        }
    }

    /** Deserializes ticking state from the buffer (sets tickingBlocks and lastTickingBlocks under write lock). */
    private void deserialize(@Nonnull ByteBuf buf, int version) {
        buf.readUnsignedShort(); // cardinality (format compatibility with BlockSection)
        int len = buf.readUnsignedShort();
        long[] data = new long[len];
        for (int i = 0; i < data.length; i++) {
            data[i] = buf.readLong();
        }
        BitSet restored = BitSet.valueOf(data);
        long writeStamp = this.arcaneSectionLock.writeLock();
        try {
            this.tickingBlocks = restored;
            this.lastTickingBlocks = (BitSet) restored.clone();
        } finally {
            this.arcaneSectionLock.unlockWrite(writeStamp);
        }
    }

    /** Entry point for codec: restore state from saved bytes. */
    public void deserialize(@Nonnull byte[] bytes, @Nonnull ExtraInfo extraInfo) {
        if (bytes == null || bytes.length == 0) {
            return;
        }
        ByteBuf buf = Unpooled.wrappedBuffer(bytes);
        deserialize(buf, extraInfo.getVersion());
    }
 
    @Override
    public Component<ChunkStore> clone() {
        ArcaneSection copy = new ArcaneSection();
        long readStamp = this.arcaneSectionLock.readLock();
        try {
            copy.tickingBlocks = (BitSet) this.tickingBlocks.clone();
            copy.lastTickingBlocks = (BitSet) this.lastTickingBlocks.clone();
        } finally {
            this.arcaneSectionLock.unlockRead(readStamp);
        }
        return copy;
    }

    public static enum BlockTickStrategy {
        PROCESSED,
        WAIT_FOR_ADJACENT_CHUNK_LOAD,
        CONTINUE;
     
        private BlockTickStrategy() {
        }
     }

    private record TickRequest(int index, @Nonnull Instant requestedGameTime) {
    }
}
