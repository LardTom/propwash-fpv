package de.lardtom.propwash.race.client;

import de.lardtom.propwash.race.CourseElement;
import de.lardtom.propwash.race.GateMounting;
import de.lardtom.propwash.race.GateSignal;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.GateTilt;
import de.lardtom.propwash.race.ScoreArea;

import java.util.ArrayList;
import java.util.List;

public final class GateEditModel {
    public static final float WHEEL_DEGREES = 5.0F;
    public static final float FINE_DEGREES = 1.0F;
    public static final int PULSE_STEP = 2;
    public static final int FINE_PULSE_STEP = 1;
    public static final double FINE_NUDGE = 1.0 / 16.0;
    public static final double HALF_NUDGE = 1.0 / 32.0;
    public static final float QUARTER_TURN = 90.0F;
    public static final List<Double> NUDGE_STEPS = List.of(1.0 / 16.0, 0.25, 1.0);
    public static final double DEFAULT_NUDGE_STEP = 0.25;
    public static final int AXIS_X = 0;
    public static final int AXIS_Y = 1;
    public static final int AXIS_Z = 2;
    private static final double GRID_EPSILON = 1.0E-4;

    public enum Row {
        HEADING,
        PITCH,
        ROLL,
        VARIANT,
        WIDTH,
        HEIGHT,
        SIGNAL,
        PULSE,
        RACE_ONLY,
        OPEN_EDGES,
        SCORE_ROLE,
        SCORE_SIDE,
        SCORE_REACH,
        SCORE_BELOW,
        SCORE_ABOVE,
        NUDGE_STEP,
        MOVE_X,
        MOVE_Y,
        MOVE_Z;

        public boolean angle() {
            return this == HEADING || this == PITCH || this == ROLL;
        }

        public boolean size() {
            return this == WIDTH || this == HEIGHT;
        }

        public boolean signal() {
            return this == SIGNAL || this == PULSE || this == RACE_ONLY;
        }

        public boolean toggle() {
            return this == SIGNAL || this == RACE_ONLY || this == OPEN_EDGES;
        }

        public boolean score() {
            return this == SCORE_ROLE || this == SCORE_SIDE || this == SCORE_REACH || this == SCORE_BELOW || this == SCORE_ABOVE;
        }

        public boolean position() {
            return this == NUDGE_STEP || this == MOVE_X || this == MOVE_Y || this == MOVE_Z;
        }

        public boolean move() {
            return this == MOVE_X || this == MOVE_Y || this == MOVE_Z;
        }

        public boolean choice() {
            return this == VARIANT || this == SCORE_ROLE || this == SCORE_SIDE || this == NUDGE_STEP;
        }

        public int axis() {
            return switch (this) {
                case MOVE_Y -> AXIS_Y;
                case MOVE_Z -> AXIS_Z;
                default -> AXIS_X;
            };
        }
    }

    public enum Section {
        ROTATION,
        SIZE,
        SIGNAL,
        SCORE,
        POSITION
    }

    public enum Page {
        POSE,
        SIGNAL,
        SCORE,
        POSITION
    }

    public enum Nudge {
        AWAY,
        TOWARD,
        LEFT,
        RIGHT,
        UP,
        DOWN
    }

    public static int[] viewNudge(float yawDegrees, Nudge nudge) {
        double r = Math.toRadians(yawDegrees);
        double fx = -Math.sin(r);
        double fz = Math.cos(r);
        int forwardAxis;
        int forwardSign;
        if (Math.abs(fx) > Math.abs(fz)) {
            forwardAxis = AXIS_X;
            forwardSign = fx > 0.0 ? 1 : -1;
        } else {
            forwardAxis = AXIS_Z;
            forwardSign = fz > 0.0 ? 1 : -1;
        }
        int sideAxis = forwardAxis == AXIS_X ? AXIS_Z : AXIS_X;
        int rightSign = forwardAxis == AXIS_X ? forwardSign : -forwardSign;
        return switch (nudge) {
            case AWAY -> new int[]{forwardAxis, forwardSign};
            case TOWARD -> new int[]{forwardAxis, -forwardSign};
            case RIGHT -> new int[]{sideAxis, rightSign};
            case LEFT -> new int[]{sideAxis, -rightSign};
            case UP -> new int[]{AXIS_Y, 1};
            case DOWN -> new int[]{AXIS_Y, -1};
        };
    }

