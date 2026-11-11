package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.video.record.path.CameraPathBuffer;
import de.lardtom.propwash.video.record.path.CameraTrack;
import de.lardtom.propwash.video.record.path.PathFrames;
import de.lardtom.propwash.video.record.path.PathResampler;
import de.lardtom.propwash.video.record.path.PathSample;
import de.lardtom.propwash.video.record.path.PathWobble;
import de.lardtom.propwash.video.record.path.Simplification;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FlashbackPropwashTest {
    private static final double SECONDS = 24.0;
    private static final double CYCLE = 3.0;
    private static final double UPTILT = 25.0;

    private record Flight(CameraPathBuffer buffer, double[] ticks, Quaterniond[] live, boolean[] wash, double rate) {
    }

    private static Flight dropsIntoOwnWash(DroneBuild build, double fps) {
        SimTuning tuning = new SimTuning();
        tuning.infiniteBattery = true;
        FlatWorld w = new FlatWorld(new DroneSim(build, new FlightControllerConfig(), tuning, 7L));
        w.place(0.0, 40.0, 0.0, 0.0);
        w.input.mode = StabMode.ANGLE;
        w.arm();
        double dt = 1.0 / fps;
        AltitudeHold hold = new AltitudeHold(40.0);
        for (int i = 0; i < (int) (3 * fps); i++) {
            hold.apply(w, dt);
            w.frame(dt);
        }
        int frames = (int) (SECONDS * fps);
        CameraPathBuffer buffer = new CameraPathBuffer();
        double[] ticks = new double[frames];
        Quaterniond[] live = new Quaterniond[frames];
        boolean[] wash = new boolean[frames];
        Random random = new Random(7L);
        double roll = 0.0;
        double pitch = 0.0;
        double target = w.y;
        for (int i = 0; i < frames; i++) {
            double time = i * dt;
            double phase = time % CYCLE;
            if (phase < 0.8) {
                w.input.throttle = 0.08;
                target = w.y;
            } else if (phase < 1.9) {
                w.input.throttle = Math.min(1.0, w.sim.telemetry().hoverThrottleStick * 1.05);
                target = w.y;
            } else {
                hold.target = target;
                hold.apply(w, dt);
            }
            if (i % (int) (fps / 2) == 0) {
                roll = (random.nextDouble() - 0.5) * 0.12;
                pitch = (random.nextDouble() - 0.5) * 0.12;
            }
            w.input.roll = roll;
            w.input.pitch = pitch;
            w.frame(dt);
            Quaterniond q = new Quaterniond(w.result.orientation).normalize().rotateY(Math.PI).rotateX(Math.toRadians(UPTILT));
            ticks[i] = 3.37 + time * 20.0;
            live[i] = q;
            wash[i] = w.sim.telemetry().propWash > 0.3 && time > 1.0 && time < SECONDS - 1.0;
            assertTrue(buffer.add(new PathSample(ticks[i], w.x, w.y, w.z, q.x, q.y, q.z, q.w, 70.0)));
        }
        return new Flight(buffer, ticks, live, wash, fps);
    }

    private static PathWobble.Result export(Flight flight, double slider) {
        PathFrames frames = PathFrames.of(PathResampler.perTick(flight.buffer));
        CameraTrack track = CameraTrack.build(frames, Simplification.ofSlider(slider), Integer.MAX_VALUE).track();
        return PathWobble.compare(flight.live, PathWobble.export(track, flight.ticks), flight.wash, flight.rate);
    }

    @Test
    void propwashInFlashbackLooksLikeLive() {
        for (DroneBuild build : new DroneBuild[]{DroneBuild.WHOOP, DroneBuild.FREESTYLE, DroneBuild.RACER}) {
            for (double fps : new double[]{60.0, 144.0}) {
                Flight flight = dropsIntoOwnWash(build, fps);
                PathWobble.Result every = export(flight, 0.0);
                String text = String.format(Locale.ROOT, "%s, %.0f fps, %d Bilder im Propwash: live %.3f° (über 12 Hz %.3f°), Export 0 %% %.3f° (x%.2f), "
                                + "Schein-Wackeln %.3f°, davon langsam %.3f°", build.frame().name(), fps, every.samples(), every.liveSway(), every.liveFast(),
                        every.exportSway(), every.ratio(), every.fakeSway(), every.fakeSlowSway());
                assertTrue(every.samples() > 5 * fps, text);
                assertTrue(every.fakeSlowSway() < 0.05, text);
                assertTrue(every.ratio() > 0.93 && every.ratio() < 1.03, text);
                PathWobble.Result reduced = export(flight, Simplification.DEFAULT_SLIDER);
                assertTrue(reduced.ratio() < 1.05, String.format(Locale.ROOT, "%s, %.0f fps, Vereinfachung Standard: x%.2f", build.frame().name(), fps,
                        reduced.ratio()));
            }
        }
    }
}
