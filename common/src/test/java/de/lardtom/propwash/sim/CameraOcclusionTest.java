package de.lardtom.propwash.sim;

import de.lardtom.propwash.api.part.MotorArm;
import de.lardtom.propwash.api.part.Vec3Mm;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.video.FovMath;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraOcclusionTest {
    private static final double[][] DEADCAT_101_ARMS = {{-135.0, 0.0, -72.0}, {135.0, 0.0, -72.0}, {-105.0, 0.0, 112.0}, {105.0, 0.0, 112.0}};

    private static DroneBuild with(FrameType frame) {
        DroneBuild fs = DroneBuild.FREESTYLE;
        return new DroneBuild(frame, fs.stack(), fs.motor(), fs.prop(), fs.video(), fs.battery());
    }

    private static FpvRig rig(double[] lens, double tilt, double[][] arms, double seat, double diameter, double fov) {
        double[][] hubs = new double[FpvRig.SLOTS][];
        for (int i = 0; i < FpvRig.SLOTS; i++) {
            hubs[i] = new double[]{arms[i][0], arms[i][1] + seat, arms[i][2]};
        }
        return new FpvRig(lens, tilt, hubs, (diameter < 50 ? 3.0 : 5.0) * FpvRig.BLUR_LIFT_FACTOR, diameter / 2.0,
                Math.max(4.0, 0.09 * diameter) / 2.0, fov);
    }

    private static FpvRig deadcat101() {
        return rig(new double[]{0.0, 16.0, -78.0}, 22.0, DEADCAT_101_ARMS, 19.5, 178.0, FovMath.DEFAULT_DIGITAL_DEGREES);
    }

    static double bruteForce(FpvRig rig, double tiltDeg, double fovDeg, double aspect, int columns, int rows) {
        double tanH = Math.tan(Math.toRadians(fovDeg) / 2.0);
        double tanV = tanH / aspect;
        double tilt = Math.toRadians(tiltDeg);
        double cos = Math.cos(tilt);
        double sin = Math.sin(tilt);
        double[] lens = rig.lensMm();
        double outer2 = rig.radiusMm() * rig.radiusMm();
        double inner2 = rig.hubRadiusMm() * rig.hubRadiusMm();
        int hits = 0;
        for (int row = 0; row < rows; row++) {
            double b = (1.0 - 2.0 * (row + 0.5) / rows) * tanV;
            for (int col = 0; col < columns; col++) {
                double a = (2.0 * (col + 0.5) / columns - 1.0) * tanH;
                double dx = a;
                double dy = b * cos + sin;
                double dz = b * sin - cos;
                for (int i = 0; i < FpvRig.SLOTS; i++) {
                    double[] c = rig.discCenterMm(i);
                    if (Math.abs(dy) < 1.0E-12) {
                        continue;
                    }
                    double t = (c[1] - lens[1]) / dy;
                    if (t <= 0.0) {
                        continue;
                    }
                    double ex = lens[0] + t * dx - c[0];
                    double ez = lens[2] + t * dz - c[2];
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
    void rowIntegrationMatchesPerPixelRays() {
        List<FpvRig> rigs = List.of(deadcat101(), FpvRig.of(DroneBuild.FREESTYLE), FpvRig.of(DroneBuild.CINEWHOOP), FpvRig.of(DroneBuild.WHOOP),
                FpvRig.of(DroneBuild.LONG_RANGE), FpvRig.of(with(AddonParts.deadcat())));
        for (FpvRig rig : rigs) {
            for (double fov : new double[]{120.0, 130.0, 160.0}) {
                double exact = CameraOcclusion.percent(rig, rig.tiltDeg(), fov, FpvRig.ASPECT, 270);
                double rays = bruteForce(rig, rig.tiltDeg(), fov, FpvRig.ASPECT, 480, 270);
                assertEquals(rays, exact, 0.15, String.format(Locale.ROOT, "%s bei %.0f°", rig.tiltDeg(), fov));
            }
        }
    }

    @Test
    void propsVisibleInTheRenderedDeadcatAreReported() {
        FpvRig rig = deadcat101();
        double percent = CameraOcclusion.percent(rig, rig.tiltDeg(), rig.horizontalFovDeg(), FpvRig.ASPECT, CameraOcclusion.ROWS);
        assertTrue(percent > 1.5 && percent < 3.0, "Deadcat 7″ aus 1.0.1: " + percent);
        double[] center = rig.discCenterMm(1);
        assertEquals(22.25, center[1], 1.0E-9, "Propscheibe über dem Motor");
        assertTrue(center[1] > rig.lensMm()[1], "Props liegen über dem Objektiv");
    }

    @Test
    void aThinSliverAtTheLensHeightIsNotZero() {
        FpvRig rig = rig(new double[]{0.0, 22.0, -78.0}, 22.0, DEADCAT_101_ARMS, 19.5, 178.0, 130.0);
        double percent = CameraOcclusion.percent(rig, rig.tiltDeg(), rig.horizontalFovDeg(), FpvRig.ASPECT, CameraOcclusion.ROWS);
        assertTrue(percent > 0.0, "Props 0,25 mm über dem Objektiv: " + percent);
    }

    @Test
    void deadcatKeepsThePropsOutOfTheView() {
        DroneBuild deadcatBuild = with(AddonParts.deadcat());
        FrameType cameraBetweenTheProps = AddonParts.part(AddonParts.fiveInch("true_x_cam").wheelbase(230).arms(MotorArm.trueX(230, 0.0))
                .camera(new Vec3Mm(0.0, 14.0, -58.0)).cameraTilt(30.0).build());
        double dc = CameraOcclusion.percent(deadcatBuild);
        double tx = CameraOcclusion.percent(with(cameraBetweenTheProps));
        FpvRig rig = FpvRig.of(deadcatBuild);
        double wide = CameraOcclusion.percent(rig, rig.tiltDeg(), 145.0, FpvRig.ASPECT, CameraOcclusion.ROWS);
        assertEquals(0.0, dc, 1.0E-12, "Deadcat");
        assertEquals(0.0, wide, 1.0E-12, "Deadcat bei 145°");
        assertTrue(tx > 1.0, "True-X " + tx);
        assertEquals(dc, BuildAnalysis.of(deadcatBuild).propsInViewPercent(), 1.0E-12);
    }

    @Test
    void uptiltDependsOnWhetherTheLensSitsAboveOrBelowTheProps() {
        FpvRig whoop = FpvRig.of(DroneBuild.WHOOP);
        assertTrue(whoop.lensMm()[1] > whoop.discCenterMm(0)[1], "Whoop: Objektiv über den Props");
        double previous = Double.POSITIVE_INFINITY;
        for (double tilt : new double[]{0.0, 15.0, 30.0, 45.0}) {
            double percent = CameraOcclusion.percent(whoop, tilt, 120.0, FpvRig.ASPECT, 540);
            assertTrue(percent <= previous, "Whoop " + tilt + "°: " + percent + " nach " + previous);
            previous = percent;
        }
        FpvRig freestyle = FpvRig.of(DroneBuild.FREESTYLE);
        assertTrue(freestyle.lensMm()[1] < freestyle.discCenterMm(0)[1], "5″: Objektiv unter den Props");
        previous = 0.0;
        for (double tilt : new double[]{0.0, 15.0, 30.0, 45.0}) {
            double percent = CameraOcclusion.percent(freestyle, tilt, 130.0, FpvRig.ASPECT, 540);
            assertTrue(percent >= previous, "5″ " + tilt + "°: " + percent + " nach " + previous);
            previous = percent;
        }
        for (DroneBuild build : DroneBuild.PRESETS) {
            FpvRig rig = FpvRig.of(build);
            double narrow = CameraOcclusion.percent(rig, rig.tiltDeg(), 90.0, FpvRig.ASPECT, 540);
            double standard = CameraOcclusion.percent(rig, rig.tiltDeg(), 120.0, FpvRig.ASPECT, 540);
            assertTrue(standard > narrow, build.frame().key() + ": " + standard + " > " + narrow);
        }
    }

    @Test
    void frameShapeDecidesHowMuchPropShowsWithTheSameCameraMount() {
        double trueX = sameMount("same_true_x", 80.0, 80.0);
        double stretched = sameMount("same_stretched", 68.0, 92.0);
        double squashed = sameMount("same_squashed", 95.0, 65.0);
        assertTrue(stretched > trueX + 5.0, "Stretched " + stretched + " gegen True-X " + trueX);
        assertTrue(trueX > squashed + 3.0, "True-X " + trueX + " gegen Squashed " + squashed);
        assertTrue(squashed > 0.5, "Squashed " + squashed);
    }

    private static double sameMount(String name, double x, double z) {
        FrameType frame = AddonParts.part(AddonParts.fiveInch(name).arms(AddonParts.arms(x, z, x, z))
                .camera(new Vec3Mm(0.0, 17.0, -60.0)).cameraTilt(30.0).build());
        return CameraOcclusion.percent(with(frame));
    }

    @Test
    void cameraAmongThePropsGetsTheWarning() {
        FrameType frame = AddonParts.part(AddonParts.fiveInch("narrow_cam").arms(AddonParts.arms(60.0, 90.0, 60.0, 90.0))
                .camera(new Vec3Mm(0.0, 26.0, -60.0)).cameraTilt(0.0).build());
        double percent = CameraOcclusion.percent(with(frame));
        assertTrue(percent > BuildAnalysis.PROPS_IN_VIEW_WARNING, "Props im Bild " + percent);
        assertTrue(BuildAnalysis.of(with(frame)).warnings().contains(BuildWarning.PROPS_IN_VIEW));
    }

    @Test
    void builtinDronesStayBelowTheWarning() {
        for (DroneBuild build : BuiltinRegressionTest.buildableCombinations()) {
            BuildAnalysis a = BuildAnalysis.of(build);
            assertTrue(a.propsInViewPercent() >= 0.0 && a.propsInViewPercent() < BuildAnalysis.PROPS_IN_VIEW_WARNING,
                    build + " " + a.propsInViewPercent());
            assertFalse(a.warnings().contains(BuildWarning.PROPS_IN_VIEW));
        }
    }

    @Test
    void rigFollowsTheRenderedDrone() {
        FpvRig freestyle = FpvRig.of(DroneBuild.FREESTYLE);
        assertEquals(130.0, freestyle.horizontalFovDeg(), 0.0);
        assertEquals(28.0, freestyle.tiltDeg(), 0.0);
        double[] hub = freestyle.hubMm(0);
        assertEquals(-79.55, hub[0], 1.0E-3);
        assertEquals(-0.3 + 18.0, hub[1], 1.0E-3);
        assertEquals(2.75, freestyle.discLiftMm(), 1.0E-6);
        assertEquals(FovMath.DEFAULT_ANALOG_DEGREES, FovMath.defaultHorizontalDegrees(VideoSystem.ANALOG));
        assertEquals(FovMath.DEFAULT_DIGITAL_DEGREES, FovMath.defaultHorizontalDegrees(VideoSystem.DIGITAL));
    }

    @Test
    void builtinPresetsMatchTheRenderedFpvImage() {
        double[] rendered = {6.22, 1.93, 8.38, 10.96, 9.48};
        for (int i = 0; i < DroneBuild.PRESETS.size(); i++) {
            DroneBuild build = DroneBuild.PRESETS.get(i);
            FpvRig rig = FpvRig.of(build);
            double atWindow = CameraOcclusion.percent(rig, rig.tiltDeg(), rig.horizontalFovDeg(), 1708.0 / 960.0, 960);
            assertEquals(rendered[i], atWindow, 0.2, build.frame().key());
            assertEquals(atWindow, BuildAnalysis.of(build).propsInViewPercent(), 0.1, build.frame().key());
        }
    }
}
