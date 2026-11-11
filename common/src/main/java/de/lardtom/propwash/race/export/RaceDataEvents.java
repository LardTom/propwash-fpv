package de.lardtom.propwash.race.export;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.event.PropwashEvent;
import de.lardtom.propwash.api.race.BoardEntry;
import de.lardtom.propwash.api.race.Category;
import de.lardtom.propwash.api.race.ChangeKind;
import de.lardtom.propwash.api.race.CourseChange;
import de.lardtom.propwash.api.race.CourseInfo;
import de.lardtom.propwash.api.race.CourseRecord;
import de.lardtom.propwash.api.race.DeletedCourse;
import de.lardtom.propwash.api.race.LapResult;
import de.lardtom.propwash.api.race.PersonalBest;
import de.lardtom.propwash.api.race.RaceEvents;
import de.lardtom.propwash.api.race.RaceResult;
import de.lardtom.propwash.race.AssistReasons;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.zone.Zone;
import de.lardtom.propwash.zone.ZoneData;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Consumer;

public final class RaceDataEvents {
    public static final Before NONE = new Before(null, null);

    public record Before(Leaderboard.@Nullable Entry own, Leaderboard.@Nullable Entry record) {
    }

    private RaceDataEvents() {
    }

    public static Before before(Leaderboard board, UUID player, String frame, boolean assisted) {
        if (!RaceEvents.PERSONAL_BEST.hasListeners() && !RaceEvents.COURSE_RECORD.hasListeners()) {
            return NONE;
        }
        Leaderboard.Entry own = assisted ? board.bestAssisted(player, frame) : board.best(player, frame);
        Leaderboard.Entry record = assisted ? null : board.record(null).orElse(null);
        return new Before(own, record);
    }

    public static void lapSaved(CourseRegistry registry, CourseRegistry.Course course, UUID player, String frame, boolean assisted, boolean improved,
                                boolean courseRecord, Before before) {
        if (!improved || !RaceEvents.PERSONAL_BEST.hasListeners() && !RaceEvents.COURSE_RECORD.hasListeners()) {
            return;
        }
        Leaderboard board = registry.board(course);
        Leaderboard.Entry stored = assisted ? board.bestAssisted(player, frame) : board.best(player, frame);
        if (stored == null) {
            return;
        }
        Category category = assisted ? Category.ASSISTED : Category.CLEAN;
        BoardEntry entry = RaceSnapshots.lapEntry(RaceSnapshots.lapRank(board, player, stored.frame(), assisted), stored, category);
        Leaderboard.Entry previous = before.own();
        PersonalBest best = new PersonalBest(course.key(), course.id, course.layout, entry,
                previous == null ? OptionalLong.empty() : OptionalLong.of(BoardEntry.millis(previous.nanos())));
        fire(RaceEvents.PERSONAL_BEST, listener -> listener.onPersonalBest(best));
        if (courseRecord) {
            Leaderboard.Entry old = before.record();
            CourseRecord record = new CourseRecord(course.key(), course.id, course.name, course.layout, RaceSnapshots.lapEntry(1, stored, Category.CLEAN),
                    old == null ? Optional.empty() : Optional.of(RaceSnapshots.lapEntry(0, old, Category.CLEAN)));
            fire(RaceEvents.COURSE_RECORD, listener -> listener.onCourseRecord(record));
        }
    }

    public static void raceSaved(CourseRegistry registry, CourseRegistry.Course course, UUID player, int laps, boolean assisted, long previousNanos) {
        if (!RaceEvents.PERSONAL_BEST.hasListeners()) {
            return;
        }
        Leaderboard board = registry.board(course);
        Category category = assisted ? Category.ASSISTED : Category.CLEAN;
        List<Leaderboard.RaceRow> rows = board.raceRows(assisted, laps);
        for (int i = 0; i < rows.size(); i++) {
            Leaderboard.RaceRow row = rows.get(i);
            if (row.player().equals(player)) {
                BoardEntry entry = RaceSnapshots.raceEntry(i + 1, row, category);
                PersonalBest best = new PersonalBest(course.key(), course.id, course.layout, entry,
                        previousNanos == LapTracker.NONE || previousNanos <= 0L ? OptionalLong.empty() : OptionalLong.of(BoardEntry.millis(previousNanos)));
                fire(RaceEvents.PERSONAL_BEST, listener -> listener.onPersonalBest(best));
                return;
            }
        }
    }

