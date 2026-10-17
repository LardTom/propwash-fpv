package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.accessory.client.AddonFlightClient;
import de.lardtom.propwash.api.part.AirframeGeometry;
import de.lardtom.propwash.audio.client.AudioHooks;
import de.lardtom.propwash.audio.client.Beep;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.core.event.Event;
import de.lardtom.propwash.drone.CameraMount;
import de.lardtom.propwash.drone.PilotBodyRules;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.WaterSurface;
import de.lardtom.propwash.drone.WindModel;
import de.lardtom.propwash.drone.RadioLink;
import de.lardtom.propwash.drone.TuneMapping;
import de.lardtom.propwash.drone.net.DroneResetS2C;
import de.lardtom.propwash.drone.net.DroneStateC2S;
import de.lardtom.propwash.drone.net.HardwareWearC2S;
import de.lardtom.propwash.drone.net.ImpactC2S;
import de.lardtom.propwash.drone.net.PilotRequestC2S;
import de.lardtom.propwash.drone.net.StateValidation;
import de.lardtom.propwash.input.FlightModeSelector;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.KeyRepeatFilter;
import de.lardtom.propwash.input.Mode3dSelector;
import de.lardtom.propwash.input.TurtleSelector;
import de.lardtom.propwash.input.RadioSource;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.interact.client.PilotInteraction;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.client.InputKeys;
import de.lardtom.propwash.input.client.TransmitterInput;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.race.AssistDetail;
import de.lardtom.propwash.relay.client.RelayClient;
import de.lardtom.propwash.relay.client.RelayControl;
import de.lardtom.propwash.rescue.client.RescueState;
import de.lardtom.propwash.server.client.ClientServerRules;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.ArmBlocker;
import de.lardtom.propwash.sim.CrashDamage;
import de.lardtom.propwash.sim.DisarmReason;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.sim.EventQueue;
import de.lardtom.propwash.sim.HoldMode;
import de.lardtom.propwash.sim.Fluid;
import de.lardtom.propwash.sim.ImpactLog;
import de.lardtom.propwash.sim.ImpactSample;
import de.lardtom.propwash.sim.MoveReport;
import de.lardtom.propwash.sim.PilotInput;
import de.lardtom.propwash.sim.SimEnvironment;
import de.lardtom.propwash.sim.SimSnapshot;
import de.lardtom.propwash.sim.SimTuning;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.StepResult;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemedToast;
import de.lardtom.propwash.video.client.ControlLinkScan;
import de.lardtom.propwash.video.client.VideoSettings;
import de.lardtom.propwash.video.client.VideoState;
import de.lardtom.propwash.zone.ZoneField;
import de.lardtom.propwash.zone.ZoneResolution;
import de.lardtom.propwash.zone.client.ClientZones;
import de.lardtom.propwash.video.client.VtxRuntime;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

public final class PilotSession {
    public static final PilotSession INSTANCE = new PilotSession();
    public static final Event<Consumer<QuadcopterEntity>> HOME_RESET = new Event<>("pilot_home_reset");
    static final long RESET_HOLD_NANOS = 5_000_000_000L;

    static final int[] LAYOUT_TO_SIM = {3, 1, 2, 0};
    public static final Object TOAST_TOKEN = new Object();
    private static final long TOAST_MILLIS = 3000L;
    private static final long HARDWARE_SEND_NANOS = 1_000_000_000L;
    private static final long MAX_FRAME_NANOS = 100_000_000L;
    private static final double SLICE_DISTANCE = 0.5;
    private static final int MAX_SLICES = 4;
    private static final double GROUND_PROBE = 5.0;
    private static final double SOLID_PROBE = 1.0;
    private static final double HIT_EPSILON = 1.0E-7;
    private static final double SUPPORT_PROBE = 0.05;
    private static final long LOW_BATTERY_INTERVAL = 3_000_000_000L;
    private static final long CRITICAL_BATTERY_INTERVAL = 1_000_000_000L;
    private static final int SUPPRESS_TICKS = 60;
    private static final float PI = (float) Math.PI;
    private static final int MAX_PENDING_IMPACTS = 16;

    private @Nullable QuadcopterEntity drone;
    private PilotMode mode = PilotMode.NONE;
    private @Nullable CameraType savedCameraType;
    private int lockedHotbarSlot = -1;
    private @Nullable DroneSim sim;
    private @Nullable AirframeSpec params;
    private final PilotInput input = new PilotInput();
    private final SimEnvironment environment = new SimEnvironment();
    private final StepResult result = new StepResult();
    private final MoveReport report = new MoveReport();
    private final SimSnapshot snapshot = new SimSnapshot();
    private final Quaterniond simOrientation = new Quaterniond();
    private final Quaternionf bodyOrientation = new Quaternionf();
    private final Vector3d velocity = new Vector3d();
    private final Vector3d comPosition = new Vector3d();
    private final float[] motorScratch = new float[QuadcopterEntity.MOTORS];

    private long physicsFrameNanos;
    private long lastSendNanos;
    private long nextLowBatteryBeep;
    private int epoch;
    private long resetHoldUntil;
    private int motorsPacked;
    private float linkStrength;
    private double rssiDbm = Double.NaN;
    private boolean armLatch;
    private boolean heldArmSwitch;
    private final FlightModeSelector modes = new FlightModeSelector();
    private final KeyRepeatFilter armKey = new KeyRepeatFilter();
    private final KeyRepeatFilter cycleKey = new KeyRepeatFilter();
    private final KeyRepeatFilter acroKey = new KeyRepeatFilter();
    private final KeyRepeatFilter angleKey = new KeyRepeatFilter();
    private final KeyRepeatFilter horizonKey = new KeyRepeatFilter();
    private final KeyRepeatFilter altHoldKey = new KeyRepeatFilter();
    private final KeyRepeatFilter posHoldKey = new KeyRepeatFilter();
    private final KeyRepeatFilter mode3dKey = new KeyRepeatFilter();
    private final Mode3dSelector mode3d = new Mode3dSelector();
    private boolean mode3dActive;
    private boolean lastMode3dAnnounced;
    private final KeyRepeatFilter turtleKey = new KeyRepeatFilter();
    private final TurtleSelector turtle = new TurtleSelector();
    private boolean turtleFlip;
    private Mode3dSelector.Block mode3dBlockShown = Mode3dSelector.Block.NONE;
    private boolean keyAltHold;
    private boolean keyPosHold;
    private boolean lastRadioAltHold;
    private boolean lastRadioPosHold;
    private boolean heldAltHold;
    private boolean heldPosHold;
    private boolean lastPosDenied;
    private boolean down;
    private int downFrames;
    private long downSince;
    private final SkyView skyView = new SkyView();
    private boolean lastFlipSwitch;
    private @Nullable FlightTune appliedTune;
    private @Nullable DroneBuild appliedBuild;
    private @Nullable PartWear appliedWear;
    private final List<ImpactSample> pendingImpacts = new ArrayList<>();
    private final float[] pendingHeatWear = new float[PartWear.POSITIONS];
    private boolean pendingEscBurn;
    private long lastHardwareSendNanos;
    private boolean derateNotified;
    private int appliedRulesVersion = -1;
    private int crashCount;
    private int suppressedDroneId = -1;
    private int suppressTicks;
    private int suppressedPilotUpdates;
    private boolean physicsModified;
    private PhysicsRules appliedPhysics = PhysicsRules.DEFAULT;
    private final ZoneField zoneField = new ZoneField();
    private double frameSeconds;
    private boolean radioSeen;
    private boolean ledsOff;
    private final ArmSwitchGuard armGuard = new ArmSwitchGuard();

    private @Nullable StickPositions testSticks;
    private boolean testArm;
    private @Nullable StabMode testMode;
    private @Nullable Boolean testMode3d;
    private @Nullable Boolean testTurtle;

    private PilotSession() {
    }

    public boolean isActive() {
        return this.drone != null && this.sim != null;
    }

    public PilotMode mode() {
        return this.isActive() ? this.mode : PilotMode.NONE;
    }

