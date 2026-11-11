package de.lardtom.propwash.race;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GateStackRulesTest {
    private static final double X = 0.5;
    private static final double Y = 64.0;
    private static final double Z = 0.5;

    private static GateGeometry opening(double y, float yaw, double openingWidth) {
        GateSize size = GateSize.MEDIUM;
        return GateGeometry.of(X, y, Z, yaw, size, GateMount.STANDING, false, size.outer(openingWidth), size.height(), GateTilt.NONE);
    }

    @Test
    void widerGateOnTheSeatOfANarrowerOneStandsOnItsOwnLegs() {
        GateGeometry narrow = opening(Y, 0.0F, 3.0);
        GateGeometry wide = opening(GateStacking.seat(narrow), 0.0F, 5.5);
        assertFalse(GateStacking.restsOn(wide, narrow), "breites Gate auf schmalem bildet keinen Stapel");
        List<GateGeometry> gates = List.of(narrow, wide);
        assertEquals(-1, GateStacking.above(gates, 0));
        assertEquals(-1, GateStacking.below(gates, 1));
        assertEquals(List.of(0), GateStacking.column(gates, 0));
        assertEquals(List.of(1), GateStacking.column(gates, 1));
        assertEquals(GateStacking.Base.BAR, GateStacking.base(gates, 0), "das schmale Gate trägt nichts");
        assertEquals(GateStacking.Base.BAR, GateStacking.base(gates, 1), "das breite Gate steht auf eigenen Beinen");
        assertTrue(GateStacking.afterRemoval(gates, 0).isEmpty(), "Abbauen des schmalen Gates lässt das breite nicht fallen");
    }

    @Test
    void widerGateTurnedByHalfACircleStaysOutOfTheStack() {
        GateGeometry narrow = opening(Y, 90.0F, 3.0);
        GateGeometry wide = opening(GateStacking.seat(narrow), 270.0F, 4.0);
        assertTrue(GateStacking.sameColumn(wide, narrow));
        assertFalse(GateStacking.restsOn(wide, narrow));
    }

    @Test
    void equalOrNarrowerGateOnTheSeatStillFormsTheStack() {
        GateGeometry lower = opening(Y, 0.0F, 4.0);
        for (double width : new double[]{1.0, 3.0, 4.0}) {
            GateGeometry upper = opening(GateStacking.seat(lower), 0.0F, width);
            assertTrue(GateStacking.restsOn(upper, lower), "Öffnung " + width + " auf 4");
            List<GateGeometry> gates = List.of(lower, upper);
            assertEquals(List.of(0, 1), GateStacking.column(gates, 1));
            assertEquals(GateStacking.Base.LEGS, GateStacking.base(gates, 0));
            assertEquals(GateStacking.Base.STACKED, GateStacking.base(gates, 1));
        }
    }

    @Test
    void movingOrWideningOntoTheSeatOfANarrowerGateIsRefused() {
        GateGeometry narrow = opening(Y, 0.0F, 3.0);
        GateGeometry wide = opening(GateStacking.seat(narrow) - 0.5, 0.0F, 5.5);
        List<GateGeometry> others = List.of(narrow);
        assertEquals(GateStacking.Refusal.NONE, GateStacking.refusal(wide, others), "Überlappung ohne Stapel bleibt mit freier Platzierung erlaubt");
        assertEquals(GateStacking.Refusal.TOO_WIDE, GateStacking.refusal(wide.moved(0.0, 0.5, 0.0), others), "Position Y +0,5 auf das schmale Gate");
        GateGeometry top = opening(GateStacking.seat(narrow), 0.0F, 3.0);
        assertEquals(GateStacking.Refusal.NONE, GateStacking.refusal(top, others));
        assertEquals(GateStacking.Refusal.TOO_WIDE, GateStacking.refusal(opening(top.y(), 0.0F, 3.5), others), "oberes Gate verbreitern");
        assertEquals(GateStacking.Refusal.TOO_WIDE, GateStacking.refusal(opening(top.y(), 180.0F, 4.0), others), "verbreitert und um 180° gedreht");
        assertEquals(GateStacking.Refusal.NONE, GateStacking.refusal(opening(top.y(), 90.0F, 4.0), others), "quer gedreht bildet keinen Stapel");
    }

    @Test
    void narrowingOrShorteningTheLowerGateBelowAWiderOneIsRefused() {
        GateGeometry upper = opening(GateStacking.seat(opening(Y, 0.0F, 4.0)), 0.0F, 4.0);
        List<GateGeometry> others = List.of(upper);
        assertEquals(GateStacking.Refusal.NONE, GateStacking.refusal(opening(Y, 0.0F, 4.0), others));
        assertEquals(GateStacking.Refusal.NONE, GateStacking.refusal(opening(Y, 0.0F, 6.0), others));
        assertEquals(GateStacking.Refusal.TOO_WIDE, GateStacking.refusal(opening(Y, 0.0F, 3.0), others), "unteres Gate schmaler");
        GateSize size = GateSize.MEDIUM;
        GateGeometry shorter = GateGeometry.of(X, Y + 1.0, Z, 0.0F, size, GateMount.STANDING, false, size.outer(2.0), size.outer(2.0), GateTilt.NONE);
        assertEquals(upper.y(), GateStacking.seat(shorter), 1.0E-9);
        assertEquals(GateStacking.Refusal.TOO_WIDE, GateStacking.refusal(shorter, others), "schmaleres Gate unter das breite geschoben");
    }

    @Test
    void aFifthGateOnAColumnOfFourIsRefused() {
        List<GateGeometry> column = new ArrayList<>();
        column.add(GateGeometry.of(X, Y, Z, 0.0F, GateSize.LARGE));
        for (int i = 1; i < GateStacking.MAX_STACK; i++) {
            column.add(GateStacking.stackOn(column.getLast(), GateSize.MEDIUM, 0.0F));
        }
        GateGeometry fifth = GateStacking.stackOn(column.getLast(), GateSize.SMALL, 0.0F);
        assertEquals(GateStacking.Refusal.TOO_HIGH, GateStacking.refusal(fifth, column));
        assertEquals(GateStacking.Refusal.NONE, GateStacking.refusal(column.getLast(), column.subList(0, GateStacking.MAX_STACK - 1)));
        assertEquals(GateStacking.Refusal.NONE, GateStacking.refusal(fifth.moved(0.0, 2.0, 0.0), column));
    }

    @Test
    void onlyStandingGatesFollowTheStackRules() {
        GateGeometry narrow = opening(Y, 0.0F, 3.0);
        GateSize size = GateSize.MEDIUM;
        GateGeometry lying = GateGeometry.of(X, GateStacking.seat(narrow), Z, 0.0F, size, GateMount.FLOOR_FLAT, false, size.outer(6.0),
                size.height(), GateTilt.NONE);
        assertEquals(GateStacking.Refusal.NONE, GateStacking.refusal(lying, List.of(narrow)));
    }

    @Test
    void everySizePairStacksOnlyWhenTheUpperGateFits() {
        for (GateSize lower : GateSize.values()) {
            for (GateSize upper : GateSize.values()) {
                GateGeometry base = GateGeometry.of(X, Y, Z, 0.0F, lower);
                GateGeometry top = GateStacking.stackOn(base, upper, 0.0F);
                boolean fits = upper.width() <= lower.width();
                assertEquals(fits, GateStacking.restsOn(top, base), upper + " auf " + lower);
                assertEquals(fits ? GateStacking.Base.STACKED : GateStacking.Base.BAR, GateStacking.base(List.of(base, top), 1), upper + " auf " + lower);
            }
        }
    }
}
