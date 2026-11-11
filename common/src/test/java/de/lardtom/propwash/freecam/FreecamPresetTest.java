package de.lardtom.propwash.freecam;

import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.creative.CreativeFlight;
import de.lardtom.propwash.creative.CreativeParam;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.BuildAnalysis;
import de.lardtom.propwash.sim.BuildWarning;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FreecamPresetTest {
    private record Band(double min, double max) {
        boolean contains(double value) {
            return value >= this.min && value <= this.max;
        }
    }

    private static Band hover(FreecamPreset preset) {
        return switch (preset) {
            case WHOOP -> new Band(45.0, 65.0);
            case THREE_FIVE -> new Band(22.0, 32.0);
            case FIVE -> new Band(18.0, 30.0);
            case SEVEN -> new Band(22.0, 34.0);
        };
    }

    private static Band topSpeed(FreecamPreset preset) {
        return switch (preset) {
            case WHOOP -> new Band(38.0, 60.0);
            case THREE_FIVE -> new Band(100.0, 130.0);
            case FIVE -> new Band(120.0, 165.0);
            case SEVEN -> new Band(95.0, 125.0);
        };
    }

    @Test
    void presetsUseTheBuiltInBlueprints() {
        assertSame(DroneBuild.WHOOP, FreecamPreset.WHOOP.build());
        assertSame(DroneBuild.FREESTYLE, FreecamPreset.FIVE.build());
        assertSame(DroneBuild.LONG_RANGE, FreecamPreset.SEVEN.build());
        assertEquals(FreecamPreset.FIVE, FreecamPreset.DEFAULT);
    }

    @Test
    void keysRoundTripAndUnknownFallsBackToFive() {
        Set<String> keys = new HashSet<>();
        for (FreecamPreset preset : FreecamPreset.values()) {
            assertTrue(keys.add(preset.key()), preset.key());
            assertSame(preset, FreecamPreset.byKey(preset.key()));
            assertSame(preset, FreecamPreset.byKey(" " + preset.key().toUpperCase() + " "));
            assertTrue(preset.translationKey().startsWith("propwash.freecam.preset."));
        }
        assertEquals(Set.of("whoop", "3_5", "5", "7"), keys);
        assertSame(FreecamPreset.FIVE, FreecamPreset.byKey(null));
        assertSame(FreecamPreset.FIVE, FreecamPreset.byKey("9"));
    }

    @Test
    void hoverThrottleAndTopSpeedStayInTheirBands() {
        for (FreecamPreset preset : FreecamPreset.values()) {
            BuildAnalysis analysis = BuildAnalysis.of(preset.build());
            assertTrue(hover(preset).contains(analysis.hoverThrottlePercent()), preset + " Schwebegas " + analysis.hoverThrottlePercent());
            assertTrue(topSpeed(preset).contains(analysis.topSpeedAcroKmh()), preset + " Spitze " + analysis.topSpeedAcroKmh());
            assertFalse(analysis.warnings().contains(BuildWarning.CANNOT_HOVER), preset + " schwebt nicht");
        }
    }

    @Test
    void threeAndAHalfInchHitsItsTargetValues() {
        DroneBuild build = FreecamPreset.THREE_FIVE.build();
        assertTrue(build.isCreative());
        assertEquals(StackType.AIO_20.key(), build.creative().base(PartKind.STACK).orElseThrow());
        assertEquals(BatteryType.B4S_650.key(), build.creative().base(PartKind.BATTERY).orElseThrow());
        assertEquals(89.0, CreativeFlight.value(build, CreativeParam.PROP_DIAMETER), 1.0E-9);
        assertEquals(3.0, CreativeFlight.value(build, CreativeParam.PROP_BLADES), 1.0E-9);
        assertEquals(3.0, CreativeFlight.value(build, CreativeParam.PROP_PITCH), 1.0E-9);
        assertEquals(2450.0, CreativeFlight.value(build, CreativeParam.MOTOR_KV), 1.0E-9);
        assertEquals(20.0, CreativeFlight.value(build, CreativeParam.MOTOR_MASS), 1.0E-9);
        assertEquals(150.0, CreativeFlight.value(build, CreativeParam.WHEELBASE), 1.0E-9);
        assertEquals(4.0, CreativeFlight.value(build, CreativeParam.BATTERY_CELLS), 1.0E-9);
        BuildAnalysis analysis = BuildAnalysis.of(build);
        assertTrue(analysis.massGrams() >= 230.0 && analysis.massGrams() <= 280.0, "Abfluggewicht " + analysis.massGrams());
        assertTrue(analysis.hoverThrottlePercent() >= 22.0 && analysis.hoverThrottlePercent() <= 32.0, "Schwebegas " + analysis.hoverThrottlePercent());
        assertTrue(analysis.topSpeedAcroKmh() >= 100.0 && analysis.topSpeedAcroKmh() <= 130.0, "Spitze " + analysis.topSpeedAcroKmh());
    }

    @Test
    void sizesGrowFromWhoopToSevenInch() {
        double previousMass = 0.0;
        double previousProp = 0.0;
        for (FreecamPreset preset : FreecamPreset.values()) {
            AirframeSpec params = AirframeSpec.of(preset.build());
            double prop = CreativeFlight.value(preset.build(), CreativeParam.PROP_DIAMETER);
            assertTrue(params.massKg > previousMass, preset + " Masse " + params.massKg);
            assertTrue(prop > previousProp, preset + " Prop " + prop);
            previousMass = params.massKg;
            previousProp = prop;
        }
    }

    @Test
    void threeAndAHalfInchDoesNotDependOnAddons() {
        DroneBuild first = FreecamPreset.THREE_FIVE.build();
        AirframeSpec a = AirframeSpec.of(first);
        AirframeSpec b = AirframeSpec.of(FreecamPreset.THREE_FIVE.build());
        assertEquals(a.massKg, b.massKg, 0.0);
        assertTrue(a.assemblable, "3,5″ lässt sich nicht zusammenbauen");
    }
}
