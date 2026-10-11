package de.lardtom.propwash.join;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

public final class VersionOrder {
    private VersionOrder() {
    }

    public static OptionalInt compare(String left, String right) {
        Parsed a = parse(left);
        Parsed b = parse(right);
        if (a == null || b == null) {
            return OptionalInt.empty();
        }
        int length = Math.max(a.numbers().size(), b.numbers().size());
        for (int i = 0; i < length; i++) {
            long x = i < a.numbers().size() ? a.numbers().get(i) : 0L;
            long y = i < b.numbers().size() ? b.numbers().get(i) : 0L;
            if (x != y) {
                return OptionalInt.of(Long.compare(x, y));
            }
        }
        if (a.pre().equals(b.pre())) {
            return OptionalInt.of(0);
        }
        if (a.pre().isEmpty()) {
            return OptionalInt.of(1);
        }
        if (b.pre().isEmpty()) {
            return OptionalInt.of(-1);
        }
        return OptionalInt.of(comparePre(a.pre(), b.pre()));
    }

    private static int comparePre(String a, String b) {
        String[] left = a.split("\\.");
        String[] right = b.split("\\.");
        for (int i = 0; i < Math.min(left.length, right.length); i++) {
            boolean ln = left[i].matches("\\d{1,9}");
            boolean rn = right[i].matches("\\d{1,9}");
            int c = ln && rn ? Integer.compare(Integer.parseInt(left[i]), Integer.parseInt(right[i]))
                    : ln ? -1 : rn ? 1 : left[i].compareTo(right[i]);
            if (c != 0) {
                return Integer.signum(c);
            }
        }
        return Integer.compare(left.length, right.length);
    }

    private static Parsed parse(String version) {
        if (version == null) {
            return null;
        }
        String value = version.strip();
        int plus = value.indexOf('+');
        if (plus >= 0) {
            value = value.substring(0, plus);
        }
        String pre = "";
        int dash = value.indexOf('-');
        if (dash >= 0) {
            pre = value.substring(dash + 1);
            value = value.substring(0, dash);
        }
        if (value.isEmpty()) {
            return null;
        }
        List<Long> numbers = new ArrayList<>();
        for (String part : value.split("\\.")) {
            if (!part.matches("\\d{1,18}")) {
                return null;
            }
            numbers.add(Long.parseLong(part));
        }
        return new Parsed(numbers, pre);
    }

    private record Parsed(List<Long> numbers, String pre) {
    }
}
