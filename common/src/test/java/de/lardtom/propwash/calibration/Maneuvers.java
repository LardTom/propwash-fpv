package de.lardtom.propwash.calibration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class Maneuvers {
    static final double DETECT_HZ = 20.0;
    static final double STEP_SPAN = 0.12;
    static final double PUNCH_JUMP = 0.22;
    static final double CUT_JUMP = -0.18;
    static final double PRE = 0.40;
    static final double PUNCH_POST = 0.60;
    static final double CUT_MAX = 2.0;
    static final double CUT_MIN = 0.22;
    static final double SETTLE = 0.10;
    static final double SKIP = 0.8;
    static final double HOVER_WINDOW = 0.8;
    static final double HOVER_STEP = 0.2;
    static final double HOVER_GYRO = 90.0;
    static final double HOVER_DUTY_STD = 0.02;
    static final double HOVER_FZ = 0.06;
    static final double HOVER_TILT = 15.0;
    static final double MIN_DUTY = 0.05;
    static final double MIN_HOVER_DUTY = 0.10;
    static final double PUNCH_GYRO = 400.0;
    static final double CUT_GYRO = 250.0;
    static final double LEVEL_GYRO = 150.0;
    static final double MAX_TILT = 35.0;
    static final double IMPACT_FZ = 8.0;
    static final double IMPACT_FXY = 2.5;
    static final double FLIGHT_EVIDENCE = 0.25;
    static final double FLIGHT_EVIDENCE_SPAN = 2.0;
    static final double GROUND_MARGIN = 1.0;
    static final double RESPONSE = 0.25;
    static final double IDLE_MARGIN = 0.04;
    static final double MIN_VBAT = 3.0;
    static final double HOVER_CLIMB = 0.5;
    static final double HOVER_GPS = 3.0;
    static final double EVENT_GPS = 4.0;
    static final double STATIC_CLIMB = 1.0;

    public record Hover(int from, int to, double duty, double vbat, double rpm, double fz, double throttle) {
        public double dutyAt1g() {
            return this.fz > 0.0 ? this.duty / Math.sqrt(this.fz) : this.duty;
        }
    }

    public enum Kind {
        PUNCH,
        CUT
    }

    public record Event(Kind kind, int from, int step, int to) {
    }

    public static final class Found {
        public final List<Hover> hovers = new ArrayList<>();
        public final List<Event> punches = new ArrayList<>();
        public final List<Event> cuts = new ArrayList<>();
        public final List<Event> levelCuts = new ArrayList<>();
        public final Map<String, Integer> rejected = new TreeMap<>();
        public double hoverDuty = Double.NaN;
        public double hoverVbat = Double.NaN;
        public double hoverRpm = Double.NaN;
        public double hoverThrottle = Double.NaN;
        public double hoverSeconds;

        void reject(String why) {
            this.rejected.merge(why, 1, Integer::sum);
        }
    }

    private Maneuvers() {
    }

    public static Found find(Flight f) {
        Found out = new Found();
        if (f.n == 0 || f.motors < Flight.MOTORS) {
            return out;
        }
        double[] smooth = Signals.pt1(f.duty, f.rate, DETECT_HZ);
        if (f.hasAcc) {
            hovers(f, smooth, out);
        }
        int span = f.index(STEP_SPAN);
        int i = span + f.index(PRE);
        while (i < f.n - f.index(PUNCH_POST)) {
            double jump = smooth[i] - smooth[i - span];
            if (Double.isNaN(jump) || (jump < PUNCH_JUMP && jump > CUT_JUMP)) {
                i++;
                continue;
            }
            boolean punch = jump >= PUNCH_JUMP;
            int c = i - span / 2;
            double pre = Signals.mean(f.duty, c - f.index(0.35), c - f.index(0.10));
            double post = Signals.mean(f.duty, c + f.index(0.10), c + f.index(0.35));
            int step = Signals.firstCrossing(smooth, c - f.index(0.15), c + f.index(0.15), 0.5 * (pre + post), punch);
            if (step < 0) {
                out.reject(punch ? "Punch: keine saubere Stufe" : "Gas weg: keine saubere Stufe");
                i += f.index(SKIP);
                continue;
            }
            String why = punch ? punch(f, smooth, step, pre, post, out) : cut(f, smooth, step, pre, post, out);
            if (why != null) {
                out.reject((punch ? "Punch: " : "Gas weg: ") + why);
            }
            i = step + f.index(SKIP);
        }
        return out;
    }

    static String airborne(Flight f, int step, double pre, Found out) {
        if (f.groundSpeed(step - f.index(0.3), step) > EVENT_GPS) {
            return "zu schnell (GPS über 4 m/s)";
        }
        if (!f.hasAcc) {
            return null;
        }
        double preFz = Signals.mean(f.fz, step - f.index(0.30), step - f.index(0.05));
        if (Double.isNaN(preFz) || preFz < 0.5 || preFz > 2.0) {
            return "nicht im Schwebe- oder Reiseflug davor";
        }
        double hover = Double.isNaN(out.hoverDuty) ? 0.15 : 0.6 * out.hoverDuty;
        if (pre < hover && preFz > 0.8) {
            return "Start vom Boden";
        }
        return null;
    }

    static String punch(Flight f, double[] smooth, int step, double pre, double post, Found out) {
        int from = step - f.index(PRE);
        int to = step + f.index(PUNCH_POST);
        if (post - pre < PUNCH_JUMP || post < 0.5 || pre < 0.04) {
            return "Stufe zu klein";
        }
        if (Signals.mean(f.duty, step + f.index(0.10), step + f.index(0.35)) < post - 0.1) {
            return "Vollgas zu kurz";
        }
        if (!Signals.allValid(f.valid, from, to)) {
            return "Lücke im Log";
        }
        if (Signals.max(f.gyro, step - f.index(0.3), step + f.index(0.5)) > PUNCH_GYRO) {
            return "Drehung während des Punchs";
        }
        if (f.hasTilt && f.tilt[step] > MAX_TILT) {
            return "zu schräg";
        }
        String air = airborne(f, step, pre, out);
        if (air != null) {
            return air;
        }
        if (f.hasAcc && (Signals.max(f.fz, from, to) > IMPACT_FZ || Signals.max(f.fxy, from, to) > IMPACT_FXY)) {
            return "Aufprall";
        }
        if (f.hasAcc && Signals.max(f.fz, step, step + f.index(0.4)) < Signals.mean(f.fz, step - f.index(0.15), step) + RESPONSE) {
            return "keine Reaktion der Beschleunigung (am Boden?)";
        }
        out.punches.add(new Event(Kind.PUNCH, from, step, to));
        return null;
    }

    static String cut(Flight f, double[] smooth, int step, double pre, double post, Found out) {
        int from = step - f.index(PRE);
        if (pre < 0.15 || post > pre + CUT_JUMP || post > 0.7 * pre) {
            return "Gas nicht weit genug zurück";
        }
        int max = Math.min(f.n, step + f.index(CUT_MAX));
        int end = Math.min(max, step + f.index(SETTLE));
        while (end < max && f.valid[end] && smooth[end] <= post + 0.12
                && !(f.hasAcc && end > step + f.index(0.05) && (f.fz[end] > 2.5 || f.fxy[end] > 2.0)) && !(f.gyro[end] > 400.0)) {
            end++;
        }
        if (end - step < f.index(CUT_MIN)) {
            return "zu kurz (unter " + Math.round(CUT_MIN * 1000.0) + " ms)";
        }
        if (!Signals.allValid(f.valid, from, end)) {
            return "Lücke im Log";
        }
        if (Signals.max(f.gyro, step, Math.min(end, step + f.index(0.6))) > CUT_GYRO) {
            return "Flip oder Rolle während des Falls";
        }
        if (f.hasTilt && f.tilt[step] > MAX_TILT) {
            return "zu schräg";
        }
        String air = airborne(f, step, pre, out);
        if (air != null) {
            return air;
        }
        if (f.hasAcc && Signals.min(f.fz, step, Math.min(end, step + f.index(0.35))) > Signals.mean(f.fz, step - f.index(0.15), step) - RESPONSE) {
            return "keine Reaktion der Beschleunigung (am Boden?)";
        }
        out.cuts.add(new Event(Kind.CUT, from, step, end));
        if (Signals.max(f.gyro, step, end) <= LEVEL_GYRO) {
            out.levelCuts.add(out.cuts.getLast());
        }
        return null;
    }

    static void hovers(Flight f, double[] smooth, Found out) {
        int w = f.index(HOVER_WINDOW);
        int s = Math.max(1, f.index(HOVER_STEP));
        int segFrom = -1;
        int segTo = -1;
        int[] evidence = new int[f.n + 1];
        for (int i = 0; i < f.n; i++) {
            evidence[i + 1] = evidence[i] + (Math.abs(f.fz[i] - 1.0) > FLIGHT_EVIDENCE ? 1 : 0);
        }
        int span = f.index(FLIGHT_EVIDENCE_SPAN);
        double groundLevel = f.hasBaro ? Signals.percentile(f.baro, 0, f.n, 0.01) : 0.0;
        List<int[]> segments = new ArrayList<>();
        for (int a = 0; a + w <= f.n; a += s) {
            int b = a + w;
            boolean ok = Signals.allValid(f.valid, a, b)
                    && Signals.max(f.gyro, a, b) <= HOVER_GYRO
                    && Signals.std(smooth, a, b) <= HOVER_DUTY_STD
                    && Math.abs(Signals.mean(f.fz, a, b) - 1.0) <= HOVER_FZ
                    && Signals.std(f.fz, a, b) <= HOVER_FZ
                    && Signals.mean(f.duty, a, b) >= Math.max(MIN_HOVER_DUTY, f.idleDuty + IDLE_MARGIN)
                    && Signals.mean(f.vbat, a, b) >= MIN_VBAT
                    && evidence[Math.min(f.n, b + span)] - evidence[Math.max(0, a - span)] > 0
                    && Signals.mean(f.fxy, a, b) <= 0.2
                    && (!f.hasTilt || Signals.max(f.tilt, a, b) <= HOVER_TILT)
                    && (!f.hasBaro || Math.abs(f.climbRate(a, b)) <= HOVER_CLIMB)
                    && !(f.groundSpeed(a, b) > HOVER_GPS)
                    && (!f.hasBaro || Signals.mean(f.baro, a, b) - groundLevel >= GROUND_MARGIN);
            if (ok) {
                if (segFrom >= 0 && a <= segTo) {
                    segTo = b;
                } else {
                    if (segFrom >= 0) {
                        segments.add(new int[]{segFrom, segTo});
                    }
                    segFrom = a;
                    segTo = b;
                }
            }
        }
        if (segFrom >= 0) {
            segments.add(new int[]{segFrom, segTo});
        }
        for (int[] seg : segments) {
            out.hovers.add(new Hover(seg[0], seg[1], Signals.mean(f.duty, seg[0], seg[1]), Signals.mean(f.vbat, seg[0], seg[1]),
                    Signals.mean(f.rpm, seg[0], seg[1]), Signals.mean(f.fz, seg[0], seg[1]), Signals.mean(f.throttle, seg[0], seg[1])));
        }
        if (out.hovers.isEmpty()) {
            return;
        }
        double[] duties = out.hovers.stream().mapToDouble(Hover::dutyAt1g).toArray();
        double median = Signals.median(duties);
        double weight = 0.0;
        double sd = 0.0;
        double sv = 0.0;
        double sr = 0.0;
        double st = 0.0;
        double wr = 0.0;
        double wt = 0.0;
        for (Hover h : out.hovers) {
            if (Math.abs(h.dutyAt1g() - median) > 0.15 * median) {
                out.reject("Schweben: Ausreißer (Boden oder Steigflug)");
                continue;
            }
            double len = (h.to() - h.from()) / f.rate;
            weight += len;
            sd += len * h.dutyAt1g();
            sv += len * h.vbat();
            if (!Double.isNaN(h.rpm())) {
                sr += len * h.rpm() / Math.sqrt(h.fz());
                wr += len;
            }
            if (!Double.isNaN(h.throttle())) {
                st += len * h.throttle();
                wt += len;
            }
        }
        if (weight > 0.0) {
            out.hoverDuty = sd / weight;
            out.hoverVbat = sv / weight;
            out.hoverRpm = wr > 0.0 ? sr / wr : Double.NaN;
            out.hoverThrottle = wt > 0.0 ? st / wt : Double.NaN;
            out.hoverSeconds = weight;
        }
    }

    public static double[][] steadySamples(Flight f, double strideSeconds) {
        double[] smooth = Signals.pt1(f.duty, f.rate, DETECT_HZ);
        int stride = Math.max(1, f.index(strideSeconds));
        int lag = f.index(0.05);
        List<double[]> rows = new ArrayList<>();
        for (int i = lag; i < f.n; i += stride) {
            if (!f.valid[i] || !f.valid[i - lag] || (f.hasAcc && Double.isNaN(f.fz[i])) || Double.isNaN(f.vbat[i])) {
                continue;
            }
            if (f.gyro[i] > 60.0 || Math.abs(smooth[i] - smooth[i - lag]) > 0.03 || f.duty[i] < Math.max(MIN_DUTY, f.idleDuty + IDLE_MARGIN)) {
                continue;
            }
            if (f.hasTilt && f.tilt[i] > 30.0) {
                continue;
            }
            if (f.hasAcc && (f.fz[i] <= 0.05 || f.fz[i] > IMPACT_FZ || f.fxy[i] > 0.5)) {
                continue;
            }
            double ground = f.groundSpeed(i - lag, i + lag);
            if (ground > EVENT_GPS) {
                continue;
            }
            rows.add(new double[]{smooth[i], f.vbat[i], f.fz[i], f.rpm[i], f.climbRate(i - f.index(0.25), i + f.index(0.25)),
                    f.hasTilt ? f.tilt[i] : Double.NaN, ground});
        }
        return rows.toArray(new double[0][]);
    }
}
