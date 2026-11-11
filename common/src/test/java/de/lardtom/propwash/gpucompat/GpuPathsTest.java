package de.lardtom.propwash.gpucompat;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuPathsTest {
    private static final GpuPaths.Result OK = GpuPaths.Result.OK;
    private static final GpuPaths.Result ZERO = GpuPaths.Result.ZERO;
    private static final GpuPaths.Result UNTESTED = GpuPaths.Result.UNTESTED;

    @Test
    void patternHasNoZeroByteAndDistinctPixels() {
        byte[] pattern = GpuPaths.pattern();
        assertEquals(GpuPaths.WIDTH * GpuPaths.HEIGHT * 4, pattern.length);
        Set<Integer> pixels = new HashSet<>();
        for (int i = 0; i < pattern.length; i += 4) {
            for (int c = 0; c < 4; c++) {
                assertTrue(pattern[i + c] != 0, "Byte " + (i + c));
            }
            assertEquals((byte) 255, pattern[i + 3]);
            pixels.add((pattern[i] & 0xFF) << 16 | (pattern[i + 1] & 0xFF) << 8 | pattern[i + 2] & 0xFF);
        }
        assertEquals(GpuPaths.WIDTH * GpuPaths.HEIGHT, pixels.size());
        assertTrue(Arrays.equals(pattern, GpuPaths.pattern()));
    }

    @Test
    void compareTellsExactZeroWrongAndMissing() {
        byte[] pattern = GpuPaths.pattern();
        assertEquals(OK, GpuPaths.compare(pattern, pattern.clone()));
        assertEquals(ZERO, GpuPaths.compare(pattern, new byte[pattern.length]));
        byte[] wrong = pattern.clone();
        wrong[5] ^= 1;
        assertEquals(GpuPaths.Result.WRONG, GpuPaths.compare(pattern, wrong));
        assertEquals(GpuPaths.Result.FAILED, GpuPaths.compare(pattern, null));
        assertEquals(GpuPaths.Result.FAILED, GpuPaths.compare(pattern, new byte[3]));
    }

    @Test
    void zeroChecks() {
        assertTrue(GpuPaths.zero(new byte[8]));
        assertFalse(GpuPaths.zero(new byte[]{0, 0, 1}));
        assertFalse(GpuPaths.zero(null));
        assertTrue(GpuPaths.rgbZero(new byte[]{0, 0, 0, (byte) 255, 0, 0, 0, (byte) 255}, 2));
        assertFalse(GpuPaths.rgbZero(new byte[]{0, 0, 0, (byte) 255, 0, 3, 0, (byte) 255}, 2));
        assertFalse(GpuPaths.rgbZero(new byte[0], 2));
    }

    @Test
    void readPathFollowsTheFirstWorkingWay() {
        assertEquals(GpuPaths.ReadPath.GPU, GpuPaths.Health.UNKNOWN.readPath());
        assertFalse(GpuPaths.Health.UNKNOWN.tested());
        assertEquals(GpuPaths.ReadPath.GPU, health(OK, OK, OK, OK).readPath());
        assertEquals(GpuPaths.ReadPath.GPU, health(OK, UNTESTED, OK, OK).readPath());
        assertEquals(GpuPaths.ReadPath.CLASSIC, health(ZERO, OK, OK, OK).readPath());
        assertEquals(GpuPaths.ReadPath.CLASSIC, health(GpuPaths.Result.FAILED, OK, UNTESTED, OK).readPath());
        assertEquals(GpuPaths.ReadPath.CLASSIC, health(ZERO, UNTESTED, UNTESTED, UNTESTED).readPath());
        assertEquals(GpuPaths.ReadPath.NONE, health(ZERO, ZERO, UNTESTED, UNTESTED).readPath());
        assertEquals(GpuPaths.ReadPath.NONE, health(GpuPaths.Result.WRONG, GpuPaths.Result.FAILED, UNTESTED, UNTESTED).readPath());
    }

    @Test
    void brokenBlitIsReportedButDoesNotChangeTheReadPath() {
        GpuPaths.Health health = health(OK, OK, ZERO, OK);
        assertTrue(health.anyBroken());
        assertFalse(health.blitWorks());
        assertEquals(GpuPaths.ReadPath.GPU, health.readPath());
        assertFalse(health(OK, OK, OK, OK).anyBroken());
        assertTrue(health(OK, OK, UNTESTED, UNTESTED).blitWorks());
    }

    @Test
    void logTextNamesEveryWay() {
        assertEquals("Rücklesen über die GPU nur Nullen, klassisch ok; Kopie per Blit nur Nullen, per Shader ok; Rücklesen über klassisch",
                health(ZERO, OK, ZERO, OK).describe());
        assertTrue(new GpuPaths.Health(GpuPaths.Result.FAILED, UNTESTED, UNTESTED, UNTESTED, "IllegalStateException: x").describe()
                .endsWith("(IllegalStateException: x)"));
    }

    private static GpuPaths.Health health(GpuPaths.Result gpu, GpuPaths.Result classic, GpuPaths.Result blit, GpuPaths.Result shader) {
        return new GpuPaths.Health(gpu, classic, blit, shader, null);
    }
}
