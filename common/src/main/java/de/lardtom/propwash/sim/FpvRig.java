package de.lardtom.propwash.sim;

import de.lardtom.propwash.api.part.AirframeGeometry;
import de.lardtom.propwash.api.part.MotorArm;
import de.lardtom.propwash.api.part.Vec3Mm;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.drone.TuneMapping;
import de.lardtom.propwash.video.FovMath;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class FpvRig {
    public static final int SLOTS = FpvLayoutData.SLOTS;
    public static final double BLUR_LIFT_FACTOR = 0.55;
    public static final double ASPECT = 16.0 / 9.0;

    private static final Map<DroneBuild, FpvRig> CACHE = new ConcurrentHashMap<>();

    private final double[] lensMm;
    private final double tiltDeg;
    private final double[][] hubsMm;
    private final double discLiftMm;
    private final double radiusMm;
    private final double hubRadiusMm;
    private final double horizontalFovDeg;

    public FpvRig(double[] lensMm, double tiltDeg, double[][] hubsMm, double discLiftMm, double radiusMm, double hubRadiusMm,
                  double horizontalFovDeg) {
        if (lensMm.length != 3 || hubsMm.length != SLOTS) {
            throw new IllegalArgumentException("FPV-Aufbau braucht ein Objektiv und " + SLOTS + " Props");
        }
        this.lensMm = lensMm.clone();
        this.tiltDeg = tiltDeg;
        this.hubsMm = new double[SLOTS][];
        for (int i = 0; i < SLOTS; i++) {
            if (hubsMm[i].length != 3) {
                throw new IllegalArgumentException("Prop " + i + " ist kein Punkt [x, y, z]");
            }
            this.hubsMm[i] = hubsMm[i].clone();
        }
        this.discLiftMm = discLiftMm;
        this.radiusMm = radiusMm;
        this.hubRadiusMm = Math.min(hubRadiusMm, radiusMm);
        this.horizontalFovDeg = horizontalFovDeg;
    }

    public static FpvRig of(DroneBuild build) {
        return CACHE.computeIfAbsent(build, FpvRig::compute);
    }

    public static void clearCache() {
        CACHE.clear();
    }

    static FpvRig compute(DroneBuild build) {
        FrameType frame = build.frame();
        MotorType motor = build.motor();
        PropType prop = build.prop();
        MotorType typical = typicalMotor(frame);
        FpvLayoutData.View view = FpvLayoutData.view(List.of(frame.id().getNamespace(), motor.id().getNamespace(),
                typical.id().getNamespace()));
        double[][] motors;
        double layoutPropHeight;
        Optional<FpvLayoutData.Frame> entry = view.frame(frame.id());
        if (entry.isPresent() && entry.get().valid() && (entry.get().motors() != null || !frame.builtin())) {
            double[][] listed = entry.get().motors();
            motors = listed != null ? listed : definitionMotors(frame);
            layoutPropHeight = entry.get().propHeight();
        } else {
            motors = definitionMotors(frame);
            layoutPropHeight = frame.builtin() ? defaultSeatMm(typical) : Double.NaN;
        }
        double propHeight = propHeightMm(layoutPropHeight, view.seat(motor.id()), view.seat(typical.id()), Double.NaN, motor);
        double[][] hubs = new double[SLOTS][];
        for (int i = 0; i < SLOTS; i++) {
            hubs[i] = new double[]{motors[i][0], (float) (motors[i][1] + propHeight), motors[i][2]};
        }
        double tilt = TuneMapping.clampUptilt((float) frame.definition().geometry().cameraTiltDeg());
        return new FpvRig(lens(build), tilt, hubs, hubHeightMm(prop) * BLUR_LIFT_FACTOR, prop.diameterMm * 0.5,
                hubDiameterMm(prop) * 0.5, FovMath.cameraHorizontalDegrees(build.video()));
    }

    public static double propHeightMm(double layoutPropHeight, double seat, double presetSeat, double modelTop, MotorType motor) {
        if (Double.isFinite(layoutPropHeight)) {
            if (Double.isFinite(seat) && Double.isFinite(presetSeat)) {
                return (float) ((float) layoutPropHeight + (float) seat - (float) presetSeat);
            }
            return layoutPropHeight;
        }
        if (Double.isFinite(seat)) {
            return seat;
        }
        if (Double.isFinite(modelTop) && modelTop > 1.0 && modelTop < 60.0) {
            return modelTop + 0.5;
        }
        return defaultSeatMm(motor);
    }

    public static double[][] definitionMotors(FrameType frame) {
        AirframeGeometry geometry = frame.definition().geometry();
        double[][] motors = new double[SLOTS][];
        if (geometry.hasExplicitArms()) {
            for (int slot = 0; slot < SLOTS; slot++) {
                Vec3Mm position = geometry.arms().get(slot).position();
                motors[slot] = new double[]{(float) position.x(), (float) position.y(), (float) position.z()};
            }
            return motors;
        }
        float s = (float) (frame.wheelbaseMm / (2.0 * Math.sqrt(2.0)));
        float y = motorMountHeightMm(frame);
        motors[0] = new double[]{-s, y, -s};
        motors[1] = new double[]{s, y, -s};
        motors[2] = new double[]{-s, y, s};
        motors[3] = new double[]{s, y, s};
        return motors;
    }

    public static double[] lens(DroneBuild build) {
        Optional<Vec3Mm> camera = build.frame().definition().geometry().cameraMm();
        if (camera.isPresent()) {
            Vec3Mm lens = camera.get();
            return new double[]{(float) lens.x(), (float) lens.y(), (float) lens.z()};
        }
        AirframeSpec spec = AirframeSpec.of(build);
        float com = (float) (spec.comHeight * 1000.0);
        float foot = (float) -spec.hitboxFootOffset;
        if (!Float.isFinite(com) || !Float.isFinite(foot) || foot < 0.0F) {
            com = Math.max(4.0F, 0.06F * build.frame().wheelbaseMm);
        }
        return new double[]{(float) (spec.lensRight * 1000.0), com + (float) (spec.lensUp * 1000.0), (float) (-spec.lensForward * 1000.0)};
    }

    public static MotorType typicalMotor(FrameType frame) {
        for (DroneBuild preset : DroneBuild.PRESETS) {
            if (preset.frame() == frame) {
                return preset.motor();
            }
        }
        for (MotorType motor : MotorType.values()) {
            if (frame.motorMounts.contains(motor.mount)) {
                return motor;
            }
        }
        return MotorType.M2207_1750;
    }

    public static float motorMountHeightMm(FrameType frame) {
        if (frame.wheelbaseMm >= 200) {
            return 3.0F;
        }
        return frame.wheelbaseMm >= 100 ? 2.5F : 1.5F;
    }

    public static float defaultSeatMm(MotorType motor) {
        return (float) motor.definition().statorHeightMm() + 6.0F + 3.0F;
    }

    public static float hubHeightMm(PropType prop) {
        return prop.diameterMm < 50 ? 3.0F : 5.0F;
    }

    public static float hubDiameterMm(PropType prop) {
        return Math.max(4.0F, 0.09F * prop.diameterMm);
    }

    public double[] lensMm() {
        return this.lensMm.clone();
    }

    public double tiltDeg() {
        return this.tiltDeg;
    }

    public double[] hubMm(int slot) {
        return this.hubsMm[slot].clone();
    }

    public double[] discCenterMm(int slot) {
        double[] hub = this.hubsMm[slot];
        return new double[]{hub[0], hub[1] + this.discLiftMm, hub[2]};
    }

    public double discLiftMm() {
        return this.discLiftMm;
    }

    public double radiusMm() {
        return this.radiusMm;
    }

    public double hubRadiusMm() {
        return this.hubRadiusMm;
    }

    public double horizontalFovDeg() {
        return this.horizontalFovDeg;
    }
}
