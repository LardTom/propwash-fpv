package de.lardtom.propwash.video.client;

import com.mojang.blaze3d.platform.Monitor;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.FpvView;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.video.EffectParams;
import de.lardtom.propwash.video.FeedInput;
import de.lardtom.propwash.video.FeedLook;
import de.lardtom.propwash.video.FeedModel;
import de.lardtom.propwash.video.DisplayLatency;
import de.lardtom.propwash.video.FovMath;
import de.lardtom.propwash.video.LatencyChain;
import de.lardtom.propwash.video.PoseHistory;
import de.lardtom.propwash.video.LinkBudget;
import de.lardtom.propwash.video.Multipath;
import de.lardtom.propwash.video.link.ObstructionScan;
import de.lardtom.propwash.relay.client.RelayClient;
import de.lardtom.propwash.relay.client.RelayVideo;
import de.lardtom.propwash.server.client.ClientWorldRules;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

public final class VideoState {
    public static final float LOST_LEVEL = 0.5F;
    public static final VideoState INSTANCE = new VideoState();

    private static final int HISTORY_CAPACITY = 8192;
    private static final double QUALITY_TAU_SECONDS = 0.08;
    private static final int CELL_BUDGET_PER_TICK = 24_000;
    private static final float MULTIPATH_SMOOTHING = 0.3F;
    private static final long REFRESH_POLL_NANOS = 1_000_000_000L;
    private static final long NONE = Long.MIN_VALUE;

    private final PoseHistory poses = new PoseHistory(HISTORY_CAPACITY);
    private final FeedModel feed = new FeedModel(0x5EED_F00DL);
    private final FeedInput input = new FeedInput();
    private final ObstructionScan scan = new ObstructionScan();
    private final double[] representative = new double[ObstructionScan.REPRESENTATIVE];
    private final ClientBlockLoss blockLoss = new ClientBlockLoss();
    private final RelayVideo relay = new RelayVideo();
    private final DisplayLatency display = new DisplayLatency();
    private final LatencyChain chain = new LatencyChain();
    private long refreshPolledNanos = NONE;
    private long pendingFrameNanos = NONE;
    private long armedFrameNanos = NONE;
    private long pendingInputAgeNanos;
    private long pendingSimNanos;
    private long pendingSystemNanos;
    private long pendingAppliedNanos;
    private long simInputAgeNanos;
    private long simPendingNanos;
    private long sampleNanos;

    private @Nullable QuadcopterEntity source;
    private @Nullable QuadcopterEntity testSource;
    private @Nullable VideoSystem testReceiver;
    private boolean testReceiverSet;
    private LinkStatus status = LinkStatus.INACTIVE;
    private @Nullable VideoSystem system;
    private @Nullable VideoSystem receiver;
    private @Nullable VideoType videoType;
    private boolean creative;
    private float quality;
    private float rawQuality;
    private double distance;
    private double obstructionDb;
    private double scanLossDb;
    private double scanCenterLossDb;
    private int representativeCount;
    private long scanTickNanos;
    private long scanTickNanosMax;
    private long scanTickNanosTotal;
    private long scanTicks;
    private int scanRounds;
    private int scanRoundRays;
    private int scanRoundCells;
    private float multipath;
    private boolean relayUsed;
    private float directQuality;
    private float testQuality = -1.0F;
    private @Nullable VideoSystem testTransmitter;
    private long physicsFrameNanos;
    private long activeSinceNanos;

    private VideoState() {
    }

    public @Nullable VideoSystem system() {
        return this.status == LinkStatus.CONNECTED ? this.system : null;
    }

    public float signalQuality() {
        return this.status == LinkStatus.CONNECTED ? this.quality : 0.0F;
    }

    public long latencyNanos() {
        return this.latencyNanosAt(Util.getNanos());
    }

    public long latencyNanosAt(long frameNanos) {
        if (this.status == LinkStatus.INACTIVE) {
            return 0L;
        }
        return Math.max(0L, this.feed.latencyAt(frameNanos));
    }

    public void record(long nanos, double x, double y, double z, Quaternionf orientation) {
        this.poses.record(nanos, x, y, z, orientation);
    }

    public boolean sample(long nanos, Vector3d outPos, Quaternionf outOrientation) {
        return this.poses.sample(nanos, outPos, outOrientation);
    }

