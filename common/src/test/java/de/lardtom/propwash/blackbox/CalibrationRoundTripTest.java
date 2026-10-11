package de.lardtom.propwash.blackbox;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.calibration.Flight;
import de.lardtom.propwash.calibration.LogData;
import de.lardtom.propwash.calibration.LogHeader;
import de.lardtom.propwash.calibration.Maneuvers;
import de.lardtom.propwash.calibration.Measure;
import de.lardtom.propwash.calibration.Trace;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.sim.MoveReport;
import de.lardtom.propwash.sim.PidLoopRate;
import de.lardtom.propwash.sim.PilotInput;
import de.lardtom.propwash.sim.SimEnvironment;
import de.lardtom.propwash.sim.SimTuning;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.StepResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CalibrationRoundTripTest {
    static final String DECODER_ENV = "PROPWASH_BBCAL_DECODER";
    static final String GAME_LOGS_ENV = "PROPWASH_BBCAL_GAME_LOGS";
    static final long FRAME_NANOS = 1_000_000_000L / 120L;
    static final double DUTY_LSB = 1.0 / 1999.0;
    static final double RPM_LSB = 100.0 / 7.0;

    record Truth(double seconds, double duty, double rpm, double voltage) {
    }

    record Flown(byte[] log, List<Truth> truth) {
    }

    static Flown fly(DroneBuild build) throws IOException {
        Path work = Files.createTempDirectory("bbcal-roundtrip");
        BlackboxRecorder recorder = new BlackboxRecorder(work, false);
        recorder.configure(true, BlackboxRate.FULL, false, true, 900, 0, false, "Kalibrierung", 512L << 20);
        FlightControllerConfig config = new FlightControllerConfig();
        config.filters.pidLoop = PidLoopRate.HZ_2000;
        SimTuning tuning = new SimTuning();
        tuning.gyroNoiseDps = 0.0;
        tuning.gyroVibrationDps = 0.0;
        DroneSim sim = new DroneSim(build, config, tuning, 99L);
        sim.reset(0.0, 120.0, 0.0, 0.0);
        List<Truth> truth = new ArrayList<>();
        sim.setFcListener((s, sample) -> {
            if (sample.armed) {
                double d = 0.0;
                double r = 0.0;
                for (int k = 0; k < 4; k++) {
                    d += sample.motorDuty[k] * sample.motorDuty[k];
                    r += Math.abs(sample.motorRpm[k]);
                }
                truth.add(new Truth(sample.substep * DroneSim.SUBSTEP_NANOS * 1e-9, Math.sqrt(d / 4.0), r / 4.0, sample.voltage));
            }
            recorder.onFcSample(s, sample);
        });
        PilotInput input = new PilotInput();
        SimEnvironment env = new SimEnvironment();
        StepResult result = new StepResult();
        MoveReport report = new MoveReport();
        input.mode = StabMode.ANGLE;
        input.armSwitch = false;
        frame(sim, input, env, result, report);
        input.armSwitch = true;
        input.throttle = 0.0;
        frame(sim, input, env, result, report);
        double[][] schedule = {{0.45, 3.0}, {1.0, 0.5}, {0.45, 2.0}, {0.0, 0.6}, {0.45, 2.0}};
        for (double[] s : schedule) {
            input.throttle = s[0];
            for (double t = 0.0; t < s[1]; t += FRAME_NANOS * 1e-9) {
                frame(sim, input, env, result, report);
            }
        }
        input.armSwitch = false;
        frame(sim, input, env, result, report);
        frame(sim, input, env, result, report);
        recorder.flush(0L);
        List<BlackboxLibrary.LogFile> files = recorder.library().list();
        assertEquals(1, files.size());
        return new Flown(Files.readAllBytes(files.getFirst().path()), truth);
    }

    static void frame(DroneSim sim, PilotInput input, SimEnvironment env, StepResult result, MoveReport report) {
        sim.step(FRAME_NANOS, input, env, result);
        report.clear();
        report.requestedX = result.dx;
        report.requestedY = result.dy;
        report.requestedZ = result.dz;
        report.actualX = result.dx;
        report.actualY = result.dy;
        report.actualZ = result.dz;
        report.supportGap = 0.05;
        sim.afterMove(report);
    }

    static Flight decodeInJava(byte[] log) {
        BlackboxDecoder d = BlackboxDecoder.decodeAll(log).getFirst();
        assertEquals(0, d.invalid);
        LogData data = new LogData(d.main.size());
        for (int c = 0; c < d.intra.names.length; c++) {
            String name = d.intra.names[c];
            if (!LogData.WANTED.contains(name)) {
                continue;
            }
            double[] v = new double[d.main.size()];
            for (int r = 0; r < v.length; r++) {
                v[r] = d.main.get(r)[c];
            }
            data.put(name, v);
        }
        return Flight.of(new LogHeader(d.headers), data);
    }

    static Flight truthFlight(List<Truth> truth, double rate) {
        double t0 = truth.getFirst().seconds();
        int n = (int) Math.floor((truth.getLast().seconds() - t0) * rate) + 1;
        double[] duty = new double[n];
        double[] rpm = new double[n];
        double[] vbat = new double[n];
        int[] count = new int[n];
        for (Truth t : truth) {
            int i = (int) Math.floor((t.seconds() - t0) * rate + 1e-9);
            if (i >= 0 && i < n) {
                duty[i] += t.duty();
                rpm[i] += t.rpm();
                vbat[i] += t.voltage();
                count[i]++;
            }
        }
        for (int i = 0; i < n; i++) {
            duty[i] /= Math.max(1, count[i]);
            rpm[i] /= Math.max(1, count[i]);
            vbat[i] /= Math.max(1, count[i]);
        }
        return Flight.synthetic(rate, duty, null, rpm, vbat, 25.0);
    }

    static void assertSame(Flight expected, Flight actual, String what) {
        int n = Math.min(expected.n, actual.n);
        assertTrue(n > 0.95 * expected.n, what + ": " + actual.n + " statt " + expected.n + " Werte");
        double worstDuty = 0.0;
        double worstRpm = 0.0;
        double worstV = 0.0;
        for (int i = 1; i < n - 1; i++) {
            worstDuty = Math.max(worstDuty, Math.abs(expected.duty[i] - actual.duty[i]));
            worstRpm = Math.max(worstRpm, Math.abs(expected.rpm[i] - actual.rpm[i]));
            worstV = Math.max(worstV, Math.abs(expected.vbat[i] - actual.vbat[i]));
        }
        System.out.println(String.format(Locale.ROOT, "Rundreise %s: %d Werte, größte Abweichung Duty %.5f, Drehzahl %.1f 1/min, Spannung %.4f V",
                what, n, worstDuty, worstRpm, worstV));
        assertTrue(worstDuty <= 2.0 * DUTY_LSB, what + " Duty " + worstDuty);
        assertTrue(worstRpm <= 2.0 * RPM_LSB, what + " Drehzahl " + worstRpm);
        assertTrue(worstV <= 0.0101, what + " Spannung " + worstV);
    }

    static List<double[]> lags(Flight f) {
        Maneuvers.Found m = Maneuvers.find(f);
        List<double[]> out = new ArrayList<>();
        for (Maneuvers.Event e : m.punches) {
            out.add(new double[]{e.step() / f.rate, Measure.punch(Trace.of(f, e.from(), e.to(), e.step())).rpmT63Ms()});
        }
        for (Maneuvers.Event e : m.cuts) {
            out.add(new double[]{e.step() / f.rate, Measure.cut(Trace.of(f, e.from(), e.to(), e.step())).rpmT63Ms()});
        }
        return out;
    }

    @Test
    void propwashLogsDecodeToTheFlightModelValues() throws IOException {
        for (DroneBuild build : List.of(DroneBuild.WHOOP, DroneBuild.FREESTYLE)) {
            Flown flown = fly(build);
            Flight decoded = decodeInJava(flown.log());
            Flight truth = truthFlight(flown.truth(), decoded.rate);
            assertEquals(1000.0, decoded.rate, 1e-9);
            assertFalse(decoded.hasAcc);
            assertTrue(decoded.hasRpm);
            assertSame(truth, decoded, "Java-Decoder " + build.frame().key());
            List<double[]> a = lags(truth);
            List<double[]> b = lags(decoded);
            assertEquals(2, a.size(), "Ereignisse " + a.size());
            assertEquals(a.size(), b.size());
            for (int k = 0; k < a.size(); k++) {
                assertEquals(a.get(k)[0], b.get(k)[0], 0.002);
                assertEquals(a.get(k)[1], b.get(k)[1], 1.01);
            }
        }
    }

    static Flight decodeWithTool(String decoder, Path bbl, int index) throws IOException, InterruptedException {
        Path dir = bbl.getParent();
        Process p = new ProcessBuilder(decoder, "--index", String.valueOf(index), "--unit-vbat", "raw", "--unit-amperage", "raw", "--unit-flags", "raw", "--merge-gps",
                "--save-headers", bbl.getFileName().toString()).directory(dir.toFile()).redirectErrorStream(true)
                .redirectOutput(dir.resolve("decode.txt").toFile()).start();
        assertEquals(0, p.waitFor());
        String base = bbl.getFileName().toString().replaceAll("\\.[^.]+$", "");
        String suffix = String.format(Locale.ROOT, ".%02d", index);
        return Flight.of(LogHeader.readCsv(dir.resolve(base + suffix + ".headers.csv")), LogData.readCsv(dir.resolve(base + suffix + ".csv")));
    }

    @Test
    void blackboxDecodeReadsPropwashLogsLikeTheFlightModel() throws Exception {
        String decoder = System.getenv(DECODER_ENV);
        assumeTrue(decoder != null && Files.isExecutable(Path.of(decoder)), DECODER_ENV + " nicht gesetzt (tools/blackbox_calibration.sh roundtrip)");
        for (DroneBuild build : List.of(DroneBuild.WHOOP, DroneBuild.FREESTYLE)) {
            Flown flown = fly(build);
            Path dir = Files.createTempDirectory("bbcal-tool");
            Path bbl = dir.resolve("log.bbl");
            Files.write(bbl, flown.log());
            Flight viaTool = decodeWithTool(decoder, bbl, 1);
            Flight viaJava = decodeInJava(flown.log());
            Flight truth = truthFlight(flown.truth(), viaTool.rate);
            assertSame(truth, viaTool, "blackbox_decode " + build.frame().key());
            assertSame(viaJava, viaTool, "blackbox_decode gegen Java-Decoder " + build.frame().key());
            List<double[]> a = lags(truth);
            List<double[]> b = lags(viaTool);
            assertEquals(a.size(), b.size());
            for (int k = 0; k < a.size(); k++) {
                assertEquals(a.get(k)[1], b.get(k)[1], 1.01);
            }
            System.out.println(String.format(Locale.ROOT, "Rundreise %s: Motorverzögerung t63 echt/dekodiert %s", build.frame().key(),
                    b.stream().map(x -> String.format(Locale.ROOT, "%.0f ms", x[1])).toList()));
        }
    }

    @Test
    void logsFromTheGameDecodeCleanly() throws Exception {
        String decoder = System.getenv(DECODER_ENV);
        String dir = System.getenv(GAME_LOGS_ENV);
        assumeTrue(decoder != null && dir != null && Files.isDirectory(Path.of(dir)), GAME_LOGS_ENV + " nicht gesetzt");
        List<Path> logs;
        try (Stream<Path> s = Files.walk(Path.of(dir))) {
            logs = s.filter(p -> p.toString().endsWith(".bbl")).sorted().toList();
        }
        assumeTrue(!logs.isEmpty(), "keine Logs aus dem Spiel in " + dir);
        for (Path log : logs) {
            Path work = Files.createTempDirectory("bbcal-game");
            Path copy = work.resolve("game.bbl");
            Files.copy(log, copy);
            Flight f = decodeWithTool(decoder, copy, 1);
            assertTrue(f.n > 100, log.getFileName() + ": " + f.n);
            for (int i = 0; i < f.n; i++) {
                if (!f.valid[i]) {
                    continue;
                }
                assertTrue(f.duty[i] >= 0.0 && f.duty[i] <= 1.0, log.getFileName() + " Duty " + f.duty[i]);
                assertTrue(!f.hasRpm || f.rpm[i] >= 0.0, log.getFileName() + " Drehzahl " + f.rpm[i]);
                assertTrue(f.vbat[i] > 2.5 && f.vbat[i] < 60.0, log.getFileName() + " Spannung " + f.vbat[i]);
            }
            System.out.println(String.format(Locale.ROOT, "Rundreise Spiel-Log %s: %.1f s, %d Werte, Drehzahl %s", log.getFileName(), f.durationSeconds(), f.n,
                    f.hasRpm ? "ja" : "nein"));
        }
    }
}