    public boolean isFpv() {
        return this.mode() == PilotMode.FPV;
    }

    public boolean isLineOfSight() {
        return this.mode() == PilotMode.LINE_OF_SIGHT;
    }

    public @Nullable QuadcopterEntity drone() {
        return this.isActive() ? this.drone : null;
    }

    public float linkStrength() {
        return this.isActive() ? this.linkStrength : 0.0F;
    }

    public double rssiDbm() {
        return this.isActive() ? this.rssiDbm : Double.NaN;
    }

    public @Nullable DroneSim sim() {
        return this.isActive() ? this.sim : null;
    }

    public long physicsFrameNanos() {
        return this.physicsFrameNanos;
    }

    public boolean altHoldRequested() {
        return this.isActive() && this.input.altHold;
    }

    public boolean posHoldRequested() {
        return this.isActive() && this.input.posHold;
    }

    void resetFlightModes() {
        this.modes.reset();
        this.mode3d.reset();
        this.turtle.reset();
    }

    public boolean turtleSelected() {
        return this.isActive() && this.input.turtle;
    }

    public boolean turtleArmed() {
        DroneSim s = this.sim;
        return this.isActive() && s != null && s.turtleArmed();
    }

    public boolean mode3dActive() {
        return this.isActive() && this.mode3dActive;
    }

    public boolean mode3dSelected() {
        return this.mode3d.selected();
    }

    public Mode3dSelector.Source mode3dSource() {
        return this.mode3d.source();
    }

    public Mode3dSelector.Block mode3dBlock() {
        return Mode3dSelector.block(InputConfig.get().mode3dUnlocked, mode3dAllowed(), RescueState.INSTANCE.active());
    }

    public boolean keyOverridesMode3dSwitch() {
        RadioState radio = RadioState.INSTANCE;
        return this.isActive() && this.testSticks == null
                && this.mode3d.keyOverridesSwitch(radioUsable(radio), radio.activeSwitches(), radio.readableSwitches());
    }

    public static boolean mode3dAllowed() {
        return ClientWorldRules.INSTANCE.on(WorldRuleSpecs.ALLOW_3D_MODE);
    }

    void clearHoldRequests() {
        this.keyAltHold = false;
        this.keyPosHold = false;
    }

    public HoldMode holdMode() {
        DroneSim s = this.sim();
        return s != null ? s.telemetry().holdMode : HoldMode.OFF;
    }

    public PhysicsRules appliedPhysics() {
        return this.sim() != null ? this.appliedPhysics : ClientWorldRules.INSTANCE.physics();
    }

    public boolean physicsModified() {
        return this.physicsModified;
    }

    public ZoneResolution zoneResolution() {
        return this.sim() != null ? this.zoneField.resolution() : ZoneResolution.world(ClientWorldRules.INSTANCE.physics());
    }

    public ZoneField zoneField() {
        return this.zoneField;
    }

    public List<AssistDetail> zoneBlendDetails() {
        return this.sim() != null ? this.zoneField.blendDetails() : List.of();
    }

    public StabMode flightMode() {
        DroneSim s = this.sim();
        return s != null ? s.telemetry().mode : this.modes.mode();
    }

    public FlightModeSelector.Source flightModeSource() {
        return this.modes.source();
    }

    public boolean keyOverridesModeSwitch() {
        RadioState radio = RadioState.INSTANCE;
        return this.isActive() && this.testSticks == null
                && this.modes.keyOverridesSwitch(radioUsable(radio), radio.activeSwitches(), radio.readableSwitches());
    }

    public StabMode previewFlightMode(String guid, int active, int readable) {
        if (!this.previewsRadio(guid)) {
            return this.flightMode();
        }
        RescueState rescue = RescueState.INSTANCE;
        return rescue.active() && rescue.forceSelfLevel() ? StabMode.ANGLE : this.modes.preview(true, active, readable);
    }

    public boolean keyOverridesModeSwitch(String guid, int active, int readable) {
        return this.previewsRadio(guid) && this.modes.keyOverridesSwitch(true, active, readable);
    }

    public boolean previewMode3d(String guid, int active, int readable, boolean unlocked) {
        if (!this.previewsRadio(guid)) {
            return this.mode3dActive();
        }
        return this.mode3d.preview(true, active, readable)
                && Mode3dSelector.block(unlocked, mode3dAllowed(), RescueState.INSTANCE.active()) == Mode3dSelector.Block.NONE;
    }

    public boolean keyOverridesMode3dSwitch(String guid, int active, int readable) {
        return this.previewsRadio(guid) && this.mode3d.keyOverridesSwitch(true, active, readable);
    }

    private boolean previewsRadio(String guid) {
        RadioState radio = RadioState.INSTANCE;
        return this.isActive() && this.testSticks == null && radioUsable(radio) && guid.equals(radio.deviceGuid());
    }

    public boolean armed() {
        DroneSim s = this.sim();
        return s != null && s.telemetry().armed;
    }

    public int armBlockers() {
        DroneSim s = this.sim();
        return s != null ? s.telemetry().armBlockers : 0;
    }

    public boolean ledsOff() {
        return this.isActive() && this.ledsOff;
    }

    public int crashCount() {
        return this.crashCount;
    }

    public float cameraUptiltDeg() {
        QuadcopterEntity d = this.drone();
        return d == null ? (float) AirframeGeometry.DEFAULT_CAMERA_TILT : CameraMount.uptilt(d.build(), d.tune().values());
    }

    public Vector3d comPosition(Vector3d dest) {
        return dest.set(this.comPosition);
    }

    public Quaternionf bodyOrientation(Quaternionf dest) {
        return dest.set(this.bodyOrientation);
    }

    public double lensForwardOffset() {
        AirframeSpec p = this.params;
        return p == null ? 0.0 : p.lensForward;
    }

    public double lensUpOffset() {
        AirframeSpec p = this.params;
        return p == null ? AirframeSpec.LEGACY_LENS_UP : p.lensUp;
    }

    public double lensRightOffset() {
        AirframeSpec p = this.params;
        return p == null ? 0.0 : p.lensRight;
    }

    public void setTestInput(@Nullable StickPositions sticks, boolean arm, @Nullable StabMode mode) {
        this.testSticks = sticks;
        this.testArm = arm;
        this.testMode = mode;
    }

    public void clearTestInput() {
        this.testSticks = null;
        this.testArm = false;
        this.testMode = null;
        this.testMode3d = null;
        this.testTurtle = null;
    }

    public void setTestTurtle(@Nullable Boolean active) {
        this.testTurtle = active;
    }

    public void setTestMode3d(@Nullable Boolean active) {
        this.testMode3d = active;
    }

    public void requestStop() {
        this.end(Minecraft.getInstance(), true);
    }

    void onDroneClientTick(QuadcopterEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        boolean mine = entity.isFlownBy(player.getUUID());
        if (entity == this.drone) {
            if (!mine) {
                this.end(mc, false);
            }
            return;
        }
        if (entity.getId() == this.suppressedDroneId && entity.pilot().isEmpty()) {
            this.clearSuppression();
        }
        if (!mine || this.drone != null) {
            return;
        }
        if (entity.getId() == this.suppressedDroneId && this.suppressTicks > 0) {
            if (entity.pilotUpdates() == this.suppressedPilotUpdates) {
                return;
            }
            this.clearSuppression();
        }
        this.start(mc, entity);
    }

    private void clearSuppression() {
        this.suppressedDroneId = -1;
        this.suppressTicks = 0;
    }

