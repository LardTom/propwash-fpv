package de.lardtom.propwash.calibration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalibrationLogicTest {
    static final double RATE = 1000.0;

    static LogHeader header(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return new LogHeader(m);
    }

    @Test
    void headerUnitsFollowTheFirmware() {
        LogHeader h = header("Firmware revision", "Betaflight 4.5.1 (77d01ba3b) STM32F405", "acc_lpf_hz", "2500", "acc_1G", "2048",
                "motor_pwm_protocol", "6", "gyro_scale", "0x3f800000", "motor_poles", "12", "rates_type", "3", "rc_rates", "7,7,7", "rates", "67,67,67",
                "rc_expo", "0,0,0");
        assertEquals(25.0, h.accLpfHz(), 1e-9);
        assertEquals(2048.0, h.acc1G(), 1e-9);
        assertTrue(h.dshot());
        assertEquals(48.0, h.motorLow(), 1e-9);
        assertEquals(1.0, h.gyroScale(), 1e-9);
        assertEquals(0.01, h.vbatScale(), 1e-12);
        assertEquals("ACTUAL 7/67/0", h.rateText());
        assertEquals(10.0, header("acc_lpf_hz", "10").accLpfHz(), 1e-9);
        assertFalse(header("motor_pwm_protocol", "1").dshot());
        assertEquals(2026, header("Firmware revision", "Betaflight 2026.6.0-alpha (543f1a5ff) STM32F405").version()[0]);
        assertEquals(0.1, header("Firmware revision", "Betaflight 3.5.7").vbatScale(), 1e-12);
    }

    @Test
    void csvColumnsAreReadByTheirBareNames() throws IOException {
        Path dir = Files.createTempDirectory("bbcal-csv");
        Path csv = dir.resolve("log.csv.gz");
        String text = "loopIteration, time (us), motor[0], motor[1], motor[2], motor[3], eRPM(/100)[0], vbatLatest, flightModeFlags (flags)\n"
                + "0, 1000, 48, 2047, 1047, 1047, 120, 1610, ANGLE_MODE\n"
                + "1, 1500, 100, 200, 300, 400, -5, 1609, ANGLE_MODE\n";
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(csv))) {
            out.write(text.getBytes(StandardCharsets.ISO_8859_1));
        }
        LogData d = LogData.readCsv(csv);
        assertEquals(2, d.rows());
        assertTrue(d.has("time"));
        assertTrue(d.has("eRPM[0]"));
        assertEquals(1500.0, d.column("time")[1], 1e-9);
        assertEquals(-5.0, d.column("eRPM[0]")[1], 1e-9);
        assertEquals(1609.0, d.column("vbatLatest")[1], 1e-9);
    }

    @Test
    void flightConvertsDshotRpmAndBattery() {
        int rows = 4000;
        LogData d = new LogData(rows);
        double[] time = new double[rows];
        double[][] motor = new double[4][rows];
        double[][] erpm = new double[4][rows];
        double[] vbat = new double[rows];
        double[] az = new double[rows];
        double[] q = new double[rows];
        for (int r = 0; r < rows; r++) {
            time[r] = 5_000_000.0 + r * 500.0;
            for (int k = 0; k < 4; k++) {
                motor[k][r] = 48.0 + 0.4 * 1999.0;
                erpm[k][r] = 1800.0;
            }
            vbat[r] = 1600.0;
            az[r] = 2048.0;
            q[r] = Math.sin(Math.toRadians(10.0)) * Attitude.QUATERNION_SCALE;
        }
        d.put("time", time);
        for (int k = 0; k < 4; k++) {
            d.put("motor[" + k + "]", motor[k]);
            d.put("eRPM[" + k + "]", erpm[k]);
        }
        d.put("vbatLatest", vbat);
        d.put("accSmooth[0]", new double[rows]);
        d.put("accSmooth[1]", new double[rows]);
        d.put("accSmooth[2]", az);
        d.put("imuQuaternion[0]", q);
        d.put("imuQuaternion[1]", new double[rows]);
        LogHeader h = header("Firmware revision", "Betaflight 4.5.1", "acc_1G", "2048", "motor_poles", "12", "motor_pwm_protocol", "7");
        Flight f = Flight.of(h, d);
        assertEquals(1000.0, f.rate, 1e-9);
        assertEquals(2000, f.n);
        assertEquals(0.4, f.duty[100], 1e-6);
        assertEquals(30000.0, f.rpm[100], 1e-6);
        assertEquals(16.0, f.vbat[100], 1e-9);
        assertEquals(1.0, f.fz[100], 1e-9);
        assertEquals(20.0, f.tilt[100], 1e-6);
        assertTrue(f.hasRpm && f.hasAcc && f.hasTilt);
    }

    @Test
    void complementaryAttitudeFindsAStaticTilt() {
        int rows = 4000;
        double[] time = new double[rows];
        double[] zero = new double[rows];
        double[] ax = new double[rows];
        double[] az = new double[rows];
        for (int r = 0; r < rows; r++) {
            time[r] = r * 1000.0;
            ax[r] = 2048.0 * Math.sin(Math.toRadians(20.0));
            az[r] = 2048.0 * Math.cos(Math.toRadians(20.0));
        }
        double[] tilt = Attitude.complementary(time, zero, zero, zero, ax, zero, az, 2048.0, 1.0);
        assertEquals(20.0, tilt[rows - 1], 0.2);
    }

    record Synthetic(double[] duty, double[] fz, double[] rpm, double[] vbat) {
    }

    static Synthetic flight() {
        int n = (int) (14.0 * RATE);
        double[] duty = new double[n];
        double[] fz = new double[n];
        double[] rpm = new double[n];
        double[] vbat = new double[n];
        double tau = 0.030;
        double w = 0.0;
        Signals.Pt2 acc = new Signals.Pt2(RATE, 25.0);
        double hover = 0.4;
        for (int i = 0; i < n; i++) {
            double t = i / RATE;
            double d;
            if (t < 1.0) {
                d = 0.05;
            } else if (t < 6.0) {
                d = hover;
            } else if (t < 6.6) {
                d = 0.7;
            } else if (t < 8.0) {
                d = hover;
            } else if (t < 9.0) {
                d = 0.06;
            } else {
                d = hover;
            }
            duty[i] = d;
            w += (d - w) * (1.0 / RATE) / tau;
            double thrust = Math.pow(w / hover, 2.0);
            fz[i] = t < 1.0 ? 1.0 : acc.apply(thrust + 0.003 * Math.sin(i * 0.7));
            rpm[i] = 60000.0 * w;
            vbat[i] = 4.0 - 0.3 * Math.max(0.0, d - hover);
        }
        return new Synthetic(duty, fz, rpm, vbat);
    }

    @Test
    void maneuversAreFoundAndMeasured() {
        Synthetic s = flight();
        Flight f = Flight.synthetic(RATE, s.duty(), s.fz(), s.rpm(), s.vbat(), 25.0);
        f.idleDuty = 0.04;
        Maneuvers.Found m = Maneuvers.find(f);
        assertEquals(0.4, m.hoverDuty, 0.01);
        assertTrue(m.hoverSeconds > 3.0, "Schweben " + m.hoverSeconds);
        assertEquals(1, m.punches.size(), m.rejected.toString());
        assertEquals(1, m.cuts.size(), m.rejected.toString());
        assertEquals(6.0, m.punches.getFirst().step() / RATE, 0.02);
        assertEquals(8.0, m.cuts.getFirst().step() / RATE, 0.02);
        Maneuvers.Event p = m.punches.getFirst();
        Measure.Punch punch = Measure.punch(Trace.of(f, p.from(), p.to(), p.step()));
        assertEquals(3.06, punch.peakFz(), 0.1);
        assertEquals(30.0, punch.rpmT63Ms(), 3.0);
        assertEquals(0.09, punch.sagVolts(), 0.01);
        Maneuvers.Event c = m.cuts.getFirst();
        Measure.Cut cut = Measure.cut(Trace.of(f, c.from(), c.to(), c.step()));
        assertEquals(0.0225, cut.fzEarly(), 0.02);
        assertEquals(30.0, cut.rpmT63Ms(), 3.0);
        assertEquals(Measure.G * (1.0 - cut.fzEarly()), cut.dropAccel(), 1e-9);
    }

    @Test
    void groundSegmentsAndRotationsAreRejected() {
        Synthetic s = flight();
        double[] fz = s.fz().clone();
        for (int i = (int) (7.9 * RATE); i < (int) (9.0 * RATE); i++) {
            fz[i] = 1.0;
        }
        Flight f = Flight.synthetic(RATE, s.duty(), fz, s.rpm(), s.vbat(), 25.0);
        for (int i = (int) (5.8 * RATE); i < (int) (6.5 * RATE); i++) {
            f.gyro[i] = 900.0;
        }
        Maneuvers.Found m = Maneuvers.find(f);
        assertEquals(0, m.punches.size());
        assertEquals(0, m.cuts.size());
        assertTrue(m.rejected.containsKey("Punch: Drehung während des Punchs"), m.rejected.toString());
        assertTrue(m.rejected.containsKey("Gas weg: keine Reaktion der Beschleunigung (am Boden?)"), m.rejected.toString());
    }

    @Test
    void terminalDescentFitRecoversTheDragLaw() {
        double rate = 1000.0;
        double f0 = 0.05;
        double k = 0.02;
        int pre = (int) (0.4 * rate);
        int n = pre + (int) (1.6 * rate);
        Trace t = new Trace(rate, pre, n);
        double v = 0.0;
        for (int i = 0; i < n; i++) {
            double fz = i < pre ? 1.0 : f0 + k * v * Math.abs(v);
            t.fz[i] = fz;
            t.duty[i] = i < pre ? 0.4 : 0.05;
            t.rpm[i] = Double.NaN;
            t.vbat[i] = 4.0;
            if (i >= pre) {
                v += Measure.G * (1.0 - fz) / rate;
            }
        }
        Measure.Cut c = Measure.cut(t);
        assertEquals(Math.sqrt((1.0 - f0) / k), c.vTerminal(), 0.35);
        assertTrue(c.fzEarly() > f0 && c.fzEarly() < 0.2, "früh " + c.fzEarly());
    }

    @Test
    void svgPlotsAreSelfContained() {
        String svg = new Svg("Test & Plot", "x", "y").line("echt", Svg.REAL, new double[]{0, 1, 2}, new double[]{0, 1, 4})
                .points("sim", Svg.SIM, new double[]{0, 1}, new double[]{1, Double.NaN}).hLine(1.0).render();
        assertTrue(svg.startsWith("<svg"));
        assertTrue(svg.contains("Test &amp; Plot"));
        assertTrue(svg.trim().endsWith("</svg>"));
    }
}
