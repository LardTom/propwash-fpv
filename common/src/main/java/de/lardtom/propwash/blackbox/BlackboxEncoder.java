package de.lardtom.propwash.blackbox;

import java.util.List;

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
import static de.lardtom.propwash.blackbox.BlackboxSample.WIDTH;

public final class BlackboxEncoder {
    public static final String PRODUCT = "Blackbox flight data recorder by Nicholas Sherlock";
    public static final int EVENT_SYNC_BEEP = 0;
    public static final int EVENT_LOGGING_RESUME = 14;
    public static final int EVENT_LOG_END = 255;
    public static final int STATE_GPS_FIX_HOME = 1;
    public static final int STATE_GPS_FIX = 1 << 1;
    public static final int STATE_GPS_FIX_EVER = 1 << 2;
    static final int HOME_REPEAT_I_FRAMES = 128;

    private final BlackboxBuffer out;
    private final int[][] history = new int[3][WIDTH];
    private final int[] group = new int[8];
    private final int[] slow = new int[5];
    private int current;
    private boolean started;
    private boolean ended;
    private int iInterval = 32;
    private int vbatReference;
    private final boolean[] dTerm = new boolean[3];
    private boolean slowWritten;
    private long lastSlowIteration;
    private boolean homeSet;
    private int homeLat;
    private int homeLon;
    private int intraFrames;
    private boolean gpsWritten;
    private int lastSats;
    private int lastLat;
    private int lastLon;
    private long frames;

    public BlackboxEncoder(BlackboxBuffer out) {
        this.out = out;
    }

    public void begin(BlackboxSetup setup) {
        BlackboxBuffer b = this.out;
        this.iInterval = setup.iInterval();
        this.vbatReference = setup.vbatReference();
        for (int a = 0; a < 3; a++) {
            this.dTerm[a] = setup.dTerm(a);
        }
        this.started = false;
        this.ended = false;
        this.slowWritten = false;
        this.homeSet = false;
        this.gpsWritten = false;
        this.intraFrames = 0;
        this.frames = 0;
        header(b, "Product", PRODUCT);
        header(b, "Data version", "2");
        List<BlackboxField> main = BlackboxLayout.main(setup);
        StringBuilder names = new StringBuilder();
        StringBuilder signed = new StringBuilder();
        StringBuilder iPredictor = new StringBuilder();
        StringBuilder iEncoding = new StringBuilder();
        StringBuilder pPredictor = new StringBuilder();
        StringBuilder pEncoding = new StringBuilder();
        for (BlackboxField field : main) {
            separate(names, signed, iPredictor, iEncoding, pPredictor, pEncoding);
            names.append(field.name());
            signed.append(field.signed() ? 1 : 0);
            iPredictor.append(field.iPredictor());
            iEncoding.append(field.iEncoding());
            pPredictor.append(field.pPredictor());
            pEncoding.append(field.pEncoding());
        }
        header(b, "Field I name", names.toString());
        header(b, "Field I signed", signed.toString());
        header(b, "Field I predictor", iPredictor.toString());
        header(b, "Field I encoding", iEncoding.toString());
        header(b, "Field P predictor", pPredictor.toString());
        header(b, "Field P encoding", pEncoding.toString());
        simpleFields(b, 'H', BlackboxLayout.home());
        simpleFields(b, 'G', BlackboxLayout.gps());
        simpleFields(b, 'S', BlackboxLayout.slow());
        for (String[] line : setup.lines()) {
            header(b, line[0], line[1]);
        }
    }

    public long frames() {
        return this.frames;
    }

    public boolean started() {
        return this.started;
    }

    public void frame(int[] data, int offset, boolean resume) {
        if (this.ended) {
            return;
        }
        int[] cur = this.history[this.current];
        System.arraycopy(data, offset, cur, 0, WIDTH);
        int iteration = cur[ITERATION];
        boolean intra = !this.started || resume || Integer.remainderUnsigned(iteration, this.iInterval) == 0;
        if (this.started && resume) {
            this.event(EVENT_LOGGING_RESUME);
            this.out.unsignedVB(iteration);
            this.out.unsignedVB(cur[TIME]);
        }
        this.slowIfNeeded(cur, iteration);
        if (intra) {
            this.intra(cur);
        } else {
            this.inter(cur);
        }
        if (!this.started) {
            this.started = true;
            this.event(EVENT_SYNC_BEEP);
            this.out.unsignedVB(cur[TIME]);
        }
        this.gps(cur, intra);
        this.frames++;
        if (intra) {
            int[] keep = this.history[this.current];
            this.rotate();
            System.arraycopy(keep, 0, this.history[this.previousIndex(2)], 0, WIDTH);
        } else {
            this.rotate();
        }
    }

