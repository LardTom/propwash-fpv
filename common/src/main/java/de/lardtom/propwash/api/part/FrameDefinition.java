package de.lardtom.propwash.api.part;

import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

public record FrameDefinition(Identifier id, PartBasics basics, FrameRole role, FrameShape shape, int wheelbaseMm, int minPropMm,
                              int maxPropMm, Set<Identifier> motorMounts, Set<Identifier> stackMounts,
                              Set<AccessorySlot> accessoryMounts, AirframeGeometry geometry, FrameAero aero, Optional<DuctSpec> duct,
                              double impactTolerance, BatteryFitRule batteryFit) implements PartDefinition {
    public static final int MAX_PROP_MM = 400;

    public FrameDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(basics, "basics");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(aero, "aero");
        Objects.requireNonNull(batteryFit, "batteryFit");
        duct = Objects.requireNonNullElse(duct, Optional.empty());
        if (wheelbaseMm < 20 || wheelbaseMm > 2000) {
            throw new IllegalArgumentException(id + ": Radstand muss zwischen 20 und 2000 mm liegen: " + wheelbaseMm);
        }
        if (minPropMm < 0 || maxPropMm < 10 || minPropMm > maxPropMm || maxPropMm > MAX_PROP_MM) {
            throw new IllegalArgumentException(id + ": Propellerbereich ungültig: " + minPropMm + "–" + maxPropMm + " mm");
        }
        motorMounts = orderedCopy(motorMounts, id + ": Motorhalterungen");
        stackMounts = orderedCopy(stackMounts, id + ": Stack-Halterungen");
        EnumSet<AccessorySlot> slots = EnumSet.noneOf(AccessorySlot.class);
        slots.addAll(Objects.requireNonNullElse(accessoryMounts, Set.of()));
        accessoryMounts = Collections.unmodifiableSet(slots);
        for (AccessorySlot slot : geometry.accessoryAnchors().keySet()) {
            if (!accessoryMounts.contains(slot)) {
                throw new IllegalArgumentException(id + ": Zubehör-Anker für " + slot.key() + " ohne passende Zubehör-Halterung");
            }
        }
        if (!(impactTolerance > 0.0 && impactTolerance <= 100.0)) {
            throw new IllegalArgumentException(id + ": Aufprall-Toleranz muss größer als 0 und höchstens 100 m/s sein: " + impactTolerance);
        }
    }

    @Override
    public PartKind kind() {
        return PartKind.FRAME;
    }

    public boolean ducted() {
        return this.duct.isPresent();
    }

    public double nominalPropInches() {
        return Math.round(this.maxPropMm / 25.4 * 2.0) / 2.0;
    }

    public static Builder builder(Identifier id) {
        return new Builder(id);
    }

    private static Set<Identifier> orderedCopy(Collection<Identifier> values, String what) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(what + " dürfen nicht leer sein");
        }
        LinkedHashSet<Identifier> copy = new LinkedHashSet<>();
        for (Identifier value : values) {
            copy.add(Objects.requireNonNull(value, what));
        }
        return Collections.unmodifiableSet(copy);
    }

    public static final class Builder {
        private final Identifier id;
        private PartBasics basics;
        private FrameRole role = FrameRole.FREESTYLE;
        private FrameShape shape = FrameShape.TRUE_X;
        private int wheelbaseMm = -1;
        private int minPropMm;
        private int maxPropMm = -1;
        private final Set<Identifier> motorMounts = new LinkedHashSet<>();
        private final Set<Identifier> stackMounts = new LinkedHashSet<>();
        private final Set<AccessorySlot> accessoryMounts = EnumSet.noneOf(AccessorySlot.class);
        private List<MotorArm> arms = List.of();
        private Boolean batteryOnTop;
        private Double batteryPlateMm;
        private Double electronicsMm;
        private Optional<Vec3Mm> cameraMm = Optional.empty();
        private double cameraTiltDeg = AirframeGeometry.DEFAULT_CAMERA_TILT;
        private Vec3Mm centerOfMassMm = Vec3Mm.ZERO;
        private MassSplit massSplit;
        private final Map<AccessorySlot, AnchorSpec> anchors = new EnumMap<>(AccessorySlot.class);
        private double frontalAreaM2 = -1.0;
        private double dragCoefficient = -1.0;
        private OptionalDouble sideAreaM2 = OptionalDouble.empty();
        private OptionalDouble topAreaM2 = OptionalDouble.empty();
        private Optional<DuctSpec> duct = Optional.empty();
        private double impactTolerance = 8.0;
        private BatteryFitRule batteryFit;

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

        public Builder role(FrameRole value) {
            this.role = value;
            return this;
        }

        public Builder shape(FrameShape value) {
            this.shape = value;
            return this;
        }

        public Builder wheelbase(int mm) {
            this.wheelbaseMm = mm;
            return this;
        }

        public Builder props(int minMm, int maxMm) {
            this.minPropMm = minMm;
            this.maxPropMm = maxMm;
            return this;
        }

        public Builder maxProp(int mm) {
            this.maxPropMm = mm;
            return this;
        }

        public Builder motorMount(Identifier... mounts) {
            this.motorMounts.addAll(List.of(mounts));
            return this;
        }

        public Builder stackMount(Identifier... mounts) {
            this.stackMounts.addAll(List.of(mounts));
            return this;
        }

        public Builder accessoryMount(AccessorySlot... slots) {
            this.accessoryMounts.addAll(List.of(slots));
            return this;
        }

        public Builder arms(List<MotorArm> value) {
            this.arms = value;
            return this;
        }

        public Builder battery(boolean onTop, double plateMm) {
            this.batteryOnTop = onTop;
            this.batteryPlateMm = plateMm;
            return this;
        }

        public Builder electronics(double mm) {
            this.electronicsMm = mm;
            return this;
        }

        public Builder camera(Vec3Mm positionMm) {
            this.cameraMm = Optional.of(positionMm);
            return this;
        }

        public Builder cameraTilt(double degrees) {
            this.cameraTiltDeg = degrees;
            return this;
        }

        public Builder centerOfMass(Vec3Mm positionMm) {
            this.centerOfMassMm = positionMm;
            return this;
        }

        public Builder massSplit(MassSplit value) {
            this.massSplit = value;
            return this;
        }

        public Builder anchor(AccessorySlot slot, AnchorSpec anchor) {
            this.anchors.put(slot, anchor);
            return this;
        }

        public Builder aero(double frontalAreaM2, double dragCoefficient) {
            this.frontalAreaM2 = frontalAreaM2;
            this.dragCoefficient = dragCoefficient;
            return this;
        }

        public Builder sideArea(double m2) {
            this.sideAreaM2 = OptionalDouble.of(m2);
            return this;
        }

        public Builder topArea(double m2) {
            this.topAreaM2 = OptionalDouble.of(m2);
            return this;
        }

        public Builder duct(DuctSpec value) {
            this.duct = Optional.of(value);
            return this;
        }

        public Builder impactTolerance(double metersPerSecond) {
            this.impactTolerance = metersPerSecond;
            return this;
        }

        public Builder batteryFit(BatteryFitRule value) {
            this.batteryFit = value;
            return this;
        }

        public FrameDefinition build() {
            if (this.basics == null) {
                throw new IllegalArgumentException(this.id + ": Masse fehlt");
            }
            if (this.wheelbaseMm < 0 || this.maxPropMm < 0) {
                throw new IllegalArgumentException(this.id + ": Radstand und größter Propeller sind Pflicht");
            }
            if (this.frontalAreaM2 < 0.0 || this.dragCoefficient < 0.0) {
                throw new IllegalArgumentException(this.id + ": Stirnfläche und Widerstandsbeiwert sind Pflicht");
            }
            if (this.batteryFit == null) {
                throw new IllegalArgumentException(this.id + ": Akku-Regel (battery_fit) ist Pflicht");
            }
            boolean onTop = this.batteryOnTop != null ? this.batteryOnTop : AirframeGeometry.defaultBatteryOnTop(this.wheelbaseMm);
            double plate = this.batteryPlateMm != null ? this.batteryPlateMm : AirframeGeometry.defaultBatteryPlateMm(this.wheelbaseMm);
            double electronics = this.electronicsMm != null ? this.electronicsMm : AirframeGeometry.defaultElectronicsMm(this.wheelbaseMm);
            MassSplit split = this.massSplit != null ? this.massSplit : this.duct.isPresent() ? MassSplit.DUCTED : MassSplit.OPEN;
            AirframeGeometry geometry = new AirframeGeometry(this.arms, onTop, plate, electronics, this.cameraMm, this.cameraTiltDeg,
                    this.centerOfMassMm, split, this.anchors);
            FrameAero aero = new FrameAero(this.frontalAreaM2, this.dragCoefficient, this.sideAreaM2, this.topAreaM2);
            return new FrameDefinition(this.id, this.basics, this.role, this.shape, this.wheelbaseMm, this.minPropMm, this.maxPropMm,
                    this.motorMounts, this.stackMounts, this.accessoryMounts, geometry, aero, this.duct, this.impactTolerance, this.batteryFit);
        }
    }
}
