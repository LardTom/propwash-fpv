package de.lardtom.propwash.api.build;

import java.util.List;
import java.util.Objects;

public record BuildStats(double massGrams, double thrustToWeight, double hoverThrottlePercent, double hoverFlightTimeMin,
                         double cruiseFlightTimeMin, double topSpeedKmh, double fullThrottleCurrentA, double escLoadPercent,
                         double batteryLoadPercent, double motorResponseMs, double crashSpeedMs, double sustainedMotorTempC,
                         double propsInViewPercent, List<String> warnings, double mixedFlightTimeMin, double aggressiveFlightTimeMin,
                         double hoverCurrentA, double cruiseCurrentA, double mixedCurrentA, double cruiseSpeedKmh, double landingCellVoltage,
                         double avionicsPowerW, double motorTempMinuteC, double mixedMotorTempC, double propwashNoise,
                         double fullThrottleCellVoltage, double rollAuthorityRadS2, double pitchAuthorityRadS2, double yawAuthorityRadS2) {
    public static final List<String> WARNINGS = List.of("cannot_hover", "underpowered", "sluggish_motors", "heavy_sag", "esc_current_limited",
            "battery_overload", "motor_thermal", "props_in_view", "esc_overvoltage");

    public BuildStats {
        warnings = List.copyOf(Objects.requireNonNullElse(warnings, List.of()));
    }

    public BuildStats(double massGrams, double thrustToWeight, double hoverThrottlePercent, double hoverFlightTimeMin,
                      double cruiseFlightTimeMin, double topSpeedKmh, double fullThrottleCurrentA, double escLoadPercent,
                      double batteryLoadPercent, double motorResponseMs, double crashSpeedMs, double sustainedMotorTempC,
                      double propsInViewPercent, List<String> warnings, double mixedFlightTimeMin, double aggressiveFlightTimeMin,
                      double hoverCurrentA, double cruiseCurrentA, double mixedCurrentA, double cruiseSpeedKmh, double landingCellVoltage,
                      double avionicsPowerW, double motorTempMinuteC, double mixedMotorTempC) {
        this(massGrams, thrustToWeight, hoverThrottlePercent, hoverFlightTimeMin, cruiseFlightTimeMin, topSpeedKmh, fullThrottleCurrentA,
                escLoadPercent, batteryLoadPercent, motorResponseMs, crashSpeedMs, sustainedMotorTempC, propsInViewPercent, warnings,
                mixedFlightTimeMin, aggressiveFlightTimeMin, hoverCurrentA, cruiseCurrentA, mixedCurrentA, cruiseSpeedKmh, landingCellVoltage,
                avionicsPowerW, motorTempMinuteC, mixedMotorTempC, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
    }

    public BuildStats(double massGrams, double thrustToWeight, double hoverThrottlePercent, double hoverFlightTimeMin,
                      double cruiseFlightTimeMin, double topSpeedKmh, double fullThrottleCurrentA, double escLoadPercent,
                      double batteryLoadPercent, double motorResponseMs, double crashSpeedMs, double sustainedMotorTempC,
                      double propsInViewPercent, List<String> warnings) {
        this(massGrams, thrustToWeight, hoverThrottlePercent, hoverFlightTimeMin, cruiseFlightTimeMin, topSpeedKmh, fullThrottleCurrentA,
                escLoadPercent, batteryLoadPercent, motorResponseMs, crashSpeedMs, sustainedMotorTempC, propsInViewPercent, warnings,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
    }

    public static String warningTranslationKey(String warning) {
        return "propwash.content.workbench.warning." + warning;
    }
}
