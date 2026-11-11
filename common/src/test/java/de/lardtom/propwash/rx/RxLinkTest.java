package de.lardtom.propwash.rx;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RxLinkTest {
    private static final double EYE_Y = 65.62;

    private static final class Wall implements RxLink.Cells {
        private final Set<Long> unloaded = new HashSet<>();
        private final Set<Long> readUnloaded = new HashSet<>();
        private final int fromX;
        private final int toX;
        private int reads;

        Wall(int fromX, int toX) {
            this.fromX = fromX;
            this.toX = toX;
        }

        Wall unload(int chunkX, int chunkZ) {
            this.unloaded.add(key(chunkX, chunkZ));
            return this;
        }

        @Override
        public boolean loaded(int chunkX, int chunkZ) {
            return !this.unloaded.contains(key(chunkX, chunkZ));
        }

        @Override
        public boolean opaque(int x, int y, int z) {
            this.reads++;
            if (this.unloaded.contains(key(x >> 4, z >> 4))) {
                this.readUnloaded.add(key(x >> 4, z >> 4));
            }
            return x >= this.fromX && x <= this.toX;
        }

        private static long key(int x, int z) {
            return ((long) x << 32) | (z & 0xFFFFFFFFL);
        }
    }

    @Test
    void freeLineIsTheStraightDistance() {
        RxLink.Result result = RxLink.measure(0.5, EYE_Y, 0.5, 40, 65, 0, 64, new Wall(1000, 1000));
        assertEquals(0, result.blocks());
        assertEquals(result.distance(), result.effective(), 1.0E-9);
        assertEquals(Math.sqrt(40.0 * 40.0 + 0.12 * 0.12), result.distance(), 1.0E-6);
        assertTrue(result.within(64));
        assertFalse(result.within(39));
    }

    @Test
    void everyOpaqueBlockAddsHalfABlock() {
        RxLink.Result result = RxLink.measure(0.5, 65.5, 0.5, 40, 65, 0, 128, new Wall(10, 19));
        assertEquals(10, result.blocks());
        assertEquals(40.0 + 5.0, result.effective(), 1.0E-9);
        assertTrue(RxLink.inRange(0.5, 65.5, 0.5, 40, 65, 0, 45, new Wall(10, 19)));
        assertFalse(RxLink.inRange(0.5, 65.5, 0.5, 40, 65, 0, 44, new Wall(10, 19)));
    }

    @Test
    void targetAndEyeCellsAreNotCounted() {
        RxLink.Result result = RxLink.measure(0.5, 65.5, 0.5, 10, 65, 0, 64, new Wall(0, 10));
        assertEquals(9, result.blocks());
    }

    @Test
    void blocksAreCountedAtMostSixtyFourTimes() {
        RxLink.Result result = RxLink.measure(0.5, 65.5, 0.5, 80, 65, 0, 128, new Wall(1, 99));
        assertEquals(RxLink.MAX_COUNTED, result.blocks());
        assertEquals(80.0 + 32.0, result.effective(), 1.0E-9);
        assertTrue(result.within(128));
        RxLink.Result longer = RxLink.measure(0.5, 65.5, 0.5, 100, 65, 0, 128, new Wall(1, 99));
        assertTrue(longer.blocks() <= RxLink.MAX_COUNTED);
        assertFalse(longer.within(128));
    }

    @Test
    void rangeIsCappedAtOneHundredTwentyEight() {
        assertEquals(128, RxLink.clampRange(500));
        assertEquals(1, RxLink.clampRange(0));
        assertTrue(RxLink.validRange(1));
        assertTrue(RxLink.validRange(128));
        assertFalse(RxLink.validRange(129));
        assertFalse(RxLink.validRange(0));
        assertTrue(RxLink.inRange(0.5, 65.5, 0.5, 127, 65, 0, 128, new Wall(1000, 1000)));
        assertFalse(RxLink.inRange(0.5, 65.5, 0.5, 130, 65, 0, 128, new Wall(1000, 1000)));
        assertFalse(RxLink.inRange(0.5, 65.5, 0.5, 130, 65, 0, 999, new Wall(1000, 1000)));
    }

    @Test
    void tooFarDoesNotTraceAtAll() {
        Wall wall = new Wall(1, 200);
        RxLink.Result result = RxLink.measure(0.5, 65.5, 0.5, 200, 65, 0, 128, wall);
        assertFalse(result.within(128));
        assertEquals(0, wall.reads);
    }

    @Test
    void unloadedChunksCountWithoutDampingAndAreNeverRead() {
        Wall wall = new Wall(1, 60).unload(1, 0).unload(2, 0);
        RxLink.Result result = RxLink.measure(0.5, 65.5, 0.5, 60, 65, 0, 128, wall);
        assertTrue(wall.readUnloaded.isEmpty(), "Blöcke in nicht geladenen Chunks gelesen: " + wall.readUnloaded);
        assertEquals(15 + 12, result.blocks());
        assertEquals(60.0 + 0.5 * 27, result.effective(), 1.0E-9);
    }

    @Test
    void diagonalLinesStepThroughEveryCell() {
        RxLink.Result result = RxLink.measure(0.5, 65.5, 0.5, 20, 85, 20, 128, new Wall(-1000, 1000));
        assertTrue(result.blocks() >= 20 && result.blocks() <= RxLink.MAX_COUNTED, "Zellen auf der Diagonale: " + result.blocks());
        assertEquals(result.distance() + 0.5 * result.blocks(), result.effective(), 1.0E-9);
    }

    @Test
    void brokenPositionsAreOutOfRange() {
        assertFalse(RxLink.inRange(Double.NaN, 65.5, 0.5, 2, 65, 0, 64, new Wall(1000, 1000)));
        assertFalse(RxLink.measure(Double.POSITIVE_INFINITY, 0.0, 0.0, 0, 0, 0, 64, new Wall(0, 0)).within(128));
    }
}
