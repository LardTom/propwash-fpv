package de.lardtom.propwash.race;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GateSignalMapTest {
    @Test
    void sharedEmitterStaysOnUntilTheLastGateReleasesIt() {
        GateSignalMap map = new GateSignalMap();
        assertTrue(map.isEmpty());
        assertTrue(map.hold(42L, GateSignalTarget.UP), "erster Puls schaltet ein");
        assertFalse(map.hold(42L, GateSignalTarget.UP), "zweites Gate auf demselben Block: kein neuer Flankenwechsel");
        assertEquals(1 << GateSignalTarget.UP, map.mask(42L));
        assertFalse(map.release(42L, GateSignalTarget.UP));
        assertEquals(1 << GateSignalTarget.UP, map.mask(42L));
        assertTrue(map.release(42L, GateSignalTarget.UP), "letztes Gate schaltet aus");
        assertEquals(0, map.mask(42L));
        assertTrue(map.isEmpty());
        assertFalse(map.release(42L, GateSignalTarget.UP), "Freigeben ohne Halten ändert nichts");
    }

    @Test
    void facesOfOneEmitterAreIndependent() {
        GateSignalMap map = new GateSignalMap();
        map.hold(7L, GateSignalTarget.UP);
        map.hold(7L, GateSignalTarget.EAST);
        map.hold(8L, GateSignalTarget.DOWN);
        assertEquals((1 << GateSignalTarget.UP) | (1 << GateSignalTarget.EAST), map.mask(7L));
        assertEquals(2, map.size());
        assertEquals(1 << GateSignalTarget.DOWN, map.masks().get(8L));
        assertTrue(map.release(7L, GateSignalTarget.UP));
        assertEquals(1 << GateSignalTarget.EAST, map.mask(7L));
        assertTrue(map.release(7L, GateSignalTarget.EAST));
        assertEquals(1, map.size());
    }
}
