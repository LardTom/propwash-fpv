package de.lardtom.propwash.guide;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulatedHoldTest {
    private static final List<String> SLOTS = List.of("result", "craft", "prop", "empty");

    @Test
    void onlyTheDevelopmentEnvironmentAcceptsASimulatedHold() {
        SimulatedHold hold = new SimulatedHold();
        assertFalse(hold.hold(2, false), "im Spiel ohne Wirkung");
        assertFalse(hold.active());
        assertEquals("craft", hold.slotOr(SLOTS, "craft"));
        assertFalse(hold.keyDown(false));
        assertTrue(hold.hold(2, true));
        assertTrue(hold.active());
        assertFalse(hold.hold(3, false), "abgelehnt ändert nichts");
        assertEquals("prop", hold.slotOr(SLOTS, "craft"));
    }

    @Test
    void theSimulatedSlotReplacesTheHoveredOneAndHoldsTheKey() {
        SimulatedHold hold = new SimulatedHold();
        assertEquals("craft", hold.slotOr(SLOTS, "craft"), "ohne Simulation gilt der Slot unter der Maus");
        assertFalse(hold.keyDown(false));
        assertTrue(hold.keyDown(true), "die echte Taste wirkt weiter");
        assertTrue(hold.hold(2, true));
        assertEquals("prop", hold.slotOr(SLOTS, "craft"));
        assertTrue(hold.keyDown(false));
        assertTrue(hold.hold(SLOTS.size(), true), "das offene Menü kann später andere Slots haben");
        assertNull(hold.slotOr(SLOTS, "craft"), "ein Slot, den das Menü nicht hat, wirkt nicht");
        assertTrue(hold.keyDown(false));
        assertFalse(hold.hold(-1, true));
        assertFalse(hold.hold(SimulatedHold.NONE, true));
        assertNull(hold.slotOr(SLOTS, "craft"), "ungültige Slots ändern das laufende Halten nicht");
        hold.release();
        assertFalse(hold.active());
        assertFalse(hold.keyDown(false));
        assertEquals("craft", hold.slotOr(SLOTS, "craft"));
    }

    @Test
    void aSimulatedHoldOpensOnceAfterTheHoldTimeLikeTheKey() {
        SimulatedHold hold = new SimulatedHold();
        HoldTracker tracker = new HoldTracker();
        assertTrue(hold.hold(2, true));
        int fired = 0;
        long firstFire = -1L;
        for (long now = 0L; now <= 3000L; now += 50L) {
            HoldTracker.Result result = tracker.update("propwash:prop@" + SLOTS.indexOf(hold.slotOr(SLOTS, "craft")), true, hold.keyDown(false), now);
            if (result.fire()) {
                fired++;
                firstFire = firstFire < 0L ? now : firstFire;
            }
        }
        assertEquals(1, fired, "solange gehalten, öffnet die Flugschule genau einmal");
        assertEquals(HoldTracker.HOLD_MILLIS, firstFire, 50L);
        hold.release();
        assertFalse(tracker.update("propwash:prop@2", true, hold.keyDown(false), 3050L).fire());
        assertTrue(hold.hold(2, true));
        tracker.reset();
        boolean again = false;
        for (long now = 3100L; now <= 3100L + HoldTracker.HOLD_MILLIS; now += 50L) {
            again |= tracker.update("propwash:prop@2", true, hold.keyDown(false), now).fire();
        }
        assertTrue(again, "neues Halten nach dem Loslassen öffnet wieder");
    }
}
