package de.lardtom.propwash.calibration;

import java.util.Arrays;

public final class Flight {
    public static final double MAX_RATE = 1000.0;
    static final int MAX_FILL = 3;
    static final int MOTORS = 4;
    static final double BARO_MIN_STD = 0.05;
    static final double GPS_MIN_SATS = 6;

    public final double rate;
    public final int n;
    public final double startSeconds;
    public final int motors;
    public final boolean hasAcc;
    public final boolean hasRpm;
    public final boolean hasTilt;
    public final boolean hasVbat;
    public final double[] duty;
    public final double[] fz;
    public final double[] fxy;
    public final double[] gyro;
    public final double[] tilt;
    public final double[] vbat;
    public final double[] rpm;
    public final double[] throttle;
    public final double[] stickRoll;
    public final double[] setpointRoll;
    public final double[] baro;
    public final double[] gpsSpeed;
    public final double[] selfLevel;
    public boolean hasGps;
    public boolean hasBaro;
    public final boolean[] valid;
    public final double accLpfHz;
    public double idleDuty;

    private Flight(double rate, int n, double startSeconds, int motors, boolean hasAcc, boolean hasRpm, boolean hasTilt, boolean hasVbat,
                   double accLpfHz) {
        this.rate = rate;
        this.n = n;
        this.startSeconds = startSeconds;
        this.motors = motors;
        this.hasAcc = hasAcc;
        this.hasRpm = hasRpm;
        this.hasTilt = hasTilt;
        this.hasVbat = hasVbat;
        this.accLpfHz = accLpfHz;
        this.duty = new double[n];
        this.fz = new double[n];
        this.fxy = new double[n];
        this.gyro = new double[n];
        this.tilt = new double[n];
        this.vbat = new double[n];
        this.rpm = new double[n];
        this.throttle = new double[n];
        this.stickRoll = new double[n];
        this.setpointRoll = new double[n];
        this.baro = new double[n];
        this.gpsSpeed = new double[n];
        this.selfLevel = new double[n];
        this.valid = new boolean[n];
    }

    public double seconds(int index) {
        return index / this.rate;
    }

    public int index(double seconds) {
        return (int) Math.round(seconds * this.rate);
    }

    public double durationSeconds() {
        return this.n / this.rate;
    }

    static int motorCount(LogData d) {
        int m = 0;
        while (d.has("motor[" + m + "]")) {
            m++;
        }
        return m;
    }

    static double sourceRate(double[] time) {
        int count = Math.min(time.length - 1, 20000);
        if (count < 10) {
            return Double.NaN;
        }
        double[] dt = new double[count];
        for (int i = 0; i < count; i++) {
            dt[i] = time[i + 1] - time[i];
        }
        Arrays.sort(dt);
        double median = dt[count / 2];
        return median > 0.0 ? 1e6 / median : Double.NaN;
    }

