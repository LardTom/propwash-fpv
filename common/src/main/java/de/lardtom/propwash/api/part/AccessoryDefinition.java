package de.lardtom.propwash.api.part;

import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.Optional;

public record AccessoryDefinition(Identifier id, PartBasics basics, AccessorySlot slot, double widthMm, double heightMm, double depthMm,
                                  double flightBatteryWatts, double frontalAreaM2, int ledCount, Optional<ActionCamDefinition> actionCam,
                                  double lensXMm, double lensLengthMm) implements PartDefinition {
    public AccessoryDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(basics, "basics");
        Objects.requireNonNull(slot, "slot");
        actionCam = Objects.requireNonNullElse(actionCam, Optional.empty());
        if (!(widthMm > 0.0 && widthMm <= 500.0 && heightMm > 0.0 && heightMm <= 500.0 && depthMm > 0.0 && depthMm <= 500.0)) {
            throw new IllegalArgumentException(id + ": Zubehörmaße ungültig");
        }
        if (!(flightBatteryWatts >= 0.0 && flightBatteryWatts <= 200.0)) {
            throw new IllegalArgumentException(id + ": Leistungsaufnahme ungültig: " + flightBatteryWatts);
        }
        if (!(frontalAreaM2 >= 0.0 && frontalAreaM2 <= 0.5)) {
            throw new IllegalArgumentException(id + ": Stirnfläche ungültig: " + frontalAreaM2);
        }
        if (ledCount < 0 || ledCount > 64) {
            throw new IllegalArgumentException(id + ": LED-Anzahl muss zwischen 0 und 64 liegen: " + ledCount);
        }
        if (actionCam.isPresent() && slot != AccessorySlot.TOP) {
            throw new IllegalArgumentException(id + ": Action-Cams sitzen oben");
        }
        if (!Double.isFinite(lensXMm) || !(lensLengthMm >= 0.0 && lensLengthMm <= 100.0)) {
            throw new IllegalArgumentException(id + ": Objektivlage ungültig");
        }
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
                    this.flightBatteryWatts, this.frontalAreaM2, this.ledCount, this.actionCam, this.lensXMm, this.lensLengthMm);
        }
    }
}