    private static final List<Row> SIGNAL_PAGE = List.of(Row.SIGNAL, Row.PULSE, Row.RACE_ONLY);
    private static final List<Row> POSITION_PAGE = List.of(Row.NUDGE_STEP, Row.MOVE_X, Row.MOVE_Y, Row.MOVE_Z);
    private static final List<List<Row>> GATE_PAGES = List.of(List.of(Row.HEADING, Row.PITCH, Row.ROLL, Row.WIDTH, Row.HEIGHT), SIGNAL_PAGE,
            POSITION_PAGE);
    private static final List<List<Row>> SCORED_PAGES = List.of(List.of(Row.HEADING, Row.VARIANT, Row.WIDTH, Row.HEIGHT),
            List.of(Row.SCORE_BELOW, Row.SCORE_ABOVE), POSITION_PAGE);
    private static final List<List<Row>> CUBE_PAGES = List.of(List.of(Row.HEADING, Row.VARIANT, Row.WIDTH, Row.HEIGHT), POSITION_PAGE);
    private static final List<List<Row>> FREEFORM_PAGES = List.of(List.of(Row.VARIANT, Row.OPEN_EDGES), SIGNAL_PAGE);
    private static final List<List<Row>> LYING_FREEFORM_PAGES = List.of(List.of(Row.VARIANT, Row.HEADING, Row.OPEN_EDGES), SIGNAL_PAGE);
    private static final List<List<Row>> POLE_PAGES = List.of(List.of(Row.HEADING, Row.VARIANT, Row.HEIGHT),
            List.of(Row.SCORE_ROLE, Row.SCORE_SIDE, Row.SCORE_REACH, Row.SCORE_BELOW, Row.SCORE_ABOVE), POSITION_PAGE);

    private final CourseElement kind;
    private final boolean quarterTurns;
    private final List<List<Row>> pages;
    private final List<Row> rows;
    private final int startVariant;
    private float heading;
    private float pitch;
    private float roll;
    private double width;
    private double height;
    private int variant;
    private int selected;
    private final GateSignal startSignal;
    private GateSignal signal;
    private final ScoreArea startScore;
    private ScoreArea score;
    private boolean roleRequired;
    private double nudgeStep = DEFAULT_NUDGE_STEP;
    private double dx;
    private double dy;
    private double dz;

    public GateEditModel(CourseElement kind, float heading, float pitch, float roll, double width, double height, int variant) {
        this(kind, heading, pitch, roll, width, height, variant, GateSignal.DEFAULT);
    }

    public GateEditModel(CourseElement kind, float heading, float pitch, float roll, double width, double height, int variant,
                         GateSignal signal) {
        this(kind, heading, pitch, roll, width, height, variant, signal, ScoreArea.DEFAULT);
    }

    public GateEditModel(CourseElement kind, float heading, float pitch, float roll, double width, double height, int variant,
                         GateSignal signal, ScoreArea score) {
        this(kind, false, heading, pitch, roll, width, height, variant, signal, score);
    }

    public GateEditModel(CourseElement kind, boolean lying, float heading, float pitch, float roll, double width, double height, int variant,
                         GateSignal signal, ScoreArea score) {
        this.kind = kind;
        this.quarterTurns = lying && kind.freeform();
        this.pages = pages(kind, this.quarterTurns);
        List<Row> all = new ArrayList<>();
        for (List<Row> page : this.pages) {
            all.addAll(page);
        }
        this.rows = List.copyOf(all);
        this.startVariant = kind.variant(variant);
        this.variant = this.startVariant;
        this.startSignal = signal;
        this.signal = signal;
        this.startScore = (score == null ? ScoreArea.DEFAULT : score).normalized(kind);
        this.score = this.startScore;
        this.set(heading, pitch, roll, width, height);
    }

    public static List<List<Row>> pages(CourseElement kind) {
        return switch (kind) {
            case GATE -> GATE_PAGES;
            case FLAG, POLE -> SCORED_PAGES;
            case CUBE -> CUBE_PAGES;
            case FREEFORM -> FREEFORM_PAGES;
            case GATE_POLE -> POLE_PAGES;
        };
    }

    public static List<List<Row>> pages(CourseElement kind, boolean lying) {
        return lying && kind.freeform() ? LYING_FREEFORM_PAGES : pages(kind);
    }

