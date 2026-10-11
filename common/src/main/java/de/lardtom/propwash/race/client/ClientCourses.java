package de.lardtom.propwash.race.client;

import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.RacePayloads;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class ClientCourses {
    private static final Map<Integer, CourseData> COURSES = new TreeMap<>();
    private static final Map<Integer, List<GateGeometry>> GEOMETRY = new HashMap<>();
    private static int version;

    private ClientCourses() {
    }

    static void apply(RacePayloads.CourseSyncS2C payload) {
        if (payload.replaceAll()) {
            COURSES.clear();
        }
        for (CourseData course : payload.courses()) {
            COURSES.put(course.id(), course);
        }
        for (int id : payload.removed()) {
            COURSES.remove(id);
        }
        GEOMETRY.clear();
        version++;
    }

    static void clear() {
        COURSES.clear();
        GEOMETRY.clear();
        version++;
    }

    public static @Nullable CourseData get(int id) {
        return COURSES.get(id);
    }

    public static Collection<CourseData> all() {
        return COURSES.values();
    }

    public static List<GateGeometry> geometry(CourseData course) {
        return GEOMETRY.computeIfAbsent(course.id(), id -> course.geometry());
    }

    public static int version() {
        return version;
    }
}
