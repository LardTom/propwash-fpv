package de.lardtom.propwash.rescue.client;

import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.KeyRepeatFilter;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.client.InputKeys;
import de.lardtom.propwash.rescue.HomeMode;
import de.lardtom.propwash.rescue.HomePoint;
import de.lardtom.propwash.rescue.HomePointCodecs;
import de.lardtom.propwash.rescue.HomeSync;
import de.lardtom.propwash.rescue.RescueAutopilot;
import de.lardtom.propwash.rescue.RescuePhase;
import de.lardtom.propwash.rescue.RescueSettings;
import de.lardtom.propwash.rescue.RescueSupervisor;
import de.lardtom.propwash.rescue.WorldProbe;
import de.lardtom.propwash.rescue.net.HomePointC2S;
import de.lardtom.propwash.rescue.net.HomePointS2C;
import de.lardtom.propwash.rescue.net.RescueStatusC2S;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.sim.DisarmReason;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemedToast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class RescueController {
    public static final RescueController INSTANCE = new RescueController();

    private static final double DISARM_GRACE = 0.5;
    private static final double PILOT_HOME_OFFSET = 2.5;
    private static final int HOME_GROUND_DEPTH = 96;
    private static final double REPORT_MOVE = 0.5;
    private static final long REPORT_INTERVAL_MS = 250L;
    private static final int PENDING_HOMES = 16;

    private final RescueAutopilot autopilot = new RescueAutopilot();
    private final RescueSupervisor supervisor = new RescueSupervisor();
    private final RescueSupervisor.Frame frame = new RescueSupervisor.Frame();
    private final LevelRescueWorld world = new LevelRescueWorld();
    private final KeyRepeatFilter rescueKey = new KeyRepeatFilter();
    private final KeyRepeatFilter setHomeKey = new KeyRepeatFilter();
    private final HomeSync homeSync = new HomeSync();
    private final Map<Integer, HomePointS2C> pendingHomes = new LinkedHashMap<>();
    private @Nullable Vec3 home;
    private @Nullable ResourceKey<Level> homeDimension;
    private boolean homeManual;
    private @Nullable UUID sessionDrone;
    private boolean sessionActive;
    private boolean pendingToggle;
    private boolean pendingSetHome;
    private double lastSimTime = Double.NaN;
    private double disarmRequestedAt = -1.0;
    private StickPositions sticks = StickPositions.IDLE;
    private RescueSupervisor.@Nullable Trigger trigger;
    private boolean reported;
    private double reportedX;
    private double reportedY;
    private double reportedZ;
    private long reportedAt;
    private int reportedSettings;
    private boolean stuckShown;

    private RescueController() {
    }

    public boolean active() {
        return this.autopilot.active();
    }

    public RescuePhase phase() {
        return this.autopilot.phase();
    }

    public StickPositions sticks() {
        return this.autopilot.active() ? this.sticks : StickPositions.IDLE;
    }

    public boolean wantsDisarm() {
        return this.autopilot.active() && this.autopilot.wantsDisarm();
    }

    public RescueSupervisor.@Nullable Trigger trigger() {
        return this.autopilot.active() ? this.trigger : null;
    }

    public boolean linkLost() {
        return this.sessionActive && this.supervisor.linkLost();
    }

    public double homeDistance() {
        return this.autopilot.active() ? this.autopilot.homeDistance() : Double.NaN;
    }

    public @Nullable Vec3 home() {
        Minecraft mc = Minecraft.getInstance();
        if (this.home == null || mc.level == null || !mc.level.dimension().equals(this.homeDimension)) {
            return null;
        }
        return this.home;
    }

    public void setHome(Vec3 position, ResourceKey<Level> dimension, boolean manual) {
        this.home = position;
        this.homeDimension = dimension;
        this.homeManual = manual;
        if (this.autopilot.active()) {
            this.autopilot.moveHome(position.x, position.y, position.z);
        }
    }

    public void clearHome() {
        this.home = null;
        this.homeDimension = null;
        this.homeManual = false;
    }

    public void requestToggle() {
        if (this.sessionActive) {
            this.pendingToggle = !this.pendingToggle;
        }
    }

    public void requestSetHome() {
        this.pendingSetHome = true;
    }

    public void onClientTick(Minecraft mc) {
        if (InputKeys.pressed(RescueKeys.RESCUE, this.rescueKey)) {
            this.requestToggle();
        }
        if (InputKeys.pressed(RescueKeys.SET_HOME, this.setHomeKey)) {
            this.requestSetHome();
        }
        if (!this.sessionActive && this.pendingSetHome) {
            this.pendingSetHome = false;
            this.setHomeAtPlayer(mc);
        }
    }

    public void onFrame(Minecraft mc) {
        this.world.forgetChunk();
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.isActive() ? session.drone() : null;
        DroneSim sim = session.isActive() ? session.sim() : null;
        if (drone == null || sim == null || mc.level == null || mc.player == null) {
            this.endSession();
            return;
        }
        if (!this.sessionActive || !drone.getUUID().equals(this.sessionDrone)) {
            this.beginSession(drone, sim);
        }
        Level level = drone.level();
        this.world.bind(level);
        Telemetry tm = sim.telemetry();
        double simTime = tm.time;
        double dt = Double.isNaN(this.lastSimTime) ? 0.0 : Math.max(0.0, simTime - this.lastSimTime);
        this.lastSimTime = simTime;
        RescueSettings cfg = RescueConfigs.get();
        Vec3 pos = drone.position();
        if (cfg.homeMode == HomeMode.PILOT) {
            Vec3 pilot = this.pilotHome(mc, level, pos);
            if (pilot != null) {
                this.setHome(pilot, level.dimension(), false);
            }
        }

        RadioState radio = RadioState.INSTANCE;
        StickPositions pilotSticks = radio.sticks();
        RescueSupervisor.Frame f = this.frame;
        f.armed = tm.armed;
        f.grounded = tm.grounded;
        f.connected = radio.connected();
        f.linkStrength = session.linkStrength();
        f.rescueSwitch = radio.isActive(SwitchFunction.RESCUE);
        f.setHomeSwitch = radio.isActive(SwitchFunction.SET_HOME);
        f.stickDeflection = Math.max(Math.abs(pilotSticks.roll()), Math.abs(pilotSticks.pitch()));
        f.keyToggle = this.pendingToggle;
        f.keySetHome = this.pendingSetHome;
        f.rescueActive = this.autopilot.active();
        f.rescueFlying = this.autopilot.phase().flying();
        f.hasHome = this.home != null && level.dimension().equals(this.homeDimension);
        f.pilotDown = session.pilotDown();
        this.pendingToggle = false;
        this.pendingSetHome = false;
        this.supervisor.update(f, cfg);

        if (this.supervisor.setHomeOnArm() || this.supervisor.setHomeManual()) {
            boolean manual = this.supervisor.setHomeManual();
            this.setHome(this.groundedPoint(level, pos), level.dimension(), manual);
            this.push(drone, manual ? HomePoint.Source.MANUAL : HomePoint.Source.ARM);
            this.message(mc, Component.translatable("propwash.rescue.home_set"));
        } else if (this.supervisor.armedNow() && cfg.homeMode == HomeMode.PILOT) {
            this.push(drone, HomePoint.Source.PILOT);
        }
        RescueSupervisor.Abort abort = this.supervisor.abort();
        if (abort != null && this.autopilot.active()) {
            this.abort(mc, abort == RescueSupervisor.Abort.OVERRIDE ? "propwash.rescue.msg.override" : "propwash.rescue.msg.aborted");
        }
        RescueSupervisor.Trigger start = this.supervisor.start();
        if (start != null) {
            this.start(mc, start, sim, level, pos, cfg);
        }

        if (!this.autopilot.active()) {
            this.report(drone, cfg);
            return;
        }
        if (!tm.armed) {
            boolean landed = this.autopilot.phase() == RescuePhase.LANDED;
            this.abort(mc, landed ? "propwash.rescue.msg.landed" : "propwash.rescue.msg.disarmed");
            this.report(drone, cfg);
            return;
        }
        this.autopilot.update(dt, sim, pos.x, pos.y, pos.z, this.world, cfg);
        if (this.autopilot.stuck() && !this.stuckShown) {
            this.stuckShown = true;
            this.message(mc, Component.translatable("propwash.rescue.msg.stuck"));
        }
        this.report(drone, cfg);
        this.sticks = new StickPositions((float) this.autopilot.roll(), (float) this.autopilot.pitch(), (float) this.autopilot.yaw(),
                (float) this.autopilot.throttle()).clamped();
        if (this.autopilot.wantsDisarm()) {
            if (this.disarmRequestedAt < 0.0) {
                this.disarmRequestedAt = simTime;
            } else if (simTime - this.disarmRequestedAt >= DISARM_GRACE) {
                sim.forceDisarm(DisarmReason.FORCED);
            }
        }
    }

    private void report(QuadcopterEntity drone, RescueSettings cfg) {
        boolean active = this.autopilot.active() && this.autopilot.phase().flying() && !this.autopilot.wantsDisarm();
        if (!active) {
            if (this.reported) {
                this.reported = false;
                ClientNet.sendToServer(RescueStatusC2S.inactive(drone.getId()));
            }
            return;
        }
        double hx = this.autopilot.homeX();
        double hy = this.autopilot.homeY();
        double hz = this.autopilot.homeZ();
        long now = Util.getMillis();
        boolean moved = Math.abs(hx - this.reportedX) + Math.abs(hy - this.reportedY) + Math.abs(hz - this.reportedZ) > REPORT_MOVE;
        int settings = settingsHash(cfg);
        if (this.reported && settings == this.reportedSettings && (!moved || now - this.reportedAt < REPORT_INTERVAL_MS)) {
            return;
        }
        this.reported = true;
        this.reportedX = hx;
        this.reportedY = hy;
        this.reportedZ = hz;
        this.reportedAt = now;
        this.reportedSettings = settings;
        ClientNet.sendToServer(new RescueStatusC2S(drone.getId(), true, hx, hy, hz, cfg.homeMode == HomeMode.PILOT,
                (float) cfg.altitude, (float) cfg.speed, (float) cfg.descentRate, cfg.obstacleAvoidance, (float) cfg.clearance,
                (float) cfg.minDistance));
    }

    private static int settingsHash(RescueSettings cfg) {
        int h = Double.hashCode(cfg.altitude);
        h = 31 * h + Double.hashCode(cfg.speed);
        h = 31 * h + Double.hashCode(cfg.descentRate);
        h = 31 * h + Double.hashCode(cfg.clearance);
        h = 31 * h + Double.hashCode(cfg.minDistance);
        h = 31 * h + (cfg.obstacleAvoidance ? 1 : 0);
        return 31 * h + cfg.homeMode.ordinal();
    }

    public void onDisconnect() {
        this.endSession();
        this.clearHome();
        this.sessionDrone = null;
        this.supervisor.forget();
        this.pendingSetHome = false;
        this.pendingHomes.clear();
        this.world.release();
    }

    private void beginSession(QuadcopterEntity drone, DroneSim sim) {
        boolean sameDrone = drone.getUUID().equals(this.sessionDrone);
        if (!sameDrone && !this.homeManual) {
            this.clearHome();
        }
        this.endSession();
        this.supervisor.beginSession(sim.telemetry().armed, sameDrone);
        this.sessionDrone = drone.getUUID();
        this.sessionActive = true;
        this.homeSync.beginSession();
        HomePointS2C pending = this.pendingHomes.remove(drone.getId());
        if (pending != null) {
            drone.setHomePoint(pending.home());
        }
        this.applyStored(drone, false);
    }

    public void onServerHome(Minecraft mc, HomePointS2C payload) {
        if (mc.level == null) {
            return;
        }
        if (!(mc.level.getEntity(payload.entityId()) instanceof QuadcopterEntity drone)) {
            this.pendingHomes.remove(payload.entityId());
            this.pendingHomes.put(payload.entityId(), payload);
            Iterator<Integer> oldest = this.pendingHomes.keySet().iterator();
            while (this.pendingHomes.size() > PENDING_HOMES && oldest.hasNext()) {
                oldest.next();
                oldest.remove();
            }
            return;
        }
        this.pendingHomes.remove(payload.entityId());
        drone.setHomePoint(payload.home());
        if (this.sessionActive && drone.getUUID().equals(this.sessionDrone) && this.homeSync.accepts(payload.ack())) {
            this.applyStored(drone, payload.edited());
        }
    }

    private void applyStored(QuadcopterEntity drone, boolean edited) {
        HomePoint stored = drone.homePoint();
        ResourceKey<Level> dimension = drone.level().dimension();
        boolean storedHere = stored != null && stored.dimension().equals(HomePointCodecs.dimension(drone.level()));
        Vec3 own = this.home;
        if (!edited) {
            boolean ownHere = this.homeManual && own != null && dimension.equals(this.homeDimension);
            boolean storedIsOwn = ownHere && storedHere && stored.samePlace(own.x, own.y, own.z, stored.dimension());
            switch (HomeSync.onStart(ownHere, storedHere, storedIsOwn)) {
                case PUSH_OWN -> this.push(drone, HomePoint.Source.MANUAL);
                case ADOPT_STORED -> this.setHome(new Vec3(stored.x(), stored.y(), stored.z()), dimension, false);
                case KEEP -> {
                }
            }
            return;
        }
        Vec3 before = own != null && dimension.equals(this.homeDimension) ? own : null;
        if (storedHere) {
            this.setHome(new Vec3(stored.x(), stored.y(), stored.z()), dimension, false);
        } else {
            this.clearHome();
        }
        if (!Objects.equals(before, this.home)) {
            this.message(Minecraft.getInstance(), Component.translatable("propwash.rescue.home_changed"));
        }
    }

    private void push(QuadcopterEntity drone, HomePoint.Source source) {
        Vec3 h = this.home;
        if (h == null || !drone.level().dimension().equals(this.homeDimension)) {
            return;
        }
        ClientNet.sendToServer(new HomePointC2S(drone.getId(), this.homeSync.push(), h.x, h.y, h.z, source));
    }

    private void endSession() {
        if (this.autopilot.active()) {
            this.autopilot.stop();
        }
        if (this.sessionActive) {
            this.supervisor.endSession();
        }
        this.trigger = null;
        this.sticks = StickPositions.IDLE;
        this.sessionActive = false;
        this.lastSimTime = Double.NaN;
        this.pendingToggle = false;
        this.disarmRequestedAt = -1.0;
        this.reported = false;
        this.stuckShown = false;
    }

    private void setHomeAtPlayer(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        this.setHome(player.position(), player.level().dimension(), true);
        this.message(mc, Component.translatable("propwash.rescue.home_set"));
    }

    private Vec3 groundedPoint(Level level, Vec3 pos) {
        this.world.bind(level);
        double ground = WorldProbe.groundBelow(this.world, pos.x, pos.y, pos.z, 0.2, HOME_GROUND_DEPTH);
        return Double.isFinite(ground) && ground <= pos.y + 1e-6 ? new Vec3(pos.x, ground, pos.z) : pos;
    }

    private @Nullable Vec3 pilotHome(Minecraft mc, Level level, Vec3 dronePos) {
        LocalPlayer player = mc.player;
        if (player == null || player.level() != level) {
            return null;
        }
        Vec3 feet = player.position();
        double dx = dronePos.x - feet.x;
        double dz = dronePos.z - feet.z;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d < 1e-3) {
            double yaw = Math.toRadians(player.getYRot());
            dx = -Math.sin(yaw);
            dz = Math.cos(yaw);
            d = 1.0;
        }
        return new Vec3(feet.x + dx / d * PILOT_HOME_OFFSET, feet.y, feet.z + dz / d * PILOT_HOME_OFFSET);
    }

    private void start(Minecraft mc, RescueSupervisor.Trigger source, DroneSim sim, Level level, Vec3 pos, RescueSettings cfg) {
        if (this.autopilot.active()) {
            return;
        }
        if (!sim.telemetry().armed) {
            if (source != RescueSupervisor.Trigger.FAILSAFE) {
                this.message(mc, Component.translatable("propwash.rescue.msg.not_armed"));
            }
            return;
        }
        Vec3 target = cfg.homeMode == HomeMode.PILOT ? this.pilotHome(mc, level, pos) : null;
        if (target == null && this.home != null && level.dimension().equals(this.homeDimension)) {
            target = this.home;
        }
        if (target == null) {
            this.message(mc, Component.translatable("propwash.rescue.msg.no_home"));
            return;
        }
        this.autopilot.start(sim, pos.x, pos.y, pos.z, target.x, target.y, target.z, this.world, cfg);
        this.stuckShown = false;
        this.trigger = source;
        this.disarmRequestedAt = -1.0;
        this.sticks = StickPositions.IDLE;
        this.message(mc, Component.translatable(source == RescueSupervisor.Trigger.FAILSAFE
                ? "propwash.rescue.msg.failsafe" : "propwash.rescue.msg.started"));
    }

    private void abort(Minecraft mc, String messageKey) {
        this.autopilot.stop();
        this.trigger = null;
        this.sticks = StickPositions.IDLE;
        this.disarmRequestedAt = -1.0;
        this.message(mc, Component.translatable(messageKey));
    }

    private void message(Minecraft mc, Component text) {
        if (mc.player != null) {
            ThemedToast.show(mc.gui.toastManager(), RescueController.class, text, null, Palette.WARNING, 3000L);
        }
    }
}
