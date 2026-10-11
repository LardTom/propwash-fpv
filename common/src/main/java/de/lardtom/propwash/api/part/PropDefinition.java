package de.lardtom.propwash.api.part;

import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.OptionalDouble;

public record PropDefinition(Identifier id, PartBasics basics, int diameterMm, double pitchInch, int blades, double thrustCoefficient,
                             double powerCoefficient, PropMaterial material, double stiffness, Identifier mount,
                             OptionalDouble reverseThrust) implements PartDefinition {
    public static final double MIN_REVERSE_THRUST = 0.2;
    public static final double MAX_REVERSE_THRUST = 1.0;

    public PropDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(basics, "basics");
        material = Objects.requireNonNullElse(material, PropMaterial.POLYCARBONATE);
        mount = Objects.requireNonNullElse(mount, Mounts.PROP_UNIVERSAL);
        if (diameterMm < 10 || diameterMm > FrameDefinition.MAX_PROP_MM) {
            throw new IllegalArgumentException(id + ": Propellerdurchmesser muss zwischen 10 und " + FrameDefinition.MAX_PROP_MM + " mm liegen: "
                    + diameterMm);
        }
        if (!(pitchInch > 0.0 && pitchInch <= 12.0)) {
            throw new IllegalArgumentException(id + ": Steigung ungültig: " + pitchInch);
        }
        if (blades < 2 || blades > 8) {
            throw new IllegalArgumentException(id + ": Blattzahl muss zwischen 2 und 8 liegen: " + blades);
        }
        if (!(thrustCoefficient > 0.0 && thrustCoefficient < 1.0 && powerCoefficient > 0.0 && powerCoefficient < 1.0)) {
            throw new IllegalArgumentException(id + ": Schub- und Leistungsbeiwert müssen zwischen 0 und 1 liegen");
        }
        if (!(stiffness > 0.0 && stiffness <= 10.0)) {
            throw new IllegalArgumentException(id + ": Steifigkeit muss größer als 0 und höchstens 10 sein: " + stiffness);
        }
        reverseThrust = Objects.requireNonNullElse(reverseThrust, OptionalDouble.empty());
        if (reverseThrust.isPresent() && !(reverseThrust.getAsDouble() >= MIN_REVERSE_THRUST && reverseThrust.getAsDouble() <= MAX_REVERSE_THRUST)) {
            throw new IllegalArgumentException(id + ": Umkehrschub muss zwischen " + MIN_REVERSE_THRUST + " und " + MAX_REVERSE_THRUST + " liegen: "
                    + reverseThrust.getAsDouble());
        }
    }

    public PropDefinition(Identifier id, PartBasics basics, int diameterMm, double pitchInch, int blades, double thrustCoefficient,
                          double powerCoefficient, PropMaterial material, double stiffness, Identifier mount) {
        this(id, basics, diameterMm, pitchInch, blades, thrustCoefficient, powerCoefficient, material, stiffness, mount, OptionalDouble.empty());
    }

    @Override
    public PartKind kind() {
        return PartKind.PROP;
    }

    public double diameterInches() {
        return this.diameterMm / 25.4;
    }

    public static Builder builder(Identifier id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final Identifier id;
        private PartBasics basics;
        private boolean durabilitySet;
        private int diameterMm = -1;
        private double pitchInch = -1.0;
        private int blades = -1;
        private double thrustCoefficient = -1.0;
        private double powerCoefficient = -1.0;
        private PropMaterial material = PropMaterial.POLYCARBONATE;
        private double stiffness = Double.NaN;
        private Identifier mount = Mounts.PROP_UNIVERSAL;
        private OptionalDouble reverseThrust = OptionalDouble.empty();

        private Builder(Identifier id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public Builder basics(PartBasics value) {
            this.basics = value;
            this.durabilitySet = true;
            return this;
        }

        public Builder mass(double grams) {
            this.basics = PartBasics.withMass(this.basics, grams);
            return this;
        }

        public Builder durability(double value) {
            if (this.basics == null) {
                throw new IllegalStateException(this.id + ": zuerst die Masse setzen");
            }
            this.basics = this.basics.withDurability(value);
            this.durabilitySet = true;
            return this;
        }

        public Builder size(int diameterMm, double pitchInch, int blades) {
            this.diameterMm = diameterMm;
            this.pitchInch = pitchInch;
            this.blades = blades;
            return this;
        }

        public Builder coefficients(double thrust, double power) {
            this.thrustCoefficient = thrust;
            this.powerCoefficient = power;
            return this;
        }

        public Builder material(PropMaterial value) {
            this.material = value;
            return this;
        }

        public Builder stiffness(double value) {
            this.stiffness = value;
            return this;
        }

        public Builder mount(Identifier value) {
            this.mount = value;
            return this;
        }

        public Builder reverseThrust(double ratio) {
            this.reverseThrust = OptionalDouble.of(ratio);
            return this;
        }

        public PropDefinition build() {
            if (this.basics == null || this.diameterMm < 0 || this.blades < 0 || this.thrustCoefficient < 0.0 || this.powerCoefficient < 0.0) {
                throw new IllegalArgumentException(this.id + ": Masse, Größe, Blattzahl und Beiwerte sind Pflicht");
            }
            PartBasics resolved = this.durabilitySet ? this.basics : this.basics.withDurability(this.material.defaultDurability());
            double resolvedStiffness = Double.isNaN(this.stiffness) ? this.material.defaultStiffness() : this.stiffness;
            return new PropDefinition(this.id, resolved, this.diameterMm, this.pitchInch, this.blades, this.thrustCoefficient,
                    this.powerCoefficient, this.material, resolvedStiffness, this.mount, this.reverseThrust);
        }
    }
}
