package de.lardtom.propwash.api.part;

import java.util.List;
import java.util.Objects;

public record MotorArm(Vec3Mm position, Spin spin) {
    public static final int FRONT_LEFT = 0;
    public static final int FRONT_RIGHT = 1;
    public static final int REAR_LEFT = 2;
    public static final int REAR_RIGHT = 3;
    public static final int COUNT = 4;
    public static final List<Spin> PROPS_IN = List.of(Spin.CW, Spin.CCW, Spin.CCW, Spin.CW);

    public MotorArm {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(spin, "spin");
    }

    public static MotorArm of(double xMm, double yMm, double zMm, Spin spin) {
        return new MotorArm(new Vec3Mm(xMm, yMm, zMm), spin);
    }

    public static List<MotorArm> trueX(int wheelbaseMm, double yMm) {
        double s = wheelbaseMm / (2.0 * Math.sqrt(2.0));
        return List.of(of(-s, yMm, -s, PROPS_IN.get(FRONT_LEFT)), of(s, yMm, -s, PROPS_IN.get(FRONT_RIGHT)),
                of(-s, yMm, s, PROPS_IN.get(REAR_LEFT)), of(s, yMm, s, PROPS_IN.get(REAR_RIGHT)));
    }

    public enum Spin {
        CW("cw"),
        CCW("ccw");

        private final String key;

        Spin(String key) {
            this.key = key;
        }

        public String key() {
            return this.key;
        }
    }
}
