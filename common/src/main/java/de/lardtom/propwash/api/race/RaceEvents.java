package de.lardtom.propwash.api.race;

import de.lardtom.propwash.api.event.PropwashEvent;

public final class RaceEvents {
    public static final PropwashEvent<LapCompleted> LAP_COMPLETED = new PropwashEvent<>("race_lap_completed");
    public static final PropwashEvent<RaceFinished> RACE_FINISHED = new PropwashEvent<>("race_finished");
    public static final PropwashEvent<PersonalBestSet> PERSONAL_BEST = new PropwashEvent<>("race_personal_best");
    public static final PropwashEvent<CourseRecordSet> COURSE_RECORD = new PropwashEvent<>("race_course_record");
    public static final PropwashEvent<CourseCreated> COURSE_CREATED = new PropwashEvent<>("race_course_created");
    public static final PropwashEvent<CourseChanged> COURSE_CHANGED = new PropwashEvent<>("race_course_changed");
    public static final PropwashEvent<CourseDeleted> COURSE_DELETED = new PropwashEvent<>("race_course_deleted");

    private RaceEvents() {
    }

    @FunctionalInterface
    public interface LapCompleted {
        void onLapCompleted(LapResult lap);
    }

    @FunctionalInterface
    public interface RaceFinished {
        void onRaceFinished(RaceResult race);
    }

    @FunctionalInterface
    public interface PersonalBestSet {
        void onPersonalBest(PersonalBest best);
    }

    @FunctionalInterface
    public interface CourseRecordSet {
        void onCourseRecord(CourseRecord record);
    }

    @FunctionalInterface
    public interface CourseCreated {
        void onCourseCreated(CourseInfo course);
    }

    @FunctionalInterface
    public interface CourseChanged {
        void onCourseChanged(CourseChange change);
    }

    @FunctionalInterface
    public interface CourseDeleted {
        void onCourseDeleted(DeletedCourse course);
    }
}
