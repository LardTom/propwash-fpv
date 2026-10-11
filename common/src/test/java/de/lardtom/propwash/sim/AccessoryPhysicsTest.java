package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessoryPhysicsTest {
    private static final DroneBuild BARE = DroneBuild.FREESTYLE;
    private static final DroneBuild LIGHT = BARE.withAccessory(AccessoryType.ACTION_CAM_LIGHT);
    private static final DroneBuild HEAVY = BARE.withAccessory(AccessoryType.ACTION_CAM_HEAVY);
    private static final DroneBuild LEDS = BARE.withAccessory(AccessoryType.LED_STRIP);

    @Test
    void massIncludesAccessories() {
        AirframeSpec bare = AirframeSpec.of(BARE);
        AirframeSpec heavy = AirframeSpec.of(HEAVY);
        assertEquals(bare.massKg + 0.150, heavy.massKg, 1.0E-9);
        assertEquals(0.150, heavy.accessoryMassKg, 1.0E-9);
        assertEquals(BuildAnalysis.of(BARE).massGrams() + 150.0, BuildAnalysis.of(HEAVY).massGrams(), 1.0E-6);
        assertTrue(heavy.buildKey.endsWith("/top=action_cam_heavy"), heavy.buildKey);
        assertEquals(bare.buildKey, AirframeSpec.of(BARE.withoutAccessories()).buildKey);
    }

    @Test
    void cameraHighUpAddsPitchAndRollInertia() {
        AirframeSpec bare = AirframeSpec.of(BARE);
        AirframeSpec light = AirframeSpec.of(LIGHT);
        AirframeSpec heavy = AirframeSpec.of(HEAVY);
        assertTrue(heavy.comHeight > light.comHeight && light.comHeight > bare.comHeight, "Schwerpunkt wandert nach oben");
        assertTrue(heavy.inertiaXx > light.inertiaXx && light.inertiaXx > bare.inertiaXx, "Nicken");
        assertTrue(heavy.inertiaZz > light.inertiaZz && light.inertiaZz > bare.inertiaZz, "Rollen");
        assertTrue(heavy.inertiaYy > bare.inertiaYy, "Gieren");
        double lumped = 0.150 * sq(heavy.comHeight - bare.comHeight);
        assertTrue(heavy.inertiaZz - bare.inertiaZz > 5.0 * lumped, "Kamera sitzt weit über dem Schwerpunkt");
        assertTrue(heavy.authorityRoll < 0.97 * bare.authorityRoll, heavy.authorityRoll + " vs " + bare.authorityRoll);
        assertTrue(heavy.authorityPitch < 0.9 * bare.authorityPitch, heavy.authorityPitch + " vs " + bare.authorityPitch);
        assertTrue(light.authorityPitch < bare.authorityPitch && light.authorityPitch > heavy.authorityPitch);
    }

    @Test
    void heavyCameraFeelsSlowerInTheAir() {
        double[] bare = FlightCharacterization.rateStep(BARE, 144.0, 1, 1.0, FlightCharacterization.ROLL_TARGET_DPS, 0.6);
        double[] heavy = FlightCharacterization.rateStep(HEAVY, 144.0, 1, 1.0, FlightCharacterization.ROLL_TARGET_DPS, 0.6);
        assertTrue(heavy[4] < 0.95 * bare[4], "Nickbeschleunigung " + heavy[4] + " vs " + bare[4]);
        BuildAnalysis a = BuildAnalysis.of(BARE);
        BuildAnalysis b = BuildAnalysis.of(HEAVY);
        assertTrue(b.hoverThrottlePercent() > a.hoverThrottlePercent());
        assertTrue(b.thrustToWeight() < a.thrustToWeight());
        assertTrue(b.cruiseFlightTimeMin() < a.cruiseFlightTimeMin());
        assertTrue(b.topSpeedAcroKmh() < a.topSpeedAcroKmh(), "mehr Stirnfläche und Gewicht");
        assertTrue(b.rollAccelDps2() < 0.97 * a.rollAccelDps2());
        assertTrue(b.thrustToWeight() < 0.85 * a.thrustToWeight(), b.thrustToWeight() + " vs " + a.thrustToWeight());
    }

    @Test
    void lightCameraAndLedsDrawFromTheFlightBattery() {
        AirframeSpec bare = AirframeSpec.of(BARE);
        assertEquals(bare.auxPower + AccessoryType.ACTION_CAM_LIGHT.flightBatteryWatts, AirframeSpec.of(LIGHT).auxPower, 1.0E-9);
        assertEquals(bare.auxPower + AccessoryType.LED_STRIP.flightBatteryWatts, AirframeSpec.of(LEDS).auxPower, 1.0E-9);
        assertEquals(bare.auxPower, AirframeSpec.of(HEAVY).auxPower, 1.0E-9);
        assertTrue(BuildAnalysis.of(LIGHT).hoverFlightTimeMin() < BuildAnalysis.of(BARE).hoverFlightTimeMin());
    }

    @Test
    void cameraBecomesTheTopOfTheHull() {
        AirframeSpec bare = AirframeSpec.of(BARE);
        AirframeSpec heavy = AirframeSpec.of(HEAVY);
        assertTrue(heavy.hullY(8) > bare.hullY(8) + 0.01, "Kamera ragt über den Akku");
        assertTrue(heavy.hullZ(8) > 0.0, "Kamera sitzt vorn");
        assertEquals(bare.hullY(9) - (heavy.comHeight - bare.comHeight), heavy.hullY(9), 1.0E-9);
        assertTrue(heavy.hitboxHeight > bare.hitboxHeight);
        assertEquals(bare.hullY(11) - (AirframeSpec.of(LEDS).comHeight - bare.comHeight), AirframeSpec.of(LEDS).hullY(11), 1.0E-9);
    }

    @Test
    void everyAccessoryOnEveryFrameDerives() {
        for (DroneBuild preset : DroneBuild.PRESETS) {
            for (AccessoryType type : AccessoryType.values()) {
                AirframeSpec spec = AirframeSpec.of(preset.withAccessory(type));
                assertTrue(Double.isFinite(spec.inertiaXx) && spec.inertiaXx > 0.0, preset + " " + type);
                assertTrue(Double.isFinite(spec.hitboxHeight) && spec.hitboxHeight > 0.0, preset + " " + type);
                BuildAnalysis analysis = BuildAnalysis.of(preset.withAccessory(type));
                assertTrue(Double.isFinite(analysis.massGrams()));
            }
        }
    }

    private static double sq(double v) {
        return v * v;
    }
}
