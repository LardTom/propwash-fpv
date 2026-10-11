package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.video.PoseHistory;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisiblePoseTimingTest {
    static final double ALTITUDE = 3000.0;
    static final double FPS = 120.0;
    static final double FRAME_JITTER = 0.15;
    static final long VIDEO_DELAY_NANOS = 18_700_000L;

    record Timing(double speed, double meanMs, double maxMs, double maxMillimetres, double meanPendingMs) {
    }

    static FlatWorld racer() {
        SimTuning tuning = new SimTuning();
        tuning.infiniteBattery = true;
        FlightControllerConfig config = new FlightControllerConfig();
        config.pid.angleLimitDeg = 85.0;
        FlatWorld w = new FlatWorld(new DroneSim(DroneBuild.RACER, config, tuning, 42L));
        w.place(0.0, ALTITUDE, 0.0, 0.0);
        w.sim.setBatteryCharge(1.0);
        w.input.mode = StabMode.ANGLE;
        w.arm();
        return w;
    }

    static Timing fly(boolean stampSimState) {
        FlatWorld w = racer();
        AltitudeHold hold = new AltitudeHold(ALTITUDE);
        Random random = new Random(7L);
        PoseHistory poses = new PoseHistory(256);
        Vector3d shown = new Vector3d();
        Quaternionf rotation = new Quaternionf();
        Quaternionf recorded = new Quaternionf();
        RigidBody body = w.sim.body;
        long now = 1_000_000_000L;
        double t = 0.0;
        double start = 0.0;
        double previousVx = 0.0;
        double previousVy = 0.0;
        double previousVz = 0.0;
        double sum = 0.0;
        double max = 0.0;
        double maxDistance = 0.0;
        double speedSum = 0.0;
        double pendingSum = 0.0;
        int n = 0;
        while (t < 11.0) {
            long frame = Math.round(1.0E9 / FPS * (1.0 + FRAME_JITTER * (2.0 * random.nextDouble() - 1.0)));
            double dt = frame * 1.0E-9;
            now += frame;
            t += dt;
            if (t < 2.0) {
                hold.apply(w, dt);
                start = w.input.throttle;
            } else {
                double ramp = Math.min(1.0, t - 2.0);
                w.input.pitch = ramp * 70.0 / 85.0;
                w.input.throttle = start + (1.0 - start) * ramp;
            }
            w.frame(frame);
            long pending = w.sim.pendingNanos();
            Quaterniond o = w.result.orientation;
            recorded.set((float) o.x, (float) o.y, (float) o.z, (float) o.w).normalize();
            poses.record(stampSimState ? now - pending : now, w.x, w.y, w.z, recorded);
            double vx = body.vx;
            double vy = body.vy;
            double vz = body.vz;
            if (t > 8.0) {
                double tau = VIDEO_DELAY_NANOS * 1.0E-9;
                double half = 0.5 * tau * tau / dt;
                double ex = w.x - vx * tau + (vx - previousVx) * half;
                double ey = w.y - vy * tau + (vy - previousVy) * half;
                double ez = w.z - vz * tau + (vz - previousVz) * half;
                assertTrue(poses.sample(now - VIDEO_DELAY_NANOS, shown, rotation));
                double speed = SimMath.hypot3(vx, vy, vz);
                double along = ((shown.x - ex) * vx + (shown.y - ey) * vy + (shown.z - ez) * vz) / speed;
                sum += Math.abs(along) / speed;
                max = Math.max(max, Math.abs(along) / speed);
                maxDistance = Math.max(maxDistance, Math.abs(along));
                speedSum += speed;
                pendingSum += pending * 1.0E-9;
                n++;
            }
            previousVx = vx;
            previousVy = vy;
            previousVz = vz;
        }
        return new Timing(speedSum / n, 1000.0 * sum / n, 1000.0 * max, 1000.0 * maxDistance, 1000.0 * pendingSum / n);
    }

    @Test
    void visiblePoseIsExtrapolatedToTheFrameTime() {
        FlatWorld w = racer();
        Random random = new Random(3L);
        RigidBody body = w.sim.body;
        w.input.pitch = 0.6;
        w.input.throttle = 1.0;
        int withRemainder = 0;
        for (int f = 0; f < 600; f++) {
            w.frame(Math.round(1.0E9 / FPS * (1.0 + FRAME_JITTER * (2.0 * random.nextDouble() - 1.0))));
            double tau = w.sim.pendingNanos() * 1.0E-9;
            if (tau > 1.0E-5) {
                withRemainder++;
            }
            assertTrue(tau >= 0.0 && tau < DroneSim.SUBSTEP_NANOS * 1.0E-9);
            assertEquals(body.px + body.vx * tau, w.x, 1.0E-7);
            assertEquals(body.py + body.vy * tau, w.y, 1.0E-7);
            assertEquals(body.pz + body.vz * tau, w.z, 1.0E-7);
        }
        assertTrue(withRemainder > 500, "frames with remainder " + withRemainder);
        assertTrue(SimMath.hypot3(body.vx, body.vy, body.vz) > 20.0);
    }

    @Test
    void poseStampedWithTheFrameTimeIsShownWhereTheDroneWas() {
        Timing timing = fly(false);
        assertTrue(timing.speed() > 40.0, timing.toString());
        assertTrue(timing.meanMs() < 0.01, timing.toString());
        assertTrue(timing.maxMs() < 0.03, timing.toString());
        assertTrue(timing.maxMillimetres() < 1.5, timing.toString());
    }

    @Test
    void stampingTheSimStateTimeWouldShiftThePoseByTheRemainder() {
        Timing timing = fly(true);
        assertTrue(timing.meanPendingMs() > 0.15 && timing.meanPendingMs() < 0.35, timing.toString());
        assertEquals(timing.meanPendingMs(), timing.meanMs(), 0.05, timing.toString());
        assertTrue(timing.maxMs() > 0.4 && timing.maxMs() < 0.55, timing.toString());
        assertTrue(timing.maxMillimetres() > 15.0, timing.toString());
    }
}
