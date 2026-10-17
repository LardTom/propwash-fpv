package de.lardtom.propwash.accessory;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionGateTest {
    @Test
    void burstThenRefillPerTick() {
        ActionGate gate = new ActionGate();
        UUID player = UUID.randomUUID();
        for (int i = 0; i < ActionGate.BURST; i++) {
            assertTrue(gate.allow(player, 100L), "Stoß " + i);
        }
        assertFalse(gate.allow(player, 100L));
        assertTrue(gate.allow(player, 101L));
        assertFalse(gate.allow(player, 101L));
        assertTrue(gate.allow(UUID.randomUUID(), 101L), "andere Spieler sind getrennt");
        gate.forget(player);
        assertTrue(gate.allow(player, 101L));
        assertTrue(gate.allow(player, 50L), "Uhr rückwärts setzt zurück");
    }
}
