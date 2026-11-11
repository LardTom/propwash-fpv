package de.lardtom.propwash.race;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GhostChoiceTest {
    static final UUID BEN = UUID.fromString("00000000-0000-4000-8000-0000000000b2");

    @Test
    void oldSettingsMergeIntoOneMode() {
        assertEquals(GhostChoice.Mode.OWN, GhostChoice.migrate(true, true));
        assertEquals(GhostChoice.Mode.OWN, GhostChoice.migrate(true, false));
        assertEquals(GhostChoice.Mode.BOARD, GhostChoice.migrate(false, true));
        assertEquals(GhostChoice.Mode.OFF, GhostChoice.migrate(false, false));
        assertEquals(GhostChoice.Mode.OWN, GhostChoice.migrate(null, null));
    }

    @Test
    void configMigrationRewritesTheJson() {
        JsonObject old = JsonParser.parseString("{\"laps\":3,\"ghost\":false,\"recordGhost\":true}").getAsJsonObject();
        GhostChoice.migrateConfig(old);
        assertEquals("BOARD", old.get("ghostMode").getAsString());
        assertFalse(old.has("ghost"));
        assertFalse(old.has("recordGhost"));
        JsonObject off = JsonParser.parseString("{\"ghost\":false,\"recordGhost\":false}").getAsJsonObject();
        GhostChoice.migrateConfig(off);
        assertEquals("OFF", off.get("ghostMode").getAsString());
        JsonObject current = JsonParser.parseString("{\"ghostMode\":\"OFF\",\"ghost\":true}").getAsJsonObject();
        GhostChoice.migrateConfig(current);
        assertEquals("OFF", current.get("ghostMode").getAsString());
        assertFalse(current.has("ghost"));
        JsonObject fresh = JsonParser.parseString("{\"laps\":2}").getAsJsonObject();
        GhostChoice.migrateConfig(fresh);
        assertFalse(fresh.has("ghostMode"));
    }

    @Test
    void choicePerCourseRoundTrips() {
        GhostKey key = new GhostKey(BEN, "race_5", 12_345_000_000L, 1_700_000_000_000L);
        Map<Integer, GhostChoice> choices = new TreeMap<>();
        choices.put(1, GhostChoice.entry(key, "Ben"));
        choices.put(2, GhostChoice.NONE);
        choices.put(3, GhostChoice.RECORD);
        JsonObject json = GhostChoice.toJson(choices);
        Map<Integer, GhostChoice> back = GhostChoice.fromJson(JsonParser.parseString(json.toString()).getAsJsonObject());
        assertEquals(choices, back);
        assertTrue(back.get(1).chosen());
        assertEquals(key, back.get(1).key());
        assertTrue(back.get(3).record());
        assertFalse(back.get(2).chosen());
    }

    @Test
    void brokenEntriesAreSkipped() {
        JsonObject json = JsonParser.parseString("{\"courses\":{\"x\":{\"mode\":\"own\"},\"4\":{\"mode\":\"board\",\"player\":\"kaputt\",\"nanos\":5},"
                + "\"5\":{\"mode\":\"board\",\"player\":\"" + BEN + "\",\"nanos\":0},\"6\":7}}").getAsJsonObject();
        Map<Integer, GhostChoice> back = GhostChoice.fromJson(json);
        assertFalse(back.containsKey(4));
        assertTrue(back.get(5).record());
        assertFalse(back.containsKey(6));
        assertNull(GhostChoice.fromJson((JsonObject) null).get(1));
    }

    @Test
    void onlyBoardChoicesKeepAKey() {
        GhostKey key = new GhostKey(BEN, "race_5", 1L, 1L);
        GhostChoice own = new GhostChoice(GhostChoice.Mode.OWN, key, "Ben");
        assertNull(own.key());
        assertEquals("", own.holder());
        assertEquals(GhostChoice.Mode.OWN, GhostChoice.Mode.byKey("weird", GhostChoice.Mode.OWN));
        assertEquals(GhostChoice.Mode.OFF, GhostChoice.Mode.byKey(" off ", GhostChoice.Mode.OWN));
    }
}
