package de.lardtom.propwash.api.part;

public record MassSplit(double arms, double plate, double duct) {
    public static final MassSplit OPEN = new MassSplit(0.60, 0.40, 0.0);
    public static final MassSplit DUCTED = new MassSplit(0.20, 0.30, 0.50);

    public MassSplit {
        if (!(arms >= 0.0 && arms <= 1.0 && plate >= 0.0 && plate <= 1.0 && duct >= 0.0 && duct <= 1.0)) {
            throw new IllegalArgumentException("Massenanteile müssen zwischen 0 und 1 liegen: " + arms + ", " + plate + ", " + duct);
        }
        if (Math.abs(arms + plate + duct - 1.0) > 0.001) {
            throw new IllegalArgumentException("Massenanteile müssen zusammen 1 ergeben: " + (arms + plate + duct));
        }
    }
}
