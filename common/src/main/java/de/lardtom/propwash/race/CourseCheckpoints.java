package de.lardtom.propwash.race;

import java.util.List;

public final class CourseCheckpoints {
    public static final int NONE = -1;

    private CourseCheckpoints() {
    }

    public static int count(List<CourseElement> kinds) {
        int total = 0;
        for (CourseElement kind : kinds) {
            total += kind.stages();
        }
        return total;
    }

    public static int index(List<CourseElement> kinds, int element, int stage) {
        if (element < 0 || element >= kinds.size()) {
            return NONE;
        }
        int stages = kinds.get(element).stages();
        if (stage < 0 || stage >= stages) {
            return NONE;
        }
        int first = kinds.getFirst().stages();
        if (element == 0) {
            if (stage == first - 1) {
                return 0;
            }
            return count(kinds) - (first - 1) + stage;
        }
        int offset = 1;
        for (int i = 1; i < element; i++) {
            offset += kinds.get(i).stages();
        }
        return offset + stage;
    }

    public static List<CourseElement> kinds(List<GateGeometry> gates) {
        return gates.stream().map(GateGeometry::kind).toList();
    }
}
