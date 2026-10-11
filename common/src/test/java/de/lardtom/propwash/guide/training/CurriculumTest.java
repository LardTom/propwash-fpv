package de.lardtom.propwash.guide.training;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurriculumTest {
    private static final int TITLE_MAX = 26;
    private static final int DESCRIPTION_MAX = 72;
    private static final int TIP_MAX = 130;
    private static final int CHAPTER_TITLE_MAX = 18;
    private static final int CHAPTER_DESCRIPTION_MAX = 48;

    private static Path guideLang() {
        for (Path candidate : List.of(Path.of("common/src/lang/guide"), Path.of("src/lang/guide"))) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Sprachdateien der Flugschule nicht gefunden");
    }

    private static JsonObject lang(String language) throws IOException {
        try (Reader reader = Files.newBufferedReader(guideLang().resolve(language + ".json"), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    @Test
    void fiveChaptersWithTwentyLessonsInOrder() {
        List<Chapter> chapters = Curriculum.chapters();
        assertEquals(List.of(Curriculum.BASICS, Curriculum.FORWARD, Curriculum.RACING, Curriculum.FREESTYLE, Curriculum.ADVANCED),
                chapters.stream().map(Chapter::id).toList());
        assertEquals(20, Curriculum.lessons().size());
        assertEquals(60, Curriculum.maxStars());
        Set<String> ids = new HashSet<>();
        for (Chapter chapter : chapters) {
            assertEquals(chapters.indexOf(chapter) + 1, chapter.number());
            for (int i = 0; i < chapter.lessons().size(); i++) {
                Lesson lesson = chapter.lessons().get(i);
                assertTrue(ids.add(lesson.id()), "doppelt: " + lesson.id());
                assertEquals(chapter.id(), lesson.chapter());
                assertEquals(i + 1, lesson.number(), lesson.id());
                assertEquals(chapter.number() + "." + (i + 1), lesson.code());
                assertTrue(lesson.id().matches("[a-z_]+"), lesson.id());
            }
        }
        assertEquals("final_exam", Curriculum.lessons().getLast().id());
        assertNull(Curriculum.next(Curriculum.lessons().getLast()));
        assertEquals("hover", Curriculum.next(Curriculum.lesson("arm")).id());
    }

    @Test
    void goalsPointAtExistingElements() {
        for (Lesson lesson : Curriculum.lessons()) {
            assertFalse(lesson.goals().isEmpty(), lesson.id());
            assertEquals(0.0, lesson.pads().getFirst().x());
            assertEquals(0.0, lesson.pads().getFirst().z());
            int ringsUsed = 0;
            for (Goal goal : lesson.goals()) {
                switch (goal) {
                    case Goal.Arm arm -> assertTrue(arm.seconds() > 0.0);
                    case Goal.Hover hover -> {
                        assertTrue(hover.zone() < lesson.zones().size(), lesson.id());
                        assertEquals(Zone.Kind.HOVER, lesson.zones().get(hover.zone()).kind());
                    }
                    case Goal.Heading heading -> {
                        assertTrue(heading.zone() < lesson.zones().size());
                        for (int marker : heading.markers()) {
                            assertTrue(marker < lesson.markers().size());
                        }
                    }
                    case Goal.Rings rings -> {
                        assertEquals(ringsUsed, rings.from(), lesson.id() + ": Ringe nicht lückenlos");
                        assertTrue(rings.to() > rings.from() && rings.to() <= lesson.rings().size(), lesson.id());
                        ringsUsed = rings.to();
                    }
                    case Goal.Laps laps -> {
                        assertEquals(ringsUsed, laps.from());
                        assertTrue(laps.count() >= 3 && laps.laps() >= 1);
                        ringsUsed = laps.to();
                    }
                    case Goal.Land land -> assertTrue(land.pad() > 0 && land.pad() < lesson.pads().size(), lesson.id());
                    case Goal.Tricks tricks -> assertTrue(tricks.count() >= 1);
                    case Goal.Combo combo -> assertTrue(combo.tricks() >= 2);
                }
            }
            assertEquals(lesson.rings().size(), ringsUsed, lesson.id() + ": Ring ohne Ziel");
        }
    }

    @Test
    void starsAndTimeLimitsAreOrdered() {
        for (Lesson lesson : Curriculum.lessons()) {
            StarRule rule = lesson.stars();
            assertTrue(rule.two() >= rule.three(), lesson.id());
            if (rule.metric() == StarRule.Metric.TIME) {
                assertTrue(lesson.timeLimit() > rule.two() * 2.0, lesson.id() + ": Zeitlimit zu knapp");
            } else {
                Goal last = lesson.goals().getLast();
                assertTrue(last instanceof Goal.Land, lesson.id());
                Pad pad = lesson.pads().get(((Goal.Land) last).pad());
                assertTrue(rule.two() < pad.radius(), lesson.id());
            }
        }
        StarRule rule = StarRule.time(10.0, 20.0);
        assertEquals(3, rule.stars(9.0, TrainingScale.FULL, false));
        assertEquals(2, rule.stars(15.0, TrainingScale.FULL, false));
        assertEquals(1, rule.stars(25.0, TrainingScale.FULL, false));
        assertEquals(1, rule.stars(9.0, TrainingScale.FULL, true));
        assertEquals(3, rule.stars(12.0, TrainingScale.SMALL, false));
        assertEquals(2, rule.stars(13.0, TrainingScale.SMALL, false));
        assertEquals(3, StarRule.padDistance(0.4, 0.8).stars(0.2, TrainingScale.SMALL, false));
        assertEquals(2, StarRule.padDistance(0.4, 0.8).stars(0.3, TrainingScale.SMALL, false));
    }

    @Test
    void ringsDoNotOverlapAndStayAboveTheGround() {
        for (Lesson lesson : Curriculum.lessons()) {
            List<Ring> rings = lesson.rings();
            for (Ring ring : rings) {
                assertTrue(ring.y() - (ring.flat() ? 0.0 : ring.height() * 0.5) >= 0.19, lesson.id() + ": Ring im Boden");
                assertTrue(ring.opening() >= 1.0, lesson.id());
            }
            for (int i = 0; i < rings.size(); i++) {
                for (int j = i + 1; j < rings.size(); j++) {
                    Ring a = rings.get(i);
                    Ring b = rings.get(j);
                    double distance = Math.sqrt(Math.pow(a.x() - b.x(), 2) + Math.pow(a.y() - b.y(), 2) + Math.pow(a.z() - b.z(), 2));
                    boolean repeat = distance < 1.0E-6 && Math.abs(TrainingFrame.delta(a.heading(), b.heading())) < 1.0E-6
                            && lesson.id().equals("figure_eight") && j == rings.size() - 1 && i == 0;
                    assertTrue(repeat || distance > a.reach() + b.reach(), lesson.id() + ": Ringe " + i + " und " + j + " überlappen");
                }
            }
        }
    }

    @Test
    void figureEightCrossesItself() {
        List<Ring> rings = Curriculum.lesson("figure_eight").rings();
        assertEquals(7, rings.size());
        assertEquals(0.0, rings.get(0).heading(), 1.0E-6);
        assertEquals(180.0, Math.abs(rings.get(2).heading()), 1.0E-6);
        assertTrue(rings.get(1).z() > rings.get(4).z());
        assertTrue(rings.get(0).x() > 0.0 && rings.get(2).x() < 0.0 && rings.get(3).x() > 0.0 && rings.get(5).x() < 0.0);
    }

    @Test
    void unlockOrderFollowsTheChapters() {
        TrainingProgress progress = new TrainingProgress();
        assertTrue(Curriculum.unlocked(Curriculum.lesson("arm"), progress));
        assertFalse(Curriculum.unlocked(Curriculum.lesson("hover"), progress));
        assertFalse(Curriculum.unlocked(Curriculum.lesson("straight"), progress));
        assertEquals("arm", Curriculum.firstOpen(progress).id());
        for (Lesson lesson : Curriculum.chapter(Curriculum.BASICS).lessons()) {
            assertTrue(Curriculum.unlocked(lesson, progress), lesson.id());
            progress.record(lesson.id()).stars = 1;
        }
        assertTrue(Curriculum.unlocked(Curriculum.lesson("straight"), progress));
        assertFalse(Curriculum.unlocked(Curriculum.lesson("gate_line"), progress));
        for (Lesson lesson : Curriculum.chapter(Curriculum.FORWARD).lessons()) {
            progress.record(lesson.id()).stars = 2;
        }
        assertTrue(Curriculum.unlocked(Curriculum.lesson("gate_line"), progress));
        assertTrue(Curriculum.unlocked(Curriculum.lesson("flip"), progress));
        assertFalse(Curriculum.unlocked(Curriculum.lesson("dive_gate"), progress));
        for (Lesson lesson : Curriculum.chapter(Curriculum.RACING).lessons()) {
            progress.record(lesson.id()).stars = 3;
        }
        assertFalse(Curriculum.unlocked(Curriculum.lesson("dive_gate"), progress), "Freestyle fehlt noch");
        for (Lesson lesson : Curriculum.chapter(Curriculum.FREESTYLE).lessons()) {
            progress.record(lesson.id()).stars = 1;
        }
        assertTrue(Curriculum.unlocked(Curriculum.lesson("dive_gate"), progress));
        assertEquals(4 + 8 + 9 + 5, Curriculum.totalStars(progress));
        assertEquals("hover", Curriculum.nextOpen(Curriculum.lesson("arm"), progress).id());
    }

    @Test
    void everyTextExistsInBothLanguagesAndFits() throws IOException {
        Map<String, JsonObject> languages = Map.of("de_de", lang("de_de"), "en_us", lang("en_us"));
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, JsonObject> entry : languages.entrySet()) {
            JsonObject texts = entry.getValue();
            for (Chapter chapter : Curriculum.chapters()) {
                check(problems, entry.getKey(), texts, chapter.titleKey(), CHAPTER_TITLE_MAX);
                check(problems, entry.getKey(), texts, chapter.descriptionKey(), CHAPTER_DESCRIPTION_MAX);
                for (Lesson lesson : chapter.lessons()) {
                    check(problems, entry.getKey(), texts, lesson.titleKey(), TITLE_MAX);
                    check(problems, entry.getKey(), texts, lesson.descriptionKey(), DESCRIPTION_MAX);
                    check(problems, entry.getKey(), texts, lesson.tipKey(), TIP_MAX);
                }
            }
            for (LessonRun.Failure failure : LessonRun.Failure.values()) {
                check(problems, entry.getKey(), texts, "propwash.guide.training.failure." + failure.key(), 40);
            }
            for (Goal goal : List.of(new Goal.Arm(1.0), new Goal.Hover(0, 1.0), new Goal.Heading(0, List.of(), 1.0, 1.0),
                    new Goal.Rings(0, 1, false), new Goal.Laps(0, 1, 1), new Goal.Land(1), new Goal.Tricks(Set.of(), 1, 0), new Goal.Combo(2, 0))) {
                check(problems, entry.getKey(), texts, "propwash.guide.training.goal." + goal.key(), 60);
            }
            for (String key : List.of("tricks.outside", "combo.outside")) {
                check(problems, entry.getKey(), texts, "propwash.guide.training.goal." + key, 60);
            }
            check(problems, entry.getKey(), texts, "propwash.guide.training.event.trick.outside", 60);
        }
        JsonObject de = languages.get("de_de");
        JsonObject en = languages.get("en_us");
        for (String key : de.keySet()) {
            if (key.startsWith("propwash.guide.training.") && !en.has(key)) {
                problems.add("en_us fehlt " + key);
            }
        }
        for (String key : en.keySet()) {
            if (key.startsWith("propwash.guide.training.") && !de.has(key)) {
                problems.add("de_de fehlt " + key);
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    private static void check(List<String> problems, String language, JsonObject texts, String key, int max) {
        if (!texts.has(key)) {
            problems.add(language + " fehlt " + key);
            return;
        }
        int length = texts.get(key).getAsString().length();
        if (length > max) {
            problems.add(language + " " + key + " hat " + length + " Zeichen (max. " + max + ")");
        }
    }

    @Test
    void scaleFollowsTheWheelbase() {
        assertEquals(TrainingScale.SMALL, TrainingScale.of(65));
        assertEquals(TrainingScale.MEDIUM, TrainingScale.of(142));
        assertEquals(TrainingScale.FULL, TrainingScale.of(225));
        assertEquals(TrainingScale.FULL, TrainingScale.of(295));
        assertEquals(TrainingScale.FULL, TrainingScale.of(0));
        assertNotNull(Curriculum.lesson("hover"));
    }
}
