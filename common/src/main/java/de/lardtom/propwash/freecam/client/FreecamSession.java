package de.lardtom.propwash.freecam.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.drone.TuneMapping;
import de.lardtom.propwash.drone.WindModel;
import de.lardtom.propwash.drone.client.FpvView;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.freecam.FreecamBounds;
import de.lardtom.propwash.freecam.FreecamPreset;
import de.lardtom.propwash.freecam.FreecamRules;
import de.lardtom.propwash.input.FlightModeSelector;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.RadioSource;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.client.InputKeys;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.Fluid;
import de.lardtom.propwash.sim.PilotInput;
import de.lardtom.propwash.sim.SimEnvironment;
import de.lardtom.propwash.sim.SimSnapshot;
import de.lardtom.propwash.sim.SimTuning;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.StepResult;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemedToast;
import de.lardtom.propwash.video.client.SpectateSession;
import de.lardtom.propwash.zone.ZoneField;
import de.lardtom.propwash.zone.client.ZonePhysics;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Util;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public final class FreecamSession {
    public static final FreecamSession INSTANCE = new FreecamSession();
    public static final Object TOAST_TOKEN = new Object();
    public static final String RULE = "allow_freecam";
    private static final long MAX_FRAME_NANOS = 100_000_000L;
    private static final long TOAST_MILLIS = 3000L;
    private static final double GROUND_PROBE = 5.0;
    private static final long EDGE_HOLD_NANOS = 400_000_000L;
    private static final float PI = (float) Math.PI;

    private @Nullable DroneSim sim;
    private @Nullable AirframeSpec params;
    private FreecamPreset preset = FreecamPreset.DEFAULT;
    private final PilotInput input = new PilotInput();
    private final SimEnvironment environment = new SimEnvironment();
    private final StepResult result = new StepResult();
    private final SimSnapshot snapshot = new SimSnapshot();
    private final ZoneField zoneField = new ZoneField();
    private final FlightModeSelector modes = new FlightModeSelector();
    private final FreecamBounds.Step boundsStep = new FreecamBounds.Step();
    private final Vector3d position = new Vector3d();
    private final Vector3d previous = new Vector3d();
    private final Vector3d velocity = new Vector3d();
    private final Quaterniond attitude = new Quaterniond();
    private final Quaternionf body = new Quaternionf();
    private final Vector3d park = new Vector3d();
    private final Quaterniond parkAttitude = new Quaterniond();
    private final Vector3f lens = new Vector3f();
    private boolean parked;
    private boolean armLatch;
    private boolean armKeyHeld;
    private boolean exitKeyHeld;
    private boolean cycleKeyHeld;
    private boolean acroKeyHeld;
    private boolean angleKeyHeld;
    private boolean horizonKeyHeld;
    private long frameNanos;
    private long edgeUntil;
    private int edgeHits;
    private int appliedRulesVersion = -1;
    private @Nullable ClientLevel level;
    private @Nullable ResourceKey<Level> dimension;
    private float lastHealth;
    private double anchorX;
    private double anchorZ;
    private int starts;
    private long frames;
    private FreecamRules.End lastEnd = FreecamRules.End.NONE;
    private FreecamRules.Denial lastDenial = FreecamRules.Denial.NONE;
    private StickPositions sticks = StickPositions.IDLE;
    private @Nullable StickPositions testSticks;
    private boolean testArm;
    private @Nullable StabMode testMode;

    private FreecamSession() {
    }

    public static boolean active() {
        return INSTANCE.sim != null;
    }

    public @Nullable DroneSim sim() {
        return this.sim;
    }

    public FreecamPreset preset() {
        return this.preset;
    }

    public @Nullable AirframeSpec params() {
        return this.params;
    }

    public boolean armed() {
        DroneSim s = this.sim;
        return s != null && s.telemetry().armed;
    }

    public boolean parked() {
        return this.parked;
    }

    public boolean edge() {
        return this.sim != null && Util.getNanos() - this.edgeUntil < 0L;
    }

    public int edgeHits() {
        return this.edgeHits;
    }

    public int starts() {
        return this.starts;
    }

    public long frames() {
        return this.frames;
    }

    public FreecamRules.End lastEnd() {
        return this.lastEnd;
    }

    public FreecamRules.Denial lastDenial() {
        return this.lastDenial;
    }

    public StabMode flightMode() {
        DroneSim s = this.sim;
        return s != null ? s.telemetry().mode : this.modes.mode();
    }

    public Vector3d position(Vector3d dest) {
        return dest.set(this.position);
    }

    public Vector3d velocity(Vector3d dest) {
        return dest.set(this.velocity);
    }

    public Quaternionf orientation(Quaternionf dest) {
        return dest.set(this.body);
    }

    public float cameraUptiltDeg() {
        return FreecamConfig.get().uptilt(this.preset);
    }

    public void setTestInput(@Nullable StickPositions sticks, boolean arm, @Nullable StabMode mode) {
        this.testSticks = sticks;
        this.testArm = arm;
        this.testMode = mode;
    }

    public boolean testInput() {
        return this.testSticks != null;
    }

    public StickPositions sticks() {
        return this.sticks;
    }

    public void clearTestInput() {
        this.testSticks = null;
        this.testArm = false;
        this.testMode = null;
    }

    public static FreecamRules.Denial denial(Minecraft mc) {
        return denial(mc, mc.gui.screen() != null);
    }

    public static FreecamRules.Denial denialFromMenu(Minecraft mc) {
        return denial(mc, false);
    }

    private static FreecamRules.Denial denial(Minecraft mc, boolean screenOpen) {
        LocalPlayer player = mc.player;
        ClientWorldRules rules = ClientWorldRules.INSTANCE;
        return FreecamRules.start(new FreecamRules.StartState(player != null && mc.level != null, rules.known(),
                rules.on(WorldRuleSpecs.ALLOW_FREECAM), player != null && player.isAlive(), PilotSession.INSTANCE.isActive(),
                SpectateSession.INSTANCE.active(), screenOpen));
    }

    public FreecamRules.Denial toggle(Minecraft mc) {
        if (active()) {
            this.end(mc, FreecamRules.End.KEY);
            return FreecamRules.Denial.NONE;
        }
        return this.tryStart(mc);
    }

    public FreecamRules.Denial tryStart(Minecraft mc) {
        FreecamRules.Denial denial = denial(mc);
        this.lastDenial = denial;
        if (!denial.allowed()) {
            String key = denial.translationKey();
            if (!key.isEmpty() && mc.player != null) {
                mc.player.sendOverlayMessage(Component.translatable(key));
            }
            return denial;
        }
        this.start(mc);
        return denial;
    }

    private void start(Minecraft mc) {
        LocalPlayer player = mc.player;
        ClientLevel world = mc.level;
        if (player == null || world == null) {
            return;
        }
        FreecamConfig config = FreecamConfig.get();
        this.preset = config.preset();
        Vec3 eye = player.getEyePosition();
        float yawRad = (float) Math.toRadians(player.getYRot());
        this.body.rotationY(PI - yawRad);
        Quaternionf simQ = new Quaternionf(this.body).rotateY(PI);
        AirframeSpec p = AirframeSpec.of(this.preset.build());
        this.lens.set((float) p.lensRight, (float) p.lensUp, (float) -p.lensForward);
        this.body.transform(this.lens);
        double cx = eye.x - this.lens.x;
        double cy = eye.y - this.lens.y;
        double cz = eye.z - this.lens.z;
        this.zoneField.reset();
        ZonePhysics.update(this.zoneField, world, cx, cy, cz, 0.0);
        this.appliedRulesVersion = ClientWorldRules.INSTANCE.version();
        DroneSim s = new DroneSim(p, TuneMapping.toConfig(config.tune(this.preset), p), this.tuning(), Util.getNanos() ^ player.getUUID().getLeastSignificantBits());
        s.reset(cx, cy, cz, 0.0);
        s.copyStateTo(this.snapshot);
        this.snapshot.qw = simQ.w;
        this.snapshot.qx = simQ.x;
        this.snapshot.qy = simQ.y;
        this.snapshot.qz = simQ.z;
        s.copyStateFrom(this.snapshot);
        this.sim = s;
        this.params = p;
        this.level = world;
        this.dimension = world.dimension();
        this.lastHealth = player.getHealth();
        this.anchorX = player.getX();
        this.anchorZ = player.getZ();
        this.position.set(cx, cy, cz);
        this.previous.set(this.position);
        this.velocity.zero();
        this.park.set(this.position);
        this.parkAttitude.set(simQ.x, simQ.y, simQ.z, simQ.w);
        this.attitude.set(this.parkAttitude);
        this.parked = true;
        this.armLatch = false;
        this.edgeUntil = Util.getNanos() - 1L;
        this.frameNanos = Util.getNanos();
        RadioState radio = RadioState.INSTANCE;
        this.modes.reset();
        if (radio.source() == RadioSource.KEYBOARD) {
            this.modes.startKeyboard(InputConfig.get().keyboard.startInAngle);
        }
        this.modes.observe(radioUsable(radio), radio.activeSwitches(), radio.readableSwitches());
        this.syncKeys();
        this.starts++;
        this.lastEnd = FreecamRules.End.NONE;
        FpvView.holdBody(player);
        FreecamClient.started(mc);
    }

    public void end(Minecraft mc, FreecamRules.End reason) {
        if (this.sim == null) {
            return;
        }
        this.sim = null;
        this.params = null;
        this.level = null;
        this.dimension = null;
        this.parked = false;
        this.armLatch = false;
        this.zoneField.reset();
        this.lastEnd = reason;
        if (!PilotSession.INSTANCE.isActive() && SpectateSession.INSTANCE.drone() == null) {
            FpvView.holdBody(null);
        }
        if (mc.player != null && mc.getCameraEntity() != mc.player && !PilotSession.INSTANCE.isActive()) {
            mc.setCameraEntity(mc.player);
        }
        String key = reason.translationKey();
        if (!key.isEmpty() && mc.player != null) {
            mc.player.sendOverlayMessage(Component.translatable(key));
        }
    }

    public void setPreset(FreecamPreset next) {
        FreecamConfig config = FreecamConfig.get();
        if (config.preset() != next) {
            config.preset = next.key();
            FreecamConfig.save();
        }
        DroneSim old = this.sim;
        if (old == null || this.preset == next) {
            this.preset = next;
            return;
        }
        this.preset = next;
        AirframeSpec p = AirframeSpec.of(next.build());
        old.copyStateTo(this.snapshot);
        DroneSim s = new DroneSim(p, TuneMapping.toConfig(config.tune(next), p), this.tuning(), Util.getNanos());
        s.reset(this.snapshot.px, this.snapshot.py, this.snapshot.pz, 0.0);
        SimSnapshot fresh = new SimSnapshot();
        s.copyStateTo(fresh);
        fresh.px = this.snapshot.px;
        fresh.py = this.snapshot.py;
        fresh.pz = this.snapshot.pz;
        fresh.vx = this.snapshot.vx;
        fresh.vy = this.snapshot.vy;
        fresh.vz = this.snapshot.vz;
        fresh.qw = this.snapshot.qw;
        fresh.qx = this.snapshot.qx;
        fresh.qy = this.snapshot.qy;
        fresh.qz = this.snapshot.qz;
        Quaterniond q = new Quaterniond(this.snapshot.qx, this.snapshot.qy, this.snapshot.qz, this.snapshot.qw).normalize();
        Vector3d spin = q.transformInverse(new Vector3d(this.snapshot.lx, this.snapshot.ly, this.snapshot.lz));
        AirframeSpec before = old.params();
        spin.set(spin.x / before.inertiaXx * p.inertiaXx, spin.y / before.inertiaYy * p.inertiaYy, spin.z / before.inertiaZz * p.inertiaZz);
        q.transform(spin);
        fresh.lx = spin.x;
        fresh.ly = spin.y;
        fresh.lz = spin.z;
        fresh.armed = this.snapshot.armed;
        fresh.sleeping = false;
        s.copyStateFrom(fresh);
        this.sim = s;
        this.params = p;
    }

    void testPlace(double x, double y, double z, float yawDeg) {
        DroneSim s = this.sim;
        if (s == null) {
            return;
        }
        Quaterniond q = new Quaterniond().rotationY(-Math.toRadians(yawDeg));
        s.copyStateTo(this.snapshot);
        this.snapshot.px = x;
        this.snapshot.py = y;
        this.snapshot.pz = z;
        this.snapshot.vx = 0.0;
        this.snapshot.vy = 0.0;
        this.snapshot.vz = 0.0;
        this.snapshot.lx = 0.0;
        this.snapshot.ly = 0.0;
        this.snapshot.lz = 0.0;
        this.snapshot.qw = q.w;
        this.snapshot.qx = q.x;
        this.snapshot.qy = q.y;
        this.snapshot.qz = q.z;
        s.copyStateFrom(this.snapshot);
        this.position.set(x, y, z);
        this.previous.set(x, y, z);
        this.velocity.zero();
        this.attitude.set(q);
        this.park.set(x, y, z);
        this.parkAttitude.set(q);
        this.body.set((float) q.x, (float) q.y, (float) q.z, (float) q.w).normalize().rotateY(PI);
    }

    void testPush(double vx, double vy, double vz) {
        DroneSim s = this.sim;
        if (s == null) {
            return;
        }
        s.copyStateTo(this.snapshot);
        this.snapshot.vx = vx;
        this.snapshot.vy = vy;
        this.snapshot.vz = vz;
        s.copyStateFrom(this.snapshot);
    }

    public void applyTune(Map<String, Float> values) {
        DroneSim s = this.sim;
        if (s != null) {
            s.setConfig(TuneMapping.toConfig(values, s.params()));
        }
    }

    public boolean cameraPose(Vector3d outPosition, Quaternionf outRotation) {
        AirframeSpec p = this.params;
        if (this.sim == null || p == null) {
            return false;
        }
        this.lens.set((float) p.lensRight, (float) p.lensUp, (float) -p.lensForward);
        this.body.transform(this.lens);
        outPosition.set(this.position.x + this.lens.x, this.position.y + this.lens.y, this.position.z + this.lens.z);
        outRotation.set(this.body).rotateX((float) Math.toRadians(this.cameraUptiltDeg()));
        return true;
    }

    public DroneBuild build() {
        return this.preset.build();
    }

    void onTick(Minecraft mc) {
        if (this.sim == null) {
            return;
        }
        FreecamRules.End end = this.endReason(mc);
        if (end != FreecamRules.End.NONE) {
            this.end(mc, end);
            return;
        }
        LocalPlayer player = mc.player;
        if (player != null) {
            this.lastHealth = player.getHealth();
        }
    }

    FreecamRules.End endReason(Minecraft mc) {
        LocalPlayer player = mc.player;
        ClientWorldRules rules = ClientWorldRules.INSTANCE;
        boolean inWorld = player != null && mc.level != null;
        boolean sameDimension = inWorld && mc.level == this.level && mc.level.dimension() == this.dimension;
        return FreecamRules.check(new FreecamRules.RunState(inWorld, rules.known(), rules.on(WorldRuleSpecs.ALLOW_FREECAM),
                player != null && player.isAlive(), player != null ? player.getHealth() : 0.0F, this.lastHealth, sameDimension,
                PilotSession.INSTANCE.isActive(), SpectateSession.INSTANCE.active()));
    }

    void onFrame(Minecraft mc) {
        DroneSim s = this.sim;
        if (s == null) {
            return;
        }
        long now = Util.getNanos();
        long elapsed = Math.max(0L, Math.min(MAX_FRAME_NANOS, now - this.frameNanos));
        this.frameNanos = now;
        FreecamRules.End end = this.endReason(mc);
        if (end == FreecamRules.End.PILOTING || end == FreecamRules.End.SPECTATING || end == FreecamRules.End.DISCONNECT
                || end == FreecamRules.End.DIMENSION || end == FreecamRules.End.RULE_OFF) {
            this.end(mc, end);
            return;
        }
        ClientLevel world = mc.level;
        if (world == null || mc.isPaused()) {
            return;
        }
        boolean keys = mc.gui.screen() == null;
        if (keys) {
            this.readKeys(mc);
            if (this.sim == null) {
                return;
            }
        } else {
            this.syncKeys();
            return;
        }
        this.syncTuning(world, s, elapsed * 1.0E-9);
        this.fillInput();
        this.fillEnvironment(world);
        this.previous.set(this.position);
        s.step(elapsed, this.input, this.environment, this.result);
        s.getPosition(this.position);
        this.applyBounds(world, s);
        this.applyPark(s);
        s.getAttitude(this.attitude);
        s.getVelocity(this.velocity);
        this.body.set((float) this.attitude.x, (float) this.attitude.y, (float) this.attitude.z, (float) this.attitude.w).normalize().rotateY(PI);
        this.frames++;
    }

    private SimTuning tuning() {
        PhysicsRules rules = this.zoneField.rules();
        SimTuning tuning = this.zoneField.apply(rules.toSimTuning());
        tuning.partCompatibility = false;
        tuning.infiniteBattery = true;
        tuning.batterySag = false;
        tuning.crashDisarm = false;
        tuning.propStrikes = false;
        tuning.thermalLimits = false;
        tuning.thermalEffects = false;
        tuning.sleepEnabled = false;
        return tuning;
    }

    private void syncTuning(Level world, DroneSim s, double seconds) {
        int version = ClientWorldRules.INSTANCE.version();
        boolean zones = ZonePhysics.update(this.zoneField, world, this.position.x, this.position.y, this.position.z, seconds);
        if (version != this.appliedRulesVersion || zones) {
            this.appliedRulesVersion = version;
            s.setTuning(this.tuning());
        }
    }

    private void applyBounds(Level world, DroneSim s) {
        FreecamBounds bounds = new FreecamBounds((cx, cz) -> world.getChunkSource().hasChunk(cx, cz), world.getMinY(), world.getMaxY() + 1.0, this.anchorX,
                this.anchorZ);
        FreecamBounds.Step step = bounds.step(this.previous.x, this.previous.y, this.previous.z, this.position.x, this.position.y,
                this.position.z, this.boundsStep);
        if (!step.edge()) {
            return;
        }
        this.edgeUntil = Util.getNanos() + EDGE_HOLD_NANOS;
        this.edgeHits++;
        s.copyStateTo(this.snapshot);
        this.snapshot.px = step.x;
        this.snapshot.py = step.y;
        this.snapshot.pz = step.z;
        if (step.blockedX) {
            this.snapshot.vx = 0.0;
        }
        if (step.blockedY) {
            this.snapshot.vy = 0.0;
        }
        if (step.blockedZ) {
            this.snapshot.vz = 0.0;
        }
        s.copyStateFrom(this.snapshot);
        this.position.set(step.x, step.y, step.z);
    }

    private void applyPark(DroneSim s) {
        if (s.telemetry().armed) {
            this.parked = false;
            return;
        }
        if (!this.parked) {
            this.parked = true;
            this.park.set(this.position);
            s.getAttitude(this.parkAttitude);
            level(this.parkAttitude);
        }
        s.copyStateTo(this.snapshot);
        this.snapshot.px = this.park.x;
        this.snapshot.py = this.park.y;
        this.snapshot.pz = this.park.z;
        this.snapshot.vx = 0.0;
        this.snapshot.vy = 0.0;
        this.snapshot.vz = 0.0;
        this.snapshot.lx = 0.0;
        this.snapshot.ly = 0.0;
        this.snapshot.lz = 0.0;
        this.snapshot.qw = this.parkAttitude.w;
        this.snapshot.qx = this.parkAttitude.x;
        this.snapshot.qy = this.parkAttitude.y;
        this.snapshot.qz = this.parkAttitude.z;
        this.snapshot.armed = false;
        s.copyStateFrom(this.snapshot);
        this.position.set(this.park);
    }

    static void level(Quaterniond q) {
        double fx = 2.0 * (q.x * q.z + q.w * q.y);
        double fz = 1.0 - 2.0 * (q.x * q.x + q.y * q.y);
        double heading = Math.atan2(fx, fz);
        q.rotationY(Double.isFinite(heading) ? heading : 0.0);
    }

    private void fillInput() {
        RadioState radio = RadioState.INSTANCE;
        PilotInput in = this.input;
        StickPositions test = this.testSticks;
        StickPositions sticks;
        boolean arm;
        StabMode mode;
        if (test != null) {
            sticks = test.clamped();
            arm = this.testArm;
            mode = this.testMode != null ? this.testMode : this.modes.mode();
        } else {
            boolean usable = radioUsable(radio);
            this.modes.observe(usable, radio.activeSwitches(), radio.readableSwitches());
            sticks = radio.sticks().clamped();
            arm = radio.isActive(SwitchFunction.ARM) || this.armLatch;
            mode = this.modes.mode();
        }
        this.sticks = sticks;
        in.roll = sticks.roll();
        in.pitch = sticks.pitch();
        in.yaw = sticks.yaw();
        in.throttle = sticks.throttle();
        in.mode = mode;
        in.armSwitch = arm;
        in.linkOk = true;
        in.altHold = false;
        in.posHold = false;
        in.mode3d = false;
        in.turtle = false;
        in.actions = 0;
    }

    private void fillEnvironment(Level world) {
        double x = this.position.x;
        double y = this.position.y;
        double z = this.position.z;
        SimEnvironment env = this.environment;
        env.groundDistance = groundDistance(world, x, y, z);
        env.solidDistance = Double.POSITIVE_INFINITY;
        env.fluid = Fluid.NONE;
        env.waterSurface = Double.NEGATIVE_INFINITY;
        env.airDensity = 1.225 * Math.exp(-Math.max(0.0, y - world.getSeaLevel()) / 8500.0);
        env.ambientTempC = 20.0;
        env.skyView = 1.0;
        env.snag.clear();
        WindModel.fill(world, x, y, z, env.groundDistance, env);
    }

    static double groundDistance(Level world, double x, double y, double z) {
        if (!world.getChunkSource().hasChunk(FreecamBounds.chunk(x), FreecamBounds.chunk(z))) {
            return Double.POSITIVE_INFINITY;
        }
        BlockPos inside = BlockPos.containing(x, y, z);
        if (!world.isOutsideBuildHeight(inside) && !world.getBlockState(inside).getCollisionShape(world, inside).isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }
        double floor = world.getMinY();
        if (y <= floor) {
            return Double.POSITIVE_INFINITY;
        }
        double probeEnd = Math.max(y - GROUND_PROBE, floor);
        BlockHitResult hit = world.clip(new ClipContext(new Vec3(x, y, z), new Vec3(x, probeEnd, z), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (hit.getType() == HitResult.Type.MISS) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(0.0, y - hit.getLocation().y);
    }

    private void readKeys(Minecraft mc) {
        if (edge(InputKeys.ARM, this.armKeyHeld)) {
            this.armLatch = !this.armLatch;
        }
        this.armKeyHeld = InputKeys.ARM.isDown();
        if (edge(InputKeys.FLIGHT_MODE, this.cycleKeyHeld)) {
            this.selectMode(mc, FlightModeSelector.next(this.modes.mode()));
        }
        this.cycleKeyHeld = InputKeys.FLIGHT_MODE.isDown();
        if (edge(InputKeys.MODE_ACRO, this.acroKeyHeld)) {
            this.selectMode(mc, StabMode.ACRO);
        }
        this.acroKeyHeld = InputKeys.MODE_ACRO.isDown();
        if (edge(InputKeys.MODE_ANGLE, this.angleKeyHeld)) {
            this.selectMode(mc, StabMode.ANGLE);
        }
        this.angleKeyHeld = InputKeys.MODE_ANGLE.isDown();
        if (edge(InputKeys.MODE_HORIZON, this.horizonKeyHeld)) {
            this.selectMode(mc, StabMode.HORIZON);
        }
        this.horizonKeyHeld = InputKeys.MODE_HORIZON.isDown();
        boolean exit = edge(InputKeys.EXIT, this.exitKeyHeld);
        this.exitKeyHeld = InputKeys.EXIT.isDown();
        if (exit) {
            this.end(mc, FreecamRules.End.KEY);
        }
    }

    private void syncKeys() {
        this.armKeyHeld = InputKeys.ARM.isDown();
        this.cycleKeyHeld = InputKeys.FLIGHT_MODE.isDown();
        this.acroKeyHeld = InputKeys.MODE_ACRO.isDown();
        this.angleKeyHeld = InputKeys.MODE_ANGLE.isDown();
        this.horizonKeyHeld = InputKeys.MODE_HORIZON.isDown();
        this.exitKeyHeld = InputKeys.EXIT.isDown();
    }

    private static boolean edge(KeyMapping key, boolean held) {
        return key.isDown() && !held;
    }

    private void selectMode(Minecraft mc, StabMode mode) {
        this.modes.select(mode);
        ThemedToast.show(mc.gui.toastManager(), TOAST_TOKEN, Component.translatable("propwash.drone.mode.changed", PilotSession.modeName(mode)), null,
                Palette.INFO, TOAST_MILLIS);
    }

    private static boolean radioUsable(RadioState radio) {
        RadioSource source = radio.source();
        return radio.connected() && (source == RadioSource.JOYSTICK || source == RadioSource.GAMEPAD);
    }
}
