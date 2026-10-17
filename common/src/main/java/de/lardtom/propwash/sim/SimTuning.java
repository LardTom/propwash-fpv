package de.lardtom.propwash.sim;

public final class SimTuning {
    public static final double GRAVITY_MIN = 0.0;
    public static final double GRAVITY_MAX = 10.0;
    public static final double GRAVITY_REFERENCE_MIN = 0.01;
    public static final double BATTERY_SCALE_MIN = 0.01;
    public static final double BATTERY_SCALE_MAX = 10.0;

    public boolean propWash = true;
    public double propWashScale = 1.0;
    public double turbulenceScale = 1.0;
    public boolean wakeField = true;
    public boolean groundEffect = true;
    public double groundEffectScale = 1.0;
    public double translationalLiftScale = 1.0;
    public double dragScale = 1.0;
    public double windScale = 1.0;
    public boolean batterySag = true;
    public boolean infiniteBattery = false;
    public double batteryScale = 1.0;
    public boolean crashDisarm = true;
    public double crashSpeedScale = 1.0;
    public double restitutionScale = 1.0;
    public boolean propStrikes = true;
    public boolean thermalEffects = false;
    public boolean thermalLimits = true;
    public boolean escOvervoltage = true;
    public boolean sleepEnabled = true;
    public double gyroNoiseDps = 0.2;
    public double gyroVibrationDps = 1.0;
    public double gravityScale = 1.0;
    public boolean partCompatibility = true;

    public SimTuning() {
    }

    public SimTuning copy() {
        SimTuning c = new SimTuning();
        c.propWash = this.propWash;
        c.propWashScale = this.propWashScale;
        c.turbulenceScale = this.turbulenceScale;
        c.wakeField = this.wakeField;
        c.groundEffect = this.groundEffect;
        c.groundEffectScale = this.groundEffectScale;
        c.translationalLiftScale = this.translationalLiftScale;
        c.dragScale = this.dragScale;
        c.windScale = this.windScale;
        c.batterySag = this.batterySag;
        c.infiniteBattery = this.infiniteBattery;
        c.batteryScale = this.batteryScale;
        c.crashDisarm = this.crashDisarm;
        c.crashSpeedScale = this.crashSpeedScale;
        c.restitutionScale = this.restitutionScale;
        c.propStrikes = this.propStrikes;
        c.thermalEffects = this.thermalEffects;
        c.thermalLimits = this.thermalLimits;
        c.escOvervoltage = this.escOvervoltage;
        c.sleepEnabled = this.sleepEnabled;
        c.gyroNoiseDps = this.gyroNoiseDps;
        c.gyroVibrationDps = this.gyroVibrationDps;
        c.gravityScale = this.gravityScale;
        c.partCompatibility = this.partCompatibility;
        return c;
    }

    public void sanitize() {
        this.propWashScale = SimMath.sanitize(this.propWashScale, 0, 4, 1.0);
        this.turbulenceScale = SimMath.sanitize(this.turbulenceScale, 0, 4, 1.0);
        this.groundEffectScale = SimMath.sanitize(this.groundEffectScale, 0, 4, 1.0);
        this.translationalLiftScale = SimMath.sanitize(this.translationalLiftScale, 0, 4, 1.0);
        this.dragScale = SimMath.sanitize(this.dragScale, 0, 4, 1.0);
        this.windScale = SimMath.sanitize(this.windScale, 0, 4, 1.0);
        this.crashSpeedScale = SimMath.sanitize(this.crashSpeedScale, 0.25, 10, 1.0);
        this.restitutionScale = SimMath.sanitize(this.restitutionScale, 0, 4, 1.0);
        this.gyroNoiseDps = SimMath.sanitize(this.gyroNoiseDps, 0, 20, 0.2);
        this.gyroVibrationDps = SimMath.sanitize(this.gyroVibrationDps, 0, 20, 1.0);
        this.gravityScale = SimMath.sanitize(this.gravityScale, GRAVITY_MIN, GRAVITY_MAX, 1.0);
        this.batteryScale = SimMath.sanitize(this.batteryScale, BATTERY_SCALE_MIN, BATTERY_SCALE_MAX, 1.0);
    }

    public double gravity() {
        return ModelConstants.G * this.gravityScale;
    }

    public static double referenceGravity(double gravity) {
        return Math.max(gravity, ModelConstants.G * GRAVITY_REFERENCE_MIN);
    }
}
