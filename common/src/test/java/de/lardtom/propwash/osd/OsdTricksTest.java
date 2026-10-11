package de.lardtom.propwash.osd;

import de.lardtom.propwash.trick.Trick;
import de.lardtom.propwash.trick.TrickSample;
import de.lardtom.propwash.trick.TrickScore;
import de.lardtom.propwash.trick.TrickSession;
import de.lardtom.propwash.trick.TrickType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsdTricksTest {
    @Test
    void trickElementSitsBelowTheCentreAndIsOffForRacing() {
        assertTrue(OsdPreset.MINIMAL.enabled(OsdElement.TRICKS));
        assertTrue(OsdPreset.STANDARD.enabled(OsdElement.TRICKS));
        assertTrue(OsdPreset.FULL.enabled(OsdElement.TRICKS));
        assertFalse(OsdPreset.RACE.enabled(OsdElement.TRICKS));
        assertTrue(OsdElement.TRICKS.movable());
        assertSame(OsdElement.Align.CENTER, OsdElement.TRICKS.align());
        assertTrue(OsdPreset.MINIMAL.row(OsdElement.TRICKS) > 11);
        assertSame(OsdElement.TRICKS, OsdElement.byKey("tricks"));
    }

    @Test
    void oldConfigsGainTheElementFromTheirPreset() {
        OsdConfig config = OsdConfig.defaults();
        config.applyPreset(OsdPreset.STANDARD);
        config.elements.remove(OsdElement.TRICKS.key());
        OsdConfig.sanitize(config);
        assertTrue(config.enabled(OsdElement.TRICKS));
        assertTrue(OsdPreset.STANDARD.matches(config));
        config.slot(OsdElement.TRICKS).enabled = false;
        assertTrue(config.customized());
        OsdConfig race = OsdConfig.defaults();
        race.applyPreset(OsdPreset.RACE);
        race.elements = new LinkedHashMap<>(Map.of());
        OsdConfig.sanitize(race);
        assertFalse(race.enabled(OsdElement.TRICKS));
    }

    @Test
    void sessionStateReachesTheOsdData() {
        TrickSession session = new TrickSession();
        TrickSample sample = new TrickSample();
        sample.armed = true;
        sample.time = 1.0;
        session.update(sample);
        OsdData data = new OsdData();
        data.applyTricks(session);
        assertTrue(data.tricksOn);
        assertEquals(OsdData.TRICK_NONE, data.trickPopup);
        assertEquals(Double.POSITIVE_INFINITY, data.trickAgeSeconds);

        TrickScore score = new TrickScore();
        score.update(sample, 1.0, 0.0, false, List.of(new Trick(TrickType.ORBIT, 1.0, 1.0, Double.POSITIVE_INFINITY)));
        sample.time = 1.6;
        score.update(sample, 1.0, 0.0, false, List.of());
        assertEquals(TrickScore.Popup.TRICK, score.popup());
        assertEquals(TrickType.ORBIT, score.popupType());
        assertEquals(250, score.popupPoints());

        data.clearTricks();
        assertFalse(data.tricksOn);
        assertNull(data.trickType);
        assertEquals(0, data.trickScore);
    }

    @Test
    void flightStatsKeepTheTrickScoreOfTheFlight() {
        OsdData data = new OsdData();
        data.armed = true;
        data.tricksOn = true;
        data.trickScore = 450;
        data.trickBestCombo = 3;
        data.trickBestComboPoints = 380;
        FlightStats stats = new FlightStats();
        stats.accumulate(data, 0.1);
        assertTrue(stats.tricksOn);
        assertEquals(450, stats.trickScore);
        assertEquals(3, stats.bestCombo);
        assertEquals(380, stats.bestComboPoints);
        data.tricksOn = false;
        data.trickScore = 0;
        stats.accumulate(data, 0.1);
        assertTrue(stats.tricksOn);
        assertEquals(450, stats.trickScore);
        FlightStats copy = new FlightStats();
        stats.copyTo(copy);
        assertEquals(450, copy.trickScore);
        assertEquals(3, copy.bestCombo);
        stats.reset();
        assertFalse(stats.tricksOn);
        assertEquals(0, stats.trickScore);
    }

    @Test
    void samplerClearsTrickValuesEveryFrame() {
        boolean[] tricks = {true};
        OsdSampler sampler = new OsdSampler(new OsdSampler.Source() {
            @Override
            public boolean active() {
                return true;
            }

            @Override
            public void fill(OsdData data) {
                data.armed = true;
                if (tricks[0]) {
                    data.tricksOn = true;
                    data.trickScore = 120;
                }
            }
        });
        OsdData first = sampler.sample(1_000_000_000L);
        assertTrue(first.tricksOn);
        tricks[0] = false;
        OsdData second = sampler.sample(1_020_000_000L);
        assertFalse(second.tricksOn);
        assertEquals(0, second.trickScore);
        assertTrue(second.stats.tricksOn);
        assertEquals(120, second.stats.trickScore);
    }

    @Test
    void editorSampleShowsAPopupAndTrickStats() {
        OsdData sample = OsdData.sample();
        assertTrue(sample.tricksOn);
        assertEquals(OsdData.TRICK_DONE, sample.trickPopup);
        assertTrue(sample.stats.tricksOn);
        assertTrue(sample.stats.trickScore > 0);
    }
}
