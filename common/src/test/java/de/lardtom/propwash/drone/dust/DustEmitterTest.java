package de.lardtom.propwash.drone.dust;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DustEmitterTest {
    @Test
    void emitsTheRequestedRateOverTime() {
        DustEmitter emitter = new DustEmitter();
        int total = 0;
        for (int i = 0; i < 200; i++) {
            total += emitter.take(8.0, 0.05);
        }
        assertEquals(80, total);
    }

    @Test
    void slowRatesStillEmitEventually() {
        DustEmitter emitter = new DustEmitter();
        int total = 0;
        for (int i = 0; i < 40; i++) {
            total += emitter.take(1.0, 0.05);
        }
        assertEquals(2, total);
    }

    @Test
    void zeroRateResetsTheCarry() {
        DustEmitter emitter = new DustEmitter();
        emitter.take(9.0, 0.05);
        assertEquals(0, emitter.take(0.0, 0.05));
        assertEquals(DustEmitter.START_CARRY, emitter.carry());
        assertEquals(0, emitter.take(Double.NaN, 0.05));
        assertEquals(0, emitter.take(5.0, -1.0));
    }

    @Test
    void neverMoreThanTheTickLimit() {
        DustEmitter emitter = new DustEmitter();
        for (int i = 0; i < 20; i++) {
            int count = emitter.take(10_000.0, 0.05);
            assertTrue(count <= DustModel.MAX_PER_TICK && count >= 0);
        }
        assertTrue(emitter.carry() < 1.0);
    }
}
