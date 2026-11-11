package de.lardtom.propwash.rescue;

import de.lardtom.propwash.accessory.AccessoryPayload;
import de.lardtom.propwash.accessory.FlightHooks;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.drone.Depenetration;
import de.lardtom.propwash.drone.DroneFluids;
import de.lardtom.propwash.drone.DroneGround;
import de.lardtom.propwash.drone.DroneWear;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.SurfaceMaterials;
import de.lardtom.propwash.drone.TuneMapping;
import de.lardtom.propwash.drone.WaterSurface;
import de.lardtom.propwash.drone.WindModel;
import de.lardtom.propwash.drone.collision.ServerCollision;
import de.lardtom.propwash.drone.net.StateValidation;
import de.lardtom.propwash.input.addon.AddonInputState;
import de.lardtom.propwash.interact.DroneSnags;
import de.lardtom.propwash.interact.DroneVibrations;
import de.lardtom.propwash.server.PersonalRulesServer;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.RuleBase;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.CrashDamage;
import de.lardtom.propwash.sim.DisarmReason;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.ImpactLog;
import de.lardtom.propwash.sim.ImpactSample;
import de.lardtom.propwash.sim.MoveReport;
import de.lardtom.propwash.sim.PilotInput;
import de.lardtom.propwash.sim.SimEnvironment;
import de.lardtom.propwash.sim.SimEventType;
import de.lardtom.propwash.sim.SimSnapshot;
import de.lardtom.propwash.sim.SimTuning;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.StepResult;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.video.VtxTables;
import de.lardtom.propwash.video.vtx.VtxPowerSettings;
import de.lardtom.propwash.video.vtx.VtxTable;
import de.lardtom.propwash.zone.ZoneField;
import de.lardtom.propwash.zone.ZoneServer;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

abstract class ServerFlight {
    static final long TICK_NANOS = 50_000_000L;
    private static final int MIN_SLICES = 4;
    private static final int MAX_SLICES = 8;
    private static final double SLICE_DISTANCE = 0.3;
    private static final double DISARM_GRACE = 0.5;
    private static final double GROUND_PROBE = 5.0;
    private static final double SOLID_PROBE = 1.0;
    private static final double SUPPORT_PROBE = 0.05;
    private static final double HIT_EPSILON = 1.0E-7;
    private static final double RESYNC_DISTANCE = 0.75;
    private static final int[] LAYOUT_TO_SIM = {3, 1, 2, 0};
    private static final float PI = (float) Math.PI;

    enum Outcome {
        FLYING,
        LANDED,
        LOST
    }

    final ServerRescueWorld world;
    final FlightChunks chunks = new FlightChunks();
    final PilotInput input = new PilotInput();
    final SimEnvironment env = new SimEnvironment();
    private final DroneGround groundProbe = new DroneGround();
    private final StepResult result = new StepResult();
    private final MoveReport report = new MoveReport();
    private final SimSnapshot snapshot = new SimSnapshot();
    private final Vector3d velocity = new Vector3d();
    private final Quaternionf body = new Quaternionf();
    private final List<ImpactSample> impacts = new ArrayList<>();
    private final float[] motors = new float[QuadcopterEntity.MOTORS];
    private final DroneSnags.Flight snags = new DroneSnags.Flight();
    private final ZoneField zones = new ZoneField();
    private @Nullable UUID rulesPlayer;
    DroneSim sim;
    AirframeSpec params;
    private DroneBuild build;
    private float cargoGrams;
    private double simTime;
    private double disarmAt = -1.0;
    private int crashes;
    private Vec3 written;
    long lastTick;

    ServerFlight(ServerRescueWorld world) {
        this.world = world;
    }

    DroneSim sim() {
        return this.sim;
    }

    int crashes() {
        return this.crashes;
    }

    boolean gpsFix(QuadcopterEntity drone, ServerLevel level) {
        double com = this.params == null ? 0.0 : -this.params.hitboxFootOffset;
        return GpsReception.fix(level, drone.getX(), drone.getY() + com, drone.getZ());
    }

    static double videoPowerDelta(Map<String, Float> tune, DroneBuild build) {
        VtxTable table = VtxTables.of(build.video());
        VtxPowerSettings.Selection selection = VtxPowerSettings.selection(tune, table);
        return selection.auto() ? 0.0 : table.extraW(selection.index());
    }