    public void end() {
        if (this.ended) {
            return;
        }
        this.ended = true;
        this.event(EVENT_LOG_END);
        this.out.ascii("End of log");
        this.out.write(0);
    }

    private void rotate() {
        this.current = (this.current + 1) % 3;
    }

    private int previousIndex(int age) {
        return (this.current + 3 - age) % 3;
    }

    private void slowIfNeeded(int[] cur, int iteration) {
        int flags = cur[STATE_FLAGS] | (this.homeSet ? STATE_GPS_FIX_HOME : 0);
        boolean changed = !this.slowWritten || this.slow[0] != cur[MODE_FLAGS] || this.slow[1] != flags || this.slow[2] != cur[FAILSAFE_PHASE]
                || this.slow[3] != cur[RX_SIGNAL] || this.slow[4] != cur[RX_VALID];
        long interval = (long) this.iInterval * BlackboxSetup.SLOW_INTERVAL_I_FRAMES;
        if (!changed && (iteration & 0xFFFFFFFFL) - this.lastSlowIteration < interval) {
            return;
        }
        this.slow[0] = cur[MODE_FLAGS];
        this.slow[1] = flags;
        this.slow[2] = cur[FAILSAFE_PHASE];
        this.slow[3] = cur[RX_SIGNAL];
        this.slow[4] = cur[RX_VALID];
        this.slowWritten = true;
        this.lastSlowIteration = iteration & 0xFFFFFFFFL;
        BlackboxBuffer b = this.out;
        b.write('S');
        b.unsignedVB(this.slow[0]);
        b.unsignedVB(this.slow[1]);
        b.tag2x3s32(this.slow[2], this.slow[3], this.slow[4]);
    }

    private void intra(int[] c) {
        BlackboxBuffer b = this.out;
        b.write('I');
        b.unsignedVB(c[ITERATION]);
        b.unsignedVB(c[TIME]);
        for (int a = 0; a < 3; a++) {
            b.signedVB(c[AXIS_P + a]);
        }
        for (int a = 0; a < 3; a++) {
            b.signedVB(c[AXIS_I + a]);
        }
        for (int a = 0; a < 3; a++) {
            if (this.dTerm[a]) {
                b.signedVB(c[AXIS_D + a]);
            }
        }
        for (int a = 0; a < 3; a++) {
            b.signedVB(c[AXIS_F + a]);
        }
        for (int a = 0; a < 3; a++) {
            b.signedVB(c[RC_COMMAND + a]);
        }
        b.unsignedVB(c[RC_COMMAND + 3]);
        for (int a = 0; a < 4; a++) {
            b.signedVB(c[SETPOINT + a]);
        }
        b.neg14Bit(c[VBAT] - this.vbatReference);
        b.signedVB(c[AMPERAGE]);
        b.signedVB(c[BARO_ALT]);
        b.unsignedVB(c[RSSI]);
        for (int a = 0; a < 3; a++) {
            b.signedVB(c[GYRO + a]);
        }
        for (int a = 0; a < 3; a++) {
            b.signedVB(c[GYRO_UNFILT + a]);
        }
        for (int a = 0; a < BlackboxSample.DEBUG_COUNT; a++) {
            b.signedVB(c[DEBUG + a]);
        }
        b.unsignedVB(c[MOTOR] - BlackboxSample.DSHOT_MIN);
        for (int m = 1; m < MOTOR_COUNT; m++) {
            b.signedVB(c[MOTOR + m] - c[MOTOR]);
        }
        for (int m = 0; m < MOTOR_COUNT; m++) {
            b.unsignedVB(c[ERPM + m]);
        }
        this.intraFrames++;
    }

