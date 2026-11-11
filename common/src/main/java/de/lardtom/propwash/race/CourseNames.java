package de.lardtom.propwash.race;

import org.jspecify.annotations.Nullable;

public final class CourseNames {
    public static final int MAX = 32;

    private CourseNames() {
    }

    public static String clean(@Nullable String raw) {
        String text = squeeze(raw, Integer.MAX_VALUE);
        while (true) {
            String next = quoted(text) ? squeeze(unquote(text), Integer.MAX_VALUE) : squeeze(text, MAX);
            if (next.equals(text)) {
                return text;
            }
            text = next;
        }
    }

    private static boolean quoted(String text) {
        if (text.length() < 2) {
            return false;
        }
        char first = text.charAt(0);
        return (first == '"' || first == '\'') && text.charAt(text.length() - 1) == first;
    }

    public static String unquote(String input) {
        String text = input.strip();
        if (!quoted(text)) {
            return text;
        }
        StringBuilder out = new StringBuilder();
        for (int i = 1; i < text.length() - 1; i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length() - 1) {
                c = text.charAt(++i);
            }
            out.append(c);
        }
        return out.toString().strip();
    }

    private static String squeeze(@Nullable String raw, int max) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        boolean space = false;
        int points = 0;
        for (int i = 0; i < raw.length() && points < max; ) {
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
                if (points + 1 >= max) {
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
