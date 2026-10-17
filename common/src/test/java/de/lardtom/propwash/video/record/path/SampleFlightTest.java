package de.lardtom.propwash.video.record.path;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.sim.MoveReport;
import de.lardtom.propwash.sim.PilotInput;
import de.lardtom.propwash.sim.SimEnvironment;
import de.lardtom.propwash.sim.SimSnapshot;
import de.lardtom.propwash.sim.SimTuning;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.StepResult;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SampleFlightTest {
    private static final int FPS = 120;
    private static final double SECONDS = 30.0;
    private static final double UPTILT = 30.0;
    private static final double FOV = 80.0;

    @Test
    void shippedSampleIsAFreestyleFlight() throws IOException {
        List<TickPose> poses = SamplePath.load();
        assertTrue(poses.size() >= 20 * 25 && poses.size() <= 20 * 40, "Ticks " + poses.size());
        double maxPitch = 0.0;
        double travelled = 0.0;
        double turned = 0.0;
        for (int i = 1; i < poses.size(); i++) {
            TickPose a = poses.get(i - 1);
            TickPose b = poses.get(i);
            assertEquals(a.tick() + 1, b.tick());
            travelled += Math.sqrt(Math.pow(b.x() - a.x(), 2) + Math.pow(b.y() - a.y(), 2) + Math.pow(b.z() - a.z(), 2));
            turned += CameraAngles.degreesBetween(a.rotation(), b.rotation());
            maxPitch = Math.max(maxPitch, Math.abs(CameraAngles.of(b.rotation()).pitch()));
        }
        assertTrue(travelled > 150.0, "Strecke " + travelled);
        assertTrue(turned > 1500.0, "Drehung " + turned);
        assertTrue(maxPitch > 80.0, "größte Neigung " + maxPitch);
        PathFrames frames = PathFrames.of(poses);
        PathPreview preview = PathPreview.of(frames, Simplification.ofSlider(Simplification.DEFAULT_SLIDER), true, 2.0F);
        assertTrue(preview.keyframes() < frames.size());
        assertTrue(preview.maxSameTime() <= 0.015 + 1.0E-9);
    }

    @Test
    void sampleRoundTrips() throws IOException {
        List<TickPose> poses = SyntheticFlight.poses(5.0);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SamplePath.write(out, poses);
        List<TickPose> back = SamplePath.read(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(poses.size(), back.size());
        for (int i = 0; i < poses.size(); i++) {
            assertEquals(poses.get(i).x(), back.get(i).x(), 1.0E-3);
            assertEquals(0.0, CameraAngles.degreesBetween(poses.get(i).rotation(), back.get(i).rotation()), 0.01);
        }
    }

    @Test
    void writeSampleWhenAsked() throws IOException {
        String target = System.getenv("PROPWASH_WRITE_SAMPLE_FLIGHT");
        if (target == null || target.isBlank()) {
            return;
        }
        List<TickPose> poses = fly();
        try (OutputStream out = Files.newOutputStream(Path.of(target))) {
            SamplePath.write(out, poses);
        }
    }

    static List<TickPose> fly() {
        DroneSim sim = new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), new SimTuning(), 4711L);
        sim.reset(120.0, 70.0, -40.0, 0.0);
        PilotInput input = new PilotInput();
        SimEnvironment env = new SimEnvironment();
        StepResult result = new StepResult();
        MoveReport report = new MoveReport();
        SimSnapshot snapshot = new SimSnapshot();
        AirframeSpec spec = sim.params();
        CameraPathBuffer buffer = new CameraPathBuffer();
        Quaterniond previous = null;
        double accumulated = 0.0;
        int trick = -1;
        long frameNanos = 1_000_000_000L / FPS;
        for (int frame = 0; frame <= SECONDS * FPS; frame++) {
            double t = frame / (double) FPS;
            sim.copyStateTo(snapshot);
            Quaterniond body = new Quaterniond(snapshot.qx, snapshot.qy, snapshot.qz, snapshot.qw).rotateY(Math.PI);
            if (previous != null) {
                accumulated += CameraAngles.degreesBetween(previous, body);
            }
            previous = new Quaterniond(body);
            int phase = phase(t);
            if (phase != trick) {
                trick = phase;
                accumulated = 0.0;
            }
            steer(input, t, phase, accumulated, snapshot);
            sim.step(frameNanos, input, env, result);
            report.clear();
            report.requestedX = result.dx;
            report.requestedY = result.dy;
            report.requestedZ = result.dz;
            report.actualX = result.dx;
            report.actualY = result.dy;
            report.actualZ = result.dz;
            sim.afterMove(report);
            sim.copyStateTo(snapshot);
            Quaterniond camera = new Quaterniond(snapshot.qx, snapshot.qy, snapshot.qz, snapshot.qw).rotateY(Math.PI);
            Vector3d lens = camera.transform(new Vector3d(spec.lensRight, spec.lensUp, -spec.lensForward));
            camera.rotateX(Math.toRadians(UPTILT));
            if (t >= 1.0) {
                buffer.add(new PathSample((t - 1.0) * 20.0, snapshot.px + lens.x, snapshot.py + lens.y, snapshot.pz + lens.z, camera.x, camera.y,
                        camera.z, camera.w, FOV));
            }
        }
        return PathResampler.perTick(buffer);
    }

    private static int phase(double t) {
        if (t < 0.1) {
            return 0;
        }
        if (t >= 6.0 && t < 7.4) {
            return 1;
        }
        if (t >= 11.0 && t < 13.5) {
            return 2;
        }
        if (t >= 17.0 && t < 18.0) {
            return 3;
        }
        if (t >= 21.0 && t < 23.0) {
            return 4;
        }
        return 5;
    }

    private static void steer(PilotInput input, double t, int phase, double accumulated, SimSnapshot s) {
        input.armSwitch = t > 0.02;
        input.yaw = 0.0;
        input.roll = 0.0;
        input.pitch = 0.0;
        switch (phase) {
            case 0 -> {
                input.mode = StabMode.ANGLE;
                input.throttle = 0.0;
            }
            case 1 -> {
                input.mode = StabMode.ACRO;
                boolean turning = accumulated < 320.0;
                input.pitch = turning ? 1.0 : 0.0;
                input.throttle = turning ? 0.12 : 0.75;
            }
            case 2 -> {
                input.mode = StabMode.ACRO;
                boolean turning = accumulated < 330.0;
                input.pitch = turning ? -0.42 : 0.0;
                input.throttle = turning ? 0.88 : 0.6;
            }
            case 3 -> {
                input.mode = StabMode.ACRO;
                boolean turning = accumulated < 330.0;
                input.roll = turning ? 1.0 : 0.0;
                input.throttle = 0.45;
            }
            case 4 -> {
                input.mode = StabMode.ACRO;
                if (accumulated < 170.0) {
                    input.roll = 1.0;
                    input.throttle = 0.3;
                } else if (accumulated < 340.0) {
                    input.pitch = -0.75;
                    input.throttle = 0.35;
                } else {
                    input.throttle = 0.7;
                }
            }
            default -> {
                input.mode = StabMode.ANGLE;
                double altitude = 70.0 + 6.0 * Math.sin(t * 0.4);
                input.throttle = Math.max(0.15, Math.min(0.95, 0.52 + 0.06 * (altitude - s.py) - 0.04 * s.vy));
                input.pitch = 0.8 + 0.12 * Math.sin(t * 0.9);
                input.roll = 0.4 * Math.sin(t * 0.45) + 0.15 * Math.sin(t * 1.7);
                input.yaw = 0.22 * Math.sin(t * 0.45);
            }
        }
    }
}
