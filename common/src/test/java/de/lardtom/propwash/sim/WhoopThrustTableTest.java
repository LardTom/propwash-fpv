package de.lardtom.propwash.sim;

import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class WhoopThrustTableTest {
    static final String MOTOR = """
            {"format": 1, "mass_g": 2.85, "stator": "1102", "kv": 21000, "mount": "propwash:m08", "max_current_a": 9.2,
             "resistance_ohm": 0.11, "cells": [1, 1]}""";
    static final String PROP = """
            {"format": 1, "mass_g": 0.32, "diameter_mm": 45, "pitch_in": 1.1, "blades": 3, "ct": 0.196, "cp": 0.17}""";
    static final String STACK = """
            {"format": 1, "mass_g": 4.2, "mount": "propwash:whoop_aio", "esc_continuous_a": 12.0, "esc_burst_a": 18.0,
             "esc_resistance_ohm": 0.05, "cells": [1, 1], "all_in_one": true}""";
    static final double RPM_CORRECTION = 7.0 / 6.0;
    static final double[][] TABLE = {
            {3.92, 0.30, 0.80, 16081, 9.6}, {3.91, 0.40, 1.54, 21132, 16.7}, {3.90, 0.50, 2.52, 25443, 24.8},
            {3.88, 0.60, 3.57, 29730, 34.1}, {3.87, 0.70, 4.73, 33111, 42.4}, {3.85, 0.80, 6.01, 36309, 50.2},
            {3.83, 0.90, 7.41, 39179, 58.5}, {3.81, 1.00, 9.18, 41913, 66.6}};

    static AirframeSpec stand() {
        MotorType motor = CalibrationHarness.fixture(PartKind.MOTOR, "m1102_21000", MOTOR);
        PropType prop = CalibrationHarness.fixture(PartKind.PROP, "p45_11_3b", PROP);
        StackType stack = CalibrationHarness.fixture(PartKind.STACK, "aio_1s_12a", STACK);
        return AirframeSpec.of(new DroneBuild(FrameType.WHOOP_65, stack, motor, prop, VideoType.ANALOG_NANO, BatteryType.B1S_300_HV));
    }

    @Test
    public void publishedWhoopThrustTableIsReproduced() {
        AirframeSpec p = stand();
        List<String> failures = new ArrayList<>();
        for (double[] row : TABLE) {
            double volts = row[0];
            double duty = row[1];
            double omega = OperatingPoints.motorOmega(p, duty, volts, 0.0, 1.0);
            double current = OperatingPoints.motorInputCurrent(p, volts, omega, OperatingPoints.phaseCurrent(p, duty, volts, omega));
            double grams = p.kThrust * omega * omega / ModelConstants.G * 1000.0;
            double rpm = omega * 60.0 / (2.0 * Math.PI);
            double wantedRpm = row[3] * RPM_CORRECTION;
            String line = String.format(Locale.ROOT, "%3.0f %%: Schub %.1f g (Tabelle %.1f), Drehzahl %.0f (%.0f), Strom %.2f A (%.2f)",
                    duty * 100.0, grams, row[4], rpm, wantedRpm, current, row[2]);
            System.out.println(line);
            if (duty >= 0.40 && Math.abs(grams / row[4] - 1.0) > 0.10) {
                failures.add("Schub " + line);
            }
            if (duty >= 0.40 && Math.abs(rpm / wantedRpm - 1.0) > 0.04) {
                failures.add("Drehzahl " + line);
            }
            if (duty >= 0.80 && Math.abs(current / row[2] - 1.0) > 0.10) {
                failures.add("Strom " + line);
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }
}
