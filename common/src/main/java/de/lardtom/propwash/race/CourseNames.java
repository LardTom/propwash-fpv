package de.lardtom.propwash.race;

import org.jspecify.annotations.Nullable;

public final class CourseNames {
    public static final int MAX = 32;

    private CourseNames() {
    }

    public static String clean(@Nullable String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        boolean space = false;
        int points = 0;
        for (int i = 0; i < raw.length() && points < MAX; ) {
            int point = raw.codePointAt(i);
            i += Character.charCount(point);
            if (point == '§' || Character.isISOControl(point) && !Character.isWhitespace(point)
                    || Character.getType(point) == Character.FORMAT || Character.getType(point) == Character.SURROGATE
                    || Character.getType(point) == Character.PRIVATE_USE || Character.getType(point) == Character.UNASSIGNED) {
                continue;
            }
            if (Character.isWhitespace(point) || Character.isSpaceChar(point)) {
                space = out.length() > 0;
                continue;
            }
            if (space) {
                if (points + 1 >= MAX) {
                    break;
                }
                out.append(' ');
                points++;
                space = false;
            }
            out.appendCodePoint(point);
            points++;
        }
        return out.toString();
    }

    public static boolean named(@Nullable String name) {
        return name != null && !name.isEmpty();
    }
}
