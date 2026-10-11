package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.drone.TuneMapping;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.sim.MoveReport;
import de.lardtom.propwash.sim.PilotInput;
import de.lardtom.propwash.sim.SimEnvironment;
import de.lardtom.propwash.sim.SimTuning;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.StepResult;
import de.lardtom.propwash.video.FeedInput;
import de.lardtom.propwash.video.FeedLook;
import de.lardtom.propwash.video.FeedModel;
import de.lardtom.propwash.video.PoseHistory;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleValues;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraJitterTest {
    static final double SLICE_DISTANCE = 0.5;
    static final int MAX_SLICES = 4;
    static final double CRUISE_ALTITUDE = 300.0;
    static final double SPIN_UP_SECONDS = 14.0;
    static final double MEASURE_SECONDS = 6.0;
    static final double FRAME_JITTER = 0.15;
    static final int HITCH_EVERY = 97;
    static final double HITCH_FACTOR = 2.5;
    static final double MIN_SPEED_KMH = 100.0;
    static final double ANGLE_RMS_DEG = 0.01;
    static final double ANGLE_MAX_DEG = 0.03;
    static final double POSITION_RMS_MM = 0.5;
    static final double POSITION_MAX_MM = 2.0;

    record Jitter(double speedKmh, double positionRmsMm, double positionMaxMm, double angleRmsDeg, double angleMaxDeg) {
        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%.0f km/h, Position RMS %.3f mm / max %.3f mm, Winkel RMS %.4f° / max %.4f°",
                    this.speedKmh, this.positionRmsMm, this.positionMaxMm, this.angleRmsDeg, this.angleMaxDeg);
        }
    }

    static SimTuning realismOff() {
        RuleValues rules = RuleValues.DEFAULTS.with(WorldRuleSpecs.PROP_WASH, 0).with(WorldRuleSpecs.BATTERY_SAG, 0)
                .with(WorldRuleSpecs.WIND, 0).with(WorldRuleSpecs.TURBULENCE, 0).with(WorldRuleSpecs.BATTERY_SCALE, 0);
        return PhysicsRules.of(rules).toSimTuning();
    }

    static List<Arguments> cases() {
        List<Arguments> out = new ArrayList<>();
        for (DroneBuild build : new DroneBuild[]{DroneBuild.FREESTYLE, DroneBuild.RACER, DroneBuild.LONG_RANGE}) {
            for (StabMode mode : new StabMode[]{StabMode.ACRO, StabMode.ANGLE}) {
                for (VideoType video : new VideoType[]{VideoType.ANALOG_RACE, VideoType.DIGITAL_HD}) {
                    for (double fps : new double[]{144.0, 60.0}) {
                        out.add(Arguments.of(build, mode, video, fps));
                    }
                }
            }
        }
        return out;
    }

    @ParameterizedTest(name = "{0} {1} {2} {3} fps")
    @MethodSource("cases")
    void fastStraightFlightShowsNoCameraJitter(DroneBuild build, StabMode mode, VideoType video, double fps) {
        Jitter jitter = fly(build, realismOff(), mode, video, fps, 7L);
        String label = build.frame() + " " + mode + " " + video + " " + fps + " fps: " + jitter;
        assertTrue(jitter.speedKmh >= MIN_SPEED_KMH, "zu langsam für den Test: " + label);
        assertTrue(jitter.angleRmsDeg <= ANGLE_RMS_DEG && jitter.angleMaxDeg <= ANGLE_MAX_DEG, "Bild wackelt (Winkel): " + label);
        assertTrue(jitter.positionRmsMm <= POSITION_RMS_MM && jitter.positionMaxMm <= POSITION_MAX_MM, "Bild ruckelt (Position): " + label);
    }

    static Jitter fly(DroneBuild build, SimTuning tuning, StabMode measureMode, VideoType video, double fps, long seed) {
        AirframeSpec spec = AirframeSpec.of(build);
        FlightControllerConfig config = TuneMapping.toConfig(Map.of(), spec);
        DroneSim sim = new DroneSim(build, config, tuning, seed);
        PilotInput input = new PilotInput();
        SimEnvironment env = new SimEnvironment();
        StepResult result = new StepResult();
        MoveReport report = new MoveReport();
        PoseHistory poses = new PoseHistory(8192);
        FeedModel feed = new FeedModel(seed);
        FeedInput feedInput = new FeedInput();
        SplittableRandom random = new SplittableRandom(seed);
        double com = -spec.hitboxFootOffset;
        double lens = spec.motorOffset * 0.7;
        double[] entity = {0.0, CRUISE_ALTITUDE - com, 0.0};
        sim.reset(entity[0], entity[1] + com, entity[2], 0.0);
        input.mode = StabMode.ANGLE;
        sim.step(0L, input, env, result);
        input.armSwitch = true;
        sim.step(0L, input, env, result);
        double nominal = 1.0 / fps;
        int measureFrom = (int) Math.round(SPIN_UP_SECONDS * fps);
        int frames = measureFrom + (int) Math.round(MEASURE_SECONDS * fps);
        long[] times = new long[frames - measureFrom];
        double[][] positions = new double[times.length][];
        Quaterniond[] rotations = new Quaterniond[times.length];
        double tilt = 0.0;
        double elapsed = 0.0;
        double speedSum = 0.0;
        long clock = 1_000_000_000L;
        Vector3d cameraPosition = new Vector3d();
        Quaternionf cameraRotation = new Quaternionf();
        Vector3f lensOffset = new Vector3f();
        Quaternionf body = new Quaternionf();
        for (int f = 0; f < frames; f++) {
            double climb = sim.telemetry().verticalSpeed;
            double height = entity[1] + com - CRUISE_ALTITUDE;
            double error = climb - Math.max(-3.0, Math.min(3.0, -0.5 * height));
            tilt = Math.max(0.0, Math.min(80.0, tilt + 0.8 * error * nominal));
            boolean acroHold = measureMode == StabMode.ACRO && f >= measureFrom - (int) fps;
            input.mode = acroHold ? StabMode.ACRO : StabMode.ANGLE;
            input.pitch = acroHold ? 0.0 : Math.max(0.0, Math.min(80.0, tilt + 2.0 * error)) / config.pid.angleLimitDeg;
            input.throttle = Math.min(1.0, 0.3 + elapsed / 3.0);
            double dt = nominal * (1.0 + FRAME_JITTER * gauss(random));
            if (f % HITCH_EVERY == HITCH_EVERY - 1) {
                dt = nominal * HITCH_FACTOR;
            }
            long frameNanos = Math.round(Math.max(0.3 * nominal, Math.min(3.0 * nominal, dt)) * 1.0E9);
            clock += frameNanos;
            elapsed += frameNanos * 1.0E-9;
            int slices = Math.max(1, Math.min(MAX_SLICES, (int) Math.ceil(sim.telemetry().speed * frameNanos * 1.0E-9 / SLICE_DISTANCE)));
            long remaining = frameNanos;
            for (int i = 0; i < slices; i++) {
                long part = i == slices - 1 ? remaining : frameNanos / slices;
                remaining -= part;
                sim.step(part, input, env, result);
                entity[0] += result.dx;
                entity[1] += result.dy;
                entity[2] += result.dz;
                report.clear();
                report.requestedX = result.dx;
                report.requestedY = result.dy;
                report.requestedZ = result.dz;
                report.actualX = result.dx;
                report.actualY = result.dy;
                report.actualZ = result.dz;
                sim.afterMove(report);
            }
            Quaterniond q = result.orientation;
            body.set((float) q.x, (float) q.y, (float) q.z, (float) q.w).normalize().rotateY((float) Math.PI);
            poses.record(clock, entity[0], entity[1] + com, entity[2], body);
            float quality = f < measureFrom ? 1.0F : (float) (1.0 - 0.25 * (f - measureFrom) / (double) times.length);
            feedInput.set(video.system == VideoSystem.DIGITAL ? FeedLook.DIGITAL : FeedLook.ANALOG, true,
                    quality, video.latencyMs * 1_000_000L);
            feed.update(clock, feedInput);
            if (!poses.sample(feed.displayedNanos(clock), cameraPosition, cameraRotation)) {
                throw new IllegalStateException("leerer Posen-Puffer");
            }
            if (f >= measureFrom) {
                int k = f - measureFrom;
                lensOffset.set(0.0F, 0.015F, (float) -lens);
                cameraRotation.transform(lensOffset);
                times[k] = clock;
                positions[k] = new double[]{cameraPosition.x + lensOffset.x, cameraPosition.y + lensOffset.y, cameraPosition.z + lensOffset.z};
                rotations[k] = new Quaterniond(cameraRotation.x, cameraRotation.y, cameraRotation.z, cameraRotation.w);
                speedSum += sim.telemetry().speed;
            }
        }
        return measure(times, positions, rotations, speedSum / times.length * 3.6);
    }

    static Jitter measure(long[] times, double[][] positions, Quaterniond[] rotations, double speedKmh) {
        double positionSq = 0.0;
        double positionMax = 0.0;
        double angleSq = 0.0;
        double angleMax = 0.0;
        int count = 0;
        double[] t = new double[4];
        double[] y = new double[4];
        for (int k = 2; k < times.length - 2; k++) {
            int[] neighbours = {k - 2, k - 1, k + 1, k + 2};
            for (int j = 0; j < 4; j++) {
                t[j] = (times[neighbours[j]] - times[k]) * 1.0E-9;
            }
            double positionError = 0.0;
            for (int axis = 0; axis < 3; axis++) {
                for (int j = 0; j < 4; j++) {
                    y[j] = positions[neighbours[j]][axis] - positions[k][axis];
                }
                double e = quadraticAtZero(t, y);
                positionError += e * e;
            }
            double position = Math.sqrt(positionError) * 1000.0;
            Quaterniond inverse = new Quaterniond(rotations[k]).conjugate();
            double[][] vectors = new double[4][];
            for (int j = 0; j < 4; j++) {
                vectors[j] = rotationVectorDeg(new Quaterniond(inverse).mul(rotations[neighbours[j]]));
            }
            double angleError = 0.0;
            for (int axis = 0; axis < 3; axis++) {
                for (int j = 0; j < 4; j++) {
                    y[j] = vectors[j][axis];
                }
                double e = quadraticAtZero(t, y);
                angleError += e * e;
            }
            double angle = Math.sqrt(angleError);
            positionSq += position * position;
            positionMax = Math.max(positionMax, position);
            angleSq += angle * angle;
            angleMax = Math.max(angleMax, angle);
            count++;
        }
        return new Jitter(speedKmh, Math.sqrt(positionSq / count), positionMax, Math.sqrt(angleSq / count), angleMax);
    }

    static double quadraticAtZero(double[] t, double[] y) {
        double s0 = t.length;
        double s1 = 0.0;
        double s2 = 0.0;
        double s3 = 0.0;
        double s4 = 0.0;
        double y0 = 0.0;
        double y1 = 0.0;
        double y2 = 0.0;
        for (int i = 0; i < t.length; i++) {
            double ti = t[i];
            double t2 = ti * ti;
            s1 += ti;
            s2 += t2;
            s3 += t2 * ti;
            s4 += t2 * t2;
            y0 += y[i];
            y1 += ti * y[i];
            y2 += t2 * y[i];
        }
        double det = s0 * (s2 * s4 - s3 * s3) - s1 * (s1 * s4 - s3 * s2) + s2 * (s1 * s3 - s2 * s2);
        double detA = y0 * (s2 * s4 - s3 * s3) - s1 * (y1 * s4 - s3 * y2) + s2 * (y1 * s3 - s2 * y2);
        return detA / det;
    }

    static double[] rotationVectorDeg(Quaterniond q) {
        double sign = q.w < 0.0 ? -1.0 : 1.0;
        double x = q.x * sign;
        double y = q.y * sign;
        double z = q.z * sign;
        double v = Math.sqrt(x * x + y * y + z * z);
        if (v < 1.0E-15) {
            return new double[]{0.0, 0.0, 0.0};
        }
        double angle = Math.toDegrees(2.0 * Math.atan2(v, q.w * sign));
        return new double[]{x / v * angle, y / v * angle, z / v * angle};
    }

    static double gauss(SplittableRandom random) {
        return (random.nextDouble() + random.nextDouble() + random.nextDouble() + random.nextDouble() - 2.0) * Math.sqrt(3.0);
    }
}
