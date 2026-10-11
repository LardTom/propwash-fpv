package de.lardtom.propwash.api.part;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record AirframeGeometry(List<MotorArm> arms, boolean batteryOnTop, double batteryPlateMm, double electronicsMm,
                               Optional<Vec3Mm> cameraMm, double cameraTiltDeg, Vec3Mm frameCenterOfMassMm, MassSplit massSplit,
                               Map<AccessorySlot, AnchorSpec> accessoryAnchors) {
    public static final double MIN_CAMERA_TILT = 0.0;
    public static final double MAX_CAMERA_TILT = 80.0;
    public static final double DEFAULT_CAMERA_TILT = 20.0;

    public AirframeGeometry {
        arms = List.copyOf(Objects.requireNonNullElse(arms, List.of()));
        if (!arms.isEmpty() && arms.size() != MotorArm.COUNT) {
            throw new IllegalArgumentException("Ein Rahmen hat genau " + MotorArm.COUNT + " Arme (vorne links, vorne rechts, hinten links, hinten rechts): "
                    + arms.size());
        }
        if (!Double.isFinite(batteryPlateMm) || !Double.isFinite(electronicsMm)) {
            throw new IllegalArgumentException("Akkuplatte und Elektronikhöhe müssen endlich sein");
        }
        cameraMm = Objects.requireNonNullElse(cameraMm, Optional.empty());
        if (!(cameraTiltDeg >= MIN_CAMERA_TILT && cameraTiltDeg <= MAX_CAMERA_TILT)) {
            throw new IllegalArgumentException("Kamerawinkel muss zwischen " + MIN_CAMERA_TILT + " und " + MAX_CAMERA_TILT + " Grad liegen: "
                    + cameraTiltDeg);
        }
        frameCenterOfMassMm = Objects.requireNonNullElse(frameCenterOfMassMm, Vec3Mm.ZERO);
        Objects.requireNonNull(massSplit, "massSplit");
        EnumMap<AccessorySlot, AnchorSpec> anchors = new EnumMap<>(AccessorySlot.class);
        anchors.putAll(Objects.requireNonNullElse(accessoryAnchors, Map.of()));
        accessoryAnchors = Collections.unmodifiableMap(anchors);
    }

    public boolean hasExplicitArms() {
        return !this.arms.isEmpty();
    }

    public List<MotorArm> armsOrTrueX(int wheelbaseMm) {
        return this.arms.isEmpty() ? MotorArm.trueX(wheelbaseMm, 0.0) : this.arms;
    }

    public static boolean defaultBatteryOnTop(int wheelbaseMm) {
        return wheelbaseMm >= 100;
    }

    public static double defaultBatteryPlateMm(int wheelbaseMm) {
        return defaultBatteryOnTop(wheelbaseMm) ? 0.13 * wheelbaseMm : -0.05 * wheelbaseMm;
    }

    public static double defaultElectronicsMm(int wheelbaseMm) {
        return 0.05 * wheelbaseMm;
    }
}
