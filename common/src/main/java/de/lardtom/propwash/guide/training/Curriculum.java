package de.lardtom.propwash.guide.training;

import de.lardtom.propwash.trick.TrickType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Curriculum {
    public static final String BASICS = "basics";
    public static final String FORWARD = "forward";
    public static final String RACING = "racing";
    public static final String FREESTYLE = "freestyle";
    public static final String ADVANCED = "advanced";
    public static final String ICON_REMOTE = "propwash:remote";
    public static final String ICON_DRONE = "propwash:drone";
    public static final String ICON_GATE = "propwash:gate_medium";
    public static final String ICON_GOGGLES = "propwash:goggles_analog";
    public static final String ICON_COURSE = "propwash:course_tool";
    public static final double RING = 3.5;
    public static final double GATE = 4.0;
    public static final double SMALL_RING = 2.2;
    public static final double CRUISE_HEIGHT = 3.0;

    private static final List<Chapter> CHAPTERS = build();
    private static final Map<String, Lesson> LESSONS = index(CHAPTERS);

    @FunctionalInterface
    public interface StarSource {
        int stars(String lessonId);
    }

    private Curriculum() {
    }

    public static List<Chapter> chapters() {
        return CHAPTERS;
    }

    public static List<Lesson> lessons() {
        return List.copyOf(LESSONS.values());
    }

    public static @Nullable Chapter chapter(String id) {
        for (Chapter chapter : CHAPTERS) {
            if (chapter.id().equals(id)) {
                return chapter;
            }
        }
        return null;
    }

    public static @Nullable Lesson lesson(String id) {
        return id == null ? null : LESSONS.get(id);
    }

    public static int chapterNumber(String chapterId) {
        Chapter chapter = chapter(chapterId);
        return chapter == null ? 0 : chapter.number();
    }

    public static @Nullable Lesson next(Lesson lesson) {
        List<Lesson> all = lessons();
        int index = all.indexOf(lesson);
        return index >= 0 && index + 1 < all.size() ? all.get(index + 1) : null;
    }

    public static @Nullable Lesson nextOpen(Lesson lesson, StarSource progress) {
        List<Lesson> all = lessons();
        int index = all.indexOf(lesson);
        for (int i = index + 1; i < all.size(); i++) {
            Lesson candidate = all.get(i);
            if (unlocked(candidate, progress) && progress.stars(candidate.id()) < StarRule.MAX_STARS) {
                return candidate;
            }
        }
        return null;
    }

    public static boolean chapterComplete(Chapter chapter, StarSource progress) {
        for (Lesson lesson : chapter.lessons()) {
            if (progress.stars(lesson.id()) <= 0) {
                return false;
            }
        }
        return true;
    }

    public static boolean chapterUnlocked(Chapter chapter, StarSource progress) {
        for (String required : chapter.requires()) {
            Chapter before = chapter(required);
            if (before != null && !chapterComplete(before, progress)) {
                return false;
            }
        }
        return true;
    }

    public static boolean unlocked(Lesson lesson, StarSource progress) {
        Chapter chapter = chapter(lesson.chapter());
        if (chapter == null || !chapterUnlocked(chapter, progress)) {
            return false;
        }
        int index = chapter.lessons().indexOf(lesson);
        return index <= 0 || progress.stars(chapter.lessons().get(index - 1).id()) > 0;
    }

    public static int chapterStars(Chapter chapter, StarSource progress) {
        int total = 0;
        for (Lesson lesson : chapter.lessons()) {
            total += Math.clamp(progress.stars(lesson.id()), 0, StarRule.MAX_STARS);
        }
        return total;
    }

    public static int totalStars(StarSource progress) {
        int total = 0;
        for (Chapter chapter : CHAPTERS) {
            total += chapterStars(chapter, progress);
        }
        return total;
    }

    public static int maxStars() {
        return LESSONS.size() * StarRule.MAX_STARS;
    }

    public static @Nullable Lesson firstOpen(StarSource progress) {
        Lesson fallback = null;
        for (Lesson lesson : LESSONS.values()) {
            if (!unlocked(lesson, progress)) {
                continue;
            }
            if (progress.stars(lesson.id()) <= 0) {
                return lesson;
            }
            fallback = lesson;
        }
        return fallback;
    }

    private static Map<String, Lesson> index(List<Chapter> chapters) {
        Map<String, Lesson> map = new LinkedHashMap<>();
        for (Chapter chapter : chapters) {
            for (Lesson lesson : chapter.lessons()) {
                if (map.put(lesson.id(), lesson) != null) {
                    throw new IllegalStateException("Doppelte Lektion: " + lesson.id());
                }
            }
        }
        return map;
    }

    private static List<Chapter> build() {
        return List.of(
                new Chapter(BASICS, 1, ICON_REMOTE, List.of(), basics()),
                new Chapter(FORWARD, 2, ICON_DRONE, List.of(BASICS), forward()),
                new Chapter(RACING, 3, ICON_GATE, List.of(FORWARD), racing()),
                new Chapter(FREESTYLE, 4, ICON_GOGGLES, List.of(FORWARD), freestyle()),
                new Chapter(ADVANCED, 5, ICON_COURSE, List.of(RACING, FREESTYLE), advanced()));
    }

    private static List<Lesson> basics() {
        List<Lesson> lessons = new ArrayList<>();
        lessons.add(Lesson.builder("arm", BASICS, 1, ICON_REMOTE)
                .arm(2.0)
                .limit(60.0).stars(StarRule.time(5.0, 10.0)).build());
        lessons.add(Lesson.builder("hover", BASICS, 2, ICON_REMOTE)
                .zone(Zone.hover(0.0, 3.0, 0.0, 1.5, 1.5, 1.5))
                .hover(0, 6.0)
                .limit(90.0).stars(StarRule.time(9.0, 15.0)).build());
        lessons.add(Lesson.builder("land", BASICS, 3, ICON_REMOTE)
                .pad(0.0, 10.0, 1.5)
                .land(1)
                .limit(60.0).stars(StarRule.padDistance(0.4, 0.8)).build());
        lessons.add(Lesson.builder("yaw", BASICS, 4, ICON_REMOTE)
                .zone(Zone.hover(0.0, 3.0, 0.0, 2.0, 1.5, 2.0))
                .marker(8.0, 0.0, 5.0).marker(0.0, -8.0, 5.0).marker(-8.0, 0.0, 5.0).marker(0.0, 8.0, 5.0)
                .heading(0, List.of(0, 1, 2, 3), 1.0, 12.0)
                .limit(120.0).stars(StarRule.time(12.0, 20.0)).build());
        return lessons;
    }

    private static List<Lesson> forward() {
        List<Lesson> lessons = new ArrayList<>();
        lessons.add(Lesson.builder("straight", FORWARD, 1, ICON_DRONE)
                .rings(line(5, 8.0, 8.0, new double[]{2.5}, RING))
                .rings(0, 5, false)
                .limit(60.0).stars(StarRule.time(6.5, 10.0)).build());
        lessons.add(Lesson.builder("altitude", FORWARD, 2, ICON_DRONE)
                .rings(line(5, 8.0, 10.0, new double[]{2.5, 5.0, 2.5, 6.0, 3.0}, RING))
                .rings(0, 5, false)
                .limit(75.0).stars(StarRule.time(8.5, 13.0)).build());
        List<Ring> turn = new ArrayList<>(arc(10.0, 8.0, 10.0, 0.0, 180.0, 5, CRUISE_HEIGHT, RING));
        turn.add(Ring.standing(20.0, CRUISE_HEIGHT, -2.0, 180.0, RING));
        lessons.add(Lesson.builder("turns", FORWARD, 3, ICON_DRONE)
                .rings(turn)
                .rings(0, turn.size(), false)
                .limit(75.0).stars(StarRule.time(8.5, 12.5)).build());
        List<Ring> eight = figureEight(18.0, 12.0, 16.0, CRUISE_HEIGHT, RING);
        lessons.add(Lesson.builder("figure_eight", FORWARD, 4, ICON_DRONE)
                .rings(eight)
                .pylon(0.0, 25.0, 6.0).pylon(0.0, 11.0, 6.0)
                .rings(0, eight.size(), false)
                .limit(90.0).stars(StarRule.time(18.0, 28.0)).build());
        return lessons;
    }

    private static List<Lesson> racing() {
        List<Lesson> lessons = new ArrayList<>();
        double gateHeight = GATE * 0.5 + 0.2;
        lessons.add(Lesson.builder("gate_line", RACING, 1, ICON_GATE)
                .rings(line(6, 8.0, 10.0, new double[]{gateHeight}, GATE))
                .rings(0, 6, false)
                .limit(60.0).stars(StarRule.time(8.0, 11.5)).build());
        List<Ring> slalom = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            slalom.add(Ring.standing(i % 2 == 0 ? -3.0 : 3.0, gateHeight, 8.0 + 10.0 * i, 0.0, GATE));
        }
        lessons.add(Lesson.builder("slalom", RACING, 2, ICON_GATE)
                .rings(slalom)
                .rings(0, slalom.size(), false)
                .limit(60.0).stars(StarRule.time(11.0, 17.0)).build());
        List<Ring> loop = List.of(
                Ring.standing(4.0, gateHeight, 12.0, 0.0, GATE),
                Ring.standing(4.0, gateHeight, 30.0, 0.0, GATE),
                Ring.standing(14.0, gateHeight, 44.0, 90.0, GATE),
                Ring.standing(24.0, gateHeight, 30.0, 180.0, GATE),
                Ring.standing(24.0, gateHeight, 12.0, 180.0, GATE),
                Ring.standing(14.0, gateHeight, 0.0, 270.0, GATE));
        lessons.add(Lesson.builder("short_course", RACING, 3, ICON_GATE)
                .rings(loop)
                .laps(0, loop.size(), 2)
                .limit(150.0).stars(StarRule.time(28.0, 42.0)).build());
        return lessons;
    }

    private static List<Lesson> freestyle() {
        List<Lesson> lessons = new ArrayList<>();
        Set<TrickType> flips = TrickType.withVariants(TrickType.FLIP);
        Set<TrickType> rolls = TrickType.withVariants(TrickType.ROLL);
        lessons.add(Lesson.builder("flip", FREESTYLE, 1, ICON_GOGGLES)
                .zone(Zone.trick(0.0, 8.0, 6.0, 4.0, 18.0))
                .tricks(flips, 1, 0)
                .limit(60.0).stars(StarRule.time(6.5, 12.0)).build());
        lessons.add(Lesson.builder("roll", FREESTYLE, 2, ICON_GOGGLES)
                .zone(Zone.trick(0.0, 8.0, 6.0, 4.0, 18.0))
                .tricks(rolls, 1, 0)
                .limit(60.0).stars(StarRule.time(6.5, 12.0)).build());
        lessons.add(Lesson.builder("power_loop", FREESTYLE, 3, ICON_GOGGLES)
                .zone(new Zone(-6.0, 4.0, 2.0, 6.0, 22.0, 30.0, Zone.Kind.TRICK))
                .tricks(TrickType.withVariants(TrickType.POWER_LOOP), 1, 0)
                .limit(90.0).stars(StarRule.time(11.0, 18.0)).build());
        lessons.add(Lesson.builder("split_s", FREESTYLE, 4, ICON_GOGGLES)
                .zone(new Zone(-6.0, 4.0, 2.0, 6.0, 22.0, 30.0, Zone.Kind.TRICK))
                .tricks(TrickType.withVariants(TrickType.SPLIT_S), 1, 0)
                .limit(90.0).stars(StarRule.time(10.0, 16.0)).build());
        lessons.add(Lesson.builder("combo", FREESTYLE, 5, ICON_GOGGLES)
                .zone(new Zone(-6.0, 4.0, 2.0, 6.0, 22.0, 30.0, Zone.Kind.TRICK))
                .combo(3, 0)
                .limit(90.0).stars(StarRule.time(11.0, 18.0)).build());
        return lessons;
    }

    private static List<Lesson> advanced() {
        List<Lesson> lessons = new ArrayList<>();
        List<Ring> dive = List.of(
                Ring.standing(0.0, 3.0, 8.0, 0.0, RING),
                Ring.flat(0.0, 8.0, 20.0, 0.0, 4.0),
                Ring.standing(0.0, 2.5, 32.0, 0.0, RING));
        lessons.add(Lesson.builder("dive_gate", ADVANCED, 1, ICON_COURSE)
                .rings(dive)
                .rings(0, dive.size(), false)
                .limit(75.0).stars(StarRule.time(8.5, 12.0)).build());
        List<Ring> precision = List.of(
                Ring.standing(0.0, 2.5, 8.0, 0.0, SMALL_RING),
                Ring.standing(2.5, 3.0, 15.0, 25.0, SMALL_RING),
                Ring.standing(4.0, 3.5, 22.0, 0.0, SMALL_RING),
                Ring.standing(1.5, 3.0, 29.0, -25.0, SMALL_RING),
                Ring.standing(-1.5, 2.5, 36.0, -15.0, SMALL_RING),
                Ring.standing(-2.0, 2.5, 43.0, 0.0, SMALL_RING));
        lessons.add(Lesson.builder("precision", ADVANCED, 2, ICON_COURSE)
                .rings(precision)
                .rings(0, precision.size(), true)
                .limit(60.0).stars(StarRule.time(8.5, 12.0)).build());
        List<Ring> low = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            low.add(new Ring(0.0, 1.0, 8.0 + 8.0 * i, 0.0, 3.0, 1.6, false));
        }
        lessons.add(Lesson.builder("low_flight", ADVANCED, 3, ICON_COURSE)
                .rings(low)
                .rings(0, low.size(), false)
                .limit(60.0).stars(StarRule.time(6.5, 10.0)).build());
        List<Ring> exam = List.of(
                Ring.standing(0.0, 3.0, 10.0, 0.0, RING),
                Ring.standing(0.0, 4.5, 20.0, 0.0, RING),
                Ring.flat(0.0, 6.0, 46.0, 0.0, 4.0));
        lessons.add(Lesson.builder("final_exam", ADVANCED, 4, ICON_COURSE)
                .rings(exam)
                .zone(new Zone(-6.0, 6.0, 26.0, 6.0, 18.0, 38.0, Zone.Kind.TRICK))
                .pad(0.0, 56.0, 1.5)
                .rings(0, 2, false)
                .tricks(TrickType.withVariants(TrickType.FLIP, TrickType.ROLL), 1, 0)
                .rings(2, 3, false)
                .land(1)
                .limit(120.0).stars(StarRule.time(21.0, 28.0)).build());
        return lessons;
    }

    public static List<Ring> line(int count, double first, double spacing, double[] heights, double size) {
        List<Ring> rings = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rings.add(Ring.standing(0.0, heights[Math.min(i, heights.length - 1)], first + spacing * i, 0.0, size));
        }
        return rings;
    }

    public static List<Ring> arc(double centerX, double centerZ, double radius, double fromDeg, double toDeg, int count, double height,
                                 double size) {
        List<Ring> rings = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double theta = fromDeg + (toDeg - fromDeg) * i / Math.max(1, count - 1);
            double r = Math.toRadians(theta);
            rings.add(Ring.standing(centerX - radius * Math.cos(r), height, centerZ + radius * Math.sin(r), theta, size));
        }
        return rings;
    }

    public static List<Ring> figureEight(double centerZ, double lengthHalf, double width, double height, double size) {
        List<Ring> rings = new ArrayList<>();
        double[] points = {0.25, 0.5, 0.75, 1.25, 1.5, 1.75, 2.25};
        for (double p : points) {
            double t = Math.PI * p;
            double x = width * 0.5 * Math.sin(2.0 * t);
            double z = centerZ + lengthHalf * Math.sin(t);
            double dx = width * Math.cos(2.0 * t);
            double dz = lengthHalf * Math.cos(t);
            double heading = Math.toDegrees(Math.atan2(dx, dz));
            rings.add(Ring.standing(x, height, z, heading, size));
        }
        return rings;
    }
}
