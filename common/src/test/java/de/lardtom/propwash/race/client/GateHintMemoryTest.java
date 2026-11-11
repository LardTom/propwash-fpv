package de.lardtom.propwash.race.client;

import com.google.gson.Gson;
import de.lardtom.propwash.race.client.GateHintMemory.Choice;
import de.lardtom.propwash.race.client.GateHintMemory.Outcome;
import de.lardtom.propwash.ui.HintMemory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GateHintMemoryTest {
    private static final String ANNA = "0b6d6c1e-7f43-4a83-9c55-0d3f1c1f0a01";
    private static final String BEN = "5f0e4e2a-1d1a-4a8f-8b7e-3c6a9f2b7c02";
    private static final String HOME = "sp_new_world";
    private static final String SERVER = "mp_play.example.org";

    @Test
    void theHintComesOncePerWorldAndPlayer() {
        HintMemory.Seen hints = new HintMemory.Seen();
        GateHintMemory.Worlds worlds = new GateHintMemory.Worlds();
        assertEquals(Outcome.SHOW, GateHintMemory.check(hints, worlds, ANNA, HOME));
        assertTrue(GateHintMemory.shown(worlds, ANNA, HOME));
        assertEquals(Outcome.SEEN_HERE, GateHintMemory.check(hints, worlds, ANNA, HOME), "zweites Gate in derselben Welt");
        assertEquals(Outcome.SHOW, GateHintMemory.check(hints, worlds, ANNA, SERVER), "erstes Gate in einer anderen Welt");
        assertEquals(Outcome.SHOW, GateHintMemory.check(hints, worlds, BEN, HOME), "anderer Spieler in derselben Welt");
        assertTrue(GateHintMemory.shown(worlds, ANNA, SERVER));
        assertEquals(Outcome.SEEN_HERE, GateHintMemory.check(hints, worlds, ANNA, SERVER));
        assertEquals(Outcome.SEEN_HERE, GateHintMemory.check(hints, worlds, ANNA, HOME));
        assertFalse(GateHintMemory.shown(worlds, ANNA, SERVER), "dieselbe Welt noch einmal ändert nichts");
    }

    @Test
    void gotItWithoutTheTickKeepsTheHintForOtherWorlds() {
        HintMemory.Seen hints = new HintMemory.Seen();
        GateHintMemory.Worlds worlds = new GateHintMemory.Worlds();
        GateHintMemory.shown(worlds, ANNA, HOME);
        assertFalse(GateHintMemory.close(hints, ANNA, Choice.OK, false));
        assertFalse(GateHintMemory.never(hints, ANNA));
        assertEquals(Outcome.SHOW, GateHintMemory.check(hints, worlds, ANNA, SERVER));
    }

    @Test
    void dontShowAgainAndOpeningTheChapterApplyToAllWorlds() {
        for (Choice choice : List.of(Choice.OK, Choice.CHAPTER)) {
            HintMemory.Seen hints = new HintMemory.Seen();
            GateHintMemory.Worlds worlds = new GateHintMemory.Worlds();
            GateHintMemory.shown(worlds, ANNA, HOME);
            assertTrue(GateHintMemory.close(hints, ANNA, choice, choice == Choice.OK), choice.name());
            for (String world : List.of(HOME, SERVER, "sp_third")) {
                assertEquals(Outcome.NEVER, GateHintMemory.check(hints, worlds, ANNA, world), choice + " in " + world);
            }
            assertEquals(Outcome.SHOW, GateHintMemory.check(hints, worlds, BEN, SERVER), "gilt nur für diesen Spieler");
            assertFalse(GateHintMemory.close(hints, ANNA, choice, true), "schon gesetzt");
        }
        assertTrue(GateHintMemory.neverAgain(Choice.CHAPTER, false));
        assertTrue(GateHintMemory.neverAgain(Choice.OK, true));
        assertFalse(GateHintMemory.neverAgain(Choice.OK, false));
    }

    @Test
    void resetBringsTheHintBackEverywhere() {
        HintMemory.Seen hints = new HintMemory.Seen();
        GateHintMemory.Worlds worlds = new GateHintMemory.Worlds();
        GateHintMemory.shown(worlds, ANNA, HOME);
        GateHintMemory.close(hints, ANNA, Choice.CHAPTER, false);
        HintMemory.learned(hints, ANNA, "gate_edit_mouse", 3);
        HintMemory.learned(hints, BEN, "gate_edit_mouse", 3);
        assertTrue(GateHintMemory.forgetHints(hints, ANNA));
        assertTrue(GateHintMemory.forgetWorlds(worlds, ANNA));
        assertEquals(Outcome.SHOW, GateHintMemory.check(hints, worlds, ANNA, HOME));
        assertEquals(0, HintMemory.count(hints, ANNA, "gate_edit_mouse"), "alle Hinweise des Spielers");
        assertEquals(3, HintMemory.count(hints, BEN, "gate_edit_mouse"), "andere Spieler bleiben");
        assertFalse(GateHintMemory.forgetHints(hints, ANNA));
        assertFalse(GateHintMemory.forgetWorlds(worlds, ANNA));
    }

    @Test
    void missingPlayerOrWorldStaysSafe() {
        HintMemory.Seen hints = new HintMemory.Seen();
        GateHintMemory.Worlds worlds = new GateHintMemory.Worlds();
        assertEquals(Outcome.NO_PLAYER, GateHintMemory.check(hints, worlds, null, HOME));
        assertEquals(Outcome.NO_PLAYER, GateHintMemory.check(hints, worlds, "", HOME));
        assertEquals(Outcome.SHOW, GateHintMemory.check(hints, worlds, ANNA, null));
        assertTrue(GateHintMemory.shown(worlds, ANNA, null));
        assertEquals(Outcome.SEEN_HERE, GateHintMemory.check(hints, worlds, ANNA, " "));
        assertEquals(GateHintMemory.UNKNOWN_WORLD, GateHintMemory.worldKey(null));
        String longKey = "mp_" + "x".repeat(200);
        assertEquals(GateHintMemory.MAX_KEY_LENGTH, GateHintMemory.worldKey(longKey).length());
        assertTrue(GateHintMemory.shown(worlds, ANNA, longKey));
        assertEquals(Outcome.SEEN_HERE, GateHintMemory.check(hints, worlds, ANNA, longKey));
    }

    @Test
    void memoryStaysBoundedAndKeepsTheNewest() {
        GateHintMemory.Worlds worlds = new GateHintMemory.Worlds();
        for (int i = 0; i < GateHintMemory.MAX_WORLDS + 10; i++) {
            GateHintMemory.shown(worlds, ANNA, "sp_world_" + i);
        }
        assertEquals(GateHintMemory.MAX_WORLDS, worlds.players.get(ANNA).size());
        assertFalse(GateHintMemory.seen(worlds, ANNA, "sp_world_0"), "älteste Welt fällt heraus");
        assertTrue(GateHintMemory.seen(worlds, ANNA, "sp_world_" + (GateHintMemory.MAX_WORLDS + 9)));
        GateHintMemory.shown(worlds, ANNA, "sp_world_10");
        GateHintMemory.shown(worlds, ANNA, "sp_world_new");
        assertTrue(GateHintMemory.seen(worlds, ANNA, "sp_world_10"), "wieder besuchte Welt rückt nach hinten");
        for (int i = 0; i < GateHintMemory.MAX_PLAYERS + 3; i++) {
            GateHintMemory.shown(worlds, "player-" + i, HOME);
        }
        assertEquals(GateHintMemory.MAX_PLAYERS, worlds.players.size());
        assertTrue(worlds.players.containsKey("player-" + (GateHintMemory.MAX_PLAYERS + 2)));
    }

    @Test
    void savedMemorySurvivesJsonAndBrokenFilesAreCleaned() {
        Gson gson = new Gson();
        GateHintMemory.Worlds worlds = new GateHintMemory.Worlds();
        GateHintMemory.shown(worlds, ANNA, HOME);
        GateHintMemory.shown(worlds, ANNA, SERVER);
        GateHintMemory.Worlds loaded = gson.fromJson(gson.toJson(worlds), GateHintMemory.Worlds.class);
        GateHintMemory.sanitize(loaded);
        assertEquals(List.of(HOME, SERVER), loaded.players.get(ANNA));
        GateHintMemory.Worlds broken = gson.fromJson("{\"players\":{\"" + ANNA + "\":[\"sp_a\",null,\"\",\"sp_a\",\"sp_b\"],\"" + BEN
                + "\":null,\"\":[\"sp_c\"]}}", GateHintMemory.Worlds.class);
        GateHintMemory.sanitize(broken);
        assertEquals(List.of("sp_a", "sp_b"), broken.players.get(ANNA));
        assertFalse(broken.players.containsKey(BEN));
        assertEquals(1, broken.players.size());
        GateHintMemory.Worlds empty = gson.fromJson("{}", GateHintMemory.Worlds.class);
        empty.players = null;
        GateHintMemory.sanitize(empty);
        assertTrue(empty.players.isEmpty());
    }
}
