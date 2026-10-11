package de.lardtom.propwash.api.part;

import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.OptionalDouble;

public record StackDefinition(Identifier id, PartBasics basics, Identifier mount, double escContinuousAmps, int minCells, int maxCells,
                              boolean allInOne, OptionalDouble escBurstAmps, OptionalDouble escResistanceOhm,
                              OptionalDouble brownoutVoltage, OptionalDouble boardSizeMm) implements PartDefinition {
    public StackDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(basics, "basics");
        Objects.requireNonNull(mount, "mount");
        if (!(escContinuousAmps > 0.0 && escContinuousAmps <= 500.0)) {
            throw new IllegalArgumentException(id + ": ESC-Dauerstrom muss größer als 0 und höchstens 500 A sein: " + escContinuousAmps);
        }
        if (minCells < 1 || maxCells < minCells || maxCells > 16) {
            throw new IllegalArgumentException(id + ": Zellbereich ungültig: " + minCells + "–" + maxCells);
        }
        escBurstAmps = Objects.requireNonNullElse(escBurstAmps, OptionalDouble.empty());
        escResistanceOhm = Objects.requireNonNullElse(escResistanceOhm, OptionalDouble.empty());
        brownoutVoltage = Objects.requireNonNullElse(brownoutVoltage, OptionalDouble.empty());
        boardSizeMm = Objects.requireNonNullElse(boardSizeMm, OptionalDouble.empty());
        if (escBurstAmps.isPresent() && escBurstAmps.getAsDouble() < escContinuousAmps) {
            throw new IllegalArgumentException(id + ": ESC-Spitzenstrom kleiner als Dauerstrom");
        }
        if (escResistanceOhm.isPresent() && !(escResistanceOhm.getAsDouble() > 0.0 && escResistanceOhm.getAsDouble() < 1.0)) {
            throw new IllegalArgumentException(id + ": ESC-Widerstand ungültig: " + escResistanceOhm.getAsDouble());
        }
        if (brownoutVoltage.isPresent() && !(brownoutVoltage.getAsDouble() > 0.0 && brownoutVoltage.getAsDouble() < 20.0)) {
            throw new IllegalArgumentException(id + ": Brownout-Spannung ungültig: " + brownoutVoltage.getAsDouble());
        }
        if (boardSizeMm.isPresent() && !(boardSizeMm.getAsDouble() >= 10.0 && boardSizeMm.getAsDouble() <= 120.0)) {
            throw new IllegalArgumentException(id + ": Platinengröße ungültig: " + boardSizeMm.getAsDouble());
        }
    }

    @Override
    public PartKind kind() {
        return PartKind.STACK;
    }

    public static Builder builder(Identifier id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final Identifier id;
        private PartBasics basics;
        private Identifier mount;
        private double escContinuousAmps = -1.0;
        private int minCells = -1;
        private int maxCells = -1;
        private boolean allInOne;
        private OptionalDouble escBurstAmps = OptionalDouble.empty();
        private OptionalDouble escResistanceOhm = OptionalDouble.empty();
        private OptionalDouble brownoutVoltage = OptionalDouble.empty();
        private OptionalDouble boardSizeMm = OptionalDouble.empty();

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

        public Builder mount(Identifier value) {
            this.mount = value;
            return this;
        }

        public Builder escContinuous(double amps) {
            this.escContinuousAmps = amps;
            return this;
        }

        public Builder cells(int min, int max) {
            this.minCells = min;
            this.maxCells = max;
            return this;
        }

        public Builder allInOne(boolean value) {
            this.allInOne = value;
            return this;
        }

        public Builder escBurst(double amps) {
            this.escBurstAmps = OptionalDouble.of(amps);
            return this;
        }

        public Builder escResistance(double ohm) {
            this.escResistanceOhm = OptionalDouble.of(ohm);
            return this;
        }

        public Builder brownout(double volts) {
            this.brownoutVoltage = OptionalDouble.of(volts);
            return this;
        }

        public Builder boardSize(double mm) {
            this.boardSizeMm = OptionalDouble.of(mm);
            return this;
        }

        public StackDefinition build() {
            if (this.basics == null || this.mount == null || this.escContinuousAmps < 0.0 || this.minCells < 0) {
                throw new IllegalArgumentException(this.id + ": Masse, Halterung, ESC-Dauerstrom und Zellbereich sind Pflicht");
            }
            return new StackDefinition(this.id, this.basics, this.mount, this.escContinuousAmps, this.minCells, this.maxCells, this.allInOne,
                    this.escBurstAmps, this.escResistanceOhm, this.brownoutVoltage, this.boardSizeMm);
        }
    }
}
