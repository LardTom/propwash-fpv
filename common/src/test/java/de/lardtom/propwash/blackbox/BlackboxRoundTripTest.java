package de.lardtom.propwash.blackbox;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.sim.PidLoopRate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlackboxRoundTripTest {
    @TempDir
    Path dir;

    static BlackboxRecorder recorder(Path dir, BlackboxRate rate) {
        BlackboxRecorder r = new BlackboxRecorder(dir, false);
        r.configure(true, rate, false, true, 1023, 0, false, "Test Quad", 512L << 20);
        return r;
    }

    static List<BlackboxDecoder> decode(Path file) throws IOException {
        return BlackboxDecoder.decodeAll(Files.readAllBytes(file));
    }

    @Test
    void everyLoggedFrameMatchesTheSimulator() throws IOException {
        BlackboxRecorder recorder = recorder(this.dir, BlackboxRate.FULL);
        BlackboxFlight flight = new BlackboxFlight(DroneBuild.FREESTYLE, PidLoopRate.HZ_2000, recorder);
        flight.arm();
        flight.fly(10.0);
        flight.disarm();
        recorder.flush(0L);
        List<BlackboxLibrary.LogFile> files = recorder.library().list();
        assertEquals(1, files.size());
        List<BlackboxDecoder> logs = decode(files.get(0).path());
        assertEquals(1, logs.size());
        BlackboxDecoder log = logs.get(0);
        List<BlackboxFlight.Truth> truth = flight.logs.get(0);
        assertTrue(log.ended, "Log-Ende fehlt");
        assertEquals(0, log.invalid);
        assertEquals(BlackboxSetup.firmwareRevision(), log.headers.get("Firmware revision"));
        assertTrue(log.headers.get("Firmware revision").startsWith("Betaflight 4.5.1 compatible (Propwash"));
        assertEquals("1/1", log.headers.get("P interval"));
        assertEquals("64", log.headers.get("I interval"));
        assertEquals("Test Quad", log.headers.get("Craft name"));
        assertEquals("6", log.headers.get("debug_mode"));
        assertTrue(log.headers.containsKey("Firmware date"));
        assertTrue(log.headers.containsKey("acc_hardware"));
        assertEquals('I', log.mainKinds.get(0));
        assertEquals(truth.size(), log.main.size());
        assertTrue(log.main.size() > 19_000, "zu wenige Frames: " + log.main.size());
        assertEquals(-1, log.intra.index("axisD[2]"));
        long timeBase = truth.get(0).substep() * 500L;
        for (int n = 0; n < log.main.size(); n++) {
            long[] f = log.main.get(n);
            BlackboxFlight.Truth t = truth.get(n);
            assertEquals(n, log.field(f, "loopIteration"));
            assertEquals(timeBase + 500L * n, log.field(f, "time"));
            assertEquals(n % 64 == 0 ? 'I' : 'P', log.mainKinds.get(n));
            for (int a = 0; a < 3; a++) {
                near(t.p()[a], log.field(f, "axisP[" + a + "]"), 0.5, "axisP", n);
                near(t.i()[a], log.field(f, "axisI[" + a + "]"), 0.5, "axisI", n);
                near(t.f()[a], log.field(f, "axisF[" + a + "]"), 0.5, "axisF", n);
                if (a < 2) {
                    near(t.d()[a], log.field(f, "axisD[" + a + "]"), 0.5, "axisD", n);
                }
                near(t.stick()[a] * 500.0, log.field(f, "rcCommand[" + a + "]"), 0.5, "rcCommand", n);
                near(t.setpoint()[a], log.field(f, "setpoint[" + a + "]"), 0.5, "setpoint", n);
                near(t.gyro()[a], log.field(f, "gyroADC[" + a + "]"), 0.5, "gyroADC", n);
                near(t.gyroRaw()[a], log.field(f, "gyroUnfilt[" + a + "]"), 0.5, "gyroUnfilt", n);
                near(t.gyroRaw()[a], log.field(f, "debug[" + a + "]"), 0.5, "debug", n);
            }
            assertEquals(0, log.field(f, "debug[3]"));
            near(1000.0 + t.stick()[3] * 1000.0, log.field(f, "rcCommand[3]"), 0.5, "rcCommand[3]", n);
            near(t.throttle() * 1000.0, log.field(f, "setpoint[3]"), 0.5, "setpoint[3]", n);
            near(t.voltage() * 100.0, log.field(f, "vbatLatest"), 0.5, "vbat", n);
            near(t.current() * 100.0, log.field(f, "amperageLatest"), 0.5, "amperage", n);
            near(t.baro() * 100.0, log.field(f, "baroAlt"), 0.5, "baroAlt", n);
            assertEquals(1023, log.field(f, "rssi"));
            for (int m = 0; m < 4; m++) {
                near(48.0 + Math.abs(t.duty()[m]) * 1999.0, log.field(f, "motor[" + m + "]"), 0.5, "motor", n);
                near(Math.abs(t.rpm()[m]) * 7.0 / 100.0, log.field(f, "eRPM[" + m + "]"), 0.5, "eRPM", n);
            }
        }
        assertFalse(log.gpsFrames.isEmpty(), "keine GPS-Frames");
        assertFalse(log.homeFrames.isEmpty(), "kein GPS-Home");
        assertFalse(log.slowFrames.isEmpty(), "keine S-Frames");
        assertEquals(BlackboxModes.ARM, log.slowFrames.get(0)[0]);
        assertTrue(log.slowFrames.stream().anyMatch(s -> s[0] == (BlackboxModes.ARM | BlackboxModes.AIRMODE)), "Airmode fehlt");
        assertTrue(log.slowFrames.stream().allMatch(s -> s[4] == 1 && s[3] == 1 && s[2] == 0));
        int sats = log.gps.index("GPS_numSat");
        int lat = log.gps.index("GPS_coord[0]");
        int lon = log.gps.index("GPS_coord[1]");
        assertTrue(log.gpsFrames.stream().allMatch(g -> g[sats] >= 5));
        long[] lastGps = log.gpsFrames.get(log.gpsFrames.size() - 1);
        BlackboxFlight.Truth end = truth.get(truth.size() - 1);
        assertTrue(Math.abs(lastGps[lat] - BlackboxSampler.latitudeE7(end.gpsZ())) < 200, "Breite");
        assertTrue(Math.abs(lastGps[lon] - BlackboxSampler.longitudeE7(end.gpsX())) < 200, "Länge");
        assertEquals(0, log.events.get(0).type());
    }

    static void near(double expected, long actual, double tolerance, String field, int frame) {
        if (Math.abs(expected - actual) > tolerance + 1e-9) {
            throw new AssertionError(field + " in Frame " + frame + ": erwartet " + expected + ", gelesen " + actual);
        }
    }

    @Test
    void sampleRateKeepsTheBetaflightFrameSchedule() throws IOException {
        for (BlackboxRate rate : List.of(BlackboxRate.HALF, BlackboxRate.QUARTER, BlackboxRate.EIGHTH)) {
            Path sub = this.dir.resolve(rate.key());
            BlackboxRecorder recorder = recorder(sub, rate);
            BlackboxFlight flight = new BlackboxFlight(DroneBuild.FREESTYLE, PidLoopRate.HZ_1000, recorder);
            flight.arm();
            flight.fly(3.0);
            flight.disarm();
            recorder.flush(0L);
            BlackboxDecoder log = decode(recorder.library().list().get(0).path()).get(0);
            assertEquals("1/" + rate.denominator(), log.headers.get("P interval"));
            assertEquals("32", log.headers.get("I interval"));
            assertEquals("2", log.headers.get("pid_process_denom"));
            assertEquals(0, log.invalid);
            int truthSize = flight.logs.get(0).size();
            int expected = (truthSize + rate.denominator() - 1) / rate.denominator();
            assertEquals(expected, log.main.size(), rate.name());
            for (int k = 0; k < log.main.size(); k++) {
                long iteration = log.field(log.main.get(k), "loopIteration");
                assertEquals((long) k * rate.denominator(), iteration);
                assertEquals(flight.logs.get(0).get(0).substep() * 500L + iteration * 1000L, log.field(log.main.get(k), "time"));
            }
        }
    }

    @Test
    void switchPausesAndResumesAtAnIntraFrame() throws IOException {
        BlackboxRecorder recorder = recorder(this.dir, BlackboxRate.FULL);
        BlackboxFlight flight = new BlackboxFlight(DroneBuild.FREESTYLE, PidLoopRate.HZ_2000, recorder);
        flight.arm();
        flight.fly(1.0);
        recorder.configure(true, BlackboxRate.FULL, true, false, 1023, 0, false, "", 512L << 20);
        flight.fly(0.5);
        assertEquals(BlackboxRecorder.State.PAUSED, recorder.state());
        recorder.configure(true, BlackboxRate.FULL, true, true, 1023, 0, false, "", 512L << 20);
        flight.fly(1.0);
        flight.disarm();
        recorder.flush(0L);
        BlackboxDecoder log = decode(recorder.library().list().get(0).path()).get(0);
        assertEquals(0, log.invalid);
        BlackboxDecoder.Event resume = log.events.stream().filter(e -> e.type() == 14).findFirst().orElseThrow();
        long[] after = log.main.get(resume.afterMainFrames());
        assertEquals('I', log.mainKinds.get(resume.afterMainFrames()));
        assertEquals(resume.a(), log.field(after, "loopIteration"));
        assertEquals(0, resume.a() % 64);
        assertTrue(resume.a() > 2900, "Pause zu kurz: " + resume.a());
        long gap = log.field(after, "loopIteration") - log.field(log.main.get(resume.afterMainFrames() - 1), "loopIteration");
        assertTrue(gap > 900, "Lücke " + gap);
        assertTrue(log.ended);
    }

    @Test
    void everyArmGetsItsOwnLogAndDisarmEndsIt() throws IOException {
        BlackboxRecorder recorder = recorder(this.dir, BlackboxRate.HALF);
        BlackboxFlight flight = new BlackboxFlight(DroneBuild.FREESTYLE, PidLoopRate.HZ_2000, recorder);
        for (int k = 0; k < 3; k++) {
            flight.sim.reset(0.0, 60.0, 0.0, 0.0);
            flight.arm();
            flight.fly(0.7);
            flight.disarm();
            flight.fly(1.2);
        }
        recorder.flush(0L);
        List<BlackboxLibrary.LogFile> files = recorder.library().list();
        assertEquals(3, files.size());
        for (BlackboxLibrary.LogFile file : files) {
            BlackboxDecoder log = decode(file.path()).get(0);
            assertTrue(log.ended);
            assertEquals(0, log.invalid);
            assertTrue(log.main.size() > 500);
        }
        assertEquals(BlackboxRecorder.State.READY, recorder.state());
    }

    @Test
    void disabledRecorderWritesNothing() throws IOException {
        BlackboxRecorder recorder = new BlackboxRecorder(this.dir, false);
        recorder.configure(false, BlackboxRate.FULL, false, true, 1023, 0, false, "", 512L << 20);
        BlackboxFlight flight = new BlackboxFlight(DroneBuild.FREESTYLE, PidLoopRate.HZ_2000, recorder);
        flight.arm();
        flight.fly(0.5);
        flight.disarm();
        recorder.flush(0L);
        assertTrue(recorder.library().list().isEmpty());
        assertEquals(BlackboxRecorder.State.OFF, recorder.state());
    }

    @Test
    void storageLimitDeletesOldLogsAndEndsAnOversizedOne() throws IOException {
        Files.createDirectories(this.dir);
        Path old = this.dir.resolve("propwash_2020-01-01_00-00-00.bbl");
        Files.write(old, new byte[900 * 1024]);
        Files.setLastModifiedTime(old, java.nio.file.attribute.FileTime.fromMillis(1000L));
        BlackboxRecorder recorder = new BlackboxRecorder(this.dir, false);
        recorder.configure(true, BlackboxRate.FULL, false, true, 1023, 0, false, "", 1L << 20);
        BlackboxFlight flight = new BlackboxFlight(DroneBuild.FREESTYLE, PidLoopRate.HZ_2000, recorder);
        flight.arm();
        flight.fly(25.0);
        assertEquals(BlackboxRecorder.State.FULL, recorder.state());
        flight.disarm();
        recorder.flush(0L);
        assertFalse(Files.exists(old), "alter Log nicht gelöscht");
        BlackboxWriter.Saved saved = recorder.writer().lastSaved();
        assertNotNull(saved);
        assertTrue(saved.full());
        assertTrue(Files.size(saved.path()) <= (1L << 20) + BlackboxWriter.FLUSH_BYTES * 2L);
        BlackboxDecoder log = decode(saved.path()).get(0);
        assertTrue(log.ended);
        assertEquals(0, log.invalid);
    }
}