    public static List<Row> rows(CourseElement kind) {
        return rows(kind, false);
    }

    public static List<Row> rows(CourseElement kind, boolean lying) {
        List<Row> all = new ArrayList<>();
        for (List<Row> page : pages(kind, lying)) {
            all.addAll(page);
        }
        return List.copyOf(all);
    }

    public static Section section(Row row) {
        if (row.signal()) {
            return Section.SIGNAL;
        }
        if (row.score()) {
            return Section.SCORE;
        }
        if (row.position()) {
            return Section.POSITION;
        }
        return row.size() ? Section.SIZE : Section.ROTATION;
    }

    public static Page pageKind(Row row) {
        if (row.signal()) {
            return Page.SIGNAL;
        }
        if (row.score()) {
            return Page.SCORE;
        }
        return row.position() ? Page.POSITION : Page.POSE;
    }

    public int page(Row row) {
        for (int page = 0; page < this.pages.size(); page++) {
            if (this.pages.get(page).contains(row)) {
                return page;
            }
        }
        return 0;
    }

    public Page pageKind(int page) {
        List<Row> rows = this.rows(page);
        return rows.isEmpty() ? Page.POSE : pageKind(rows.getFirst());
    }

    public int page() {
        return this.page(this.selected());
    }

    public int pages() {
        return this.pages.size();
    }

    public List<Row> rows(int page) {
        return page >= 0 && page < this.pages.size() ? this.pages.get(page) : List.of();
    }

    public void showPage(int page) {
        List<Row> rows = this.rows(Math.floorMod(page, this.pages()));
        if (!rows.isEmpty()) {
            this.select(rows.getFirst());
        }
    }

    public void turnPage(int delta) {
        this.showPage(this.page() + delta);
    }

    public int pageOf(Page kind) {
        for (int page = 0; page < this.pages.size(); page++) {
            if (this.pageKind(page) == kind) {
                return page;
            }
        }
        return -1;
    }

    public GateSignal signal() {
        return this.signal;
    }

    public GateSignal startSignal() {
        return this.startSignal;
    }

    public boolean signalChanged() {
        return !this.signal.equals(this.startSignal);
    }

    public void setSignal(GateSignal signal) {
        this.signal = signal;
    }

    public CourseElement kind() {
        return this.kind;
    }

    public boolean quarterTurns() {
        return this.quarterTurns;
    }

    public List<Row> rows() {
        return this.rows;
    }

    public float heading() {
        return this.heading;
    }

    public float pitch() {
        return this.pitch;
    }

    public float roll() {
        return this.roll;
    }

    public double width() {
        return this.width;
    }

    public double height() {
        return this.height;
    }

    public int variant() {
        return this.variant;
    }

    public int startVariant() {
        return this.startVariant;
    }

    public double value(Row row) {
        return switch (row) {
            case HEADING -> this.heading;
            case PITCH -> this.pitch;
            case ROLL -> this.roll;
            case VARIANT -> this.variant;
            case WIDTH -> this.width;
            case HEIGHT -> this.height;
            case SIGNAL -> this.signal.enabled() ? 1.0 : 0.0;
            case PULSE -> this.signal.pulseTicks();
            case RACE_ONLY -> this.signal.raceOnly() ? 1.0 : 0.0;
            case OPEN_EDGES -> this.score.open() ? 1.0 : 0.0;
            case SCORE_ROLE -> this.score.role().ordinal();
            case SCORE_SIDE -> this.score.side();
            case SCORE_REACH -> this.score.reach();
            case SCORE_BELOW -> this.score.below();
            case SCORE_ABOVE -> this.score.above();
            case NUDGE_STEP -> this.nudgeStep;
            case MOVE_X -> this.dx;
            case MOVE_Y -> this.dy;
            case MOVE_Z -> this.dz;
        };
    }

    public ScoreArea score() {
        return this.score;
    }

    public ScoreArea startScore() {
        return this.startScore;
    }

    public void setScore(ScoreArea next) {
        ScoreArea value = (next == null ? ScoreArea.DEFAULT : next).normalized(this.kind);
        if (this.roleRequired && !value.scoring()) {
            value = value.withRole(ScoreArea.Role.PASS);
        }
        this.score = value;
    }

    public boolean roleRequired() {
        return this.roleRequired;
    }

    public void setRoleRequired(boolean required) {
        this.roleRequired = required;
        this.setScore(this.score);
    }

