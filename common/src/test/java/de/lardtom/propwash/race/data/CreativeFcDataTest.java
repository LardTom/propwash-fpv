package de.lardtom.propwash.race.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import de.lardtom.propwash.api.race.CourseInfo;
import de.lardtom.propwash.race.AssistCheck;
import de.lardtom.propwash.race.AssistDetail;
import de.lardtom.propwash.race.AssistReasons;
import de.lardtom.propwash.race.AssistTimeline;
import de.lardtom.propwash.race.CourseEdits;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.CreativeFcRule;
import de.lardtom.propwash.race.LapAssist;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.RaceRuleSet;
import de.lardtom.propwash.race.RaceStandard;
import de.lardtom.propwash.race.RuleTimeline;
import de.lardtom.propwash.race.export.RaceDataJson;
import de.lardtom.propwash.race.export.RaceSnapshots;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreativeFcDataTest {
    private static final long S = TestWorlds.S;
    private static final long DAY = TestWorlds.DAY;
    private static final String FRAME = "race_5";

    @TempDir
    Path temp;

    private static AssistCheck.LapVerdict creativeLap(boolean legal) {
        AssistTimeline assist = new AssistTimeline();
        for (long t = 0L; t <= S; t += S / 20) {
            assist.observe(t, AssistReasons.of(List.of(AssistDetail.creative())));
        }
        return AssistCheck.creativeFc(AssistCheck.serverLapVerdict(assist, new RuleTimeline(), S / 10, S * 9 / 10, AssistReasons.NONE,
                RaceRuleSet.DEFAULTS, AssistReasons.NONE, RaceStandard.DEFAULTS), legal);
    }

    private static Leaderboard.Result submit(CourseRegistry registry, CourseRegistry.Course course, java.util.UUID player, String name, long nanos,
                                             long date) {
        AssistCheck.LapVerdict verdict = creativeLap(registry.creativeFcLegal(course));
        return registry.submitTime(course, player, name, FRAME, nanos, date, verdict.fixed(), verdict.rules(), true, null, verdict.creativeFc());
    }

    @Test
    void courseSettingIsSavedAndUntouchedFilesStayAsTheyWere() throws IOException {
        CourseRegistry registry = TestWorlds.open(this.temp, TestWorlds.V052);
        CourseRegistry.Course course = registry.course(1);
        assertEquals(CreativeFcRule.WORLD, course.creativeFc(), "alte Welten: Kurs folgt der Welt-Regel");
        assertFalse(registry.setCreativeFc(course, CreativeFcRule.WORLD));
        assertTrue(registry.setCreativeFc(course, CreativeFcRule.LEGAL));
        assertEquals(CreativeFcRule.LEGAL, course.toData().creativeFc(), "Abgleich zum Client trägt die Einstellung");
        registry.save();
        String saved = Files.readString(registry.path(), StandardCharsets.UTF_8);
        assertTrue(saved.contains("\"creativeFc\": \"legal\""), saved);
        CourseRegistry again = TestWorlds.reopen(registry.path());
        assertEquals(CreativeFcRule.LEGAL, again.course(1).creativeFc());
        assertEquals(CreativeFcRule.WORLD, again.course(2).creativeFc());
        assertTrue(again.setCreativeFc(again.course(1), CreativeFcRule.WORLD));
        again.save();
        assertFalse(Files.readString(again.path(), StandardCharsets.UTF_8).contains("creativeFc"), "Standard steht nicht in der Datei");
    }

    @Test
    void onlyAdminsChangeTheSwitchOnTheServer() throws IOException {
        CourseRegistry registry = TestWorlds.open(this.temp, TestWorlds.V052);
        CourseRegistry.Course course = registry.course(1);
        assertTrue(course.ownedBy(TestWorlds.ANNA));
        assertEquals(CourseEdits.Result.DENIED, CourseEdits.creativeFc(registry, course, CreativeFcRule.LEGAL, false),
                "Besitzerin ohne /gamerule darf den Schalter nicht stellen");
        assertEquals(CreativeFcRule.WORLD, course.creativeFc());
        assertEquals(CourseEdits.Result.DONE, CourseEdits.creativeFc(registry, course, CreativeFcRule.ASSIST, true));
        assertEquals(CreativeFcRule.ASSIST, course.creativeFc());
        assertEquals(CourseEdits.Result.UNCHANGED, CourseEdits.creativeFc(registry, course, CreativeFcRule.ASSIST, true));
        assertEquals(CourseEdits.Result.NO_COURSE, CourseEdits.creativeFc(registry, null, CreativeFcRule.LEGAL, true));
    }

    @Test
    void legalLapsCarryTheStateAndOldLapsStayAsTheyWere() throws IOException {
        CourseRegistry registry = TestWorlds.open(this.temp, TestWorlds.V052);
        CourseRegistry.Course course = registry.course(1);
        assertFalse(registry.creativeFcLegal(course), "Welt-Regel aus: Kreativ-FC zählt als Flughilfe");
        submit(registry, course, TestWorlds.BEN, "Ben", 28 * S, DAY);
        Leaderboard board = registry.board(course);
        assertNull(board.best(TestWorlds.BEN, null));
        Leaderboard.Entry old = board.bestAssisted(TestWorlds.BEN, null);
        assertNotNull(old);
        assertTrue(LapAssist.CREATIVE_DRONE.in(old.assist()));
        assertFalse(old.creativeFc());
        registry.setCreativeFc(course, CreativeFcRule.LEGAL);
        assertTrue(registry.creativeFcLegal(course));
        assertNotNull(board.bestAssisted(TestWorlds.BEN, null), "bestehende Runden bleiben unverändert");
        assertNull(board.best(TestWorlds.BEN, null));
        Leaderboard.Result result = submit(registry, course, TestWorlds.ANNA, "Anna", 30 * S, DAY + 10);
        assertTrue(result.clean());
        Leaderboard.Entry legal = board.best(TestWorlds.ANNA, null);
        assertNotNull(legal);
        assertTrue(legal.creativeFc(), "Runde trägt: mit Kreativ-FC, renntauglich gewertet");
        assertEquals(LapAssist.NONE, legal.assist());
        registry.setCreativeFc(course, CreativeFcRule.ASSIST);
        assertNotNull(board.best(TestWorlds.ANNA, null), "Wechsel auf „zählt als Flughilfe“ ordnet die Runde nicht neu ein");
        JsonElement json = Leaderboard.Entry.CODEC.encodeStart(JsonOps.INSTANCE, legal).getOrThrow();
        assertTrue(json.getAsJsonObject().get("creative_fc").getAsBoolean());
        assertEquals(legal, Leaderboard.Entry.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        JsonObject plain = Leaderboard.Entry.CODEC.encodeStart(JsonOps.INSTANCE, old).getOrThrow().getAsJsonObject();
        assertFalse(plain.has("creative_fc"), "ohne Vermerk bleibt der gespeicherte Eintrag wie bisher");
        assertFalse(Leaderboard.Entry.CODEC.parse(JsonOps.INSTANCE, plain).getOrThrow().creativeFc());
        AssistCheck.LapVerdict race = creativeLap(true);
        Leaderboard.Result raced = registry.submitRaceTime(course, TestWorlds.ANNA, "Anna", 3, 95 * S, FRAME, DAY + 20, race.fixed(), race.rules(),
                true, null, race.creativeFc());
        assertTrue(raced.clean());
        assertTrue(board.storedRaceTimes().getFirst().creativeFc());
        assertTrue(board.raceRows(false, 3).getFirst().creativeFc());
        JsonElement raceJson = Leaderboard.RaceTime.CODEC.encodeStart(JsonOps.INSTANCE, board.storedRaceTimes().getFirst()).getOrThrow();
        assertTrue(raceJson.getAsJsonObject().get("creative_fc").getAsBoolean());
    }

    @Test
    void raceDataExportNamesTheSettingAndTheLaps() throws IOException {
        CourseRegistry registry = TestWorlds.open(this.temp, TestWorlds.V052);
        CourseRegistry.Course course = registry.course(1);
        registry.setCreativeFc(course, CreativeFcRule.LEGAL);
        submit(registry, course, TestWorlds.ANNA, "Anna", 30 * S, DAY);
        registry.submitLap(course, TestWorlds.BEN, "Ben", FRAME, 31 * S, DAY + 5);
        CourseInfo info = RaceSnapshots.info(course, registry.records(), List.of());
        JsonObject board = JsonParser.parseString(RaceDataJson.leaderboard(info, course, registry.records())).getAsJsonObject();
        JsonObject state = board.getAsJsonObject("creativeFc");
        assertEquals("legal", state.get("course").getAsString());
        assertFalse(state.get("worldRule").getAsBoolean());
        assertTrue(state.get("raceLegal").getAsBoolean());
        JsonObject clean = board.getAsJsonArray("layouts").get(0).getAsJsonObject().getAsJsonObject("laps").getAsJsonArray("clean").get(0)
                .getAsJsonObject();
        assertEquals("Anna", clean.get("name").getAsString());
        assertTrue(clean.get("creativeFc").getAsBoolean());
        JsonObject second = board.getAsJsonArray("layouts").get(0).getAsJsonObject().getAsJsonObject("laps").getAsJsonArray("clean").get(1)
                .getAsJsonObject();
        assertFalse(second.get("creativeFc").getAsBoolean());
        JsonObject courses = JsonParser.parseString(RaceDataJson.courses(registry, List.of(info))).getAsJsonObject();
        JsonObject first = courses.getAsJsonArray("courses").get(0).getAsJsonObject();
        assertEquals("legal", first.getAsJsonObject("creativeFc").get("course").getAsString());
        assertTrue(first.getAsJsonObject("creativeFc").get("raceLegal").getAsBoolean());
        assertTrue(first.getAsJsonObject("record").get("creativeFc").getAsBoolean());
    }
}
