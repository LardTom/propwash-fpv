package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;

final class FlatWorld {
    static final double RAYCAST_RANGE = 5.0;
    static final double SUPPORT_PROBE = 0.05;

    final DroneSim sim;
    final PilotInput input = new PilotInput();
    final SimEnvironment env = new SimEnvironment();
    final StepResult result = new StepResult();
    final MoveReport report = new MoveReport();
    double groundY = 0.0;
    double wallX = Double.POSITIVE_INFINITY;
    double grip = 1.0;
    double restitution = -1.0;
    double x;
    double y;
    double z;
    private final double halfWidth;
    private final double foot;

    FlatWorld(DroneSim sim) {
        this.sim = sim;
        this.halfWidth = sim.params().hitboxWidth / 2.0;
        this.foot = sim.params().hitboxFootOffset;
    }

    static FlatWorld of(DroneBuild build) {
        return new FlatWorld(new DroneSim(build, new FlightControllerConfig(), Tests.quietTuning(), 42L));
    }

    static FlatWorld of(DroneBuild build, SimTuning tuning) {
        return new FlatWorld(new DroneSim(build, new FlightControllerConfig(), tuning, 42L));
    }

    void place(double px, double py, double pz, double heading) {
        this.sim.reset(px, py, pz, heading);
        this.x = px;
        this.y = py;
        this.z = pz;
    }

    void placeOnGround(double px, double pz, double heading) {
        this.place(px, this.groundY - this.foot, pz, heading);
    }

    double bottom() {
        return this.y + this.foot;
    }

    void frame(double dt) {
        this.frame(Math.round(dt * 1e9));
    }

    void frame(long nanos) {
        double distance = this.y - this.groundY;
        this.env.groundDistance = distance <= RAYCAST_RANGE ? Math.max(0.0, distance) : Double.POSITIVE_INFINITY;
        this.sim.step(nanos, this.input, this.env, this.result);
        this.move();
    }

    void move() {
        double dx = this.result.dx;
        double dy = this.result.dy;
        double dz = this.result.dz;
        double ay = dy;
        if (dy < 0.0) {
            ay = Math.max(dy, Math.min(0.0, this.groundY - this.bottom()));
        }
        this.y += ay;
        double ax = dx;
        if (dx > 0.0 && this.wallX < Double.POSITIVE_INFINITY) {
            ax = Math.min(dx, Math.max(0.0, this.wallX - (this.x + this.halfWidth)));
        }
        this.x += ax;
        double az = dz;
        this.z += az;
        MoveReport r = this.report;
        r.clear();
        r.requestedX = dx;
        r.requestedY = dy;
        r.requestedZ = dz;
        r.actualX = ax;
        r.actualY = ay;
        r.actualZ = az;
        r.hitX = Math.abs(ax - dx) > 1e-7;
        r.hitY = Math.abs(ay - dy) > 1e-7;
        r.hitZ = Math.abs(az - dz) > 1e-7;
        r.surfaceGrip = this.grip;
        r.surfaceRestitution = this.restitution;
        r.supportGap = Math.min(SUPPORT_PROBE, Math.max(0.0, this.bottom() - this.groundY));
        this.sim.afterMove(r);
    }

    void run(double seconds, double fps) {
        long frameNanos = Math.round(1e9 / fps);
        long total = Math.round(seconds * 1e9);
        for (long t = 0; t < total; t += frameNanos) {
            this.frame(frameNanos);
        }
    }

    void arm() {
        this.input.armSwitch = false;
        this.frame(0L);
        this.input.armSwitch = true;
        this.frame(0L);
    }
}