    public List<ScoreArea.Role> roles() {
        return this.roleRequired ? List.of(ScoreArea.Role.PASS, ScoreArea.Role.TURN) : List.of(ScoreArea.Role.values());
    }

    public double nudgeStep() {
        return this.nudgeStep;
    }

    public void setNudgeStep(double step) {
        double best = NUDGE_STEPS.getFirst();
        for (double candidate : NUDGE_STEPS) {
            if (Math.abs(candidate - step) < Math.abs(best - step)) {
                best = candidate;
            }
        }
        this.nudgeStep = best;
    }

    public double dx() {
        return this.dx;
    }

    public double dy() {
        return this.dy;
    }

    public double dz() {
        return this.dz;
    }

    public boolean moved() {
        return this.dx != 0.0 || this.dy != 0.0 || this.dz != 0.0;
    }

    public void setOffset(int axis, double value) {
        double clean = Double.isFinite(value) ? Math.round(value / HALF_NUDGE) * HALF_NUDGE : 0.0;
        double clamped = Math.clamp(clean, -GateMounting.MAX_NUDGE, GateMounting.MAX_NUDGE) + 0.0;
        switch (axis) {
            case AXIS_Y -> this.dy = clamped;
            case AXIS_Z -> this.dz = clamped;
            default -> this.dx = clamped;
        }
    }

    public double offset(int axis) {
        return switch (axis) {
            case AXIS_Y -> this.dy;
            case AXIS_Z -> this.dz;
            default -> this.dx;
        };
    }

    public double nudgeStep(boolean fine, boolean half) {
        if (!fine) {
            return this.nudgeStep;
        }
        return half ? HALF_NUDGE : FINE_NUDGE;
    }

    public void nudge(int axis, int steps, boolean fine) {
        this.nudge(axis, steps, fine, false);
    }

    public void nudge(int axis, int steps, boolean fine, boolean half) {
        if (steps == 0) {
            return;
        }
        this.setOffset(axis, onGrid(this.offset(axis), this.nudgeStep(fine, half), steps));
    }

    public void resetOffset() {
        this.dx = 0.0;
        this.dy = 0.0;
        this.dz = 0.0;
    }

    public void resetScore() {
        this.setScore(ScoreArea.DEFAULT.withOpen(this.score.open()).withRole(this.roleRequired ? this.score.role() : ScoreArea.Role.NONE));
    }

    public boolean scoreChanged() {
        return !this.score.equals(this.startScore);
    }

    public void set(float heading, float pitch, float roll, double width, double height) {
        if (!this.kind.gate()) {
            pitch = 0.0F;
            roll = 0.0F;
        }
        this.heading = this.quarterTurns ? quarter(heading) : wrapHeading(heading);
        this.pitch = Math.clamp(Float.isFinite(pitch) ? pitch : 0.0F, -GateTilt.MAX_PITCH, GateTilt.MAX_PITCH);
        this.roll = GateTilt.wrap(roll);
        this.width = GateSize.snapOpening(width);
        this.height = GateSize.snapOpening(height);
    }

    public void set(Row row, double value) {
        switch (row) {
            case HEADING -> this.set((float) value, this.pitch, this.roll, this.width, this.height);
            case PITCH -> this.set(this.heading, (float) value, this.roll, this.width, this.height);
            case ROLL -> this.set(this.heading, this.pitch, (float) value, this.width, this.height);
            case VARIANT -> this.setVariant((int) Math.round(value));
            case WIDTH -> this.set(this.heading, this.pitch, this.roll, value, this.height);
            case HEIGHT -> this.set(this.heading, this.pitch, this.roll, this.width, value);
            case SIGNAL -> this.signal = this.signal.withEnabled(value >= 0.5);
            case PULSE -> this.signal = this.signal.withPulse((int) Math.round(Double.isFinite(value) ? value : GateSignal.DEFAULT_PULSE));
            case RACE_ONLY -> this.signal = this.signal.withRaceOnly(value >= 0.5);
            case OPEN_EDGES -> this.setScore(this.score.withOpen(value >= 0.5));
            case SCORE_ROLE -> this.setScore(this.score.withRole(ScoreArea.Role.byOrdinal((int) Math.round(Double.isFinite(value) ? value : 0.0))));
            case SCORE_SIDE -> this.setScore(this.score.withSide(value >= 0.5 ? CourseElement.RIGHT : CourseElement.LEFT));
            case SCORE_REACH -> this.setScore(this.score.withReach(value));
            case SCORE_BELOW -> this.setScore(this.score.withBelow(value));
            case SCORE_ABOVE -> this.setScore(this.score.withAbove(value));
            case NUDGE_STEP -> this.setNudgeStep(value);
            case MOVE_X, MOVE_Y, MOVE_Z -> this.setOffset(row.axis(), value);
        }
    }

