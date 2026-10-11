package de.lardtom.propwash.guide.training;

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TrainingProgress implements Curriculum.StarSource {
    public Map<String, LessonRecord> lessons = new LinkedHashMap<>();

    public static final class LessonRecord {
        public int stars;
        public int attempts;
        public int completions;
        public Map<String, Best> bests = new LinkedHashMap<>();

        public @Nullable Best best(String scale) {
            return this.bests == null ? null : this.bests.get(scale);
        }
    }

    public static final class Best {
        public double time = -1.0;
        public double metric = -1.0;
        public double[] splits = new double[0];

        public Best() {
        }

        public Best(double time, double metric, double[] splits) {
            this.time = time;
            this.metric = metric;
            this.splits = splits == null ? new double[0] : splits.clone();
        }
    }

    public record Outcome(int previousStars, int stars, boolean moreStars, boolean bestTime, double previousTime,
                          boolean chapterDone) {
    }

    public TrainingProgress() {
    }

    @Override
    public int stars(String lessonId) {
        LessonRecord record = this.lessons == null ? null : this.lessons.get(lessonId);
        return record == null ? 0 : Math.clamp(record.stars, 0, StarRule.MAX_STARS);
    }

    public @Nullable LessonRecord get(String lessonId) {
        return this.lessons == null ? null : this.lessons.get(lessonId);
    }

    public LessonRecord record(String lessonId) {
        if (this.lessons == null) {
            this.lessons = new LinkedHashMap<>();
        }
        return this.lessons.computeIfAbsent(lessonId, id -> new LessonRecord());
    }

    public @Nullable Best best(String lessonId, TrainingScale scale) {
        LessonRecord record = this.get(lessonId);
        return record == null ? null : record.best(scale.key());
    }

    public double[] referenceSplits(String lessonId, TrainingScale scale) {
        Best best = this.best(lessonId, scale);
        return best == null || best.splits == null ? new double[0] : best.splits.clone();
    }

    public void attempt(String lessonId) {
        this.record(lessonId).attempts++;
    }

    public Outcome complete(Lesson lesson, TrainingScale scale, LessonRun.Result result) {
        Chapter chapter = Curriculum.chapter(lesson.chapter());
        boolean chapterBefore = chapter != null && Curriculum.chapterComplete(chapter, this);
        LessonRecord record = this.record(lesson.id());
        int previous = Math.clamp(record.stars, 0, StarRule.MAX_STARS);
        record.completions++;
        record.stars = Math.max(previous, Math.clamp(result.stars(), 1, StarRule.MAX_STARS));
        if (record.bests == null) {
            record.bests = new LinkedHashMap<>();
        }
        Best best = record.bests.get(scale.key());
        double previousTime = best == null ? -1.0 : best.time;
        boolean faster = !result.assisted() && (best == null || best.time < 0.0 || result.time() < best.time);
        if (faster) {
            double metric = best != null && best.metric >= 0.0 ? Math.min(best.metric, result.metric()) : result.metric();
            record.bests.put(scale.key(), new Best(result.time(), metric, result.splits()));
        } else if (best != null && !result.assisted() && result.metric() < best.metric) {
            best.metric = result.metric();
        }
        boolean chapterAfter = chapter != null && Curriculum.chapterComplete(chapter, this);
        return new Outcome(previous, record.stars, record.stars > previous, faster && previousTime >= 0.0, previousTime,
                chapterAfter && !chapterBefore);
    }

    public void sanitize() {
        if (this.lessons == null) {
            this.lessons = new LinkedHashMap<>();
            return;
        }
        this.lessons.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
        for (LessonRecord record : this.lessons.values()) {
            record.stars = Math.clamp(record.stars, 0, StarRule.MAX_STARS);
            record.attempts = Math.max(0, record.attempts);
            record.completions = Math.max(0, record.completions);
            if (record.bests == null) {
                record.bests = new LinkedHashMap<>();
            }
            record.bests.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null
                    || !Double.isFinite(entry.getValue().time) || entry.getValue().time < 0.0);
            for (Best best : record.bests.values()) {
                if (best.splits == null) {
                    best.splits = new double[0];
                }
                if (!Double.isFinite(best.metric) || best.metric < 0.0) {
                    best.metric = best.time;
                }
            }
        }
    }
}
