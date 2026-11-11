package de.lardtom.propwash.video.record.path;

import de.lardtom.propwash.sim.RateCurves;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AntiAliasTest {
    private static final double START = 3.37;
    private static final Quaterniond BASE = new Quaterniond().rotationY(0.4).rotateX(Math.toRadians(-20.0));
    private static final double LIMIT = RateCurves.RATE_LIMIT_DPS;
    private static final double RIPPLE = 0.2;
    private static final double RIPPLE_HZ = 5.0;
    private static final double[][] FAST_AXES = {{0.0, 0.0, 1.0}, {0.6, 0.0, 0.8}};

    private record Tone(double hz, double degrees, int axis, double phase) {
    }

    private record Flight(CameraPathBuffer buffer, double[] ticks, Quaterniond[] live, double rate) {
    }

    private static Flight hover(double fps, double seconds, double jitter, Tone... tones) {
        CameraPathBuffer buffer = new CameraPathBuffer();
        Random random = new Random(11L);
        int frames = (int) (seconds * fps);
        double[] ticks = new double[frames];
        Quaterniond[] live = new Quaterniond[frames];
        for (int i = 0; i < frames; i++) {
            double time = (i + (i > 0 ? jitter * (random.nextDouble() - 0.5) : 0.0)) / fps;
            double[] angle = new double[3];
            for (Tone tone : tones) {
                angle[tone.axis] += Math.toRadians(tone.degrees) * Math.sin(2.0 * Math.PI * tone.hz * time + tone.phase);
            }
            Quaterniond q = new Quaterniond(BASE).rotateX(angle[0]).rotateY(angle[1]).rotateZ(angle[2]);
            ticks[i] = START + time * 20.0;
            live[i] = q;
            assertTrue(buffer.add(new PathSample(ticks[i], 0.0, 70.0, 0.0, q.x, q.y, q.z, q.w, 70.0)));
        }
        return new Flight(buffer, ticks, live, fps);
    }

    private static Quaterniond[] everyTick(Flight flight) {
        CameraTrack track = CameraTrack.build(PathFrames.of(PathResampler.perTick(flight.buffer)), Simplification.OFF, Integer.MAX_VALUE).track();
        return PathWobble.export(track, flight.ticks);
    }

    private static double[][] fromBase(Quaterniond[] rotations) {
        Quaterniond[] base = new Quaterniond[rotations.length];
        Arrays.fill(base, BASE);
        return PathWobble.deviation(base, rotations);
    }

    private static boolean[] inner(Flight flight) {
        boolean[] mask = new boolean[flight.live.length];
        for (int i = (int) flight.rate; i < mask.length - (int) flight.rate; i++) {
            mask[i] = true;
        }
        return mask;
    }

    @Test
    void fastVibrationDoesNotTurnIntoSlowSway() {
        for (double fps : new double[]{60.0, 144.0}) {
            for (double hz : new double[]{13.3, 15.0, 16.7, 18.9, 21.1, 23.4, 26.0, 33.0}) {
                Flight flight = hover(fps, 12.0, 0.0, new Tone(hz, 1.0, 2, 0.3));
                double[][] export = fromBase(everyTick(flight));
                double alias = Math.abs(hz - 20.0 * Math.round(hz / 20.0));
                double sway = PathWobble.amplitude(export[2], (int) (2 * fps), (int) (10 * fps), fps, alias);
                assertTrue(sway < 0.05, String.format(Locale.ROOT, "%.0f fps: 1° bei %.1f Hz wird im Export %.2f° bei %.1f Hz", fps, hz, sway, alias));
            }
        }
    }

    @Test
    void slowMotionKeepsItsSize() {
        for (double fps : new double[]{30.0, 60.0, 144.0}) {
            for (double jitter : new double[]{0.0, 0.6}) {
                for (double hz : new double[]{0.5, 1.0, 2.0, 3.0}) {
                    Flight flight = hover(fps, 12.0, jitter, new Tone(hz, 3.0, 0, 0.7));
                    double[][] export = fromBase(everyTick(flight));
                    double gain = PathWobble.amplitude(export[0], (int) (2 * fps), (int) (10 * fps), fps, hz) / 3.0;
                    assertTrue(gain > 0.95 && gain < 1.01,
                            String.format(Locale.ROOT, "%.0f fps, Zittern %.1f: %.1f Hz mit Faktor %.3f im Export", fps, jitter, hz, gain));
                }
            }
        }
    }

    @Test
    void propwashLikeVibrationAddsNoSway() {
        for (double fps : new double[]{60.0, 144.0}) {
            for (double jitter : new double[]{0.0, 0.6}) {
                Flight flight = hover(fps, 16.0, jitter, new Tone(1.3, 2.0, 0, 0.0), new Tone(2.6, 1.2, 2, 1.0), new Tone(0.8, 1.5, 1, 2.0),
                        new Tone(14.6, 0.8, 0, 0.4), new Tone(17.3, 1.0, 2, 1.1), new Tone(22.7, 0.6, 1, 2.5), new Tone(19.4, 0.7, 0, 0.9),
                        new Tone(25.9, 0.5, 2, 1.7));
                PathWobble.Result result = PathWobble.compare(flight.live, everyTick(flight), inner(flight), fps);
                String text = String.format(Locale.ROOT, "%.0f fps, Zittern %.1f: live %.3f°, Export %.3f° (x%.2f), Schein-Wackeln %.3f°, davon langsam %.3f°",
                        fps, jitter, result.liveSway(), result.exportSway(), result.ratio(), result.fakeSway(), result.fakeSlowSway());
                assertTrue(result.fakeSlowSway() < 0.05, text);
                assertTrue(result.fakeSway() < 0.1, text);
                assertTrue(result.ratio() > 0.97 && result.ratio() < 1.02, text);
            }
        }
    }

    @Test
    void steadyTurnsStayExact() {
        StringBuilder wrong = new StringBuilder();
        for (double rate : new double[]{500.0, LIMIT, LIMIT * Math.sqrt(2.0), LIMIT * Math.sqrt(3.0)}) {
            for (double fps : new double[]{30.0, 60.0, 144.0, 240.0}) {
                CameraPathBuffer buffer = turning(fps, 6.0, rate, 0.0, 0.0);
                List<TickPose> poses = PathResampler.perTick(buffer);
                assertEquals((int) Math.floor(buffer.lastTick()) - (int) Math.ceil(buffer.firstTick()) + 1, poses.size());
                double worst = 0.0;
                for (TickPose pose : poses) {
                    worst = Math.max(worst, CameraAngles.degreesBetween(pointSampled(buffer, pose.tick()), pose.rotation()));
                    assertEquals(PathResampler.at(buffer, pose.tick()).orElseThrow().x(), pose.x(), 1.0E-9);
                }
                double tolerance = fps >= 144.0 ? 1.0E-4 : 2.0E-3;
                if (!(worst < tolerance)) {
                    wrong.append(String.format(Locale.ROOT, "%n%.0f°/s, %.0f fps: Tick-Lage %.5f° neben der Drehung (Grenze %.4f°)", rate, fps, worst, tolerance));
                }
            }
        }
        assertTrue(wrong.isEmpty(), wrong.toString());
    }

    @Test
    void fastTurnsWithRippleKeepTheirTicks() {
        StringBuilder wrong = new StringBuilder();
        for (double[] axis : FAST_AXES) {
            for (double rate : new double[]{0.8 * LIMIT, LIMIT, LIMIT * Math.sqrt(2.0), LIMIT * Math.sqrt(3.0)}) {
                for (double fps : new double[]{60.0, 144.0}) {
                    CameraPathBuffer buffer = turning(fps, 4.0, rate, RIPPLE, RIPPLE_HZ, axis[0], axis[1], axis[2]);
                    double worst = 0.0;
                    for (TickPose pose : PathResampler.perTick(buffer)) {
                        if (pose.tick() >= 10 && pose.tick() <= 70) {
                            worst = Math.max(worst, CameraAngles.degreesBetween(pointSampled(buffer, pose.tick()), pose.rotation()));
                        }
                    }
                    double swing = RIPPLE * rate / (2.0 * Math.PI * RIPPLE_HZ);
                    if (!(worst < 0.03 * swing)) {
                        wrong.append(String.format(Locale.ROOT, "%n%s %.0f°/s mit %.0f %% bei %.0f Hz, %.0f fps: Tick-Lage %.2f° neben der Drehung (Schwankung %.1f°)",
                                Arrays.toString(axis), rate, 100.0 * RIPPLE, RIPPLE_HZ, fps, worst, swing));
                    }
                }
            }
        }
        assertTrue(wrong.isEmpty(), wrong.toString());
    }

    @Test
    void fastTurnsExportLikePointSampledTicks() {
        StringBuilder wrong = new StringBuilder();
        for (double[] axis : FAST_AXES) {
            for (double rate : new double[]{0.8 * LIMIT, LIMIT, LIMIT * Math.sqrt(2.0)}) {
                CameraPathBuffer buffer = turning(144.0, 4.0, rate, RIPPLE, RIPPLE_HZ, axis[0], axis[1], axis[2]);
                List<TickPose> filtered = PathResampler.perTick(buffer);
                List<TickPose> sampled = new ArrayList<>();
                for (TickPose pose : filtered) {
                    PathSample point = PathResampler.at(buffer, pose.tick()).orElseThrow();
                    sampled.add(new TickPose(pose.tick(), point.x(), point.y(), point.z(), pointSampled(buffer, pose.tick()), point.fov()));
                }
                double export = splineError(buffer, filtered);
                double reference = splineError(buffer, sampled);
                if (!(export < reference + 0.5)) {
                    wrong.append(String.format(Locale.ROOT, "%n%s %.0f°/s mit %.0f %% bei %.0f Hz: Export %.2f° neben den Bildern, ohne Tiefpass %.2f°",
                            Arrays.toString(axis), rate, 100.0 * RIPPLE, RIPPLE_HZ, export, reference));
                }
            }
        }
        assertTrue(wrong.isEmpty(), wrong.toString());
    }

    private static CameraPathBuffer turning(double fps, double seconds, double rate, double ripple, double hz) {
        return turning(fps, seconds, rate, ripple, hz, 0.3, 0.8, -0.52);
    }

    private static CameraPathBuffer turning(double fps, double seconds, double rate, double ripple, double hz, double ax, double ay, double az) {
        CameraPathBuffer buffer = new CameraPathBuffer();
        for (int i = 0; i < seconds * fps; i++) {
            double time = i / fps;
            double angle = rate * time - (hz > 0.0 ? ripple * rate / (2.0 * Math.PI * hz) * Math.cos(2.0 * Math.PI * hz * time) : 0.0);
            Quaterniond q = new Quaterniond(BASE).rotateAxis(Math.toRadians(angle), ax, ay, az);
            assertTrue(buffer.add(new PathSample(START + time * 20.0, time, 70.0, 0.0, q.x, q.y, q.z, q.w, 70.0)));
        }
        return buffer;
    }

    private static Quaterniond pointSampled(CameraPathBuffer buffer, double tick) {
        PathSample point = PathResampler.at(buffer, tick).orElseThrow();
        return new Quaterniond(point.qx(), point.qy(), point.qz(), point.qw());
    }

    private static double splineError(CameraPathBuffer buffer, List<TickPose> poses) {
        CameraTrack track = CameraTrack.build(PathFrames.of(poses), Simplification.OFF, Integer.MAX_VALUE).track();
        double worst = 0.0;
        for (int i = 0; i < buffer.size(); i++) {
            double tick = buffer.tick(i);
            if (tick >= 10.0 && tick <= 70.0) {
                Quaterniond export = FlashbackSpline.at(track.camera(), (float) tick).angles().rotation();
                worst = Math.max(worst, CameraAngles.degreesBetween(pointSampled(buffer, tick), export));
            }
        }
        return worst;
    }

    @Test
    void positionAndFieldOfViewStayPointSampled() {
        Flight flight = hover(75.0, 4.0, 0.6, new Tone(17.3, 1.0, 2, 1.1));
        for (TickPose pose : PathResampler.perTick(flight.buffer)) {
            PathSample point = PathResampler.at(flight.buffer, pose.tick()).orElseThrow();
            assertEquals(point.x(), pose.x(), 1.0E-12);
            assertEquals(point.y(), pose.y(), 1.0E-12);
            assertEquals(point.fov(), pose.fov(), 1.0E-12);
        }
    }
}
