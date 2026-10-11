package de.lardtom.propwash.drone.bind;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BindAccessTest {
    private static final UUID ME = new UUID(1L, 1L);
    private static final UUID FRIEND = new UUID(2L, 2L);
    @Test
    void legacyDronesBehaveAsBefore() {
        BindAccess access = BindAccess.of(null, ME);
        assertEquals(BindAccess.UNBOUND, access);
        assertTrue(access.connects());
        assertFalse(access.matches());
        assertFalse(access.resettable());
    }

    @Test
    void lastPilotConnects() {
        BindAccess access = BindAccess.of(ReceiverBind.of(ME), ME);
        assertEquals(BindAccess.PILOT, access);
        assertTrue(access.connects() && access.matches() && access.resettable());
    }

    @Test
    void otherPilotsNeedToClickTheDrone() {
        BindAccess access = BindAccess.of(ReceiverBind.of(FRIEND), ME);
        assertEquals(BindAccess.OTHER, access);
        assertFalse(access.connects());
        assertFalse(access.matches());
        assertTrue(access.resettable());
    }

    @Test
    void clearedNeedsAFreshBind() {
        BindAccess access = BindAccess.of(ReceiverBind.CLEARED, ME);
        assertEquals(BindAccess.CLEARED, access);
        assertFalse(access.connects());
        assertFalse(access.resettable());
    }

    @Test
    void noAccessLevelBesidesPilotMatches() {
        for (BindAccess access : BindAccess.values()) {
            assertEquals(access == BindAccess.PILOT, access.matches(), access.name());
        }
        assertEquals(4, BindAccess.values().length);
    }

    @Test
    void ordinalsRoundTrip() {
        assertEquals(BindAccess.UNBOUND, BindAccess.byOrdinal(-1));
        assertEquals(BindAccess.UNBOUND, BindAccess.byOrdinal(BindAccess.values().length));
        for (BindAccess access : BindAccess.values()) {
            assertEquals(access, BindAccess.byOrdinal(access.ordinal()));
        }
    }
}
