package de.lardtom.propwash.video.vtx;

public final class VtxHeat {
    public static final double HEAT_CAPACITY_J_PER_GK = 1.0;
    public static final double REFERENCE_AMBIENT_C = 22.0;
    public static final double DESIGN_C = 75.0;
    public static final double MIN_STILL_AIR_W_PER_GK = 0.002;
    public static final double AIRFLOW_GAIN = 1.2;
    public static final double PROP_AIR_MS = 6.0;
    public static final double DEFAULT_AMBIENT_C = 22.0;
    public static final double MIN_AMBIENT_C = -15.0;
    public static final double MAX_AMBIENT_C = 40.0;
    public static final double LIMIT_C = 85.0;
    public static final double WARN_C = 80.0;
    public static final double RELEASE_C = 75.0;

    private final double capacity;
    private final double stillConductance;
    private double tempC;

    public VtxHeat(double massGrams, double designHeatW, double ambientC) {
        double mass = Double.isFinite(massGrams) ? Math.max(VtxTable.MIN_MASS_G, massGrams) : VtxTable.MIN_MASS_G;
        double design = Double.isFinite(designHeatW) ? Math.max(0.0, designHeatW) : 0.0;
        this.capacity = HEAT_CAPACITY_J_PER_GK * mass;
        this.stillConductance = Math.max(MIN_STILL_AIR_W_PER_GK * mass, design / (DESIGN_C - REFERENCE_AMBIENT_C));
        this.tempC = clampAmbient(ambientC);
    }

    public static VtxHeat of(VtxTable table, double ambientC) {
        return new VtxHeat(table.massGrams(), table.heatW(table.ratedIndex()), ambientC);
    }

    public double tempC() {
        return this.tempC;
    }

    public void set(double tempC) {
        if (Double.isFinite(tempC)) {
            this.tempC = tempC;
        }
    }

    public double conductance(double airSpeed) {
        double v = Double.isFinite(airSpeed) ? Math.max(0.0, airSpeed) : 0.0;
        return this.stillConductance * (1.0 + AIRFLOW_GAIN * Math.sqrt(v));
    }

    public double steadyC(double heatW, double airSpeed, double ambientC) {
        return clampAmbient(ambientC) + Math.max(0.0, heatW) / this.conductance(airSpeed);
    }

    public double secondsToLimit(double heatW, double airSpeed, double ambientC) {
        double ambient = clampAmbient(ambientC);
        double h = this.conductance(airSpeed);
        double rise = Math.max(0.0, Double.isFinite(heatW) ? heatW : 0.0) / h;
        double need = LIMIT_C - ambient;
        if (need <= 0.0) {
            return 0.0;
        }
        if (rise <= need) {
            return Double.POSITIVE_INFINITY;
        }
        return this.capacity / h * Math.log(rise / (rise - need));
    }

    public double step(double dt, double heatW, double airSpeed, double ambientC) {
        if (!(dt > 0.0) || !Double.isFinite(dt)) {
            return this.tempC;
        }
        double h = this.conductance(airSpeed);
        double steady = clampAmbient(ambientC) + Math.max(0.0, Double.isFinite(heatW) ? heatW : 0.0) / h;
        this.tempC = steady + (this.tempC - steady) * Math.exp(-h * dt / this.capacity);
        return this.tempC;
    }

    public static double airSpeed(boolean armed, double droneSpeed, double motorOutput) {
        double speed = Double.isFinite(droneSpeed) ? Math.max(0.0, droneSpeed) : 0.0;
        if (!armed) {
            return speed;
        }
        double out = Double.isFinite(motorOutput) ? Math.max(0.0, Math.min(1.0, motorOutput)) : 0.0;
        return speed + PROP_AIR_MS * Math.sqrt(out);
    }

    public static double ambientFromBiome(float biomeTemperature) {
        if (!Float.isFinite(biomeTemperature)) {
            return DEFAULT_AMBIENT_C;
        }
        return clampAmbient(6.0 + 20.0 * biomeTemperature);
    }

    static double clampAmbient(double ambientC) {
        if (!Double.isFinite(ambientC)) {
            return DEFAULT_AMBIENT_C;
        }
        return Math.max(MIN_AMBIENT_C, Math.min(MAX_AMBIENT_C, ambientC));
    }
}