    final void begin(QuadcopterEntity drone, ServerLevel level, Vec3 velocity, boolean armed) {
        this.build = AccessoryPayload.flightBuild(drone);
        this.cargoGrams = drone.cargoGrams();
        this.params = AirframeSpec.of(this.build);
        if (this.rulesPlayer == null) {
            this.rulesPlayer = drone.pilot().orElse(null);
        }
        RuleBase rules = PersonalRulesServer.base(level.getServer(), this.rulesPlayer);
        this.zones.update(ZoneServer.zones(level), ZoneServer.dimension(level), rules, drone.getX(), drone.getY(), drone.getZ(), 0.0);
        SimTuning tuning = this.tuning(level);
        long seed = drone.getUUID().getLeastSignificantBits() ^ level.getGameTime();
        this.sim = new DroneSim(this.params, TuneMapping.toConfig(drone.tune().values(), this.params), tuning, seed);
        this.sim.setBatteryCharge(drone.batteryCharge());
        this.sim.setWear(drone.wear());
        this.sim.setWaterproof(drone.waterproof() || WorldRules.on(level.getServer(), WorldRuleSpecs.WATERPROOF_DRONES));
        this.sim.setVideoPowerDelta(videoPowerDelta(drone.tune().values(), this.build));
        FlightHooks.INSTANCE.attachForces(this.sim, this.build);
        AddonInputState.bind(drone, this.sim);
        this.place(drone, velocity, armed);
        this.input.mode = StabMode.ANGLE;
        this.input.armSwitch = armed;
        this.input.linkOk = true;
        this.world.bind(level);
        this.lastTick = level.getGameTime();
    }

    final boolean arm(QuadcopterEntity drone, ServerLevel level) {
        this.world.bind(level);
        this.fillEnvironment(level, drone);
        this.input.roll = 0.0;
        this.input.pitch = 0.0;
        this.input.yaw = 0.0;
        this.input.throttle = 0.0;
        this.input.mode = StabMode.ANGLE;
        this.input.armSwitch = false;
        this.sim.step(0L, this.input, this.env, this.result);
        this.input.armSwitch = true;
        this.sim.step(0L, this.input, this.env, this.result);
        return this.sim.telemetry().armed;
    }

    final void place(QuadcopterEntity drone, Vec3 v) {
        this.place(drone, v, true);
    }

    private void place(QuadcopterEntity drone, Vec3 v, boolean armed) {
        Handover.place(this.sim, this.snapshot, drone.getX(), drone.getY(), drone.getZ(), drone.orientation(), v.x, v.y, v.z, armed);
        this.written = drone.position();
    }

    private SimTuning tuning(ServerLevel level) {
        SimTuning tuning = this.zones.apply(this.zones.rules().toSimTuning());
        tuning.partCompatibility = WorldRules.partCompatibility(level.getServer()).enforced();
        return tuning;
    }

    private void followZones(QuadcopterEntity drone, ServerLevel level) {
        boolean wasInfinite = this.zones.rules().infiniteBattery();
        if (this.zones.update(ZoneServer.zones(level), ZoneServer.dimension(level), PersonalRulesServer.base(level.getServer(), this.rulesPlayer),
                drone.getX(), drone.getY(), drone.getZ(), TICK_NANOS * 1.0E-9)) {
            this.sim.setTuning(this.tuning(level));
            if (wasInfinite && !this.zones.rules().infiniteBattery()) {
                this.sim.keepBatteryCharge();
            }
        }
    }

    private void followCargo(QuadcopterEntity drone) {
        float grams = drone.cargoGrams();
        if (grams == this.cargoGrams) {
            return;
        }
        this.cargoGrams = grams;
        DroneBuild build = AccessoryPayload.flightBuild(drone);
        if (AccessoryPayload.unladen(build).equals(AccessoryPayload.unladen(this.build)) && this.sim.setAirframe(AirframeSpec.of(build))) {
            this.build = build;
            this.params = this.sim.params();
        }
    }

    final void rulesOf(@Nullable UUID player) {
        this.rulesPlayer = player;
    }

    void resynced(QuadcopterEntity drone) {
    }

    void beforeSlices(QuadcopterEntity drone, ServerLevel level) {
    }

    abstract void steer(double dt, QuadcopterEntity drone);

    abstract boolean wantsDisarm();

    abstract boolean landed();

