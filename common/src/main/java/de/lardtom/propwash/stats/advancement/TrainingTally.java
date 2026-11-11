package de.lardtom.propwash.stats.advancement;

import de.lardtom.propwash.guide.training.Chapter;
import de.lardtom.propwash.guide.training.Curriculum;

public record TrainingTally(int chapters, boolean perfect) {
    public static final TrainingTally NONE = new TrainingTally(0, false);

    public TrainingTally {
        chapters = Math.clamp(chapters, 0, Curriculum.chapters().size());
    }

    public static TrainingTally of(Curriculum.StarSource progress) {
        int chapters = 0;
        for (Chapter chapter : Curriculum.chapters()) {
            if (Curriculum.chapterComplete(chapter, progress)) {
                chapters++;
            }
        }
        return new TrainingTally(chapters, Curriculum.maxStars() > 0 && Curriculum.totalStars(progress) >= Curriculum.maxStars());
    }
}
