package de.lardtom.propwash.rx;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RxOutputTest {
    @Test
    void frontPowersOnlyTheBlockInFront() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (Direction toward : Direction.values()) {
                int wanted = toward == facing.getOpposite() ? 11 : 0;
                assertEquals(wanted, RxOutput.FRONT.signal(facing, toward, 11), "facing=" + facing + ", Nachbar sieht nach " + toward);
                assertEquals(wanted, RxOutput.FRONT.direct(facing, toward, 11), "stark: facing=" + facing + ", Nachbar sieht nach " + toward);
            }
        }
    }

    @Test
    void frontOfAnEastFacingBlockIsItsEastNeighbour() {
        assertEquals(15, RxOutput.FRONT.signal(Direction.EAST, Direction.WEST, 15), "Nachbar im Osten fragt nach Westen");
        assertEquals(0, RxOutput.FRONT.signal(Direction.EAST, Direction.EAST, 15), "Nachbar im Westen (hinten)");
        assertEquals(0, RxOutput.FRONT.signal(Direction.EAST, Direction.DOWN, 15), "Nachbar oben");
        assertEquals(0, RxOutput.FRONT.signal(Direction.EAST, null, 15));
    }

    @Test
    void oldBlocksPowerAllSidesWeakly() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (Direction toward : Direction.values()) {
                assertEquals(9, RxOutput.ALL.signal(facing, toward, 9));
                assertEquals(0, RxOutput.ALL.direct(facing, toward, 9), "alter Block leitet nicht stark durch");
            }
        }
    }

    @Test
    void dustConnectsOnlyInFront() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (Direction toward : Direction.Plane.HORIZONTAL) {
                assertEquals(toward == facing.getOpposite(), RxOutput.FRONT.connects(facing, toward), "facing=" + facing + ", Staub sieht nach " + toward);
                assertTrue(RxOutput.ALL.connects(facing, toward));
            }
            assertFalse(RxOutput.FRONT.connects(facing, null));
            assertFalse(RxOutput.ALL.connects(facing, null));
        }
    }

    @Test
    void savedNamesStayStable() {
        assertEquals("front", RxOutput.FRONT.getSerializedName());
        assertEquals("all", RxOutput.ALL.getSerializedName());
    }

    @Test
    void zeroPowerIsZeroEverywhere() {
        for (RxOutput output : RxOutput.values()) {
            for (Direction toward : Direction.values()) {
                assertEquals(0, output.signal(Direction.NORTH, toward, 0));
                assertEquals(0, output.direct(Direction.NORTH, toward, 0));
            }
        }
    }
}
