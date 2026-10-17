package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.video.FisheyeLens;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraOcclusionFisheyeTest {
    static double bruteForce(FpvRig rig, double tiltDeg, double fovDeg, int columns, int rows) {
        return bruteForce(rig, tiltDeg, fovDeg, columns, rows, FisheyeLens.DEFAULT_STRENGTH);
    }

    static double bruteForce(FpvRig rig, double tiltDeg, double fovDeg, int columns, int rows, double strength) {
        FisheyeLens lens = FisheyeLens.of(fovDeg, strength, columns, rows, columns, rows);
        double tilt = Math.toRadians(tiltDeg);
        double cos = Math.cos(tilt);
        double sin = Math.sin(tilt);
        double[] origin = rig.lensMm();
        double outer2 = rig.radiusMm() * rig.radiusMm();
        double inner2 = rig.hubRadiusMm() * rig.hubRadiusMm();
        double[] tangent = new double[2];
        int hits = 0;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                double px = col + 0.5 - columns * 0.5;
                double py = rows * 0.5 - (row + 0.5);
                if (!lens.covers(px, py) || !lens.tangentAt(px, py, tangent)) {
                    continue;
                }
                double a = tangent[0];
                double b = tangent[1];
                double dy = b * cos + sin;
                double dz = b * sin - cos;
                for (int i = 0; i < FpvRig.SLOTS; i++) {
                    double[] c = rig.discCenterMm(i);
                    if (Math.abs(dy) < 1.0E-12) {
                        continue;
                    }
                    double t = (c[1] - origin[1]) / dy;
                    if (t <= 0.0) {
                        continue;
                    }
                    double ex = origin[0] + t * a - c[0];
                    double ez = origin[2] + t * dz - c[2];
                    double q = ex * ex + ez * ez;
                    if (q <= outer2 && q >= inner2) {
                        hits++;
                        break;
                    }
                }
            }
        }
        return 100.0 * hits / ((double) rows * columns);
    }

    @Test
    void fisheyeShareMatchesPerPixelRays() {
        List<FpvRig> rigs = List.of(FpvRig.of(DroneBuild.FREESTYLE), FpvRig.of(DroneBuild.CINEWHOOP), FpvRig.of(DroneBuild.WHOOP),
                FpvRig.of(DroneBuild.RACER), FpvRig.of(DroneBuild.LONG_RANGE));
        for (FpvRig rig : rigs) {
            for (double fov : new double[]{104.0, 125.0, 130.0, 150.0}) {
                double fast = CameraOcclusion.fisheyePercent(rig, rig.tiltDeg(), fov, FpvRig.ASPECT, 180);
                double rays = bruteForce(rig, rig.tiltDeg(), fov, 320, 180);
                assertEquals(rays, fast, 1.0E-9, String.format(Locale.ROOT, "%s bei %.0f°", rig.tiltDeg(), fov));
            }
        }
    }

    @Test
    void shareFollowsTheLensStrength() {
        FpvRig rig = FpvRig.of(DroneBuild.RACER);
        for (double strength : new double[]{0.05, 0.4, 1.0}) {
            double fast = CameraOcclusion.fisheyePercent(rig, rig.tiltDeg(), 125.0, FpvRig.ASPECT, 180, strength);
            double rays = bruteForce(rig, rig.tiltDeg(), 125.0, 320, 180, strength);
            assertEquals(rays, fast, 1.0E-9, "Stärke " + strength);
        }
        double flat = CameraOcclusion.percent(rig, rig.tiltDeg(), 125.0, FpvRig.ASPECT, 540);
        double almostFlat = CameraOcclusion.fisheyePercent(rig, rig.tiltDeg(), 125.0, FpvRig.ASPECT, 540, 0.01);
        assertEquals(flat, almostFlat, Math.max(0.3, flat * 0.08));
    }

    @Test
    void aNarrowLensLooksAlmostFlat() {
        FpvRig rig = FpvRig.of(DroneBuild.FREESTYLE);
        for (double tilt : new double[]{-30.0, -10.0, 0.0}) {
            double flat = CameraOcclusion.percent(rig, tilt, 40.0, FpvRig.ASPECT, 540);
            double fisheye = CameraOcclusion.fisheyePercent(rig, tilt, 40.0, FpvRig.ASPECT, 540);
            assertEquals(flat, fisheye, Math.max(0.2, flat * 0.06), "Neigung " + tilt);
        }
    }

    @Test
    void fisheyeShowsThePropsInTheCornersOfAWideCamera() {
        FpvRig rig = FpvRig.of(DroneBuild.FREESTYLE);
        double flat = CameraOcclusion.percent(rig, rig.tiltDeg(), 130.0, FpvRig.ASPECT, CameraOcclusion.ROWS);
        double fisheye = CameraOcclusion.fisheyePercent(rig, rig.tiltDeg(), 130.0, FpvRig.ASPECT, CameraOcclusion.FISHEYE_ROWS);
        assertTrue(fisheye > 0.0 && flat > 0.0, "flach " + flat + ", Fisheye " + fisheye);
        double boxed = CameraOcclusion.fisheyePercent(rig, rig.tiltDeg(), 130.0, 4.0 / 3.0, CameraOcclusion.FISHEYE_ROWS);
        assertTrue(boxed >= 0.0 && boxed < 100.0);
    }
}
