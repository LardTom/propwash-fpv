package de.lardtom.propwash.video;

import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoseHistoryTest {
    private static final long MS = 1_000_000L;

    private static float angleBetween(Quaternionf a, Quaternionf b) {
        float dot = Math.min(1.0F, Math.abs(a.dot(b)));
        return (float) (2.0 * Math.acos(dot));
    }

    @Test
    void emptyBufferReportsNothingAndKeepsOutputs() {
        PoseHistory history = new PoseHistory(8);
        Vector3d position = new Vector3d(7.0, 8.0, 9.0);
        Quaternionf orientation = new Quaternionf(0.0F, 0.0F, 1.0F, 0.0F);
        assertFalse(history.sample(123L, position, orientation));
        assertEquals(new Vector3d(7.0, 8.0, 9.0), position);
        assertEquals(new Quaternionf(0.0F, 0.0F, 1.0F, 0.0F), orientation);
    }

    @Test
    void singlePoseIsReturnedForAnyTime() {
        PoseHistory history = new PoseHistory(8);
        history.record(10 * MS, 1.0, 2.0, 3.0, new Quaternionf().rotationX(0.5F));
        Vector3d position = new Vector3d();
        Quaternionf orientation = new Quaternionf();
        assertTrue(history.sample(0L, position, orientation));
        assertEquals(1.0, position.x, 1.0E-12);
        assertTrue(history.sample(Long.MAX_VALUE, position, orientation));
        assertEquals(3.0, position.z, 1.0E-12);
        assertEquals(0.0F, angleBetween(orientation, new Quaternionf().rotationX(0.5F)), 1.0E-5F);
    }

    @Test
    void positionIsLinearlyInterpolated() {
        PoseHistory history = new PoseHistory(8);
        history.record(0L, 0.0, 10.0, -4.0, new Quaternionf());
        history.record(40 * MS, 8.0, 14.0, 4.0, new Quaternionf());
        Vector3d position = new Vector3d();
        Quaternionf orientation = new Quaternionf();
        history.sample(10 * MS, position, orientation);
        assertEquals(2.0, position.x, 1.0E-9);
        assertEquals(11.0, position.y, 1.0E-9);
        assertEquals(-2.0, position.z, 1.0E-9);
    }

    @Test
    void orientationIsSphericallyInterpolated() {
        PoseHistory history = new PoseHistory(8);
        history.record(0L, 0.0, 0.0, 0.0, new Quaternionf());
        history.record(100 * MS, 0.0, 0.0, 0.0, new Quaternionf().rotationY((float) (Math.PI / 2.0)));
        Vector3d position = new Vector3d();
        Quaternionf orientation = new Quaternionf();
        for (int percent = 0; percent <= 100; percent += 10) {
            history.sample(percent * MS, position, orientation);
            Quaternionf expected = new Quaternionf().rotationY((float) (Math.PI / 2.0 * percent / 100.0));
            assertEquals(1.0F, Math.abs(orientation.dot(expected)), 1.0E-6F, "at " + percent + " %");
            assertEquals(1.0F, orientation.lengthSquared(), 1.0E-5F);
        }
    }

    @Test
    void slerpTakesTheShortestPath() {
        PoseHistory history = new PoseHistory(8);
        Quaternionf a = new Quaternionf().rotationZ(0.2F);
        Quaternionf b = new Quaternionf().rotationZ(0.6F);
        b.set(-b.x, -b.y, -b.z, -b.w);
        history.record(0L, 0.0, 0.0, 0.0, a);
        history.record(2 * MS, 0.0, 0.0, 0.0, b);
        Vector3d position = new Vector3d();
        Quaternionf orientation = new Quaternionf();
        history.sample(MS, position, orientation);
        assertEquals(1.0F, Math.abs(orientation.dot(new Quaternionf().rotationZ(0.4F))), 1.0E-6F);
        assertTrue(orientation.dot(a) > 0.0F);
    }

    @Test
    void clampsBeforeOldestAndAfterNewest() {
        PoseHistory history = new PoseHistory(8);
        history.record(100L, 1.0, 0.0, 0.0, new Quaternionf());
        history.record(200L, 2.0, 0.0, 0.0, new Quaternionf());
        history.record(300L, 3.0, 0.0, 0.0, new Quaternionf());
        Vector3d position = new Vector3d();
        Quaternionf orientation = new Quaternionf();
        history.sample(50L, position, orientation);
        assertEquals(1.0, position.x, 1.0E-12);
        history.sample(900L, position, orientation);
        assertEquals(3.0, position.x, 1.0E-12);
        history.sample(250L, position, orientation);
        assertEquals(2.5, position.x, 1.0E-12);
    }

    @Test
    void olderOrEqualTimestampsAreIgnored() {
        PoseHistory history = new PoseHistory(8);
        assertTrue(history.record(100L, 1.0, 0.0, 0.0, new Quaternionf()));
        assertFalse(history.record(100L, 5.0, 0.0, 0.0, new Quaternionf()));
        assertFalse(history.record(50L, 5.0, 0.0, 0.0, new Quaternionf()));
        assertFalse(history.record(150L, Double.NaN, 0.0, 0.0, new Quaternionf()));
        assertEquals(1, history.size());
        assertEquals(100L, history.newestNanos());
    }

    @Test
    void recordCopiesAndNormalizesTheOrientation() {
        PoseHistory history = new PoseHistory(8);
        Quaternionf input = new Quaternionf(0.0F, 0.0F, 0.0F, 2.0F);
        history.record(1L, 0.0, 0.0, 0.0, input);
        input.set(1.0F, 0.0F, 0.0F, 0.0F);
        Vector3d position = new Vector3d();
        Quaternionf orientation = new Quaternionf();
        history.sample(1L, position, orientation);
        assertEquals(new Quaternionf(), orientation);
        history.record(2L, 0.0, 0.0, 0.0, new Quaternionf(0.0F, 0.0F, 0.0F, 0.0F));
        history.sample(2L, position, orientation);
        assertEquals(new Quaternionf(), orientation);
    }

    @Test
    void ringBufferOverwritesTheOldestPoses() {
        PoseHistory history = new PoseHistory(4);
        for (int i = 0; i < 10; i++) {
            history.record(i * 10L, i, 0.0, 0.0, new Quaternionf());
        }
        assertEquals(4, history.size());
        assertEquals(60L, history.oldestNanos());
        assertEquals(90L, history.newestNanos());
        Vector3d position = new Vector3d();
        Quaternionf orientation = new Quaternionf();
        history.sample(0L, position, orientation);
        assertEquals(6.0, position.x, 1.0E-12);
        history.sample(75L, position, orientation);
        assertEquals(7.5, position.x, 1.0E-12);
    }

    @Test
    void clearEmptiesTheBuffer() {
        PoseHistory history = new PoseHistory(4);
        history.record(1L, 0.0, 0.0, 0.0, new Quaternionf());
        history.clear();
        assertTrue(history.isEmpty());
        assertFalse(history.sample(1L, new Vector3d(), new Quaternionf()));
        assertTrue(history.record(0L, 0.0, 0.0, 0.0, new Quaternionf()));
    }

    @Test
    void cutSeparatesPosesBeforeAndAfterWithoutInterpolating() {
        PoseHistory history = new PoseHistory(16);
        Quaternionf level = new Quaternionf();
        Quaternionf turned = new Quaternionf().rotationY((float) Math.PI);
        history.record(0L, 0.0, 64.0, 0.0, level);
        history.record(10 * MS, 10.0, 64.0, 0.0, level);
        history.cut(15 * MS);
        history.record(20 * MS, 500.0, 80.0, -300.0, turned);
        history.record(30 * MS, 510.0, 80.0, -300.0, turned);
        Vector3d position = new Vector3d();
        Quaternionf orientation = new Quaternionf();
        assertTrue(history.sample(5 * MS, position, orientation));
        assertEquals(5.0, position.x, 1.0E-9);
        for (long t = 10 * MS; t < 15 * MS; t += MS / 4) {
            history.sample(t, position, orientation);
            assertEquals(10.0, position.x, 1.0E-9, "before the cut at " + t);
            assertEquals(1.0F, Math.abs(orientation.dot(level)), 1.0E-6F);
        }
        for (long t = 15 * MS; t <= 20 * MS; t += MS / 4) {
            history.sample(t, position, orientation);
            assertEquals(500.0, position.x, 1.0E-9, "after the cut at " + t);
            assertEquals(-300.0, position.z, 1.0E-9);
            assertEquals(1.0F, Math.abs(orientation.dot(turned)), 1.0E-6F);
        }
        history.sample(25 * MS, position, orientation);
        assertEquals(505.0, position.x, 1.0E-9);
    }

    @Test
    void samplesAfterAPendingCutReportNothingUntilTheNextPose() {
        PoseHistory history = new PoseHistory(8);
        history.record(0L, 1.0, 0.0, 0.0, new Quaternionf());
        history.record(10 * MS, 2.0, 0.0, 0.0, new Quaternionf());
        history.cut(20 * MS);
        assertTrue(history.cutPending());
        Vector3d position = new Vector3d(7.0, 7.0, 7.0);
        Quaternionf orientation = new Quaternionf();
        assertTrue(history.sample(15 * MS, position, orientation));
        assertEquals(2.0, position.x, 1.0E-12);
        position.set(7.0, 7.0, 7.0);
        assertFalse(history.sample(20 * MS, position, orientation));
        assertFalse(history.sample(Long.MAX_VALUE, position, orientation));
        assertEquals(new Vector3d(7.0, 7.0, 7.0), position);
        history.record(40 * MS, 9.0, 0.0, 0.0, new Quaternionf());
        assertFalse(history.cutPending());
        assertTrue(history.sample(25 * MS, position, orientation));
        assertEquals(9.0, position.x, 1.0E-12);
        history.sample(19 * MS, position, orientation);
        assertEquals(2.0, position.x, 1.0E-12);
    }

    @Test
    void cutAtOrBeforeTheNewestPoseStartsRightAfterIt() {
        PoseHistory history = new PoseHistory(8);
        history.record(0L, 0.0, 0.0, 0.0, new Quaternionf());
        history.record(10L, 10.0, 0.0, 0.0, new Quaternionf());
        history.cut(5L);
        Vector3d position = new Vector3d();
        Quaternionf orientation = new Quaternionf();
        assertTrue(history.sample(10L, position, orientation));
        assertEquals(10.0, position.x, 1.0E-12);
        assertFalse(history.sample(11L, position, orientation));
        history.record(20L, 100.0, 0.0, 0.0, new Quaternionf());
        history.sample(5L, position, orientation);
        assertEquals(5.0, position.x, 1.0E-12);
        history.sample(10L, position, orientation);
        assertEquals(10.0, position.x, 1.0E-12);
        history.sample(11L, position, orientation);
        assertEquals(100.0, position.x, 1.0E-12);
    }

    @Test
    void cutOnAnEmptyBufferOrAfterClearHasNoEffect() {
        PoseHistory history = new PoseHistory(8);
        history.cut(5L);
        assertFalse(history.cutPending());
        history.record(10L, 1.0, 0.0, 0.0, new Quaternionf());
        history.record(20L, 3.0, 0.0, 0.0, new Quaternionf());
        Vector3d position = new Vector3d();
        Quaternionf orientation = new Quaternionf();
        history.sample(15L, position, orientation);
        assertEquals(2.0, position.x, 1.0E-12);
        history.cut(30L);
        history.clear();
        assertFalse(history.cutPending());
        history.record(40L, 0.0, 0.0, 0.0, new Quaternionf());
        history.record(60L, 2.0, 0.0, 0.0, new Quaternionf());
        history.sample(50L, position, orientation);
        assertEquals(1.0, position.x, 1.0E-12);
    }

    @Test
    void secondCutBeforeTheNextPoseKeepsTheFirst() {
        PoseHistory history = new PoseHistory(8);
        history.record(0L, 0.0, 0.0, 0.0, new Quaternionf());
        history.cut(10L);
        history.cut(30L);
        history.record(40L, 100.0, 0.0, 0.0, new Quaternionf());
        Vector3d position = new Vector3d();
        Quaternionf orientation = new Quaternionf();
        history.sample(9L, position, orientation);
        assertEquals(0.0, position.x, 1.0E-12);
        history.sample(10L, position, orientation);
        assertEquals(100.0, position.x, 1.0E-12);
    }

    @Test
    void cutSurvivesTheRingBufferWrapping() {
        PoseHistory history = new PoseHistory(4);
        history.record(0L, 0.0, 0.0, 0.0, new Quaternionf());
        history.record(10L, 1.0, 0.0, 0.0, new Quaternionf());
        history.record(20L, 2.0, 0.0, 0.0, new Quaternionf());
        history.cut(25L);
        history.record(30L, 100.0, 0.0, 0.0, new Quaternionf());
        history.record(40L, 101.0, 0.0, 0.0, new Quaternionf());
        history.record(50L, 102.0, 0.0, 0.0, new Quaternionf());
        assertEquals(20L, history.oldestNanos());
        Vector3d position = new Vector3d();
        Quaternionf orientation = new Quaternionf();
        history.sample(24L, position, orientation);
        assertEquals(2.0, position.x, 1.0E-12);
        history.sample(25L, position, orientation);
        assertEquals(100.0, position.x, 1.0E-12);
        history.record(60L, 103.0, 0.0, 0.0, new Quaternionf());
        history.sample(0L, position, orientation);
        assertEquals(100.0, position.x, 1.0E-12);
        history.sample(35L, position, orientation);
        assertEquals(100.5, position.x, 1.0E-12);
    }
}
