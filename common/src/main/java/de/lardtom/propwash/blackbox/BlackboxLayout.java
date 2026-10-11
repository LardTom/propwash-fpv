package de.lardtom.propwash.blackbox;

import java.util.ArrayList;
import java.util.List;

import static de.lardtom.propwash.blackbox.BlackboxField.NEG_14BIT;
import static de.lardtom.propwash.blackbox.BlackboxField.NULL;
import static de.lardtom.propwash.blackbox.BlackboxField.PREDICT_AVERAGE_2;
import static de.lardtom.propwash.blackbox.BlackboxField.PREDICT_HOME_COORD;
import static de.lardtom.propwash.blackbox.BlackboxField.PREDICT_INC;
import static de.lardtom.propwash.blackbox.BlackboxField.PREDICT_LAST_MAIN_FRAME_TIME;
import static de.lardtom.propwash.blackbox.BlackboxField.PREDICT_MINMOTOR;
import static de.lardtom.propwash.blackbox.BlackboxField.PREDICT_MOTOR_0;
import static de.lardtom.propwash.blackbox.BlackboxField.PREDICT_PREVIOUS;
import static de.lardtom.propwash.blackbox.BlackboxField.PREDICT_STRAIGHT_LINE;
import static de.lardtom.propwash.blackbox.BlackboxField.PREDICT_VBATREF;
import static de.lardtom.propwash.blackbox.BlackboxField.PREDICT_ZERO;
import static de.lardtom.propwash.blackbox.BlackboxField.SIGNED_VB;
import static de.lardtom.propwash.blackbox.BlackboxField.TAG2_3S32;
import static de.lardtom.propwash.blackbox.BlackboxField.TAG8_4S16;
import static de.lardtom.propwash.blackbox.BlackboxField.TAG8_8SVB;
import static de.lardtom.propwash.blackbox.BlackboxField.UNSIGNED_VB;

public final class BlackboxLayout {
    private BlackboxLayout() {
    }

