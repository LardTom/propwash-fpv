package de.lardtom.propwash.video.link;

public final class RadioBand {
    public static final double FREQUENCY_GHZ = 5.8;
    public static final double SPEED_OF_LIGHT = 299_792_458.0;
    public static final double WAVELENGTH_M = SPEED_OF_LIGHT / (FREQUENCY_GHZ * 1.0E9);
    public static final double WAVENUMBER = 2.0 * Math.PI / WAVELENGTH_M;

    private RadioBand() {
    }

    public static double firstFresnelRadius(double toTransmitter, double toReceiver) {
        double d1 = Math.max(toTransmitter, 0.0);
        double d2 = Math.max(toReceiver, 0.0);
        double sum = d1 + d2;
        if (!(sum > 0.0)) {
            return 0.0;
        }
        return Math.sqrt(WAVELENGTH_M * d1 * d2 / sum);
    }

    public static double nu(double clearance, double toTransmitter, double toReceiver) {
        double radius = firstFresnelRadius(toTransmitter, toReceiver);
        if (!(radius > 0.0)) {
            return clearance > 0.0 ? Double.POSITIVE_INFINITY : (clearance < 0.0 ? Double.NEGATIVE_INFINITY : 0.0);
        }
        return Math.sqrt(2.0) * clearance / radius;
    }
}
