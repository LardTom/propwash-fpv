package de.lardtom.propwash.video.record.path;

import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathPreviewGapTest {
    private static final int CORNER = 60;
    private static final int TURN_TICKS = 2;
    private static final double FAST = 1.6;
    private static final double SLOW = 0.5;

    static List<TickPose> hairpin() {
        List<TickPose> poses = new ArrayList<>();
        double x = 0.0;
        double z = 0.0;
        double first = Math.toRadians(-65.0);
        double down = Math.toRadians(2.0);
        double back = Math.toRadians(-62.0);
        for (int t = 0; t < 200; t++) {
            double heading;
            double speed;
            if (t < CORNER) {
                heading = first;
                speed = FAST;
            } else if (t < CORNER + TURN_TICKS) {
                double a = (t - CORNER) / (double) TURN_TICKS;
                heading = first + (down - first) * a;
                speed = FAST + (SLOW - FAST) * a;
            } else if (t < 110) {
                heading = down;
                speed = SLOW;
            } else if (t < 120) {
                double a = (t - 110) / 10.0;
                heading = down + (back - down) * a;
                speed = SLOW + (FAST - SLOW) * a;
            } else {
                heading = back;
                speed = FAST;
            }
            x += -Math.sin(heading) * speed;
            z += Math.cos(heading) * speed;
            Quaterniond rotation = new CameraAngles(Math.toDegrees(heading), 20.0, 0.0).rotation();
            poses.add(new TickPose(1000 + t, x, 70.0, z, rotation, 100.0));
        }
        return poses;
    }

    @Test
    void markerSitsOnTheVisibleCornerCutNotOnATimingOffset() {
        PathFrames frames = PathFrames.of(hairpin());
        CameraTrack.Built built = CameraTrack.build(frames, Simplification.ofPosition(0.8), Integer.MAX_VALUE);
        PathPreview preview = PathPreview.of(frames, built, 1, 2.0F, Integer.MAX_VALUE);
        PathPreview.Gap gap = preview.topGap();
        assertTrue(gap.visible());
        assertTrue(Math.abs(gap.time() - CORNER) <= 16.0, "Ring bei Tick " + gap.time());
        int worst = preview.worstIndex();
        assertTrue(Math.abs(worst - CORNER) > 16, "gleiche Zeit am stärksten bei Tick " + worst);
        double[] along = alongAndAcross(frames, built.track().camera(), worst);
        assertTrue(Math.abs(along[0]) > 3.0 * Math.abs(along[1]), "längs " + along[0] + ", quer " + along[1]);
        double offAtOldMarker = flatDistanceToSaved(preview, worst);
        assertTrue(gap.distance() > 3.0 * offAtOldMarker, "Abstand am Ring " + gap.distance() + ", an der alten Stelle " + offAtOldMarker);
        assertTrue(gap.distance() <= preview.maxOffPath() + 1.0E-9);
        double fx = gap.bx() - gap.ax();
        double fz = gap.bz() - gap.az();
        assertEquals(gap.distance(), Math.hypot(fx, fz), 1.0E-9);
    }

    @Test
    void everyTickShowsNoGap() {
        PathFrames frames = PathFrames.of(hairpin());
        PathPreview preview = PathPreview.of(frames, Simplification.OFF, false, 2.0F);
        assertEquals(0.0, preview.topGap().distance(), 0.0);
        assertEquals(0.0, preview.sideGap().distance(), 0.0);
        assertEquals(0.0, preview.maxOffPath(), 0.0);
        assertTrue(!preview.topGap().visible());
    }

    @Test
    void sideGapIsTheHeightDifferenceAtTheSameTime() {
        List<TickPose> flat = hairpin();
        List<TickPose> poses = new ArrayList<>();
        for (TickPose pose : flat) {
            int t = pose.tick() - 1000;
            double y = t >= 140 && t < 150 ? 70.0 + 3.0 * Math.sin((t - 140) / 10.0 * Math.PI) : 70.0;
            poses.add(new TickPose(pose.tick(), pose.x(), y, pose.z(), pose.rotation(), pose.fov()));
        }
        PathFrames frames = PathFrames.of(poses);
        PathPreview preview = PathPreview.of(frames, Simplification.ofPosition(0.4), false, 2.0F);
        PathPreview.Gap side = preview.sideGap();
        assertTrue(side.visible());
        assertTrue(side.time() >= 135.0 && side.time() <= 155.0, "Höhe am stärksten bei Tick " + side.time());
        assertEquals(side.distance(), Math.abs(side.by() - side.ay()), 1.0E-12);
        assertEquals(side.ax(), interpolatedActual(preview, side.time())[0], 1.0E-9);
    }

    @Test
    void actualPathIsFlashbacksSplineThroughEveryTick() {
        PathFrames frames = PathFrames.of(hairpin());
        PathPreview preview = PathPreview.of(frames, Simplification.ofPosition(0.3), false, 2.0F);
        List<PathKeyframe> every = CameraTrack.build(frames, Simplification.OFF, Integer.MAX_VALUE).track().camera();
        double[] actual = preview.actual();
        for (int s = 0; s < actual.length / 3; s += 3) {
            FlashbackSpline.Pose at = FlashbackSpline.at(every, (float) (frames.firstTick() + s / (double) PathPreview.SUBSTEPS));
            assertEquals(at.x(), actual[s * 3], 1.0E-9);
            assertEquals(at.y(), actual[s * 3 + 1], 1.0E-9);
            assertEquals(at.z(), actual[s * 3 + 2], 1.0E-9);
        }
    }

    private static double[] interpolatedActual(PathPreview preview, double time) {
        int s = (int) Math.round(time * PathPreview.SUBSTEPS);
        return new double[]{preview.actual()[s * 3], preview.actual()[s * 3 + 1], preview.actual()[s * 3 + 2]};
    }

    private static double[] alongAndAcross(PathFrames frames, List<PathKeyframe> keys, int index) {
        TickPose truth = frames.pose(index);
        TickPose next = frames.pose(Math.min(frames.size() - 1, index + 1));
        TickPose previous = frames.pose(Math.max(0, index - 1));
        FlashbackSpline.Pose at = FlashbackSpline.at(keys, truth.tick());
        double tx = next.x() - previous.x();
        double tz = next.z() - previous.z();
        double length = Math.hypot(tx, tz);
        double ex = at.x() - truth.x();
        double ez = at.z() - truth.z();
        return new double[]{(ex * tx + ez * tz) / length, (ex * tz - ez * tx) / length};
    }

    private static double flatDistanceToSaved(PathPreview preview, int index) {
        double px = preview.truth()[index * 3];
        double pz = preview.truth()[index * 3 + 2];
        double[] saved = preview.saved();
        double best = Double.POSITIVE_INFINITY;
        for (int s = 0; s + 1 < saved.length / 3; s++) {
            double ax = saved[s * 3];
            double az = saved[s * 3 + 2];
            double vx = saved[s * 3 + 3] - ax;
            double vz = saved[s * 3 + 5] - az;
            double length = vx * vx + vz * vz;
            double t = length > 0.0 ? Math.max(0.0, Math.min(1.0, ((px - ax) * vx + (pz - az) * vz) / length)) : 0.0;
            best = Math.min(best, Math.hypot(ax + vx * t - px, az + vz * t - pz));
        }
        return best;
    }
}
