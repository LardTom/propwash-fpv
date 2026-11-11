package de.lardtom.propwash.accessory.fix;

import de.lardtom.propwash.accessory.MotionTrack;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MotionTrackTest {
    private static final long MS = 1_000_000L;
    private static final long TICK = 50 * MS;
    private static final double SPEED = 5.6;
    private static final int TICKS = 40;

    private record Packet(long arrival, double x) {
    }

    private static List<Packet> pilotPackets(double fps, int sendHz, long latency, long jitter, long seed, long until) {
        Random random = new Random(seed);
        List<Packet> out = new ArrayList<>();
        long frame = Math.round(1.0E9 / fps);
        long interval = 1_000_000_000L / sendHz;
        long lastSend = Long.MIN_VALUE / 2;
        for (long now = 0; now < until; now += frame + (random.nextInt(5) - 2) * MS / 2) {
            if (now - lastSend >= interval) {
                lastSend = now;
                long arrival = now + latency + (jitter == 0 ? 0 : (long) (random.nextDouble() * jitter));
                out.add(new Packet(arrival, SPEED * now * 1.0E-9));
            }
        }
        out.sort((a, b) -> Long.compare(a.arrival, b.arrival));
        return out;
    }

    private static final class Server {
        final MotionTrack track = new MotionTrack();
        final List<Packet> packets;
        int next;
        double tickX = Double.NaN;
        double position;

        Server(List<Packet> packets) {
            this.packets = packets;
        }

        void deliverUntil(long now) {
            while (this.next < this.packets.size() && this.packets.get(this.next).arrival <= now) {
                Packet packet = this.packets.get(this.next++);
                this.position = packet.x;
                this.track.add(packet.arrival, packet.x, 64.0, 0.0);
            }
        }

        double oldTickSpeed() {
            double speed = Double.isNaN(this.tickX) ? 0.0 : (this.position - this.tickX) * 20.0;
            this.tickX = this.position;
            return speed;
        }

        double speed(long now) {
            double[] out = new double[3];
            return this.track.estimate(now, out) ? Math.sqrt(out[0] * out[0] + out[1] * out[1] + out[2] * out[2]) : 0.0;
        }
    }

    private static int[] flyAndMeasure(double fps, int sendHz, long latency, long jitter, long seed, long phase, double tolerance) {
        Server server = new Server(pilotPackets(fps, sendHz, latency, jitter, seed, 6_000 * MS));
        int oldZeros = 0;
        int newZeros = 0;
        long start = 2_000 * MS + phase;
        for (long t = phase; t < start; t += TICK) {
            server.deliverUntil(t);
            server.oldTickSpeed();
        }
        for (int i = 0; i < TICKS; i++) {
            long tick = start + i * TICK;
            server.deliverUntil(tick);
            if (server.oldTickSpeed() == 0.0) {
                oldZeros++;
            }
            double speed = server.speed(tick);
            if (speed == 0.0) {
                newZeros++;
            }
            assertEquals(SPEED, speed, SPEED * tolerance, "Tick " + i + " bei " + sendHz + " Hz");
            long between = tick + TICK / 2 + 7 * MS;
            server.deliverUntil(between);
            assertEquals(SPEED, server.speed(between), SPEED * tolerance, "zwischen Tick " + i + " und " + (i + 1));
        }
        return new int[]{oldZeros, newZeros};
    }

    @Test
    void pilotFlightNeverReadsZeroBecauseOfAMissingPacket() {
        int oldZeros = 0;
        for (long seed = 1; seed <= 12; seed++) {
            int[] zeros = flyAndMeasure(60.0, 20, 2 * MS, 18 * MS, seed, (seed * 7) % 50 * MS, 0.1);
            oldZeros += zeros[0];
            assertEquals(0, zeros[1], "Seed " + seed);
        }
        assertTrue(oldZeros > 12, "Szenario zeigt den alten Fehler nicht: " + oldZeros + " Nullen in 12 × 40 Ticks");
    }

    @Test
    void slowAndFastSendRatesStayClose() {
        for (int hz : new int[]{5, 10, 20, 30, 40}) {
            int[] zeros = flyAndMeasure(144.0, hz, 30 * MS, 8 * MS, hz, 13 * MS, 0.1);
            assertEquals(0, zeros[1], hz + " Hz");
        }
    }

    @Test
    void busyServerTicksStayCloseOnAverage() {
        double sum = 0.0;
        double worst = 0.0;
        int samples = 0;
        for (long seed = 1; seed <= 20; seed++) {
            Random random = new Random(seed);
            List<long[]> busy = new ArrayList<>();
            for (long tick = 0; tick < 8_000 * MS; ) {
                long work = 2 * MS + (long) ((random.nextDouble() < 0.15 ? 60.0 : 10.0) * random.nextDouble() * MS);
                busy.add(new long[]{tick, tick + work});
                tick += Math.max(TICK, work);
            }
            List<Packet> packets = new ArrayList<>();
            for (Packet packet : pilotPackets(60.0, 20, MS, 3 * MS, seed, 8_000 * MS)) {
                long arrival = packet.arrival();
                for (long[] window : busy) {
                    if (arrival >= window[0] && arrival < window[1]) {
                        arrival = window[1];
                        break;
                    }
                }
                packets.add(new Packet(arrival, packet.x()));
            }
            packets.sort((a, b) -> Long.compare(a.arrival(), b.arrival()));
            Server server = new Server(packets);
            for (long[] window : busy) {
                long query = window[1];
                server.deliverUntil(query);
                if (query < 2_000 * MS || query > 7_000 * MS) {
                    continue;
                }
                double error = Math.abs(server.speed(query) - SPEED);
                sum += error * error;
                worst = Math.max(worst, error);
                samples++;
            }
        }
        double rms = Math.sqrt(sum / samples);
        assertTrue(rms < 0.2, "Streuung " + rms + " m/s");
        assertTrue(worst < SPEED * 0.5, "größte Abweichung " + worst + " m/s");
    }

    @Test
    void burstAfterAStallKeepsTheSpeed() {
        MotionTrack track = new MotionTrack();
        for (int i = 0; i <= 4; i++) {
            track.add(i * 50 * MS, SPEED * i * 0.05, 64.0, 0.0);
        }
        for (int i = 5; i <= 7; i++) {
            track.add(380 * MS, SPEED * i * 0.05, 64.0, 0.0);
        }
        double[] out = new double[3];
        assertTrue(track.estimate(390 * MS, out));
        assertTrue(out[0] > SPEED * 0.85 && out[0] < SPEED * 1.25, "nach dem Schub " + out[0]);
    }

    @Test
    void restingDroneReadsZero() {
        MotionTrack track = new MotionTrack();
        for (int i = 0; i < 20; i++) {
            track.add(i * 50 * MS, 10.0, 64.0, -3.0);
        }
        double[] out = {1.0, 1.0, 1.0};
        assertTrue(track.estimate(19 * 50 * MS + 20 * MS, out));
        assertEquals(0.0, out[0], 0.0);
        assertEquals(0.0, out[1], 0.0);
        assertEquals(0.0, out[2], 0.0);
    }

    @Test
    void staleTrackGivesNoEstimate() {
        MotionTrack track = new MotionTrack();
        track.add(0, 0.0, 64.0, 0.0);
        track.add(50 * MS, 0.28, 64.0, 0.0);
        double[] out = new double[3];
        assertTrue(track.estimate(50 * MS + MotionTrack.STALE_NANOS, out));
        assertEquals(SPEED, out[0], 1.0E-9);
        assertFalse(track.estimate(50 * MS + MotionTrack.STALE_NANOS + 1, out));
        assertTrue(track.fresh(50 * MS));
        assertFalse(track.fresh(50 * MS + MotionTrack.STALE_NANOS + 1));
    }

    @Test
    void needsTwoFixesWithSpan() {
        MotionTrack track = new MotionTrack();
        double[] out = new double[3];
        assertFalse(track.estimate(0, out));
        track.add(100 * MS, 1.0, 64.0, 0.0);
        assertFalse(track.estimate(100 * MS, out));
        track.add(101 * MS, 1.0056, 64.0, 0.0);
        assertEquals(1, track.size(), "gebündelte Pakete ersetzen das ältere");
        assertTrue(track.endsAt(1.0056, 64.0, 0.0));
        assertFalse(track.estimate(101 * MS, out), "1 ms Abstand ist keine Schätzung");
        track.add(145 * MS, 1.252, 64.0, 0.0);
        assertTrue(track.estimate(145 * MS, out));
        track.add(160 * MS, 1.336, 64.0, 0.0);
        assertTrue(track.estimate(160 * MS, out));
        assertEquals(5.6, out[0], 1.0E-6);
    }

    @Test
    void oldFixesBeyondTheWindowAreIgnored() {
        MotionTrack track = new MotionTrack();
        track.add(0, -100.0, 64.0, 0.0);
        for (int i = 0; i <= 10; i++) {
            track.add(1_000 * MS + i * 50 * MS, -2.0 * i * 0.05, 64.0, 0.0);
        }
        double[] out = new double[3];
        assertTrue(track.estimate(1_500 * MS, out));
        assertEquals(-2.0, out[0], 1.0E-9);
        long newest = track.newestNanos();
        assertEquals(1_500 * MS, newest);
        assertTrue(track.size() <= MotionTrack.CAPACITY);
    }

    @Test
    void discontinuityIsDetectedAtTheNewestFix() {
        MotionTrack track = new MotionTrack();
        assertFalse(track.endsAt(0.0, 0.0, 0.0));
        track.add(0, 1.0, 64.0, 2.0);
        assertTrue(track.endsAt(1.0, 64.0, 2.0));
        assertFalse(track.endsAt(1.0, 66.0, 2.0));
        track.add(10 * MS, Double.NaN, 64.0, 2.0);
        assertTrue(track.isEmpty());
    }

    @Test
    void burstsOfAClientCannotExceedTheSpeedRule() {
        MotionTrack track = new MotionTrack();
        track.add(0, 0.0, 64.0, 0.0);
        track.add(45 * MS, 9.0, 64.0, 0.0);
        double[] out = new double[3];
        assertTrue(track.estimate(45 * MS, out));
        assertEquals(200.0, out[0], 1.0E-9);
        MotionTrack.limit(out, 33.0);
        assertEquals(33.0, out[0], 1.0E-9);
        double[] diagonal = {30.0, 0.0, 40.0};
        MotionTrack.limit(diagonal, 10.0);
        assertEquals(6.0, diagonal[0], 1.0E-9);
        assertEquals(8.0, diagonal[2], 1.0E-9);
        double[] slow = {3.0, -1.0, 2.0};
        MotionTrack.limit(slow, Double.POSITIVE_INFINITY);
        assertEquals(3.0, slow[0], 0.0);
        MotionTrack.limit(slow, 33.0);
        assertEquals(-1.0, slow[1], 0.0);
        MotionTrack.limit(slow, 0.0);
        assertEquals(0.0, slow[0], 0.0);
        assertEquals(0.0, slow[2], 0.0);
    }

    @Test
    void verticalAndDiagonalSpeedsAreKept() {
        MotionTrack track = new MotionTrack();
        for (int i = 0; i <= 6; i++) {
            track.add(i * 50 * MS, 3.0 * i * 0.05, 64.0 - 4.0 * i * 0.05, 0.0);
        }
        double[] out = new double[3];
        assertTrue(track.estimate(300 * MS, out));
        assertEquals(3.0, out[0], 1.0E-9);
        assertEquals(-4.0, out[1], 1.0E-9);
        assertEquals(0.0, out[2], 0.0);
    }
}
