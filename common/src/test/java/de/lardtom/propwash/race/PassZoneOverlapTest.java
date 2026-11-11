package de.lardtom.propwash.race;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PassZoneOverlapTest {
    private static GateGeometry gate(double x, double z, float yaw) {
        return GateGeometry.of(x, 64.0, z, yaw, GateSize.MEDIUM);
    }

    private static GateGeometry pole(double x, double z, int side, double reach) {
        return GateGeometry.element(x, 64.0, z, 0.0F, CourseElement.POLE, side, reach, 3.0, GateTilt.NONE);
    }

    private static GateGeometry gatePole(double x, double z, int side, double reach) {
        return GateGeometry.element(x, 64.0, z, 0.0F, CourseElement.GATE_POLE, 0, 1.0, 3.0, GateTilt.NONE, GateMount.STANDING,
                ScoreArea.DEFAULT.withRole(ScoreArea.Role.PASS).withSide(side).withReach(reach));
    }

    private static GateGeometry flag(double x, double z, int side) {
        return GateGeometry.element(x, 64.0, z, 0.0F, CourseElement.FLAG, side, 5.0, 4.0, GateTilt.NONE);
    }

    private static GateGeometry withReach(GateGeometry element, double value) {
        if (element.kind() == CourseElement.GATE_POLE) {
            return element.withScore(element.score().withReach(value));
        }
        return new GateGeometry(element.x(), element.y(), element.z(), element.yawDegrees(), value, element.height(), element.frame(), element.depth(), element.mount(),
                element.flipped(), element.tilt(), element.kind(), element.variant(), element.score()).withReverse(element.reversed());
    }

    @Test
    void poleBesideTheNextGateIsReportedWithAReachThatClears() {
        List<GateGeometry> course = List.of(gate(0.0, 0.0, 0.0F), pole(-3.0, 12.0, CourseElement.LEFT, 4.0), gate(0.0, 12.0, 0.0F));
        List<PassZoneOverlap.Finding> findings = PassZoneOverlap.around(course, 1, false);
        assertEquals(1, findings.size(), findings.toString());
        PassZoneOverlap.Finding finding = findings.getFirst();
        assertEquals(1, finding.element());
        assertEquals(2, finding.neighbour());
        assertTrue(finding.shortenable());
        assertEquals(1.5, finding.shortened(), 1.0E-9);
        GateGeometry trimmed = withReach(course.get(1), finding.shortened());
        assertNull(PassZoneOverlap.between(1, trimmed, 2, course.get(2)), "Mit der gekürzten Reichweite ist die Öffnung frei");
        assertEquals(1.5, trimmed.scoreWidth(), 1.0E-9);
    }

    @Test
    void gatePoleGetsItsScoreReachShortened() {
        GateGeometry pole = gatePole(-3.0, 12.0, CourseElement.LEFT, 4.0);
        PassZoneOverlap.Finding finding = PassZoneOverlap.between(1, pole, 2, gate(0.0, 12.0, 0.0F));
        assertNotNull(finding);
        assertEquals(1.5, finding.shortened(), 1.0E-9);
        GateGeometry trimmed = withReach(pole, finding.shortened());
        assertEquals(1.5, trimmed.score().reach(), 1.0E-9);
        assertNull(PassZoneOverlap.between(1, trimmed, 2, gate(0.0, 12.0, 0.0F)));
    }

    @Test
    void zoneReachingAPartOfTheOpeningAlsoCounts() {
        assertNotNull(PassZoneOverlap.between(1, pole(-4.0, 12.0, CourseElement.LEFT, 3.0), 2, gate(0.0, 12.0, 0.0F)), "Zone endet mitten in der Öffnung");
        assertNull(PassZoneOverlap.between(1, pole(-4.0, 12.0, CourseElement.LEFT, 2.5), 2, gate(0.0, 12.0, 0.0F)), "Zone endet genau am Rand");
    }

    @Test
    void poleTooCloseToShortenStaysWithoutButton() {
        PassZoneOverlap.Finding slalom = PassZoneOverlap.between(1, pole(-2.25, 12.0, CourseElement.LEFT, 4.0), 2, gate(0.0, 12.0, 0.0F));
        assertNotNull(slalom);
        assertFalse(slalom.shortenable(), "0,75 Blöcke sind weniger als die kürzeste Slalomstange");
        PassZoneOverlap.Finding gatePole = PassZoneOverlap.between(1, gatePole(-2.25, 12.0, CourseElement.LEFT, 4.0), 2, gate(0.0, 12.0, 0.0F));
        assertNotNull(gatePole);
        assertTrue(gatePole.shortenable());
        assertEquals(0.5, gatePole.shortened(), 1.0E-9);
        PassZoneOverlap.Finding inside = PassZoneOverlap.between(1, pole(0.0, 12.0, CourseElement.LEFT, 4.0), 2, gate(0.0, 12.0, 0.0F));
        assertNotNull(inside);
        assertFalse(inside.shortenable(), "Stange steht in der Öffnung");
    }

    @Test
    void otherSideOtherPlaneOtherHeightAreFine() {
        GateGeometry target = gate(0.0, 12.0, 0.0F);
        assertNull(PassZoneOverlap.between(1, pole(-3.0, 12.0, CourseElement.RIGHT, 4.0), 2, target), "Zone zeigt vom Gate weg");
        assertNull(PassZoneOverlap.between(1, pole(-3.0, 15.0, CourseElement.LEFT, 4.0), 2, target), "Zone liegt drei Blöcke weiter: normaler Slalom");
        assertNull(PassZoneOverlap.between(1, pole(-3.0, 9.0, CourseElement.LEFT, 4.0), 2, target));
        assertNotNull(PassZoneOverlap.between(1, pole(-3.0, 13.0, CourseElement.LEFT, 4.0), 2, target), "Ein Block daneben zählt schon");
        GateGeometry high = GateGeometry.of(0.0, 80.0, 12.0, 0.0F, GateSize.MEDIUM);
        assertNull(PassZoneOverlap.between(1, pole(-3.0, 12.0, CourseElement.LEFT, 4.0), 2, high), "Gate hoch über der Zone");
        assertNull(PassZoneOverlap.between(1, pole(-3.0, 12.0, CourseElement.LEFT, 4.0), 2, gate(0.0, 12.0, 180.0F)), "Gate wird entgegengesetzt durchflogen");
        assertNull(PassZoneOverlap.between(1, pole(-3.0, 12.0, CourseElement.LEFT, 4.0), 2, gate(0.0, 12.0, 90.0F)), "Gate quer zur Zone");
        assertNull(PassZoneOverlap.between(0, target, 1, gate(0.0, 12.0, 0.0F)), "Ein Gate hat keine Pass-Zone");
        assertNull(PassZoneOverlap.between(1, pole(-3.0, 12.0, CourseElement.LEFT, 4.0), 2, pole(0.0, 12.0, CourseElement.LEFT, 4.0)), "Eine Stange hat keine Öffnung");
    }

    @Test
    void previousElementAndWrapAroundAreChecked() {
        List<GateGeometry> beforeTheGate = List.of(gate(0.0, 12.0, 0.0F), pole(-3.0, 12.0, CourseElement.LEFT, 4.0), gate(0.0, 30.0, 0.0F));
        List<PassZoneOverlap.Finding> previous = PassZoneOverlap.around(beforeTheGate, 1, false);
        assertEquals(1, previous.size());
        assertEquals(0, previous.getFirst().neighbour(), "Pole deckt das vorige Gate ab");
        List<GateGeometry> atTheEnd = List.of(gate(0.0, 0.0, 0.0F), gate(0.0, 30.0, 0.0F), pole(-3.0, 0.0, CourseElement.LEFT, 4.0));
        assertEquals(1, PassZoneOverlap.around(atTheEnd, 2, false).size(), "Letzte Stange neben dem Start-Gate");
        assertTrue(PassZoneOverlap.around(atTheEnd, 2, true).isEmpty(), "Punkt zu Punkt fliegt nach dem letzten Element nicht zum Start");
    }

    @Test
    void appendingANeighbourFindsTheOlderPole() {
        List<GateGeometry> course = List.of(gate(0.0, 0.0, 0.0F), pole(-3.0, 12.0, CourseElement.LEFT, 4.0), gate(0.0, 12.0, 0.0F));
        List<PassZoneOverlap.Finding> findings = PassZoneOverlap.around(course, 2, false);
        assertEquals(1, findings.size(), "Das neue Gate meldet die Stange davor");
        assertEquals(1, findings.getFirst().element());
    }

    @Test
    void turnPoleLastPlaneCoversTheGateFlownBack() {
        GateGeometry flag = flag(0.0, 10.0, CourseElement.LEFT);
        PassZoneOverlap.Finding finding = PassZoneOverlap.between(1, flag, 2, gate(3.0, 10.0, 180.0F));
        assertNotNull(finding);
        assertEquals(1.5, finding.shortened(), 1.0E-9);
        assertNull(PassZoneOverlap.between(1, withReach(flag, 1.5), 2, gate(3.0, 10.0, 180.0F)));
    }

    @Test
    void reversedPassOfTheGateCounts() {
        GateGeometry pole = pole(-3.0, 12.0, CourseElement.LEFT, 4.0);
        assertNull(PassZoneOverlap.between(1, pole, 2, gate(0.0, 12.0, 0.0F).withReverse(true)), "Rückwärts-Durchgang fliegt gegen die Zone");
        GateGeometry mirrored = pole(3.0, 12.0, CourseElement.LEFT, 4.0).withReverse(true);
        assertNotNull(PassZoneOverlap.between(1, mirrored, 2, gate(0.0, 12.0, 0.0F).withReverse(true)), "Rückwärts liegt die Zone auf der anderen Seite der Stange");
    }

    @Test
    void freeformOpeningIsCoveredToo() {
        FreeformRegion region = FreeformRegion.single(FreeformRegion.AXIS_Z);
        GateGeometry freeform = GateGeometry.freeform(0.5, 65.5, 12.0, region, 0);
        assertNotNull(PassZoneOverlap.between(1, pole(-3.0, 12.0, CourseElement.LEFT, 4.0), 2, freeform));
    }

    @Test
    void shortestFindingDecidesTheButton() {
        PassZoneOverlap.Finding wide = new PassZoneOverlap.Finding(1, 2, 3.0);
        PassZoneOverlap.Finding narrow = new PassZoneOverlap.Finding(1, 0, 1.0);
        assertEquals(narrow, PassZoneOverlap.shortest(List.of(wide, narrow)));
        assertNull(PassZoneOverlap.shortest(List.of()));
    }
}