    final Outcome fly(QuadcopterEntity drone, ServerLevel level) {
        this.world.bind(level);
        this.followZones(drone, level);
        this.followCargo(drone);
        this.world.exempt(SectionPos.blockToSectionCoord(drone.getBlockX()), SectionPos.blockToSectionCoord(drone.getBlockZ()));
        this.lastTick = level.getGameTime();
        if (this.written != null && drone.position().distanceToSqr(this.written) > RESYNC_DISTANCE * RESYNC_DISTANCE) {
            this.place(drone, Vec3.ZERO);
            this.resynced(drone);
        }
        this.beforeSlices(drone, level);
        Telemetry t = this.sim.telemetry();
        double speed = Double.isFinite(t.speed) ? t.speed : 0.0;
        int slices = Math.max(MIN_SLICES, Math.min(MAX_SLICES, (int) Math.ceil(speed * TICK_NANOS * 1.0E-9 / SLICE_DISTANCE)));
        long remaining = TICK_NANOS;
        int crashesBefore = this.crashes;
        this.impacts.clear();
        for (int i = 0; i < slices; i++) {
            long part = i == slices - 1 ? remaining : TICK_NANOS / slices;
            remaining -= part;
            double dt = part * 1.0E-9;
            this.steer(dt, drone);
            if (this.wantsDisarm()) {
                if (this.disarmAt < 0.0) {
                    this.disarmAt = this.simTime;
                } else if (this.simTime - this.disarmAt >= DISARM_GRACE) {
                    this.sim.forceDisarm(DisarmReason.FORCED);
                }
            }
            this.fillEnvironment(level, drone);
            this.snags.fill(level, this.sim, this.env, dt);
            this.sim.step(part, this.input, this.env, this.result);
            this.move(drone);
            this.collectImpacts();
            this.crashes += this.sim.events().count(SimEventType.CRASH);
            this.simTime += dt;
            if (!this.sim.telemetry().armed) {
                break;
            }
        }
        this.snags.applyWear(level, drone, this.sim);
        this.applyWear(level, drone);
        this.write(drone);
        if (this.crashes > crashesBefore) {
            DroneVibrations.crashed(drone);
        }
        if (this.sim.telemetry().armed) {
            return Outcome.FLYING;
        }
        return this.landed() ? Outcome.LANDED : Outcome.LOST;
    }

    static double clamp(double v, double min, double max) {
        return Double.isFinite(v) ? Math.max(min, Math.min(max, v)) : 0.0;
    }

    private void fillEnvironment(ServerLevel level, QuadcopterEntity drone) {
        double com = -this.params.hitboxFootOffset;
        double x = drone.getX();
        double y = drone.getY() + com;
        double z = drone.getZ();
        AABB box = drone.getBoundingBox();
        this.groundProbe.collect(level, drone, x, y, z, 0.5 * box.getXsize(), GROUND_PROBE);
        this.env.groundDistance = this.groundDistance(level, drone, x, y, z);
        this.env.solidDistance = this.env.groundDistance < SOLID_PROBE ? this.solidDistance(level, drone, x, y, z) : Double.POSITIVE_INFINITY;
        this.env.fluid = DroneFluids.at(level, x, y, z);
        this.env.waterSurface = WaterSurface.relative(level, x, y, z);
        this.env.airDensity = 1.225 * Math.exp(-Math.max(0.0, y - level.getSeaLevel()) / 8500.0);
        this.env.ambientTempC = 20.0;
        this.env.skyView = 1.0;
        WindModel.fill(level, x, y, z, this.env.groundDistance, this.env);
        if ((this.env.groundDistance < SOLID_PROBE || this.sim.telemetry().grounded) && !drone.noPhysics) {
            this.groundProbe.fillSupport(level, drone, x, box.minY, z, box.getXsize(), this.env);
            this.groundProbe.fillPoints(level, drone, this.params, drone.orientation(), x, box.minY, z, this.env);
        } else {
            this.env.clearSupport();
        }
    }

    private double groundDistance(Level level, QuadcopterEntity drone, double x, double y, double z) {
        if (!level.hasChunk(SectionPos.posToSectionCoord(x), SectionPos.posToSectionCoord(z))) {
            return Double.POSITIVE_INFINITY;
        }
        double floor = level.getMinY();
        if (y <= floor) {
            return 0.0;
        }
        double probeEnd = Math.max(y - GROUND_PROBE, floor);
        BlockHitResult hit = level.clip(new ClipContext(new Vec3(x, y, z), new Vec3(x, probeEnd, z), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY, drone));
        double gate = this.groundProbe.colliderDistance(x, y, z, y - probeEnd);
        if (hit.getType() == HitResult.Type.MISS) {
            return Math.min(gate, probeEnd <= floor ? y - floor : Double.POSITIVE_INFINITY);
        }
        return Math.min(gate, Math.max(0.0, y - hit.getLocation().y));
    }

