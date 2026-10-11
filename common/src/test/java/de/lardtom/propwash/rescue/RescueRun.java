package de.lardtom.propwash.rescue;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.EventQueue;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.sim.SimEventType;
import de.lardtom.propwash.sim.SimTuning;

import java.util.EnumSet;
import java.util.SplittableRandom;
import java.util.Set;

final class RescueRun {
    static final double FPS = 60.0;

    final VoxelWorld world;
    final RescueSettings cfg;
    final VoxelFlight flight;
    final RescueAutopilot autopilot = new RescueAutopilot();
    final Set<RescuePhase> visited = EnumSet.noneOf(RescuePhase.class);
    final double homeX;
    final double homeY;
    final double homeZ;
    int crashes;
    int hardImpacts;
    double time;
    double maxBottom = Double.NEGATIVE_INFINITY;
    double fps = FPS;
    double jitter;
    private final SplittableRandom random = new SplittableRandom(3L);

    RescueRun(VoxelWorld world, DroneBuild build, RescueSettings cfg, double homeX, double homeY, double homeZ) {
        this.world = world;
        this.cfg = cfg;
        this.homeX = homeX;
        this.homeY = homeY;
        this.homeZ = homeZ;
        this.flight = new VoxelFlight(new DroneSim(build, new FlightControllerConfig(), new SimTuning(), 7L), world);
    }

    RescueRun autonomous() {
        this.autopilot.setAutonomous(true);
        return this;
    }

    RescueRun launch(double x, double bottom, double z, double heading) {
        this.flight.place(x, bottom, z, heading);
        this.flight.arm();
        this.autopilot.start(this.flight.sim, this.flight.x(), this.flight.bottom(), this.flight.z(),
                this.homeX, this.homeY, this.homeZ, this.world, this.cfg);
        this.visited.add(this.autopilot.phase());
        return this;
    }

    boolean runUntilDisarmed(double maxSeconds) {
        while (this.time < maxSeconds) {
            this.step(this.nextDt());
            if (!this.flight.sim.telemetry().armed) {
                return true;
            }
        }
        return false;
    }

    void runFor(double seconds) {
        double end = this.time + seconds;
        while (this.time < end) {
            this.step(this.nextDt());
        }
    }

    private void step(double dt) {
        this.autopilot.update(dt, this.flight.sim, this.flight.x(), this.flight.bottom(), this.flight.z(), this.world, this.cfg);
        this.visited.add(this.autopilot.phase());
        this.flight.apply(this.autopilot);
        this.flight.frame(Math.round(dt * 1e9));
        EventQueue events = this.flight.sim.events();
        this.crashes += events.count(SimEventType.CRASH);
        this.hardImpacts += events.count(SimEventType.HARD_IMPACT);
        this.maxBottom = Math.max(this.maxBottom, this.flight.bottom());
        this.time += dt;
    }

    RescueRun timing(double framesPerSecond, double jitterFraction) {
        this.fps = framesPerSecond;
        this.jitter = jitterFraction;
        return this;
    }

    private double nextDt() {
        double base = 1.0 / this.fps;
        return base * (1.0 + this.jitter * (2.0 * this.random.nextDouble() - 1.0));
    }

    double distanceToHome() {
        double dx = this.flight.x() - this.homeX;
        double dz = this.flight.z() - this.homeZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    String describe() {
        return String.format("t=%.1fs phase=%s pos=(%.2f, %.2f, %.2f) dist=%.2f crashes=%d hard=%d visited=%s cruiseAlt=%.1f maxBottom=%.1f",
                this.time, this.autopilot.phase(), this.flight.x(), this.flight.bottom(), this.flight.z(), this.distanceToHome(),
                this.crashes, this.hardImpacts, this.visited, this.autopilot.cruiseAltitude(), this.maxBottom);
    }
}
