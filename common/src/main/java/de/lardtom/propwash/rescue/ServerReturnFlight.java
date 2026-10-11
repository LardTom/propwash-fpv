package de.lardtom.propwash.rescue;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.drone.DroneWear;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.TuneMapping;
import de.lardtom.propwash.drone.WindModel;
import de.lardtom.propwash.drone.net.StateValidation;
import de.lardtom.propwash.interact.CargoMass;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.CrashDamage;
import de.lardtom.propwash.sim.DisarmReason;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.Fluid;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

final class ServerReturnFlight {
    static final long TICK_NANOS = 50_000_000L;
    static final double PILOT_HOME_OFFSET = 2.5;
    private static final int MIN_SLICES = 4;
    private static final int MAX_SLICES = 8;
    private static final double SLICE_DISTANCE = 0.3;
    private static final double DISARM_GRACE = 0.5;
    private static final double GROUND_PROBE = 5.0;
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

    final RescueAutopilot autopilot = new RescueAutopilot();
    private final ServerRescueWorld world;
    private final PilotInput input = new PilotInput();
    private final SimEnvironment env = new SimEnvironment();
    private final StepResult result = new StepResult();
    private final MoveReport report = new MoveReport();
    private final SimSnapshot snapshot = new SimSnapshot();
    private final Vector3d velocity = new Vector3d();
    private final Quaternionf body = new Quaternionf();
    private final List<ImpactSample> impacts = new ArrayList<>();
    private final float[] motors = new float[QuadcopterEntity.MOTORS];
    private DroneSim sim;
    private AirframeSpec params;
    private DroneBuild build;
    private RescueSettings cfg;
    private double simTime;
    private double disarmAt = -1.0;
    private boolean landNowApplied;
    private int crashes;
    private Vec3 written;
    long lastTick;
    boolean announcedStuck;

    private ServerReturnFlight(ServerRescueWorld world) {
        this.world = world;
    }

    static ServerReturnFlight start(QuadcopterEntity drone, ReturnPlan plan, ServerLevel level, ServerRescueWorld world) {
        ServerReturnFlight flight = new ServerReturnFlight(world);
        flight.cfg = plan.settings();
        flight.build = CargoMass.withPayload(drone.build(), drone.cargoGrams());
        flight.params = AirframeSpec.of(flight.build);
        PhysicsRules rules = WorldRules.physics(level.getServer());
        SimTuning tuning = rules.toSimTuning();
        tuning.partCompatibility = WorldRules.partCompatibility(level.getServer()).enforced();
        long seed = drone.getUUID().getLeastSignificantBits() ^ level.getGameTime();
        flight.sim = new DroneSim(flight.params, TuneMapping.toConfig(drone.tune().values(), flight.params), tuning, seed);
        flight.sim.setBatteryCharge(drone.batteryCharge());
        flight.sim.setWear(drone.wear());
        flight.place(drone, drone.getDeltaMovement().scale(20.0));
        flight.input.mode = StabMode.ANGLE;
        flight.input.armSwitch = true;
        flight.input.linkOk = true;
        world.bind(level);
        flight.autopilot.setAutonomous(true);
        flight.autopilot.start(flight.sim, drone.getX(), drone.getY(), drone.getZ(), plan.homeX(), plan.homeY(), plan.homeZ(), world, flight.cfg);
        flight.lastTick = level.getGameTime();
        return flight;
    }

    private void place(QuadcopterEntity drone, Vec3 v) {
        Handover.place(this.sim, this.snapshot, drone.getX(), drone.getY(), drone.getZ(), drone.orientation(), v.x, v.y, v.z, true);
        this.written = drone.position();
    }

