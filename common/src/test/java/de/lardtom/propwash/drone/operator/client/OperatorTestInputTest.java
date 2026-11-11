package de.lardtom.propwash.drone.operator.client;

import de.lardtom.propwash.drone.operator.OperatorChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperatorTestInputTest {
    private static final String PROPERTY = "propwash.test.operatorInput";
    private String previous;

    @BeforeEach
    void remember() {
        this.previous = System.getProperty(PROPERTY);
        System.clearProperty(PROPERTY);
    }

    @AfterEach
    void restore() {
        if (this.previous == null) {
            System.clearProperty(PROPERTY);
        } else {
            System.setProperty(PROPERTY, this.previous);
        }
    }

    @Test
    void propertyNameIsTheDocumentedOne() {
        assertEquals(PROPERTY, OperatorTestInput.PROPERTY);
    }

    @Test
    void withoutThePropertyNothingIsOverridden() {
        assertNull(OperatorTestInput.current());
        assertNull(OperatorTestInput.current());
    }

    @Test
    void fourAxesWithoutActions() {
        OperatorTestInput.Input input = OperatorTestInput.parse("0.5,-0.25,1,0.75");
        assertNotNull(input);
        assertArrayEquals(new short[]{OperatorChannel.quantize(0.5), OperatorChannel.quantize(-0.25), OperatorChannel.quantize(1.0),
                OperatorChannel.quantize(0.75)}, input.axes());
        assertEquals(0, input.actions());
        assertEquals(0.5, input.axis(0), 1.0E-4);
        assertEquals(-0.25, input.axis(1), 1.0E-4);
        assertEquals(0.0, input.axis(4));
    }

    @Test
    void furtherValuesAreActionNumbers() {
        OperatorTestInput.Input input = OperatorTestInput.parse(" 1 , 0 , 0 , 0.5 , 1 , 4 ");
        assertNotNull(input);
        assertEquals(0b1001, input.actions());
        assertTrue(OperatorChannel.action(input.actions(), 1));
        assertFalse(OperatorChannel.action(input.actions(), 2));
        assertFalse(OperatorChannel.action(input.actions(), 3));
        assertTrue(OperatorChannel.action(input.actions(), 4));
        OperatorTestInput.Input all = OperatorTestInput.parse("0,0,0,0,4,3,2,1,4");
        assertNotNull(all);
        assertEquals(OperatorChannel.ACTION_MASK, all.actions());
    }

    @Test
    void axesAreClampedLikeSticksThrottleZeroToOne() {
        OperatorTestInput.Input input = OperatorTestInput.parse("2,-3,0.25,-0.5");
        assertNotNull(input);
        assertEquals(1.0, input.axis(0));
        assertEquals(-1.0, input.axis(1), 1.0E-4);
        assertEquals(0.0, input.axis(3));
        OperatorTestInput.Input full = OperatorTestInput.parse("0,0,0,7");
        assertNotNull(full);
        assertEquals(1.0, full.axis(3));
    }

    @Test
    void invalidValuesAreIgnored() {
        for (String raw : new String[]{"", "abc", "1,0,0", "1,0,0,", "1,0,0,0,", "1,0,0,0,5", "1,0,0,0,0", "1,0,0,0,x", "NaN,0,0,0",
                "0,Infinity,0,0", "1;0;0;0", "1,0,0,0,1.5"}) {
            assertNull(OperatorTestInput.parse(raw), raw);
        }
    }

    @Test
    void currentFollowsThePropertyAndForgetsItWhenCleared() {
        System.setProperty(PROPERTY, "1,0,0,0.5,1,4");
        OperatorTestInput.Input first = OperatorTestInput.current();
        assertNotNull(first);
        assertEquals(1.0, first.axis(0));
        assertEquals(0b1001, first.actions());
        System.setProperty(PROPERTY, "-1,0.5,0,0,2");
        OperatorTestInput.Input second = OperatorTestInput.current();
        assertNotNull(second);
        assertEquals(-1.0, second.axis(0), 1.0E-4);
        assertEquals(0b0010, second.actions());
        System.setProperty(PROPERTY, "1,0,0");
        assertNull(OperatorTestInput.current());
        System.clearProperty(PROPERTY);
        assertNull(OperatorTestInput.current());
    }

    @Test
    void axesAreCopiesSoCallersCannotChangeTheInput() {
        OperatorTestInput.Input input = OperatorTestInput.parse("1,0,0,0");
        assertNotNull(input);
        short[] axes = input.axes();
        axes[0] = 0;
        assertEquals(1.0, input.axis(0));
    }
}
