package de.lardtom.propwash.race.client;

import de.lardtom.propwash.race.client.GateKeyRoute.Action;
import de.lardtom.propwash.race.client.GateKeyRoute.Context;
import de.lardtom.propwash.race.client.GateKeyRoute.Hands;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GateKeyRouteTest {
    private static final Hands GATE = new Hands(true, false, false);
    private static final Hands TOOL = new Hands(false, true, false);
    private static final Hands REMOTE = new Hands(false, false, true);
    private static final Hands GATE_AND_REMOTE = new Hands(true, false, true);
    private static final Hands TOOL_AND_REMOTE = new Hands(false, true, true);

    private static Action shared(Hands hands, boolean lookingAtGate, boolean sneaking) {
        return GateKeyRoute.route(new Context(false, false, hands, lookingAtGate, sneaking, true));
    }

    private static Action separate(Hands hands, boolean lookingAtGate, boolean sneaking) {
        return GateKeyRoute.route(new Context(false, false, hands, lookingAtGate, sneaking, false));
    }

    private static Action piloting(Hands hands, boolean lookingAtGate, boolean sharedKey) {
        return GateKeyRoute.route(new Context(false, true, hands, lookingAtGate, false, sharedKey));
    }

    @Test
    void aGateInHandTurnsThePreviewAndSneakingChangesTheMount() {
        assertEquals(Action.TURN, shared(GATE, false, false));
        assertEquals(Action.NEXT_MOUNT, shared(GATE, false, true));
        assertEquals(Action.TURN, shared(GATE_AND_REMOTE, false, false));
        assertEquals(Action.TURN, separate(GATE, false, false));
    }

    @Test
    void gateOrCourseToolRotatesThePlacedGateInView() {
        for (Hands hands : new Hands[]{GATE, TOOL, GATE_AND_REMOTE, TOOL_AND_REMOTE}) {
            assertEquals(Action.ROTATE_GATE, shared(hands, true, false), hands.toString());
            assertEquals(Action.NEXT_MOUNT_GATE, shared(hands, true, true), hands.toString());
            assertEquals(Action.ROTATE_GATE, separate(hands, true, false), hands.toString());
        }
    }

    @Test
    void theCourseToolAloneDoesNothingWithoutAGateInView() {
        assertEquals(Action.NONE, shared(TOOL, false, false));
        assertEquals(Action.NONE, shared(TOOL, false, true));
    }

    @Test
    void inAPilotSessionTheSharedKeyBelongsToTheFlightMode() {
        for (Hands hands : new Hands[]{Hands.EMPTY, GATE, TOOL, REMOTE, GATE_AND_REMOTE}) {
            assertEquals(Action.FLIGHT_MODE, piloting(hands, false, true), hands.toString());
            assertEquals(Action.FLIGHT_MODE, piloting(hands, true, true), hands.toString());
            assertEquals(Action.NONE, piloting(hands, true, false), hands.toString());
        }
    }

    @Test
    void theRemoteKeepsTheSharedKeyQuietOutsideAFlight() {
        assertEquals(Action.NONE, shared(REMOTE, false, false));
        assertEquals(Action.NONE, shared(REMOTE, true, false));
        assertEquals(Action.NONE, shared(REMOTE, true, true));
    }

    @Test
    void anOwnRotateKeyStillExplainsWhatToHold() {
        assertEquals(Action.NEED_TOOL, separate(REMOTE, true, false));
        assertEquals(Action.NEED_TOOL, separate(Hands.EMPTY, true, false));
        assertEquals(Action.NEED_TOOL, shared(Hands.EMPTY, true, false));
        assertEquals(Action.NONE, separate(Hands.EMPTY, false, false));
        assertEquals(Action.NONE, shared(Hands.EMPTY, false, false));
    }

    @Test
    void anOpenScreenSwallowsTheKey() {
        for (boolean piloting : new boolean[]{false, true}) {
            assertEquals(Action.NONE, GateKeyRoute.route(new Context(true, piloting, GATE, true, false, true)));
            assertEquals(Action.NONE, GateKeyRoute.route(new Context(true, piloting, GATE, false, true, false)));
        }
    }

    @Test
    void handsKnowWhichItemsAreTools() {
        assertTrue(GATE.tool());
        assertTrue(TOOL.tool());
        assertFalse(REMOTE.tool());
        assertFalse(Hands.EMPTY.tool());
    }
}
