package de.lardtom.propwash.api.part;

import java.util.Objects;
import java.util.OptionalDouble;

public record MotorThermal(OptionalDouble heatCapacityJPerK, OptionalDouble coolingWPerK, OptionalDouble coolingWPerKPerRadS,
                           double derateStartC, double maxTempC, double wearPerSecondAtMax) {
    public static final MotorThermal DEFAULT = new MotorThermal(OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(),
            130.0, 150.0, 0.002);

    public MotorThermal {
        heatCapacityJPerK = positive(heatCapacityJPerK, 100000.0, "Wärmekapazität");
        coolingWPerK = positive(coolingWPerK, 100.0, "Kühlung");
        coolingWPerKPerRadS = positive(coolingWPerKPerRadS, 1.0, "drehzahlabhängige Kühlung");
        if (!(derateStartC >= 40.0 && derateStartC < maxTempC && maxTempC <= 300.0)) {
            throw new IllegalArgumentException("Temperaturgrenzen ungültig: Drosselung ab " + derateStartC + " °C, Maximum " + maxTempC + " °C");
        }
        if (!(wearPerSecondAtMax >= 0.0 && wearPerSecondAtMax <= 1.0)) {
            throw new IllegalArgumentException("Hitzeverschleiß muss zwischen 0 und 1 pro Sekunde liegen: " + wearPerSecondAtMax);
        }
    }

    public static MotorThermal limits(double derateStartC, double maxTempC) {
        return new MotorThermal(OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(), derateStartC, maxTempC,
                DEFAULT.wearPerSecondAtMax);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private OptionalDouble heatCapacityJPerK = OptionalDouble.empty();
        private OptionalDouble coolingWPerK = OptionalDouble.empty();
        private OptionalDouble coolingWPerKPerRadS = OptionalDouble.empty();
        private double derateStartC = DEFAULT.derateStartC;
        private double maxTempC = DEFAULT.maxTempC;
        private double wearPerSecondAtMax = DEFAULT.wearPerSecondAtMax;

        private Builder() {
        }

        public Builder heatCapacity(double joulePerKelvin) {
            this.heatCapacityJPerK = OptionalDouble.of(joulePerKelvin);
            return this;
        }

        public Builder cooling(double wattPerKelvin) {
            this.coolingWPerK = OptionalDouble.of(wattPerKelvin);
            return this;
        }

        public Builder coolingPerRadS(double wattPerKelvinPerRadS) {
            this.coolingWPerKPerRadS = OptionalDouble.of(wattPerKelvinPerRadS);
            return this;
        }

        public Builder limits(double derateStartC, double maxTempC) {
            this.derateStartC = derateStartC;
            this.maxTempC = maxTempC;
            return this;
        }

        public Builder wearAtMax(double perSecond) {
            this.wearPerSecondAtMax = perSecond;
            return this;
        }

        public MotorThermal build() {
            return new MotorThermal(this.heatCapacityJPerK, this.coolingWPerK, this.coolingWPerKPerRadS, this.derateStartC, this.maxTempC,
                    this.wearPerSecondAtMax);
        }
    }

    private static OptionalDouble positive(OptionalDouble value, double max, String name) {
        OptionalDouble v = Objects.requireNonNullElse(value, OptionalDouble.empty());
        if (v.isPresent() && !(v.getAsDouble() > 0.0 && v.getAsDouble() <= max)) {
            throw new IllegalArgumentException(name + " muss größer als 0 und höchstens " + max + " sein: " + v.getAsDouble());
        }
        return v;
    }
}
