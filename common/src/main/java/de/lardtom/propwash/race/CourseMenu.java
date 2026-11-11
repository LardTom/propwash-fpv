package de.lardtom.propwash.race;

import java.util.ArrayList;
import java.util.List;

public final class CourseMenu {
    public enum Section {
        COURSE,
        ACCESS,
        BOARD,
        DELETE
    }

    public enum Need {
        NONE,
        EDIT,
        OPERATOR,
        DELETE,
        ADMIN
    }

    public enum Role {
        OWNER,
        OPERATOR,
        VIEWER
    }

    public enum Row {
        NAME(Section.COURSE, Need.EDIT),
        KEY(Section.COURSE, Need.NONE),
        MODE(Section.COURSE, Need.EDIT),
        LAPS(Section.COURSE, Need.EDIT),
        FINISH(Section.COURSE, Need.NONE),
        GATES(Section.COURSE, Need.NONE),
        RACE_LINE(Section.COURSE, Need.EDIT),
        BIND(Section.COURSE, Need.EDIT),
        OWNER(Section.ACCESS, Need.NONE),
        RIGHTS(Section.ACCESS, Need.NONE),
        BOARD(Section.BOARD, Need.NONE),
        GHOSTS(Section.BOARD, Need.NONE),
        CREATIVE_FC(Section.BOARD, Need.ADMIN),
        RESET(Section.BOARD, Need.OPERATOR),
        DELETE(Section.DELETE, Need.DELETE);

        private final Section section;
        private final Need need;

        Row(Section section, Need need) {
            this.section = section;
            this.need = need;
        }

        public Section section() {
            return this.section;
        }

        public Need need() {
            return this.need;
        }
    }

    public record Rights(boolean edit, boolean delete, boolean reset, Role role, boolean admin) {
        public static final Rights VIEW = new Rights(false, false, false, Role.VIEWER, false);

        public Rights(boolean edit, boolean delete, boolean reset, Role role) {
            this(edit, delete, reset, role, reset);
        }

        public static Rights of(CourseAccess edit, CourseAccess delete, boolean owner, boolean gamemaster) {
            return of(edit, delete, owner, gamemaster, gamemaster);
        }

        public static Rights of(CourseAccess edit, CourseAccess delete, boolean owner, boolean gamemaster, boolean admin) {
            boolean allowed = edit.allowed();
            Role role = !allowed ? Role.VIEWER : owner ? Role.OWNER : Role.OPERATOR;
            return new Rights(allowed, delete.allowed(), gamemaster, role, admin);
        }

        public boolean readOnly() {
            return !this.edit && !this.delete && !this.reset && !this.admin;
        }
    }

    private CourseMenu() {
    }

    public static List<Row> rows(boolean pointToPoint) {
        List<Row> out = new ArrayList<>();
        for (Row row : Row.values()) {
            if (row != (pointToPoint ? Row.LAPS : Row.FINISH)) {
                out.add(row);
            }
        }
        return out;
    }

    public static List<Row> rows(Section section, boolean pointToPoint) {
        List<Row> out = new ArrayList<>();
        for (Row row : rows(pointToPoint)) {
            if (row.section() == section) {
                out.add(row);
            }
        }
        return out;
    }

    public static boolean active(Row row, Rights rights) {
        return switch (row.need()) {
            case NONE -> true;
            case EDIT -> rights.edit();
            case OPERATOR -> rights.reset();
            case DELETE -> rights.delete();
            case ADMIN -> rights.admin();
        };
    }

    public static String resetCommand(int courseId) {
        return "propwash race reset " + courseId;
    }

    public static int times(RacePayloads.LeaderboardS2C board) {
        return board.times();
    }
}