    public boolean sampleDisplayed(long frameNanos, Vector3d outPos, Quaternionf outOrientation) {
        long system = this.status == LinkStatus.INACTIVE ? 0L : Math.max(0L, this.feed.latencyAt(frameNanos));
        this.display.frameStart(frameNanos);
        long applied = this.display.applied(system);
        if (frameNanos != this.armedFrameNanos) {
            this.armedFrameNanos = frameNanos;
            this.pendingFrameNanos = frameNanos;
            this.pendingSystemNanos = system;
            this.pendingAppliedNanos = applied;
            this.pendingInputAgeNanos = this.simInputAgeNanos;
            this.pendingSimNanos = this.simPendingNanos;
        }
        this.sampleNanos = frameNanos - applied;
        return this.poses.sample(this.sampleNanos, outPos, outOrientation);
    }

    public long lastSampleNanos() {
        return this.sampleNanos;
    }

    public void simFrame(long inputAgeNanos, long pendingNanos) {
        this.simInputAgeNanos = Math.max(0L, inputAgeNanos);
        this.simPendingNanos = Math.max(0L, pendingNanos);
    }

    public void presented(long nanos) {
        long frame = this.pendingFrameNanos;
        this.display.presented(nanos);
        if (frame == NONE || nanos < frame) {
            return;
        }
        this.pendingFrameNanos = NONE;
        this.chain.frame(this.pendingInputAgeNanos, this.pendingSimNanos, this.pendingSystemNanos, this.pendingAppliedNanos, nanos - frame,
                this.display.scanoutNanos());
    }

    public DisplayLatency displayLatency() {
        return this.display;
    }

    public LatencyChain latencyChain() {
        return this.chain;
    }

    public long appliedLatencyNanos() {
        return this.display.applied(this.latencyNanos());
    }

    public void cut(long nanos) {
        this.poses.cut(nanos);
    }

    public LinkStatus status() {
        return this.status;
    }

    public boolean active() {
        return this.status != LinkStatus.INACTIVE;
    }

    public @Nullable VideoSystem receiver() {
        return this.status == LinkStatus.INACTIVE ? null : this.receiver;
    }

    public @Nullable VideoSystem goggles(@Nullable LivingEntity wearer) {
        if (this.testReceiverSet) {
            return this.testReceiver;
        }
        return wearer == null ? null : ContentIds.wornGogglesSystem(wearer).orElse(null);
    }

    public @Nullable VideoType videoType() {
        return this.status == LinkStatus.INACTIVE ? null : this.videoType;
    }

    public boolean creative() {
        return this.status != LinkStatus.INACTIVE && this.creative;
    }

    public double distanceBlocks() {
        return this.status == LinkStatus.INACTIVE ? 0.0 : this.distance;
    }

    public double obstructionDb() {
        return this.status == LinkStatus.INACTIVE ? 0.0 : this.obstructionDb;
    }

    public boolean noSignal() {
        return this.status != LinkStatus.INACTIVE && this.feed.params().noSignal >= 0.999F;
    }

    public boolean videoLost() {
        return this.status != LinkStatus.INACTIVE && this.feed.params().noSignal >= LOST_LEVEL;
    }

    public float noiseLevel() {
        if (this.status == LinkStatus.INACTIVE) {
            return 0.0F;
        }
        EffectParams p = this.feed.params();
        return Math.clamp(Math.max(p.noise, p.noSignal), 0.0F, 1.0F);
    }

    public int relayHops() {
        return this.status == LinkStatus.CONNECTED && this.relayUsed ? this.relay.relays() : 0;
    }

    public float relayHopQuality(int hop) {
        VideoSystem transmitter = this.system;
        if (this.relayHops() == 0 || transmitter == null || hop < 0 || hop > this.relay.relays()) {
            return 0.0F;
        }
        return (float) this.relay.hopQuality(transmitter, hop);
    }

    public float directQuality() {
        return this.status == LinkStatus.CONNECTED ? this.directQuality : 0.0F;
    }

    public RelayVideo relay() {
        return this.relay;
    }

    public boolean frozen() {
        return this.status == LinkStatus.CONNECTED && this.feed.frozen();
    }

    public float osdShift() {
        if (this.status != LinkStatus.CONNECTED || this.system != VideoSystem.ANALOG) {
            return 0.0F;
        }
        return this.feed.params().osdShift;
    }

    public int bufferedPoses() {
        return this.poses.size();
    }

