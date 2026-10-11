package de.lardtom.propwash.trick;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrickScoreTest {
    private static final double DT = 1.0 / 60.0;
    private static final double FAR = Double.POSITIVE_INFINITY;

    private final TrickScore score = new TrickScore();
    private final TrickSample sample = new TrickSample();
    private final List<Trick> found = new ArrayList<>();
    private double upY = 1.0;
    private double rate;
    private boolean contact;

    TrickScoreTest() {
        this.sample.armed = true;
        this.sample.time = 0.0;
        this.tick();
    }

    private void tick() {
        this.score.update(this.sample, this.upY, this.rate, this.contact, this.found);
        this.found.clear();
    }

    private void advance(double seconds) {
        int frames = (int) Math.round(seconds / DT);
        for (int i = 0; i < frames; i++) {
            this.sample.time += DT;
            this.tick();
        }
    }

    private void trick(TrickType type) {
        this.trick(type, 0.0, FAR);
    }

    private void trick(TrickType type, double magnitude, double clearance) {
        this.sample.time += DT;
        this.found.add(new Trick(type, this.sample.time, magnitude, clearance));
        this.tick();
    }

    @Test
    void singleTrickIsConfirmedAfterTheRecoveryWindow() {
        this.trick(TrickType.FLIP);
        assertEquals(0, this.score.flightScore());
        assertEquals(1, this.score.pendingCount());
        this.advance(TrickScore.CONFIRM_SECONDS + 0.05);
        assertEquals(120, this.score.flightScore());
        assertEquals(TrickScore.Popup.TRICK, this.score.popup());
        assertEquals(TrickType.FLIP, this.score.popupType());
        assertEquals(120, this.score.popupPoints());
        assertTrue(this.score.popupClean());
        this.advance(TrickScore.COMBO_WINDOW + 0.1);
        assertEquals(120, this.score.flightScore());
        assertEquals(1, this.score.bestCombo());
        assertEquals(120, this.score.bestComboPoints());
        assertEquals(TrickScore.Popup.TRICK, this.score.popup());
    }

    @Test
    void sloppyRecoveryLosesTheCleanBonus() {
        this.rate = 600.0;
        this.trick(TrickType.ROLL);
        this.advance(0.6);
        assertEquals(100, this.score.flightScore());
        assertFalse(this.score.popupClean());
    }

    @Test
    void chainedTricksMultiplyWhenTheComboEnds() {
        this.trick(TrickType.FLIP);
        this.advance(1.0);
        this.trick(TrickType.ROLL);
        this.advance(1.0);
        this.trick(TrickType.POWER_LOOP);
        this.advance(0.6);
        assertEquals(3, this.score.comboCount());
        assertEquals(TrickScore.Popup.TRICK, this.score.popup());
        assertEquals(3, this.score.popupCombo());
        int base = 120 + 120 + 300;
        assertEquals(base, this.score.flightScore());
        this.advance(TrickScore.COMBO_WINDOW);
        int bonus = TrickScore.round5(base * (TrickScore.multiplier(3) - 1.0));
        assertEquals(base + bonus, this.score.flightScore());
        assertEquals(TrickScore.Popup.COMBO, this.score.popup());
        assertEquals(bonus, this.score.popupPoints());
        assertEquals(3, this.score.bestCombo());
        assertEquals(base + bonus, this.score.bestComboPoints());
        assertEquals(0, this.score.comboCount());
    }

    @Test
    void multiplierGrowsAndCaps() {
        assertEquals(1.0, TrickScore.multiplier(1));
        assertEquals(1.5, TrickScore.multiplier(2));
        assertEquals(2.0, TrickScore.multiplier(3));
        assertEquals(TrickScore.COMBO_MAX, TrickScore.multiplier(7));
        assertEquals(TrickScore.COMBO_MAX, TrickScore.multiplier(30));
    }

    @Test
    void repeatingTheSameTrickIsWorthLess() {
        this.trick(TrickType.FLIP);
        this.advance(0.6);
        this.trick(TrickType.FLIP);
        this.advance(0.6);
        this.trick(TrickType.FLIP);
        this.advance(0.6);
        assertEquals(120 + TrickScore.round5(120 * TrickScore.REPEAT_FACTOR)
                + TrickScore.round5(120 * TrickScore.REPEAT_FACTOR * TrickScore.REPEAT_FACTOR), this.score.flightScore());
    }

    @Test
    void crashLosesTheComboBonusButKeepsTheTricks() {
        this.trick(TrickType.FLIP);
        this.advance(1.0);
        this.trick(TrickType.ROLL);
        this.advance(1.0);
        this.sample.crashCount = 1;
        this.advance(0.1);
        assertEquals(240, this.score.flightScore());
        assertEquals(TrickScore.Popup.LOST, this.score.popup());
        assertEquals(2, this.score.popupCombo());
        assertEquals(0, this.score.comboCount());
        assertEquals(0, this.score.bestCombo());
        this.advance(TrickScore.COMBO_WINDOW + 1.0);
        assertEquals(240, this.score.flightScore());
    }

    @Test
    void crashRightAfterATrickVoidsIt() {
        this.trick(TrickType.DOUBLE_FLIP);
        this.advance(0.2);
        this.sample.crashCount = 1;
        this.advance(1.0);
        assertEquals(0, this.score.flightScore());
        assertEquals(TrickScore.Popup.NONE, this.score.popup());
    }

    @Test
    void disarmBreaksTheCombo() {
        this.trick(TrickType.FLIP);
        this.advance(1.0);
        this.trick(TrickType.ROLL);
        this.advance(1.0);
        this.sample.armed = false;
        this.advance(0.1);
        assertEquals(TrickScore.Popup.LOST, this.score.popup());
        assertEquals(240, this.score.flightScore());
        this.sample.armed = true;
        this.advance(0.1);
        assertEquals(0, this.score.flightScore());
        assertEquals(TrickScore.Popup.NONE, this.score.popup());
    }

    @Test
    void touchingSomethingDropsPendingTricks() {
        this.trick(TrickType.DIVE, 20.0, FAR);
        this.advance(0.1);
        this.contact = true;
        this.advance(0.05);
        this.contact = false;
        this.advance(1.0);
        assertEquals(0, this.score.flightScore());
    }

    @Test
    void closeCallsEarnAStyleBonus() {
        this.rate = 600.0;
        this.trick(TrickType.FLIP, 1.0, 0.8);
        this.advance(0.6);
        assertEquals(150, this.score.flightScore());
        assertTrue(this.score.popupNear());
        this.advance(TrickScore.COMBO_WINDOW + 0.1);
        this.trick(TrickType.FLIP, 1.0, 1.8);
        this.advance(0.6);
        assertEquals(150 + 125, this.score.flightScore());
        this.advance(TrickScore.COMBO_WINDOW + 0.1);
        this.trick(TrickType.GAP, 1.0, 0.5);
        this.advance(0.6);
        assertEquals(150 + 125 + TrickScore.round5(TrickType.GAP.points(1.0, 0.5)), this.score.flightScore());
    }

    @Test
    void slowChainsAreSeparateCombos() {
        this.trick(TrickType.FLIP);
        this.advance(TrickScore.COMBO_WINDOW + 0.5);
        this.trick(TrickType.ROLL);
        this.advance(TrickScore.COMBO_WINDOW + 0.5);
        assertEquals(240, this.score.flightScore());
        assertEquals(1, this.score.bestCombo());
    }

    @Test
    void newFlightStartsAtZero() {
        this.trick(TrickType.FLIP);
        this.advance(TrickScore.COMBO_WINDOW + 1.0);
        assertEquals(120, this.score.flightScore());
        this.sample.armed = false;
        this.advance(0.5);
        assertEquals(120, this.score.flightScore());
        this.sample.armed = true;
        this.advance(0.1);
        assertEquals(0, this.score.flightScore());
        assertEquals(0, this.score.bestCombo());
    }

    @Test
    void tricksFlownIntoEachOtherFormAChainAndShareOnePopup() {
        this.advance(2.0);
        double now = this.sample.time + DT;
        this.found.add(new Trick(TrickType.FLIP, now - 1.0, 1.0, FAR, now - 1.6));
        this.found.add(new Trick(TrickType.ROLL, now - 0.5, 1.0, FAR, now - 1.0));
        this.sample.time = now;
        this.tick();
        int linked = TrickScore.round5(100 * (1.0 + TrickScore.CLEAN_BONUS + TrickScore.CHAIN_BONUS));
        assertEquals(120 + linked, this.score.flightScore());
        assertEquals(TrickScore.Popup.TRICK, this.score.popup());
        assertEquals(2, this.score.popupTypeCount());
        assertEquals(TrickType.FLIP, this.score.popupType(0));
        assertEquals(TrickType.ROLL, this.score.popupType(1));
        assertEquals(TrickType.ROLL, this.score.popupType());
        assertEquals(120 + linked, this.score.popupPoints());
        assertEquals(2, this.score.popupCombo());
        assertTrue(this.score.popupChain());
        assertEquals(2, this.score.chain());
        assertEquals(2, this.score.bestChain());
    }

    @Test
    void aBreathBetweenTricksIsNoChain() {
        this.trick(TrickType.FLIP);
        this.advance(0.6);
        this.trick(TrickType.ROLL);
        this.advance(0.6);
        assertEquals(240, this.score.flightScore());
        assertFalse(this.score.popupChain());
        assertEquals(1, this.score.popupTypeCount());
        assertEquals(1, this.score.chain());
    }

    @Test
    void longBatchesShowTheLastThreeTricks() {
        this.advance(3.0);
        double now = this.sample.time + DT;
        TrickType[] types = {TrickType.FLIP, TrickType.ROLL, TrickType.JUICY_FLICK, TrickType.RUBIKS_CUBE};
        for (int i = 0; i < types.length; i++) {
            this.found.add(new Trick(types[i], now - 2.1 + 0.4 * (i + 1), 1.0, FAR, now - 2.1 + 0.4 * i));
        }
        this.sample.time = now;
        this.tick();
        assertEquals(TrickScore.POPUP_TYPES, this.score.popupTypeCount());
        assertEquals(TrickType.ROLL, this.score.popupType(0));
        assertEquals(TrickType.RUBIKS_CUBE, this.score.popupType(2));
        assertEquals(4, this.score.popupTricks());
        assertEquals(4, this.score.chain());
    }

    @Test
    void crashEndsTheChain() {
        this.trick(TrickType.FLIP);
        this.advance(0.6);
        this.sample.crashCount = 1;
        this.advance(0.1);
        assertEquals(0, this.score.chain());
    }

    @Test
    void aLinkedTrickJoinsTheVisiblePopup() {
        this.advance(1.0);
        double now = this.sample.time + DT;
        this.found.add(new Trick(TrickType.FLIP, now - 0.5, 1.0, FAR, now - 1.1));
        this.found.add(new Trick(TrickType.ROLL, now - 0.1, 1.0, FAR, now - 0.5));
        this.sample.time = now;
        this.tick();
        assertEquals(1, this.score.popupTypeCount());
        this.advance(0.5);
        assertEquals(2, this.score.popupTypeCount());
        assertEquals(TrickType.ROLL, this.score.popupType(1));
        assertEquals(this.score.flightScore(), this.score.popupPoints());
        assertTrue(this.score.popupAge() < 0.2);
    }

    @Test
    void lineBonusAddsPointsWithoutGrowingComboOrChain() {
        this.sample.time += DT;
        double start = this.sample.time;
        this.found.add(new Trick(TrickType.EJECT, this.sample.time, 10.0, FAR, start));
        this.tick();
        this.sample.time += 0.3;
        this.found.add(new Trick(TrickType.SPLIT_S, this.sample.time, 3.0, FAR, this.sample.time - 0.6));
        this.tick();
        this.sample.time += 0.6;
        this.found.add(new Trick(TrickType.JUICY_FLICK, this.sample.time, 360.0, FAR, this.sample.time - 0.5));
        this.found.add(new Trick(TrickType.SBANG, this.sample.time, 3.0, FAR, start));
        this.tick();
        this.advance(TrickScore.CONFIRM_SECONDS + 0.05);
        assertEquals(3, this.score.comboCount());
        assertEquals(3, this.score.chain());
        assertEquals(TrickScore.Popup.TRICK, this.score.popup());
        assertEquals(TrickType.SBANG, this.score.popupType(this.score.popupTypeCount() - 1));
        int withBonus = this.score.flightScore();
        assertTrue(withBonus >= TrickType.SBANG.basePoints() + 60 + 200 + 200, "Punkte " + withBonus);
        this.advance(TrickScore.COMBO_WINDOW + 0.1);
        assertEquals(3, this.score.bestCombo());
        assertEquals(TrickScore.Popup.COMBO, this.score.popup());
    }

    @Test
    void lineBonusIsLostWithTheLineOnContact() {
        this.sample.time += DT;
        this.found.add(new Trick(TrickType.RUBIKS_CUBE, this.sample.time, 3.0, FAR, this.sample.time - 1.0));
        this.found.add(new Trick(TrickType.SHINOBI, this.sample.time, 1.0, FAR, this.sample.time - 2.0));
        this.tick();
        this.contact = true;
        this.sample.time += DT;
        this.tick();
        this.contact = false;
        this.advance(TrickScore.CONFIRM_SECONDS + 0.05);
        assertEquals(0, this.score.flightScore());
        assertEquals(0, this.score.tricks());
    }
}
