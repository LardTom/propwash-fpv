package de.lardtom.propwash.api.part;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public record AccessoryDefinition(Identifier id, PartBasics basics, AccessorySlot slot, double widthMm, double heightMm, double depthMm,
                                  double flightBatteryWatts, double frontalAreaM2, int ledCount, Optional<ActionCamDefinition> actionCam,
                                  double lensXMm, double lensLengthMm, List<Vec3Mm> ledPathMm, boolean hangsStraight,
                                  Optional<Vec3Mm> muzzleMm, Set<Identifier> frames, double recessMm, double dropMm) implements PartDefinition {
    public static final int MAX_LEDS = 64;
    public static final int MIN_LED_PATH_POINTS = 2;
    public static final int MAX_LED_PATH_POINTS = 256;
    public static final double MAX_LED_PATH_MM = 500.0;
    public static final double MAX_MUZZLE_OUTSIDE_MM = 20.0;

    public AccessoryDefinition(Identifier id, PartBasics basics, AccessorySlot slot, double widthMm, double heightMm, double depthMm,
                               double flightBatteryWatts, double frontalAreaM2, int ledCount, Optional<ActionCamDefinition> actionCam,
                               double lensXMm, double lensLengthMm) {
        this(id, basics, slot, widthMm, heightMm, depthMm, flightBatteryWatts, frontalAreaM2, ledCount, actionCam, lensXMm,
                lensLengthMm, List.of(), false);
    }

    public AccessoryDefinition(Identifier id, PartBasics basics, AccessorySlot slot, double widthMm, double heightMm, double depthMm,
                               double flightBatteryWatts, double frontalAreaM2, int ledCount, Optional<ActionCamDefinition> actionCam,
                               double lensXMm, double lensLengthMm, List<Vec3Mm> ledPathMm) {
        this(id, basics, slot, widthMm, heightMm, depthMm, flightBatteryWatts, frontalAreaM2, ledCount, actionCam, lensXMm,
                lensLengthMm, ledPathMm, false);
    }

    public AccessoryDefinition(Identifier id, PartBasics basics, AccessorySlot slot, double widthMm, double heightMm, double depthMm,
                               double flightBatteryWatts, double frontalAreaM2, int ledCount, Optional<ActionCamDefinition> actionCam,
                               double lensXMm, double lensLengthMm, List<Vec3Mm> ledPathMm, boolean hangsStraight) {
        this(id, basics, slot, widthMm, heightMm, depthMm, flightBatteryWatts, frontalAreaM2, ledCount, actionCam, lensXMm,
                lensLengthMm, ledPathMm, hangsStraight, Optional.empty());
    }

    public AccessoryDefinition(Identifier id, PartBasics basics, AccessorySlot slot, double widthMm, double heightMm, double depthMm,
                               double flightBatteryWatts, double frontalAreaM2, int ledCount, Optional<ActionCamDefinition> actionCam,
                               double lensXMm, double lensLengthMm, List<Vec3Mm> ledPathMm, boolean hangsStraight, Optional<Vec3Mm> muzzleMm) {
        this(id, basics, slot, widthMm, heightMm, depthMm, flightBatteryWatts, frontalAreaM2, ledCount, actionCam, lensXMm,
                lensLengthMm, ledPathMm, hangsStraight, muzzleMm, Set.of());
    }

    public AccessoryDefinition(Identifier id, PartBasics basics, AccessorySlot slot, double widthMm, double heightMm, double depthMm,
                               double flightBatteryWatts, double frontalAreaM2, int ledCount, Optional<ActionCamDefinition> actionCam,
                               double lensXMm, double lensLengthMm, List<Vec3Mm> ledPathMm, boolean hangsStraight, Optional<Vec3Mm> muzzleMm,
                               Set<Identifier> frames) {
        this(id, basics, slot, widthMm, heightMm, depthMm, flightBatteryWatts, frontalAreaM2, ledCount, actionCam, lensXMm,
                lensLengthMm, ledPathMm, hangsStraight, muzzleMm, frames, 0.0, 0.0);
    }

    public AccessoryDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(basics, "basics");
        Objects.requireNonNull(slot, "slot");
        actionCam = Objects.requireNonNullElse(actionCam, Optional.empty());
        ledPathMm = checkedLedPath(id, ledPathMm, ledCount);
        if (!(widthMm > 0.0 && widthMm <= 500.0 && heightMm > 0.0 && heightMm <= 500.0 && depthMm > 0.0 && depthMm <= 500.0)) {
            throw new IllegalArgumentException(id + ": Zubehörmaße ungültig");
        }
        if (!(recessMm >= 0.0 && recessMm <= depthMm)) {
            throw new IllegalArgumentException(id + ": recess_mm muss zwischen 0 und der Tiefe " + depthMm + " mm liegen: " + recessMm);
        }
        if (!(dropMm >= 0.0 && dropMm <= heightMm)) {
            throw new IllegalArgumentException(id + ": drop_mm muss zwischen 0 und der Höhe " + heightMm + " mm liegen: " + dropMm);
        }
        if (!(flightBatteryWatts >= 0.0 && flightBatteryWatts <= 200.0)) {
            throw new IllegalArgumentException(id + ": Leistungsaufnahme ungültig: " + flightBatteryWatts);
        }
        if (!(frontalAreaM2 >= 0.0 && frontalAreaM2 <= 0.5)) {
            throw new IllegalArgumentException(id + ": Stirnfläche ungültig: " + frontalAreaM2);
        }
        if (ledCount < 0 || ledCount > MAX_LEDS) {
            throw new IllegalArgumentException(id + ": LED-Anzahl muss zwischen 0 und " + MAX_LEDS + " liegen: " + ledCount);
        }
        if (actionCam.isPresent() && slot != AccessorySlot.TOP && slot != AccessorySlot.FRONT) {
            throw new IllegalArgumentException(id + ": Action-Cams sitzen oben oder vorn");
        }
        if (!Double.isFinite(lensXMm) || !(lensLengthMm >= 0.0 && lensLengthMm <= 100.0)) {
            throw new IllegalArgumentException(id + ": Objektivlage ungültig");
        }
        muzzleMm = Objects.requireNonNullElse(muzzleMm, Optional.empty());
        muzzleMm.ifPresent(point -> checkMuzzle(id, point, widthMm, heightMm, depthMm));
        frames = checkedFrames(id, frames);
    }

    private static Set<Identifier> checkedFrames(Identifier id, @Nullable Collection<Identifier> frames) {
        if (frames == null || frames.isEmpty()) {
            return Set.of();
        }
        TreeSet<Identifier> sorted = new TreeSet<>();
        for (Identifier frame : frames) {
            if (frame == null) {
                throw new IllegalArgumentException(id + ": frames enthält einen leeren Eintrag");
            }
            sorted.add(frame);
        }
        return Collections.unmodifiableSet(sorted);
    }

    private static void checkMuzzle(Identifier id, Vec3Mm point, double widthMm, double heightMm, double depthMm) {
        double margin = MAX_MUZZLE_OUTSIDE_MM;
        if (Math.abs(point.x()) > widthMm / 2.0 + margin || Math.abs(point.y()) > heightMm / 2.0 + margin || point.z() < -margin
                || point.z() > depthMm + margin) {
            throw new IllegalArgumentException(id + ": muzzle_mm [" + point.x() + ", " + point.y() + ", " + point.z()
                    + "] liegt mehr als " + (int) margin + " mm außerhalb der Box (Versatz von der Mitte der Vorderfläche: x rechts, y oben, −z vorn)");
        }
    }

    private static List<Vec3Mm> checkedLedPath(Identifier id, @Nullable List<Vec3Mm> path, int ledCount) {
        if (path == null || path.isEmpty()) {
            return List.of();
        }
        if (path.size() < MIN_LED_PATH_POINTS || path.size() > MAX_LED_PATH_POINTS) {
            throw new IllegalArgumentException(id + ": led_path braucht " + MIN_LED_PATH_POINTS + " bis " + MAX_LED_PATH_POINTS
                    + " Punkte, hat " + path.size());
        }
        if (ledCount <= 0) {
            throw new IllegalArgumentException(id + ": led_path ohne LEDs (leds ist 0)");
        }
        double length = 0.0;
        for (int i = 0; i < path.size(); i++) {
            Vec3Mm point = path.get(i);
            if (point == null) {
                throw new IllegalArgumentException(id + ": led_path, Punkt " + (i + 1) + " fehlt");
            }
            if (Math.abs(point.x()) > MAX_LED_PATH_MM || Math.abs(point.y()) > MAX_LED_PATH_MM || Math.abs(point.z()) > MAX_LED_PATH_MM) {
                throw new IllegalArgumentException(id + ": led_path, Punkt " + (i + 1) + " liegt außerhalb von ±" + (int) MAX_LED_PATH_MM
                        + " mm: [" + point.x() + ", " + point.y() + ", " + point.z() + "]");
            }
            if (i > 0) {
                Vec3Mm previous = path.get(i - 1);
                length += Math.sqrt(square(point.x() - previous.x()) + square(point.y() - previous.y()) + square(point.z() - previous.z()));
            }
        }
        if (!(length > 0.0)) {
            throw new IllegalArgumentException(id + ": led_path hat die Länge 0 (alle Punkte gleich)");
        }
        return List.copyOf(path);
    }

    private static double square(double value) {
        return value * value;
    }

    @Override
    public PartKind kind() {
        return PartKind.ACCESSORY;
    }

    public static Builder builder(Identifier id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final Identifier id;
        private PartBasics basics;
        private AccessorySlot slot;
        private double widthMm = -1.0;
        private double heightMm = -1.0;
        private double depthMm = -1.0;
        private double flightBatteryWatts;
        private double frontalAreaM2;
        private int ledCount;
        private Optional<ActionCamDefinition> actionCam = Optional.empty();
        private double lensXMm;
        private double lensLengthMm;
        private List<Vec3Mm> ledPathMm = List.of();
        private boolean hangsStraight;
        private Optional<Vec3Mm> muzzleMm = Optional.empty();
        private Set<Identifier> frames = Set.of();
        private double recessMm;
        private double dropMm;

        private Builder(Identifier id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public Builder basics(PartBasics value) {
            this.basics = value;
            return this;
        }

        public Builder mass(double grams) {
            this.basics = PartBasics.withMass(this.basics, grams);
            return this;
        }

        public Builder slot(AccessorySlot value) {
            this.slot = value;
            return this;
        }

        public Builder size(double widthMm, double heightMm, double depthMm) {
            this.widthMm = widthMm;
            this.heightMm = heightMm;
            this.depthMm = depthMm;
            return this;
        }

        public Builder flightBatteryWatts(double value) {
            this.flightBatteryWatts = value;
            return this;
        }

        public Builder frontalArea(double m2) {
            this.frontalAreaM2 = m2;
            return this;
        }

        public Builder leds(int count) {
            this.ledCount = count;
            return this;
        }

        public Builder ledPath(List<Vec3Mm> pointsMm) {
            this.ledPathMm = pointsMm;
            return this;
        }

        public Builder ledPath(Vec3Mm... pointsMm) {
            return this.ledPath(Arrays.asList(pointsMm));
        }

        public Builder hangsStraight(boolean value) {
            this.hangsStraight = value;
            return this;
        }

        public Builder muzzle(Vec3Mm offsetMm) {
            this.muzzleMm = Optional.ofNullable(offsetMm);
            return this;
        }

        public Builder muzzle(double xMm, double yMm, double zMm) {
            return this.muzzle(new Vec3Mm(xMm, yMm, zMm));
        }

        public Builder frames(Identifier... frameIds) {
            this.frames = frameIds == null ? Set.of() : new LinkedHashSet<>(Arrays.asList(frameIds));
            return this;
        }

        public Builder recess(double mm) {
            this.recessMm = mm;
            return this;
        }

        public Builder drop(double mm) {
            this.dropMm = mm;
            return this;
        }

        public Builder actionCam(ActionCamDefinition value) {
            this.actionCam = Optional.of(value);
            return this;
        }

        public Builder lens(double xMm, double lengthMm) {
            this.lensXMm = xMm;
            this.lensLengthMm = lengthMm;
            return this;
        }

        public AccessoryDefinition build() {
            if (this.basics == null || this.slot == null || this.widthMm < 0.0) {
                throw new IllegalArgumentException(this.id + ": Masse, Halterung und Maße sind Pflicht");
            }
            return new AccessoryDefinition(this.id, this.basics, this.slot, this.widthMm, this.heightMm, this.depthMm,
                    this.flightBatteryWatts, this.frontalAreaM2, this.ledCount, this.actionCam, this.lensXMm, this.lensLengthMm,
                    this.ledPathMm, this.hangsStraight, this.muzzleMm, this.frames, this.recessMm, this.dropMm);
        }
    }
}