    private void inter(int[] c) {
        BlackboxBuffer b = this.out;
        int[] p1 = this.history[this.previousIndex(1)];
        int[] p2 = this.history[this.previousIndex(2)];
        b.write('P');
        b.signedVB(c[TIME] - 2 * p1[TIME] + p2[TIME]);
        for (int a = 0; a < 3; a++) {
            b.signedVB(c[AXIS_P + a] - p1[AXIS_P + a]);
        }
        b.tag2x3s32(c[AXIS_I] - p1[AXIS_I], c[AXIS_I + 1] - p1[AXIS_I + 1], c[AXIS_I + 2] - p1[AXIS_I + 2]);
        for (int a = 0; a < 3; a++) {
            if (this.dTerm[a]) {
                b.signedVB(c[AXIS_D + a] - p1[AXIS_D + a]);
            }
        }
        for (int a = 0; a < 3; a++) {
            b.signedVB(c[AXIS_F + a] - p1[AXIS_F + a]);
        }
        b.tag8x4s16(c[RC_COMMAND] - p1[RC_COMMAND], c[RC_COMMAND + 1] - p1[RC_COMMAND + 1], c[RC_COMMAND + 2] - p1[RC_COMMAND + 2],
                c[RC_COMMAND + 3] - p1[RC_COMMAND + 3]);
        b.tag8x4s16(c[SETPOINT] - p1[SETPOINT], c[SETPOINT + 1] - p1[SETPOINT + 1], c[SETPOINT + 2] - p1[SETPOINT + 2],
                c[SETPOINT + 3] - p1[SETPOINT + 3]);
        int[] g = this.group;
        g[0] = c[VBAT] - p1[VBAT];
        g[1] = c[AMPERAGE] - p1[AMPERAGE];
        g[2] = c[BARO_ALT] - p1[BARO_ALT];
        g[3] = c[RSSI] - p1[RSSI];
        b.tag8x8svb(g, 4);
        average(b, c, p1, p2, GYRO, 3);
        average(b, c, p1, p2, GYRO_UNFILT, 3);
        average(b, c, p1, p2, DEBUG, BlackboxSample.DEBUG_COUNT);
        average(b, c, p1, p2, MOTOR, MOTOR_COUNT);
        for (int m = 0; m < MOTOR_COUNT; m++) {
            b.signedVB(c[ERPM + m] - p1[ERPM + m]);
        }
    }

    private void gps(int[] c, boolean intra) {
        if (c[GPS_FIX] == 0) {
            return;
        }
        BlackboxBuffer b = this.out;
        boolean writeHome = false;
        if (!this.homeSet) {
            this.homeSet = true;
            this.homeLat = c[GPS_LAT];
            this.homeLon = c[GPS_LON];
            writeHome = true;
        } else if (intra && this.intraFrames % HOME_REPEAT_I_FRAMES == 0) {
            writeHome = true;
        }
        if (writeHome) {
            b.write('H');
            b.signedVB(this.homeLat);
            b.signedVB(this.homeLon);
        }
        if (writeHome || !this.gpsWritten || this.lastSats != c[GPS_SATS] || this.lastLat != c[GPS_LAT] || this.lastLon != c[GPS_LON]) {
            b.write('G');
            b.unsignedVB(0);
            b.unsignedVB(c[GPS_SATS]);
            b.signedVB(c[GPS_LAT] - this.homeLat);
            b.signedVB(c[GPS_LON] - this.homeLon);
            b.signedVB(c[GPS_ALT]);
            b.unsignedVB(c[GPS_SPEED]);
            b.unsignedVB(c[GPS_COURSE]);
            this.gpsWritten = true;
            this.lastSats = c[GPS_SATS];
            this.lastLat = c[GPS_LAT];
            this.lastLon = c[GPS_LON];
        }
    }

    public boolean homeSet() {
        return this.homeSet;
    }

    private void event(int type) {
        this.out.write('E');
        this.out.write(type);
    }

    private static void average(BlackboxBuffer b, int[] c, int[] p1, int[] p2, int start, int count) {
        for (int i = start; i < start + count; i++) {
            b.signedVB(c[i] - (int) (((long) p1[i] + p2[i]) / 2));
        }
    }

    private static void separate(StringBuilder... builders) {
        for (StringBuilder sb : builders) {
            if (!sb.isEmpty()) {
                sb.append(',');
            }
        }
    }

    private static void simpleFields(BlackboxBuffer b, char type, List<BlackboxField> fields) {
        StringBuilder names = new StringBuilder();
        StringBuilder signed = new StringBuilder();
        StringBuilder predictor = new StringBuilder();
        StringBuilder encoding = new StringBuilder();
        for (BlackboxField field : fields) {
            separate(names, signed, predictor, encoding);
            names.append(field.name());
            signed.append(field.signed() ? 1 : 0);
            predictor.append(field.iPredictor());
            encoding.append(field.iEncoding());
        }
        header(b, "Field " + type + " name", names.toString());
        header(b, "Field " + type + " signed", signed.toString());
        header(b, "Field " + type + " predictor", predictor.toString());
        header(b, "Field " + type + " encoding", encoding.toString());
    }

    private static void header(BlackboxBuffer b, String name, String value) {
        b.ascii("H " + name + ":" + value + "\n");
    }
}