    void onClientTick(Minecraft mc) {
        if (this.suppressTicks > 0 && --this.suppressTicks == 0) {
            this.clearSuppression();
        }
        QuadcopterEntity d = this.drone;
        if (d == null) {
            this.drainKeys();
            return;
        }
        if (!this.stillValid(mc, d)) {
            this.endOrWait(mc, d, false);
            return;
        }
        PilotMode wanted = effectiveMode(d.pilotMode());
        if (wanted != this.mode) {
            this.applyMode(mc, d, wanted);
        } else if (this.mode == PilotMode.FPV && mc.getCameraEntity() != d) {
            mc.setCameraEntity(d);
        }
        this.holdHotbar(mc);
        FpvView.setPilotBody(mc.player);
        if (pressed(InputKeys.ARM, this.armKey)) {
            this.toggleArm(mc);
        }
        if (pressed(InputKeys.FLIGHT_MODE, this.cycleKey)) {
            this.selectMode(mc, FlightModeSelector.next(this.modes.mode()));
        }
        if (pressed(InputKeys.MODE_ACRO, this.acroKey)) {
            this.selectMode(mc, StabMode.ACRO);
        }
        if (pressed(InputKeys.MODE_ANGLE, this.angleKey)) {
            this.selectMode(mc, StabMode.ANGLE);
        }
        if (pressed(InputKeys.MODE_HORIZON, this.horizonKey)) {
            this.selectMode(mc, StabMode.HORIZON);
        }
        if (pressed(InputKeys.ALT_HOLD, this.altHoldKey)) {
            this.toggleAltHold(mc);
        }
        if (pressed(InputKeys.POS_HOLD, this.posHoldKey)) {
            this.togglePosHold(mc);
        }
        if (pressed(InputKeys.MODE_3D, this.mode3dKey)) {
            this.toggleMode3d(mc);
        }
        if (pressed(InputKeys.TURTLE, this.turtleKey)) {
            this.toggleTurtle(mc);
        }
        while (InputKeys.FLIP_UPRIGHT.consumeClick()) {
            this.flipUpright(mc);
        }
        while (InputKeys.EXIT.consumeClick()) {
            this.end(mc, true);
            return;
        }
    }

