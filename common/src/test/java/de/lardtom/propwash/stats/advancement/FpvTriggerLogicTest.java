package de.lardtom.propwash.stats.advancement;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.lardtom.propwash.guide.training.Curriculum;
import de.lardtom.propwash.guide.training.Lesson;
import de.lardtom.propwash.guide.training.StarRule;
import de.lardtom.propwash.trick.TrickType;
import net.minecraft.advancements.predicates.MinMaxBounds;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FpvTriggerLogicTest {
    @Test
    void takeoffNeedsOneBlockAboveTheArmPointAndCountsOncePerArming() {
        FlightFeats feats = new FlightFeats();
        assertFalse(feats.update(false, 64.0).takeoff());
        assertFalse(feats.update(true, 64.0).takeoff());
        assertFalse(feats.update(true, 64.9).takeoff());
        assertTrue(feats.update(true, 65.0).takeoff());
        assertFalse(feats.update(true, 70.0).takeoff());
        assertTrue(feats.update(true, 70.0).airborne());
        assertFalse(feats.update(false, 70.0).airborne());
        assertFalse(feats.update(true, 70.5).takeoff(), "neue Scharfschaltung zählt ab der neuen Höhe");
        assertTrue(feats.update(true, 71.5).takeoff());
    }

    @Test
    void diveIsTheDropSinceTheLastPeakAndResetsWhenClimbingAgain() {
        FlightFeats feats = new FlightFeats();
        feats.update(true, 200.0);
        double dive = 0.0;
        for (double y = 200.0; y >= 95.0; y -= 5.0) {
            dive = feats.update(true, y).dive();
        }
        assertEquals(105.0, dive, 1.0E-9);
        assertEquals(105.0, feats.update(true, 97.0).dive(), 1.0E-9, "kleines Abfangen bricht den Sturz nicht ab");
        assertEquals(0.0, feats.update(true, 99.0).dive(), 1.0E-9, "mehr als drei Blöcke Steigflug beginnt neu");
        assertTrue(Double.isNaN(feats.update(false, 50.0).dive()));
        assertTrue(Double.isNaN(feats.update(true, Double.NaN).dive()));
    }

    @Test
    void flightConditionsIgnoreMissingValues() {
        Optional<MinMaxBounds.Doubles> none = Optional.empty();
        Optional<MinMaxBounds.Doubles> kilometer = Optional.of(MinMaxBounds.Doubles.atLeast(1000.0));
        Optional<MinMaxBounds.Doubles> high = Optional.of(MinMaxBounds.Doubles.atLeast(320.0));
        Optional<MinMaxBounds.Ints> veteran = Optional.of(MinMaxBounds.Ints.atLeast(100));
        assertFalse(FlightTrigger.matches(kilometer, none, none, Optional.empty(), new FlightTrigger.Report(999.9, Double.NaN, Double.NaN, 0)));
        assertTrue(FlightTrigger.matches(kilometer, none, none, Optional.empty(), new FlightTrigger.Report(1000.0, Double.NaN, Double.NaN, 0)));
        assertFalse(FlightTrigger.matches(none, high, none, Optional.empty(), new FlightTrigger.Report(5000.0, Double.NaN, Double.NaN, 0)),
                "ohne Höhe (unscharf) kein Treffer");
        assertTrue(FlightTrigger.matches(none, high, none, Optional.empty(), new FlightTrigger.Report(0.0, 320.5, 0.0, 0)));
        assertFalse(FlightTrigger.matches(none, none, none, veteran, new FlightTrigger.Report(0.0, Double.NaN, Double.NaN, 99)));
        assertTrue(FlightTrigger.matches(none, none, none, veteran, new FlightTrigger.Report(0.0, Double.NaN, Double.NaN, 100)));
    }

    @Test
    void trickConditionsMatchNamesAndCombos() {
        Optional<List<String>> any = Optional.empty();
        Optional<List<String>> loop = Optional.of(List.of(TrickType.POWER_LOOP.key()));
        Optional<MinMaxBounds.Ints> free = Optional.empty();
        Optional<MinMaxBounds.Ints> five = Optional.of(MinMaxBounds.Ints.atLeast(5));
        assertTrue(TrickTrigger.matches(any, free, TrickType.FLIP.key(), 0));
        assertFalse(TrickTrigger.matches(loop, free, TrickType.FLIP.key(), 0));
        assertFalse(TrickTrigger.matches(loop, free, TrickType.POWER_LOOP_3D.key(), 0), "nur der Power Loop selbst");
        assertTrue(TrickTrigger.matches(loop, free, TrickType.POWER_LOOP.key(), 0));
        assertFalse(TrickTrigger.matches(loop, free, "", 7));
        assertFalse(TrickTrigger.matches(any, five, "", 4));
        assertTrue(TrickTrigger.matches(any, five, "", 5));
        assertTrue(FeatCodecs.trick(TrickType.POWER_LOOP.key()).isPresent());
        assertFalse(FeatCodecs.trick("power_loop_x").isPresent());
        assertFalse(FeatCodecs.trick("").isPresent());
        assertFalse(FeatCodecs.trick("x".repeat(FeatCodecs.MAX_TRICK_KEY + 1)).isPresent());
    }

    @Test
    void flightSchoolCountsCompleteChaptersAndPerfectStars() {
        Map<String, Integer> stars = new HashMap<>();
        Curriculum.StarSource source = id -> stars.getOrDefault(id, 0);
        assertEquals(new TrainingTally(0, false), TrainingTally.of(source));
        for (Lesson lesson : Curriculum.chapters().getFirst().lessons()) {
            stars.put(lesson.id(), 1);
        }
        assertEquals(new TrainingTally(1, false), TrainingTally.of(source));
        for (Lesson lesson : Curriculum.lessons()) {
            stars.put(lesson.id(), StarRule.MAX_STARS);
        }
        assertEquals(new TrainingTally(Curriculum.chapters().size(), true), TrainingTally.of(source));
        assertEquals(Curriculum.chapters().size(), new TrainingTally(999, false).chapters(), "Server kürzt auf die Zahl der Kapitel");
        assertEquals(0, new TrainingTally(-3, false).chapters());
        Optional<MinMaxBounds.Ints> first = Optional.of(MinMaxBounds.Ints.atLeast(1));
        assertFalse(FlightSchoolTrigger.matches(first, Optional.empty(), TrainingTally.NONE));
        assertTrue(FlightSchoolTrigger.matches(first, Optional.empty(), new TrainingTally(1, false)));
        assertFalse(FlightSchoolTrigger.matches(Optional.empty(), Optional.of(true), new TrainingTally(5, false)));
        assertTrue(FlightSchoolTrigger.matches(Optional.empty(), Optional.of(true), new TrainingTally(5, true)));
    }

    @Test
    void filesNameOnlyKnownMilestonesAndTricks() throws IOException {
        Set<String> milestones = Arrays.stream(Milestone.values()).map(Milestone::getSerializedName).collect(Collectors.toCollection(TreeSet::new));
        Set<String> used = new TreeSet<>();
        for (Map.Entry<String, JsonObject> file : FpvAdvancementFilesTest.files().entrySet()) {
            for (Map.Entry<String, JsonElement> criterion : file.getValue().getAsJsonObject("criteria").entrySet()) {
                JsonObject json = criterion.getValue().getAsJsonObject();
                String trigger = json.get("trigger").getAsString();
                JsonObject conditions = json.has("conditions") ? json.getAsJsonObject("conditions") : new JsonObject();
                if (trigger.equals("propwash:milestone")) {
                    String name = conditions.get("milestone").getAsString();
                    assertTrue(milestones.contains(name), file.getKey() + ": Meilenstein " + name);
                    used.add(name);
                }
                if (trigger.equals("propwash:trick") && conditions.has("tricks")) {
                    for (JsonElement trick : conditions.getAsJsonArray("tricks")) {
                        assertTrue(FeatCodecs.trick(trick.getAsString()).isPresent(), file.getKey() + ": Trick " + trick);
                    }
                }
            }
        }
        assertEquals(milestones, used, "jeder Meilenstein hat einen Fortschritt");
        Set<String> registered = new TreeSet<>();
        for (String name : List.of("milestone", "flight", "trick", "flight_school", "cargo_delivered", "drone_strike")) {
            registered.add("propwash:" + name);
        }
        Set<String> expected = new TreeSet<>(FpvAdvancementFilesTest.TRIGGERS);
        expected.removeIf(id -> id.startsWith("minecraft:"));
        assertEquals(expected, registered);
    }
}
