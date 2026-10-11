package de.lardtom.propwash.race.client;

import de.lardtom.propwash.race.CourseElement;
import de.lardtom.propwash.race.GateSignal;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.GateTilt;

import java.util.List;

public final class GateEditModel {
    public static final float WHEEL_DEGREES = 5.0F;
    public static final float FINE_DEGREES = 1.0F;
    public static final int PULSE_STEP = 2;
    public static final int FINE_PULSE_STEP = 1;
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
        RACE_ONLY;

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
            return this == SIGNAL || this == RACE_ONLY;
        }
    }

    public enum Section {
        ROTATION,
        SIZE,
        SIGNAL
    }

    private static final List<Row> GATE_ROWS = List.of(Row.HEADING, Row.PITCH, Row.ROLL, Row.WIDTH, Row.HEIGHT, Row.SIGNAL, Row.PULSE,
            Row.RACE_ONLY);
    private static final List<Row> ELEMENT_ROWS = List.of(Row.HEADING, Row.VARIANT, Row.WIDTH, Row.HEIGHT);
    private static final List<Row> FREEFORM_ROWS = List.of(Row.VARIANT, Row.SIGNAL, Row.PULSE, Row.RACE_ONLY);
    private static final List<Row> POLE_ROWS = List.of(Row.HEADING, Row.VARIANT, Row.HEIGHT);

    private final CourseElement kind;
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

    public GateEditModel(CourseElement kind, float heading, float pitch, float roll, double width, double height, int variant) {
        this(kind, heading, pitch, roll, width, height, variant, GateSignal.DEFAULT);
    }

    public GateEditModel(CourseElement kind, float heading, float pitch, float roll, double width, double height, int variant,
                         GateSignal signal) {
        this.kind = kind;
        this.rows = rows(kind);
        this.startVariant = kind.variant(variant);
        this.variant = this.startVariant;
        this.startSignal = signal;
        this.signal = signal;
        this.set(heading, pitch, roll, width, height);
    }

    public static List<Row> rows(CourseElement kind) {
        if (kind.freeform()) {
            return FREEFORM_ROWS;
        }
        if (kind == CourseElement.GATE_POLE) {
            return POLE_ROWS;
        }
        return kind.gate() ? GATE_ROWS : ELEMENT_ROWS;
    }

    public static Section section(Row row) {
        if (row.signal()) {
            return Section.SIGNAL;
        }
        return row.size() ? Section.SIZE : Section.ROTATION;
    }

    public static int page(Row row) {
        return row.signal() ? 1 : 0;
    }

    public int page() {
        return page(this.selected());
    }

    public int pages() {
        int pages = 0;
        for (Row row : this.rows) {
            pages = Math.max(pages, page(row) + 1);
        }
        return pages;
    }

    public List<Row> rows(int page) {
        return this.rows.stream().filter(row -> page(row) == page).toList();
    }

    public void showPage(int page) {
        List<Row> rows = this.rows(Math.floorMod(page, this.pages()));
        if (!rows.isEmpty()) {
            this.select(rows.getFirst());
        }
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
        };
    }

    public void set(float heading, float pitch, float roll, double width, double height) {
        if (!this.kind.gate()) {
            pitch = 0.0F;
            roll = 0.0F;
        }
        this.heading = wrapHeading(heading);
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
        if (notches == 0 || !this.rows.contains(row)) {
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

    public static float wrapHeading(float value) {
        if (!Float.isFinite(value)) {
            return 0.0F;
        }
        float wrapped = value % 360.0F;
        float positive = wrapped < 0.0F ? wrapped + 360.0F : wrapped + 0.0F;
        return positive >= 360.0F ? 0.0F : positive;
    }
}
