package de.lardtom.propwash.guide.training;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainingProgressTest {
    private static LessonRun.Result result(int stars, double time, boolean assisted, double... splits) {
        return new LessonRun.Result(stars, time, time, assisted, splits);
    }

    @Test
    void bestTimesAndStarsOnlyImprove() {
        TrainingProgress progress = new TrainingProgress();
        Lesson straight = Curriculum.lesson("straight");
        progress.attempt(straight.id());
        TrainingProgress.Outcome first = progress.complete(straight, TrainingScale.FULL, result(2, 11.0, false, 2.0, 4.0));
        assertTrue(first.moreStars());
        assertFalse(first.bestTime(), "erste Zeit ist keine verbesserte Bestzeit");
        assertEquals(2, progress.stars(straight.id()));
        TrainingProgress.Outcome slower = progress.complete(straight, TrainingScale.FULL, result(1, 20.0, false));
        assertFalse(slower.moreStars());
        assertFalse(slower.bestTime());
        assertEquals(2, progress.stars(straight.id()));
        assertEquals(11.0, progress.best(straight.id(), TrainingScale.FULL).time);
        TrainingProgress.Outcome faster = progress.complete(straight, TrainingScale.FULL, result(3, 7.5, false, 1.5, 3.0));
        assertTrue(faster.moreStars());
        assertTrue(faster.bestTime());
        assertEquals(11.0, faster.previousTime());
        assertArrayEquals(new double[]{1.5, 3.0}, progress.referenceSplits(straight.id(), TrainingScale.FULL));
        TrainingProgress.LessonRecord record = progress.get(straight.id());
        assertNotNull(record);
        assertEquals(1, record.attempts);
        assertEquals(3, record.completions);
    }

    @Test
    void assistedRunsNeverSetBestTimesAndScalesKeepTheirOwnBest() {
        TrainingProgress progress = new TrainingProgress();
        Lesson hover = Curriculum.lesson("hover");
        progress.complete(hover, TrainingScale.FULL, result(1, 5.0, true));
        assertEquals(1, progress.stars(hover.id()));
        assertEquals(null, progress.best(hover.id(), TrainingScale.FULL));
        progress.complete(hover, TrainingScale.SMALL, result(3, 9.0, false));
        assertEquals(9.0, progress.best(hover.id(), TrainingScale.SMALL).time);
        assertEquals(null, progress.best(hover.id(), TrainingScale.FULL));
    }

    @Test
    void chapterCompletionIsReportedOnce() {
        TrainingProgress progress = new TrainingProgress();
        Chapter basics = Curriculum.chapter(Curriculum.BASICS);
        TrainingProgress.Outcome last = null;
        for (Lesson lesson : basics.lessons()) {
            last = progress.complete(lesson, TrainingScale.FULL, result(1, 30.0, false));
        }
        assertNotNull(last);
        assertTrue(last.chapterDone());
        assertFalse(progress.complete(basics.lessons().getFirst(), TrainingScale.FULL, result(3, 2.0, false)).chapterDone());
    }

    @Test
    void brokenFilesAreRepaired() {
        TrainingProgress progress = new Gson().fromJson("""
                {"lessons": {"arm": {"stars": 9, "attempts": -4, "bests": {"full": {"time": -1}, "small": {"time": 4.0}}},
                 "hover": null}}
                """, TrainingProgress.class);
        progress.sanitize();
        assertEquals(3, progress.stars("arm"));
        assertEquals(0, progress.get("arm").attempts);
        assertEquals(1, progress.get("arm").bests.size());
        assertEquals(4.0, progress.get("arm").bests.get("small").metric);
        assertEquals(0, progress.stars("hover"));
        TrainingProgress empty = new Gson().fromJson("{\"lessons\": null}", TrainingProgress.class);
        empty.sanitize();
        assertEquals(0, empty.stars("arm"));
    }
}