    public float fpvFov() {
        if (!PilotSession.INSTANCE.isFpv() && !FpvView.spectating()) {
            return -1.0F;
        }
        Minecraft minecraft = Minecraft.getInstance();
        int width = minecraft.getWindow().getWidth();
        int height = minecraft.getWindow().getHeight();
        double aspect = height > 0 ? FpvImage.area(width, height).spanAspect() : 16.0 / 9.0;
        return FovMath.verticalFromHorizontal(this.horizontalFov(), aspect);
    }

    public double horizontalFov() {
        Minecraft minecraft = Minecraft.getInstance();
        VideoSystem profile = this.receiver;
        if (profile == null && minecraft.player != null) {
            profile = ContentIds.wornGogglesSystem(minecraft.player).orElse(null);
        }
        QuadcopterEntity drone = this.resolveSource();
        return VideoSettings.get().horizontalFov(profile, drone != null ? drone.build().flight().video() : this.videoType());
    }

    public @Nullable VideoType camera() {
        QuadcopterEntity drone = this.resolveSource();
        return drone != null ? drone.build().video() : this.videoType();
    }

    EffectParams params() {
        return this.feed.params();
    }

    @Nullable QuadcopterEntity source() {
        return this.status == LinkStatus.INACTIVE ? null : this.source;
    }

    long activeSinceNanos() {
        return this.activeSinceNanos;
    }

    long raysTraced() {
        return this.scan.raysTraced();
    }

    long scanTickNanos() {
        return this.scanTickNanos;
    }

    long scanTickNanosMax() {
        return this.scanTickNanosMax;
    }

    double scanTickNanosAverage() {
        return this.scanTicks == 0L ? 0.0 : (double) this.scanTickNanosTotal / this.scanTicks;
    }

    int scanRounds() {
        return this.scanRounds;
    }

    int scanRoundRays() {
        return this.scanRoundRays;
    }

    int scanRoundCells() {
        return this.scanRoundCells;
    }

    double scanLossDb() {
        return this.scanLossDb;
    }

    double scanCenterLossDb() {
        return this.scanCenterLossDb;
    }

    void resetScanStats() {
        this.scanTickNanos = 0L;
        this.scanTickNanosMax = 0L;
        this.scanTickNanosTotal = 0L;
        this.scanTicks = 0L;
        this.scanRounds = 0;
    }

    float rawQuality() {
        return this.rawQuality;
    }

    float multipath() {
        return this.status == LinkStatus.INACTIVE ? 0.0F : this.multipath;
    }

    FeedModel feed() {
        return this.feed;
    }

    void overrideTestQuality(float quality) {
        this.testQuality = Float.isNaN(quality) ? -1.0F : Math.min(quality, 1.0F);
    }

    void overrideTestTransmitter(@Nullable VideoSystem transmitter) {
        this.testTransmitter = transmitter;
    }

    void attachTestSource(@Nullable QuadcopterEntity drone) {
        this.testSource = drone;
    }

    void overrideTestReceiver(boolean enabled, @Nullable VideoSystem receiver) {
        this.testReceiverSet = enabled;
        this.testReceiver = enabled ? receiver : null;
    }

    void clearPoses() {
        this.poses.clear();
    }

