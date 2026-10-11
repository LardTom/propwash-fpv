package de.lardtom.propwash.race;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GateSignals {
    private static final Map<ServerLevel, GateSignalMap> ACTIVE = new IdentityHashMap<>();
    private static final Set<GateEntity> PULSING = Collections.newSetFromMap(new IdentityHashMap<>());
    private static volatile Map<BlockGetter, Long2IntMap> view = Map.of();

    private GateSignals() {
    }

    public static int signal(BlockGetter level, BlockPos pos, Direction direction, int original) {
        Map<BlockGetter, Long2IntMap> current = view;
        if (current.isEmpty() || original >= GateSignal.STRENGTH) {
            return original;
        }
        Long2IntMap masks = current.get(level);
        if (masks == null) {
            return original;
        }
        return (masks.get(pos.asLong()) & 1 << direction.get3DDataValue()) != 0 ? GateSignal.STRENGTH : original;
    }

    static void passed(GateEntity gate, boolean courseGate, boolean racing) {
        if (gate.signal().fires(courseGate, gate.gateIndex() == 0, racing)) {
            gate.pulse();
        }
    }

    static void hold(ServerLevel level, GateSignalTarget target) {
        GateSignalMap map = ACTIVE.computeIfAbsent(level, key -> new GateSignalMap());
        if (map.hold(emitter(target), target.face())) {
            publish();
            notify(level, target);
        }
    }

    static void release(ServerLevel level, GateSignalTarget target) {
        GateSignalMap map = ACTIVE.get(level);
        if (map == null) {
            return;
        }
        boolean last = map.release(emitter(target), target.face());
        if (map.isEmpty()) {
            ACTIVE.remove(level);
        }
        if (last) {
            publish();
            notify(level, target);
        }
    }

    static void track(GateEntity gate) {
        PULSING.add(gate);
    }

    public static void tick() {
        if (PULSING.isEmpty()) {
            return;
        }
        List<GateEntity> pulsing = new ArrayList<>(PULSING);
        for (GateEntity gate : pulsing) {
            if (!(gate.level() instanceof ServerLevel level) || !gate.signalTick(level)) {
                PULSING.remove(gate);
            }
        }
    }

    public static int pulsing() {
        return PULSING.size();
    }

    public static int active(ServerLevel level) {
        GateSignalMap map = ACTIVE.get(level);
        return map == null ? 0 : map.size();
    }

    public static void clear() {
        ACTIVE.clear();
        PULSING.clear();
        view = Map.of();
    }

    public static BlockPos support(GateSignalTarget target) {
        return new BlockPos(target.x(), target.y(), target.z());
    }

    public static BlockPos emitterPos(GateSignalTarget target) {
        return new BlockPos(target.emitterX(), target.emitterY(), target.emitterZ());
    }

    private static long emitter(GateSignalTarget target) {
        return BlockPos.asLong(target.emitterX(), target.emitterY(), target.emitterZ());
    }

    private static void publish() {
        if (ACTIVE.isEmpty()) {
            view = Map.of();
            return;
        }
        Map<BlockGetter, Long2IntMap> next = new IdentityHashMap<>();
        for (Map.Entry<ServerLevel, GateSignalMap> entry : ACTIVE.entrySet()) {
            Long2IntOpenHashMap masks = new Long2IntOpenHashMap();
            entry.getValue().masks().forEach(masks::put);
            next.put(entry.getKey(), masks);
        }
        view = next;
    }

    private static void notify(ServerLevel level, GateSignalTarget target) {
        BlockPos support = support(target);
        BlockPos emitter = emitterPos(target);
        if (!level.isLoaded(support) || !level.isLoaded(emitter)) {
            return;
        }
        Direction front = Direction.from3DDataValue(target.face()).getOpposite();
        Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(level, front,
                front.getAxis().isHorizontal() ? Direction.UP : Direction.NORTH);
        Block block = level.getBlockState(emitter).getBlock();
        level.updateNeighborsAt(emitter, block, orientation);
        level.updateNeighborsAt(support, block, orientation);
    }
}
