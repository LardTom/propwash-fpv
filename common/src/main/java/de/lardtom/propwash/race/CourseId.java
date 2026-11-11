package de.lardtom.propwash.race;

import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class CourseId {
    private static final Pattern PLAIN = Pattern.compile("[0-9a-fA-F]{32}");
    private static final Pattern DASHED = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private CourseId() {
    }

    public static UUID random() {
        return UUID.randomUUID();
    }

    public static @Nullable UUID parse(@Nullable String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.strip();
        if (DASHED.matcher(trimmed).matches()) {
            return UUID.fromString(trimmed.toLowerCase(Locale.ROOT));
        }
        if (PLAIN.matcher(trimmed).matches()) {
            String lower = trimmed.toLowerCase(Locale.ROOT);
            return UUID.fromString(lower.substring(0, 8) + "-" + lower.substring(8, 12) + "-" + lower.substring(12, 16) + "-"
                    + lower.substring(16, 20) + "-" + lower.substring(20));
        }
        return null;
    }

    public static Optional<UUID> fromInput(@Nullable String text) {
        return Optional.ofNullable(parse(text));
    }

    public static String text(UUID id) {
        return id.toString();
    }

    public static boolean usable(@Nullable UUID id) {
        return id != null && (id.getMostSignificantBits() != 0L || id.getLeastSignificantBits() != 0L);
    }

    public static int assign(List<CourseRegistry.Course> courses, Supplier<UUID> source) {
        Set<UUID> seen = new HashSet<>();
        int assigned = 0;
        for (CourseRegistry.Course course : courses) {
            UUID stored = parse(course.uuid);
            if (!usable(stored) || !seen.add(stored)) {
                UUID fresh = source.get();
                while (!usable(fresh) || seen.contains(fresh)) {
                    fresh = source.get();
                }
                seen.add(fresh);
                course.assignKey(fresh);
                assigned++;
            } else {
                course.assignKey(stored);
            }
        }
        return assigned;
    }
}
