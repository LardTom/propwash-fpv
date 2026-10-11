package de.lardtom.propwash.race;

import java.util.ArrayList;
import java.util.List;

public final class CourseOrder {
    private CourseOrder() {
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
