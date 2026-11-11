package de.lardtom.propwash.api.accessory;

public record DispenseOptions(double speedScale, double inaccuracy, boolean allowFluids, boolean applyRecoil, boolean consume) {
    private static final DispenseOptions DEFAULTS = new DispenseOptions(1.0, 1.0, false, true, true);

    public DispenseOptions {
        if (!(speedScale >= 0.0) || !Double.isFinite(speedScale)) {
            throw new IllegalArgumentException("speedScale ungültig: " + speedScale);
        }
        if (!(inaccuracy >= 0.0) || !Double.isFinite(inaccuracy)) {
            throw new IllegalArgumentException("inaccuracy ungültig: " + inaccuracy);
        }
    }

    public static DispenseOptions defaults() {
        return DEFAULTS;
    }

    public DispenseOptions speedScale(double value) {
        return new DispenseOptions(value, this.inaccuracy, this.allowFluids, this.applyRecoil, this.consume);
    }

    public DispenseOptions inaccuracy(double value) {
        return new DispenseOptions(this.speedScale, value, this.allowFluids, this.applyRecoil, this.consume);
    }

    public DispenseOptions allowFluids(boolean value) {
        return new DispenseOptions(this.speedScale, this.inaccuracy, value, this.applyRecoil, this.consume);
    }

    public DispenseOptions applyRecoil(boolean value) {
        return new DispenseOptions(this.speedScale, this.inaccuracy, this.allowFluids, value, this.consume);
    }

    public DispenseOptions consume(boolean value) {
        return new DispenseOptions(this.speedScale, this.inaccuracy, this.allowFluids, this.applyRecoil, value);
    }
}
