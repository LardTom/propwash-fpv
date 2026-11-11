package de.lardtom.propwash.drone.operator;

import de.lardtom.propwash.api.drone.OperatorAccess;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public final class OperatorChannel {
    public static final int AXES = OperatorAccess.AXES;
    public static final int ACTIONS = OperatorAccess.ACTIONS;
    public static final int SEND_HZ = 20;
    public static final int MAX_PER_SECOND = 25;
    public static final long WINDOW_MILLIS = 1000L;
    public static final int SCALE = Short.MAX_VALUE;
    public static final int ACTION_MASK = (1 << ACTIONS) - 1;
    public static final int LEAVE = 0x80;
    public static final double RELEASE_FACTOR = 1.1;

    private OperatorChannel() {
    }

    public enum Denial {
        NONE,
        NOT_A_DRONE,
        PILOT,
        PILOTING_OTHER,
        OTHER_LEVEL,
        OUT_OF_RANGE,
        TAKEN
    }

    public static short quantize(double value) {
        if (!Double.isFinite(value)) {
            return 0;
        }
        double clamped = Math.max(-1.0, Math.min(1.0, value));
        return (short) Math.round(clamped * SCALE);
    }

    public static double axis(short raw) {
        return Math.max(-1.0, raw / (double) SCALE);
    }

    public static int actions(boolean... pressed) {
        int bits = 0;
        for (int i = 0; i < Math.min(ACTIONS, pressed.length); i++) {
            if (pressed[i]) {
                bits |= 1 << i;
            }
        }
        return bits;
    }

    public static boolean action(int bits, int number) {
        return number >= 1 && number <= ACTIONS && (bits & (1 << (number - 1))) != 0;
    }

    public static int sanitize(int bits) {
        return bits & ACTION_MASK;
    }

    public static boolean leaves(int bits) {
        return (bits & LEAVE) != 0;
    }

    public static Denial bind(boolean pilotOfDrone, boolean pilotsOther, boolean sameLevel, double distance, double range, @Nullable UUID current,
                              UUID candidate) {
        if (pilotOfDrone) {
            return Denial.PILOT;
        }
        if (pilotsOther) {
            return Denial.PILOTING_OTHER;
        }
        if (!sameLevel) {
            return Denial.OTHER_LEVEL;
        }
        if (!inRange(distance, range, 1.0)) {
            return Denial.OUT_OF_RANGE;
        }
        if (current != null && !current.equals(candidate)) {
            return Denial.TAKEN;
        }
        return Denial.NONE;
    }

    public static Denial keep(boolean pilotOfDrone, boolean pilotsOther, boolean sameLevel, double distance, double range) {
        if (pilotOfDrone) {
            return Denial.PILOT;
        }
        if (pilotsOther) {
            return Denial.PILOTING_OTHER;
        }
        if (!sameLevel) {
            return Denial.OTHER_LEVEL;
        }
        return inRange(distance, range, RELEASE_FACTOR) ? Denial.NONE : Denial.OUT_OF_RANGE;
    }

    public static boolean inRange(double distance, double range, double factor) {
        if (!Double.isFinite(distance) || !(range > 0.0)) {
            return false;
        }
        return Double.isInfinite(range) || distance <= range * factor;
    }

    public static boolean accepts(@Nullable UUID bound, @Nullable UUID sender) {
        return bound != null && bound.equals(sender);
    }

    public static final class Rate {
        private final long[] stamps = new long[MAX_PER_SECOND];
        private int head;
        private int count;
        private int refused;

        public boolean accept(long nowMillis) {
            while (this.count > 0) {
                long oldest = this.stamps[(this.head - this.count + MAX_PER_SECOND) % MAX_PER_SECOND];
                if (nowMillis - oldest >= WINDOW_MILLIS || nowMillis < oldest) {
                    this.count--;
                } else {
                    break;
                }
            }
            if (this.count >= MAX_PER_SECOND) {
                this.refused++;
                return false;
            }
            this.stamps[this.head] = nowMillis;
            this.head = (this.head + 1) % MAX_PER_SECOND;
            this.count++;
            return true;
        }

        public int refused() {
            return this.refused;
        }

        public void reset() {
            this.head = 0;
            this.count = 0;
            this.refused = 0;
        }
    }
}
