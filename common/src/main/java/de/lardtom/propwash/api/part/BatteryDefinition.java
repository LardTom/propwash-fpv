package de.lardtom.propwash.api.part;

import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.Optional;

public record BatteryDefinition(Identifier id, PartBasics basics, int cells, int capacityMah, double cellResistanceOhm, int cRating,
                                Identifier chemistry, Optional<PackSize> packSize, String connector) implements PartDefinition {
    public static final int MAX_CELLS = 12;
    public static final Identifier LIPO = Identifier.fromNamespaceAndPath("propwash", "lipo");
    public static final Identifier LIHV = Identifier.fromNamespaceAndPath("propwash", "lihv");
    public static final Identifier LION = Identifier.fromNamespaceAndPath("propwash", "lion");

    public BatteryDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(basics, "basics");
        Objects.requireNonNull(chemistry, "chemistry");
        packSize = Objects.requireNonNullElse(packSize, Optional.empty());
        connector = Objects.requireNonNullElse(connector, "");
        if (cells < 1 || cells > MAX_CELLS) {
            throw new IllegalArgumentException(id + ": Zellzahl muss zwischen 1 und " + MAX_CELLS + " liegen: " + cells);
        }
        if (capacityMah < 50 || capacityMah > 50000) {
            throw new IllegalArgumentException(id + ": Kapazität muss zwischen 50 und 50000 mAh liegen: " + capacityMah);
        }
        if (!(cellResistanceOhm > 0.0 && cellResistanceOhm < 1.0)) {
            throw new IllegalArgumentException(id + ": Innenwiderstand je Zelle ungültig: " + cellResistanceOhm);
        }
        if (cRating < 1 || cRating > 300) {
            throw new IllegalArgumentException(id + ": C-Rate muss zwischen 1 und 300 liegen: " + cRating);
        }
        if (connector.length() > 16) {
            throw new IllegalArgumentException(id + ": Stecker-Bezeichnung zu lang");
        }
    }

    @Override
    public PartKind kind() {
        return PartKind.BATTERY;
    }

    public double maxContinuousCurrent() {
        return this.cRating * this.capacityMah / 1000.0;
    }

    public static Builder builder(Identifier id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final Identifier id;
        private PartBasics basics;
        private int cells = -1;
        private int capacityMah = -1;
        private double cellResistanceOhm = -1.0;
        private int cRating = -1;
        private Identifier chemistry = LIPO;
        private Optional<PackSize> packSize = Optional.empty();
        private String connector = "";

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

        public Builder cells(int value) {
            this.cells = value;
            return this;
        }

        public Builder capacity(int mah) {
            this.capacityMah = mah;
            return this;
        }

        public Builder cellResistance(double ohm) {
            this.cellResistanceOhm = ohm;
            return this;
        }

        public Builder cRating(int value) {
            this.cRating = value;
            return this;
        }

        public Builder chemistry(Identifier value) {
            this.chemistry = value;
            return this;
        }

        public Builder packSize(PackSize value) {
            this.packSize = Optional.of(value);
            return this;
        }

        public Builder connector(String value) {
            this.connector = value;
            return this;
        }

        public BatteryDefinition build() {
            if (this.basics == null || this.cells < 0 || this.capacityMah < 0 || this.cellResistanceOhm < 0.0 || this.cRating < 0) {
                throw new IllegalArgumentException(this.id + ": Masse, Zellzahl, Kapazität, Innenwiderstand und C-Rate sind Pflicht");
            }
            return new BatteryDefinition(this.id, this.basics, this.cells, this.capacityMah, this.cellResistanceOhm, this.cRating,
                    this.chemistry, this.packSize, this.connector);
        }
    }
}
