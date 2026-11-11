package de.lardtom.propwash.race.client;

import de.lardtom.propwash.race.CourseElement;
import de.lardtom.propwash.race.GateMounting;
import de.lardtom.propwash.race.GateSignal;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.GateTilt;
import de.lardtom.propwash.race.ScoreArea;
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

    private static List<Row> concat(List<Row> first, List<Row> second) {
        List<Row> all = new java.util.ArrayList<>(first);
        all.addAll(second);
        return all;
    }

    @Test
    void everyKindHasItsOwnRowsInPanelOrder() {
        List<Row> position = List.of(Row.NUDGE_STEP, Row.MOVE_X, Row.MOVE_Y, Row.MOVE_Z);
        assertEquals(concat(List.of(Row.HEADING, Row.PITCH, Row.ROLL, Row.WIDTH, Row.HEIGHT, Row.SIGNAL, Row.PULSE, Row.RACE_ONLY), position),
                GateEditModel.rows(CourseElement.GATE));
        for (CourseElement element : List.of(CourseElement.FLAG, CourseElement.POLE)) {
            assertEquals(concat(List.of(Row.HEADING, Row.VARIANT, Row.WIDTH, Row.HEIGHT, Row.SCORE_BELOW, Row.SCORE_ABOVE), position),
                    GateEditModel.rows(element), element.name());
        }
        assertEquals(concat(List.of(Row.HEADING, Row.VARIANT, Row.WIDTH, Row.HEIGHT), position), GateEditModel.rows(CourseElement.CUBE));
        assertEquals(concat(List.of(Row.HEADING, Row.VARIANT, Row.HEIGHT, Row.SCORE_ROLE, Row.SCORE_SIDE, Row.SCORE_REACH, Row.SCORE_BELOW,
                Row.SCORE_ABOVE), position), GateEditModel.rows(CourseElement.GATE_POLE));
        assertEquals(List.of(Row.VARIANT, Row.OPEN_EDGES, Row.SIGNAL, Row.PULSE, Row.RACE_ONLY), GateEditModel.rows(CourseElement.FREEFORM));
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
        assertEquals(Row.MOVE_Z, model.selected());
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
        assertEquals(3, model.pages());
        assertEquals(List.of(Row.HEADING, Row.PITCH, Row.ROLL, Row.WIDTH, Row.HEIGHT), model.rows(0));
        assertEquals(List.of(Row.SIGNAL, Row.PULSE, Row.RACE_ONLY), model.rows(1));
        assertEquals(List.of(Row.NUDGE_STEP, Row.MOVE_X, Row.MOVE_Y, Row.MOVE_Z), model.rows(2));
        assertEquals(GateEditModel.Section.SIGNAL, GateEditModel.section(Row.PULSE));
        assertEquals(GateEditModel.Page.POSITION, model.pageKind(2));
        assertEquals(0, model.page());
        model.moveSelection(-1);
        assertEquals(2, model.page(), "Schleichen + Mausrad läuft über alle Seiten");
        model.moveSelection(-4);
        assertEquals(1, model.page());
        model.showPage(0);
        assertEquals(Row.HEADING, model.selected());
        model.showPage(1);
        assertEquals(Row.SIGNAL, model.selected());
        GateEditModel freeform = new GateEditModel(CourseElement.FREEFORM, 0.0F, 0.0F, 0.0F, 1.0, 1.0, 0);
        assertEquals(List.of(Row.VARIANT, Row.OPEN_EDGES), freeform.rows(0));
        assertEquals(2, freeform.pages());
        GateEditModel flag = new GateEditModel(CourseElement.FLAG, 0.0F, 0.0F, 0.0F, 1.0, 1.0, 0);
        assertEquals(3, flag.pages());
        assertEquals(GateEditModel.Page.SCORE, flag.pageKind(1));
        flag.showPage(1);
        assertEquals(Row.SCORE_BELOW, flag.selected());
        flag.showPage(3);
        assertEquals(Row.HEADING, flag.selected());
        GateEditModel cube = new GateEditModel(CourseElement.CUBE, 0.0F, 0.0F, 0.0F, 3.0, 3.0, 0);
        assertEquals(2, cube.pages());
        assertEquals(GateEditModel.Page.POSITION, cube.pageKind(1));
    }

    @Test
    void tabsTurnAroundAndFindThePositionPage() {
        GateEditModel model = gate(0.0F, 0.0F, 0.0F);
        assertEquals(2, model.pageOf(GateEditModel.Page.POSITION));
        assertEquals(1, model.pageOf(GateEditModel.Page.SIGNAL));
        assertEquals(-1, model.pageOf(GateEditModel.Page.SCORE));
        model.turnPage(1);
        assertEquals(1, model.page());
        assertEquals(Row.SIGNAL, model.selected());
        model.turnPage(1);
        assertEquals(GateEditModel.Page.POSITION, model.pageKind(model.page()));
        assertEquals(Row.NUDGE_STEP, model.selected());
        model.turnPage(1);
        assertEquals(0, model.page(), "Tab nach der letzten Seite beginnt vorn");
        model.turnPage(-1);
        assertEquals(2, model.page(), "Umschalt+Tab auf der ersten Seite springt auf die letzte");
        assertFalse(model.moved());
        for (CourseElement kind : List.of(CourseElement.FLAG, CourseElement.POLE, CourseElement.CUBE, CourseElement.GATE_POLE)) {
            GateEditModel element = new GateEditModel(kind, 0.0F, 0.0F, 0.0F, 1.0, 1.0, 0);
            int position = element.pageOf(GateEditModel.Page.POSITION);
            assertEquals(element.pages() - 1, position, kind + ": Position ist der letzte Tab");
            element.turnPage(-1);
            assertEquals(position, element.page(), kind.name());
        }
        GateEditModel freeform = new GateEditModel(CourseElement.FREEFORM, 0.0F, 0.0F, 0.0F, 1.0, 1.0, 0);
        assertEquals(-1, freeform.pageOf(GateEditModel.Page.POSITION));
        freeform.turnPage(1);
        assertEquals(Row.SIGNAL, freeform.selected());
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

    @Test
    void nudgeMovesOnTheStepGridWithinEightBlocks() {
        GateEditModel model = gate(0.0F, 0.0F, 0.0F);
        assertEquals(GateEditModel.DEFAULT_NUDGE_STEP, model.nudgeStep());
        model.change(Row.MOVE_X, 1, false);
        assertEquals(0.25, model.dx());
        model.change(Row.MOVE_X, 1, true);
        assertEquals(0.3125, model.dx(), 1.0E-9, "Strg: 1/16 Block");
        model.change(Row.MOVE_X, 1, false);
        assertEquals(0.5, model.dx(), 1.0E-9, "danach wieder auf dem Raster der Schrittweite");
        model.change(Row.NUDGE_STEP, 1, false);
        assertEquals(1.0, model.nudgeStep());
        model.change(Row.NUDGE_STEP, 5, false);
        assertEquals(1.0, model.nudgeStep(), "Schrittweite bleibt am Ende stehen");
        model.change(Row.NUDGE_STEP, -9, false);
        assertEquals(1.0 / 16.0, model.nudgeStep());
        model.nudge(GateEditModel.AXIS_Y, -3, false);
        assertEquals(-0.1875, model.dy(), 1.0E-9);
        model.setNudgeStep(1.0);
        model.nudge(GateEditModel.AXIS_Z, 20, false);
        assertEquals(8.0, model.dz(), "höchstens 8 Blöcke");
        model.set(Row.MOVE_Z, -100.0);
        assertEquals(-8.0, model.dz());
        model.set(Row.MOVE_X, 0.3);
        assertEquals(0.3125, model.dx(), 1.0E-9, "freie Werte rasten auf 1/16");
        assertTrue(model.moved());
        model.resetOffset();
        assertFalse(model.moved());
        model.setNudgeStep(0.3);
        assertEquals(0.25, model.nudgeStep(), "nur die drei Schrittweiten");
    }

    @Test
    void scoreRowsChangeTheScoringAreaInHalfBlocks() {
        GateEditModel flag = new GateEditModel(CourseElement.FLAG, 0.0F, 0.0F, 0.0F, 5.0, 4.0, CourseElement.LEFT);
        assertEquals(ScoreArea.DEFAULT, flag.score());
        flag.change(Row.SCORE_BELOW, 4, false);
        assertEquals(3.0, flag.score().below());
        flag.change(Row.SCORE_ABOVE, 3, false);
        assertEquals(1.5, flag.score().above());
        flag.change(Row.SCORE_BELOW, -20, false);
        assertEquals(0.0, flag.score().below());
        flag.set(Row.SCORE_ABOVE, 99.0);
        assertEquals(ScoreArea.MAX_EXTENT, flag.score().above());
        assertTrue(flag.scoreChanged());
        flag.resetScore();
        assertEquals(ScoreArea.DEFAULT, flag.score());
        GateEditModel pole = new GateEditModel(CourseElement.GATE_POLE, 0.0F, 0.0F, 0.0F, 0.5, 4.0, 3, GateSignal.DEFAULT, ScoreArea.DEFAULT);
        pole.change(Row.SCORE_ROLE, 1, false);
        assertEquals(ScoreArea.Role.PASS, pole.score().role());
        pole.change(Row.SCORE_ROLE, 1, false);
        assertEquals(ScoreArea.Role.TURN, pole.score().role());
        pole.change(Row.SCORE_ROLE, 1, false);
        assertEquals(ScoreArea.Role.NONE, pole.score().role());
        pole.change(Row.SCORE_SIDE, 1, false);
        assertEquals(CourseElement.RIGHT, pole.score().side());
        pole.change(Row.SCORE_REACH, -20, false);
        assertEquals(ScoreArea.MIN_REACH, pole.score().reach());
        pole.setRoleRequired(true);
        assertEquals(ScoreArea.Role.PASS, pole.score().role(), "im Kurs wertet eine Stange immer");
        assertEquals(List.of(ScoreArea.Role.PASS, ScoreArea.Role.TURN), pole.roles());
        pole.change(Row.SCORE_ROLE, 1, false);
        pole.change(Row.SCORE_ROLE, 1, false);
        assertEquals(ScoreArea.Role.PASS, pole.score().role());
        pole.resetScore();
        assertEquals(ScoreArea.Role.PASS, pole.score().role());
        assertEquals(ScoreArea.DEFAULT_REACH, pole.score().reach());
        GateEditModel freeform = new GateEditModel(CourseElement.FREEFORM, 0.0F, 0.0F, 0.0F, 1.0, 1.0, 0);
        freeform.change(Row.OPEN_EDGES, 1, false);
        assertTrue(freeform.score().open());
        freeform.change(Row.OPEN_EDGES, 2, false);
        assertTrue(freeform.score().open());
        assertEquals(ScoreArea.DEFAULT.withOpen(true), freeform.score());
    }
    @Test
    void ctrlMovesOnePixelAndCtrlShiftHalfAPixel() {
        GateEditModel model = gate(0.0F, 0.0F, 0.0F);
        model.set(Row.MOVE_X, 1.0 / 32.0);
        assertEquals(1.0 / 32.0, model.dx(), 1.0E-12, "1/32 Block bleibt stehen");
        model.set(Row.MOVE_X, 0.3);
        assertEquals(0.3125, model.dx(), 1.0E-12, "freie Werte rasten auf 1/32");
        model.set(Row.MOVE_X, 0.29);
        assertEquals(0.28125, model.dx(), 1.0E-12);
        model.resetOffset();
        model.change(Row.MOVE_X, 1, true, true);
        assertEquals(1.0 / 32.0, model.dx(), 1.0E-12, "Strg+Umschalt: 1/32 Block");
        model.change(Row.MOVE_X, 1, true, false);
        assertEquals(1.0 / 16.0, model.dx(), 1.0E-12, "Strg: auf das 1/16-Raster");
        model.change(Row.MOVE_X, 1, true, true);
        assertEquals(3.0 / 32.0, model.dx(), 1.0E-12);
        model.change(Row.MOVE_X, 1, false, true);
        assertEquals(0.25, model.dx(), 1.0E-12, "Umschalt ohne Strg ist die gewählte Schrittweite");
        model.nudge(GateEditModel.AXIS_Y, -3, true, true);
        assertEquals(-3.0 / 32.0, model.dy(), 1.0E-12);
        model.nudge(GateEditModel.AXIS_Y, -1, true);
        assertEquals(-0.125, model.dy(), 1.0E-12, "Strg allein bleibt 1/16");
        assertEquals(1.0 / 32.0, model.nudgeStep(true, true));
        assertEquals(1.0 / 16.0, model.nudgeStep(true, false));
        assertEquals(GateEditModel.DEFAULT_NUDGE_STEP, model.nudgeStep(false, true));
        model.set(Row.MOVE_Z, GateMounting.MAX_NUDGE + 1.0 / 32.0);
        assertEquals(GateMounting.MAX_NUDGE, model.dz(), "Grenze bleibt bei 8 Blöcken");
        model.change(Row.HEADING, 1, true, true);
        assertEquals(1.0F, model.heading(), "Winkel: Strg+Umschalt wie Strg");
        assertEquals("0.03125", GateMounting.blocks(1.0 / 32.0));
        assertEquals("-0.09375", GateMounting.signedBlocks(-3.0 / 32.0));
        assertEquals("+0.0625", GateMounting.signedBlocks(1.0 / 16.0));
        assertEquals("8", GateMounting.blocks(GateMounting.MAX_NUDGE));
    }

    @Test
    void lyingFreeformGatesTurnInQuarterSteps() {
        GateEditModel lying = new GateEditModel(CourseElement.FREEFORM, true, 0.0F, 0.0F, 0.0F, 1.0, 1.0, CourseElement.AWAY, GateSignal.DEFAULT,
                ScoreArea.DEFAULT);
        assertEquals(List.of(Row.VARIANT, Row.HEADING, Row.OPEN_EDGES, Row.SIGNAL, Row.PULSE, Row.RACE_ONLY), lying.rows());
        assertEquals(lying.rows(), GateEditModel.rows(CourseElement.FREEFORM, true));
        assertEquals(GateEditModel.rows(CourseElement.FREEFORM), GateEditModel.rows(CourseElement.FREEFORM, false), "senkrecht ohne Ausrichtung");
        lying.change(Row.HEADING, 1, false);
        assertEquals(90.0F, lying.heading());
        lying.change(Row.HEADING, 1, true);
        assertEquals(180.0F, lying.heading(), "Strg ändert die Vierteldrehung nicht");
        lying.change(Row.HEADING, -3, false);
        assertEquals(270.0F, lying.heading());
        lying.set(Row.HEADING, 100.0);
        assertEquals(90.0F, lying.heading());
        lying.set(Row.HEADING, 359.0);
        assertEquals(0.0F, lying.heading());
        GateEditModel turned = new GateEditModel(CourseElement.FREEFORM, true, 180.0F, 0.0F, 0.0F, 1.0, 1.0, CourseElement.AWAY, GateSignal.DEFAULT,
                ScoreArea.DEFAULT);
        assertEquals(180.0F, turned.heading());
        assertEquals(0.0F, turned.pitch());
    }
}
