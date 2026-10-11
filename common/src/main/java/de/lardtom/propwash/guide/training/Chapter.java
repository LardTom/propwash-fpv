package de.lardtom.propwash.guide.training;

import java.util.List;

public record Chapter(String id, int number, String icon, List<String> requires, List<Lesson> lessons) {
    public static final String PREFIX = "propwash.guide.training.chapter.";

    public Chapter {
        requires = List.copyOf(requires);
        lessons = List.copyOf(lessons);
    }

    public String titleKey() {
        return PREFIX + this.id + ".title";
    }

    public String descriptionKey() {
        return PREFIX + this.id + ".description";
    }

    public int maxStars() {
        return this.lessons.size() * StarRule.MAX_STARS;
    }
}
