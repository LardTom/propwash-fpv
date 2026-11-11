package de.lardtom.propwash.race;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandingStartTest {
    private static final int DRONE = 42;

    @Test
    void sessionStartingWithArmedDroneIsNoEdge() {
        ArmEdge edge = new ArmEdge();
        assertFalse(edge.update(DRONE, true));
        assertFalse(edge.update(DRONE, true));
        assertFalse(edge.update(DRONE, false));
        assertTrue(edge.update(DRONE, true));
    }

    @Test
    void realArmingIsAnEdgeOnce() {
        ArmEdge edge = new ArmEdge();
        assertFalse(edge.update(DRONE, false));
        assertTrue(edge.update(DRONE, true));
        assertFalse(edge.update(DRONE, true));
    }

    @Test
    void staleArmedFramesAfterResetAreNoEdge() {
        ArmEdge edge = new ArmEdge();
        assertFalse(edge.update(DRONE, false));
        assertTrue(edge.update(DRONE, true));
        edge.afterReset();
        assertFalse(edge.update(DRONE, true));
        assertFalse(edge.update(DRONE, true));
        assertFalse(edge.update(DRONE, false));
        assertTrue(edge.update(DRONE, true));
    }

    @Test
    void resetWhileDisarmedNeedsADisarmedFrameFirst() {
        ArmEdge edge = new ArmEdge();
        assertFalse(edge.update(DRONE, false));
        edge.afterReset();
        assertFalse(edge.update(DRONE, false));
        assertTrue(edge.update(DRONE, true));
    }

    @Test
    void otherDroneStartsFresh() {
        ArmEdge edge = new ArmEdge();
        assertFalse(edge.update(DRONE, false));
        assertFalse(edge.update(DRONE + 1, true));
        assertFalse(edge.update(DRONE + 1, true));
        edge.clear();
        assertFalse(edge.update(DRONE + 1, true));
    }

    @Test
    void standingStartOnlyWithSettingOrOpenLobbyNearStart() {
        assertFalse(StandingStart.request(true, false, false, false, false, true));
        assertTrue(StandingStart.request(true, true, false, false, false, true));
        assertTrue(StandingStart.request(true, false, true, false, false, true));
        assertFalse(StandingStart.request(false, true, true, false, false, true));
        assertFalse(StandingStart.request(true, true, false, true, false, true));
        assertFalse(StandingStart.request(true, true, false, false, true, true));
        assertFalse(StandingStart.request(true, true, false, false, false, false));
    }

    @Test
    void returningHomeArmedNeverStartsWithoutTheSetting() {
        ArmEdge edge = new ArmEdge();
        edge.update(DRONE, false);
        edge.update(DRONE, true);
        edge.afterReset();
        for (int frame = 0; frame < 120; frame++) {
            boolean armed = frame < 100;
            boolean rising = edge.update(DRONE, armed);
            assertFalse(StandingStart.request(rising, false, false, false, false, true));
        }
        assertTrue(StandingStart.request(edge.update(DRONE, true), true, false, false, false, true));
    }
}