    private double solidDistance(Level level, QuadcopterEntity drone, double x, double y, double z) {
        BlockHitResult hit = level.clip(new ClipContext(new Vec3(x, y, z), new Vec3(x, y - SOLID_PROBE, z), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, drone));
        double gate = this.groundProbe.colliderDistance(x, y, z, SOLID_PROBE);
        return Math.min(gate, hit.getType() == HitResult.Type.MISS ? Double.POSITIVE_INFINITY : Math.max(0.0, y - hit.getLocation().y));
    }

    private void move(QuadcopterEntity drone) {
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
        Depenetration.escape(drone, this.sim);
        double bx = drone.getX();
        double by = drone.getY();
        double bz = drone.getZ();
        boolean moved = rx != 0.0 || ry != 0.0 || rz != 0.0;
        if (moved) {
            drone.move(MoverType.SELF, new Vec3(rx, ry, rz));
            drone.resetFallDistance();
        }
        double ax = drone.getX() - bx;
        double ay = drone.getY() - by;
        double az = drone.getZ() - bz;
        r.requestedX = rx;
        r.requestedY = ry;
        r.requestedZ = rz;
        r.actualX = ax;
        r.actualY = ay;
        r.actualZ = az;
        double ex = rx;
        double ey = ry;
        double ez = rz;
        if (moved && drone.obliqueContact()) {
            Vec3 normal = drone.obliqueNormal();
            Vec3 wanted = drone.obliqueDelta();
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
        Telemetry t = this.sim.telemetry();
        if (r.hitX || r.hitY || r.hitZ || r.obliqueContact() || t.grounded || Double.isFinite(this.env.solidDistance)) {
            SurfaceMaterials.fill(drone.level(), drone.getBoundingBox(), r, t.grounded);
            ServerCollision.afterSurface(drone, this.sim, r, t.grounded);
        }
        if (t.grounded || t.sleeping) {
            r.supportGap = drone.supportGap(SUPPORT_PROBE);
        }
        SurfaceMaterials.sticky(drone.level(), drone.getBoundingBox(), r);
        this.sim.afterMove(r);
    }

    private void collectImpacts() {
        ImpactLog log = this.sim.impacts();
        for (int i = 0; i < log.size() && this.impacts.size() < 16; i++) {
            ImpactSample impact = log.get(i);
            if (CrashDamage.harmful(this.build, impact)) {
                this.impacts.add(impact);
            }
        }
    }

    private void applyWear(ServerLevel level, QuadcopterEntity drone) {
        if (this.impacts.isEmpty()) {
            return;
        }
        float strength = WorldRules.crashDamageStrength(level.getServer());
        if (strength <= 0.0F) {
            return;
        }
        PartWear before = drone.wear();
        PartWear after = DroneWear.apply(before, drone.build(), this.impacts, strength, level.getRandom().nextLong());
        if (after.equals(before)) {
            return;
        }
        drone.setWear(after);
        this.sim.setWear(after);
        if (DroneWear.newlyBroken(before, after)) {
            level.playSound(null, drone.getX(), drone.getY(), drone.getZ(), SoundEvents.ITEM_BREAK, SoundSource.NEUTRAL, 0.7F, 1.3F);
        }
    }

    private void write(QuadcopterEntity drone) {
        Quaterniond q = this.result.orientation;
        this.body.set((float) q.x, (float) q.y, (float) q.z, (float) q.w).normalize().rotateY(PI);
        drone.setAttitude(this.body);
        drone.setYRot(QuadcopterEntity.yawDegrees(this.body));
        Telemetry t = this.sim.telemetry();
        double rpmMax = this.params.omegaMax * 60.0 / (2.0 * Math.PI);
        for (int i = 0; i < QuadcopterEntity.MOTORS; i++) {
            double rpm = t.motorRpm[LAYOUT_TO_SIM[i]];
            this.motors[i] = rpmMax > 0.0 && Double.isFinite(rpm) ? (float) (Math.abs(rpm) / rpmMax) : 0.0F;
        }
        drone.setArmed(t.armed);
        drone.setMotorOutputsPacked(t.armed ? StateValidation.packMotors(this.motors[0], this.motors[1], this.motors[2], this.motors[3]) : 0);
        drone.setBatteryCharge((float) Math.max(0.0, Math.min(1.0, t.batteryCharge)));
        this.sim.getVelocity(this.velocity);
        drone.setDeltaMovement(new Vec3(finite(this.velocity.x), finite(this.velocity.y), finite(this.velocity.z)).scale(0.05));
        this.written = drone.position();
    }

    private static double finite(double v) {
        return Double.isFinite(v) ? v : 0.0;
    }
}
