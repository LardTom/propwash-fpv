package de.lardtom.propwash.blackbox;

import de.lardtom.propwash.sim.FcSample;

import static de.lardtom.propwash.blackbox.BlackboxSample.AMPERAGE;
import static de.lardtom.propwash.blackbox.BlackboxSample.AXIS_D;
import static de.lardtom.propwash.blackbox.BlackboxSample.AXIS_F;
import static de.lardtom.propwash.blackbox.BlackboxSample.AXIS_I;
import static de.lardtom.propwash.blackbox.BlackboxSample.AXIS_P;
import static de.lardtom.propwash.blackbox.BlackboxSample.BARO_ALT;
import static de.lardtom.propwash.blackbox.BlackboxSample.DEBUG;
import static de.lardtom.propwash.blackbox.BlackboxSample.ERPM;
import static de.lardtom.propwash.blackbox.BlackboxSample.FAILSAFE_PHASE;
import static de.lardtom.propwash.blackbox.BlackboxSample.GPS_ALT;
import static de.lardtom.propwash.blackbox.BlackboxSample.GPS_COURSE;
import static de.lardtom.propwash.blackbox.BlackboxSample.GPS_FIX;
import static de.lardtom.propwash.blackbox.BlackboxSample.GPS_LAT;
import static de.lardtom.propwash.blackbox.BlackboxSample.GPS_LON;
import static de.lardtom.propwash.blackbox.BlackboxSample.GPS_SATS;
import static de.lardtom.propwash.blackbox.BlackboxSample.GPS_SPEED;
import static de.lardtom.propwash.blackbox.BlackboxSample.GYRO;
import static de.lardtom.propwash.blackbox.BlackboxSample.GYRO_UNFILT;
import static de.lardtom.propwash.blackbox.BlackboxSample.ITERATION;
import static de.lardtom.propwash.blackbox.BlackboxSample.MODE_FLAGS;
import static de.lardtom.propwash.blackbox.BlackboxSample.MOTOR;
import static de.lardtom.propwash.blackbox.BlackboxSample.MOTOR_COUNT;
import static de.lardtom.propwash.blackbox.BlackboxSample.RC_COMMAND;
import static de.lardtom.propwash.blackbox.BlackboxSample.RSSI;
import static de.lardtom.propwash.blackbox.BlackboxSample.RX_SIGNAL;
import static de.lardtom.propwash.blackbox.BlackboxSample.RX_VALID;
import static de.lardtom.propwash.blackbox.BlackboxSample.SETPOINT;
import static de.lardtom.propwash.blackbox.BlackboxSample.STATE_FLAGS;
import static de.lardtom.propwash.blackbox.BlackboxSample.TIME;
import static de.lardtom.propwash.blackbox.BlackboxSample.VBAT;

public final class BlackboxSampler {
    public static final double METERS_PER_DEGREE = 111_319.49;
    static final int MAX_LAT_E7 = 899_000_000;
    static final int MAX_LON_E7 = 1_799_000_000;

    private BlackboxSampler() {
    }

    public static void fill(int[] out, int iteration, int timeUs, FcSample s, int rssi, int extraModes, boolean rescue) {
        out[ITERATION] = iteration;
        out[TIME] = timeUs;
        for (int a = 0; a < 3; a++) {
            out[AXIS_P + a] = round(s.pTerm[a]);
            out[AXIS_I + a] = round(s.iTerm[a]);
            out[AXIS_D + a] = round(s.dTerm[a]);
            out[AXIS_F + a] = round(s.fTerm[a]);
            out[RC_COMMAND + a] = clamp(round(s.stick[a] * 500.0), -500, 500);
            out[SETPOINT + a] = clamp(round(s.setpoint[a]), -16000, 16000);
            out[GYRO + a] = clamp(round(s.gyro[a]), -32768, 32767);
            out[GYRO_UNFILT + a] = clamp(round(s.gyroRaw[a]), -32768, 32767);
            out[DEBUG + a] = out[GYRO_UNFILT + a];
        }
        out[DEBUG + 3] = 0;
        out[RC_COMMAND + 3] = clamp(1000 + round(s.stick[3] * 1000.0), 1000, 2000);
        out[SETPOINT + 3] = clamp(round(s.mixerThrottle * 1000.0), -1000, 1000);
        out[VBAT] = clamp(round(s.voltage * 100.0), 0, 0xFFFF);
        out[AMPERAGE] = clamp(round(s.current * 100.0), -1_000_000, 1_000_000);
        out[BARO_ALT] = clamp(round(s.baroAltitude * 100.0), -100_000_000, 100_000_000);
        out[RSSI] = clamp(rssi, 0, 1023);
        for (int m = 0; m < MOTOR_COUNT; m++) {
            out[MOTOR + m] = motor(s.motorDuty[m]);
            out[ERPM + m] = erpm(s.motorRpm[m]);
        }
        out[MODE_FLAGS] = BlackboxModes.flags(s, extraModes);
        out[STATE_FLAGS] = s.gpsFix ? BlackboxEncoder.STATE_GPS_FIX | BlackboxEncoder.STATE_GPS_FIX_EVER : 0;
        out[FAILSAFE_PHASE] = BlackboxModes.failsafePhase(s, rescue);
        out[RX_SIGNAL] = s.linkOk ? 1 : 0;
        out[RX_VALID] = s.linkOk ? 1 : 0;
        out[GPS_FIX] = s.gpsFix ? 1 : 0;
        out[GPS_SATS] = clamp(s.gpsSatellites, 0, 255);
        out[GPS_LAT] = latitudeE7(s.gpsZ);
        out[GPS_LON] = longitudeE7(s.gpsX);
        out[GPS_ALT] = clamp(round(s.gpsY * 10.0), -1_000_000, 1_000_000);
        out[GPS_SPEED] = clamp(round(Math.hypot(s.gpsVx, s.gpsVz) * 100.0), 0, 1_000_000);
        out[GPS_COURSE] = course(s.gpsVx, s.gpsVz);
    }

    public static int motor(double duty) {
        double d = Double.isFinite(duty) ? Math.min(1.0, Math.abs(duty)) : 0.0;
        int span = BlackboxSample.DSHOT_MAX - BlackboxSample.DSHOT_MIN;
        return clamp(BlackboxSample.DSHOT_MIN + round(d * span), BlackboxSample.DSHOT_MIN, BlackboxSample.DSHOT_MAX);
    }

    public static int erpm(double rpm) {
        double value = Double.isFinite(rpm) ? Math.abs(rpm) * (BlackboxSample.MOTOR_POLES / 2.0) / 100.0 : 0.0;
        return clamp(round(value), 0, 0xFFFF);
    }

    public static int latitudeE7(double z) {
        return clamp(round(-z / METERS_PER_DEGREE * 1e7), -MAX_LAT_E7, MAX_LAT_E7);
    }

    public static int longitudeE7(double x) {
        return clamp(round(x / METERS_PER_DEGREE * 1e7), -MAX_LON_E7, MAX_LON_E7);
    }

    public static int course(double vx, double vz) {
        if (!(Math.hypot(vx, vz) > 1e-6)) {
            return 0;
        }
        double deg = Math.toDegrees(Math.atan2(vx, -vz));
        if (deg < 0.0) {
            deg += 360.0;
        }
        return Math.floorMod(round(deg * 10.0), 3600);
    }

    static int round(double v) {
        if (!Double.isFinite(v)) {
            return 0;
        }
        long r = Math.round(v);
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, r));
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
