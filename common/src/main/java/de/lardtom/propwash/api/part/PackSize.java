package de.lardtom.propwash.api.part;

public record PackSize(double lengthMm, double widthMm, double heightMm) {
    public PackSize {
        if (!(lengthMm > 1.0 && lengthMm <= 500.0 && widthMm > 1.0 && widthMm <= 500.0 && heightMm > 1.0 && heightMm <= 500.0)) {
            throw new IllegalArgumentException("Akkumaße ungültig: " + lengthMm + " × " + widthMm + " × " + heightMm + " mm");
        }
    }
}
