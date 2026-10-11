package de.lardtom.propwash.drone.trail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrailBufferTest {
    @Test
    void keepsOrderFromOldestToNewest() {
        TrailBuffer buffer = new TrailBuffer(4);
        for (int i = 0; i < 3; i++) {
            buffer.push(i, i, 10 + i, 20 + i, 1.0F);
        }
        assertEquals(3, buffer.size());
        assertEquals(0.0, buffer.x(0));
        assertEquals(2.0, buffer.x(buffer.newest()));
        assertEquals(22.0, buffer.z(2));
    }

    @Test
    void fullBufferDropsTheOldestSample() {
        TrailBuffer buffer = new TrailBuffer(4);
        for (int i = 0; i < 10; i++) {
            buffer.push(i, i, 0, 0, 100.0F);
        }
        assertEquals(4, buffer.size());
        assertEquals(6.0, buffer.x(0));
        assertEquals(9.0, buffer.x(3));
        assertEquals(9.0, buffer.time(3));
    }

    @Test
    void expiresByTheLifeOfEachSample() {
        TrailBuffer buffer = new TrailBuffer(8);
        buffer.push(0.0, 0, 0, 0, 1.0F);
        buffer.push(0.5, 1, 0, 0, 1.0F);
        buffer.push(1.0, 2, 0, 0, 2.0F);
        assertEquals(0, buffer.expire(0.99));
        assertEquals(1, buffer.expire(1.0));
        assertEquals(1.0, buffer.x(0));
        assertEquals(1, buffer.expire(1.6));
        assertEquals(1, buffer.size());
        assertEquals(1, buffer.expire(3.0));
        assertTrue(buffer.isEmpty());
        buffer.push(4.0, 5, 0, 0, 1.0F);
        assertEquals(5.0, buffer.x(0));
    }

    @Test
    void rejectsIndexesOutsideTheSamples() {
        TrailBuffer buffer = new TrailBuffer(3);
        buffer.push(0.0, 1, 2, 3, 1.0F);
        assertThrows(IndexOutOfBoundsException.class, () -> buffer.x(1));
        assertThrows(IllegalArgumentException.class, () -> new TrailBuffer(1));
    }
}
