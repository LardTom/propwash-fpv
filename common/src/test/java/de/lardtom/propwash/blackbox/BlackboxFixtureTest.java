package de.lardtom.propwash.blackbox;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.sim.PidLoopRate;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class BlackboxFixtureTest {
    static final String ENV = "PROPWASH_BLACKBOX_FIXTURE";

    @Test
    void writesFlightsForTheExternalDecoders() throws IOException {
        String target = System.getenv(ENV);
        assumeTrue(target != null && !target.isBlank(), ENV + " nicht gesetzt");
        Path out = Path.of(target);
        Files.createDirectories(out);
        BlackboxSetup.producerVersion("0.5.0");
        record Case(String name, DroneBuild build, PidLoopRate loop, BlackboxRate rate, double seconds) {
        }
        List<Case> cases = List.of(
                new Case("freestyle_2k_full", DroneBuild.FREESTYLE, PidLoopRate.HZ_2000, BlackboxRate.FULL, 20.0),
                new Case("freestyle_1k_half", DroneBuild.FREESTYLE, PidLoopRate.HZ_1000, BlackboxRate.HALF, 8.0),
                new Case("whoop_500_quarter", DroneBuild.WHOOP, PidLoopRate.HZ_500, BlackboxRate.QUARTER, 8.0));
        for (Case c : cases) {
            Path work = Files.createTempDirectory("bbx-" + c.name());
            BlackboxRecorder recorder = new BlackboxRecorder(work, false);
            recorder.configure(true, c.rate(), false, true, 900, 0, false, "Fixture", 512L << 20);
            BlackboxFlight flight = new BlackboxFlight(c.build(), c.loop(), recorder);
            flight.arm();
            flight.fly(c.seconds());
            flight.disarm();
            recorder.flush(0L);
            List<BlackboxLibrary.LogFile> files = recorder.library().list();
            assertEquals(1, files.size());
            Files.copy(files.get(0).path(), out.resolve(c.name() + ".bbl"), StandardCopyOption.REPLACE_EXISTING);
            List<BlackboxFlight.Truth> truth = flight.logs.get(0);
            List<String> lines = new ArrayList<>();
            lines.add("loopIteration,time,axisP[0],axisP[1],axisP[2],axisI[0],axisI[1],axisI[2],axisF[0],axisF[1],axisF[2],"
                    + "rcCommand[0],rcCommand[1],rcCommand[2],rcCommand[3],setpoint[0],setpoint[1],setpoint[2],setpoint[3],"
                    + "vbatLatest,amperageLatest,baroAlt,gyroADC[0],gyroADC[1],gyroADC[2],gyroUnfilt[0],gyroUnfilt[1],gyroUnfilt[2],"
                    + "debug[0],debug[1],debug[2],debug[3],motor[0],motor[1],motor[2],motor[3],eRPM[0],eRPM[1],eRPM[2],eRPM[3]");
            long base = truth.get(0).substep() * 500L;
            int period = 500 * truth.get(0).divider();
            for (int n = 0; n < truth.size(); n += c.rate().denominator()) {
                BlackboxFlight.Truth t = truth.get(n);
                StringBuilder sb = new StringBuilder();
                sb.append(n).append(',').append(base + (long) n * period);
                for (double[] arr : new double[][]{t.p(), t.i(), t.f()}) {
                    for (double v : arr) {
                        sb.append(',').append(fmt(v));
                    }
                }
                for (int a = 0; a < 3; a++) {
                    sb.append(',').append(fmt(t.stick()[a] * 500.0));
                }
                sb.append(',').append(fmt(1000.0 + t.stick()[3] * 1000.0));
                for (int a = 0; a < 3; a++) {
                    sb.append(',').append(fmt(t.setpoint()[a]));
                }
                sb.append(',').append(fmt(t.throttle() * 1000.0));
                sb.append(',').append(fmt(t.voltage())).append(',').append(fmt(t.current())).append(',').append(fmt(t.baro() * 100.0));
                for (double v : t.gyro()) {
                    sb.append(',').append(fmt(v));
                }
                for (double v : t.gyroRaw()) {
                    sb.append(',').append(fmt(v));
                }
                for (double v : t.gyroRaw()) {
                    sb.append(',').append(fmt(v));
                }
                sb.append(",0");
                for (double v : t.duty()) {
                    sb.append(',').append(fmt(48.0 + Math.abs(v) * 1999.0));
                }
                for (double v : t.rpm()) {
                    sb.append(',').append(fmt(Math.abs(v) * 7.0 / 100.0));
                }
                lines.add(sb.toString());
            }
            Files.write(out.resolve(c.name() + ".truth.csv"), lines);
        }
        Path work = Files.createTempDirectory("bbx-pause");
        BlackboxRecorder recorder = new BlackboxRecorder(work, false);
        recorder.configure(true, BlackboxRate.HALF, true, true, 1023, 0, false, "Pause", 512L << 20);
        BlackboxFlight flight = new BlackboxFlight(DroneBuild.RACER, PidLoopRate.HZ_2000, recorder);
        flight.arm();
        flight.fly(2.0);
        recorder.configure(true, BlackboxRate.HALF, true, false, 1023, 0, false, "Pause", 512L << 20);
        flight.fly(1.0);
        recorder.configure(true, BlackboxRate.HALF, true, true, 1023, 0, false, "Pause", 512L << 20);
        flight.fly(2.0);
        flight.disarm();
        flight.sim.reset(0.0, 60.0, 0.0, 0.0);
        flight.arm();
        flight.fly(1.0);
        flight.disarm();
        recorder.flush(0L);
        List<BlackboxLibrary.LogFile> files = recorder.library().list();
        assertEquals(2, files.size());
        List<byte[]> both = new ArrayList<>();
        for (int k = files.size() - 1; k >= 0; k--) {
            both.add(Files.readAllBytes(files.get(k).path()));
        }
        java.io.ByteArrayOutputStream joined = new java.io.ByteArrayOutputStream();
        for (byte[] b : both) {
            joined.write(b);
        }
        Files.write(out.resolve("racer_pause_two_logs.bbl"), joined.toByteArray());
        List<BlackboxDecoder> decoded = BlackboxDecoder.decodeAll(joined.toByteArray());
        List<String> counts = new ArrayList<>();
        for (BlackboxDecoder d : decoded) {
            assertEquals(0, d.invalid);
            counts.add(String.valueOf(d.main.size()));
        }
        Files.write(out.resolve("racer_pause_two_logs.frames"), counts);
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.6f", v);
    }
}