    public void setVariant(int value) {
        this.variant = this.kind.variant(value);
    }

    public Row selected() {
        return this.rows.get(this.selected);
    }

    public int selectedIndex() {
        return this.selected;
    }

    public void select(Row row) {
        int index = this.rows.indexOf(row);
        if (index >= 0) {
            this.selected = index;
        }
    }

    public void moveSelection(int delta) {
        this.selected = Math.floorMod(this.selected + delta, this.rows.size());
    }

    public void scroll(int notches, boolean fine) {
        this.change(this.selected(), notches, fine);
    }

    public void change(Row row, int notches, boolean fine) {
        this.change(row, notches, fine, false);
    }

    public void change(Row row, int notches, boolean fine, boolean half) {
        if (notches == 0 || !this.rows.contains(row)) {
            return;
        }
        if (row == Row.HEADING && this.quarterTurns) {
            this.set(row, this.heading + QUARTER_TURN * notches);
            return;
        }
        if (row == Row.VARIANT) {
            this.setVariant(this.kind.freeform() ? (Math.floorMod(notches, 2) == 1 ? this.kind.opposite(this.variant) : this.variant)
                    : this.variant + notches);
            return;
        }
        if (row.toggle()) {
            if (Math.floorMod(notches, 2) == 1) {
                this.set(row, this.value(row) >= 0.5 ? 0.0 : 1.0);
            }
            return;
        }
        if (row == Row.SCORE_SIDE) {
            if (Math.floorMod(notches, 2) == 1) {
                this.set(row, this.score.side() == CourseElement.LEFT ? 1.0 : 0.0);
            }
            return;
        }
        if (row == Row.SCORE_ROLE) {
            List<ScoreArea.Role> roles = this.roles();
            int at = Math.max(0, roles.indexOf(this.score.role()));
            this.setScore(this.score.withRole(roles.get(Math.floorMod(at + notches, roles.size()))));
            return;
        }
        if (row == Row.NUDGE_STEP) {
            int at = Math.max(0, NUDGE_STEPS.indexOf(this.nudgeStep));
            this.nudgeStep = NUDGE_STEPS.get(Math.clamp(at + notches, 0, NUDGE_STEPS.size() - 1));
            return;
        }
        if (row.move()) {
            this.nudge(row.axis(), notches, fine, half);
            return;
        }
        if (row.score()) {
            this.set(row, onGrid(this.value(row), ScoreArea.STEP, notches));
            return;
        }
        if (row == Row.PULSE) {
            this.set(row, onGrid(this.value(row), fine ? FINE_PULSE_STEP : PULSE_STEP, notches));
            return;
        }
        double step = row.angle() ? (fine ? FINE_DEGREES : WHEEL_DEGREES) : GateSize.OPENING_STEP;
        this.set(row, onGrid(this.value(row), step, notches));
    }

    public void turn(float degrees) {
        this.set(this.heading + degrees, this.pitch, this.roll, this.width, this.height);
    }

    public void reset(float heading, double width, double height) {
        this.set(heading, 0.0F, 0.0F, width, height);
    }

    public static double onGrid(double value, double step, int notches) {
        double cells = value / step;
        double base = notches > 0 ? Math.floor(cells + GRID_EPSILON) : Math.ceil(cells - GRID_EPSILON);
        return (base + notches) * step;
    }

    public static float quarter(float value) {
        float wrapped = wrapHeading(value);
        float snapped = Math.round(wrapped / QUARTER_TURN) * QUARTER_TURN;
        return snapped >= 360.0F ? 0.0F : snapped;
    }

    public static float wrapHeading(float value) {
        if (!Float.isFinite(value)) {
            return 0.0F;
        }
        float wrapped = value % 360.0F;
        float positive = wrapped < 0.0F ? wrapped + 360.0F : wrapped + 0.0F;
        return positive >= 360.0F ? 0.0F : positive;
    }
}
