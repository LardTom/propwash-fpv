package de.lardtom.propwash.sim;

public final class FilterSettings {
    public PidLoopRate pidLoop = PidLoopRate.HZ_2000;
    public double gyroLpfHz = 250;
    public double dtermLpf1Hz = 100;
    public double dtermLpf2Hz = 0;
    public double rcSmoothingHz = 40;

    public FilterSettings() {
    }

    public FilterSettings copy() {
        FilterSettings c = new FilterSettings();
        c.pidLoop = this.pidLoop;
        c.gyroLpfHz = this.gyroLpfHz;
        c.dtermLpf1Hz = this.dtermLpf1Hz;
        c.dtermLpf2Hz = this.dtermLpf2Hz;
        c.rcSmoothingHz = this.rcSmoothingHz;
        return c;
    }

    public void sanitize() {
        if (this.pidLoop == null) {
            this.pidLoop = PidLoopRate.HZ_2000;
        }
        this.gyroLpfHz = SimMath.sanitizeFilterHz(this.gyroLpfHz, 20, 1000, 250);
        this.dtermLpf1Hz = SimMath.sanitizeFilterHz(this.dtermLpf1Hz, 20, 1000, 100);
        this.dtermLpf2Hz = SimMath.sanitizeFilterHz(this.dtermLpf2Hz, 20, 1000, 0);
        this.rcSmoothingHz = SimMath.sanitizeFilterHz(this.rcSmoothingHz, 5, 200, 40);
    }

    boolean sameAs(FilterSettings o) {
        return o != null && this.pidLoop == o.pidLoop && this.gyroLpfHz == o.gyroLpfHz && this.dtermLpf1Hz == o.dtermLpf1Hz
                && this.dtermLpf2Hz == o.dtermLpf2Hz && this.rcSmoothingHz == o.rcSmoothingHz;
    }
}
