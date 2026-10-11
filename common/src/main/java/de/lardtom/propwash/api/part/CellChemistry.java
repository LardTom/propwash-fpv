package de.lardtom.propwash.api.part;

import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

public record CellChemistry(Identifier id, double fullCellVoltage, double nominalCellVoltage, double emptyCellVoltage,
                            double cutoffCellVoltage, double storageCellVoltage, double densityGramsPerCm3, List<Double> ocvShape,
                            OptionalDouble usableFraction) {
    public static final List<Double> SOC_GRID = List.of(0.0, 0.05, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0);

    public CellChemistry(Identifier id, double fullCellVoltage, double nominalCellVoltage, double emptyCellVoltage, double cutoffCellVoltage,
                         double storageCellVoltage, double densityGramsPerCm3, List<Double> ocvShape) {
        this(id, fullCellVoltage, nominalCellVoltage, emptyCellVoltage, cutoffCellVoltage, storageCellVoltage, densityGramsPerCm3, ocvShape,
                OptionalDouble.empty());
    }

    public CellChemistry {
        Objects.requireNonNull(id, "id");
        if (!(cutoffCellVoltage > 0.5 && cutoffCellVoltage <= emptyCellVoltage && emptyCellVoltage < nominalCellVoltage
                && nominalCellVoltage < fullCellVoltage && fullCellVoltage <= 5.0)) {
            throw new IllegalArgumentException(id + ": Zellspannungen müssen Abschalt ≤ Leer < Nenn < Voll ≤ 5 V erfüllen");
        }
        if (!(storageCellVoltage >= emptyCellVoltage && storageCellVoltage <= fullCellVoltage)) {
            throw new IllegalArgumentException(id + ": Lagerspannung außerhalb des Bereichs: " + storageCellVoltage);
        }
        if (!(densityGramsPerCm3 > 0.5 && densityGramsPerCm3 < 10.0)) {
            throw new IllegalArgumentException(id + ": Dichte ungültig: " + densityGramsPerCm3);
        }
        ocvShape = List.copyOf(Objects.requireNonNull(ocvShape, "ocvShape"));
        if (ocvShape.size() != SOC_GRID.size()) {
            throw new IllegalArgumentException(id + ": Spannungskurve braucht genau " + SOC_GRID.size() + " Stützstellen");
        }
        double previous = -1.0;
        for (double value : ocvShape) {
            if (!(value >= previous && value >= 0.0 && value <= 1.0)) {
                throw new IllegalArgumentException(id + ": Spannungskurve muss von 0 bis 1 monoton steigen");
            }
            previous = value;
        }
        if (ocvShape.getFirst() != 0.0 || ocvShape.getLast() != 1.0) {
            throw new IllegalArgumentException(id + ": Spannungskurve muss bei 0 beginnen und bei 1 enden");
        }
        usableFraction = Objects.requireNonNullElse(usableFraction, OptionalDouble.empty());
        if (usableFraction.isPresent() && !(usableFraction.getAsDouble() >= 0.5 && usableFraction.getAsDouble() <= 1.0)) {
            throw new IllegalArgumentException(id + ": Nutzbarer Anteil muss zwischen 0,5 und 1 liegen: " + usableFraction.getAsDouble());
        }
    }

    public double landingCellVoltage() {
        return 0.5 * (this.emptyCellVoltage + this.cutoffCellVoltage);
    }

    public double effectiveUsableFraction() {
        if (this.usableFraction.isPresent()) {
            return this.usableFraction.getAsDouble();
        }
        if (this.emptyCellVoltage < 3.25) {
            return 0.88;
        }
        return this.fullCellVoltage >= 4.3 ? 0.85 : 0.8;
    }
}
