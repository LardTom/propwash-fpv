package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public record BuildAnalysis(
        double massGrams, double thrustToWeight, double maxThrustPerMotorGrams,
        double hoverThrottlePercent, double hoverCurrentA, double hoverFlightTimeMin, double cruiseFlightTimeMin,
        double mixedFlightTimeMin, double aggressiveFlightTimeMin, double cruiseCurrentA, double mixedCurrentA, double cruiseSpeedKmh,
        double landingCellVoltage, double avionicsPowerW,
        double fullThrottleCurrentA, double fullThrottleCellVoltage, double escLoadPercent, double batteryLoadPercent,
        double topSpeedAcroKmh, double topSpeedAngleKmh, double motorResponseMs, double rollAccelDps2,
        double crashSpeedMs, double sustainedMotorTempC, double motorTempMinuteC, double mixedMotorTempC, double propsInViewPercent,
        Set<BuildWarning> warnings) {
    static final double TOP_SPEED_SOC = 0.8;
    static final double DEFAULT_ANGLE_LIMIT = Math.toRadians(60.0);
    public static final double REFERENCE_AMBIENT_C = 25.0;
    public static final double PROPS_IN_VIEW_WARNING = 30.0;
    public static final double MOTOR_TEMP_WINDOW_S = 60.0;

    private static final ConcurrentHashMap<DroneBuild, BuildAnalysis> CACHE = new ConcurrentHashMap<>();
    private static final BoundedCache<DroneBuild, BuildAnalysis> EDITED = new BoundedCache<>(32);

    public static BuildAnalysis of(DroneBuild build) {
        if (build.edited()) {
            return EDITED.get(build, BuildAnalysis::compute);
        }
        return CACHE.computeIfAbsent(build, BuildAnalysis::compute);
    }

    static BuildAnalysis compute(DroneBuild build) {
        AirframeSpec p = AirframeSpec.of(build);
        EnumSet<BuildWarning> warnings = EnumSet.noneOf(BuildWarning.class);
        double weight = p.massKg * ModelConstants.G;

        OperatingPoints.Point full = new OperatingPoints.Point();
        OperatingPoints.uniformDuty(p, 1.0, 1.0, false, 0.0, 0.0, full);
        double maxThrust = full.thrustPerMotor;
        double tw = 4.0 * maxThrust / weight;

        OperatingPoints.Point hoverHalf = new OperatingPoints.Point();
        OperatingPoints.hover(p, 0.5, true, 0.0, true, hoverHalf);
        double hoverStick = (hoverHalf.duty - ModelConstants.IDLE_REFERENCE) / (1.0 - ModelConstants.IDLE_REFERENCE);
        OperatingPoints.Point hoverFull = new OperatingPoints.Point();
        OperatingPoints.hover(p, 1.0, true, 0.0, true, hoverFull);
        boolean canHover = tw >= 1.15 && hoverStick <= 1.0 && hoverHalf.feasible;
        if (!canHover) {
            warnings.add(BuildWarning.CANNOT_HOVER);
        }
        if (tw < 2.0) {
            warnings.add(BuildWarning.UNDERPOWERED);
        }
        if (p.motorTauPhys > 0.060) {
            warnings.add(BuildWarning.SLUGGISH_MOTORS);
        }
        double fullCurrent = EnergyModel.packCurrent(p, full);
        double fullCell = full.busVoltage / p.cells;
        if (fullCell < p.chemistry.cutoffCellVoltage) {
            warnings.add(BuildWarning.HEAVY_SAG);
        }
        if (full.phaseCurrent >= OperatingPoints.currentLimit(p, full.busVoltage, full.omega) - 1e-9) {
            warnings.add(BuildWarning.ESC_CURRENT_LIMITED);
        }
        if (full.batteryCurrent > 1.3 * p.continuousCurrent) {
            warnings.add(BuildWarning.BATTERY_OVERLOAD);
        }

        double hoverPercent = canHover ? 100.0 * hoverStick : Double.NaN;
        double hoverCurrent = canHover ? EnergyModel.packCurrent(p, hoverFull) : Double.NaN;

        double topTilt = canHover ? OperatingPoints.topSpeedTilt(p, TOP_SPEED_SOC) : 0.0;
        double topSpeed = topTilt > 0.0 ? OperatingPoints.speedAtTilt(p, topTilt) : 0.0;
        double angleTilt = Math.min(DEFAULT_ANGLE_LIMIT, topTilt);
        double angleSpeed = angleTilt > 0.0 ? OperatingPoints.speedAtTilt(p, angleTilt) : 0.0;

        EnergyModel.Flight hover = EnergyModel.Flight.NONE;
        EnergyModel.Flight cruise = EnergyModel.Flight.NONE;
        EnergyModel.Flight mixed = EnergyModel.Flight.NONE;
        EnergyModel.Flight aggressive = EnergyModel.Flight.NONE;
        double cruiseSpeed = Double.NaN;
        if (canHover) {
            EnergyModel.Points points = new EnergyModel.Points(p, topSpeed, topTilt);
            hover = EnergyModel.fly(points, EnergyModel.Profile.HOVER, REFERENCE_AMBIENT_C);
            cruise = EnergyModel.fly(points, EnergyModel.Profile.CRUISE, REFERENCE_AMBIENT_C);
            mixed = EnergyModel.fly(points, EnergyModel.Profile.MIXED, REFERENCE_AMBIENT_C);
            aggressive = EnergyModel.fly(points, EnergyModel.Profile.AGGRESSIVE, REFERENCE_AMBIENT_C);
            cruiseSpeed = points.cruiseSpeed * 3.6;
        }

        EnergyModel.Heat heat = EnergyModel.fullThrottleMotorTemp(p, REFERENCE_AMBIENT_C, MOTOR_TEMP_WINDOW_S);
        double motorTemp = heat.sustained();
        double minuteTemp = heat.minute();
        if (motorTemp > p.motorDerateStartC) {
            warnings.add(BuildWarning.MOTOR_THERMAL);
        }
        double propsInView = CameraOcclusion.percent(build);
        if (propsInView > PROPS_IN_VIEW_WARNING) {
            warnings.add(BuildWarning.PROPS_IN_VIEW);
        }
        if (p.escOvervoltage) {
            warnings.add(BuildWarning.ESC_OVERVOLTAGE);
        }

        return new BuildAnalysis(
                p.massKg * 1000.0, tw, maxThrust / ModelConstants.G * 1000.0,
                hoverPercent, hoverCurrent, hover.minutes(), cruise.minutes(), mixed.minutes(), aggressive.minutes(),
                cruise.averageCurrent(), mixed.averageCurrent(), cruiseSpeed, mixed.landingCellVoltage(), p.avionicsPower,
                fullCurrent, fullCell, 100.0 * full.phaseCurrent / build.stack().escCurrentPerMotor,
                100.0 * fullCurrent / p.continuousCurrent,
                topSpeed * 3.6, angleSpeed * 3.6, p.motorTauPhys * 1000.0, 0.5 * p.authorityRoll * SimMath.DEG,
                p.impactTolerance, motorTemp, minuteTemp, mixed.motorTempC(), propsInView, Set.copyOf(warnings));
    }
}
