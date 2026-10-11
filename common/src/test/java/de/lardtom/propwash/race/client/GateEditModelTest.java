package de.lardtom.propwash.race.client;

import de.lardtom.propwash.race.CourseElement;
import de.lardtom.propwash.race.GateSignal;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.GateTilt;
import de.lardtom.propwash.race.client.GateEditModel.Row;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GateEditModelTest {
    private static GateEditModel gate(float heading, float pitch, float roll) {
        return new GateEditModel(CourseElement.GATE, heading, pitch, roll, 3.0, 3.0, 0);
    }

    @Test
    void everyKindHasItsOwnRowsInPanelOrder() {
        assertEquals(List.of(Row.HEADING, Row.PITCH, Row.ROLL, Row.WIDTH, Row.HEIGHT, Row.SIGNAL, Row.PULSE, Row.RACE_ONLY),
                GateEditModel.rows(CourseElement.GATE));
        for (CourseElement element : List.of(CourseElement.FLAG, CourseElement.POLE, CourseElement.CUBE)) {
            assertEquals(List.of(Row.HEADING, Row.VARIANT, Row.WIDTH, Row.HEIGHT), GateEditModel.rows(element), element.name());
        }
        assertEquals(List.of(Row.VARIANT, Row.SIGNAL, Row.PULSE, Row.RACE_ONLY), GateEditModel.rows(CourseElement.FREEFORM));
        assertEquals(GateEditModel.Section.ROTATION, GateEditModel.section(Row.VARIANT));
        assertEquals(GateEditModel.Section.SIZE, GateEditModel.section(Row.HEIGHT));
    }

    @Test
    void theWheelTurnsInFiveDegreeStepsOnTheGridAndCtrlInSingleDegrees() {
        GateEditModel model = gate(33.0F, 0.0F, 0.0F);
        model.scroll(1, false);
        assertEquals(35.0F, model.heading());
        model.scroll(2, false);
        assertEquals(45.0F, model.heading());
        model.scroll(-1, false);
        assertEquals(40.0F, model.heading());
        model.scroll(1, true);
        assertEquals(41.0F, model.heading());
        model.scroll(-1, false);
        assertEquals(40.0F, model.heading());
        GateEditModel off = gate(33.0F, 0.0F, 0.0F);
        off.scroll(-1, false);
        assertEquals(30.0F, off.heading());
    }

    @Test
    void yawWrapsAroundTheCompassRollWrapsAndPitchStopsAtNinety() {
        GateEditModel model = gate(355.0F, 85.0F, 180.0F);
        model.change(Row.HEADING, 1, false);
        assertEquals(0.0F, model.heading());
        model.change(Row.HEADING, -1, false);
        assertEquals(355.0F, model.heading());
        model.change(Row.PITCH, 3, false);
        assertEquals(GateTilt.MAX_PITCH, model.pitch());
        model.change(Row.PITCH, -40, false);
        assertEquals(-GateTilt.MAX_PITCH, model.pitch());
        model.change(Row.ROLL, 1, false);
        assertEquals(-175.0F, model.roll());
        model.change(Row.ROLL, -2, false);
        assertEquals(175.0F, model.roll());
        for (int i = 0; i < 200; i++) {
            model.change(Row.HEADING, 7, true);
            assertTrue(model.heading() >= 0.0F && model.heading() < 360.0F, "Gieren " + model.heading());
        }
    }

    @Test
    void sizesMoveInHalfBlocksBetweenOneAndSixteen() {
        GateEditModel model = gate(0.0F, 0.0F, 0.0F);
        model.change(Row.WIDTH, 3, false);
        assertEquals(4.5, model.width());
        model.change(Row.WIDTH, 3, true);
        assertEquals(6.0, model.width(), "Strg macht Größen nicht feiner als halbe Blöcke");
        model.change(Row.WIDTH, 100, false);
        assertEquals(GateSize.MAX_OPENING, model.width());
        model.change(Row.HEIGHT, -100, false);
        assertEquals(GateSize.MIN_OPENING, model.height());
    }

    @Test
    void sneakScrollMovesTheSelectionAroundTheRows() {
        GateEditModel model = gate(0.0F, 0.0F, 0.0F);
        assertEquals(Row.HEADING, model.selected());
        model.moveSelection(1);
        assertEquals(Row.PITCH, model.selected());
        model.moveSelection(-2);
        assertEquals(Row.RACE_ONLY, model.selected());
        model.moveSelection(1);
        assertEquals(Row.HEADING, model.selected());
        model.select(Row.VARIANT);
        assertEquals(Row.HEADING, model.selected(), "eine Zeile, die es beim Gate nicht gibt, bleibt unausgewählt");
        model.select(Row.ROLL);
        model.scroll(2, false);
        assertEquals(10.0F, model.roll());
        assertEquals(0.0F, model.heading());
    }

    @Test
    void elementsCycleTheirVariantAndStayUpright() {
        GateEditModel cube = new GateEditModel(CourseElement.CUBE, 90.0F, 20.0F, 30.0F, 3.0, 3.0, CourseElement.EXIT_FRONT);
        assertEquals(0.0F, cube.pitch());
        assertEquals(0.0F, cube.roll());
        cube.change(Row.VARIANT, 3, false);
        assertEquals(CourseElement.EXIT_LEFT, cube.variant());
        cube.change(Row.VARIANT, 1, false);
        assertEquals(CourseElement.EXIT_FRONT, cube.variant());
        cube.change(Row.PITCH, 4, false);
        assertEquals(0.0F, cube.pitch(), "Kurs-Elemente haben keine Nicken-Zeile");
        cube.set(90.0F, 15.0F, 15.0F, 3.0, 3.0);
        assertEquals(0.0F, cube.pitch());
        GateEditModel pole = new GateEditModel(CourseElement.POLE, 0.0F, 0.0F, 0.0F, 4.0, 3.0, CourseElement.LEFT);
        pole.change(Row.VARIANT, -1, false);
        assertEquals(CourseElement.RIGHT, pole.variant());
    }

    @Test
    void freeformTogglesItsDirectionOnOddNotches() {
        GateEditModel model = new GateEditModel(CourseElement.FREEFORM, 0.0F, 0.0F, 0.0F, 1.0, 1.0, CourseElement.AWAY);
        model.scroll(1, false);
        assertEquals(CourseElement.TOWARD, model.variant());
        model.scroll(2, false);
        assertEquals(CourseElement.TOWARD, model.variant());
        model.scroll(-1, false);
        assertEquals(CourseElement.AWAY, model.variant());
        assertEquals(CourseElement.AWAY, model.startVariant());
    }

    @Test
    void resetAndTurnKeepTheRestAndSetSnapsLikeTheServer() {
        GateEditModel model = gate(45.0F, 20.0F, 30.0F);
        model.turn(90.0F);
        assertEquals(135.0F, model.heading());
        assertEquals(20.0F, model.pitch());
        model.turn(-270.0F);
        assertEquals(225.0F, model.heading());
        model.reset(45.0F, 2.0, 2.0);
        assertEquals(45.0F, model.heading());
        assertEquals(0.0F, model.pitch());
        assertEquals(0.0F, model.roll());
        assertEquals(2.0, model.width());
        model.set(Row.WIDTH, 3.3);
        assertEquals(3.5, model.width());
        model.set(Row.HEADING, -10.0);
        assertEquals(350.0F, model.heading());
        model.set(Float.NaN, Float.NaN, 0.0F, 2.0, 2.0);
        assertEquals(0.0F, model.heading());
        assertEquals(0.0F, model.pitch());
    }

    @Test
    void gridStepsLandOnMultiplesFromAnyValue() {
        assertEquals(10.0, GateEditModel.onGrid(7.2, 5.0, 1), 1.0E-9);
        assertEquals(5.0, GateEditModel.onGrid(7.2, 5.0, -1), 1.0E-9);
        assertEquals(15.0, GateEditModel.onGrid(10.0, 5.0, 1), 1.0E-9);
        assertEquals(5.0, GateEditModel.onGrid(10.0, 5.0, -1), 1.0E-9);
        assertEquals(-10.0, GateEditModel.onGrid(-7.0, 5.0, -1), 1.0E-9);
        assertEquals(3.5, GateEditModel.onGrid(3.0, 0.5, 1), 1.0E-9);
        assertEquals(0.0F, GateEditModel.wrapHeading(360.0F));
        assertEquals(0.0F, GateEditModel.wrapHeading(-360.0F));
        assertEquals(359.0F, GateEditModel.wrapHeading(-1.0F));
    }
    @Test
    void signalRowsLiveOnTheirOwnPage() {
        GateEditModel model = gate(0.0F, 0.0F, 0.0F);
        assertEquals(2, model.pages());
        assertEquals(List.of(Row.HEADING, Row.PITCH, Row.ROLL, Row.WIDTH, Row.HEIGHT), model.rows(0));
        assertEquals(List.of(Row.SIGNAL, Row.PULSE, Row.RACE_ONLY), model.rows(1));
        assertEquals(GateEditModel.Section.SIGNAL, GateEditModel.section(Row.PULSE));
        assertEquals(0, model.page());
        model.moveSelection(-1);
        assertEquals(1, model.page(), "Schleichen + Mausrad läuft über beide Seiten");
        model.showPage(0);
        assertEquals(Row.HEADING, model.selected());
        model.showPage(1);
        assertEquals(Row.SIGNAL, model.selected());
        GateEditModel freeform = new GateEditModel(CourseElement.FREEFORM, 0.0F, 0.0F, 0.0F, 1.0, 1.0, 0);
        assertEquals(List.of(Row.VARIANT), freeform.rows(0));
        GateEditModel flag = new GateEditModel(CourseElement.FLAG, 0.0F, 0.0F, 0.0F, 1.0, 1.0, 0);
        assertEquals(1, flag.pages());
        flag.showPage(1);
        assertEquals(Row.HEADING, flag.selected());
    }

    @Test
    void signalRowsChangeTheSignalOnly() {
        GateSignal start = new GateSignal(true, 10, false);
        GateEditModel model = new GateEditModel(CourseElement.GATE, 90.0F, 0.0F, 0.0F, 3.0, 3.0, 0, start);
        assertEquals(start, model.signal());
        assertFalse(model.signalChanged());
        model.change(Row.PULSE, 1, false);
        assertEquals(12, model.signal().pulseTicks());
        model.change(Row.PULSE, -1, true);
        assertEquals(11, model.signal().pulseTicks());
        model.change(Row.PULSE, 1, false);
        assertEquals(12, model.signal().pulseTicks(), "Mausrad rastet auf gerade Ticks");
        model.change(Row.PULSE, -100, false);
        assertEquals(GateSignal.MIN_PULSE, model.signal().pulseTicks());
        model.set(Row.PULSE, 1000.0);
        assertEquals(GateSignal.MAX_PULSE, model.signal().pulseTicks());
        model.change(Row.SIGNAL, 1, false);
        assertFalse(model.signal().enabled());
        model.change(Row.SIGNAL, 2, false);
        assertFalse(model.signal().enabled(), "zwei Rasten schalten hin und zurück");
        model.change(Row.SIGNAL, -1, false);
        assertTrue(model.signal().enabled());
        model.change(Row.RACE_ONLY, 1, false);
        assertTrue(model.signal().raceOnly());
        assertTrue(model.signalChanged());
        assertEquals(90.0F, model.heading());
        assertEquals(3.0, model.width());
        model.set(Row.PULSE, 10.0);
        model.set(Row.RACE_ONLY, 0.0);
        assertFalse(model.signalChanged());
    }
}
