package de.lardtom.propwash.api.part;

import net.minecraft.resources.Identifier;

import java.util.Objects;

public record MotorDefinition(Identifier id, PartBasics basics, String statorLabel, double statorDiameterMm, double statorHeightMm, int kv,
                              Identifier mount, double maxCurrentAmps, double resistanceOhm, int minCells, int maxCells,
                              Identifier propMount, MotorThermal thermal) implements PartDefinition {
    public MotorDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(basics, "basics");
        Objects.requireNonNull(mount, "mount");
        propMount = Objects.requireNonNullElse(propMount, Mounts.PROP_UNIVERSAL);
        thermal = Objects.requireNonNullElse(thermal, MotorThermal.DEFAULT);
        if (statorLabel == null || statorLabel.isBlank() || statorLabel.length() > 16) {
            throw new IllegalArgumentException(id + ": Stator-Bezeichnung fehlt oder ist zu lang");
        }
        if (!(statorDiameterMm >= 3.0 && statorDiameterMm <= 120.0 && statorHeightMm >= 0.5 && statorHeightMm <= 60.0)) {
            throw new IllegalArgumentException(id + ": Statormaße ungültig: " + statorDiameterMm + " × " + statorHeightMm + " mm");
        }
        if (kv < 50 || kv > 100000) {
            throw new IllegalArgumentException(id + ": KV muss zwischen 50 und 100000 liegen: " + kv);
        }
        if (!(maxCurrentAmps > 0.0 && maxCurrentAmps <= 500.0)) {
            throw new IllegalArgumentException(id + ": Höchststrom ungültig: " + maxCurrentAmps);
        }
        if (!(resistanceOhm > 0.0 && resistanceOhm < 10.0)) {
            throw new IllegalArgumentException(id + ": Wicklungswiderstand ungültig: " + resistanceOhm);
        }
        if (minCells < 1 || maxCells < minCells || maxCells > 16) {
            throw new IllegalArgumentException(id + ": Zellbereich ungültig: " + minCells + "–" + maxCells);
        }
    }

    @Override
    public PartKind kind() {
        return PartKind.MOTOR;
    }

    public static double[] parseStatorLabel(String label) {
        if (label == null || label.length() < 3) {
            return new double[]{22.0, 7.0};
        }
        try {
            double diameter = Double.parseDouble(label.substring(0, 2));
            double height = Double.parseDouble(label.substring(2));
            if (diameter > 0.0 && height > 0.0 && Double.isFinite(diameter) && Double.isFinite(height)) {
                return new double[]{diameter, height};
            }
        } catch (NumberFormatException ignored) {
        }
        return new double[]{22.0, 7.0};
    }

    public static Builder builder(Identifier id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final Identifier id;
        private PartBasics basics;
        private String statorLabel;
        private double statorDiameterMm = -1.0;
        private double statorHeightMm = -1.0;
        private int kv = -1;
        private Identifier mount;
        private double maxCurrentAmps = -1.0;
        private double resistanceOhm = -1.0;
        private int minCells = -1;
        private int maxCells = -1;
        private Identifier propMount = Mounts.PROP_UNIVERSAL;
        private MotorThermal thermal = MotorThermal.DEFAULT;

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

        public Builder stator(String label) {
            this.statorLabel = label;
            return this;
        }

        public Builder statorSize(double diameterMm, double heightMm) {
            this.statorDiameterMm = diameterMm;
            this.statorHeightMm = heightMm;
            return this;
        }

        public Builder kv(int value) {
            this.kv = value;
            return this;
        }

        public Builder mount(Identifier value) {
            this.mount = value;
            return this;
        }

        public Builder maxCurrent(double amps) {
            this.maxCurrentAmps = amps;
            return this;
        }

        public Builder resistance(double ohm) {
            this.resistanceOhm = ohm;
            return this;
        }

        public Builder cells(int min, int max) {
            this.minCells = min;
            this.maxCells = max;
            return this;
        }

        public Builder propMount(Identifier value) {
            this.propMount = value;
            return this;
        }

        public Builder thermal(MotorThermal value) {
            this.thermal = value;
            return this;
        }

        public MotorDefinition build() {
            if (this.basics == null || this.statorLabel == null || this.kv < 0 || this.mount == null || this.maxCurrentAmps < 0.0
                    || this.resistanceOhm < 0.0 || this.minCells < 0) {
                throw new IllegalArgumentException(this.id + ": Masse, Stator, KV, Halterung, Höchststrom, Widerstand und Zellbereich sind Pflicht");
            }
            double diameter = this.statorDiameterMm;
            double height = this.statorHeightMm;
            if (diameter < 0.0 || height < 0.0) {
                double[] parsed = parseStatorLabel(this.statorLabel);
                diameter = parsed[0];
                height = parsed[1];
            }
            return new MotorDefinition(this.id, this.basics, this.statorLabel, diameter, height, this.kv, this.mount, this.maxCurrentAmps,
                    this.resistanceOhm, this.minCells, this.maxCells, this.propMount, this.thermal);
        }
    }
}
