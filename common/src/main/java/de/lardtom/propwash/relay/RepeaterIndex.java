package de.lardtom.propwash.relay;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class RepeaterIndex {
    public static final int CELL_SHIFT = 7;
    private static final Map<Level, RepeaterIndex> LEVELS = Collections.synchronizedMap(new WeakHashMap<>());

    private final Long2ObjectOpenHashMap<Entry> byPos = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<LongArrayList> byCell = new Long2ObjectOpenHashMap<>();
    private int version;

    public static RepeaterIndex of(Level level) {
        return LEVELS.computeIfAbsent(level, key -> new RepeaterIndex());
    }

    public static @Nullable RepeaterIndex existing(@Nullable Level level) {
        return level == null ? null : LEVELS.get(level);
    }

    public synchronized void put(BlockPos pos, BlockState state, Object owner) {
        long key = pos.asLong();
        Vec3 antenna = SignalRepeaterBlock.antenna(pos, state);
        Entry previous = this.byPos.put(key, new Entry(key, owner, antenna.x, antenna.y, antenna.z));
        if (previous == null) {
            this.byCell.computeIfAbsent(cell(pos.getX(), pos.getZ()), k -> new LongArrayList(4)).add(key);
        }
        this.version++;
    }

    public synchronized void remove(BlockPos pos, Object owner) {
        long key = pos.asLong();
        Entry entry = this.byPos.get(key);
        if (entry == null || entry.owner != owner) {
            return;
        }
        this.byPos.remove(key);
        long cell = cell(pos.getX(), pos.getZ());
        LongArrayList list = this.byCell.get(cell);
        if (list != null) {
            list.rem(key);
            if (list.isEmpty()) {
                this.byCell.remove(cell);
            }
        }
        this.version++;
    }

    public synchronized int size() {
        return this.byPos.size();
    }

    public synchronized int version() {
        return this.version;
    }

    public synchronized boolean contains(BlockPos pos) {
        return this.byPos.containsKey(pos.asLong());
    }

    public synchronized @Nullable Vec3 antenna(BlockPos pos) {
        Entry entry = this.byPos.get(pos.asLong());
        return entry == null ? null : new Vec3(entry.x, entry.y, entry.z);
    }

    public synchronized int collect(double minX, double minZ, double maxX, double maxZ, Sink sink) {
        int x0 = ((int) Math.floor(minX)) >> CELL_SHIFT;
        int x1 = ((int) Math.floor(maxX)) >> CELL_SHIFT;
        int z0 = ((int) Math.floor(minZ)) >> CELL_SHIFT;
        int z1 = ((int) Math.floor(maxZ)) >> CELL_SHIFT;
        int found = 0;
        if (this.byPos.isEmpty()) {
            return 0;
        }
        if ((long) (x1 - x0 + 1) * (z1 - z0 + 1) > this.byCell.size()) {
            for (LongArrayList list : this.byCell.values()) {
                found += this.emit(list, minX, minZ, maxX, maxZ, sink);
            }
            return found;
        }
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                LongArrayList list = this.byCell.get(pack(cx, cz));
                if (list != null) {
                    found += this.emit(list, minX, minZ, maxX, maxZ, sink);
                }
            }
        }
        return found;
    }

    private int emit(LongArrayList list, double minX, double minZ, double maxX, double maxZ, Sink sink) {
        int found = 0;
        for (int i = 0; i < list.size(); i++) {
            Entry entry = this.byPos.get(list.getLong(i));
            if (entry == null || entry.x < minX || entry.x > maxX || entry.z < minZ || entry.z > maxZ) {
                continue;
            }
            sink.accept(entry.pos, entry.x, entry.y, entry.z);
            found++;
        }
        return found;
    }

    private static long cell(int x, int z) {
        return pack(x >> CELL_SHIFT, z >> CELL_SHIFT);
    }

    private static long pack(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    @FunctionalInterface
    public interface Sink {
        void accept(long pos, double x, double y, double z);
    }

    private record Entry(long pos, Object owner, double x, double y, double z) {
    }
}
