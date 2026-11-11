package de.lardtom.propwash.race;

import java.util.ArrayList;
import java.util.List;

public final class CourseOrder {
    public static final String REVERSE_MARK = "←";
    public static final String SEPARATOR = " · ";
    private static final char REVERSE_CODE = 'r';

    private CourseOrder() {
    }

    public record Pass(int index, boolean reverse) {
    }

    public static String encode(List<Pass> passes) {
        if (passes.isEmpty() || passes.size() == 1 && !passes.getFirst().reverse()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (Pass pass : passes) {
            if (!out.isEmpty()) {
                out.append(',');
            }
            out.append(pass.index());
            if (pass.reverse()) {
                out.append(REVERSE_CODE);
            }
        }
        return out.toString();
    }

    public static List<Pass> decode(String text, int first) {
        if (text == null || text.isEmpty()) {
            return first < 0 ? List.of() : List.of(new Pass(first, false));
        }
        List<Pass> passes = new ArrayList<>();
        for (String part : text.split(",")) {
            String value = part.trim();
            boolean reverse = value.endsWith(String.valueOf(REVERSE_CODE));
            if (reverse) {
                value = value.substring(0, value.length() - 1);
            }
            try {
                int index = Integer.parseInt(value);
                if (index >= 0 && index < CourseData.MAX_GATES) {
                    passes.add(new Pass(index, reverse));
                }
            } catch (NumberFormatException ignored) {
                return first < 0 ? List.of() : List.of(new Pass(first, false));
            }
        }
        return List.copyOf(passes);
    }

    public static String label(List<Pass> passes) {
        StringBuilder out = new StringBuilder();
        for (Pass pass : passes) {
            if (!out.isEmpty()) {
                out.append(SEPARATOR);
            }
            out.append(pass.index() + 1);
            if (pass.reverse()) {
                out.append(REVERSE_MARK);
            }
        }
        return out.toString();
    }

    public static boolean plain(List<Pass> passes) {
        return passes.size() == 1 && !passes.getFirst().reverse();
    }

    public static int repeatSide(double lookX, double lookY, double lookZ, GateGeometry geometry) {
        double fx;
        double fy;
        double fz;
        if (geometry.kind() == CourseElement.CUBE) {
            fx = geometry.exitX();
            fy = 0.0;
            fz = geometry.exitZ();
        } else {
            fx = geometry.normalX();
            fy = geometry.normalY();
            fz = geometry.normalZ();
        }
        double dot = lookX * fx + lookY * fy + lookZ * fz;
        return dot < 0.0 ? -1 : 1;
    }

    public static boolean inside(int size, int index) {
        return index >= 0 && index < size;
    }

    public static boolean canMove(List<CourseElement> kinds, int from, int to) {
        if (!inside(kinds.size(), from) || !inside(kinds.size(), to) || from == to) {
            return false;
        }
        List<CourseElement> moved = new ArrayList<>(kinds);
        move(moved, from, to);
        return moved.getFirst().startable();
    }

    public static boolean canStart(List<CourseElement> kinds, int index) {
        return index > 0 && inside(kinds.size(), index) && kinds.get(index).startable();
    }

    public static boolean canRemove(List<CourseElement> kinds, int index) {
        if (!inside(kinds.size(), index)) {
            return false;
        }
        return index != 0 || kinds.size() == 1 || kinds.get(1).startable();
    }

    public static <T> boolean move(List<T> list, int from, int to) {
        if (!inside(list.size(), from) || !inside(list.size(), to) || from == to) {
            return false;
        }
        list.add(to, list.remove(from));
        return true;
    }

    public static <T> boolean rotate(List<T> list, int start) {
        if (start <= 0 || !inside(list.size(), start)) {
            return false;
        }
        List<T> head = new ArrayList<>(list.subList(0, start));
        list.subList(0, start).clear();
        list.addAll(head);
        return true;
    }
}