    Outcome tick(QuadcopterEntity drone, ServerLevel level, ReturnPlan plan, @Nullable ServerPlayer owner) {
        this.world.bind(level);
        this.world.exempt(SectionPos.blockToSectionCoord(drone.getBlockX()), SectionPos.blockToSectionCoord(drone.getBlockZ()));
        this.lastTick = level.getGameTime();
        if (this.written != null && drone.position().distanceToSqr(this.written) > RESYNC_DISTANCE * RESYNC_DISTANCE) {
            this.place(drone, Vec3.ZERO);
            this.autopilot.start(this.sim, drone.getX(), drone.getY(), drone.getZ(), plan.homeX(), plan.homeY(), plan.homeZ(), this.world, this.cfg);
        }
        Vec3 followed = plan.followOwner() && owner != null && owner.level() == level && !owner.isSpectator()
                ? pilotHome(owner.position(), drone.position(), owner.getYRot()) : null;
        if (followed != null && Math.hypot(followed.x - drone.getX(), followed.z - drone.getZ()) <= AutonomousReturns.MAX_HOME_DISTANCE) {
            this.autopilot.moveHome(followed.x, followed.y, followed.z);
        } else {
            this.autopilot.moveHome(plan.homeX(), plan.homeY(), plan.homeZ());
        }
        if (plan.landNow() && !this.landNowApplied) {
            this.landNowApplied = true;
            this.autopilot.landNow(this.world);
        }
        Telemetry t = this.sim.telemetry();
        double speed = Double.isFinite(t.speed) ? t.speed : 0.0;
        int slices = Math.max(MIN_SLICES, Math.min(MAX_SLICES, (int) Math.ceil(speed * TICK_NANOS * 1.0E-9 / SLICE_DISTANCE)));
        long remaining = TICK_NANOS;
        this.impacts.clear();
        for (int i = 0; i < slices; i++) {
            long part = i == slices - 1 ? remaining : TICK_NANOS / slices;
            remaining -= part;
            double dt = part * 1.0E-9;
            this.autopilot.update(dt, this.sim, drone.getX(), drone.getY(), drone.getZ(), this.world, this.cfg);
            this.input.roll = clamp(this.autopilot.roll(), -1.0, 1.0);
            this.input.pitch = clamp(this.autopilot.pitch(), -1.0, 1.0);
            this.input.yaw = clamp(this.autopilot.yaw(), -1.0, 1.0);
            this.input.throttle = clamp(this.autopilot.throttle(), 0.0, 1.0);
            this.input.mode = StabMode.ANGLE;
            this.input.armSwitch = true;
            if (this.autopilot.wantsDisarm()) {
                if (this.disarmAt < 0.0) {
                    this.disarmAt = this.simTime;
                } else if (this.simTime - this.disarmAt >= DISARM_GRACE) {
                    this.sim.forceDisarm(DisarmReason.FORCED);
                }
            }
            this.fillEnvironment(level, drone);
            this.sim.step(part, this.input, this.env, this.result);
            this.move(drone);
            this.collectImpacts();
            this.crashes += this.sim.events().count(SimEventType.CRASH);
            this.simTime += dt;
            if (!this.sim.telemetry().armed) {
                break;
            }
        }
        this.applyWear(level, drone);
        this.write(drone);
        if (this.sim.telemetry().armed) {
            return Outcome.FLYING;
        }
        return this.autopilot.phase() == RescuePhase.LANDED ? Outcome.LANDED : Outcome.LOST;
    }

    int crashes() {
        return this.crashes;
    }

    static Vec3 pilotHome(Vec3 feet, Vec3 drone, float yawDegrees) {
        double dx = drone.x - feet.x;
        double dz = drone.z - feet.z;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d < 1e-3) {
            double yaw = Math.toRadians(yawDegrees);
            dx = -Math.sin(yaw);
            dz = Math.cos(yaw);
            d = 1.0;
        }
        return new Vec3(feet.x + dx / d * PILOT_HOME_OFFSET, feet.y, feet.z + dz / d * PILOT_HOME_OFFSET);
    }

    private void fillEnvironment(ServerLevel level, QuadcopterEntity drone) {
        double com = -this.params.hitboxFootOffset;
        double x = drone.getX();
        double y = drone.getY() + com;
        double z = drone.getZ();
        this.env.groundDistance = groundDistance(level, drone, x, y, z);
        this.env.fluid = fluidAt(level, x, y, z);
        this.env.airDensity = 1.225 * Math.exp(-Math.max(0.0, y - level.getSeaLevel()) / 8500.0);
        this.env.ambientTempC = 20.0;
        this.env.skyView = 1.0;
        WindModel.fill(level, x, y, z, this.env.groundDistance, this.env);
    }

    private static double groundDistance(Level level, QuadcopterEntity drone, double x, double y, double z) {
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
        if (hit.getType() == HitResult.Type.MISS) {
            return probeEnd <= floor ? y - floor : Double.POSITIVE_INFINITY;
        }
        return Math.max(0.0, y - hit.getLocation().y);
    }

    private static Fluid fluidAt(Level level, double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        if (!level.hasChunk(SectionPos.posToSectionCoord(x), SectionPos.posToSectionCoord(z)) || level.isOutsideBuildHeight(pos)) {
            return Fluid.NONE;
        }
        FluidState fluid = level.getFluidState(pos);
        if (fluid.isEmpty() || y > pos.getY() + fluid.getHeight(level, pos)) {
            return Fluid.NONE;
        }
        return fluid.is(FluidTags.LAVA) ? Fluid.LAVA : Fluid.WATER;
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
        double bx = drone.getX();
        double by = drone.getY();
        double bz = drone.getZ();
        boolean moved = rx != 0.0 || ry != 0.0 || rz != 0.0;
        if (moved) {
            drone.move(MoverType.SELF, new Vec3(rx, ry, rz));
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
        if (t.grounded || t.sleeping) {
            r.supportGap = drone.supportGap(SUPPORT_PROBE);
        }
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

    private static double clamp(double v, double min, double max) {
        return Double.isFinite(v) ? Math.max(min, Math.min(max, v)) : 0.0;
    }

    private static double finite(double v) {
        return Double.isFinite(v) ? v : 0.0;
    }
}
