package de.lardtom.propwash.race;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatePolesTest {
    private static final double EPS = 1.0E-9;

    private static GatePlacement.Click floor(int x, int y, int z, float yaw) {
        return new GatePlacement.Click(GateMount.Surface.FLOOR, x, y - 1, z, 0, 0, x + 0.5, y, z + 0.5, x + 0.5, y, z + 0.5, yaw);
    }

    private static List<OrientedBoxBounds> parts(GateGeometry geometry) {
        List<OrientedBoxBounds> out = new ArrayList<>();
        geometry.parts(box -> out.add(new OrientedBoxBounds(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())),
                GateStacking.Base.MOUNTED);
        return out;
    }

    private record OrientedBoxBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    }

    @Test
    void poleIsAGateSideWithColorsAndLengthSteps() {
        CourseElement pole = CourseElement.GATE_POLE;
        assertFalse(pole.courseable());
        assertTrue(pole.mountable());
        assertFalse(pole.startable());
        assertEquals(0, pole.stages());
        assertEquals(GatePoles.COLORS, pole.variants());
        assertEquals("gate_pole", pole.itemId());
        assertEquals(pole, CourseElement.byKey("gate_pole"));
        assertEquals(GateSize.MEDIUM.frame(), pole.thickness(), EPS);
        assertEquals(GateSize.MEDIUM.depth(), pole.depth(4.0), EPS);
        assertEquals(0.5, pole.outerWidth(7.0), EPS);
        assertEquals(6.5, pole.outerHeight(6.4), EPS);
        assertEquals(GatePoles.MIN_LENGTH, GatePoles.snapLength(0.2), EPS);
        assertEquals(GatePoles.MAX_LENGTH, GatePoles.snapLength(40.0), EPS);
        assertEquals(GatePoles.DEFAULT_LENGTH, GatePoles.snapLength(Double.NaN), EPS);
        assertEquals("white", GatePoles.colorKey(GatePoles.WHITE));
        assertEquals("black", GatePoles.colorKey(GatePoles.BLACK));
        assertEquals("red", pole.variantKey(14));
        Set<Integer> colors = new HashSet<>();
        Set<String> keys = new HashSet<>();
        for (int v = 0; v < GatePoles.COLORS; v++) {
            colors.add(GatePoles.ledColor(v));
            keys.add(GatePoles.colorKey(v));
        }
        assertEquals(GatePoles.COLORS, colors.size());
        assertEquals(GatePoles.COLORS, keys.size());
        assertEquals(pole.variantKey(3), pole.variantKey(3 + GatePoles.COLORS));
    }

    @Test
    void placesStandingOnFloorHangingFromCeilingAndOutOfWall() {
        GatePlacement.Preference preference = GatePlacement.Preference.DEFAULT;
        GateGeometry ground = GatePoles.place(floor(3, 64, 7, 0.0F), preference, 0, GatePoles.DEFAULT_LENGTH);
        assertEquals(GateMount.STANDING, ground.mount());
        assertTrue(GatePoles.standing(ground));
        List<OrientedBoxBounds> parts = parts(ground);
        assertEquals(1, parts.size());
        assertEquals(new OrientedBoxBounds(3.25, 64.0, 7.25, 3.75, 68.0, 7.75), parts.getFirst());

        GatePlacement.Click ceiling = new GatePlacement.Click(GateMount.Surface.CEILING, 3, 70, 7, 0, 0, 3.5, 70.0, 7.5, 3.5, 69.0, 7.5, 0.0F);
        GateGeometry hanging = GatePoles.place(ceiling, preference, 2, 3.0);
        assertEquals(GateMount.HANGING, hanging.mount());
        assertFalse(GatePoles.standing(hanging));
        assertEquals(new OrientedBoxBounds(3.25, 67.0, 7.25, 3.75, 70.0, 7.75), parts(hanging).getFirst());

        GatePlacement.Click wall = new GatePlacement.Click(GateMount.Surface.WALL, 5, 64, 7, 1, 0, 6.0, 64.4, 7.3, 6.5, 64.0, 7.5, 0.0F);
        GateGeometry out = GatePoles.place(wall, preference, 0, 4.0);
        assertEquals(GateMount.WALL_DIVE, out.mount());
        OrientedBoxBounds box = parts(out).getFirst();
        assertEquals(6.0, box.minX(), 1.0E-6);
        assertEquals(10.0, box.maxX(), 1.0E-6);
        assertEquals(64.25, box.minY(), 1.0E-6);
        assertEquals(64.75, box.maxY(), 1.0E-6);
        assertEquals(7.25, box.minZ(), 1.0E-6);
        assertEquals(7.75, box.maxZ(), 1.0E-6);
        assertTrue(GatePoles.seats(out).isEmpty());
        assertTrue(GatePoles.seats(hanging).isEmpty());
    }

    @Test
    void standingGatesOfferTheirTopCornersAsSeats() {
        GateGeometry gate = GateGeometry.of(0.5, 64.0, 0.5, 0.0F, GateSize.MEDIUM);
        List<GatePoles.Seat> seats = GatePoles.seats(gate);
        assertEquals(2, seats.size());
        assertEquals(0.5 - 1.75, seats.get(0).x(), 1.0E-9);
        assertEquals(0.5 + 1.75, seats.get(1).x(), 1.0E-9);
        for (GatePoles.Seat seat : seats) {
            assertEquals(68.0, seat.y(), 1.0E-9);
            assertEquals(0.5, seat.z(), 1.0E-9);
            GateGeometry pole = GatePoles.onSeat(seat, 0, 5.0);
            assertTrue(GatePoles.restsOn(pole, gate));
            assertEquals(GateStacking.Base.STACKED, GateStacking.base(List.of(gate, pole), 1));
            assertFalse(GatePoles.restsOn(pole.at(pole.x() + 0.5, pole.y(), pole.z()), gate));
        }
        GateGeometry turned = GateGeometry.of(0.5, 64.0, 0.5, 90.0F, GateSize.LARGE);
        List<GatePoles.Seat> along = GatePoles.seats(turned);
        assertEquals(0.5, along.get(0).x(), 1.0E-9);
        assertEquals(2.75, Math.abs(along.get(0).z() - 0.5), 1.0E-9);
        assertEquals(90.0F, along.get(0).yaw());
        GateGeometry wide = GateGeometry.of(0.5, 64.0, 0.5, 0.0F, GateSize.MEDIUM, GateMount.STANDING, false, 7.0, 6.0, GateTilt.NONE);
        assertEquals(0.5 + 3.25, GatePoles.seats(wide).get(1).x(), 1.0E-9);
        assertEquals(70.0, GatePoles.seats(wide).get(1).y(), 1.0E-9);
        GateGeometry fine = GateGeometry.of(0.5, 64.0, 0.5, 0.0F, GateSize.MEDIUM).withTilt(new GateTilt(30.0F, 0.0F, 0.0F));
        GatePoles.Seat fineSeat = GatePoles.seats(fine).get(1);
        assertEquals(30.0F, fineSeat.fine());
        assertEquals(68.0, fineSeat.y(), 1.0E-9);
        assertEquals(30.0F, GatePoles.onSeat(fineSeat, 0, 4.0).heading(), 1.0E-4F);
    }

    @Test
    void onlyUprightGatesCarryPoles() {
        GateGeometry hanging = GatePlacement.place(GateMount.HANGING, 0.5, 70.0, 0.5, 0.0F, false, GateSize.MEDIUM);
        GateGeometry dive = GatePlacement.place(GateMount.WALL_DIVE, 4.0, 64.5, 0.5, 90.0F, false, GateSize.MEDIUM);
        GateGeometry flat = GatePlacement.place(GateMount.FLOOR_FLAT, 0.5, 64.0, 0.5, 0.0F, false, GateSize.MEDIUM);
        GateGeometry side = GatePlacement.place(GateMount.WALL_SIDE, 4.0, 66.0, 0.5, 90.0F, false, GateSize.MEDIUM);
        GateGeometry pitched = GateGeometry.of(0.5, 64.0, 0.5, 0.0F, GateSize.MEDIUM).withTilt(new GateTilt(0.0F, 20.0F, 0.0F));
        assertTrue(GatePoles.seats(hanging).isEmpty());
        assertTrue(GatePoles.seats(dive).isEmpty());
        assertTrue(GatePoles.seats(flat).isEmpty());
        assertTrue(GatePoles.seats(pitched).isEmpty());
        assertEquals(2, GatePoles.seats(side).size());
        GateGeometry flag = GateGeometry.element(0.5, 64.0, 0.5, 0.0F, CourseElement.FLAG, 0, 5.0, 4.0, GateTilt.NONE);
        assertTrue(GatePoles.seats(flag).isEmpty());
    }

    @Test
    void polesStackOnPolesUpToTheLimit() {
        GateGeometry lower = GatePoles.place(floor(0, 64, 0, 0.0F), GatePlacement.Preference.DEFAULT, 0, 3.0);
        List<GatePoles.Seat> top = GatePoles.seats(lower);
        assertEquals(1, top.size());
        assertEquals(67.0, top.getFirst().y(), 1.0E-9);
        GateGeometry upper = GatePoles.onSeat(top.getFirst(), 4, 2.5);
        assertTrue(GatePoles.restsOn(upper, lower));
        assertEquals(4, upper.variant());
        assertEquals(GateStacking.Base.STACKED, GateStacking.base(List.of(lower, upper), 1));
        assertEquals(GateStacking.Base.BAR, GateStacking.base(List.of(lower, upper), 0));
        assertEquals(GatePoles.Refusal.NONE, GatePoles.check(GatePoles.MAX_CHAIN - 1));
        assertEquals(GatePoles.Refusal.TOO_HIGH, GatePoles.check(GatePoles.MAX_CHAIN));
        assertEquals(GateStacking.Base.MOUNTED, GatePoles.base(GateMount.HANGING, false));
        assertEquals(GateStacking.Base.MOUNTED, GatePoles.base(GateMount.WALL_DIVE, false));
    }

    @Test
    void nearestSeatFollowsTheClickedSide() {
        GateGeometry gate = GateGeometry.of(0.5, 64.0, 0.5, 0.0F, GateSize.MEDIUM);
        List<GatePoles.Seat> seats = GatePoles.seats(gate);
        assertEquals(0, GatePoles.nearest(seats, -1.0, 0.4));
        assertEquals(1, GatePoles.nearest(seats, 2.0, 0.6));
        assertEquals(-1, GatePoles.nearest(List.of(), 0.0, 0.0));
    }

    @Test
    void editingKeepsTheMountPointAndOnlyTurnsTheYaw() {
        GatePlacement.Click ceiling = new GatePlacement.Click(GateMount.Surface.CEILING, 3, 70, 7, 0, 0, 3.5, 70.0, 7.5, 3.5, 69.0, 7.5, 0.0F);
        GateGeometry hanging = GatePoles.place(ceiling, GatePlacement.Preference.DEFAULT, 0, 4.0);
        GateGeometry longer = GatePlacement.edited(hanging, 45.0F, 30.0F, 10.0F, 9.0, 6.5);
        assertEquals(GateMount.HANGING, longer.mount());
        assertEquals(6.5, longer.height(), EPS);
        assertEquals(70.0, longer.y() + longer.height(), 1.0E-9);
        assertEquals(45.0F, longer.heading(), 1.0E-4F);
        assertFalse(longer.tilted());
        assertEquals(0.5, longer.width(), EPS);

        GateGeometry standing = GatePoles.place(floor(0, 64, 0, 0.0F), GatePlacement.Preference.DEFAULT, 0, 4.0);
        GateGeometry shorter = GatePlacement.edited(standing, 0.0F, 0.0F, 0.0F, 1.0, 2.0);
        assertEquals(64.0, shorter.y(), EPS);
        assertEquals(2.0, shorter.height(), EPS);

        GateGeometry turned = GatePlacement.rotated(standing, false, false, 15.0F);
        assertNotNull(turned);
        assertEquals(15.0F, turned.heading(), 1.0E-4F);
        assertEquals(standing.x(), turned.x(), EPS);
        assertEquals(standing.y(), turned.y(), EPS);
        GateGeometry recolored = GatePlacement.rotated(standing, true, false, 90.0F);
        assertNotNull(recolored);
        assertEquals(1, recolored.variant());
        assertTrue(standing.sameAs(standing.at(standing.x(), standing.y(), standing.z())));
    }

    @Test
    void preferenceRemembersAllSixteenColors() {
        GatePlacement.Preference red = GatePlacement.Preference.DEFAULT.withVariant(14);
        assertEquals(14, red.variant(CourseElement.GATE_POLE));
        assertEquals(14 % 4, red.variant(CourseElement.CUBE));
        assertEquals(0, red.variant(CourseElement.POLE));
        assertEquals(15, red.nextVariant(CourseElement.GATE_POLE).variant(CourseElement.GATE_POLE));
        assertEquals(0, red.nextVariant(CourseElement.GATE_POLE).nextVariant(CourseElement.GATE_POLE).variant(CourseElement.GATE_POLE));
    }

    @Test
    void polesHaveNoPassagesAndNeverJoinCourses() {
        GateGeometry pole = GatePoles.place(floor(0, 64, 0, 0.0F), GatePlacement.Preference.DEFAULT, 0, 4.0);
        assertTrue(pole.stages().isEmpty());
        assertTrue(pole.fouls(0).isEmpty());
        assertEquals(GateGeometry.NO_CROSSING, pole.stageCrossing(0, 0.5, 66.0, -3.0, 0.5, 66.0, 3.0));
        assertEquals(1, pole.boxCount(GateStacking.Base.MOUNTED));
        for (CourseElement kind : CourseElement.values()) {
            assertEquals(kind != CourseElement.GATE_POLE, kind.courseable(), kind.key());
        }
    }
}