    void onFrame(Minecraft minecraft, DeltaTracker deltaTracker) {
        long now = Util.getNanos();
        this.pollRefreshRate(minecraft, now);
        double dt = this.physicsFrameNanos == 0L ? 0.0 : Math.min(Math.max((now - this.physicsFrameNanos) * 1.0E-9, 0.0), 0.25);
        this.physicsFrameNanos = now;
        LocalPlayer player = minecraft.player;
        QuadcopterEntity drone = this.resolveSource();
        if (drone == null || player == null || minecraft.level == null || drone.isRemoved() || drone.level() != minecraft.level) {
            if (this.status != LinkStatus.INACTIVE) {
                this.deactivate();
            }
            return;
        }
        if (this.status == LinkStatus.INACTIVE || drone != this.source) {
            this.begin(minecraft, drone, now);
        }
        VideoConfig config = VideoSettings.get();
        VideoType type = drone.build().video();
        VideoSystem transmitter = this.testTransmitter != null ? this.testTransmitter : type.system;
        VideoSystem goggles = this.goggles(player);
        this.videoType = type;
        this.system = transmitter;
        this.receiver = goggles;
        if (goggles == null) {
            this.status = LinkStatus.NO_GOGGLES;
        } else if (goggles.receives(transmitter)) {
            this.status = LinkStatus.CONNECTED;
        } else {
            this.status = LinkStatus.WRONG_SYSTEM;
        }
        this.creative = goggles == VideoSystem.CREATIVE;
        double range = this.range(config, type);
        this.distance = player.getEyePosition().distanceTo(antenna(drone));
        this.obstructionDb = this.obstructs(config) ? this.scanLossDb : 0.0;
        boolean connected = this.status == LinkStatus.CONNECTED;
        boolean forced = this.testQuality >= 0.0F;
        this.relayUsed = false;
        if (!connected) {
            this.rawQuality = 0.0F;
            this.directQuality = 0.0F;
            this.quality = 0.0F;
        } else if (forced) {
            this.rawQuality = this.testQuality;
            this.directQuality = this.testQuality;
            this.quality = this.testQuality;
        } else {
            this.directQuality = (float) LinkBudget.quality(transmitter, this.distance, range, this.obstructionDb);
            this.rawQuality = this.directQuality;
            if (this.relay.solve(player.getEyePosition(), antenna(drone), transmitter, range, this.obstructs(config))) {
                float relayed = (float) this.relay.quality(transmitter);
                if (relayed > this.directQuality) {
                    this.rawQuality = relayed;
                    this.relayUsed = true;
                }
            }
            float alpha = (float) (1.0 - Math.exp(-dt / QUALITY_TAU_SECONDS));
            this.quality += (this.rawQuality - this.quality) * alpha;
        }
        FeedInput in = this.input;
        in.look = this.creative && Double.isInfinite(range) ? FeedLook.CLEAN : FeedLook.of(transmitter);
        in.connected = connected;
        in.snowOnLoss = goggles == VideoSystem.ANALOG || (goggles == VideoSystem.CREATIVE && transmitter == VideoSystem.ANALOG);
        in.quality = this.quality;
        in.imageQuality = this.creative ? 1.0F : (float) type.imageQuality;
        in.baseLatencyNanos = config.latencyMs(goggles, type.latencyMs) * 1_000_000L;
        in.strength = config.effectStrength;
        in.reduceFlicker = config.reduceFlicker;
        in.extraNoise = config.extraNoise(goggles);
        in.cameraCondition = drone.wear().video();
        in.multipath = forced || !this.obstructs(config) ? 0.0F : this.multipath;
        this.feed.update(now, in);
    }

    private void pollRefreshRate(Minecraft minecraft, long now) {
        if (this.refreshPolledNanos != NONE && now - this.refreshPolledNanos < REFRESH_POLL_NANOS) {
            return;
        }
        this.refreshPolledNanos = now;
        Monitor monitor = minecraft.getWindow().findBestMonitor();
        this.display.refreshHz(monitor == null ? DisplayLatency.DEFAULT_REFRESH_HZ : monitor.currentMode().getRefreshRate());
    }

    void onTick(Minecraft minecraft) {
        QuadcopterEntity drone = this.source;
        LocalPlayer player = minecraft.player;
        if (this.status == LinkStatus.INACTIVE || drone == null || player == null || minecraft.level == null) {
            return;
        }
        this.blockLoss.bind(minecraft.level);
        long start = Util.getNanos();
        if (this.scan.complete()) {
            this.startScan(player, drone);
        }
        if (this.scan.advance(this.blockLoss, CELL_BUDGET_PER_TICK)) {
            this.publishScan();
        }
        if (this.status == LinkStatus.CONNECTED && !Double.isInfinite(this.range(VideoSettings.get(), drone.build().video()))) {
            this.relay.tick(minecraft.level, player.getEyePosition(), antenna(drone), this.blockLoss, RelayClient.relayLimit());
        } else {
            this.relay.clear();
        }
        long spent = Util.getNanos() - start;
        this.scanTickNanos = spent;
        this.scanTickNanosMax = Math.max(this.scanTickNanosMax, spent);
        this.scanTickNanosTotal += spent;
        this.scanTicks++;
        this.updateMultipath(player, drone);
    }

    private void startScan(LocalPlayer player, QuadcopterEntity drone) {
        Vec3 eye = player.getEyePosition();
        Vec3 target = antenna(drone);
        double length = eye.distanceTo(target);
        VideoType type = drone.build().video();
        double reach = LinkBudget.maxReachBlocks(type.system, this.scanRange(VideoSettings.get(), type));
        if (length < 1.0E-3 || length > reach * 1.05 + 16.0) {
            this.scan.clear();
            return;
        }
        this.scan.start(eye.x, eye.y, eye.z, target.x, target.y, target.z);
    }

