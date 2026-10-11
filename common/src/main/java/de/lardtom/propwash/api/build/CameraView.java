package de.lardtom.propwash.api.build;

import de.lardtom.propwash.api.part.AirframeGeometry;
import de.lardtom.propwash.api.part.Vec3Mm;

import java.util.List;
import java.util.Objects;

public record CameraView(Vec3Mm lensMm, double uptiltDeg, double horizontalFovDeg, double aspect, List<PropDisc> props,
                         double propsInViewPercent, UptiltRange uptiltRange) {
    public CameraView(Vec3Mm lensMm, double uptiltDeg, double horizontalFovDeg, double aspect, List<PropDisc> props, double propsInViewPercent) {
        this(lensMm, uptiltDeg, horizontalFovDeg, aspect, props, propsInViewPercent, UptiltRange.unrestricted(uptiltDeg));
    }

    public CameraView {
        Objects.requireNonNull(lensMm, "lensMm");
        props = List.copyOf(Objects.requireNonNull(props, "props"));
        if (!(horizontalFovDeg > 0.0 && horizontalFovDeg < 180.0 && aspect > 0.0 && Double.isFinite(uptiltDeg))) {
            throw new IllegalArgumentException("Kamera ungültig: " + horizontalFovDeg + "°, Seitenverhältnis " + aspect + ", Uptilt " + uptiltDeg);
        }
        Objects.requireNonNull(uptiltRange, "uptiltRange");
    }

    public double verticalFovDeg() {
        return Math.toDegrees(2.0 * Math.atan(Math.tan(Math.toRadians(this.horizontalFovDeg) / 2.0) / this.aspect));
    }

    public double uptiltMinDeg() {
        return this.uptiltRange.minDeg();
    }

    public double uptiltMaxDeg() {
        return this.uptiltRange.maxDeg();
    }

    public double defaultUptiltDeg() {
        return this.uptiltRange.defaultDeg();
    }

    public record UptiltRange(double minDeg, double maxDeg, double defaultDeg) {
        public UptiltRange {
            if (!(minDeg >= AirframeGeometry.MIN_CAMERA_TILT && maxDeg <= AirframeGeometry.MAX_CAMERA_TILT && minDeg <= defaultDeg
                    && defaultDeg <= maxDeg)) {
                throw new IllegalArgumentException("Uptilt-Bereich ungültig: " + minDeg + "–" + maxDeg + "°, Standard " + defaultDeg + "°");
            }
        }

        public static UptiltRange unrestricted(double defaultDeg) {
            double clamped = Double.isFinite(defaultDeg)
                    ? Math.max(AirframeGeometry.MIN_CAMERA_TILT, Math.min(AirframeGeometry.MAX_CAMERA_TILT, defaultDeg))
                    : AirframeGeometry.DEFAULT_CAMERA_TILT;
            return new UptiltRange(AirframeGeometry.MIN_CAMERA_TILT, AirframeGeometry.MAX_CAMERA_TILT, clamped);
        }

        public double clamp(double degrees) {
            return Double.isFinite(degrees) ? Math.max(this.minDeg, Math.min(this.maxDeg, degrees)) : this.defaultDeg;
        }

        public boolean contains(double degrees) {
            return degrees >= this.minDeg && degrees <= this.maxDeg;
        }
    }

    public record PropDisc(Vec3Mm centerMm, double radiusMm, double hubRadiusMm) {
        public PropDisc {
            Objects.requireNonNull(centerMm, "centerMm");
            if (!(radiusMm > 0.0 && hubRadiusMm >= 0.0 && hubRadiusMm <= radiusMm)) {
                throw new IllegalArgumentException("Propscheibe ungültig: " + radiusMm + " / " + hubRadiusMm + " mm");
            }
        }
    }
}
