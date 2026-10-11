package de.lardtom.propwash.sim;

import java.util.concurrent.ConcurrentHashMap;

public final class PidAutoTune {
    static final double P_SCALE = 0.032029e-3;
    static final double I_SCALE = 0.244381e-3;
    static final double D_SCALE = 0.000529e-3;
    static final double MAX_CROSSOVER = 150.0;
    static final double DEAD_TIME_FACTOR = 0.40;
    static final double MOTOR_FACTOR = 4.0;
    static final double DEFAULT_FF = 120.0;
    static final double RELAX_REFERENCE_HZ = 15.0;

    private static final ConcurrentHashMap<Key, Derived> CACHE = new ConcurrentHashMap<>();

    private PidAutoTune() {
    }

    private record Key(String buildKey, PidLoopRate loop, double gyroLpfHz) {
    }

    record Derived(AxisPid roll, AxisPid pitch, AxisPid yaw, double angleStrength, double tpaBreakpoint,
                   double airmodeStartPercent, double itermRelaxCutoffHz, double crossover) {
    }

    public static String filtersKey(FilterSettings filters) {
        FilterSettings f = filters == null ? new FilterSettings() : filters;
        PidLoopRate loop = f.pidLoop == null ? PidLoopRate.HZ_2000 : f.pidLoop;
        return loop.name() + "@" + f.gyroLpfHz;
    }

    public static boolean isCurrent(AirframeSpec params, FilterSettings filters, PidProfile pid) {
        return pid != null && params.buildKey.equals(pid.derivedForBuild) && filtersKey(filters).equals(pid.derivedForFilters);
    }

    public static PidProfile derive(AirframeSpec params, FilterSettings filters, PidProfile base) {
        FilterSettings f = filters == null ? new FilterSettings() : filters;
        PidLoopRate loop = f.pidLoop == null ? PidLoopRate.HZ_2000 : f.pidLoop;
        Derived v = CACHE.computeIfAbsent(new Key(params.buildKey, loop, f.gyroLpfHz), k -> compute(params, k.loop(), k.gyroLpfHz()));
        PidProfile out = base == null ? new PidProfile() : base.copy();
        out.roll = v.roll().copy();
        out.pitch = v.pitch().copy();
        out.yaw = v.yaw().copy();
        out.angleStrength = v.angleStrength();
        out.tpaBreakpoint = v.tpaBreakpoint();
        out.airmodeStartThrottlePercent = v.airmodeStartPercent();
        out.itermRelaxCutoffHz = v.itermRelaxCutoffHz();
        out.source = PidSource.DERIVED;
        out.derivedForBuild = params.buildKey;
        out.derivedForFilters = filtersKey(f);
        return out;
    }

    static double deadTime(PidLoopRate loop, double gyroLpfHz) {
        double gyro = gyroLpfHz > 0.0 ? 1.0 / (SimMath.TWO_PI * gyroLpfHz) : 0.0;
        return 1.5 / loop.hz() + 0.5 / DroneSim.PHYSICS_HZ + gyro;
    }

    static double crossover(AirframeSpec params, PidLoopRate loop, double gyroLpfHz) {
        return Math.min(MAX_CROSSOVER, Math.min(DEAD_TIME_FACTOR / deadTime(loop, gyroLpfHz), MOTOR_FACTOR / params.motorTau));
    }

    static Derived compute(AirframeSpec params, PidLoopRate loop, double gyroLpfHz) {
        double wc = crossover(params, loop, gyroLpfHz);
        AxisPid roll = rollPitchRate(wc, params.authorityRoll, params.motorTau);
        AxisPid pitch = rollPitchRate(wc, params.authorityPitch, params.motorTau);
        double kpYaw = Math.min(2.0 * wc / params.authorityYawInstant, 40.0 / params.authorityYawSteady);
        AxisPid yaw = new AxisPid(toP(kpYaw), toI(10.0 * kpYaw), 0.0, DEFAULT_FF);
        double angleStrength = SimMath.clamp(Math.round(10.0 * wc / 14.0), 20.0, 120.0);
        double hoverHalf = OperatingPoints.hoverStick(params, 0.5);
        double hoverFull = OperatingPoints.hoverStick(params, 1.0);
        double tpa = Math.min(0.9, Math.max(0.35, hoverHalf + 0.15));
        if (!Double.isFinite(tpa)) {
            tpa = 0.9;
        }
        tpa = Math.round(tpa * 100.0) / 100.0;
        double airmode = Double.isFinite(hoverFull) ? SimMath.clamp(Math.round(90.0 * hoverFull), 10.0, 25.0) : 25.0;
        double ratio = wc / MAX_CROSSOVER;
        double relax = SimMath.clamp(Math.round(RELAX_REFERENCE_HZ * ratio * ratio), 3.0, RELAX_REFERENCE_HZ);
        return new Derived(roll, pitch, yaw, angleStrength, tpa, airmode, relax, wc);
    }

    private static AxisPid rollPitchRate(double wc, double authority, double tau) {
        double kp = wc / authority;
        double kd = kp * Math.min(tau, 0.040);
        double ki = kp * SimMath.clamp(0.06 * wc, 4.0, 12.0);
        return new AxisPid(toP(kp), toI(ki), toD(kd), DEFAULT_FF);
    }

    private static double toP(double kp) {
        return SimMath.clamp(Math.round(kp / (P_SCALE * SimMath.DEG)), 5.0, 200.0);
    }

    private static double toI(double ki) {
        return SimMath.clamp(Math.round(ki / (I_SCALE * SimMath.DEG)), 5.0, 250.0);
    }

    private static double toD(double kd) {
        return SimMath.clamp(Math.round(kd / (D_SCALE * SimMath.DEG)), 0.0, 150.0);
    }
}