    void onFrame(Minecraft mc, DeltaTracker deltaTracker) {
        QuadcopterEntity d = this.drone;
        DroneSim s = this.sim;
        if (d == null || s == null) {
            return;
        }
        long now = Util.getNanos();
        long frameNanos = now - this.physicsFrameNanos;
        this.physicsFrameNanos = now;
        mc.getFramerateLimitTracker().onInputReceived();
        if (!this.stillValid(mc, d)) {
            this.endOrWait(mc, d, true);
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        if (this.resetHoldUntil != 0L) {
            if (now - this.resetHoldUntil < 0L && !columnLoaded(d.level(), d.getX(), d.getZ())) {
                return;
            }
            this.resetHoldUntil = 0L;
        }
        frameNanos = Math.max(0L, Math.min(MAX_FRAME_NANOS, frameNanos));
        this.frameSeconds = frameNanos * 1.0E-9;
        this.syncConfiguration(d, s);
        this.updateLinkQuality(mc, d);
        this.applySwitches(mc, now);
        this.applyLedSwitch();
        this.fillInput();
        AddonFlightClient.INSTANCE.beforeFrame(mc, d, s, this.input, this.appliedBuild);
        this.announceMode3d(mc);
        this.fillEnvironment(d);
        s.setVideoPowerDelta(VtxRuntime.INSTANCE.frame(mc, d, s, frameNanos * 1.0E-9));
        int slices = this.sliceCount(s, frameNanos);
        long remaining = frameNanos;
        for (int i = 0; i < slices; i++) {
            long part = i == slices - 1 ? remaining : frameNanos / slices;
            remaining -= part;
            EdgeGuard.INSTANCE.apply(mc, s, now);
            PilotInteraction.INSTANCE.beforeStep(d, s, this.environment, part);
            s.step(part, this.input, this.environment, this.result);
            this.moveEntity(d, s);
            this.collectImpacts(d, s);
            PilotInteraction.INSTANCE.afterSlice(d, s);
            this.handleEvents(mc, s, now);
            if (this.drone != d) {
                return;
            }
        }
        this.writeEntity(d, s);
        this.holdWarnings(mc, s);
        long pending = s.pendingNanos();
        long polled = TransmitterInput.INSTANCE.pollNanos();
        VideoState.INSTANCE.simFrame(polled == 0L ? 0L : now - polled, pending);
        VideoState.INSTANCE.record(now, this.comPosition.x, this.comPosition.y, this.comPosition.z, this.bodyOrientation);
        FpvShake.INSTANCE.update(d, s, now - pending);
        this.batteryWarnings(s, now);
        this.sendImpacts(d);
        PilotInteraction.INSTANCE.afterFrame(d, this.epoch);
        this.sendHardwareWear(d, now);
        long interval = 1_000_000_000L / DroneClientConfig.CONFIG.get().stateSendHz;
        if (now - this.lastSendNanos >= interval) {
            this.lastSendNanos = now;
            this.pushState(d, s);
        }
    }

    void onReset(DroneResetS2C payload) {
        QuadcopterEntity d = this.drone;
        DroneSim s = this.sim;
        if (d == null || s == null || d.getId() != payload.entityId()) {
            return;
        }
        this.epoch = payload.epoch();
        Quaternionf body = new Quaternionf(payload.qx(), payload.qy(), payload.qz(), payload.qw()).normalize();
        Quaternionf simQ = new Quaternionf(body).rotateY(PI);
        double com = -this.paramsOrDefault().hitboxFootOffset;
        s.copyStateTo(this.snapshot);
        this.snapshot.px = payload.x();
        this.snapshot.py = payload.y() + com;
        this.snapshot.pz = payload.z();
        this.snapshot.vx = 0.0;
        this.snapshot.vy = 0.0;
        this.snapshot.vz = 0.0;
        this.snapshot.lx = 0.0;
        this.snapshot.ly = 0.0;
        this.snapshot.lz = 0.0;
        this.snapshot.qw = simQ.w;
        this.snapshot.qx = simQ.x;
        this.snapshot.qy = simQ.y;
        this.snapshot.qz = simQ.z;
        s.copyStateFrom(this.snapshot);
        if (payload.home()) {
            s.forceDisarm(DisarmReason.FORCED);
            s.requireArmSwitchReset();
            this.motorsPacked = 0;
            this.resetHoldUntil = Util.getNanos() + RESET_HOLD_NANOS;
        }
        this.bodyOrientation.set(body);
        this.comPosition.set(payload.x(), payload.y() + com, payload.z());
        VideoState.INSTANCE.cut(Util.getNanos());
        d.snapClientPose(new Vec3(payload.x(), payload.y(), payload.z()), body);
        d.applyLocalState(body, s.telemetry().armed, (float) s.telemetry().batteryCharge, this.motorsPacked, this.ledsOff);
        this.lastSendNanos = 0L;
        if (payload.home()) {
            HOME_RESET.fire(listener -> listener.accept(d));
        }
    }

    void onDisconnect(Minecraft mc) {
        this.end(mc, false);
        this.clearSuppression();
        this.clearTestInput();
    }

    private void endOrWait(Minecraft mc, QuadcopterEntity d, boolean frame) {
        if (!pilotDown(mc, d)) {
            this.endInvalid(mc, d);
            return;
        }
        long now = Util.getNanos();
        if (!this.down) {
            this.down = true;
            this.downFrames = 0;
            this.downSince = now;
        }
        if (frame) {
            this.downFrames++;
        }
        if (PilotBodyRules.leaveAfterDeath(this.downFrames, now - this.downSince)) {
            this.end(mc, true);
        }
    }

    private static boolean pilotDown(Minecraft mc, QuadcopterEntity d) {
        LocalPlayer player = mc.player;
        return player != null && mc.level != null && !d.isRemoved() && d.level() == mc.level && !player.isAlive()
                && d.isFlownBy(player.getUUID());
    }

    public boolean pilotDown() {
        QuadcopterEntity d = this.drone;
        return d != null && pilotDown(Minecraft.getInstance(), d);
    }

    private void endInvalid(Minecraft mc, QuadcopterEntity d) {
        LocalPlayer player = mc.player;
        Entity.RemovalReason reason = d.getRemovalReason();
        boolean goneOnServer = reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED;
        boolean released = player != null && !d.isRemoved() && !d.isFlownBy(player.getUUID());
        boolean notify = player != null && player.isAlive() && !goneOnServer && !released;
        this.end(mc, notify);
    }

    private void drainKeys() {
        InputKeys.drain(InputKeys.ARM);
        InputKeys.drain(InputKeys.FLIGHT_MODE);
        InputKeys.drain(InputKeys.MODE_ACRO);
        InputKeys.drain(InputKeys.MODE_ANGLE);
        InputKeys.drain(InputKeys.MODE_HORIZON);
        InputKeys.drain(InputKeys.ALT_HOLD);
        InputKeys.drain(InputKeys.POS_HOLD);
        InputKeys.drain(InputKeys.MODE_3D);
        InputKeys.drain(InputKeys.FLIP_UPRIGHT);
        InputKeys.drain(InputKeys.TURTLE);
        InputKeys.drain(InputKeys.EXIT);
        this.armKey.sync(InputKeys.ARM.isDown());
        this.cycleKey.sync(InputKeys.FLIGHT_MODE.isDown());
        this.acroKey.sync(InputKeys.MODE_ACRO.isDown());
        this.angleKey.sync(InputKeys.MODE_ANGLE.isDown());
        this.horizonKey.sync(InputKeys.MODE_HORIZON.isDown());
        this.altHoldKey.sync(InputKeys.ALT_HOLD.isDown());
        this.posHoldKey.sync(InputKeys.POS_HOLD.isDown());
        this.mode3dKey.sync(InputKeys.MODE_3D.isDown());
        this.turtleKey.sync(InputKeys.TURTLE.isDown());
    }

    private static boolean pressed(KeyMapping key, KeyRepeatFilter filter) {
        int clicks = 0;
        while (key.consumeClick()) {
            clicks++;
        }
        return filter.pressed(clicks, key.isDown());
    }

    private boolean stillValid(Minecraft mc, QuadcopterEntity d) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || d.isRemoved() || d.level() != mc.level) {
            return false;
        }
        return player.isAlive() && d.isFlownBy(player.getUUID());
    }

    private void start(Minecraft mc, QuadcopterEntity d) {
        d.getInterpolation().cancel();
        d.setOldPosAndRot();
        DroneBuild build = PilotInteraction.flightBuild(d);
        AirframeSpec p = AirframeSpec.of(build);
        long seed = d.getUUID().getMostSignificantBits() ^ Util.getNanos();
        this.zoneField.reset();
        this.zoneField.update(ClientZones.INSTANCE.zones(), ClientZones.dimension(d.level()), ClientWorldRules.INSTANCE.physics(), d.getX(), d.getY(),
                d.getZ(), 0.0);
        PhysicsRules rules = this.zoneField.rules();
        this.appliedPhysics = rules;
        this.physicsModified = this.zoneField.assisted();
        DroneSim s = new DroneSim(p, TuneMapping.toConfig(d.tune().values(), p), this.zoneField.apply(simTuning(rules)), seed);
        s.setBatteryCharge(d.batteryCharge());
        s.setWear(d.wear());
        double com = -p.hitboxFootOffset;
        Quaternionf body = new Quaternionf(d.orientation()).normalize();
        Quaternionf simQ = new Quaternionf(body).rotateY(PI);
        s.reset(d.getX(), d.getY() + com, d.getZ(), 0.0);
        s.copyStateTo(this.snapshot);
        this.snapshot.qw = simQ.w;
        this.snapshot.qx = simQ.x;
        this.snapshot.qy = simQ.y;
        this.snapshot.qz = simQ.z;
        s.copyStateFrom(this.snapshot);
        this.drone = d;
        this.sim = s;
        this.params = p;
        this.appliedBuild = build;
        this.appliedTune = d.tune();
        this.appliedWear = d.wear();
        this.pendingImpacts.clear();
        this.clearHardwareWear();
        PilotInteraction.INSTANCE.start();
        EdgeGuard.INSTANCE.reset();
        this.appliedRulesVersion = ClientWorldRules.INSTANCE.version();
        this.epoch = d.resetEpoch();
        this.resetHoldUntil = 0L;
        this.armLatch = false;
        RadioState radio = RadioState.INSTANCE;
        if (radio.source() == RadioSource.KEYBOARD) {
            this.modes.startKeyboard(InputConfig.get().keyboard.startInAngle);
        }
        this.modes.observe(radioUsable(radio), radio.activeSwitches(), radio.readableSwitches());
        this.mode3d.observe(radioUsable(radio), radio.activeSwitches(), radio.readableSwitches());
        this.turtle.observe(radioUsable(radio), radio.activeSwitches(), radio.readableSwitches());
        this.turtleFlip = false;
        this.mode3dActive = this.mode3d.active(InputConfig.get().mode3dUnlocked, mode3dAllowed(), false);
        this.lastMode3dAnnounced = this.mode3dActive;
        this.mode3dBlockShown = Mode3dSelector.Block.NONE;
        TransmitterInput.INSTANCE.setThrottle3d(this.mode3dActive);
        this.lastFlipSwitch = RadioState.INSTANCE.isActive(SwitchFunction.FLIP_OVER);
        this.physicsFrameNanos = Util.getNanos();
        this.lastSendNanos = 0L;
        this.nextLowBatteryBeep = 0L;
        this.radioSeen = radioUsable(RadioState.INSTANCE);
        this.ledsOff = RadioState.INSTANCE.isActive(SwitchFunction.LEDS_OFF);
        this.heldArmSwitch = false;
        this.armGuard.sync(RadioState.INSTANCE.armRevision());
        this.keyAltHold = false;
        this.keyPosHold = false;
        this.heldAltHold = false;
        this.heldPosHold = false;
        this.lastRadioAltHold = RadioState.INSTANCE.isActive(SwitchFunction.ALT_HOLD);
        this.lastRadioPosHold = RadioState.INSTANCE.isActive(SwitchFunction.POS_HOLD);
        this.lastPosDenied = false;
        this.skyView.reset();
        this.crashCount = 0;
        this.motorsPacked = 0;
        this.bodyOrientation.set(body);
        this.comPosition.set(d.getX(), d.getY() + com, d.getZ());
        this.linkStrength = RadioState.INSTANCE.connected() ? 1.0F : 0.0F;
        this.mode = PilotMode.NONE;
        this.savedCameraType = null;
        this.lockedHotbarSlot = mc.player != null ? mc.player.getInventory().getSelectedSlot() : -1;
        this.applyMode(mc, d, effectiveMode(d.pilotMode()));
        FpvView.setPilotBody(mc.player);
        if (mc.player != null && !RadioState.INSTANCE.connected() && this.testSticks == null) {
            overlay(mc, Component.translatable("propwash.drone.session.no_radio"), Palette.WARNING);
        }
    }

    static PilotMode effectiveMode(PilotMode synced) {
        return synced == PilotMode.LINE_OF_SIGHT ? PilotMode.LINE_OF_SIGHT : PilotMode.FPV;
    }

    private void applyMode(Minecraft mc, QuadcopterEntity d, PilotMode next) {
        this.mode = next;
        if (next == PilotMode.FPV) {
            this.restoreCameraType(mc);
            mc.setCameraEntity(d);
            return;
        }
        if (mc.player != null && mc.getCameraEntity() != mc.player) {
            mc.setCameraEntity(mc.player);
        }
        if (this.savedCameraType == null) {
            this.savedCameraType = mc.options.getCameraType();
        }
        if (mc.options.getCameraType() != CameraType.FIRST_PERSON) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }
        VideoState.INSTANCE.cut(Util.getNanos());
    }

    private void restoreCameraType(Minecraft mc) {
        CameraType saved = this.savedCameraType;
        this.savedCameraType = null;
        if (saved != null && mc.options.getCameraType() != saved) {
            mc.options.setCameraType(saved);
        }
    }

    private void holdHotbar(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || this.lockedHotbarSlot < 0) {
            return;
        }
        if (player.getInventory().getSelectedSlot() != this.lockedHotbarSlot) {
            player.getInventory().setSelectedSlot(this.lockedHotbarSlot);
        }
    }

    private void end(Minecraft mc, boolean notifyServer) {
        QuadcopterEntity d = this.drone;
        if (d == null) {
            return;
        }
        if (notifyServer && ClientBus.inSession()) {
            ClientNet.sendToServer(new PilotRequestC2S(d.getId(), false));
        }
        if (notifyServer) {
            this.suppressedDroneId = d.getId();
            this.suppressTicks = SUPPRESS_TICKS;
            this.suppressedPilotUpdates = d.pilotUpdates();
        }
        d.clearLocalState();
        AddonFlightClient.INSTANCE.end();
        if (this.mode == PilotMode.LINE_OF_SIGHT) {
            this.restoreCameraType(mc);
        }
        this.savedCameraType = null;
        this.mode = PilotMode.NONE;
        this.lockedHotbarSlot = -1;
        this.drone = null;
        this.sim = null;
        this.params = null;
        this.appliedTune = null;
        this.appliedBuild = null;
        this.appliedWear = null;
        this.pendingImpacts.clear();
        this.clearHardwareWear();
        PilotInteraction.INSTANCE.reset();
        EdgeGuard.INSTANCE.reset();
        this.armLatch = false;
        this.linkStrength = 0.0F;
        this.motorsPacked = 0;
        this.radioSeen = false;
        this.ledsOff = false;
        this.heldArmSwitch = false;
        this.keyAltHold = false;
        this.keyPosHold = false;
        this.heldAltHold = false;
        this.heldPosHold = false;
        this.lastPosDenied = false;
        this.down = false;
        this.downFrames = 0;
        this.input.altHold = false;
        this.input.posHold = false;
        this.input.mode3d = false;
        this.input.turtle = false;
        this.turtleFlip = false;
        this.mode3dActive = false;
        TransmitterInput.INSTANCE.setThrottle3d(false);
        this.physicsModified = false;
        this.appliedPhysics = PhysicsRules.DEFAULT;
        this.zoneField.reset();
        FpvView.setPilotBody(null);
        if (mc.getCameraEntity() == d && mc.player != null) {
            mc.setCameraEntity(mc.player);
        }
    }

    static SimTuning simTuning(PhysicsRules rules) {
        SimTuning tuning = rules.toSimTuning();
        tuning.partCompatibility = ClientWorldRules.INSTANCE.partCompatibility().enforced();
        return tuning;
    }

    private AirframeSpec paramsOrDefault() {
        AirframeSpec p = this.params;
        return p != null ? p : AirframeSpec.of(DroneBuild.FREESTYLE);
    }

    private void syncConfiguration(QuadcopterEntity d, DroneSim s) {
        DroneBuild build = PilotInteraction.flightBuild(d);
        if (!build.equals(this.appliedBuild)) {
            if (PilotInteraction.sameAirframe(build, this.appliedBuild)) {
                s.setAirframe(AirframeSpec.of(build));
                this.appliedBuild = build;
                this.params = s.params();
            } else if (s.setBuild(build)) {
                this.appliedBuild = build;
                this.params = s.params();
                this.appliedTune = null;
            }
        }
        FlightTune tune = d.tune();
        if (!tune.equals(this.appliedTune)) {
            this.appliedTune = tune;
            s.setConfig(TuneMapping.toConfig(tune.values(), s.params()));
        }
        s.setWaterproof(d.waterproof() || ClientWorldRules.INSTANCE.on(WorldRuleSpecs.WATERPROOF_DRONES));
        PartWear wear = d.wear();
        if (!wear.equals(this.appliedWear)) {
            this.appliedWear = wear;
            s.setWear(wear);
        }
        int rulesVersion = ClientWorldRules.INSTANCE.version();
        boolean zones = this.zoneField.update(ClientZones.INSTANCE.zones(), ClientZones.dimension(d.level()), ClientWorldRules.INSTANCE.physics(),
                d.getX(), d.getY(), d.getZ(), this.frameSeconds);
        if (this.appliedRulesVersion != rulesVersion || zones) {
            this.appliedRulesVersion = rulesVersion;
            PhysicsRules rules = this.zoneField.rules();
            s.setTuning(this.zoneField.apply(simTuning(rules)));
            if (this.appliedPhysics.infiniteBattery() && !rules.infiniteBattery()) {
                s.keepBatteryCharge();
            }
            this.appliedPhysics = rules;
        }
        this.physicsModified = this.zoneField.assisted();
    }

    void applyTuneLocally(FlightTune tune) {
        DroneSim s = this.sim;
        if (s != null) {
            this.appliedTune = tune;
            s.setConfig(TuneMapping.toConfig(tune.values(), s.params()));
        }
    }

    private void updateLinkQuality(Minecraft mc, QuadcopterEntity d) {
        this.rssiDbm = Double.NaN;
        if (this.testSticks != null) {
            this.linkStrength = 1.0F;
            return;
        }
        if (!RadioState.INSTANCE.connected() || mc.player == null) {
            this.linkStrength = 0.0F;
            return;
        }
        double distance = mc.player.position().distanceTo(d.position());
        boolean free = RadioLink.freeRange(mc.player) || ClientWorldRules.INSTANCE.infiniteRange(d.build().video().system);
        double creativeRange = ContentIds.wornGogglesSystem(mc.player).filter(system -> system == VideoSystem.CREATIVE).isPresent()
                ? VideoSettings.get().creativeRangeBlocks : 0.0;
        double serverLimit = ClientServerRules.INSTANCE.maxPilotDistance();
        double shadow = free ? 0.0 : ControlLinkScan.INSTANCE.videoBandLossDb(d);
        float direct = RadioLink.quality(distance, serverLimit, free, creativeRange, shadow);
        this.rssiDbm = free ? Double.NaN : RadioLink.rssiDbm(distance, shadow);
        this.linkStrength = RelayControl.INSTANCE.quality(mc.level, mc.player.position(), d.position(), direct, serverLimit, free, creativeRange,
                RelayClient.relayLimit());
    }

    static float linkStrengthAt(double distance, double serverLimit) {
        return RadioLink.quality(distance, serverLimit);
    }

    private void fillInput() {
        RadioState radio = RadioState.INSTANCE;
        RescueState rescue = RescueState.INSTANCE;
        PilotInput in = this.input;
        StickPositions sticks;
        StabMode mode;
        boolean armSwitch;
        boolean linkOk;
        boolean altHold;
        boolean posHold;
        StickPositions test = this.testSticks;
        boolean rescueActive = rescue.active();
        boolean unlocked = InputConfig.get().mode3dUnlocked;
        boolean allowed = mode3dAllowed();
        boolean threeD;
        boolean turtleOn;
        float throttle;
        if (test != null) {
            sticks = test.clamped();
            mode = this.testMode != null ? this.testMode : this.modes.mode();
            armSwitch = this.testArm;
            linkOk = true;
            altHold = this.keyAltHold;
            posHold = this.keyPosHold;
            threeD = this.testMode3d != null ? this.testMode3d : this.mode3d.active(unlocked, allowed, rescueActive);
            turtleOn = this.testTurtle != null ? this.testTurtle : this.turtle.selected();
            throttle = sticks.throttle();
        } else {
            this.armGuard.check(radio.armRevision(), this.sim);
            if (radio.source() == RadioSource.KEYBOARD) {
                this.radioSeen = false;
            }
            boolean radioLost = this.radioSeen && !radioUsable(radio);
            if (!radioLost && radioUsable(radio)) {
                this.radioSeen = true;
            }
            sticks = rescueActive ? rescue.commandedSticks().clamped() : radioLost ? StickPositions.IDLE : radio.sticks().clamped();
            if (radioLost) {
                armSwitch = this.heldArmSwitch || this.armLatch;
                altHold = this.heldAltHold;
                posHold = this.heldPosHold;
            } else {
                boolean radioAlt = radio.isActive(SwitchFunction.ALT_HOLD);
                boolean radioPos = radio.isActive(SwitchFunction.POS_HOLD);
                if (this.lastRadioAltHold && !radioAlt) {
                    this.keyAltHold = false;
                }
                if (this.lastRadioPosHold && !radioPos) {
                    this.keyPosHold = false;
                }
                this.lastRadioAltHold = radioAlt;
                this.lastRadioPosHold = radioPos;
                altHold = radioAlt || this.keyAltHold;
                posHold = radioPos || this.keyPosHold;
                this.heldAltHold = altHold;
                this.heldPosHold = posHold;
                this.modes.observe(radioUsable(radio), radio.activeSwitches(), radio.readableSwitches());
                this.mode3d.observe(radioUsable(radio), radio.activeSwitches(), radio.readableSwitches());
                this.turtle.observe(radioUsable(radio), radio.activeSwitches(), radio.readableSwitches());
                boolean radioArm = radio.isActive(SwitchFunction.ARM);
                armSwitch = radioArm || this.armLatch;
                this.heldArmSwitch = radioArm;
            }
            mode = rescueActive && rescue.forceSelfLevel() ? StabMode.ANGLE : this.modes.mode();
            linkOk = rescueActive || (!radioLost && radio.connected() && this.linkStrength > 0.0F);
            if (rescueActive) {
                altHold = false;
                posHold = false;
            }
            threeD = this.mode3d.active(unlocked, allowed, rescueActive);
            turtleOn = this.turtle.selected() && !rescueActive;
            throttle = !threeD ? sticks.throttle() : radioLost ? 0.5F : radio.throttle3d();
        }
        this.mode3dActive = threeD;
        TransmitterInput.INSTANCE.setThrottle3d(threeD);
        in.roll = sticks.roll();
        in.pitch = sticks.pitch();
        in.yaw = sticks.yaw();
        in.throttle = throttle;
        in.mode3d = threeD;
        in.turtle = turtleOn;
        in.mode = mode;
        in.armSwitch = armSwitch;
        in.linkOk = linkOk;
        in.altHold = altHold;
        in.posHold = posHold;
    }

    private static boolean radioUsable(RadioState radio) {
        RadioSource source = radio.source();
        return radio.connected() && (source == RadioSource.JOYSTICK || source == RadioSource.GAMEPAD);
    }

    private void fillEnvironment(QuadcopterEntity d) {
        Level level = d.level();
        double com = -this.paramsOrDefault().hitboxFootOffset;
        double x = d.getX();
        double y = d.getY() + com;
        double z = d.getZ();
        SimEnvironment env = this.environment;
        env.groundDistance = this.groundDistance(level, d, x, y, z);
        env.solidDistance = env.groundDistance < SOLID_PROBE ? solidDistance(level, d, x, y, z) : Double.POSITIVE_INFINITY;
        env.fluid = fluidAt(level, x, y, z);
        env.waterSurface = WaterSurface.relative(level, x, y, z);
        env.airDensity = 1.225 * Math.exp(-Math.max(0.0, y - level.getSeaLevel()) / 8500.0);
        env.ambientTempC = 20.0;
        env.skyView = this.skyView.sample(level, x, y, z, Util.getNanos());
        WindModel.fill(level, x, y, z, env.groundDistance, env);
    }

    private double groundDistance(Level level, QuadcopterEntity d, double x, double y, double z) {
        if (!columnLoaded(level, x, z)) {
            return Double.POSITIVE_INFINITY;
        }
        double floor = level.getMinY();
        if (y <= floor) {
            return 0.0;
        }
        double probeEnd = Math.max(y - GROUND_PROBE, floor);
        Vec3 from = new Vec3(x, y, z);
        Vec3 to = new Vec3(x, probeEnd, z);
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, d));
        if (hit.getType() == HitResult.Type.MISS) {
            return probeEnd <= floor ? y - floor : Double.POSITIVE_INFINITY;
        }
        return Math.max(0.0, y - hit.getLocation().y);
    }

    private static double solidDistance(Level level, QuadcopterEntity d, double x, double y, double z) {
        BlockHitResult hit = level.clip(new ClipContext(new Vec3(x, y, z), new Vec3(x, y - SOLID_PROBE, z), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, d));
        return hit.getType() == HitResult.Type.MISS ? Double.POSITIVE_INFINITY : Math.max(0.0, y - hit.getLocation().y);
    }

    static boolean columnLoaded(Level level, double x, double z) {
        return level.hasChunk(SectionPos.posToSectionCoord(x), SectionPos.posToSectionCoord(z));
    }

    private static Fluid fluidAt(Level level, double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        if (!columnLoaded(level, x, z) || level.isOutsideBuildHeight(pos)) {
            return Fluid.NONE;
        }
        FluidState fluid = level.getFluidState(pos);
        if (fluid.isEmpty() || y > pos.getY() + fluid.getHeight(level, pos)) {
            return Fluid.NONE;
        }
        return fluid.is(FluidTags.LAVA) ? Fluid.LAVA : Fluid.WATER;
    }

    private int sliceCount(DroneSim s, long frameNanos) {
        double speed = s.telemetry().speed;
        if (!Double.isFinite(speed)) {
            return 1;
        }
        int slices = (int) Math.ceil(speed * frameNanos * 1.0E-9 / SLICE_DISTANCE);
        return Math.max(1, Math.min(MAX_SLICES, slices));
    }

    private void moveEntity(QuadcopterEntity d, DroneSim s) {
        MoveReport r = this.report;
        r.clear();
        double rx = this.result.dx;
        double ry = this.result.dy;
        double rz = this.result.dz;
        if (!Double.isFinite(rx) || !Double.isFinite(ry) || !Double.isFinite(rz)) {
            rx = 0.0;
            ry = 0.0;
            rz = 0.0;
        }
        double bx = d.getX();
        double by = d.getY();
        double bz = d.getZ();
        boolean moved = rx != 0.0 || ry != 0.0 || rz != 0.0;
        if (moved) {
            d.move(MoverType.SELF, PilotInteraction.INSTANCE.clip(d, s, new Vec3(rx, ry, rz)));
            PilotInteraction.INSTANCE.afterMove(d);
        }
        double ax = d.getX() - bx;
        double ay = d.getY() - by;
        double az = d.getZ() - bz;
        r.requestedX = rx;
        r.requestedY = ry;
        r.requestedZ = rz;
        r.actualX = ax;
        r.actualY = ay;
        r.actualZ = az;
        double ex = rx;
        double ey = ry;
        double ez = rz;
        if (moved && d.obliqueContact()) {
            Vec3 normal = d.obliqueNormal();
            Vec3 wanted = d.obliqueDelta();
            r.contactNormalX = normal.x;
            r.contactNormalY = normal.y;
            r.contactNormalZ = normal.z;
            ex = wanted.x;
            ey = wanted.y;
            ez = wanted.z;
        }
        r.hitX = Math.abs(ax - ex) > HIT_EPSILON;
        r.hitY = Math.abs(ay - ey) > HIT_EPSILON;
        r.hitZ = Math.abs(az - ez) > HIT_EPSILON;
        Telemetry t = s.telemetry();
        if (r.hitX || r.hitY || r.hitZ || r.obliqueContact() || t.grounded) {
            this.fillSurface(d, r);
        }
        if (t.grounded || t.sleeping) {
            r.supportGap = d.supportGap(SUPPORT_PROBE);
        }
        s.afterMove(r);
        d.setOldPosAndRot();
    }

    private void fillSurface(QuadcopterEntity d, MoveReport r) {
        BlockPos below = d.getBlockPosBelowThatAffectsMyMovement();
        if (!columnLoaded(d.level(), below.getX(), below.getZ()) || d.level().isOutsideBuildHeight(below)) {
            return;
        }
        BlockState state = d.level().getBlockState(below);
        float slip = state.getBlock().getFriction();
        r.surfaceGrip = Math.max(0.05, Math.min(1.5, (1.0 - slip) / 0.4));
        if (state.is(Blocks.SLIME_BLOCK)) {
            r.surfaceRestitution = 0.8;
        } else if (state.is(Blocks.HONEY_BLOCK)) {
            r.surfaceRestitution = 0.0;
            r.surfaceGrip = 1.5;
        }
    }

    private void writeEntity(QuadcopterEntity d, DroneSim s) {
        Quaterniond q = this.result.orientation;
        this.bodyOrientation.set((float) q.x, (float) q.y, (float) q.z, (float) q.w).normalize().rotateY(PI);
        double com = -this.paramsOrDefault().hitboxFootOffset;
        this.comPosition.set(d.getX(), d.getY() + com, d.getZ());
        Telemetry t = s.telemetry();
        double rpmMax = this.paramsOrDefault().omegaMax * 60.0 / (2.0 * Math.PI);
        float[] out = this.motorScratch;
        for (int i = 0; i < QuadcopterEntity.MOTORS; i++) {
            double rpm = t.motorRpm[LAYOUT_TO_SIM[i]];
            out[i] = rpmMax > 0.0 && Double.isFinite(rpm) ? (float) (Math.abs(rpm) / rpmMax) : 0.0F;
        }
        this.motorsPacked = t.armed ? StateValidation.packMotors(out[0], out[1], out[2], out[3]) : 0;
        d.applyLocalState(this.bodyOrientation, t.armed, (float) t.batteryCharge, this.motorsPacked, this.ledsOff);
        float yaw = QuadcopterEntity.yawDegrees(this.bodyOrientation);
        d.setYRot(yaw);
        d.setOldPosAndRot();
    }

    private void pushState(QuadcopterEntity d, DroneSim s) {
        s.getVelocity(this.velocity);
        Telemetry t = s.telemetry();
        Quaternionf q = this.bodyOrientation;
        ClientNet.sendToServer(new DroneStateC2S(d.getId(), this.epoch, d.getX(), d.getY(), d.getZ(), q.x, q.y, q.z, q.w,
                (float) this.velocity.x, (float) this.velocity.y, (float) this.velocity.z, t.armed, (float) t.batteryCharge,
                this.motorsPacked, this.physicsModified, this.ledsOff, RadioState.INSTANCE.isActive(SwitchFunction.BEEPER)));
    }

    private void collectImpacts(QuadcopterEntity d, DroneSim s) {
        ImpactLog impacts = s.impacts();
        DroneBuild build = d.build();
        for (int i = 0; i < impacts.size() && this.pendingImpacts.size() < MAX_PENDING_IMPACTS; i++) {
            ImpactSample impact = impacts.get(i);
            if (CrashDamage.harmful(build, impact)) {
                this.pendingImpacts.add(impact);
            }
        }
    }

    private void sendImpacts(QuadcopterEntity d) {
        if (this.pendingImpacts.isEmpty()) {
            return;
        }
        float strength = ClientWorldRules.INSTANCE.crashDamageStrength();
        if (strength > 0.0F && ClientBus.inSession()) {
            for (int from = 0; from < this.pendingImpacts.size(); from += ImpactC2S.MAX_IMPACTS) {
                int to = Math.min(this.pendingImpacts.size(), from + ImpactC2S.MAX_IMPACTS);
                ClientNet.sendToServer(new ImpactC2S(d.getId(), this.epoch, strength, new ArrayList<>(this.pendingImpacts.subList(from, to))));
            }
        }
        this.pendingImpacts.clear();
    }

    private void clearHardwareWear() {
        Arrays.fill(this.pendingHeatWear, 0.0F);
        this.pendingEscBurn = false;
        this.derateNotified = false;
    }

    private void sendHardwareWear(QuadcopterEntity d, long now) {
        boolean heat = false;
        for (float v : this.pendingHeatWear) {
            heat |= v > 0.0F;
        }
        if (!heat && !this.pendingEscBurn) {
            return;
        }
        if (!this.pendingEscBurn && now - this.lastHardwareSendNanos < HARDWARE_SEND_NANOS) {
            return;
        }
        this.lastHardwareSendNanos = now;
        if (ClientWorldRules.INSTANCE.crashDamage() && ClientBus.inSession()) {
            ClientNet.sendToServer(new HardwareWearC2S(d.getId(), this.epoch, this.pendingEscBurn, this.pendingHeatWear.clone()));
        }
        Arrays.fill(this.pendingHeatWear, 0.0F);
        this.pendingEscBurn = false;
    }

    private void handleEvents(Minecraft mc, DroneSim s, long now) {
        EventQueue events = s.events();
        for (int i = 0; i < events.size(); i++) {
            switch (events.type(i)) {
                case ARMED -> {
                    AudioHooks.beep(Beep.ARM);
                    if (events.value(i) > 0.5) {
                        this.turtleFlip = true;
                        overlay(mc, Component.translatable("propwash.drone.turtle.armed"), Palette.WARNING);
                    }
                }
                case DISARMED -> {
                    AudioHooks.beep(Beep.DISARM);
                    int reason = (int) events.value(i);
                    if (reason != DisarmReason.SWITCH.ordinal()) {
                        this.armLatch = false;
                    }
                    this.turtleDisarmed(mc, reason);
                }
                case ARM_BLOCKED -> {
                    this.armLatch = false;
                    if (!this.isFpv()) {
                        DroneSim current = this.sim;
                        double maxAngle = current == null ? FlightControllerConfig.ARM_ANGLE_DEFAULT : current.config().armMaxTiltDeg;
                        overlay(mc, blockedMessage((int) events.value(i), maxAngle), Palette.ERROR);
                    }
                }
                case CRASH -> {
                    this.crashCount++;
                    AudioHooks.beep(Beep.CRASH);
                }
                case BATTERY_LOW, BATTERY_CRITICAL, PACK_DEPLETED -> {
                    AudioHooks.beep(Beep.LOW_BATTERY);
                    this.nextLowBatteryBeep = now + LOW_BATTERY_INTERVAL;
                }
                case FAILSAFE -> {
                    if (!this.isFpv()) {
                        overlay(mc, Component.translatable("propwash.drone.failsafe"), Palette.ERROR);
                    }
                }
                case ESC_BURNED -> {
                    this.pendingEscBurn = true;
                    AudioHooks.beep(Beep.CRASH);
                    overlay(mc, Component.translatable("propwash.drone.esc_burned", (int) events.value(i), s.params().escMaxCells), Palette.ERROR);
                }
                case MOTOR_THERMAL_WEAR -> {
                    int position = PartWear.positionOfSimMotor(events.motor(i));
                    this.pendingHeatWear[position] += (float) events.value(i);
                }
                case MOTOR_DERATE -> {
                    if (events.value(i) > 0.5 && !this.derateNotified) {
                        this.derateNotified = true;
                        overlay(mc, Component.translatable("propwash.drone.motor_derate"), Palette.WARNING);
                    } else if (events.value(i) <= 0.5 && !s.telemetry().motorDerating) {
                        this.derateNotified = false;
                    }
                }
                default -> {
                }
            }
        }
    }

    private void batteryWarnings(DroneSim s, long now) {
        Telemetry t = s.telemetry();
        if (!t.armed) {
            return;
        }
        long interval = switch (t.batteryState) {
            case LOW -> LOW_BATTERY_INTERVAL;
            case CRITICAL, EMPTY -> CRITICAL_BATTERY_INTERVAL;
            default -> 0L;
        };
        if (interval > 0L && now >= this.nextLowBatteryBeep) {
            AudioHooks.beep(Beep.LOW_BATTERY);
            this.nextLowBatteryBeep = now + interval;
        }
    }

    private void applySwitches(Minecraft mc, long now) {
        if (this.testSticks != null) {
            return;
        }
        RadioState radio = RadioState.INSTANCE;
        boolean flip = radio.isActive(SwitchFunction.FLIP_OVER);
        if (flip && !this.lastFlipSwitch) {
            this.flipUpright(mc);
        }
        this.lastFlipSwitch = flip;
    }

    private void applyLedSwitch() {
        boolean off = RadioState.INSTANCE.isActive(SwitchFunction.LEDS_OFF);
        if (off != this.ledsOff) {
            this.ledsOff = off;
            this.lastSendNanos = 0L;
        }
    }

    private void toggleArm(Minecraft mc) {
        DroneSim s = this.sim;
        if (s == null) {
            return;
        }
        if (s.telemetry().armed) {
            this.armLatch = false;
            if (RadioState.INSTANCE.isActive(SwitchFunction.ARM) || this.heldArmSwitch) {
                s.forceDisarm(DisarmReason.FORCED);
            }
        } else {
            this.armLatch = true;
        }
    }

    private void toggleMode3d(Minecraft mc) {
        Mode3dSelector.Block block = this.mode3dBlock();
        if (block == Mode3dSelector.Block.LOCKED || block == Mode3dSelector.Block.FORBIDDEN) {
            this.mode3dBlockShown = block;
            overlay(mc, mode3dBlockText(block), Palette.WARNING);
            return;
        }
        boolean on = this.mode3d.toggle();
        RadioState radio = RadioState.INSTANCE;
        Boolean fromSwitch = radioUsable(radio) && this.testSticks == null
                ? Mode3dSelector.fromSwitches(radio.activeSwitches(), radio.readableSwitches()) : null;
        Component text = fromSwitch != null && fromSwitch != on
                ? Component.translatable(on ? "propwash.drone.mode3d.on_key_switch_off" : "propwash.drone.mode3d.off_key_switch_on")
                : Component.translatable(on ? "propwash.drone.mode3d.on" : "propwash.drone.mode3d.off");
        overlay(mc, text, on ? Palette.WARNING : Palette.INFO);
        this.lastMode3dAnnounced = this.mode3d.active(InputConfig.get().mode3dUnlocked, mode3dAllowed(), RescueState.INSTANCE.active());
    }

    private void toggleTurtle(Minecraft mc) {
        boolean on = this.turtle.toggle();
        DroneSim s = this.sim;
        boolean flying = s != null && s.telemetry().armed && !s.turtleArmed();
        String key = !on ? "propwash.drone.turtle.off" : flying ? "propwash.drone.turtle.on_flying" : "propwash.drone.turtle.on";
        overlay(mc, Component.translatable(key), on ? Palette.WARNING : Palette.INFO);
    }

    private void turtleDisarmed(Minecraft mc, int reason) {
        if (!this.turtleFlip) {
            return;
        }
        this.turtleFlip = false;
        if (reason == DisarmReason.TURTLE_OFF.ordinal()) {
            overlay(mc, Component.translatable("propwash.drone.turtle.ended"), Palette.INFO);
        } else if (this.turtle.releaseKey()) {
            overlay(mc, Component.translatable("propwash.drone.turtle.off"), Palette.INFO);
        }
    }

    private void announceMode3d(Minecraft mc) {
        boolean active = this.mode3dActive;
        if (active != this.lastMode3dAnnounced) {
            this.lastMode3dAnnounced = active;
            if (this.testSticks == null) {
                overlay(mc, Component.translatable(active ? "propwash.drone.mode3d.on" : "propwash.drone.mode3d.off"),
                        active ? Palette.WARNING : Palette.INFO);
            }
        }
        Mode3dSelector.Block block = this.mode3d.selected() ? this.mode3dBlock() : Mode3dSelector.Block.NONE;
        if (block != this.mode3dBlockShown) {
            this.mode3dBlockShown = block;
            if (block != Mode3dSelector.Block.NONE && this.testSticks == null) {
                overlay(mc, mode3dBlockText(block), Palette.WARNING);
            }
        }
    }

    public static Component mode3dBlockText(Mode3dSelector.Block block) {
        return switch (block) {
            case LOCKED -> Component.translatable("propwash.drone.mode3d.locked");
            case FORBIDDEN -> Component.translatable("propwash.drone.mode3d.forbidden");
            case RESCUE -> Component.translatable("propwash.drone.mode3d.rescue");
            case NONE -> Component.translatable("propwash.drone.mode3d.on");
        };
    }

    private void toggleAltHold(Minecraft mc) {
        this.keyAltHold = !this.keyAltHold;
        if (!this.keyAltHold) {
            this.keyPosHold = false;
        }
        overlay(mc, Component.translatable(this.keyAltHold ? "propwash.drone.hold.alt_on" : "propwash.drone.hold.off"), Palette.INFO);
    }

    private void togglePosHold(Minecraft mc) {
        this.keyPosHold = !this.keyPosHold;
        if (!this.keyPosHold) {
            overlay(mc, Component.translatable(this.keyAltHold ? "propwash.drone.hold.alt_on" : "propwash.drone.hold.off"), Palette.INFO);
            return;
        }
        DroneSim s = this.sim;
        Telemetry t = s == null ? null : s.telemetry();
        if (t != null && !t.posHoldReady) {
            overlay(mc, Component.translatable("propwash.drone.hold.no_gps", t.gpsSatellites), Palette.WARNING);
            this.lastPosDenied = true;
        } else {
            overlay(mc, Component.translatable("propwash.drone.hold.pos_on"), Palette.INFO);
        }
    }

    private void holdWarnings(Minecraft mc, DroneSim s) {
        Telemetry t = s.telemetry();
        boolean denied = t.posHoldDenied && t.armed;
        if (denied && !this.lastPosDenied && this.mode == PilotMode.LINE_OF_SIGHT) {
            overlay(mc, Component.translatable("propwash.drone.hold.no_gps", t.gpsSatellites), Palette.WARNING);
        }
        this.lastPosDenied = denied;
    }

    private void selectMode(Minecraft mc, StabMode mode) {
        this.modes.select(mode);
        RadioState radio = RadioState.INSTANCE;
        StabMode fromSwitches = radioUsable(radio) && this.testSticks == null
                ? FlightModeSelector.fromSwitches(radio.activeSwitches(), radio.readableSwitches()) : null;
        Component text = fromSwitches != null && fromSwitches != mode
                ? Component.translatable("propwash.drone.mode.changed_switch", modeName(mode), modeName(fromSwitches))
                : Component.translatable("propwash.drone.mode.changed", modeName(mode));
        overlay(mc, text, Palette.INFO);
    }

    public static Component modeName(StabMode mode) {
        return Component.translatable("propwash.drone.mode." + mode.name().toLowerCase(Locale.ROOT));
    }

    public boolean flipUpright(Minecraft mc) {
        DroneSim s = this.sim;
        QuadcopterEntity d = this.drone;
        if (s == null || d == null) {
            return false;
        }
        Telemetry t = s.telemetry();
        if (t.armed) {
            overlay(mc, Component.translatable("propwash.drone.flip.armed"), Palette.ERROR);
            return false;
        }
        if (t.speed > 1.0) {
            overlay(mc, Component.translatable("propwash.drone.flip.moving"), Palette.ERROR);
            return false;
        }
        s.getAttitude(this.simOrientation);
        Quaterniond q = this.simOrientation;
        double fx = 2.0 * (q.x * q.z + q.w * q.y);
        double fz = 1.0 - 2.0 * (q.x * q.x + q.y * q.y);
        double heading = Math.atan2(fx, fz);
        if (fx * fx + fz * fz < 1.0E-6) {
            heading = Math.toRadians(t.headingDeg);
        }
        double com = -this.paramsOrDefault().hitboxFootOffset;
        s.reset(d.getX(), d.getY() + com, d.getZ(), heading);
        this.armLatch = false;
        overlay(mc, Component.translatable("propwash.drone.flip.done"), Palette.SUCCESS);
        return true;
    }

    static Component blockedMessage(int mask, double maxAngle) {
        MutableComponent list = Component.empty();
        boolean first = true;
        for (ArmBlocker blocker : ArmBlocker.values()) {
            if (blocker.isSet(mask)) {
                if (!first) {
                    list.append(", ");
                }
                if (blocker == ArmBlocker.TILTED) {
                    list.append(Component.translatable("propwash.drone.arm_blocker.tilted_limit", Math.round(maxAngle)));
                } else {
                    list.append(Component.translatable("propwash.drone.arm_blocker." + blocker.name().toLowerCase(Locale.ROOT)));
                }
                first = false;
            }
        }
        return Component.translatable("propwash.drone.arm_blocked", list);
    }

    private static void overlay(Minecraft mc, Component message, int accent) {
        if (mc.player != null) {
            ThemedToast.show(mc.gui.toastManager(), TOAST_TOKEN, message, null, accent, TOAST_MILLIS);
        }
    }

    static boolean isLocalPlayer(UUID uuid) {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player.getUUID().equals(uuid);
    }
}