    public static List<BlackboxField> main(BlackboxSetup setup) {
        List<BlackboxField> f = new ArrayList<>();
        f.add(new BlackboxField("loopIteration", false, PREDICT_ZERO, UNSIGNED_VB, PREDICT_INC, NULL));
        f.add(new BlackboxField("time", false, PREDICT_ZERO, UNSIGNED_VB, PREDICT_STRAIGHT_LINE, SIGNED_VB));
        for (int a = 0; a < 3; a++) {
            f.add(new BlackboxField("axisP[" + a + "]", true, PREDICT_ZERO, SIGNED_VB, PREDICT_PREVIOUS, SIGNED_VB));
        }
        for (int a = 0; a < 3; a++) {
            f.add(new BlackboxField("axisI[" + a + "]", true, PREDICT_ZERO, SIGNED_VB, PREDICT_PREVIOUS, TAG2_3S32));
        }
        for (int a = 0; a < 3; a++) {
            if (setup.dTerm(a)) {
                f.add(new BlackboxField("axisD[" + a + "]", true, PREDICT_ZERO, SIGNED_VB, PREDICT_PREVIOUS, SIGNED_VB));
            }
        }
        for (int a = 0; a < 3; a++) {
            f.add(new BlackboxField("axisF[" + a + "]", true, PREDICT_ZERO, SIGNED_VB, PREDICT_PREVIOUS, SIGNED_VB));
        }
        for (int a = 0; a < 3; a++) {
            f.add(new BlackboxField("rcCommand[" + a + "]", true, PREDICT_ZERO, SIGNED_VB, PREDICT_PREVIOUS, TAG8_4S16));
        }
        f.add(new BlackboxField("rcCommand[3]", false, PREDICT_ZERO, UNSIGNED_VB, PREDICT_PREVIOUS, TAG8_4S16));
        for (int a = 0; a < 4; a++) {
            f.add(new BlackboxField("setpoint[" + a + "]", true, PREDICT_ZERO, SIGNED_VB, PREDICT_PREVIOUS, TAG8_4S16));
        }
        f.add(new BlackboxField("vbatLatest", false, PREDICT_VBATREF, NEG_14BIT, PREDICT_PREVIOUS, TAG8_8SVB));
        f.add(new BlackboxField("amperageLatest", true, PREDICT_ZERO, SIGNED_VB, PREDICT_PREVIOUS, TAG8_8SVB));
        f.add(new BlackboxField("baroAlt", true, PREDICT_ZERO, SIGNED_VB, PREDICT_PREVIOUS, TAG8_8SVB));
        f.add(new BlackboxField("rssi", false, PREDICT_ZERO, UNSIGNED_VB, PREDICT_PREVIOUS, TAG8_8SVB));
        for (int a = 0; a < 3; a++) {
            f.add(new BlackboxField("gyroADC[" + a + "]", true, PREDICT_ZERO, SIGNED_VB, PREDICT_AVERAGE_2, SIGNED_VB));
        }
        for (int a = 0; a < 3; a++) {
            f.add(new BlackboxField("gyroUnfilt[" + a + "]", true, PREDICT_ZERO, SIGNED_VB, PREDICT_AVERAGE_2, SIGNED_VB));
        }
        for (int a = 0; a < BlackboxSample.DEBUG_COUNT; a++) {
            f.add(new BlackboxField("debug[" + a + "]", true, PREDICT_ZERO, SIGNED_VB, PREDICT_AVERAGE_2, SIGNED_VB));
        }
        f.add(new BlackboxField("motor[0]", false, PREDICT_MINMOTOR, UNSIGNED_VB, PREDICT_AVERAGE_2, SIGNED_VB));
        for (int m = 1; m < BlackboxSample.MOTOR_COUNT; m++) {
            f.add(new BlackboxField("motor[" + m + "]", false, PREDICT_MOTOR_0, SIGNED_VB, PREDICT_AVERAGE_2, SIGNED_VB));
        }
        for (int m = 0; m < BlackboxSample.MOTOR_COUNT; m++) {
            f.add(new BlackboxField("eRPM[" + m + "]", false, PREDICT_ZERO, UNSIGNED_VB, PREDICT_PREVIOUS, SIGNED_VB));
        }
        return f;
    }

    public static List<BlackboxField> gps() {
        return List.of(
                BlackboxField.simple("time", false, PREDICT_LAST_MAIN_FRAME_TIME, UNSIGNED_VB),
                BlackboxField.simple("GPS_numSat", false, PREDICT_ZERO, UNSIGNED_VB),
                BlackboxField.simple("GPS_coord[0]", true, PREDICT_HOME_COORD, SIGNED_VB),
                BlackboxField.simple("GPS_coord[1]", true, PREDICT_HOME_COORD, SIGNED_VB),
                BlackboxField.simple("GPS_altitude", true, PREDICT_ZERO, SIGNED_VB),
                BlackboxField.simple("GPS_speed", false, PREDICT_ZERO, UNSIGNED_VB),
                BlackboxField.simple("GPS_ground_course", false, PREDICT_ZERO, UNSIGNED_VB));
    }

    public static List<BlackboxField> home() {
        return List.of(
                BlackboxField.simple("GPS_home[0]", true, PREDICT_ZERO, SIGNED_VB),
                BlackboxField.simple("GPS_home[1]", true, PREDICT_ZERO, SIGNED_VB));
    }

    public static List<BlackboxField> slow() {
        return List.of(
                BlackboxField.simple("flightModeFlags", false, PREDICT_ZERO, UNSIGNED_VB),
                BlackboxField.simple("stateFlags", false, PREDICT_ZERO, UNSIGNED_VB),
                BlackboxField.simple("failsafePhase", false, PREDICT_ZERO, TAG2_3S32),
                BlackboxField.simple("rxSignalReceived", false, PREDICT_ZERO, TAG2_3S32),
                BlackboxField.simple("rxFlightChannelsValid", false, PREDICT_ZERO, TAG2_3S32));
    }
}
