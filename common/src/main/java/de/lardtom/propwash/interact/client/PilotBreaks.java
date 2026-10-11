package de.lardtom.propwash.interact.client;

import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.interact.BreakMath;
import de.lardtom.propwash.interact.BreakRules;
import de.lardtom.propwash.interact.BreakableBlocks;
import de.lardtom.propwash.interact.ClearOrder;
import de.lardtom.propwash.interact.net.DroneBreakC2S;
import de.lardtom.propwash.interact.net.DroneBreakClearC2S;
import de.lardtom.propwash.interact.net.DroneBreakClearS2C;
import de.lardtom.propwash.interact.net.DroneBreakDeniedS2C;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.sim.BreakContact;
import de.lardtom.propwash.sim.BreakReport;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemedToast;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PilotBreaks {
    public static final PilotBreaks INSTANCE = new PilotBreaks();
    public static final Object TOAST_TOKEN = new Object();
    private static final double EPSILON = 1.0E-7;
    private static final int MAX_BLOCKS = 128;
    private static final int MAX_PENDING = 16;
    private static final int MAX_MEMORY = 32;
    private static final long TOAST_MILLIS = 2500L;
    private static final long TOAST_PAUSE_MILLIS = 3000L;
    private static final long NEVER = Long.MIN_VALUE;
    private static final int SCAN_TICKS = 2;
    private static final double LOOKAHEAD_SECONDS = 1.0;
    private static final double MAX_LOOKAHEAD = 24.0;
    private static final double SCAN_MARGIN = 6.0;
    private static final int MAX_SCAN = 4096;
    private static final int BACKLOG_POSITIONS = 28;
    private static final long CLEARED_TICKS = 200L;
    private static final long REFRESH_TICKS = 60L;
    private static final long ASK_AGAIN_TICKS = 20L;
    private static final int MAX_TRACKED = 4096;

    private final Vector3d velocity = new Vector3d();
    private final Vector3d ahead = new Vector3d();
    private final Long2LongOpenHashMap cleared = new Long2LongOpenHashMap();
    private final Long2LongOpenHashMap asked = new Long2LongOpenHashMap();
    private final List<Ask> scan = new ArrayList<>();
    private boolean backlog;
    private @Nullable ClientLevel clearedLevel;
    private long lastScan = NEVER;
    private int uncleared;
    private int clearRequests;
    private final List<Candidate> candidates = new ArrayList<>();
    private final List<Candidate> limiters = new ArrayList<>();
    private final List<DroneBreakC2S> pending = new ArrayList<>();
    private final List<Memory> memory = new ArrayList<>();
    private BreakRules.@Nullable Outcome withheld;
    private long lastToastMillis;
    private int predicted;
    private int requests;

    private PilotBreaks() {
        this.cleared.defaultReturnValue(NEVER);
        this.asked.defaultReturnValue(NEVER);
    }

    public void reset() {
        this.candidates.clear();
        this.limiters.clear();
        this.pending.clear();
        this.withheld = null;
    }

    public void forget() {
        this.reset();
        this.memory.clear();
        this.cleared.clear();
        this.asked.clear();
        this.clearedLevel = null;
        this.lastScan = NEVER;
    }

    public int uncleared() {
        return this.uncleared;
    }

    public int clearRequests() {
        return this.clearRequests;
    }

    public boolean isCleared(BlockPos pos, long now) {
        return this.cleared.get(pos.asLong()) >= now;
    }

    public int predicted() {
        return this.predicted;
    }

    public int requests() {
        return this.requests;
    }

    public void beforeMove(QuadcopterEntity drone, DroneSim sim, Vec3 delta) {
        if (!ClientInteractRules.dronesBreakBlocks() || drone.noPhysics || delta.lengthSqr() <= 0.0 || drone.pilot().isEmpty()) {
            return;
        }
        sim.getVelocity(this.ahead);
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || !(drone.level() instanceof ClientLevel level) || !this.collect(level, drone, delta)) {
            return;
        }
        sim.getVelocity(this.velocity);
        GameType mode = mc.gameMode.getPlayerMode();
        AABB moving = drone.getBoundingBox();
        for (Direction.Axis axis : Direction.axisStepOrder(delta)) {
            double wanted = delta.get(axis);
            if (wanted == 0.0) {
                continue;
            }
            double allowed = this.allowed(axis, moving, wanted);
            while (Math.abs(allowed) < Math.abs(wanted) - EPSILON && this.shatter(level, player, mode, drone, sim, axis, moving, wanted, allowed)) {
                allowed = this.allowed(axis, moving, wanted);
            }
            moving = moving.move(axis == Direction.Axis.X ? allowed : 0.0, axis == Direction.Axis.Y ? allowed : 0.0,
                    axis == Direction.Axis.Z ? allowed : 0.0);
        }
        this.candidates.clear();
    }

    public void afterFrame(QuadcopterEntity drone, int epoch) {
        if (!this.pending.isEmpty()) {
            boolean session = ClientBus.inSession();
            for (DroneBreakC2S request : this.pending) {
                DroneBreakC2S stamped = new DroneBreakC2S(drone.getId(), epoch, request.pos(), request.vx(), request.vy(), request.vz(),
                        request.axis(), request.positive());
                if (session && ClientNet.sendToServer(stamped)) {
                    this.requests++;
                }
            }
            this.pending.clear();
        }
        BreakRules.Outcome told = this.withheld;
        this.withheld = null;
        if (told != null) {
            this.toast(told);
        }
        this.clearAhead(drone, epoch);
    }

    void onClearance(DroneBreakClearS2C payload) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level != this.clearedLevel) {
            return;
        }
        long now = level.getGameTime();
        for (DroneBreakClearS2C.Entry entry : payload.entries()) {
            long key = entry.pos().asLong();
            if (entry.cleared()) {
                this.cleared.put(key, now + CLEARED_TICKS);
                continue;
            }
            this.cleared.remove(key);
            if (entry.outcome() == BreakRules.Outcome.TOO_FAR) {
                this.asked.remove(key);
            }
            if (entry.outcome() == BreakRules.Outcome.PROTECTED && this.remembered(entry.pos(), now) == null) {
                this.remember(entry.pos(), entry.outcome(), now);
            }
        }
    }

    private void clearAhead(QuadcopterEntity drone, int epoch) {
        if (!ClientInteractRules.dronesBreakBlocks() || drone.pilot().isEmpty() || !(drone.level() instanceof ClientLevel level)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) {
            return;
        }
        long now = level.getGameTime();
        if (level != this.clearedLevel) {
            this.cleared.clear();
            this.asked.clear();
            this.clearedLevel = level;
            this.lastScan = NEVER;
        }
        if (this.lastScan != NEVER && now >= this.lastScan && now - this.lastScan < (this.backlog ? 1 : SCAN_TICKS)) {
            return;
        }
        this.lastScan = now;
        this.prune(now);
        GameType mode = mc.gameMode.getPlayerMode();
        double lx = this.ahead.x * LOOKAHEAD_SECONDS;
        double ly = this.ahead.y * LOOKAHEAD_SECONDS;
        double lz = this.ahead.z * LOOKAHEAD_SECONDS;
        double length = Math.sqrt(lx * lx + ly * ly + lz * lz);
        if (!Double.isFinite(length)) {
            lx = 0.0;
            ly = 0.0;
            lz = 0.0;
        } else if (length > MAX_LOOKAHEAD) {
            double scale = MAX_LOOKAHEAD / length;
            lx *= scale;
            ly *= scale;
            lz *= scale;
        }
        AABB box = drone.getBoundingBox().expandTowards(lx, ly, lz).inflate(SCAN_MARGIN);
        int x0 = Mth.floor(box.minX);
        int x1 = Mth.floor(box.maxX);
        int y0 = Math.max(level.getMinY(), Mth.floor(box.minY));
        int y1 = Math.min(level.getMaxY(), Mth.floor(box.maxY));
        int z0 = Mth.floor(box.minZ);
        int z1 = Mth.floor(box.maxZ);
        this.scan.clear();
        Vec3 center = drone.getBoundingBox().getCenter();
        double halfSize = 0.5 * Math.max(drone.getBoundingBox().getXsize(), drone.getBoundingBox().getZsize());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) {
                    continue;
                }
                for (int sy = y0 >> 4; sy <= y1 >> 4; sy++) {
                    int index = chunk.getSectionIndexFromSectionY(sy);
                    if (index < 0 || index >= chunk.getSectionsCount()) {
                        continue;
                    }
                    LevelChunkSection section = chunk.getSection(index);
                    if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(BreakableBlocks.DRONE_BREAKABLE))) {
                        continue;
                    }
                    for (int x = Math.max(x0, cx << 4); x <= Math.min(x1, (cx << 4) + 15); x++) {
                        for (int y = Math.max(y0, sy << 4); y <= Math.min(y1, (sy << 4) + 15); y++) {
                            for (int z = Math.max(z0, cz << 4); z <= Math.min(z1, (cz << 4) + 15); z++) {
                                BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                                if (!state.is(BreakableBlocks.DRONE_BREAKABLE)) {
                                    continue;
                                }
                                if (this.scan.size() >= MAX_SCAN) {
                                    continue;
                                }
                                double score = ClearOrder.score(x + 0.5 - center.x, y + 0.5 - center.y, z + 0.5 - center.z, this.ahead.x, this.ahead.y,
                                        this.ahead.z, halfSize);
                                if (score == ClearOrder.SKIP) {
                                    continue;
                                }
                                cursor.set(x, y, z);
                                if (this.wanted(level, player, mode, cursor, state, now)) {
                                    this.scan.add(new Ask(cursor.immutable(), score));
                                }
                            }
                        }
                    }
                }
            }
        }
        this.backlog = this.scan.size() > DroneBreakClearC2S.MAX_POSITIONS;
        if (this.scan.isEmpty()) {
            return;
        }
        this.scan.sort(Comparator.comparingDouble(Ask::score));
        int most = this.backlog ? BACKLOG_POSITIONS : DroneBreakClearC2S.MAX_POSITIONS;
        List<BlockPos> ask = this.scan.subList(0, Math.min(this.scan.size(), most)).stream().map(Ask::pos).toList();
        this.scan.clear();
        if (ClientBus.inSession() && ClientNet.sendToServer(new DroneBreakClearC2S(drone.getId(), epoch, ask))) {
            this.clearRequests++;
            for (BlockPos pos : ask) {
                this.asked.put(pos.asLong(), now);
            }
        }
    }

    private boolean wanted(ClientLevel level, LocalPlayer player, GameType mode, BlockPos pos, BlockState state, long now) {
        long key = pos.asLong();
        long until = this.cleared.get(key);
        if (until != NEVER && until - now > REFRESH_TICKS) {
            return false;
        }
        long asked = this.asked.get(key);
        if (asked != NEVER && now >= asked && now - asked < ASK_AGAIN_TICKS) {
            return false;
        }
        return BreakableBlocks.candidate(level, pos, state) && this.denied(level, player, mode, pos, now) == null;
    }

    private void prune(long now) {
        if (this.cleared.size() > MAX_TRACKED) {
            this.cleared.long2LongEntrySet().removeIf(entry -> entry.getLongValue() < now);
        }
        if (this.asked.size() > MAX_TRACKED) {
            this.asked.long2LongEntrySet().removeIf(entry -> now - entry.getLongValue() >= ASK_AGAIN_TICKS || entry.getLongValue() > now);
        }
        if (this.cleared.size() > MAX_TRACKED) {
            this.cleared.clear();
        }
        if (this.asked.size() > MAX_TRACKED) {
            this.asked.clear();
        }
    }

    void onDenied(DroneBreakDeniedS2C payload) {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level == null ? 0L : mc.level.getGameTime();
        BreakRules.Outcome outcome = payload.outcome();
        this.cleared.remove(payload.pos().asLong());
        this.remember(payload.pos(), outcome, now);
        if (outcome.told()) {
            this.toast(outcome);
        }
    }

    private void remember(BlockPos pos, BreakRules.Outcome outcome, long now) {
        if (this.memory.size() >= MAX_MEMORY) {
            this.memory.removeFirst();
        }
        this.memory.add(new Memory(pos, BreakRules.memoryRadius(outcome), now + BreakRules.memoryTicks(outcome), outcome));
    }

    private void toast(BreakRules.Outcome outcome) {
        long now = System.currentTimeMillis();
        if (now - this.lastToastMillis < TOAST_PAUSE_MILLIS) {
            return;
        }
        this.lastToastMillis = now;
        Minecraft mc = Minecraft.getInstance();
        ThemedToast.show(mc.gui.toastManager(), TOAST_TOKEN, Component.translatable(outcome.messageKey()), null, Palette.WARNING, TOAST_MILLIS);
    }

    private boolean collect(ClientLevel level, QuadcopterEntity drone, Vec3 delta) {
        this.candidates.clear();
        AABB swept = drone.getBoundingBox().expandTowards(delta).inflate(EPSILON);
        int x0 = Mth.floor(swept.minX);
        int x1 = Mth.floor(swept.maxX);
        int y0 = Mth.floor(swept.minY) - 1;
        int y1 = Mth.floor(swept.maxY);
        int z0 = Mth.floor(swept.minZ);
        int z1 = Mth.floor(swept.maxZ);
        if ((long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1) > MAX_BLOCKS) {
            return false;
        }
        CollisionContext context = CollisionContext.of(drone);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        boolean any = false;
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    cursor.set(x, y, z);
                    if (!level.isLoaded(cursor)) {
                        this.candidates.clear();
                        return false;
                    }
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir()) {
                        continue;
                    }
                    VoxelShape shape = state.getCollisionShape(level, cursor, context);
                    if (shape.isEmpty()) {
                        continue;
                    }
                    VoxelShape placed = shape.move(cursor);
                    if (!placed.bounds().intersects(swept)) {
                        continue;
                    }
                    BlockPos pos = cursor.immutable();
                    boolean breakable = BreakableBlocks.candidate(level, pos, state);
                    any |= breakable;
                    this.candidates.add(new Candidate(pos, state, placed, breakable));
                }
            }
        }
        if (!any) {
            this.candidates.clear();
        }
        return any;
    }

    private double allowed(Direction.Axis axis, AABB moving, double wanted) {
        double allowed = wanted;
        for (Candidate candidate : this.candidates) {
            if (!candidate.broken) {
                double a = candidate.shape.collide(axis, moving, allowed);
                if (Math.abs(a) < Math.abs(allowed)) {
                    allowed = a;
                }
            }
        }
        return allowed;
    }

    private boolean shatter(ClientLevel level, LocalPlayer player, GameType mode, QuadcopterEntity drone, DroneSim sim, Direction.Axis axis, AABB moving,
                            double wanted, double allowed) {
        this.limiters.clear();
        for (Candidate candidate : this.candidates) {
            if (!candidate.broken && Math.abs(candidate.shape.collide(axis, moving, wanted)) <= Math.abs(allowed) + EPSILON) {
                if (!candidate.breakable) {
                    this.limiters.clear();
                    return false;
                }
                this.limiters.add(candidate);
            }
        }
        if (this.limiters.isEmpty()) {
            return false;
        }
        double closing = (wanted > 0.0 ? 1.0 : -1.0) * component(this.velocity, axis);
        double threshold = 0.0;
        for (Candidate candidate : this.limiters) {
            threshold = Math.max(threshold, BreakableBlocks.threshold(level, candidate.pos, candidate.state, axis));
        }
        BreakMath.Impact impact = BreakMath.of(sim.params().massKg, closing, threshold);
        if (!impact.breaks()) {
            return false;
        }
        long now = level.getGameTime();
        for (Candidate candidate : this.limiters) {
            BreakRules.Outcome denied = this.denied(level, player, mode, candidate.pos, now);
            if (denied != null) {
                this.withheld = denied.told() ? denied : this.withheld;
                return false;
            }
        }
        for (Candidate candidate : this.limiters) {
            if (!this.isCleared(candidate.pos, now)) {
                this.uncleared++;
                return false;
            }
        }
        double vx = this.velocity.x;
        double vy = this.velocity.y;
        double vz = this.velocity.z;
        for (Candidate candidate : this.limiters) {
            candidate.broken = true;
            level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_DESTROY_BLOCK, candidate.pos, Block.getId(candidate.state));
            level.setBlock(candidate.pos, level.getFluidState(candidate.pos).createLegacyBlock(), Block.UPDATE_ALL_IMMEDIATE);
            if (this.pending.size() < MAX_PENDING) {
                this.pending.add(new DroneBreakC2S(drone.getId(), 0, candidate.pos, (float) vx, (float) vy, (float) vz, axis.ordinal(), wanted > 0.0));
            }
            this.predicted++;
        }
        AABB glass = this.limiters.getFirst().shape.bounds();
        long seed = 0L;
        for (Candidate candidate : this.limiters) {
            glass = glass.minmax(candidate.shape.bounds());
            seed = seed * 31L + candidate.pos.asLong();
        }
        BreakContact contact = new BreakContact(BreakMath.shardMass(threshold), glass.minX, glass.minY, glass.minZ, glass.maxX, glass.maxY,
                glass.maxZ, ClientInteractRules.breakImpact(), seed);
        int before = sim.breakCount();
        double sign = wanted > 0.0 ? -1.0 : 1.0;
        sim.breakThrough(axis == Direction.Axis.X ? sign : 0.0, axis == Direction.Axis.Y ? sign : 0.0, axis == Direction.Axis.Z ? sign : 0.0,
                impact.absorbed(), contact);
        BreakReport report = sim.lastBreak();
        if (report != null && sim.breakCount() != before) {
            for (Candidate candidate : this.limiters) {
                BreakEffects.spray(level, drone, candidate.pos, candidate.state, candidate.shape.bounds(), axis, wanted > 0.0, vx, vy, vz, report);
            }
            BreakEffects.sound(level, drone, this.limiters.getFirst().state, report);
        }
        sim.getVelocity(this.velocity);
        return true;
    }

    private BreakRules.@Nullable Outcome denied(ClientLevel level, LocalPlayer player, GameType mode, BlockPos pos, long now) {
        if (player.blockActionRestricted(level, pos, mode)) {
            return BreakRules.Outcome.GAME_MODE;
        }
        if (!level.getWorldBorder().isWithinBounds(pos)) {
            return BreakRules.Outcome.PROTECTED;
        }
        if (BreakableBlocks.courseFrame(level, pos)) {
            return BreakRules.Outcome.COURSE;
        }
        return this.remembered(pos, now);
    }

    private BreakRules.@Nullable Outcome remembered(BlockPos pos, long now) {
        this.memory.removeIf(entry -> entry.until() < now || entry.until() - now > BreakRules.PROTECTED_MEMORY_TICKS);
        for (Memory entry : this.memory) {
            if (entry.radius() <= 0.0 ? entry.pos().equals(pos) : entry.pos().distSqr(pos) <= entry.radius() * entry.radius()) {
                return entry.outcome();
            }
        }
        return null;
    }

    private static double component(Vector3d v, Direction.Axis axis) {
        return switch (axis) {
            case X -> v.x;
            case Y -> v.y;
            case Z -> v.z;
        };
    }

    private static final class Candidate {
        private final BlockPos pos;
        private final BlockState state;
        private final VoxelShape shape;
        private final boolean breakable;
        private boolean broken;

        private Candidate(BlockPos pos, BlockState state, VoxelShape shape, boolean breakable) {
            this.pos = pos;
            this.state = state;
            this.shape = shape;
            this.breakable = breakable;
        }
    }

    private record Ask(BlockPos pos, double score) {
    }

    private record Memory(BlockPos pos, double radius, long until, BreakRules.Outcome outcome) {
    }
}