    private void publishScan() {
        this.scanLossDb = this.scan.lossDb();
        this.scanCenterLossDb = this.scan.centerLossDb();
        this.representativeCount = this.scan.representativeLosses(this.representative);
        this.scanRounds++;
        this.scanRoundRays = this.scan.roundRays();
        this.scanRoundCells = this.scan.roundCells();
    }

    private void updateMultipath(LocalPlayer player, QuadcopterEntity drone) {
        Vec3 target = antenna(drone);
        Vec3 eye = player.getEyePosition();
        double droneSide = Multipath.reflectors(target.x, target.y, target.z, this.blockLoss);
        double pilotSide = Multipath.reflectors(eye.x, eye.y, eye.z, this.blockLoss);
        double partial = this.relayUsed ? 0.0 : Multipath.partial(this.representative, this.representativeCount);
        float value = (float) Multipath.factor(droneSide, pilotSide, partial);
        this.multipath += (value - this.multipath) * MULTIPATH_SMOOTHING;
    }

    void onDisconnect(Minecraft minecraft) {
        this.testSource = null;
        this.overrideTestReceiver(false, null);
        this.testQuality = -1.0F;
        this.testTransmitter = null;
        this.deactivate();
        this.poses.clear();
    }

    private @Nullable QuadcopterEntity resolveSource() {
        PilotSession session = PilotSession.INSTANCE;
        if (session.isFpv()) {
            QuadcopterEntity drone = session.drone();
            if (drone != null) {
                return drone;
            }
        }
        if (!session.isActive()) {
            QuadcopterEntity watched = SpectateSession.INSTANCE.drone();
            if (watched != null) {
                return watched;
            }
        }
        return this.testSource;
    }

    private void begin(Minecraft minecraft, QuadcopterEntity drone, long now) {
        boolean switched = this.source != null && this.source != drone;
        this.relay.clear();
        this.relayUsed = false;
        this.source = drone;
        this.status = LinkStatus.CONNECTED;
        this.quality = 0.0F;
        this.rawQuality = 0.0F;
        this.multipath = 0.0F;
        this.activeSinceNanos = now;
        this.feed.reset();
        VideoPass.INSTANCE.retryAfterCompileFailure();
        if (switched) {
            this.poses.clear();
        }
        this.blockLoss.bind(minecraft.level);
        LocalPlayer player = minecraft.player;
        if (player != null) {
            this.startScan(player, drone);
            this.scan.evaluate(this.blockLoss);
            this.publishScan();
        }
    }

    private void deactivate() {
        this.status = LinkStatus.INACTIVE;
        this.source = null;
        this.system = null;
        this.receiver = null;
        this.videoType = null;
        this.creative = false;
        this.quality = 0.0F;
        this.rawQuality = 0.0F;
        this.distance = 0.0;
        this.obstructionDb = 0.0;
        this.multipath = 0.0F;
        this.relay.clear();
        this.relayUsed = false;
        this.directQuality = 0.0F;
        this.scan.clear();
        this.scanLossDb = 0.0;
        this.scanCenterLossDb = 0.0;
        this.representativeCount = 0;
        Arrays.fill(this.representative, 0.0);
        this.blockLoss.bind(null);
        this.feed.reset();
        this.poses.clear();
    }

    private double scanRange(VideoConfig config, VideoType type) {
        return this.creative ? Math.max(type.rangeBlocks, config.creativeRangeBlocks) : type.rangeBlocks;
    }

    private double range(VideoConfig config, VideoType type) {
        if (this.creative) {
            return config.rangeBlocks(VideoSystem.CREATIVE, type.rangeBlocks);
        }
        VideoSystem transmitter = this.system;
        if (transmitter != null && ClientWorldRules.INSTANCE.infiniteRange(transmitter)) {
            return Double.POSITIVE_INFINITY;
        }
        return type.rangeBlocks;
    }

    private boolean obstructs(VideoConfig config) {
        return config.obstructs(this.creative ? VideoSystem.CREATIVE : null);
    }

    private static Vec3 antenna(QuadcopterEntity drone) {
        return drone.position().add(0.0, drone.getBbHeight() * 0.5, 0.0);
    }

    public enum LinkStatus {
        INACTIVE,
        CONNECTED,
        NO_GOGGLES,
        WRONG_SYSTEM
    }
}
