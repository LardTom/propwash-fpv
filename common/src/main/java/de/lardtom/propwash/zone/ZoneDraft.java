package de.lardtom.propwash.zone;

import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleSpec;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ZoneDraft {
    public static final double MOVE_STEP = 1.0;
    public static final double FINE_MOVE_STEP = 0.5;
    public static final double SIZE_STEP = 2.0;
    public static final double FINE_SIZE_STEP = 1.0;
    public static final int COARSE_PERCENT = 10;
    public static final int NEW_SIZE = 16;
    public static final int NEW_HEIGHT = 8;

    public enum Kind {
        SHAPE,
        X,
        Y,
        Z,
        WIDTH,
        HEIGHT,
        DEPTH,
        PRIORITY,
        RACE_LEGAL,
        TRANSITION,
        RULE;

        public boolean geometry() {
            return this.ordinal() <= DEPTH.ordinal();
        }
    }

    public record Row(Kind kind, @Nullable RuleSpec spec) {
        public static Row of(Kind kind) {
            return new Row(kind, null);
        }

        public String key() {
            return this.spec == null ? this.kind.name().toLowerCase(Locale.ROOT) : this.spec.name();
        }
    }

    private final Zone original;
    private Zone zone;
    private Row selected;

    public ZoneDraft(Zone zone) {
        this.original = zone;
        this.zone = zone;
        this.selected = this.rows().getFirst();
    }

    public static Zone fresh(String dimension, String name, double x, double y, double z) {
        double minX = Math.floor(x) - NEW_SIZE / 2.0;
        double minZ = Math.floor(z) - NEW_SIZE / 2.0;
        double minY = Math.floor(y);
        return Zone.create(dimension, name, ZoneShape.BOX, new ZoneBox(minX, minY, minZ, minX + NEW_SIZE, minY + NEW_HEIGHT, minZ + NEW_SIZE));
    }

    public Zone original() {
        return this.original;
    }

    public Zone zone() {
        return this.zone;
    }

    public boolean isNew() {
        return this.original.id() == Zone.NEW;
    }

    public boolean changed() {
        return this.isNew() || !this.zone.equals(this.original);
    }

    public List<Row> rows() {
        List<Row> out = new ArrayList<>();
        out.add(Row.of(Kind.SHAPE));
        out.add(Row.of(Kind.X));
        out.add(Row.of(Kind.Y));
        out.add(Row.of(Kind.Z));
        out.add(Row.of(Kind.WIDTH));
        out.add(Row.of(Kind.HEIGHT));
        if (this.zone.shape() == ZoneShape.BOX) {
            out.add(Row.of(Kind.DEPTH));
        }
        out.add(Row.of(Kind.PRIORITY));
        out.add(Row.of(Kind.RACE_LEGAL));
        out.add(Row.of(Kind.TRANSITION));
        for (RuleSpec spec : PhysicsRules.SPECS) {
            out.add(new Row(Kind.RULE, spec));
        }
        return out;
    }

    public Row selected() {
        List<Row> rows = this.rows();
        return rows.contains(this.selected) ? this.selected : rows.getFirst();
    }

    public void select(Row row) {
        if (this.rows().contains(row)) {
            this.selected = row;
        }
    }

    public void moveSelection(int delta) {
        List<Row> rows = this.rows();
        int index = rows.indexOf(this.selected());
        this.selected = rows.get(Math.floorMod(index + delta, rows.size()));
    }

    public void setName(String name) {
        this.zone = this.zone.withName(name);
    }

    public void set(Zone next) {
        this.zone = next;
    }

    public void centerOn(double x, double y, double z) {
        ZoneBox box = this.zone.box();
        double minX = Math.round(x - box.sizeX() / 2.0);
        double minZ = Math.round(z - box.sizeZ() / 2.0);
        this.zone = this.zone.withBox(new ZoneBox(minX, Math.floor(y), minZ, minX + box.sizeX(), Math.floor(y) + box.sizeY(), minZ + box.sizeZ()));
    }

    public void change(Row row, int notches, boolean fine, PhysicsRules world) {
        if (notches == 0) {
            return;
        }
        this.select(row);
        ZoneBox box = this.zone.box();
        double move = (fine ? FINE_MOVE_STEP : MOVE_STEP) * notches;
        double grow = (fine ? FINE_SIZE_STEP : SIZE_STEP) * notches;
        this.zone = switch (row.kind()) {
            case SHAPE -> this.zone.withShape(this.zone.shape() == ZoneShape.BOX ? ZoneShape.CYLINDER : ZoneShape.BOX);
            case X -> this.zone.withBox(box.moved(move, 0.0, 0.0));
            case Y -> this.zone.withBox(box.moved(0.0, move, 0.0));
            case Z -> this.zone.withBox(box.moved(0.0, 0.0, move));
            case WIDTH -> this.zone.withBox(ZoneBox.around(box.centerX(), box.minY(), box.centerZ(), size(box.sizeX() + grow), box.sizeY(),
                    this.zone.shape() == ZoneShape.CYLINDER ? size(box.sizeZ() + grow) : box.sizeZ()));
            case HEIGHT -> this.zone.withBox(box.sized(box.sizeX(), size(box.sizeY() + grow), box.sizeZ()));
            case DEPTH -> this.zone.withBox(ZoneBox.around(box.centerX(), box.minY(), box.centerZ(), box.sizeX(), box.sizeY(), size(box.sizeZ() + grow)));
            case PRIORITY -> this.zone.withPriority(this.zone.priority() + notches);
            case RACE_LEGAL -> this.zone.withRaceLegal(!this.zone.raceLegal());
            case TRANSITION -> this.zone.withTransition(this.zone.transitionTenths() + notches);
            case RULE -> rule(this.zone, row.spec(), notches, fine, world);
        };
    }

    private static double size(double value) {
        return Math.max(ZoneLimits.MIN_SIZE, Math.min(ZoneLimits.MAX_SIZE, value));
    }

    static Zone rule(Zone zone, @Nullable RuleSpec spec, int notches, boolean fine, PhysicsRules world) {
        if (spec == null) {
            return zone;
        }
        if (spec.kind() == RuleSpec.Kind.BOOL) {
            int state = zone.overrides(spec) ? 1 + (zone.value(spec, 0) != 0 ? 0 : 1) : 0;
            int next = Math.floorMod(state + notches, 3);
            return next == 0 ? zone.without(spec) : zone.withOverride(spec, next == 1 ? 1 : 0);
        }
        int step = spec.display() == RuleSpec.Display.PERCENT && !fine ? Math.max(spec.step(), COARSE_PERCENT) : spec.step();
        int current = zone.overrides(spec) ? zone.value(spec, spec.defaultValue()) : world.value(spec);
        int next = spec.clamp(current + step * notches);
        if (!zone.overrides(spec) && next == current) {
            next = spec.clamp(current - step * notches);
        }
        return zone.withOverride(spec, next);
    }

    public void reset(Row row) {
        this.select(row);
        if (row.kind() == Kind.RULE && row.spec() != null) {
            this.zone = this.zone.without(row.spec());
        } else if (row.kind() == Kind.PRIORITY) {
            this.zone = this.zone.withPriority(0);
        } else if (row.kind() == Kind.TRANSITION) {
            this.zone = this.zone.withTransition(ZoneLimits.DEFAULT_TRANSITION_TENTHS);
        } else if (row.kind() == Kind.RACE_LEGAL) {
            this.zone = this.zone.withRaceLegal(false);
        } else if (row.kind().geometry()) {
            this.zone = this.zone.withShape(this.original.shape()).withBox(this.original.box());
        }
    }
}
