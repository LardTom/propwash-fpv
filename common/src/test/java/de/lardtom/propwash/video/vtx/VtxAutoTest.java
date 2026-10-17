package de.lardtom.propwash.video.vtx;

import de.lardtom.propwash.video.LinkBudget;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VtxAutoTest {
    private static final VtxTable HD = VtxTable.of(true, 700, List.of(25, 100, 400, 700, 1200), 9.0, 23.0);
    private static final double FRAME = 0.05;

    private static int run(VtxAuto auto, double ratedMarginDb, double seconds) {
        int index = auto.index();
        for (double t = 0.0; t < seconds; t += FRAME) {
            index = auto.update(FRAME, ratedMarginDb, HD);
        }
        return index;
    }

    @Test
    void closeToThePilotItSettlesOnTheLowestLevel() {
        VtxAuto auto = new VtxAuto();
        auto.reset(HD.ratedIndex());
        assertEquals(0, run(auto, 40.0, 30.0));
    }

    @Test
    void stepsDownOneLevelAtATimeAndSlowly() {
        VtxAuto auto = new VtxAuto();
        auto.reset(HD.ratedIndex());
        assertEquals(HD.ratedIndex(), run(auto, 40.0, VtxAuto.DOWN_DELAY_S - 0.2));
        assertEquals(HD.ratedIndex() - 1, run(auto, 40.0, 0.4));
    }

    @Test
    void jumpsUpQuicklyWhenTheLinkGetsWeak() {
        VtxAuto auto = new VtxAuto();
        auto.reset(0);
        assertEquals(HD.maxIndex(), run(auto, -5.0, VtxAuto.UP_DELAY_S + 0.1));
    }

    @Test
    void picksTheLowestLevelThatKeepsTheTargetMargin() {
        VtxAuto auto = new VtxAuto();
        auto.reset(0);
        int index = run(auto, 9.0, 1.0);
        assertTrue(9.0 + HD.gainDb(index) >= VtxAuto.DIGITAL_TARGET_DB, "Reserve zu klein bei " + HD.levelMw(index));
        assertTrue(index == 0 || 9.0 + HD.gainDb(index - 1) < VtxAuto.DIGITAL_TARGET_DB, "eine Stufe weniger hätte gereicht");
        assertEquals(1200, HD.levelMw(index));
        VtxAuto hopeless = new VtxAuto();
        hopeless.reset(0);
        assertEquals(HD.maxIndex(), run(hopeless, 2.0, 1.0));
    }

    @Test
    void hysteresisAvoidsPumpingAroundTheThreshold() {
        VtxAuto auto = new VtxAuto();
        auto.reset(0);
        double margin = VtxAuto.DIGITAL_TARGET_DB - HD.gainDb(2) + 0.5;
        int index = run(auto, margin, 2.0);
        assertEquals(2, index);
        assertEquals(2, run(auto, margin + 1.5, 10.0));
    }

    @Test
    void unknownMarginKeepsTheLevel() {
        VtxAuto auto = new VtxAuto();
        auto.reset(3);
        assertEquals(3, run(auto, Double.NaN, 10.0));
    }

    @Test
    void analogAutoKeepsTheReserveACleanAnalogPictureNeeds() {
        VtxTable race = VtxTable.of(false, 400, List.of(25, 200, 400, 800, 1600), 3.5, 8.0);
        assertEquals(VtxAuto.ANALOG_TARGET_DB, VtxAuto.targetDb(race));
        assertEquals(VtxAuto.DIGITAL_TARGET_DB, VtxAuto.targetDb(HD));
        assertTrue(VtxAuto.ANALOG_TARGET_DB > LinkBudget.ANALOG_CLEAN_DB);
        assertTrue(VtxAuto.DIGITAL_TARGET_DB > LinkBudget.DIGITAL_CLEAN_DB);
        VtxAuto auto = new VtxAuto();
        auto.reset(race.ratedIndex());
        int index = auto.index();
        for (double t = 0.0; t < 1.0; t += FRAME) {
            index = auto.update(FRAME, 14.0, race);
        }
        assertEquals(3, index, "14 dB bei Nennleistung reichen analog nicht, eine Stufe höher (+3 dB) schon");
        for (double t = 0.0; t < 20.0; t += FRAME) {
            index = auto.update(FRAME, 16.0, race);
        }
        assertEquals(3, index, "16 dB: zurück auf die Nennleistung erst ab 18 dB (Hysterese)");
        for (double t = 0.0; t < 20.0; t += FRAME) {
            index = auto.update(FRAME, 40.0, race);
        }
        assertEquals(0, index);
    }
}
