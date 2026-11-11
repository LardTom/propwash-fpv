package de.lardtom.propwash.interact.client;

import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.interact.CovVineArena;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.Telemetry;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

final class CovVineDropWatch {
    static final int WATCH_TICKS = 100;
    static final int LATE_TICKS = 30;
    private static final int AIRMODE_GRACE_TICKS = 6;
    private static final double HIGH_OUTPUT = 0.5;
    private static final int MAX_HIGH_RUN_TICKS = 6;
    private static final double MAX_HIGH_SECONDS = 0.5;
    private static final double MAX_LATE_OUTPUT = 0.05;
    private static final double MAX_LATE_THRUST = 0.15;
    private static final double MAX_LATE_TRAVEL = 1.0;
    private static final double FREE_BELOW = 0.25;
    private static final double FREE_SIDE = 0.05;
    private static final double FALL_SPEED_LIMIT = 6.0;
    private static final double LOW_THRUST = 0.3;
    private static final double TICK = 0.05;
    private static final double MIN_STEP = 0.02;
    private static final double GRAVITY = 9.81;
    private static final double FALL_SHARE = 0.4;

    private final CovVineArena.Flight flight;
    private int ticks;
    private int contactTick = -1;
    private final int crashes;
    private final int impacts;
    private final int epoch;
    private final int pushOuts;
    private boolean airmode;
    private double worst;
    private int eyeTicks;
    private int highTicks;
    private int highRun;
    private int longestRun;
    private int floatTicks;
    private int snagTicks;
    private double lateOutput;
    private double lateThrust;
    private int lateCount;
    private @Nullable Vec3 lateStart;
    private double lateTravel;
    private double lastVy;
    private double lastTime;
    private @Nullable String firstFloat;

    CovVineDropWatch(CovVineArena.Flight flight, QuadcopterEntity drone, DroneSim sim) {
        this.flight = flight;
        this.crashes = PilotSession.INSTANCE.crashCount();
        this.impacts = sim.telemetry().impactCount;
        this.epoch = drone.resetEpoch();
        this.pushOuts = sim.pushOuts();
        this.lastVy = -flight.descent();
        this.lastTime = sim.telemetry().time;
    }

    static double meanOutput(Telemetry t) {
        double sum = 0.0;
        for (int i = 0; i < 4; i++) {
            sum += t.motorOutput[i];
        }
        return sum / 4.0;
    }

    static boolean freeBelow(Level level, QuadcopterEntity drone) {
        AABB box = drone.getBoundingBox();
        return level.noBlockCollision(drone, box.expandTowards(0.0, -FREE_BELOW, 0.0).inflate(FREE_SIDE, 0.0, FREE_SIDE));
    }

    boolean done() {
        return this.ticks >= WATCH_TICKS;
    }

    void tick(Level level, QuadcopterEntity drone, DroneSim sim, Vec3 eye) {
        this.ticks++;
        int since = this.ticks;
        Telemetry t = sim.telemetry();
        Vector3d velocity = new Vector3d();
        sim.getVelocity(velocity);
        double dvy = velocity.y - this.lastVy;
        double step = t.time - this.lastTime;
        this.lastVy = velocity.y;
        this.lastTime = t.time;
        if (this.contactTick < 0 && (t.grounded || t.impactCount != this.impacts || PilotSession.INSTANCE.crashCount() != this.crashes)) {
            this.contactTick = since;
            this.airmode = t.airmodeActive;
        }
        this.worst = Math.max(this.worst, CovVineBeamSelfTests.penetration(level, drone));
        if (CovVineBeamSelfTests.eyeInside(level, eye)) {
            this.eyeTicks++;
        }
        if (this.contactTick < 0) {
            return;
        }
        double out = meanOutput(t);
        if (since - this.contactTick > AIRMODE_GRACE_TICKS) {
            this.highRun = out > HIGH_OUTPUT ? this.highRun + 1 : 0;
            this.longestRun = Math.max(this.longestRun, this.highRun);
            if (out > HIGH_OUTPUT) {
                this.highTicks++;
            }
        }
        if (since > WATCH_TICKS - LATE_TICKS) {
            if (this.lateStart == null) {
                this.lateStart = drone.position();
            }
            this.lateOutput += out;
            this.lateThrust += t.thrustToWeight;
            this.lateCount++;
            this.lateTravel = Math.max(this.lateTravel, Math.hypot(drone.getX() - this.lateStart.x, drone.getZ() - this.lateStart.z));
        }
        if (since > this.contactTick) {
            boolean snag = PilotSnags.INSTANCE.touching();
            if (snag) {
                this.snagTicks++;
            }
            if (!snag && step >= MIN_STEP && freeBelow(level, drone) && t.thrustToWeight < LOW_THRUST && velocity.length() < FALL_SPEED_LIMIT
                    && dvy > -FALL_SHARE * GRAVITY * step) {
                this.floatTicks++;
                if (this.firstFloat == null) {
                    this.firstFloat = String.format(Locale.ROOT, "nach %d Ticks bei %.2f %.2f %.2f, vy %.2f, Δvy %.3f in %.3f s, aufliegend %s", since,
                            drone.getX(), drone.getY(), drone.getZ(), velocity.y, dvy, step, t.grounded);
                }
            }
        }
    }

    String line(QuadcopterEntity drone, DroneSim sim, int[] base) {
        int resets = drone.resetEpoch() - this.epoch;
        double late = this.lateCount == 0 ? Double.NaN : this.lateOutput / this.lateCount;
        double lateThrust = this.lateCount == 0 ? Double.NaN : this.lateThrust / this.lateCount;
        return String.format(Locale.ROOT, "%s: Airmode %s, Treffer nach %d Ticks, Motoren über %.0f %% %.2f s (längster Stoß %.2f s), "
                        + "in den letzten %.1f s Motoren %.3f, Schub %.2f×G, Weg %.2f Blöcke, schwebend frei %d Ticks%s, in Ranken %d Ticks, "
                        + "Eindringen %.3f m, Auge im Block %d Ticks, Server-Rücksetzer %d, Ausweichen %d, Ende %.2f %.2f %.2f, scharf %s",
                this.flight.what(), this.airmode, this.contactTick, 100.0 * HIGH_OUTPUT, this.highTicks * TICK, this.longestRun * TICK, LATE_TICKS * TICK,
                late, lateThrust, this.lateTravel, this.floatTicks, this.firstFloat == null ? "" : " (" + this.firstFloat + ")", this.snagTicks,
                this.worst, this.eyeTicks, resets, sim.pushOuts() - this.pushOuts, drone.getX() - base[0], drone.getY() - base[1], drone.getZ() - base[2],
                sim.telemetry().armed);
    }

    boolean bad(QuadcopterEntity drone) {
        int resets = drone.resetEpoch() - this.epoch;
        double late = this.lateCount == 0 ? Double.NaN : this.lateOutput / this.lateCount;
        double lateThrust = this.lateCount == 0 ? Double.NaN : this.lateThrust / this.lateCount;
        boolean motors = this.longestRun <= MAX_HIGH_RUN_TICKS && this.highTicks * TICK <= MAX_HIGH_SECONDS && late <= MAX_LATE_OUTPUT
                && lateThrust <= MAX_LATE_THRUST && this.lateTravel <= MAX_LATE_TRAVEL;
        return this.contactTick < 0 || !this.airmode || !motors || this.floatTicks > 1 || this.worst > CovVineBeamSelfTests.PENETRATION
                || this.eyeTicks > 0 || resets > 0;
    }
}
