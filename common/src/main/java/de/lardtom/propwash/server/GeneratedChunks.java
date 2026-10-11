package de.lardtom.propwash.server;

import de.lardtom.propwash.Constants;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.visitors.CollectFields;
import net.minecraft.nbt.visitors.FieldSelector;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

final class GeneratedChunks implements GeneratedArea.Lookup {
    private static final byte UNKNOWN = 0;
    private static final byte GENERATED = 1;
    private static final byte MISSING = 2;
    private static final byte QUEUED = 3;
    private static final int MAX_CACHED = 262_144;
    private static final int FORGET_MISSING_INTERVAL = 600;

    private final ServerLevel level;
    private final Long2ByteOpenHashMap states = new Long2ByteOpenHashMap();
    private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
    private final ConcurrentLinkedQueue<Result> results = new ConcurrentLinkedQueue<>();
    private int inFlight;
    private int version;
    private int ticksSinceForget;

    GeneratedChunks(ServerLevel level) {
        this.level = level;
    }

    @Override
    public GeneratedArea.Status status(int chunkX, int chunkZ) {
        if (this.level.getChunkSource().getChunkNow(chunkX, chunkZ) != null) {
            return GeneratedArea.Status.GENERATED;
        }
        long key = ChunkPos.pack(chunkX, chunkZ);
        byte state = this.states.get(key);
        if (state == GENERATED) {
            return GeneratedArea.Status.GENERATED;
        }
        if (state == MISSING) {
            return GeneratedArea.Status.MISSING;
        }
        if (state == UNKNOWN) {
            this.states.put(key, QUEUED);
            this.queue.enqueue(key);
        }
        return GeneratedArea.Status.UNKNOWN;
    }

    int version() {
        return this.version;
    }

    int pending() {
        return this.queue.size() + this.inFlight;
    }

    int cached() {
        return this.states.size();
    }

    void tick(int scanBudget) {
        boolean changed = false;
        Result result;
        while ((result = this.results.poll()) != null) {
            this.inFlight--;
            if (this.states.get(result.key()) != GENERATED) {
                this.states.put(result.key(), result.generated() ? GENERATED : MISSING);
                changed = true;
            }
        }
        if (changed) {
            this.version++;
        }
        if (++this.ticksSinceForget >= FORGET_MISSING_INTERVAL) {
            this.ticksSinceForget = 0;
            this.forgetMissing();
        }
        if (this.states.size() > MAX_CACHED && this.inFlight == 0) {
            this.states.clear();
            this.queue.clear();
            this.version++;
        }
        int started = 0;
        while (started < scanBudget && !this.queue.isEmpty()) {
            long key = this.queue.dequeueLong();
            if (this.states.get(key) != QUEUED) {
                continue;
            }
            this.startScan(key);
            started++;
        }
    }

    void markGenerated(long key) {
        byte state = this.states.get(key);
        if (state == MISSING || state == QUEUED) {
            this.states.put(key, GENERATED);
            this.version++;
        }
    }

    private void forgetMissing() {
        boolean removed = this.states.long2ByteEntrySet().removeIf(entry -> entry.getByteValue() == MISSING);
        if (removed) {
            this.version++;
        }
    }

    private void startScan(long key) {
        this.inFlight++;
        scanStored(this.level, ChunkPos.unpack(key)).whenComplete((generated, error) ->
                this.results.add(new Result(key, error == null && Boolean.TRUE.equals(generated))));
    }

    static CompletableFuture<Boolean> scanStored(ServerLevel level, ChunkPos pos) {
        CollectFields visitor = new CollectFields(new FieldSelector(StringTag.TYPE, "Status"));
        try {
            return level.getChunkSource().chunkMap.chunkScanner().scanChunk(pos, visitor)
                    .handle((ignored, error) -> error == null && visitor.getResult() instanceof CompoundTag tag
                            && SerializableChunkData.getChunkStatusFromTag(tag).isOrAfter(ChunkStatus.FULL));
        } catch (RuntimeException e) {
            Constants.LOG.warn("Chunk-Prüfung für {} fehlgeschlagen: {}", pos, e.toString());
            return CompletableFuture.completedFuture(false);
        }
    }

    private record Result(long key, boolean generated) {
    }
}
