package de.lardtom.propwash.freecam.client;

import com.google.gson.Gson;
import de.lardtom.propwash.race.client.GateHintMemory;
import de.lardtom.propwash.ui.HintMemory;
import de.lardtom.propwash.ui.client.FirstUseHints;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CovFreecamIntroHintTest {
    private static final String PLAYER = "0f8c2a5e-1d4b-4c3a-9e7f-2b6d8a1c3e5f";
    private static final String OTHER = "local";

    @Test
    void theIntroIsShownExactlyOnce() {
        assertEquals("freecam_intro", FirstUseHints.FREECAM_INTRO);
        assertEquals(1, FreecamClient.INTRO_TIMES);
        HintMemory.Seen seen = new HintMemory.Seen();
        assertTrue(HintMemory.fresh(seen, PLAYER, FirstUseHints.FREECAM_INTRO, FreecamClient.INTRO_TIMES));
        assertTrue(HintMemory.shown(seen, PLAYER, FirstUseHints.FREECAM_INTRO, FreecamClient.INTRO_TIMES));
        assertFalse(HintMemory.fresh(seen, PLAYER, FirstUseHints.FREECAM_INTRO, FreecamClient.INTRO_TIMES));
        assertFalse(HintMemory.shown(seen, PLAYER, FirstUseHints.FREECAM_INTRO, FreecamClient.INTRO_TIMES));
        assertEquals(1, HintMemory.count(seen, PLAYER, FirstUseHints.FREECAM_INTRO));
        assertTrue(HintMemory.fresh(seen, OTHER, FirstUseHints.FREECAM_INTRO, FreecamClient.INTRO_TIMES));
    }

    @Test
    void theCountSurvivesTheHintFile() {
        HintMemory.Seen seen = new HintMemory.Seen();
        HintMemory.shown(seen, PLAYER, FirstUseHints.FREECAM_INTRO, FreecamClient.INTRO_TIMES);
        Gson gson = new Gson();
        HintMemory.Seen loaded = gson.fromJson(gson.toJson(seen), HintMemory.Seen.class);
        HintMemory.sanitize(loaded);
        assertFalse(HintMemory.fresh(loaded, PLAYER, FirstUseHints.FREECAM_INTRO, FreecamClient.INTRO_TIMES));
    }

    @Test
    void resettingTheHintsBringsTheIntroBack() {
        HintMemory.Seen seen = new HintMemory.Seen();
        HintMemory.shown(seen, PLAYER, FirstUseHints.FREECAM_INTRO, FreecamClient.INTRO_TIMES);
        HintMemory.shown(seen, PLAYER, "gate_edit_mouse", 3);
        HintMemory.shown(seen, OTHER, FirstUseHints.FREECAM_INTRO, FreecamClient.INTRO_TIMES);
        assertTrue(GateHintMemory.forgetHints(seen, PLAYER));
        assertTrue(HintMemory.fresh(seen, PLAYER, FirstUseHints.FREECAM_INTRO, FreecamClient.INTRO_TIMES));
        assertFalse(HintMemory.fresh(seen, OTHER, FirstUseHints.FREECAM_INTRO, FreecamClient.INTRO_TIMES));
        assertTrue(HintMemory.shown(seen, PLAYER, FirstUseHints.FREECAM_INTRO, FreecamClient.INTRO_TIMES));
        assertFalse(HintMemory.fresh(seen, PLAYER, FirstUseHints.FREECAM_INTRO, FreecamClient.INTRO_TIMES));
        assertFalse(GateHintMemory.forgetHints(new HintMemory.Seen(), PLAYER));
    }

    @Test
    void forgettingOnlyTheIntroKeepsOtherHints() {
        HintMemory.Seen seen = new HintMemory.Seen();
        HintMemory.shown(seen, PLAYER, FirstUseHints.FREECAM_INTRO, FreecamClient.INTRO_TIMES);
        HintMemory.shown(seen, PLAYER, "gate_edit_mouse", 3);
        assertTrue(HintMemory.forget(seen, PLAYER, FirstUseHints.FREECAM_INTRO));
        assertTrue(HintMemory.fresh(seen, PLAYER, FirstUseHints.FREECAM_INTRO, FreecamClient.INTRO_TIMES));
        assertEquals(1, HintMemory.count(seen, PLAYER, "gate_edit_mouse"));
    }
}
