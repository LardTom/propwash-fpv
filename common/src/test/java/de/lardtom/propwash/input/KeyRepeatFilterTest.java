package de.lardtom.propwash.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyRepeatFilterTest {
    @Test
    void shortTapsCountOnceEach() {
        KeyRepeatFilter key = new KeyRepeatFilter();
        assertTrue(key.pressed(1, false));
        assertFalse(key.pressed(0, false));
        assertTrue(key.pressed(1, false));
        assertTrue(key.pressed(1, true));
        assertFalse(key.pressed(0, false));
        assertTrue(key.pressed(1, false));
    }

    @Test
    void osKeyRepeatWhileHeldIsIgnored() {
        KeyRepeatFilter key = new KeyRepeatFilter();
        int presses = 0;
        for (int tick = 0; tick < 40; tick++) {
            int clicks = tick == 0 ? 1 : tick >= 5 ? 2 : 0;
            presses += key.pressed(clicks, true) ? 1 : 0;
        }
        presses += key.pressed(1, false) ? 1 : 0;
        assertEquals(1, presses);
        assertTrue(key.pressed(1, true));
    }

    @Test
    void lagSpikeWithPressRepeatsAndReleaseCountsOnce() {
        KeyRepeatFilter key = new KeyRepeatFilter();
        assertTrue(key.pressed(4, false));
        assertFalse(key.held());
        assertTrue(key.pressed(3, true));
        assertTrue(key.held());
    }

    @Test
    void keyHeldBeforeTheSessionDoesNotFire() {
        KeyRepeatFilter key = new KeyRepeatFilter();
        key.sync(true);
        assertFalse(key.pressed(2, true));
        assertFalse(key.pressed(0, false));
        assertTrue(key.pressed(1, true));
    }

    @Test
    void keyStillDownWhenAScreenClosesDoesNotFire() {
        KeyRepeatFilter key = new KeyRepeatFilter();
        assertFalse(key.pressed(0, true));
        assertFalse(key.pressed(1, true));
        assertFalse(key.pressed(0, false));
        assertTrue(key.pressed(1, false));
    }
}
