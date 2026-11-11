package de.lardtom.propwash.race;

import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.InflaterInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GhostLeadTest {
    private static final long MS = 1_000_000L;
    private static final String OLD_V3 = "78da0b08773764662b4a4c4e8d37656060606760142828ca2f284f2cceb04a2f4a2ccb2ca93cc1d8f0745679c3c3d7e26c0e2a0c60e01000a10f70"
            + "3040051830c13f792c820c00d82f12e5";

    private static void fly(GhostRecorder recorder, long from, long to, double speed) {
        Quaternionf level = new Quaternionf();
        for (long t = from; t <= to; t += 10 * MS) {
            recorder.sample(t, t / 1.0E9 * speed, 64.0, 0.0, level);
        }
    }

    private static double xAt(GhostTrack track, long time) {
        double[] position = new double[3];
        assertTrue(track.pose(time, position, new Quaternionf()), "keine Pose bei " + time / MS + " ms");
        return position[0];
    }

    @Test
    void secondLapStartsWithTheLastSecondOfTheFirst() {
        GhostRecorder recorder = new GhostRecorder();
        recorder.onLapStart(1, 0L);
        fly(recorder, 0L, 3_000L * MS, 10.0);
        GhostTrack first = recorder.onLapComplete(1_000L * MS, 1_000L * MS, "", 0);
        GhostTrack second = recorder.onLapComplete(1_500L * MS, 2_500L * MS, "", 0);
        assertNotNull(first);
        assertNotNull(second);
        assertFalse(first.pose(-GhostTrack.INTERVAL_NANOS, new double[3], new Quaternionf()));
        assertEquals(0.0, xAt(second, -1_000L * MS), 1.0E-9);
        assertEquals(5.0, xAt(second, -500L * MS), 1.0E-9);
        assertEquals(10.0, xAt(second, 0L), 1.0E-9);
        assertEquals(25.0, xAt(second, 1_500L * MS), 1.0E-9);
        assertEquals(1_500L * MS, second.durationNanos());
        assertFalse(second.pose(-1_001L * MS, new double[3], new Quaternionf()));
        assertEquals(0L, first.leadNanos());
        assertEquals(GhostRecorder.LEAD_NANOS, second.leadNanos());
        assertEquals(20, second.leadSamples());
        assertEquals(51, second.samples());
        assertEquals(-1_000L * MS, second.beginNanos());
    }

    @Test
    void firstLapUsesTheIdleHistory() {
        GhostRecorder recorder = new GhostRecorder();
        fly(recorder, 0L, 2_000L * MS, 10.0);
        recorder.onLapStart(1, 2_000L * MS);
        fly(recorder, 2_010L * MS, 4_000L * MS, 10.0);
        GhostTrack track = recorder.onLapComplete(2_000L * MS, 4_000L * MS, "", 0);
        assertNotNull(track);
        assertEquals(10.0, xAt(track, -1_000L * MS), 1.0E-9);
        assertEquals(20.0, xAt(track, 0L), 1.0E-9);
        assertEquals(40.0, xAt(track, 2_000L * MS), 1.0E-9);
    }

    @Test
    void shortHistoryGivesAShorterLead() {
        GhostRecorder recorder = new GhostRecorder();
        fly(recorder, 0L, 400L * MS, 10.0);
        recorder.onLapStart(1, 400L * MS);
        fly(recorder, 410L * MS, 2_400L * MS, 10.0);
        GhostTrack track = recorder.onLapComplete(2_000L * MS, 2_400L * MS, "", 0);
        assertNotNull(track);
        assertEquals(0.0, xAt(track, -400L * MS), 1.0E-9);
        assertEquals(4.0, xAt(track, 0L), 1.0E-9);
        assertFalse(track.pose(-450L * MS, new double[3], new Quaternionf()));
        assertEquals(400L * MS, track.leadNanos());
    }

    @Test
    void leadSurvivesTheCodec() throws IOException {
        GhostRecorder recorder = new GhostRecorder();
        recorder.onLapStart(1, 0L);
        fly(recorder, 0L, 3_000L * MS, 10.0);
        recorder.onLapComplete(1_000L * MS, 1_000L * MS, "race_5", 7);
        GhostTrack second = recorder.onLapComplete(1_500L * MS, 2_500L * MS, "race_5", 7);
        assertNotNull(second);
        GhostTrack decoded = GhostCodec.decode(GhostCodec.encode(second));
        assertEquals(second.samples(), decoded.samples());
        assertEquals(5.0, xAt(decoded, -500L * MS), 0.5 / GhostCodec.POSITION_STEPS);
        assertEquals(10.0, xAt(decoded, 0L), 0.5 / GhostCodec.POSITION_STEPS);
        assertEquals(1_500L * MS, decoded.lapNanos());
        assertEquals(second.leadNanos(), decoded.leadNanos());
        assertEquals(GhostCodec.VERSION_WITH_LEAD, version(GhostCodec.encode(second)));
    }

    @Test
    void standingStartOnlyKeepsTheTakeOff() {
        GhostRecorder recorder = new GhostRecorder();
        fly(recorder, 0L, 500L * MS, 10.0);
        recorder.sample(5_000L * MS, 5.0, 64.0, 0.0, new Quaternionf());
        Quaternionf level = new Quaternionf();
        for (long t = 5_010L * MS; t <= 5_200L * MS; t += 10 * MS) {
            recorder.sample(t, 5.0 + (t - 5_000L * MS) / 1.0E9 * 10.0, 64.0, 0.0, level);
        }
        recorder.onLapStart(1, 5_200L * MS);
        for (long t = 5_210L * MS; t <= 7_200L * MS; t += 10 * MS) {
            recorder.sample(t, 5.0 + (t - 5_000L * MS) / 1.0E9 * 10.0, 64.0, 0.0, level);
        }
        GhostTrack track = recorder.onLapComplete(2_000L * MS, 7_200L * MS, "", 0);
        assertNotNull(track);
        assertEquals(5.0, xAt(track, -200L * MS), 1.0E-9);
        assertFalse(track.pose(-250L * MS, new double[3], new Quaternionf()));
        assertEquals(200L * MS, track.leadNanos());
    }

    @Test
    void jumpBeforeTheStartCutsTheLead() {
        GhostRecorder recorder = new GhostRecorder();
        Quaternionf level = new Quaternionf();
        for (long t = 0L; t <= 1_000L * MS; t += 10 * MS) {
            recorder.sample(t, 500.0 + t / 1.0E9 * 10.0, 64.0, 0.0, level);
        }
        fly(recorder, 1_010L * MS, 1_500L * MS, 10.0);
        recorder.onLapStart(1, 1_500L * MS);
        fly(recorder, 1_510L * MS, 3_500L * MS, 10.0);
        GhostTrack track = recorder.onLapComplete(2_000L * MS, 3_500L * MS, "", 0);
        assertNotNull(track);
        assertEquals(10.5, xAt(track, -450L * MS), 1.0E-9);
        assertFalse(track.pose(-500L * MS, new double[3], new Quaternionf()));
        assertEquals(450L * MS, track.leadNanos());
    }

    @Test
    void lapWithoutHistoryStartsAtTheGate() {
        GhostRecorder recorder = new GhostRecorder();
        recorder.onLapStart(1, 0L);
        fly(recorder, 0L, 2_000L * MS, 10.0);
        GhostTrack track = recorder.onLapComplete(2_000L * MS, 2_000L * MS, "", 0);
        assertNotNull(track);
        assertEquals(0.0, track.x(0), 1.0E-9);
        assertEquals(41, track.samples());
        assertFalse(track.pose(-1L, new double[3], new Quaternionf()));
        assertEquals(0L, track.leadNanos());
        assertEquals(GhostCodec.VERSION, version(GhostCodec.encode(track)));
    }

    private static int version(byte[] data) {
        try (DataInputStream in = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(data)))) {
            assertEquals(GhostCodec.MAGIC, in.readInt());
            return in.readUnsignedByte();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void oldDataWithoutLeadPlaysFromTheGate() throws IOException {
        byte[] old = HexFormat.of().parseHex(OLD_V3);
        assertEquals(GhostCodec.VERSION_WITH_RULES, version(old));
        GhostTrack track = GhostCodec.decode(old);
        assertEquals(0L, track.leadNanos());
        assertEquals(0L, track.beginNanos());
        assertEquals(6, track.samples());
        assertEquals(250L * MS, track.durationNanos());
        assertEquals(10.0, xAt(track, 0L), 1.0E-9);
        assertFalse(track.pose(-1L, new double[3], new Quaternionf()));
        assertFalse(track.smoothPose(-1L, new double[3], new Quaternionf()));
        assertArrayEquals(old, GhostCodec.encode(track));
    }

    @Test
    void replayClockStaysAtZeroUntilTheGate() {
        GhostRecorder recorder = new GhostRecorder();
        recorder.onLapStart(1, 0L);
        fly(recorder, 0L, 3_000L * MS, 10.0);
        recorder.onLapComplete(1_000L * MS, 1_000L * MS, "", 0);
        GhostTrack track = recorder.onLapComplete(1_500L * MS, 2_500L * MS, "", 0);
        assertNotNull(track);
        assertEquals(-1_000L * MS, track.clamp(Long.MIN_VALUE));
        assertEquals(-1_000L * MS, track.clamp(-5_000L * MS));
        assertEquals(1_500L * MS, track.clamp(Long.MAX_VALUE));
        assertEquals(0L, track.lapClock(track.beginNanos()));
        assertEquals(0L, track.lapClock(-1L));
        assertEquals(0L, track.lapClock(0L));
        assertEquals(1L, track.lapClock(1L));
        assertEquals(700L * MS, track.lapClock(700L * MS));
        assertEquals(1_500L * MS, track.lapClock(track.durationNanos()));
        double[] position = new double[3];
        assertTrue(track.smoothPose(track.beginNanos(), position, new Quaternionf()));
        assertEquals(0.0, position[0], 1.0E-9);
        assertFalse(track.smoothPose(track.beginNanos() - 1L, position, new Quaternionf()));
    }

    @Test
    void serverCutKeepsTheLeadOnTheFineGrid() {
        GhostRecorder recorder = new GhostRecorder();
        Quaternionf level = new Quaternionf();
        for (long t = 0L; t <= 3_000L * MS; t += 50 * MS) {
            recorder.sample(t, t / 1.0E9 * 20.0, 64.0, 0.0, level);
        }
        recorder.onLapStart(1, 3_000L * MS - 8_000L * MS);
        for (long t = 3_050L * MS; t <= 13_100L * MS; t += 50 * MS) {
            recorder.sample(t, t / 1.0E9 * 20.0, 64.0, 0.0, level);
        }
        GhostTrack plain = recorder.cut(3_000L * MS, 13_000L * MS, 10_000L * MS, "race_5", 3, GhostServer.FINE_INTERVAL_NANOS);
        GhostTrack lead = recorder.cut(3_000L * MS, 13_000L * MS, 10_000L * MS, "race_5", 3, GhostServer.FINE_INTERVAL_NANOS,
                GhostRecorder.LEAD_NANOS);
        assertNotNull(plain);
        assertNotNull(lead);
        assertEquals(0L, plain.leadNanos());
        assertEquals(GhostRecorder.LEAD_NANOS, lead.leadNanos());
        assertEquals(GhostServer.FINE_INTERVAL_NANOS, lead.intervalNanos());
        assertEquals(plain.samples() + 50, lead.samples());
        assertEquals(plain.durationNanos(), lead.durationNanos());
        for (int i = 0; i < plain.samples(); i++) {
            assertEquals(plain.x(i), lead.x(i + lead.leadSamples()), 1.0E-9);
        }
        assertEquals(40.0, xAt(lead, -1_000L * MS), 0.05);
        assertArrayEquals(GhostCodec.encode(plain), GhostCodec.encode(lead.withoutLead()));
        recorder.trim(13_000L * MS - GhostRecorder.LEAD_NANOS);
        for (long t = 13_150L * MS; t <= 20_100L * MS; t += 50 * MS) {
            recorder.sample(t, t / 1.0E9 * 20.0, 64.0, 0.0, level);
        }
        GhostTrack next = recorder.cut(13_000L * MS, 20_000L * MS, 7_000L * MS, "race_5", 3, GhostServer.FINE_INTERVAL_NANOS,
                GhostRecorder.LEAD_NANOS);
        assertNotNull(next);
        assertEquals(GhostRecorder.LEAD_NANOS, next.leadNanos());
        assertEquals(240.0, xAt(next, -1_000L * MS), 0.05);
    }

    @Test
    void splitsAndOpponentPosesIgnoreTheLead() {
        List<GateGeometry> gates = List.of(GateGeometry.of(0.0, 64.0, 0.0, 0.0F, GateSize.MEDIUM), GateGeometry.of(0.0, 64.0, 10.0, 0.0F, GateSize.MEDIUM));
        GhostRecorder recorder = new GhostRecorder();
        Quaternionf level = new Quaternionf();
        for (long t = 0L; t <= 1_000L * MS; t += 10 * MS) {
            recorder.sample(t, 0.0, 65.5, -10.0 + t / 1.0E9 * 10.0, level);
        }
        recorder.onLapStart(1, 1_000L * MS);
        for (long t = 1_010L * MS; t <= 3_000L * MS; t += 10 * MS) {
            recorder.sample(t, 0.0, 65.5, -10.0 + t / 1.0E9 * 10.0, level);
        }
        GhostTrack track = recorder.onLapComplete(2_000L * MS, 3_000L * MS, "", 0);
        assertNotNull(track);
        assertEquals(GhostRecorder.LEAD_NANOS, track.leadNanos());
        long[] splits = track.splits(gates);
        assertEquals(2_000L * MS, splits[0]);
        assertEquals(1_000L * MS, splits[1], 2 * MS);
        assertArrayEquals(track.withoutLead().splits(gates), splits);
        double[] position = new double[3];
        assertTrue(track.pose(0L, position, new Quaternionf()));
        assertEquals(0.0, position[2], 1.0E-9);
        double[] plain = new double[3];
        assertTrue(track.withoutLead().pose(800L * MS, plain, new Quaternionf()));
        assertTrue(track.pose(800L * MS, position, new Quaternionf()));
        assertArrayEquals(plain, position, 1.0E-12);
    }

    @Test
    void limitsShortenTheLead() {
        long interval = GhostTrack.INTERVAL_NANOS;
        long lap = GhostTrack.MAX_DURATION_NANOS - 300L * MS;
        long[] times = {-2_000L * MS, lap + 1_000L * MS};
        double[] positions = {0.0, 64.0, 0.0, 100.0, 64.0, 0.0};
        float[] rotations = {0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F};
        GhostTrack track = GhostTrack.resample(times, positions, rotations, 2, 0L, lap, lap, "", 0, interval, GhostRecorder.LEAD_NANOS);
        assertNotNull(track);
        assertEquals(GhostTrack.MAX_SAMPLES, track.samples());
        assertEquals(300L * MS, track.leadNanos());
        assertEquals(lap, track.durationNanos());
        GhostTrack full = GhostTrack.resample(times, positions, rotations, 2, 0L, GhostTrack.MAX_DURATION_NANOS + 5_000L * MS, lap, "", 0, interval,
                GhostRecorder.LEAD_NANOS);
        assertNotNull(full);
        assertEquals(0L, full.leadNanos());
        assertEquals(GhostTrack.MAX_SAMPLES, full.samples());
    }

    @Test
    void invalidLeadIsRejected() {
        double[] positions = new double[3 * 4];
        float[] rotations = new float[4 * 4];
        for (int i = 0; i < 4; i++) {
            rotations[i * 4 + 3] = 1.0F;
        }
        long interval = GhostTrack.INTERVAL_NANOS;
        assertThrows(IllegalArgumentException.class, () -> new GhostTrack(interval, positions, rotations, interval, "", 0, null, null, -interval));
        assertThrows(IllegalArgumentException.class, () -> new GhostTrack(interval, positions, rotations, interval, "", 0, null, null, interval / 2));
        assertThrows(IllegalArgumentException.class, () -> new GhostTrack(interval, positions, rotations, interval, "", 0, null, null, 3 * interval));
        GhostTrack track = new GhostTrack(interval, positions, rotations, interval, "", 0, null, null, 2 * interval);
        assertEquals(2, track.leadSamples());
        assertEquals(2, track.withoutLead().samples());
    }
}