    public static Flight of(LogHeader h, LogData d) {
        double[] time = d.has("time") ? d.column("time") : new double[0];
        int motors = motorCount(d);
        double src = sourceRate(time);
        double rate = Double.isNaN(src) ? MAX_RATE : Math.min(MAX_RATE, Math.round(src));
        double width = 1e6 / rate;
        double t0 = time.length > 0 ? time[0] : 0.0;
        int bins = time.length > 0 ? (int) Math.max(0, Math.floor((time[time.length - 1] - t0) / width) + 1) : 0;
        boolean acc = d.has("accSmooth[2]") && !Double.isNaN(h.acc1G());
        boolean rpm = motors >= MOTORS && d.has("eRPM[0]") && d.has("eRPM[" + (MOTORS - 1) + "]");
        double[] tiltRow = Attitude.tiltDegrees(h, d);
        boolean tilt = tiltRow != null;
        boolean vbat = d.has("vbatLatest");
        Flight f = new Flight(rate, bins, t0 * 1e-6, motors, acc, rpm, tilt, vbat, h.accLpfHz());
        f.idleDuty = h.idlePercent() / 100.0;
        if (bins == 0 || motors < MOTORS) {
            return f;
        }
        double lo = h.motorLow();
        double span = h.motorHigh() - lo;
        double acc1g = h.acc1G();
        double pairs = Math.max(1, h.motorPoles() / 2);
        double[][] m = new double[MOTORS][];
        double[][] e = new double[MOTORS][];
        for (int k = 0; k < MOTORS; k++) {
            m[k] = d.column("motor[" + k + "]");
            e[k] = rpm ? d.column("eRPM[" + k + "]") : null;
        }
        double[] ax = acc ? d.column("accSmooth[0]") : null;
        double[] ay = acc ? d.column("accSmooth[1]") : null;
        double[] az = acc ? d.column("accSmooth[2]") : null;
        double[] gx = d.has("gyroADC[0]") ? d.column("gyroADC[0]") : null;
        double[] gy = d.has("gyroADC[1]") ? d.column("gyroADC[1]") : null;
        double[] gz = d.has("gyroADC[2]") ? d.column("gyroADC[2]") : null;
        double vScale = h.vbatScale();
        double gScale = h.gyroScale();
        double[] vb = vbat ? d.column("vbatLatest") : null;
        double[] thr = d.has("rcCommand[3]") ? d.column("rcCommand[3]") : null;
        double[] stick = d.has("rcCommand[0]") ? d.column("rcCommand[0]") : null;
        double[] sp = d.has("setpoint[0]") ? d.column("setpoint[0]") : null;
        double[] ba = d.has("baroAlt") ? d.column("baroAlt") : null;
        double[] gs = d.has("GPS_speed") && d.has("GPS_numSat") ? d.column("GPS_speed") : null;
        double[] sats = gs != null ? d.column("GPS_numSat") : null;
        double[] modes = d.has("flightModeFlags") ? d.column("flightModeFlags") : null;
        int[] count = new int[bins];
        double[][] sums = new double[13][bins];
        for (int r = 0; r < time.length; r++) {
            long b = (long) Math.floor((time[r] - t0) / width);
            if (b < 0 || b >= bins) {
                continue;
            }
            int i = (int) b;
            if (Double.isNaN(m[0][r]) || Double.isNaN(m[1][r]) || Double.isNaN(m[2][r]) || Double.isNaN(m[3][r])) {
                continue;
            }
            double sq = 0.0;
            double rp = 0.0;
            for (int k = 0; k < MOTORS; k++) {
                double dk = Math.max(0.0, Math.min(1.0, (m[k][r] - lo) / span));
                sq += dk * dk;
                if (rpm) {
                    rp += e[k][r];
                }
            }
            count[i]++;
            sums[0][i] += Math.sqrt(sq / MOTORS);
            sums[1][i] += acc ? az[r] / acc1g : Double.NaN;
            sums[2][i] += acc ? Math.hypot(ax[r], ay[r]) / acc1g : Double.NaN;
            sums[3][i] += gx != null && gy != null && gz != null ? gScale * Math.sqrt(gx[r] * gx[r] + gy[r] * gy[r] + gz[r] * gz[r]) : Double.NaN;
            sums[4][i] += tilt ? tiltRow[r] : Double.NaN;
            sums[5][i] += vbat ? vb[r] * vScale : Double.NaN;
            sums[6][i] += rpm ? rp / MOTORS * 100.0 / pairs : Double.NaN;
            sums[7][i] += thr != null ? (thr[r] - 1000.0) / 1000.0 : Double.NaN;
            sums[8][i] += stick != null ? stick[r] / 500.0 : Double.NaN;
            sums[9][i] += sp != null ? sp[r] : Double.NaN;
            sums[10][i] += ba != null ? ba[r] / 100.0 : Double.NaN;
            sums[11][i] += gs != null && sats[r] >= GPS_MIN_SATS ? gs[r] : Double.NaN;
            sums[12][i] += modes != null ? modes[r] : Double.NaN;
        }
        double[][] outs = {f.duty, f.fz, f.fxy, f.gyro, f.tilt, f.vbat, f.rpm, f.throttle, f.stickRoll, f.setpointRoll, f.baro, f.gpsSpeed, f.selfLevel};
        for (int i = 0; i < bins; i++) {
            f.valid[i] = count[i] > 0;
            for (int c = 0; c < outs.length; c++) {
                outs[c][i] = count[i] > 0 ? sums[c][i] / count[i] : Double.NaN;
            }
        }
        int i = 0;
        while (i < bins) {
            if (f.valid[i]) {
                i++;
                continue;
            }
            int j = i;
            while (j < bins && !f.valid[j]) {
                j++;
            }
            if (i > 0 && j < bins && j - i <= MAX_FILL) {
                for (int k = i; k < j; k++) {
                    double w = (double) (k - i + 1) / (j - i + 1);
                    for (double[] o : outs) {
                        o[k] = o[i - 1] + w * (o[j] - o[i - 1]);
                    }
                    f.valid[k] = true;
                }
            }
            i = j;
        }
        f.hasBaro = ba != null && Signals.std(f.baro, 0, f.n) > BARO_MIN_STD;
        f.hasGps = gs != null && !Double.isNaN(Signals.max(f.gpsSpeed, 0, f.n));
        return f;
    }

    public double climbRate(int from, int to) {
        if (!this.hasBaro) {
            return Double.NaN;
        }
        int a = Math.max(0, from);
        int b = Math.min(this.n, to);
        double[] x = new double[Math.max(0, b - a)];
        double[] y = new double[x.length];
        for (int i = a; i < b; i++) {
            x[i - a] = i / this.rate;
            y[i - a] = this.baro[i];
        }
        return Signals.linearFit(x, y)[0];
    }

    public double groundSpeed(int from, int to) {
        return this.hasGps ? Signals.mean(this.gpsSpeed, from, to) : Double.NaN;
    }

    public static Flight synthetic(double rate, double[] duty, double[] fz, double[] rpm, double[] vbat, double accLpfHz) {
        int n = duty.length;
        Flight f = new Flight(rate, n, 0.0, MOTORS, fz != null, rpm != null, false, vbat != null, accLpfHz);
        for (int i = 0; i < n; i++) {
            f.duty[i] = duty[i];
            f.fz[i] = fz != null ? fz[i] : Double.NaN;
            f.fxy[i] = fz != null ? 0.0 : Double.NaN;
            f.gyro[i] = 0.0;
            f.tilt[i] = Double.NaN;
            f.vbat[i] = vbat != null ? vbat[i] : Double.NaN;
            f.rpm[i] = rpm != null ? rpm[i] : Double.NaN;
            f.throttle[i] = Double.NaN;
            f.stickRoll[i] = Double.NaN;
            f.setpointRoll[i] = Double.NaN;
            f.baro[i] = Double.NaN;
            f.gpsSpeed[i] = Double.NaN;
            f.selfLevel[i] = Double.NaN;
            f.valid[i] = true;
        }
        return f;
    }
}