    public static void lap(CourseRegistry registry, CourseRegistry.Course course, UUID player, String name, int lap, long nanos, String frame,
                           AssistReasons assist, boolean saved, boolean personalBest, boolean classRecord, boolean courseRecord) {
        if (!RaceEvents.LAP_COMPLETED.hasListeners()) {
            return;
        }
        boolean assisted = assist.any();
        int rank = saved ? RaceSnapshots.lapRank(registry.board(course), player, frame, assisted) : -1;
        LapResult result = new LapResult(course.key(), course.id, course.name, course.layout, player, name, lap, BoardEntry.millis(nanos), nanos,
                frame, assisted ? Category.ASSISTED : Category.CLEAN, RaceSnapshots.reasons(assist), RaceSnapshots.details(assist), saved, personalBest,
                classRecord, courseRecord, rank);
        fire(RaceEvents.LAP_COMPLETED, listener -> listener.onLapCompleted(result));
    }

    public static void race(CourseRegistry.Course course, UUID player, String name, int laps, long nanos, String frame, AssistReasons assist,
                            boolean saved, boolean personalBest, int heatPlace) {
        if (!RaceEvents.RACE_FINISHED.hasListeners()) {
            return;
        }
        RaceResult result = new RaceResult(course.key(), course.id, course.name, course.layout, player, name, laps, BoardEntry.millis(nanos), nanos,
                frame, assist.any() ? Category.ASSISTED : Category.CLEAN, RaceSnapshots.reasons(assist), RaceSnapshots.details(assist), saved,
                personalBest, heatPlace);
        fire(RaceEvents.RACE_FINISHED, listener -> listener.onRaceFinished(result));
    }

    public static void created(CourseRegistry registry, CourseRegistry.Course course) {
        if (!RaceEvents.COURSE_CREATED.hasListeners()) {
            return;
        }
        CourseInfo info = info(registry, course);
        fire(RaceEvents.COURSE_CREATED, listener -> listener.onCourseCreated(info));
    }

    public static void changed(CourseRegistry registry, CourseRegistry.Course course, ChangeKind kind, int archivedLayout) {
        if (!RaceEvents.COURSE_CHANGED.hasListeners()) {
            return;
        }
        CourseChange change = new CourseChange(kind, info(registry, course), archivedLayout);
        fire(RaceEvents.COURSE_CHANGED, listener -> listener.onCourseChanged(change));
    }

    public static void deleted(CourseRegistry.Course course) {
        if (!RaceEvents.COURSE_DELETED.hasListeners()) {
            return;
        }
        DeletedCourse deleted = new DeletedCourse(course.key(), course.id, course.name, RaceSnapshots.dimension(course.dimension));
        fire(RaceEvents.COURSE_DELETED, listener -> listener.onCourseDeleted(deleted));
    }

    public static CourseInfo info(CourseRegistry registry, CourseRegistry.Course course) {
        return RaceSnapshots.info(course, registry.records(), zones(registry.server(), course.dimension));
    }

    public static List<Zone> zones(@Nullable MinecraftServer server, String dimension) {
        if (server == null) {
            return List.of();
        }
        try {
            return ZoneData.get(server).in(dimension);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    static <T> void fire(PropwashEvent<T> event, Consumer<T> action) {
        event.fire(listener -> {
            try {
                action.accept(listener);
            } catch (RuntimeException e) {
                Constants.LOG.error("Renndaten-Listener für {} fehlgeschlagen", event.name(), e);
            }
        });
    }
}
