package de.lardtom.propwash.blackbox;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.FcSample;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.sim.SimTuning;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlackboxHotPathTest {
    @TempDir
    Path dir;

    @Test
    void samplingAllocatesNothingOnTheSimulatorThread() {
        com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        threads.setThreadAllocatedMemoryEnabled(true);
        DroneSim sim = new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), new SimTuning(), 1L);
        BlackboxRecorder recorder = new BlackboxRecorder(this.dir, true);
        recorder.configure(true, BlackboxRate.FULL, false, true, 1000, 0, false, "Hot", 512L << 20);
        FcSample s = new FcSample();
        s.armed = true;
        s.voltage = 16.4;
        s.gpsFix = true;
        s.gpsSatellites = 12;
        for (int i = 0; i < 4; i++) {
            s.motorDuty[i] = 0.3;
            s.motorRpm[i] = 20000.0;
        }
        recorder.onFcSample(sim, s);
        for (int n = 0; n < 5000; n++) {
            step(s, n);
            recorder.onFcSample(sim, s);
        }
        long before = threads.getCurrentThreadAllocatedBytes();
        for (int n = 5000; n < 45000; n++) {
            step(s, n);
            recorder.onFcSample(sim, s);
        }
        long allocated = threads.getCurrentThreadAllocatedBytes() - before;
        assertTrue(allocated < 1024, "Spielthread allokiert " + allocated + " Bytes für 40000 Abtastungen");
        s.armed = false;
        recorder.onFcSample(sim, s);
        assertTrue(recorder.flush(5_000_000_000L));
        assertEquals(1, recorder.library().list().size());
        recorder.writer().shutdown();
    }

    private static void step(FcSample s, int n) {
        s.substep = n;
        double t = n * 0.0005;
        for (int a = 0; a < 3; a++) {
            s.gyro[a] = 200.0 * Math.sin(t * (a + 1));
            s.gyroRaw[a] = s.gyro[a] + Math.sin(n * 1.7);
            s.setpoint[a] = 210.0 * Math.sin(t * (a + 1));
            s.pTerm[a] = 30.0 * Math.cos(t);
            s.iTerm[a] = 5.0;
        }
        s.gpsX = t * 10.0;
        s.gpsZ = -t * 3.0;
    }
}
