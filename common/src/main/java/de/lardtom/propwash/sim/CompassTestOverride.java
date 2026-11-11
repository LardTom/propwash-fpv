package de.lardtom.propwash.sim;

import de.lardtom.propwash.Constants;

public final class CompassTestOverride {
    public static final String PROPERTY = "propwash.test.compassErrorDeg";
    private static volatile String reported;

    private CompassTestOverride() {
    }

    static double errorDeg() {
        String raw = System.getProperty(PROPERTY);
        if (raw == null) {
            return Double.NaN;
        }
        double value = parse(raw);
        report(raw, value);
        return value;
    }

    static double parse(String raw) {
        try {
            double value = Double.parseDouble(raw.strip());
            return Double.isFinite(value) ? value : Double.NaN;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static void report(String raw, double value) {
        if (raw.equals(reported)) {
            return;
        }
        reported = raw;
        if (Double.isNaN(value)) {
            Constants.LOG.warn("Propwash: Test-Eigenschaft {}=\"{}\" ist keine endliche Gradzahl und wird ignoriert", PROPERTY, raw);
        } else {
            Constants.LOG.warn("Propwash: Test-Eigenschaft {}={} – jeder neue Flug startet mit diesem Kompassfehler statt eines gewürfelten (nur für Tests)",
                    PROPERTY, value);
        }
    }
}
