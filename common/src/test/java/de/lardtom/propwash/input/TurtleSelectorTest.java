package de.lardtom.propwash.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurtleSelectorTest {
    private static final int TURTLE = SwitchEvaluator.bit(SwitchFunction.TURTLE);

    @Test
    void switchDecidesWhenItIsBound() {
        TurtleSelector selector = new TurtleSelector();
        assertFalse(selector.observe(true, 0, 0));
        assertFalse(selector.selected());
        assertTrue(selector.observe(true, TURTLE, TURTLE));
        assertTrue(selector.selected());
        assertFalse(selector.fromKey());
        assertTrue(selector.observe(true, 0, TURTLE));
        assertFalse(selector.selected());
    }

    @Test
    void keyTogglesAndIsReleasedAfterTheFlip() {
        TurtleSelector selector = new TurtleSelector();
        assertTrue(selector.toggle());
        assertTrue(selector.fromKey());
        assertTrue(selector.releaseKey());
        assertFalse(selector.selected());
        assertFalse(selector.releaseKey());
    }

    @Test
    void switchOwnsTheStateAfterItMoves() {
        TurtleSelector selector = new TurtleSelector();
        selector.toggle();
        selector.observe(true, 0, TURTLE);
        assertFalse(selector.selected(), "Schalter aus schlägt die Taste");
        selector.observe(true, TURTLE, TURTLE);
        assertTrue(selector.selected());
        assertFalse(selector.releaseKey(), "vom Schalter gewählt: kein automatisches Ausschalten");
        assertTrue(selector.selected());
    }

    @Test
    void turtleIsAToggleFunctionThatFitsTheSwitchMask() {
        assertTrue(SwitchFunction.TURTLE.supportsToggle());
        assertTrue(SwitchFunction.TURTLE.latchesOnGamepad());
        assertFalse(SwitchFunction.TURTLE.command());
        assertTrue(SwitchFunction.values().length <= 32);
    }
}
