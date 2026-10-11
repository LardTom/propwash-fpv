package de.lardtom.propwash.api.part;

public record BatteryFitRule(int minCells, int maxCells, double maxMassGrams, int preferredMinCells, int preferredMinCapacityMah) {
    public BatteryFitRule {
        if (minCells < 1 || maxCells < minCells || maxCells > 16) {
            throw new IllegalArgumentException("Zellbereich ungültig: " + minCells + "–" + maxCells);
        }
        if (!(maxMassGrams > 0.0 && maxMassGrams <= 20000.0)) {
            throw new IllegalArgumentException("Höchstmasse des Akkus ungültig: " + maxMassGrams);
        }
        if (preferredMinCells < minCells || preferredMinCells > maxCells) {
            throw new IllegalArgumentException("Bevorzugte Mindestzellen außerhalb des Bereichs: " + preferredMinCells);
        }
        if (preferredMinCapacityMah < 0) {
            throw new IllegalArgumentException("Bevorzugte Mindestkapazität darf nicht negativ sein: " + preferredMinCapacityMah);
        }
    }
}
