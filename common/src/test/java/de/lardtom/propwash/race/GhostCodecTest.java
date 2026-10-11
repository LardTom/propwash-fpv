package de.lardtom.propwash.race;

import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GhostCodecTest {
    private static final long MS = 1_000_000L;

    private static GhostTrack circuit(int samples) {
        double[] positions = new double[samples * 3];
        float[] rotations = new float[samples * 4];
        Quaternionf rotation = new Quaternionf();
        for (int i = 0; i < samples; i++) {
            double t = i * 0.05;
            positions[i * 3] = 1200.25 + 40.0 * Math.sin(t * 0.4);
            positions[i * 3 + 1] = 72.0 + 3.0 * Math.sin(t * 1.3);
            positions[i * 3 + 2] = -845.5 + 25.0 * Math.cos(t * 0.4);
            rotation.identity().rotateY((float) (t * 0.4)).rotateX((float) (0.3 * Math.sin(t))).rotateZ((float) (0.5 * Math.cos(t * 0.7)));
            rotations[i * 4] = rotation.x;
            rotations[i * 4 + 1] = rotation.y;
            rotations[i * 4 + 2] = rotation.z;
            rotations[i * 4 + 3] = rotation.w;
        }
        return new GhostTrack(GhostTrack.INTERVAL_NANOS, positions, rotations, (samples - 1) * GhostTrack.INTERVAL_NANOS + 3 * MS, "freestyle_5", 1234);
    }

    @Test
    void roundTripKeepsPoseWithinQuantisation() throws IOException {
        GhostTrack track = circuit(1201);
        GhostTrack decoded = GhostCodec.decode(GhostCodec.encode(track));
        assertEquals(track.samples(), decoded.samples());
        assertEquals(track.lapNanos(), decoded.lapNanos());
        assertEquals(track.intervalNanos(), decoded.intervalNanos());
        assertEquals("freestyle_5", decoded.frame());
        assertEquals(1234, decoded.layout());
        double maxError = 0.0;
        double maxAngle = 0.0;
        Quaternionf a = new Quaternionf();
        Quaternionf b = new Quaternionf();
        for (int i = 0; i < track.samples(); i++) {
            maxError = Math.max(maxError, Math.abs(track.x(i) - decoded.x(i)));
            maxError = Math.max(maxError, Math.abs(track.y(i) - decoded.y(i)));
            maxError = Math.max(maxError, Math.abs(track.z(i) - decoded.z(i)));
            a.set(track.rotation(i, 0), track.rotation(i, 1), track.rotation(i, 2), track.rotation(i, 3));
            b.set(decoded.rotation(i, 0), decoded.rotation(i, 1), decoded.rotation(i, 2), decoded.rotation(i, 3));
            double dot = Math.min(1.0, Math.abs(a.dot(b)));
            maxAngle = Math.max(maxAngle, Math.toDegrees(2.0 * Math.acos(dot)));
        }
        assertTrue(maxError <= 0.5 / GhostCodec.POSITION_STEPS + 1.0E-9, "Positionsfehler " + maxError);
        assertTrue(maxAngle < 0.2, "Winkelfehler " + maxAngle);
    }

    @Test
    void encodingIsCompact() {
        GhostTrack track = circuit(1201);
        byte[] data = GhostCodec.encode(track);
        double perSample = data.length / (double) track.samples();
        assertTrue(perSample < 8.0, "Bytes je Probe: " + perSample);
        assertTrue(data.length < 12_000, "Bytes: " + data.length);
    }

    @Test
    void corruptOrTruncatedDataIsRejected() {
        byte[] data = GhostCodec.encode(circuit(200));
        assertThrows(IOException.class, () -> GhostCodec.decode(Arrays.copyOf(data, data.length / 2)));
        byte[] garbage = data.clone();
        for (int i = 2; i < garbage.length; i += 3) {
            garbage[i] ^= 0x5A;
        }
        assertThrows(IOException.class, () -> GhostCodec.decode(garbage));
        assertThrows(IOException.class, () -> GhostCodec.decode(new byte[0]));
    }

    @Test
    void zigzagRoundTrips() {
        for (long value : new long[]{0L, 1L, -1L, 63L, -64L, Long.MAX_VALUE, Long.MIN_VALUE}) {
            assertEquals(value, GhostCodec.unzigzag(GhostCodec.zigzag(value)));
        }
    }

    @Test
    void splitsFollowTheGatesOfTheTrack() {
        int samples = 81;
        double[] positions = new double[samples * 3];
        float[] rotations = new float[samples * 4];
        for (int i = 0; i < samples; i++) {
            positions[i * 3 + 1] = 65.5;
            positions[i * 3 + 2] = i * 0.5 - 0.25;
            rotations[i * 4 + 3] = 1.0F;
        }
        GhostTrack track = new GhostTrack(GhostTrack.INTERVAL_NANOS, positions, rotations, 4_000L * MS, "", 0);
        List<GateGeometry> gates = List.of(
                GateGeometry.of(0.0, 64.0, 0.0, 0.0F, GateSize.MEDIUM),
                GateGeometry.of(0.0, 64.0, 10.0, 0.0F, GateSize.MEDIUM),
                GateGeometry.of(0.0, 64.0, 20.0, 0.0F, GateSize.MEDIUM));
        long[] splits = track.splits(gates);
        assertEquals(4_000L * MS, splits[0]);
        assertEquals(1_025L * MS, splits[1], 2 * MS);
        assertEquals(2_025L * MS, splits[2], 2 * MS);
        assertTrue(GhostTrack.completeSplits(splits));
        long[] missing = track.splits(List.of(gates.get(0), GateGeometry.of(50.0, 64.0, 10.0, 0.0F, GateSize.MEDIUM)));
        assertEquals(LapTracker.NONE, missing[1]);
        assertArrayEquals(new long[]{4_000L * MS, LapTracker.NONE}, missing);
    }
}
