package de.lardtom.propwash.ui;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HintMemoryTest {
    private static final String HINT = "gate_edit_mouse";
    private static final int TIMES = 3;

    @Test
    void aHintIsFreshForTheFirstFewTimesPerPlayer() {
        HintMemory.Seen seen = new HintMemory.Seen();
        for (int i = 0; i < TIMES; i++) {
            assertTrue(HintMemory.fresh(seen, "anna", HINT, TIMES), "Mal " + (i + 1));
            assertTrue(HintMemory.shown(seen, "anna", HINT, TIMES));
        }
        assertFalse(HintMemory.fresh(seen, "anna", HINT, TIMES));
        assertFalse(HintMemory.shown(seen, "anna", HINT, TIMES));
        assertEquals(TIMES, HintMemory.count(seen, "anna", HINT));
        assertTrue(HintMemory.fresh(seen, "ben", HINT, TIMES));
        assertEquals(0, HintMemory.count(seen, "ben", HINT));
        assertTrue(HintMemory.fresh(seen, "anna", "other", TIMES));
    }

    @Test
    void usingTheFeatureEndsTheHighlightAtOnce() {
        HintMemory.Seen seen = new HintMemory.Seen();
        assertTrue(HintMemory.shown(seen, "anna", HINT, TIMES));
        assertTrue(HintMemory.learned(seen, "anna", HINT, TIMES));
        assertFalse(HintMemory.fresh(seen, "anna", HINT, TIMES));
        assertFalse(HintMemory.learned(seen, "anna", HINT, TIMES));
        assertTrue(HintMemory.forget(seen, "anna", HINT));
        assertTrue(HintMemory.fresh(seen, "anna", HINT, TIMES));
        assertFalse(HintMemory.forget(seen, "anna", HINT));
        assertFalse(HintMemory.forget(seen, "nobody", HINT));
    }

    @Test
    void theFileSurvivesSavingAndBrokenContent() {
        HintMemory.Seen seen = new HintMemory.Seen();
        HintMemory.shown(seen, "anna", HINT, TIMES);
        HintMemory.shown(seen, "anna", HINT, TIMES);
        Gson gson = new Gson();
        HintMemory.Seen loaded = gson.fromJson(gson.toJson(seen), HintMemory.Seen.class);
        HintMemory.sanitize(loaded);
        assertEquals(2, HintMemory.count(loaded, "anna", HINT));
        HintMemory.Seen broken = gson.fromJson("{\"players\":{\"anna\":{\"" + HINT + "\":-4,\"x\":null,\"ok\":2000000000},\"ben\":null,\"\":{}}}",
                HintMemory.Seen.class);
        HintMemory.sanitize(broken);
        assertEquals(0, HintMemory.count(broken, "anna", HINT));
        assertEquals(HintMemory.MAX_COUNT, HintMemory.count(broken, "anna", "ok"));
        assertEquals(1, broken.players.size());
        HintMemory.Seen empty = gson.fromJson("{\"players\":null}", HintMemory.Seen.class);
        HintMemory.sanitize(empty);
        assertTrue(HintMemory.fresh(empty, "anna", HINT, TIMES));
        assertFalse(HintMemory.shown(empty, "", HINT, TIMES));
        assertFalse(HintMemory.shown(empty, "anna", "x".repeat(HintMemory.MAX_KEY_LENGTH + 1), TIMES));
    }

    @Test
    void onlyTheMostRecentPlayersAreKept() {
        HintMemory.Seen seen = new HintMemory.Seen();
        for (int i = 0; i < HintMemory.MAX_PLAYERS + 5; i++) {
            HintMemory.shown(seen, "p" + i, HINT, TIMES);
        }
        assertEquals(HintMemory.MAX_PLAYERS, seen.players.size());
        assertEquals(0, HintMemory.count(seen, "p0", HINT));
        assertEquals(1, HintMemory.count(seen, "p" + (HintMemory.MAX_PLAYERS + 4), HINT));
        HintMemory.shown(seen, "p5", HINT, TIMES);
        HintMemory.shown(seen, "new", HINT, TIMES);
        assertEquals(2, HintMemory.count(seen, "p5", HINT));
        assertEquals(0, HintMemory.count(seen, "p6", HINT));
        Map<String, Map<String, Integer>> many = new LinkedHashMap<>();
        Map<String, Integer> hints = new LinkedHashMap<>();
        for (int i = 0; i < HintMemory.MAX_HINTS + 9; i++) {
            hints.put("h" + i, 1);
        }
        many.put("anna", hints);
        HintMemory.Seen crowded = new HintMemory.Seen();
        crowded.players = many;
        HintMemory.sanitize(crowded);
        assertEquals(HintMemory.MAX_HINTS, crowded.players.get("anna").size());
    }

    @Test
    void thePulseRunsSmoothlyBetweenCalmAndBright() {
        assertEquals(0.0F, HintMemory.pulse(0L), 1.0E-6F);
        assertEquals(1.0F, HintMemory.pulse(HintMemory.PULSE_MILLIS / 2L), 1.0E-6F);
        assertEquals(0.5F, HintMemory.pulse(HintMemory.PULSE_MILLIS / 4L), 1.0E-6F);
        assertEquals(HintMemory.pulse(100L), HintMemory.pulse(100L + 7L * HintMemory.PULSE_MILLIS), 1.0E-6F);
        float previous = HintMemory.pulse(0L);
        for (long t = 10L; t <= 3L * HintMemory.PULSE_MILLIS; t += 10L) {
            float now = HintMemory.pulse(t);
            assertTrue(now >= 0.0F && now <= 1.0F);
            assertTrue(Math.abs(now - previous) < 0.03F, "Sprung bei " + t);
            previous = now;
        }
        assertTrue(HintMemory.pulse(-1L) >= 0.0F);
    }
}
