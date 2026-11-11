package de.lardtom.propwash.race.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.lardtom.propwash.api.race.BoardEntry;
import de.lardtom.propwash.api.race.Category;
import de.lardtom.propwash.api.race.RaceStandardInfo;
import de.lardtom.propwash.api.race.RuleValue;
import de.lardtom.propwash.race.AssistReasons;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.LapAssist;
import de.lardtom.propwash.race.RaceRuleSet;
import de.lardtom.propwash.race.RaceRules;
import de.lardtom.propwash.race.RaceStandard;
import de.lardtom.propwash.race.export.RaceDataJson;
import de.lardtom.propwash.race.export.RaceSnapshots;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.zone.Zone;
import de.lardtom.propwash.zone.ZoneBox;
import de.lardtom.propwash.zone.ZoneShape;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaceRulesDataTest {
    private static final UUID ANNA = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    private static final String WIND = WorldRuleSpecs.WIND.id();
    private static final String GRAVITY = WorldRuleSpecs.GRAVITY_SCALE.id();
    private static final RaceRuleSet RULES = RaceRuleSet.of(List.of(new RaceRuleSet.Seen(WIND, 0, RaceRuleSet.WORLD, false),
            new RaceRuleSet.Seen(GRAVITY, 40, 4, true)));

    @Test
    void everyEntryCarriesItsRuleSetInTheApiAndTheJson() {
        Leaderboard.Entry entry = new Leaderboard.Entry(ANNA, "Anna", "race_5", 30_250_000_000L, 1_792_300_000_000L, LapAssist.NONE, List.of(), RULES);
        BoardEntry api = RaceSnapshots.lapEntry(1, entry, Category.CLEAN);
        assertEquals(List.of(new RuleValue(GRAVITY, 40, 4, true), new RuleValue(WIND, 0, RuleValue.WORLD, false)), api.rules());
        assertTrue(api.rules().get(1).world());
        assertFalse(api.rules().getFirst().counts());
        JsonArray rules = RaceDataJson.entry(api, entry.creativeFc()).getAsJsonArray("rules");
        assertEquals(2, rules.size());
        JsonObject zone = rules.get(0).getAsJsonObject();
        assertEquals(GRAVITY, zone.get("rule").getAsString());
        assertEquals(40, zone.get("value").getAsInt());
        assertEquals(4, zone.get("zone").getAsInt());
        assertTrue(zone.get("raceLegalZone").getAsBoolean());
        JsonObject world = rules.get(1).getAsJsonObject();
        assertTrue(world.get("zone").isJsonNull());
        assertFalse(world.get("raceLegalZone").getAsBoolean());
        Leaderboard.RaceRow row = new Leaderboard.RaceRow(ANNA, "Anna", "race_5", 3, 93_000_000_000L, 1L, AssistReasons.NONE,
                RULES);
        assertEquals(api.rules(), RaceSnapshots.raceEntry(1, row, Category.CLEAN).rules());
        assertTrue(RaceSnapshots.lapEntry(1, new Leaderboard.Entry(ANNA, "Anna", "race_5", 1L, 1L), Category.CLEAN).rules().isEmpty());
    }

    @Test
    void theStandardListsEveryRelevantRuleAndTheNeutralOnes() {
        RaceStandardInfo info = RaceSnapshots.standard(RaceStandard.DEFAULTS.with("wind", 0), 1_792_300_000_000L, "Op");
        assertFalse(info.defaults());
        assertEquals(OptionalInt.of(0), info.value(WIND));
        assertEquals(OptionalInt.of(100), info.value(GRAVITY));
        assertTrue(info.relevant(WorldRuleSpecs.CRASH_DAMAGE.id()));
        assertFalse(info.relevant(WorldRuleSpecs.CRASH_DISARM.id()));
        assertTrue(info.neutral().contains(WorldRuleSpecs.CRASH_DISARM.id()));
        assertTrue(info.neutral().contains(WorldRuleSpecs.FREE_COURSE_PLACEMENT.id()));
        assertEquals(RaceRules.relevant().size(), info.rules().size());
        assertTrue(info.changedKnown());
        JsonObject json = RaceDataJson.standard(info);
        assertFalse(json.get("defaults").getAsBoolean());
        assertEquals(0, json.getAsJsonObject("values").get(WIND).getAsInt());
        assertEquals(1, json.getAsJsonObject("values").size());
        assertEquals(1_792_300_000_000L, json.get("changedAt").getAsLong());
        assertEquals("Op", json.get("changedBy").getAsString());
        RaceStandardInfo defaults = RaceSnapshots.standard(RaceStandard.DEFAULTS, 0L, "");
        assertTrue(defaults.defaults());
        JsonObject plain = RaceDataJson.standard(defaults);
        assertTrue(plain.get("defaults").getAsBoolean());
        assertTrue(plain.get("changedAt").isJsonNull());
        assertEquals(0, plain.getAsJsonObject("values").size());
    }

    @Test
    void zoneHintsCountOnlyRelevantRulesThatDifferFromTheStandard() {
        Zone disarm = new Zone(2, Zone.OVERWORLD, "Sanft", ZoneShape.BOX, new ZoneBox(0, 0, 0, 10, 10, 10), 0, false, 0, Map.of("crash_disarm", 0));
        Zone low = new Zone(3, Zone.OVERWORLD, "Mond", ZoneShape.BOX, new ZoneBox(0, 0, 0, 10, 10, 10), 0, false, 0, Map.of("gravity_scale", 40));
        assertFalse(RaceRules.changes(disarm, RaceStandard.DEFAULTS));
        assertTrue(RaceRules.changes(low, RaceStandard.DEFAULTS));
        assertTrue(RaceRules.differs(low, RaceStandard.DEFAULTS));
        assertFalse(RaceRules.changes(low, RaceStandard.DEFAULTS.with("gravity_scale", 40)));
        assertFalse(RaceRules.differs(low.withRaceLegal(true), RaceStandard.DEFAULTS));
    }
}
