package de.lardtom.propwash.video.record.path;

import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraTrackTest {
    private static PathSample sample(double tick, double x, double y, double z, Quaterniond q, double fov) {
        return new PathSample(tick, x, y, z, q.x, q.y, q.z, q.w, fov);
    }

    private static CameraPathBuffer cruise(double fps, double seconds, double speed, double fov) {
        CameraPathBuffer buffer = new CameraPathBuffer();
        double step = 20.0 / fps;
        Quaterniond q = new Quaterniond().rotationY(0.3).rotateX(0.4);
        for (double t = 3.37; t <= 3.37 + seconds * 20.0; t += step) {
            buffer.add(sample(t, 10.0 + speed * t / 20.0, 70.0, -4.0, q, fov));
        }
        return buffer;
    }

    @Test
    void bufferKeepsOnlyIncreasingFiniteSamples() {
        CameraPathBuffer buffer = new CameraPathBuffer();
        Quaterniond q = new Quaterniond();
        assertTrue(buffer.add(sample(1.0, 0, 0, 0, q, 90)));
        assertFalse(buffer.add(sample(1.0, 1, 0, 0, q, 90)));
        assertFalse(buffer.add(sample(0.5, 1, 0, 0, q, 90)));
        assertFalse(buffer.add(sample(2.0, Double.NaN, 0, 0, q, 90)));
        assertFalse(buffer.add(new PathSample(2.0, 0, 0, 0, 0, 0, 0, 0, 90)));
        assertFalse(buffer.add(sample(2.0, 0, 0, 0, q, 0)));
        assertTrue(buffer.add(sample(2.5, 1, 0, 0, q, 90)));
        assertEquals(2, buffer.size());
        assertEquals(1.5, buffer.spanTicks(), 1.0E-12);
    }

    @Test
    void bufferGrowsPastItsFirstBlock() {
        CameraPathBuffer buffer = cruise(144.0, 30.0, 10.0, 100.0);
        assertTrue(buffer.size() > 4000);
        assertEquals(10.0 + 10.0 * buffer.lastTick() / 20.0, buffer.get(buffer.size() - 1).x(), 1.0E-9);
    }

    @Test
    void bufferFlipsQuaternionsOntoOneHemisphere() {
        CameraPathBuffer buffer = new CameraPathBuffer();
        Quaterniond q = new Quaterniond().rotationY(1.0);
        buffer.add(sample(0.0, 0, 0, 0, q, 90));
        buffer.add(new PathSample(1.0, 0, 0, 0, -q.x, -q.y, -q.z, -q.w, 90));
        PathSample second = buffer.get(1);
        assertTrue(second.qw() * q.w + second.qy() * q.y > 0.0);
    }

    @Test
    void resamplesOntoWholeTicks() {
        CameraPathBuffer buffer = new CameraPathBuffer();
        Quaterniond a = new Quaterniond();
        Quaterniond b = new Quaterniond().rotationY(Math.toRadians(40.0));
        buffer.add(sample(4.6, 0.0, 64.0, 0.0, a, 100.0));
        buffer.add(sample(5.2, 6.0, 64.0, 0.0, b, 110.0));
        buffer.add(sample(6.9, 6.0, 81.0, 0.0, b, 110.0));
        List<TickPose> poses = PathResampler.perTick(buffer);
        assertEquals(2, poses.size());
        TickPose five = poses.get(0);
        assertEquals(5, five.tick());
        assertEquals(4.0, five.x(), 1.0E-9);
        assertEquals(40.0 * 2.0 / 3.0, CameraAngles.degreesBetween(five.rotation(), a), 1.0E-6);
        assertEquals(106.6666667, five.fov(), 1.0E-6);
        TickPose six = poses.get(1);
        assertEquals(6, six.tick());
        assertEquals(64.0 + 17.0 * 0.8 / 1.7, six.y(), 1.0E-9);
    }

    @Test
    void interpolatesAtAnyTickInsideThePath() {
        CameraPathBuffer buffer = cruise(50.0, 2.0, 20.0, 110.0);
        PathSample mid = PathResampler.at(buffer, 17.25).orElseThrow();
        assertEquals(10.0 + 20.0 * 17.25 / 20.0, mid.x(), 1.0E-9);
        assertEquals(110.0, mid.fov(), 1.0E-9);
        assertTrue(PathResampler.at(buffer, 1.0).isEmpty());
        assertTrue(PathResampler.at(buffer, 1000.0).isEmpty());
        assertEquals(buffer.firstTick(), PathResampler.at(buffer, buffer.firstTick()).orElseThrow().tick(), 1.0E-12);
    }

    @Test
    void everyTickKeepsOneKeyframePerTick() {
        CameraTrack track = CameraTrack.of(cruise(60.0, 5.0, 12.0, 120.0), KeyframeDensity.EVERY_TICK);
        assertTrue(track.usable());
        assertEquals(4, track.startTick());
        assertEquals(103, track.endTick());
        assertEquals(100, track.camera().size());
        for (int i = 0; i < track.camera().size(); i++) {
            PathKeyframe key = track.camera().get(i);
            assertEquals(track.startTick() + i, key.tick());
            assertEquals(10.0 + 12.0 * key.tick() / 20.0, key.x(), 1.0E-9);
        }
        assertEquals(List.of(new FovKeyframe(4, 120.0F), new FovKeyframe(103, 120.0F)), track.fov());
        assertEquals(4.95, track.durationSeconds(), 1.0E-9);
    }

    @Test
    void reducedKeepsStraightFlightLight() {
        CameraTrack track = CameraTrack.of(cruise(75.0, 10.0, 15.0, 120.0), KeyframeDensity.REDUCED);
        assertTrue(track.camera().size() <= 2 + 200 / CameraTrack.MAX_SPAN_TICKS + 5, "Keyframes: " + track.camera().size());
        assertEquals(track.startTick(), track.camera().getFirst().tick());
        assertEquals(track.endTick(), track.camera().getLast().tick());
        for (int i = 1; i < track.camera().size(); i++) {
            assertTrue(track.camera().get(i).tick() - track.camera().get(i - 1).tick() <= CameraTrack.MAX_SPAN_TICKS);
        }
    }

    @Test
    void reducedStaysWithinToleranceThroughFlips() {
        List<Quaterniond> rotations = EulerPathTest.maneuver(20.0, 30.0, 2.0, 1.0, 100);
        CameraPathBuffer buffer = new CameraPathBuffer();
        for (int i = 0; i < rotations.size(); i++) {
            double t = i;
            buffer.add(sample(t, Math.sin(t * 0.07) * 9.0, 80.0 + Math.cos(t * 0.05) * 3.0, t * 0.4, rotations.get(i), 120.0));
        }
        CameraTrack every = CameraTrack.of(buffer, KeyframeDensity.EVERY_TICK);
        CameraTrack reduced = CameraTrack.of(buffer, KeyframeDensity.REDUCED);
        assertTrue(reduced.camera().size() <= every.camera().size());
        PathFrames frames = PathFrames.of(PathResampler.perTick(buffer));
        Simplification tolerance = Simplification.ofPosition(KeyframeDensity.REDUCED.positionTolerance());
        PathPreview preview = PathPreview.of(frames, CameraTrack.build(frames, tolerance, Integer.MAX_VALUE), 1, 2.0F, Integer.MAX_VALUE);
        assertEquals(reduced, preview.built().track());
        assertTrue(preview.maxSameTime() <= tolerance.position() * Simplification.REFINE_FACTOR + 1.0E-9, "Position " + preview.maxSameTime());
        assertTrue(preview.maxAngle() <= tolerance.angle() * Simplification.REFINE_FACTOR + 1.0E-3, "Winkel " + preview.maxAngle());
    }

    @Test
    void fovChangesBecomeHoldKeyframes() {
        CameraPathBuffer buffer = new CameraPathBuffer();
        Quaterniond q = new Quaterniond();
        for (int t = 0; t <= 40; t++) {
            buffer.add(sample(t, 0, 0, 0, q, t < 20 ? 100.0 : 112.5));
        }
        CameraTrack track = CameraTrack.of(buffer, KeyframeDensity.REDUCED);
        assertEquals(List.of(new FovKeyframe(0, 100.0F), new FovKeyframe(20, 112.5F), new FovKeyframe(40, 112.5F)), track.fov());
    }

    @Test
    void tooShortPathsAreNotUsable() {
        CameraPathBuffer buffer = new CameraPathBuffer();
        assertFalse(CameraTrack.of(buffer, KeyframeDensity.REDUCED).usable());
        Quaterniond q = new Quaterniond();
        buffer.add(sample(2.2, 0, 0, 0, q, 90));
        buffer.add(sample(2.8, 0, 0, 0, q, 90));
        assertFalse(CameraTrack.of(buffer, KeyframeDensity.REDUCED).usable());
        buffer.add(sample(4.1, 0, 0, 0, q, 90));
        assertTrue(CameraTrack.of(buffer, KeyframeDensity.REDUCED).usable());
    }
}
