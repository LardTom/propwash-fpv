package de.lardtom.propwash.rescue;

import de.lardtom.propwash.sim.Axis;
import de.lardtom.propwash.sim.BatteryState;
import de.lardtom.propwash.sim.BuildAnalysis;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.sim.PidProfile;
import de.lardtom.propwash.sim.RateProfile;
import de.lardtom.propwash.sim.SimTuning;
import de.lardtom.propwash.sim.StickMath;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.sim.ThrottleCurve;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class RescueAutopilot {
    public static final double LAND_RADIUS = 1.2;
    private static final double G = 9.81;
    private static final double PLAN_INTERVAL = 0.1;
    private static final double BRAKE_ACCEL = 2.5;
    private static final double MAX_TILT_DEG = 35.0;
    private static final double KV = 1.6;
    private static final double KI_H = 0.4;
    private static final double MAX_I_H = 1.5;
    private static final double KP_POS = 0.9;
    private static final double KP_ALT = 1.2;
    private static final double KV_Z = 2.5;
    private static final double KI_Z = 1.2;
    private static final double MAX_I_Z = 3.0;
    private static final double YAW_GAIN = 2.5;
    private static final double MAX_YAW_RATE_DEG = 120.0;
    private static final double YAW_MIN_SPEED = 1.5;
    private static final double MIN_FLIGHT_HEIGHT = 1.2;
    private static final double ESCAPE_SPEED = 3.0;
    private static final double STEER_SPEED = 3.0;
    private static final double STEER_TIME = 1.5;
    private static final double STEER_PROBE = 5.0;
    private static final double STUCK_WINDOW = 4.0;
    private static final double STUCK_MOVE = 1.5;
    private static final double HOLD_RETRY = 4.0;
    private static final double ESCAPE_TIMEOUT = 12.0;
    private static final double SKY_MARGIN = 0.9;
    private static final double SKY_MARGIN_MIN = 0.35;
    private static final double ESCAPE_MIN_RADIUS = 1.5;
    private static final double ESCAPE_MAX_RADIUS = 16.0;
    private static final int ESCAPE_DIRECTIONS = 24;
    private static final double DIRECT_MAX_DISTANCE = 64.0;
    private static final double TOUCHDOWN_RATE = 0.35;
    private static final double DESCENT_SLOPE = 0.45;
    private static final double LANDED_CONFIRM = 0.3;
    private static final double TERRAIN_STEP = 0.5;
    private static final double TERRAIN_MARGIN = 0.5;
    private static final int GROUND_SCAN_DEPTH = 96;
    private static final int MAX_UNKNOWN_RAISES = 4;
    private static final double LANDING_SPOT_MARGIN = 0.4;
    private static final double LANDING_SPOT_RADIUS = 12.0;
    private static final int LANDING_SPOT_DIRECTIONS = 16;
    private static final double LAND_HERE_RADIUS = 8.0;
    private static final double LANDING_SPOT_LOW = 2.0;
    private static final double LANDING_SPOT_DEEP = 24.0;
    private static final double PATH_SCAN_MAX = 256.0;
    private static final double PATH_SCAN_STEP = 1.0;
    private static final double ROUTE_SPEED = 3.0;
    private static final double ROUTE_SPEED_NARROW = 1.5;
    private static final double ROUTE_LATERAL_GAIN = 1.8;
    private static final double ROUTE_PASS_SLACK = 0.6;
    private static final double ROUTE_REACH_V = 0.6;
    private static final double ROUTE_REPLAN = 1.0;
    private static final double ROUTE_MIN_GAIN = 2.0;
    private static final double ROUTE_GOAL_PROBE = 10.0;
    private static final double ROUTE_TIMEOUT = 20.0;
    private static final double ROUTE_NARROW_INFLATION = 0.45;
    private static final int MAX_RETRIES = 3;
    private static final double PROGRESS_GAIN = 3.0;
    private static final int SPOT_BAD = -1;
    private static final int SPOT_POOR = 0;
    private static final int SPOT_GOOD = 1;
    private static final ThreadLocal<RoutePlanner> PLANNERS = ThreadLocal.withInitial(RoutePlanner::new);
    private static final double[] STEER_OFFSETS_DEG = {25, -25, 50, -50, 75, -75, 100, -100, 130, -130, 160, -160};

    private final Vector3d velocity = new Vector3d();
    private final Quaterniond orientation = new Quaterniond();

    private RescuePhase phase = RescuePhase.IDLE;
    private double homeX;
    private double homeY;
    private double homeZ;
    private double landX;
    private double landZ;
    private double x;
    private double bottom;
    private double z;
    private double vx;
    private double vy;
    private double vz;
    private double heading;
    private boolean grounded;
    private BatteryState battery = BatteryState.OK;
    private double hoverStick;

    private AirframeSpec params;
    private double halfWidth;
    private double height;
    private double thrustToWeight;
    private double gravity = G;
    private double maxSpeed;
    private double hoverFallback;
    private double dragPerMass;
    private double maxTilt;
    private double climbRate;
    private PidProfile pid = new PidProfile();
    private RateProfile rates = new RateProfile();

    private double time;
    private double phaseTime;
    private double planTimer;
    private double baseAlt;
    private double cruiseAlt;
    private double altCeiling;
    private double climbLimit = Double.POSITIVE_INFINITY;
    private double escapeAlt;
    private double holdAlt;
    private double holdX;
    private double holdZ;
    private double escapeX;
    private double escapeZ;
    private boolean direct;
    private boolean holdAtHome;
    private double steerX;
    private double steerZ;
    private double steerUntil = -1.0;
    private double groundY;
    private double obstacleSpeed = Double.POSITIVE_INFINITY;
    private double firstHigh = Double.POSITIVE_INFINITY;
    private double anchorX;
    private double anchorY;
    private double anchorZ;
    private double progressTime;
    private int stuckLevel;
    private double landedTimer;
    private int unknownRaises;
    private boolean wantsDisarm;
    private double integralX;
    private double integralZ;
    private double integralY;
    private final double[] routeX = new double[RoutePlanner.MAX_WAYPOINTS];
    private final double[] routeY = new double[RoutePlanner.MAX_WAYPOINTS];
    private final double[] routeZ = new double[RoutePlanner.MAX_WAYPOINTS];
    private int routeLen;
    private int routeIdx;
    private boolean routeSky;
    private double routeStartX;
    private double routeStartY;
    private double routeStartZ;
    private double routeSpeed = ROUTE_SPEED;
    private double routeStarted;
    private double routeRetryAt = -1.0;
    private double skyRetryAt = -1.0;
    private int retries;
    private double bestDistance = Double.POSITIVE_INFINITY;
    private double retryDistance = Double.POSITIVE_INFINITY;
    private boolean gaveUp;
    private boolean autonomous;
    private double desiredVx;
    private double desiredVz;
    private double roll;
    private double pitch;
    private double yaw;
    private double throttle;

    public RescueAutopilot() {
    }

    public void start(DroneSim sim, double x, double bottom, double z, double homeX, double homeY, double homeZ,
                      RescueWorld world, RescueSettings cfg) {
        this.readDrone(sim, x, bottom, z);
        this.homeX = homeX;
        this.homeY = homeY;
        this.homeZ = homeZ;
        this.landX = homeX;
        this.landZ = homeZ;
        this.time = 0.0;
        this.integralX = 0.0;
        this.integralZ = 0.0;
        this.integralY = 0.0;
        this.wantsDisarm = false;
        this.stuckLevel = 0;
        this.steerUntil = -1.0;
        this.landedTimer = 0.0;
        this.unknownRaises = 0;
        this.holdAtHome = false;
        this.obstacleSpeed = Double.POSITIVE_INFINITY;
        this.firstHigh = Double.POSITIVE_INFINITY;
        this.climbLimit = Double.POSITIVE_INFINITY;
        this.clearRoute();
        this.routeRetryAt = -1.0;
        this.skyRetryAt = -1.0;
        this.retries = 0;
        this.gaveUp = false;
        this.altCeiling = world.maxY() - this.height - 2.0;
        this.groundY = this.scanGround(world);
        this.baseAlt = Math.min(this.altCeiling, Math.max(bottom, homeY + cfg.altitude));
        this.cruiseAlt = this.baseAlt;
        this.markProgress();
        double dist = this.homeDistance();
        this.bestDistance = dist;
        this.retryDistance = dist;
        if (dist < Math.max(cfg.minDistance, LAND_RADIUS)) {
            this.direct = true;
            this.cruiseAlt = Math.max(bottom, this.groundY + MIN_FLIGHT_HEIGHT);
            if (dist < LAND_RADIUS) {
                this.arriveHome(world, cfg);
            } else {
                this.enter(RescuePhase.CRUISE);
            }
        } else {
            this.direct = false;
            this.cruiseAlt = this.pathAltitude(world, cfg);
            this.enter(RescuePhase.CLIMB);
        }
    }

    public void setAutonomous(boolean autonomous) {
        this.autonomous = autonomous;
    }

    public boolean autonomous() {
        return this.autonomous;
    }

    public boolean stuck() {
        return this.gaveUp && this.phase == RescuePhase.HOLD;
    }

    public boolean gaveUp() {
        return this.gaveUp;
    }

    public boolean following() {
        return this.routeLen > 0;
    }

    public double landX() {
        return this.landX;
    }

    public double landZ() {
        return this.landZ;
    }

    public void landNow(RescueWorld world) {
        if (this.phase == RescuePhase.IDLE || this.phase == RescuePhase.LANDED || this.phase == RescuePhase.LAND) {
            return;
        }
        this.groundY = this.scanGround(world);
        this.chooseLandingNear(world);
        this.holdAtHome = false;
        this.enter(RescuePhase.LAND);
    }

    public void stop() {
        this.phase = RescuePhase.IDLE;
        this.wantsDisarm = false;
        this.clearRoute();
        this.gaveUp = false;
        this.roll = 0.0;
        this.pitch = 0.0;
        this.yaw = 0.0;
        this.throttle = 0.0;
    }

    public void update(double dt, DroneSim sim, double x, double bottom, double z, RescueWorld world, RescueSettings cfg) {
        if (this.phase == RescuePhase.IDLE) {
            return;
        }
        this.readDrone(sim, x, bottom, z);
        if (!(dt > 0.0)) {
            return;
        }
        dt = Math.min(dt, 0.1);
        this.time += dt;
        this.phaseTime += dt;
        if (this.phase != RescuePhase.LANDED) {
            this.planTimer -= dt;
            if (this.planTimer <= 0.0) {
                this.planTimer = PLAN_INTERVAL;
                this.plan(world, cfg);
            }
        }
        if (this.phase == RescuePhase.LAND) {
            this.detectTouchdown(dt);
        }
        if (this.phase == RescuePhase.LANDED) {
            this.roll = 0.0;
            this.pitch = 0.0;
            this.yaw = 0.0;
            this.throttle = 0.0;
            this.wantsDisarm = true;
            return;
        }
        this.guideAndControl(dt, cfg);
    }

    public RescuePhase phase() {
        return this.phase;
    }

    public boolean active() {
        return this.phase != RescuePhase.IDLE;
    }

    public boolean wantsDisarm() {
        return this.wantsDisarm;
    }

    public double roll() {
        return this.roll;
    }

    public double pitch() {
        return this.pitch;
    }

    public double yaw() {
        return this.yaw;
    }

    public double throttle() {
        return this.throttle;
    }

    public double homeX() {
        return this.homeX;
    }

    public double homeY() {
        return this.homeY;
    }

    public double homeZ() {
        return this.homeZ;
    }

    public double cruiseAltitude() {
        return this.cruiseAlt;
    }

    public double homeDistance() {
        double dx = this.homeX - this.x;
        double dz = this.homeZ - this.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public void moveHome(double homeX, double homeY, double homeZ) {
        this.homeX = homeX;
        this.homeY = homeY;
        this.homeZ = homeZ;
        if (this.phase != RescuePhase.LAND && this.phase != RescuePhase.LANDED) {
            this.landX = homeX;
            this.landZ = homeZ;
        }
        if (this.holdAtHome) {
            this.holdX = homeX;
            this.holdZ = homeZ;
        }
    }

    private void readDrone(DroneSim sim, double x, double bottom, double z) {
        AirframeSpec p = sim.params();
        double gravity = sim.gravity();
        if (p != this.params || gravity != this.gravity) {
            this.params = p;
            this.gravity = gravity;
            BuildAnalysis analysis = BuildAnalysis.of(p.build);
            this.halfWidth = 0.5 * p.hitboxWidth;
            this.height = p.hitboxHeight;
            double tw = analysis.thrustToWeight() * (G / SimTuning.referenceGravity(gravity));
            this.thrustToWeight = Double.isFinite(tw) && tw > 1.05 ? tw : 1.5;
            double top = analysis.topSpeedAngleKmh() / 3.6;
            this.maxSpeed = Double.isFinite(top) && top > 2.0 ? 0.8 * top : 6.0;
            double hover = analysis.hoverThrottlePercent() / 100.0;
            this.hoverFallback = Double.isFinite(hover) && hover > 0.02 ? hover : 0.45;
            double area = 0.5 * (p.areaZ + p.areaY);
            this.dragPerMass = p.massKg > 0.0 ? 0.5 * 1.225 * p.dragCoefficient * area / p.massKg : 0.0;
            this.climbRate = Math.max(1.5, Math.min(4.0, 1.0 + 0.6 * this.thrustToWeight));
        }
        FlightControllerConfig config = sim.config();
        this.pid = config.pid;
        this.rates = config.rates;
        double tiltByThrust = Math.acos(Math.min(1.0, 1.25 / this.thrustToWeight));
        double tiltByAngleLimit = Math.toRadians(Math.max(5.0, this.pid.angleLimitDeg - 3.0));
        this.maxTilt = Math.max(Math.toRadians(8.0), Math.min(Math.toRadians(MAX_TILT_DEG), Math.min(tiltByThrust, tiltByAngleLimit)));
        sim.getVelocity(this.velocity);
        sim.getAttitude(this.orientation);
        Telemetry tm = sim.telemetry();
        this.x = x;
        this.bottom = bottom;
        this.z = z;
        this.vx = finite(this.velocity.x);
        this.vy = finite(this.velocity.y);
        this.vz = finite(this.velocity.z);
        this.heading = wrap(2.0 * Math.atan2(this.orientation.y, this.orientation.w));
        this.grounded = tm.grounded;
        this.battery = tm.batteryState;
        this.hoverStick = tm.hoverThrottleStick > 0.02 && tm.hoverThrottleStick < 1.0 ? tm.hoverThrottleStick : this.hoverFallback;
    }

    private void enter(RescuePhase next) {
        this.phase = next;
        this.phaseTime = 0.0;
        this.planTimer = 0.0;
        this.landedTimer = 0.0;
        if (next == RescuePhase.CLIMB || next == RescuePhase.ESCAPE) {
            this.holdX = this.x;
            this.holdZ = this.z;
        }
        if (next != RescuePhase.CRUISE) {
            this.steerUntil = -1.0;
            this.obstacleSpeed = Double.POSITIVE_INFINITY;
        }
        if (next != RescuePhase.CRUISE && next != RescuePhase.ESCAPE) {
            this.clearRoute();
        }
    }

    private void clearRoute() {
        this.routeLen = 0;
        this.routeIdx = 0;
        this.routeSky = false;
    }

    private void enterHold() {
        this.holdAtHome = false;
        this.holdX = this.x;
        this.holdZ = this.z;
        this.holdAlt = Math.max(this.bottom, Math.min(this.groundY + MIN_FLIGHT_HEIGHT, this.climbLimit));
        this.enter(RescuePhase.HOLD);
    }

    private void arriveHome(RescueWorld world, RescueSettings cfg) {
        if (cfg.autoLand || this.autonomous) {
            this.chooseLandingSpot(world);
            this.enter(RescuePhase.LAND);
        } else {
            this.holdX = this.homeX;
            this.holdZ = this.homeZ;
            this.holdAlt = this.bottom;
            this.enter(RescuePhase.HOLD);
            this.holdAtHome = true;
        }
    }

    private void chooseLandingSpot(RescueWorld world) {
        this.landX = this.homeX;
        this.landZ = this.homeZ;
        double half = this.halfWidth + LANDING_SPOT_MARGIN;
        double homeTop = WorldProbe.maxSurface(world, this.homeX, this.homeZ, half);
        boolean covered = homeTop > this.homeY + 1.0 && this.bottom >= homeTop;
        int homeQuality = covered ? SPOT_BAD : spotQuality(world, this.homeX, this.homeZ, half, this.bottom);
        if (homeQuality == SPOT_GOOD) {
            return;
        }
        double[] poor = {Double.NaN, Double.NaN};
        if (this.searchSpot(world, half, LANDING_SPOT_LOW, poor) || this.searchSpot(world, half, LANDING_SPOT_DEEP, poor)) {
            return;
        }
        if (homeQuality == SPOT_POOR && !covered) {
            return;
        }
        if (!Double.isNaN(poor[0])) {
            this.landX = poor[0];
            this.landZ = poor[1];
        }
    }

    private boolean searchSpot(RescueWorld world, double half, double below, double[] poor) {
        double dx = this.x - this.homeX;
        double dz = this.z - this.homeZ;
        double base = dx * dx + dz * dz > 1e-6 ? Math.atan2(dx, dz) : 0.0;
        double step = 2.0 * Math.PI / LANDING_SPOT_DIRECTIONS;
        for (double r = 1.0; r <= LANDING_SPOT_RADIUS; r += 1.0) {
            for (int k = 0; k < LANDING_SPOT_DIRECTIONS; k++) {
                int n = (k + 1) / 2;
                double angle = base + (k % 2 == 1 ? n : -n) * step;
                double px = this.homeX + r * Math.sin(angle);
                double pz = this.homeZ + r * Math.cos(angle);
                double top = WorldProbe.maxSurface(world, px, pz, half + 0.3);
                if (!(top <= this.homeY + 1.0 && top >= this.homeY - below && top <= this.bottom + 1e-3)) {
                    continue;
                }
                int quality = spotQuality(world, px, pz, half, this.bottom);
                if (quality == SPOT_GOOD) {
                    this.landX = px;
                    this.landZ = pz;
                    return true;
                }
                if (quality == SPOT_POOR && Double.isNaN(poor[0])) {
                    poor[0] = px;
                    poor[1] = pz;
                }
            }
        }
        return false;
    }

    private void chooseLandingNear(RescueWorld world) {
        this.landX = this.x;
        this.landZ = this.z;
        double half = this.halfWidth + LANDING_SPOT_MARGIN;
        int below = spotQuality(world, this.x, this.z, half, this.bottom);
        if (below == SPOT_GOOD) {
            return;
        }
        double dx = this.homeX - this.x;
        double dz = this.homeZ - this.z;
        double base = dx * dx + dz * dz > 1e-6 ? Math.atan2(dx, dz) : this.heading;
        double step = 2.0 * Math.PI / LANDING_SPOT_DIRECTIONS;
        double poorX = Double.NaN;
        double poorZ = Double.NaN;
        double inflation = WorldProbe.fitInflation(world, this.x, this.bottom, this.z, this.halfWidth, this.height, 0.25);
        for (double r = 1.0; r <= LAND_HERE_RADIUS; r += 1.0) {
            for (int k = 0; k < LANDING_SPOT_DIRECTIONS; k++) {
                int n = (k + 1) / 2;
                double angle = base + (k % 2 == 1 ? n : -n) * step;
                double px = this.x + r * Math.sin(angle);
                double pz = this.z + r * Math.cos(angle);
                int quality = spotQuality(world, px, pz, half, this.bottom);
                if (quality == SPOT_BAD || (quality == SPOT_POOR && !Double.isNaN(poorX))) {
                    continue;
                }
                double free = WorldProbe.sweep(world, this.x, this.bottom, this.z, this.halfWidth, this.height, inflation,
                        0.5 * inflation, px - this.x, 0.0, pz - this.z, r);
                if (free < r - 1e-6) {
                    continue;
                }
                if (quality == SPOT_GOOD) {
                    this.landX = px;
                    this.landZ = pz;
                    return;
                }
                poorX = px;
                poorZ = pz;
            }
        }
        if (below == SPOT_POOR || Double.isNaN(poorX)) {
            return;
        }
        this.landX = poorX;
        this.landZ = poorZ;
    }

    static int spotQuality(RescueWorld world, double x, double z, double half, double fromBottom) {
        int x0 = WorldProbe.floor(x - half + 1e-7);
        int x1 = WorldProbe.floor(x + half - 1e-7);
        int z0 = WorldProbe.floor(z - half + 1e-7);
        int z1 = WorldProbe.floor(z + half - 1e-7);
        int top = Integer.MIN_VALUE;
        boolean flat = true;
        boolean solid = true;
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                long column = columnGround(world, cx, cz, fromBottom);
                int y = (int) (column >> 8);
                Ground kind = Ground.values()[(int) (column & 0xFF)];
                if (kind == Ground.UNKNOWN || kind.lethal()) {
                    return SPOT_BAD;
                }
                if (!kind.landable()) {
                    solid = false;
                }
                if (top == Integer.MIN_VALUE) {
                    top = y;
                } else if (y != top) {
                    flat = false;
                }
            }
        }
        return solid && flat ? SPOT_GOOD : SPOT_POOR;
    }

    static long columnGround(RescueWorld world, int cx, int cz, double fromBottom) {
        int start = WorldProbe.floor(fromBottom + 1e-4) - 1;
        int surface = world.surfaceY(cx, cz);
        if (surface != RescueWorld.UNKNOWN_SURFACE && surface <= fromBottom + 1e-4) {
            start = surface - 1;
        }
        int end = Math.max(world.minY(), start - GROUND_SCAN_DEPTH);
        for (int y = start; y >= end; y--) {
            Ground kind = world.ground(cx, y, cz);
            if (kind != Ground.NONE) {
                return ((long) (y + 1) << 8) | kind.ordinal();
            }
        }
        return ((long) end << 8) | Ground.UNKNOWN.ordinal();
    }

    private double pathAltitude(RescueWorld world, RescueSettings cfg) {
        if (!cfg.obstacleAvoidance) {
            return this.cruiseAlt;
        }
        double dx = this.homeX - this.x;
        double dz = this.homeZ - this.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 1e-6) {
            return this.cruiseAlt;
        }
        double ux = dx / dist;
        double uz = dz / dist;
        double half = this.halfWidth + cfg.clearance;
        double end = Math.min(dist, PATH_SCAN_MAX);
        double highest = Double.NEGATIVE_INFINITY;
        for (double s = 0.0; s <= end + 1e-9; s += PATH_SCAN_STEP) {
            highest = Math.max(highest, WorldProbe.maxSurface(world, this.x + ux * s, this.z + uz * s, half));
        }
        if (!Double.isFinite(highest)) {
            return this.cruiseAlt;
        }
        return Math.min(this.altCeiling, Math.max(this.cruiseAlt, highest + cfg.clearance + TERRAIN_MARGIN));
    }

    private boolean planRoute(RescueWorld world, RescueSettings cfg, boolean sky) {
        if (this.time < (sky ? this.skyRetryAt : this.routeRetryAt)) {
            return false;
        }
        if (sky) {
            this.skyRetryAt = this.time + ROUTE_REPLAN;
        } else {
            this.routeRetryAt = this.time + ROUTE_REPLAN;
        }
        RoutePlanner planner = PLANNERS.get();
        double lowest = Math.max(world.minY(), this.bottom - RoutePlanner.DOWN);
        double ceiling = Math.min(this.altCeiling, this.bottom + RoutePlanner.UP);
        double wide = 0.5 * cfg.clearance;
        double narrow = Math.max(0.02, Math.min(wide, ROUTE_NARROW_INFLATION - this.halfWidth));
        double[] inflations = narrow < wide - 1e-6 ? new double[]{wide, narrow} : new double[]{wide};
        for (double inflation : inflations) {
            RoutePlanner.Goal goal = sky ? new SkyGoal(this.halfWidth + SKY_MARGIN_MIN)
                    : new HomeGoal(this.homeX, this.homeZ, this.homeDistance(), this.halfWidth, this.height, inflation);
            if (planner.plan(world, this.x, this.bottom, this.z, this.halfWidth, this.height, inflation, lowest, ceiling,
                    sky ? 0.0 : ROUTE_MIN_GAIN, goal) && (!sky || planner.complete())) {
                int n = Math.min(planner.size(), this.routeX.length);
                for (int i = 0; i < n; i++) {
                    this.routeX[i] = planner.x(i);
                    this.routeY[i] = planner.bottom(i);
                    this.routeZ[i] = planner.z(i);
                }
                this.routeLen = n;
                this.routeIdx = 0;
                this.routeSky = sky;
                this.routeStarted = this.time;
                this.routeStartX = this.x;
                this.routeStartY = this.bottom;
                this.routeStartZ = this.z;
                this.routeSpeed = inflation < wide - 1e-6 ? ROUTE_SPEED_NARROW : ROUTE_SPEED;
                return n > 0;
            }
        }
        return false;
    }

    private boolean followRoute(RescueWorld world, RescueSettings cfg) {
        while (this.routeIdx < this.routeLen) {
            int i = this.routeIdx;
            double ey = this.routeY[i] - this.bottom;
            double ex = this.routeX[i] - this.x;
            double ez = this.routeZ[i] - this.z;
            boolean close = ex * ex + ez * ez < 0.35 * 0.35;
            boolean passed = i < this.routeLen - 1 && this.segmentRemaining(i) < 0.15 && this.crossTrack(i) < 0.4;
            if ((close || passed) && Math.abs(ey) < ROUTE_REACH_V) {
                this.routeIdx++;
            } else {
                break;
            }
        }
        if (this.routeIdx >= this.routeLen || this.time - this.routeStarted > ROUTE_TIMEOUT) {
            this.clearRoute();
            return false;
        }
        double tx = this.routeX[this.routeIdx] - this.x;
        double ty = this.routeY[this.routeIdx] - this.bottom;
        double tz = this.routeZ[this.routeIdx] - this.z;
        double len = Math.sqrt(tx * tx + ty * ty + tz * tz);
        double probe = Math.min(len, 1.0);
        if (probe > 0.3) {
            double inflation = WorldProbe.fitInflation(world, this.x, this.bottom, this.z, this.halfWidth, this.height, 0.05);
            double free = WorldProbe.sweep(world, this.x, this.bottom, this.z, this.halfWidth, this.height, inflation,
                    0.5 * inflation, tx, ty, tz, probe);
            if (free < Math.min(probe, 0.5) - 1e-6) {
                this.clearRoute();
                return false;
            }
        }
        return true;
    }

    private double segmentStartX(int i) {
        return i == 0 ? this.routeStartX : this.routeX[i - 1];
    }

    private double segmentStartY(int i) {
        return i == 0 ? this.routeStartY : this.routeY[i - 1];
    }

    private double segmentStartZ(int i) {
        return i == 0 ? this.routeStartZ : this.routeZ[i - 1];
    }

    private double segmentRemaining(int i) {
        double sx = this.routeX[i] - this.segmentStartX(i);
        double sz = this.routeZ[i] - this.segmentStartZ(i);
        double len = Math.sqrt(sx * sx + sz * sz);
        if (len < 1e-6) {
            return 0.0;
        }
        return ((this.routeX[i] - this.x) * sx + (this.routeZ[i] - this.z) * sz) / len;
    }

    private double crossTrack(int i) {
        double ax = this.segmentStartX(i);
        double az = this.segmentStartZ(i);
        double sx = this.routeX[i] - ax;
        double sz = this.routeZ[i] - az;
        double len = Math.sqrt(sx * sx + sz * sz);
        if (len < 1e-6) {
            return Math.sqrt((this.x - ax) * (this.x - ax) + (this.z - az) * (this.z - az));
        }
        return Math.abs(((this.x - ax) * sz - (this.z - az) * sx) / len);
    }

    private static final class SkyGoal implements RoutePlanner.Goal {
        private final double half;

        SkyGoal(double half) {
            this.half = half;
        }

        @Override
        public double estimate(double x, double bottom, double z) {
            return 0.0;
        }

        @Override
        public boolean reached(RescueWorld world, double x, double bottom, double z) {
            return WorldProbe.skyClear(world, x, bottom, z, this.half);
        }
    }

    private static final class HomeGoal implements RoutePlanner.Goal {
        private final double homeX;
        private final double homeZ;
        private final double halfWidth;
        private final double height;
        private final double inflation;
        private double checked;

        HomeGoal(double homeX, double homeZ, double startDistance, double halfWidth, double height, double inflation) {
            this.homeX = homeX;
            this.homeZ = homeZ;
            this.halfWidth = halfWidth;
            this.height = height;
            this.inflation = inflation;
            this.checked = startDistance - 0.5;
        }

        @Override
        public double estimate(double x, double bottom, double z) {
            double dx = this.homeX - x;
            double dz = this.homeZ - z;
            return Math.sqrt(dx * dx + dz * dz);
        }

        @Override
        public boolean reached(RescueWorld world, double x, double bottom, double z) {
            double d = this.estimate(x, bottom, z);
            if (d < LAND_RADIUS + 0.5) {
                return true;
            }
            if (d > this.checked - 0.5) {
                return false;
            }
            this.checked = d;
            double probe = Math.min(d, ROUTE_GOAL_PROBE);
            double free = WorldProbe.sweep(world, x, bottom, z, this.halfWidth, this.height, this.inflation,
                    Math.min(RoutePlanner.MAX_VERTICAL_INFLATION, 0.5 * this.inflation), this.homeX - x, 0.0, this.homeZ - z, probe);
            return free >= probe - 1e-6;
        }
    }

    private void plan(RescueWorld world, RescueSettings cfg) {
        this.groundY = this.scanGround(world);
        this.bestDistance = Math.min(this.bestDistance, this.homeDistance());
        switch (this.phase) {
            case CLIMB -> this.planClimb(world, cfg);
            case ESCAPE -> this.planEscape(world, cfg);
            case CRUISE -> this.planCruise(world, cfg);
            case HOLD -> this.planHold(world, cfg);
            default -> {
            }
        }
        this.watchdog(world, cfg);
    }

    private double scanGround(RescueWorld world) {
        double g = WorldProbe.groundBelow(world, this.x, this.bottom, this.z, this.halfWidth, GROUND_SCAN_DEPTH);
        return Double.isFinite(g) ? g : world.minY();
    }

    private double climbRoom(RescueWorld world, double need) {
        if (WorldProbe.skyClear(world, this.x, this.bottom, this.z, this.halfWidth + 0.3)) {
            return Double.POSITIVE_INFINITY;
        }
        double inflation = WorldProbe.fitInflation(world, this.x, this.bottom, this.z, this.halfWidth, this.height, 0.25);
        return WorldProbe.sweep(world, this.x, this.bottom, this.z, this.halfWidth, this.height, inflation, 0.0,
                0.0, 1.0, 0.0, Math.max(need, 0.0) + 2.0);
    }

    private void updateClimbLimit(double room) {
        this.climbLimit = room == Double.POSITIVE_INFINITY ? Double.POSITIVE_INFINITY : this.bottom + room - 0.4;
    }

    private void planClimb(RescueWorld world, RescueSettings cfg) {
        if (!cfg.obstacleAvoidance) {
            this.climbLimit = Double.POSITIVE_INFINITY;
            if (this.bottom >= this.cruiseAlt - 0.6) {
                this.enter(RescuePhase.CRUISE);
            }
            return;
        }
        double need = this.cruiseAlt - this.bottom;
        double room = this.climbRoom(world, need);
        this.updateClimbLimit(room);
        if (this.bottom >= this.cruiseAlt - 0.6) {
            this.enter(RescuePhase.CRUISE);
            return;
        }
        if (room < need) {
            if (room > 0.8 && this.bottom - this.groundY < MIN_FLIGHT_HEIGHT - 0.2) {
                return;
            }
            this.startEscape(world, cfg);
        }
    }

    private void startEscape(RescueWorld world, RescueSettings cfg) {
        double alt = Math.min(Math.max(this.bottom, this.groundY + MIN_FLIGHT_HEIGHT), this.climbLimit);
        alt = Math.max(alt, Math.min(this.bottom, this.climbLimit));
        if (this.findSky(world, cfg, alt)) {
            this.escapeAlt = alt;
            this.clearRoute();
            this.enter(RescuePhase.ESCAPE);
            return;
        }
        if (cfg.obstacleAvoidance && this.planRoute(world, cfg, true)) {
            this.escapeAlt = this.routeY[this.routeLen - 1];
            this.enter(RescuePhase.ESCAPE);
            return;
        }
        if (this.findDirect(world, cfg, alt)) {
            this.direct = true;
            this.cruiseAlt = alt;
            this.clearRoute();
            this.enter(RescuePhase.CRUISE);
            return;
        }
        if (cfg.obstacleAvoidance && this.homeDistance() <= DIRECT_MAX_DISTANCE && this.planRoute(world, cfg, false)) {
            this.direct = true;
            this.cruiseAlt = alt;
            this.enter(RescuePhase.CRUISE);
            return;
        }
        this.enterHold();
    }

    private boolean findSky(RescueWorld world, RescueSettings cfg, double alt) {
        double dx = this.homeX - this.x;
        double dz = this.homeZ - this.z;
        double base = dx * dx + dz * dz > 1e-6 ? Math.atan2(dx, dz) : this.heading;
        double step = 2.0 * Math.PI / ESCAPE_DIRECTIONS;
        double[] margins = {SKY_MARGIN, SKY_MARGIN_MIN};
        for (double margin : margins) {
            for (double r = ESCAPE_MIN_RADIUS; r <= ESCAPE_MAX_RADIUS; r += 1.0) {
                for (int k = 0; k < ESCAPE_DIRECTIONS; k++) {
                    int n = (k + 1) / 2;
                    double angle = base + (k % 2 == 1 ? n : -n) * step;
                    double px = this.x + r * Math.sin(angle);
                    double pz = this.z + r * Math.cos(angle);
                    if (!WorldProbe.skyClear(world, px, alt, pz, this.halfWidth + margin)) {
                        continue;
                    }
                    double free = WorldProbe.sweepFitted(world, this.x, alt, this.z, this.halfWidth, this.height,
                            0.5 * cfg.clearance, px - this.x, 0.0, pz - this.z, r);
                    if (free < r - 1e-6) {
                        continue;
                    }
                    this.escapeX = px;
                    this.escapeZ = pz;
                    return true;
                }
            }
        }
        return false;
    }

    private boolean findDirect(RescueWorld world, RescueSettings cfg, double alt) {
        double dist = this.homeDistance();
        if (dist > DIRECT_MAX_DISTANCE) {
            return false;
        }
        if (dist < 1e-3) {
            return true;
        }
        double free = WorldProbe.sweepFitted(world, this.x, alt, this.z, this.halfWidth, this.height, cfg.clearance,
                this.homeX - this.x, 0.0, this.homeZ - this.z, dist);
        return free >= dist - 1e-6;
    }

    private void planEscape(RescueWorld world, RescueSettings cfg) {
        this.updateClimbLimit(this.climbRoom(world, 2.0));
        if (WorldProbe.skyClear(world, this.x, this.bottom, this.z, this.halfWidth + 0.5)) {
            this.enter(RescuePhase.CLIMB);
            return;
        }
        if (this.routeLen > 0) {
            if (!this.followRoute(world, cfg)) {
                if (WorldProbe.skyClear(world, this.x, this.bottom, this.z, this.halfWidth + SKY_MARGIN_MIN)) {
                    this.enter(RescuePhase.CLIMB);
                } else {
                    this.startEscape(world, cfg);
                }
            }
            return;
        }
        double ex = this.escapeX - this.x;
        double ez = this.escapeZ - this.z;
        double d = Math.sqrt(ex * ex + ez * ez);
        if (d < 0.4) {
            this.enter(RescuePhase.CLIMB);
            return;
        }
        if (this.phaseTime > ESCAPE_TIMEOUT) {
            this.startEscape(world, cfg);
            return;
        }
        if (Math.abs(this.bottom - this.escapeAlt) < 0.6) {
            double probe = Math.min(d, 4.0);
            double free = WorldProbe.sweepFitted(world, this.x, this.bottom, this.z, this.halfWidth, this.height,
                    0.5 * cfg.clearance, ex, 0.0, ez, probe);
            if (free < probe - 1e-6 && free < 1.0) {
                this.startEscape(world, cfg);
            }
        }
    }

    private void planCruise(RescueWorld world, RescueSettings cfg) {
        double dx = this.homeX - this.x;
        double dz = this.homeZ - this.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < LAND_RADIUS) {
            this.arriveHome(world, cfg);
            return;
        }
        this.obstacleSpeed = Double.POSITIVE_INFINITY;
        this.firstHigh = Double.POSITIVE_INFINITY;
        if (!cfg.obstacleAvoidance) {
            this.climbLimit = Double.POSITIVE_INFINITY;
            return;
        }
        double ux = dx / dist;
        double uz = dz / dist;
        double speedCap = this.speedCap(cfg);
        double room = this.climbRoom(world, Math.max(this.cruiseAlt - this.bottom, 1.0));
        this.updateClimbLimit(room);
        double len = Math.min(dist, speedCap * 1.2 + speedCap * speedCap / (2.0 * BRAKE_ACCEL) + 3.0);
        if (!this.direct) {
            this.scanTerrain(world, cfg, ux, uz, Math.min(dist, Math.max(12.0, len + 4.0)));
        }
        double sweepBottom = Math.max(this.bottom, this.groundY + 0.1);
        if (this.routeLen > 0 && !this.routeSky) {
            double probe = Math.min(dist, ROUTE_GOAL_PROBE);
            double ahead = WorldProbe.sweepFitted(world, this.x, sweepBottom, this.z, this.halfWidth, this.height,
                    cfg.clearance, ux, 0.0, uz, probe);
            if (ahead >= probe - 1e-6 && this.routeIdx > 0) {
                this.clearRoute();
            } else if (this.followRoute(world, cfg)) {
                return;
            }
        }
        if (this.steerUntil > this.time) {
            double homeProbe = Math.min(dist, 6.0);
            double homeFree = WorldProbe.sweepFitted(world, this.x, sweepBottom, this.z, this.halfWidth, this.height,
                    cfg.clearance, ux, 0.0, uz, homeProbe);
            if (homeFree >= homeProbe - 1e-6) {
                this.steerUntil = -1.0;
            } else {
                double free = WorldProbe.sweepFitted(world, this.x, sweepBottom, this.z, this.halfWidth, this.height,
                        cfg.clearance, this.steerX, 0.0, this.steerZ, 2.0);
                if (free < 1.0 && !this.steerAround(world, cfg, ux, uz, sweepBottom)) {
                    this.enterHold();
                }
                return;
            }
        }
        double free = WorldProbe.sweepFitted(world, this.x, sweepBottom, this.z, this.halfWidth, this.height,
                cfg.clearance, ux, 0.0, uz, len);
        if (free < len - 1e-6) {
            if (this.direct) {
                this.direct = false;
                this.cruiseAlt = Math.max(this.bottom, this.baseAlt);
                this.enter(RescuePhase.CLIMB);
                return;
            }
            boolean canClimb = this.bottom < this.cruiseAlt - 0.3 && this.climbLimit > this.bottom + 1.0;
            if (!canClimb) {
                if (room == Double.POSITIVE_INFINITY && this.unknownRaises < MAX_UNKNOWN_RAISES) {
                    this.unknownRaises++;
                    this.cruiseAlt = Math.min(this.altCeiling, Math.max(this.cruiseAlt, this.bottom) + 3.0);
                } else if (free < 2.5) {
                    if (this.planRoute(world, cfg, false)) {
                        return;
                    }
                    if (this.findSky(world, cfg, this.bottom)) {
                        this.escapeAlt = this.bottom;
                        this.clearRoute();
                        this.enter(RescuePhase.ESCAPE);
                        return;
                    }
                    if (!this.steerAround(world, cfg, ux, uz, sweepBottom)) {
                        this.enterHold();
                    }
                    return;
                }
            }
            this.obstacleSpeed = Math.sqrt(2.0 * BRAKE_ACCEL * Math.max(0.0, free - 1.0));
        }
        if (!this.direct && this.bottom < this.cruiseAlt - 0.3 && this.firstHigh < Double.POSITIVE_INFINITY) {
            this.obstacleSpeed = Math.min(this.obstacleSpeed,
                    Math.sqrt(2.0 * BRAKE_ACCEL * Math.max(0.0, this.firstHigh - cfg.clearance - 1.0)));
        }
    }

    private void scanTerrain(RescueWorld world, RescueSettings cfg, double ux, double uz, double distance) {
        double half = this.halfWidth + cfg.clearance;
        double highest = Double.NEGATIVE_INFINITY;
        for (double s = 0.0; s <= distance + 1e-9; s += TERRAIN_STEP) {
            double surface = WorldProbe.maxSurface(world, this.x + ux * s, this.z + uz * s, half);
            if (surface > highest) {
                highest = surface;
            }
            if (this.firstHigh == Double.POSITIVE_INFINITY && surface + cfg.clearance > this.bottom + 0.2) {
                this.firstHigh = s;
            }
        }
        if (Double.isFinite(highest)) {
            double required = highest + cfg.clearance + TERRAIN_MARGIN;
            if (required > this.cruiseAlt) {
                this.cruiseAlt = Math.min(this.altCeiling, required);
            }
        }
    }

    private boolean steerAround(RescueWorld world, RescueSettings cfg, double ux, double uz, double sweepBottom) {
        double base = Math.atan2(ux, uz);
        for (double offset : STEER_OFFSETS_DEG) {
            double angle = base + Math.toRadians(offset);
            double sx = Math.sin(angle);
            double sz = Math.cos(angle);
            double free = WorldProbe.sweepFitted(world, this.x, sweepBottom, this.z, this.halfWidth, this.height,
                    cfg.clearance, sx, 0.0, sz, STEER_PROBE);
            if (free >= STEER_PROBE - 1e-6) {
                this.steerX = sx;
                this.steerZ = sz;
                this.steerUntil = this.time + STEER_TIME;
                return true;
            }
        }
        return false;
    }

    private void planHold(RescueWorld world, RescueSettings cfg) {
        if (cfg.obstacleAvoidance) {
            this.updateClimbLimit(this.climbRoom(world, 2.0));
        } else {
            this.climbLimit = Double.POSITIVE_INFINITY;
        }
        if (this.battery == BatteryState.CRITICAL || this.battery == BatteryState.EMPTY) {
            this.landX = this.holdAtHome ? this.homeX : this.x;
            this.landZ = this.holdAtHome ? this.homeZ : this.z;
            this.holdAtHome = false;
            this.enter(RescuePhase.LAND);
            return;
        }
        if (this.holdAtHome) {
            if (cfg.autoLand || this.autonomous) {
                this.holdAtHome = false;
                this.arriveHome(world, cfg);
            }
            return;
        }
        if (this.gaveUp) {
            if (this.autonomous) {
                this.landNow(world);
            }
            return;
        }
        if (this.phaseTime >= HOLD_RETRY) {
            if (this.bestDistance < this.retryDistance - PROGRESS_GAIN) {
                this.retries = 0;
            }
            this.retryDistance = Math.min(this.retryDistance, this.bestDistance);
            this.retries++;
            if (this.retries > MAX_RETRIES) {
                this.gaveUp = true;
                if (this.autonomous) {
                    this.landNow(world);
                }
                return;
            }
            this.stuckLevel = 0;
            this.markProgress();
            this.enter(RescuePhase.CLIMB);
        }
    }

    private void watchdog(RescueWorld world, RescueSettings cfg) {
        if (this.phase != RescuePhase.CLIMB && this.phase != RescuePhase.ESCAPE && this.phase != RescuePhase.CRUISE) {
            this.markProgress();
            return;
        }
        double dx = this.x - this.anchorX;
        double dy = this.bottom - this.anchorY;
        double dz = this.z - this.anchorZ;
        if (dx * dx + dy * dy + dz * dz > STUCK_MOVE * STUCK_MOVE) {
            this.markProgress();
            this.stuckLevel = 0;
            return;
        }
        if (this.time - this.progressTime < STUCK_WINDOW) {
            return;
        }
        this.markProgress();
        this.stuckLevel++;
        if (this.stuckLevel == 1 && cfg.obstacleAvoidance) {
            this.direct = false;
            this.cruiseAlt = Math.min(this.altCeiling, Math.max(this.cruiseAlt, this.bottom) + 6.0);
            this.enter(RescuePhase.CLIMB);
        } else if (this.stuckLevel == 2 && cfg.obstacleAvoidance) {
            this.startEscape(world, cfg);
        } else {
            this.enterHold();
        }
    }

    private void markProgress() {
        this.anchorX = this.x;
        this.anchorY = this.bottom;
        this.anchorZ = this.z;
        this.progressTime = this.time;
    }

    private void detectTouchdown(double dt) {
        double speed = Math.sqrt(this.vx * this.vx + this.vy * this.vy + this.vz * this.vz);
        boolean resting = this.grounded && speed < 0.6;
        boolean nearGround = this.bottom - this.groundY < 0.08 && Math.abs(this.vy) < 0.2 && speed < 0.5;
        if (resting || nearGround) {
            this.landedTimer += dt;
        } else {
            this.landedTimer = 0.0;
        }
        if (this.landedTimer >= LANDED_CONFIRM) {
            this.enter(RescuePhase.LANDED);
            this.wantsDisarm = true;
        }
    }

    private double speedCap(RescueSettings cfg) {
        return Math.max(1.0, Math.min(cfg.speed, this.maxSpeed));
    }

    private void positionTarget(double tx, double tz, double maxSpeed) {
        double ex = tx - this.x;
        double ez = tz - this.z;
        double e = Math.sqrt(ex * ex + ez * ez);
        if (e < 1e-6) {
            this.desiredVx = 0.0;
            this.desiredVz = 0.0;
            return;
        }
        double v = Math.min(maxSpeed, Math.min(KP_POS * e, Math.sqrt(2.0 * BRAKE_ACCEL * e)));
        this.desiredVx = ex / e * v;
        this.desiredVz = ez / e * v;
    }

    private void guideAndControl(double dt, RescueSettings cfg) {
        double altTarget = this.bottom;
        double vyd = Double.NaN;
        boolean yawToTravel = false;
        switch (this.phase) {
            case CLIMB -> {
                this.positionTarget(this.holdX, this.holdZ, 2.0);
                altTarget = Math.min(this.cruiseAlt, this.climbLimit);
            }
            case ESCAPE -> {
                altTarget = Math.min(this.escapeAlt, this.climbLimit);
                if (this.routeLen > 0) {
                    altTarget = this.routeTarget();
                    yawToTravel = true;
                } else if (Math.abs(this.bottom - this.escapeAlt) < 0.6) {
                    this.positionTarget(this.escapeX, this.escapeZ, ESCAPE_SPEED);
                    yawToTravel = true;
                } else {
                    this.positionTarget(this.holdX, this.holdZ, 1.0);
                }
            }
            case CRUISE -> {
                altTarget = Math.min(this.cruiseAlt, this.climbLimit);
                yawToTravel = true;
                if (this.routeLen > 0) {
                    altTarget = this.routeTarget();
                } else if (this.steerUntil > this.time) {
                    this.desiredVx = this.steerX * STEER_SPEED;
                    this.desiredVz = this.steerZ * STEER_SPEED;
                } else {
                    double dx = this.homeX - this.x;
                    double dz = this.homeZ - this.z;
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    double cap = this.direct ? Math.min(this.speedCap(cfg), 4.0) : this.speedCap(cfg);
                    double v = Math.min(cap, Math.min(this.obstacleSpeed, Math.sqrt(2.0 * BRAKE_ACCEL * Math.max(0.0, dist - 0.3))));
                    if (dist > 1e-6) {
                        this.desiredVx = dx / dist * v;
                        this.desiredVz = dz / dist * v;
                    } else {
                        this.desiredVx = 0.0;
                        this.desiredVz = 0.0;
                    }
                }
            }
            case HOLD -> {
                this.positionTarget(this.holdX, this.holdZ, 2.0);
                altTarget = Math.min(this.holdAlt, this.climbLimit);
            }
            case LAND -> {
                this.positionTarget(this.landX, this.landZ, 2.0);
                double ex = this.landX - this.x;
                double ez = this.landZ - this.z;
                double err = Math.sqrt(ex * ex + ez * ez);
                double h = Math.max(0.0, this.bottom - this.groundY);
                double rate = Math.max(TOUCHDOWN_RATE, Math.min(cfg.descentRate, DESCENT_SLOPE * h));
                double scale = Math.max(0.0, Math.min(1.0, (1.5 - err) / 1.0));
                vyd = -rate * scale;
            }
            default -> {
                this.desiredVx = 0.0;
                this.desiredVz = 0.0;
            }
        }
        if (Double.isNaN(vyd)) {
            vyd = Math.max(-cfg.descentRate, Math.min(this.climbRate, KP_ALT * (altTarget - this.bottom)));
        }
        double speed = Math.sqrt(this.desiredVx * this.desiredVx + this.desiredVz * this.desiredVz);
        this.control(dt, this.desiredVx, this.desiredVz, vyd, yawToTravel && speed > YAW_MIN_SPEED);
    }

    private double routeTarget() {
        int i = Math.min(this.routeIdx, this.routeLen - 1);
        boolean last = i == this.routeLen - 1;
        double ax = this.segmentStartX(i);
        double ay = this.segmentStartY(i);
        double az = this.segmentStartZ(i);
        double bx = this.routeX[i];
        double by = this.routeY[i];
        double bz = this.routeZ[i];
        double sx = bx - ax;
        double sz = bz - az;
        double len = Math.sqrt(sx * sx + sz * sz);
        if (len < 1e-6) {
            this.positionTarget(bx, bz, this.routeSpeed);
            return by;
        }
        double ux = sx / len;
        double uz = sz / len;
        double t = (this.x - ax) * ux + (this.z - az) * uz;
        double ex = ax + ux * t - this.x;
        double ez = az + uz * t - this.z;
        double remaining = len - t;
        double along;
        if (last) {
            along = Math.signum(remaining) * Math.min(this.routeSpeed,
                    Math.min(KP_POS * Math.abs(remaining) + 0.2, Math.sqrt(2.0 * BRAKE_ACCEL * Math.abs(remaining))));
        } else {
            along = this.routeSpeed * clamp((remaining + ROUTE_PASS_SLACK) / 1.0, -1.0, 1.0);
        }
        double vx = ux * along + ROUTE_LATERAL_GAIN * ex;
        double vz = uz * along + ROUTE_LATERAL_GAIN * ez;
        double v = Math.sqrt(vx * vx + vz * vz);
        double cap = this.routeSpeed + 0.5;
        if (v > cap) {
            vx *= cap / v;
            vz *= cap / v;
        }
        this.desiredVx = vx;
        this.desiredVz = vz;
        double progress = clamp((t + 0.5) / len, 0.0, 1.0);
        return ay + (by - ay) * progress;
    }

    private void control(double dt, double vdx, double vdz, double vyd, boolean yawToTravel) {
        double evx = vdx - this.vx;
        double evz = vdz - this.vz;
        double desiredSpeed = Math.sqrt(vdx * vdx + vdz * vdz);
        double ax = KV * evx + this.integralX + this.dragPerMass * desiredSpeed * vdx;
        double az = KV * evz + this.integralZ + this.dragPerMass * desiredSpeed * vdz;
        double g = SimTuning.referenceGravity(this.gravity);
        double maxHorizontal = g * Math.tan(this.maxTilt);
        double ah = Math.sqrt(ax * ax + az * az);
        if (ah > maxHorizontal) {
            ax *= maxHorizontal / ah;
            az *= maxHorizontal / ah;
        } else {
            this.integralX = clamp(this.integralX + KI_H * evx * dt, -MAX_I_H, MAX_I_H);
            this.integralZ = clamp(this.integralZ + KI_H * evz * dt, -MAX_I_H, MAX_I_H);
        }
        double evy = vyd - this.vy;
        double ty = g + KV_Z * evy + this.integralY;
        double minTy = 0.3 * g;
        double maxTy = 0.9 * this.thrustToWeight * g * Math.cos(this.maxTilt);
        boolean saturated = false;
        if (ty < minTy) {
            ty = minTy;
            saturated = true;
        } else if (ty > maxTy) {
            ty = maxTy;
            saturated = true;
        }
        if (!saturated) {
            this.integralY = clamp(this.integralY + KI_Z * evy * dt, -MAX_I_Z, MAX_I_Z);
        }
        double th = Math.sqrt(ax * ax + az * az);
        double maxTh = ty * Math.tan(this.maxTilt);
        if (th > maxTh && th > 1e-9) {
            ax *= maxTh / th;
            az *= maxTh / th;
        }
        double magnitude = Math.sqrt(ax * ax + ty * ty + az * az);
        double ratio = Math.min(magnitude / g, 0.95 * this.thrustToWeight);
        this.throttle = this.stickForThrustRatio(ratio);
        double sin = Math.sin(this.heading);
        double cos = Math.cos(this.heading);
        double left = (ax * cos - az * sin) / magnitude;
        double forward = (ax * sin + az * cos) / magnitude;
        double up = ty / magnitude;
        double rollDeg = Math.toDegrees(Math.asin(clamp(-left, -1.0, 1.0)));
        double pitchDeg = Math.toDegrees(Math.atan2(forward, up));
        this.roll = StickMath.angleToStick(rollDeg, this.pid);
        this.pitch = StickMath.angleToStick(pitchDeg, this.pid);
        if (yawToTravel) {
            double target = Math.atan2(vdx, vdz);
            double errorDeg = Math.toDegrees(wrap(target - this.heading));
            double headingRate = clamp(YAW_GAIN * errorDeg, -MAX_YAW_RATE_DEG, MAX_YAW_RATE_DEG);
            this.yaw = StickMath.rateToStick(-headingRate, this.rates, Axis.YAW);
        } else {
            this.yaw = 0.0;
        }
    }

    private double stickForThrustRatio(double ratio) {
        double idle = this.pid.motorIdlePercent / 100.0;
        double span = (1.0 - idle) * this.pid.motorOutputLimitPercent / 100.0;
        if (span < 1e-6) {
            return this.hoverStick;
        }
        double hoverDuty = idle + span * ThrottleCurve.apply(this.pid, this.hoverStick);
        double duty = hoverDuty * Math.sqrt(Math.max(0.0, ratio));
        return ThrottleCurve.inverse(this.pid, clamp((duty - idle) / span, 0.0, 1.0));
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double finite(double v) {
        return Double.isFinite(v) ? v : 0.0;
    }

    private static double wrap(double angle) {
        double a = angle % (2.0 * Math.PI);
        if (a > Math.PI) {
            a -= 2.0 * Math.PI;
        } else if (a <= -Math.PI) {
            a += 2.0 * Math.PI;
        }
        return a;
    }
}
