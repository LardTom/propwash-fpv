package de.lardtom.propwash.relay.client;

import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.relay.RelayCandidates;
import de.lardtom.propwash.relay.RelayLinks;
import de.lardtom.propwash.relay.RelayModule;
import de.lardtom.propwash.relay.RepeaterIndex;
import de.lardtom.propwash.relay.RepeaterMeter;
import de.lardtom.propwash.video.LinkBudget;
import de.lardtom.propwash.video.client.ClientLinkWorld;
import de.lardtom.propwash.video.client.VideoState;
import de.lardtom.propwash.video.link.ObstructionScan;
import de.lardtom.propwash.video.link.ObstructionTracer;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class RepeaterSignals {
    public static final RepeaterSignals INSTANCE = new RepeaterSignals();
    public static final VideoType REFERENCE = VideoType.ANALOG_RACE;
    private static final int CELL_BUDGET_PER_TICK = 8_000;
    private static final int METER_BUDGET_PER_TICK = 6_000;
    private static final int LEVEL_REFRESH_TICKS = 10;
    private static final int LINK_REFRESH_TICKS = 200;
    private static final int FORGET_TICKS = 100;
    private static final int LINK_FORGET_TICKS = 400;
    private static final int PARTNERS = 3;
    private static final int METER_RESCAN_TICKS = 10;
    private static final int METER_MESSAGE_TICKS = 10;
    private static final double MOVED_SQR = 0.25;

    private final ClientLinkWorld world = new ClientLinkWorld();
    private final Long2ObjectOpenHashMap<Shown> shown = new Long2ObjectOpenHashMap<>();
    private final Map<Pair, Link> links = new HashMap<>();
    private final ArrayDeque<Link> queue = new ArrayDeque<>();
    private final ObstructionScan meterScan = new ObstructionScan();
    private final long[] partnerKeys = new long[PARTNERS];
    private final double[] partnerXyz = new double[3 * PARTNERS];
    private final double[] partnerDistance = new double[PARTNERS];
    private @Nullable Level level;
    private int tick;
    private boolean holding;
    private long meterTarget = Long.MIN_VALUE;
    private double meterX;
    private double meterY;
    private double meterZ;
    private int meterStarted = Integer.MIN_VALUE / 2;
    private double meterLoss = Double.NaN;
    private double meterQuality = -1.0;
    private double meterDistance = Double.NaN;
    private @Nullable Component meterText;
    private int meterSentTick = Integer.MIN_VALUE / 2;
    private int partnerCount;

    private RepeaterSignals() {
    }

    public static void init() {
        ClientBus.TICK_END.register(INSTANCE::tick);
        ClientBus.DISCONNECT.register(minecraft -> INSTANCE.clear());
    }

    public int bars(BlockPos pos) {
        Shown entry = this.shown.get(pos.asLong());
        if (entry == null) {
            entry = new Shown();
            entry.updated = Integer.MIN_VALUE / 2;
            this.shown.put(pos.asLong(), entry);
        }
        entry.requested = this.tick;
        return entry.bars;
    }

    public double quality(BlockPos pos) {
        Shown entry = this.shown.get(pos.asLong());
        return entry == null ? Double.NaN : entry.quality;
    }

    public boolean inChain(BlockPos pos) {
        Shown entry = this.shown.get(pos.asLong());
        return entry != null && entry.chain;
    }

    public boolean holding() {
        return this.holding;
    }

    public double meterQuality() {
        return this.meterQuality;
    }

    public double meterDistance() {
        return this.meterDistance;
    }

    public @Nullable Component meterText() {
        return this.holding ? this.meterText : null;
    }

    public void clear() {
        this.shown.clear();
        this.links.clear();
        this.queue.clear();
        this.meterScan.clear();
        this.level = null;
        this.holding = false;
        this.meterTarget = Long.MIN_VALUE;
        this.meterLoss = Double.NaN;
        this.meterQuality = -1.0;
        this.meterDistance = Double.NaN;
        this.meterText = null;
        this.partnerCount = 0;
        RepeaterMeter.hide();
    }

    private void tick(Minecraft minecraft) {
        Level current = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (current == null || player == null) {
            if (this.level != null) {
                this.clear();
            }
            return;
        }
        if (current != this.level) {
            this.clear();
            this.level = current;
        }
        this.tick++;
        ObstructionTracer.World blocks = this.world.bind(current);
        RepeaterIndex index = RepeaterIndex.existing(current);
        boolean on = RelayClient.relayLimit() > 0;
        VideoSystem system = this.system();
        double range = this.range();
        boolean obstruction = VideoState.INSTANCE.relay().lastObstruction();
        double reach = RelayLinks.videoHopReach(system, range);
        this.updateShown(index, on, system, range, obstruction, reach);
        this.runLinks(blocks);
        this.updateMeter(player, index, on, system, range, obstruction, reach, blocks);
    }

    private VideoSystem system() {
        VideoSystem last = VideoState.INSTANCE.relay().lastSystem();
        return last != null ? last : REFERENCE.system;
    }

    private double range() {
        double last = VideoState.INSTANCE.relay().lastRange();
        return last > 0.0 && Double.isFinite(last) ? last : REFERENCE.rangeBlocks;
    }

    private void updateShown(@Nullable RepeaterIndex index, boolean on, VideoSystem system, double range, boolean obstruction, double reach) {
        VideoState video = VideoState.INSTANCE;
        RelayVideo relay = video.relay();
        int hops = video.relayHops();
        Iterator<Long2ObjectOpenHashMap.Entry<Shown>> it = this.shown.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            Long2ObjectOpenHashMap.Entry<Shown> next = it.next();
            Shown entry = next.getValue();
            if (this.tick - entry.requested > FORGET_TICKS) {
                it.remove();
                continue;
            }
            long key = next.getLongKey();
            if (!on || index == null) {
                entry.set(0.0, false);
                continue;
            }
            int r = hops > 0 ? relay.chainIndex(key) : -1;
            if (r >= 0) {
                entry.set(Math.min(video.relayHopQuality(r), video.relayHopQuality(r + 1)), true);
                entry.updated = this.tick;
                continue;
            }
            if (this.tick - entry.updated < LEVEL_REFRESH_TICKS && !entry.chain) {
                continue;
            }
            entry.updated = this.tick;
            entry.set(this.bestPartner(index, BlockPos.of(key), system, range, obstruction, reach), false);
        }
        if (this.tick % LINK_FORGET_TICKS == 0) {
            this.links.values().removeIf(link -> this.tick - link.usedTick > LINK_FORGET_TICKS && !link.queued);
        }
    }

    private double bestPartner(RepeaterIndex index, BlockPos pos, VideoSystem system, double range, boolean obstruction, double reach) {
        Vec3 antenna = index.antenna(pos);
        if (antenna == null) {
            return 0.0;
        }
        this.nearest(index, antenna.x, antenna.y, antenna.z, reach, pos.asLong());
        double best = 0.0;
        for (int i = 0; i < this.partnerCount; i++) {
            long other = this.partnerKeys[i];
            boolean first = pos.asLong() < other;
            Pair pair = first ? new Pair(pos.asLong(), other) : new Pair(other, pos.asLong());
            Link link = this.links.computeIfAbsent(pair, k -> new Link());
            link.usedTick = this.tick;
            double px = this.partnerXyz[3 * i];
            double py = this.partnerXyz[3 * i + 1];
            double pz = this.partnerXyz[3 * i + 2];
            double ax = first ? antenna.x : px;
            double ay = first ? antenna.y : py;
            double az = first ? antenna.z : pz;
            double bx = first ? px : antenna.x;
            double by = first ? py : antenna.y;
            double bz = first ? pz : antenna.z;
            boolean moved = link.ax != ax || link.ay != ay || link.az != az || link.bx != bx || link.by != by || link.bz != bz;
            if (moved && !link.running) {
                link.ax = ax;
                link.ay = ay;
                link.az = az;
                link.bx = bx;
                link.by = by;
                link.bz = bz;
                link.lossDb = Double.NaN;
            }
            if (!link.queued && (Double.isNaN(link.lossDb) || this.tick - link.measuredTick >= LINK_REFRESH_TICKS)) {
                link.queued = true;
                this.queue.add(link);
            }
            if (!Double.isNaN(link.lossDb)) {
                double loss = obstruction ? link.lossDb : 0.0;
                best = Math.max(best, LinkBudget.quality(system, LinkBudget.marginDb(this.partnerDistance[i], range) - loss));
            }
        }
        return best;
    }

    private void nearest(RepeaterIndex index, double x, double y, double z, double reach, long except) {
        this.partnerCount = 0;
        double box = Double.isFinite(reach) ? reach : 1024.0;
        index.collect(x - box, z - box, x + box, z + box, (pos, px, py, pz) -> {
            if (pos == except) {
                return;
            }
            double distance = RelayCandidates.distance(x, y, z, px, py, pz);
            if (!(distance <= reach)) {
                return;
            }
            int slot;
            if (this.partnerCount < PARTNERS) {
                slot = this.partnerCount++;
            } else if (distance < this.partnerDistance[PARTNERS - 1]) {
                slot = PARTNERS - 1;
            } else {
                return;
            }
            while (slot > 0 && this.partnerDistance[slot - 1] > distance) {
                this.partnerKeys[slot] = this.partnerKeys[slot - 1];
                this.partnerDistance[slot] = this.partnerDistance[slot - 1];
                System.arraycopy(this.partnerXyz, 3 * (slot - 1), this.partnerXyz, 3 * slot, 3);
                slot--;
            }
            this.partnerKeys[slot] = pos;
            this.partnerDistance[slot] = distance;
            this.partnerXyz[3 * slot] = px;
            this.partnerXyz[3 * slot + 1] = py;
            this.partnerXyz[3 * slot + 2] = pz;
        });
    }

    private void runLinks(ObstructionTracer.World blocks) {
        int budget = CELL_BUDGET_PER_TICK;
        while (budget > 0 && !this.queue.isEmpty()) {
            Link link = this.queue.peek();
            if (!link.running) {
                link.scan.start(link.ax, link.ay, link.az, link.bx, link.by, link.bz);
                link.running = true;
            }
            int before = link.scan.roundCells();
            boolean done = link.scan.advance(blocks, budget);
            budget -= Math.max(1, link.scan.roundCells() - before);
            if (done) {
                link.lossDb = link.scan.lossDb();
                link.measuredTick = this.tick;
                link.running = false;
                link.queued = false;
                this.queue.poll();
            }
        }
    }

    private void updateMeter(LocalPlayer player, @Nullable RepeaterIndex index, boolean on, VideoSystem system, double range, boolean obstruction,
                             double reach, ObstructionTracer.World blocks) {
        Item item = RelayModule.SIGNAL_REPEATER.item().get();
        if (!player.getMainHandItem().is(item) && !player.getOffhandItem().is(item)) {
            if (this.holding) {
                this.holding = false;
                this.meterTarget = Long.MIN_VALUE;
                this.meterScan.clear();
                this.meterText = null;
                RepeaterMeter.hide();
            }
            return;
        }
        this.holding = true;
        Component text;
        if (!on) {
            this.meterQuality = -1.0;
            this.meterDistance = Double.NaN;
            RepeaterMeter.hide();
            text = Component.translatable("propwash.relay.meter.off");
        } else {
            Vec3 eye = player.getEyePosition();
            this.partnerCount = 0;
            if (index != null) {
                this.nearest(index, eye.x, eye.y, eye.z, reach, Long.MIN_VALUE);
            }
            if (this.partnerCount == 0) {
                this.meterTarget = Long.MIN_VALUE;
                this.meterQuality = 0.0;
                this.meterDistance = Double.NaN;
                RepeaterMeter.show(0.0);
                text = Component.translatable("propwash.relay.meter.none");
            } else {
                text = this.measure(eye, system, range, obstruction, blocks);
            }
        }
        if (!text.equals(this.meterText) || this.tick - this.meterSentTick >= METER_MESSAGE_TICKS) {
            this.meterText = text;
            this.meterSentTick = this.tick;
            player.sendOverlayMessage(text);
        }
    }

    private Component measure(Vec3 eye, VideoSystem system, double range, boolean obstruction, ObstructionTracer.World blocks) {
        long target = this.partnerKeys[0];
        double tx = this.partnerXyz[0];
        double ty = this.partnerXyz[1];
        double tz = this.partnerXyz[2];
        double dx = eye.x - this.meterX;
        double dy = eye.y - this.meterY;
        double dz = eye.z - this.meterZ;
        boolean changed = target != this.meterTarget;
        if (changed) {
            this.meterLoss = Double.NaN;
            this.meterScan.clear();
        }
        if (this.meterScan.complete() && (changed || dx * dx + dy * dy + dz * dz > MOVED_SQR || this.tick - this.meterStarted >= METER_RESCAN_TICKS)) {
            this.meterTarget = target;
            this.meterX = eye.x;
            this.meterY = eye.y;
            this.meterZ = eye.z;
            this.meterStarted = this.tick;
            this.meterScan.start(eye.x, eye.y, eye.z, tx, ty, tz);
        }
        if (this.meterScan.advance(blocks, METER_BUDGET_PER_TICK) && this.meterTarget == target) {
            this.meterLoss = this.meterScan.lossDb();
        }
        this.meterDistance = this.partnerDistance[0];
        if (!Double.isNaN(this.meterLoss)) {
            double loss = obstruction ? this.meterLoss : 0.0;
            this.meterQuality = LinkBudget.quality(system, LinkBudget.marginDb(this.meterDistance, range) - loss);
        } else if (changed) {
            this.meterQuality = 0.0;
        }
        RepeaterMeter.show(this.meterQuality);
        return Component.translatable("propwash.relay.meter.signal", Math.round(this.meterQuality * 100.0), Math.round(this.meterDistance));
    }

    private record Pair(long a, long b) {
    }

    private static final class Shown {
        private int requested;
        private int updated;
        private int bars;
        private double quality;
        private boolean chain;

        private void set(double quality, boolean chain) {
            this.quality = quality;
            this.bars = RepeaterMeter.bars(quality);
            this.chain = chain;
        }
    }

    private static final class Link {
        private final ObstructionScan scan = new ObstructionScan();
        private double ax = Double.NaN;
        private double ay;
        private double az;
        private double bx;
        private double by;
        private double bz;
        private double lossDb = Double.NaN;
        private int measuredTick;
        private int usedTick;
        private boolean queued;
        private boolean running;
    }
}
