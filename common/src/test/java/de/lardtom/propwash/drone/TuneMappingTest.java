package de.lardtom.propwash.drone;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.sim.ItermRelaxMode;
import de.lardtom.propwash.sim.PidAutoTune;
import de.lardtom.propwash.sim.PidLoopRate;
import de.lardtom.propwash.sim.PidSource;
import de.lardtom.propwash.sim.RatesType;
import de.lardtom.propwash.sim.ThrottleLimitType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TuneMappingTest {
    private static final List<String> REQUIRED_KEYS = List.of(
            "rates.type", "rates.roll.rcRate", "rates.roll.superRate", "rates.roll.rcExpo", "rates.roll.centerRateDps",
            "rates.roll.maxRateDps", "rates.roll.actualExpo", "rates.pitch.rcRate", "rates.yaw.actualExpo", "rates.rcDeadband",
            "rates.yawDeadband", "pid.source", "pid.roll.p", "pid.roll.i", "pid.roll.d", "pid.roll.ff", "pid.pitch.ff", "pid.yaw.p",
            "pid.angleStrength", "pid.angleLimitDeg", "pid.horizonStrength", "pid.itermRelax", "pid.antiGravityGain", "pid.tpaMode",
            "pid.tpaRatePercent", "pid.tpaBreakpoint", "pid.airmode", "pid.motorIdlePercent", "pid.throttleMid", "pid.throttleExpo",
            "pid.throttleLimitType", "pid.throttleLimitPercent", "pid.thrustLinearPercent", "pid.vbatSagCompensationPercent",
            "filters.pidLoop", "filters.gyroLpfHz", "filters.dtermLpf1Hz", "filters.dtermLpf2Hz", "filters.rcSmoothingHz");

    @Test
    void keySetMatchesDocumentedSchema() {
        for (String key : REQUIRED_KEYS) {
            assertTrue(TuneMapping.keys().contains(key), key);
        }
        assertEquals(12, TuneMapping.pidAxisKeys().size());
        assertTrue(TuneMapping.isKnownKey(TuneMapping.CAMERA_UPTILT));
        assertFalse(TuneMapping.isKnownKey("rate.roll.rc"));
    }

    @Test
    void defaultsRoundTripForEveryPreset() {
        for (DroneBuild build : DroneBuild.PRESETS) {
            AirframeSpec params = AirframeSpec.of(build);
            Map<String, Float> defaults = TuneMapping.defaults(params);
            assertEquals(TuneMapping.keys().size(), defaults.size());
            assertEquals(PidSource.DERIVED.ordinal(), defaults.get(TuneMapping.PID_SOURCE));
            FlightControllerConfig expected = FlightControllerConfig.defaultsFor(params);
            assertEquals((float) expected.pid.roll.p, defaults.get("pid.roll.p"), 1.0E-4F, build.toString());
            assertEquals((float) expected.pid.yaw.i, defaults.get("pid.yaw.i"), 1.0E-4F, build.toString());
            assertTrue(TuneMapping.normalize(defaults, params).isEmpty(), build.toString());
            assertEquals(defaults, TuneMapping.fromConfig(TuneMapping.toConfig(defaults, params)));
        }
    }

    @Test
    void fullRoundTripKeepsEveryValue() {
        AirframeSpec params = AirframeSpec.of(DroneBuild.FREESTYLE);
        Map<String, Float> tune = new HashMap<>();
        tune.put("rates.type", (float) RatesType.BETAFLIGHT.ordinal());
        tune.put("rates.roll.rcRate", 1.35F);
        tune.put("rates.pitch.superRate", 0.72F);
        tune.put("rates.yaw.rcExpo", 0.2F);
        tune.put("rates.roll.centerRateDps", 150.0F);
        tune.put("rates.roll.maxRateDps", 900.0F);
        tune.put("rates.rcDeadband", 0.05F);
        tune.put("pid.source", (float) PidSource.CUSTOM.ordinal());
        tune.put("pid.roll.p", 55.0F);
        tune.put("pid.pitch.d", 40.0F);
        tune.put("pid.itermRelax", (float) ItermRelaxMode.RPY.ordinal());
        tune.put("pid.airmode", 0.0F);
        tune.put("pid.throttleLimitType", (float) ThrottleLimitType.SCALE.ordinal());
        tune.put("pid.throttleLimitPercent", 80.0F);
        tune.put("filters.pidLoop", (float) PidLoopRate.HZ_1000.ordinal());
        tune.put("filters.gyroLpfHz", 180.0F);
        FlightControllerConfig config = TuneMapping.toConfig(tune, params);
        assertEquals(RatesType.BETAFLIGHT, config.rates.type);
        assertEquals(1.35, config.rates.roll.rcRate, 1.0E-5);
        assertEquals(PidSource.CUSTOM, config.pid.source);
        assertEquals(55.0, config.pid.roll.p, 1.0E-5);
        assertEquals(ItermRelaxMode.RPY, config.pid.itermRelax);
        assertFalse(config.pid.airmode);
        assertEquals(ThrottleLimitType.SCALE, config.pid.throttleLimitType);
        assertEquals(PidLoopRate.HZ_1000, config.filters.pidLoop);
        Map<String, Float> back = TuneMapping.fromConfig(config);
        for (Map.Entry<String, Float> e : tune.entrySet()) {
            assertEquals(e.getValue(), back.get(e.getKey()), 1.0E-4F, e.getKey());
        }
        Map<String, Float> normalized = TuneMapping.normalize(tune, params);
        for (String key : TuneMapping.pidAxisKeys()) {
            assertTrue(normalized.containsKey(key), key);
        }
        assertEquals(TuneMapping.fromConfig(TuneMapping.toConfig(normalized, params)), back);
    }

    @Test
    void derivedSourceIgnoresAxisGainsAndUsesBuildDerivation() {
        AirframeSpec params = AirframeSpec.of(DroneBuild.RACER);
        Map<String, Float> tune = new HashMap<>();
        tune.put("pid.source", (float) PidSource.DERIVED.ordinal());
        tune.put("pid.roll.p", 199.0F);
        FlightControllerConfig config = TuneMapping.toConfig(tune, params);
        FlightControllerConfig derived = FlightControllerConfig.defaultsFor(params);
        assertEquals(PidSource.DERIVED, config.pid.source);
        assertEquals(derived.pid.roll.p, config.pid.roll.p, 1.0E-9);
        assertEquals(params.buildKey, config.pid.derivedForBuild);
    }

    @Test
    void derivedPidsFollowTheFilterSettings() {
        AirframeSpec params = AirframeSpec.of(DroneBuild.FREESTYLE);
        Map<String, Float> tune = Map.of("filters.pidLoop", (float) PidLoopRate.HZ_500.ordinal());
        FlightControllerConfig config = TuneMapping.toConfig(tune, params);
        FlightControllerConfig reference = new FlightControllerConfig();
        reference.filters.pidLoop = PidLoopRate.HZ_500;
        reference.pid = PidAutoTune.derive(params, reference.filters, reference.pid);
        assertEquals(reference.pid.roll.p, config.pid.roll.p, 1.0E-9);
    }

    @Test
    void changingAxisGainSwitchesToManual() {
        AirframeSpec params = AirframeSpec.of(DroneBuild.FREESTYLE);
        Map<String, Float> edited = new HashMap<>(TuneMapping.effective(Map.of(), params));
        float derivedP = edited.get("pid.pitch.p");
        edited.put("pid.pitch.p", derivedP + 5.0F);
        Map<String, Float> stored = TuneMapping.applyEdit(Map.of(), edited, params);
        assertEquals(PidSource.CUSTOM.ordinal(), stored.get(TuneMapping.PID_SOURCE));
        assertEquals(derivedP + 5.0F, stored.get("pid.pitch.p"), 1.0E-4F);
        assertTrue(stored.containsKey("pid.roll.p"));
        Map<String, Float> ratesOnly = new HashMap<>(TuneMapping.effective(stored, params));
        ratesOnly.put("rates.roll.maxRateDps", 800.0F);
        Map<String, Float> again = TuneMapping.applyEdit(stored, ratesOnly, params);
        assertEquals(PidSource.CUSTOM.ordinal(), again.get(TuneMapping.PID_SOURCE));
        assertEquals(800.0F, again.get("rates.roll.maxRateDps"), 1.0E-4F);
        Map<String, Float> backToDerived = new HashMap<>(TuneMapping.effective(again, params));
        backToDerived.put(TuneMapping.PID_SOURCE, (float) PidSource.DERIVED.ordinal());
        Map<String, Float> derived = TuneMapping.applyEdit(again, backToDerived, params);
        assertFalse(derived.containsKey(TuneMapping.PID_SOURCE));
        assertFalse(derived.containsKey("pid.pitch.p"));
        assertEquals(800.0F, derived.get("rates.roll.maxRateDps"), 1.0E-4F);
    }

    @Test
    void explicitSwitchToDerivedWinsOverChangedGains() {
        AirframeSpec params = AirframeSpec.of(DroneBuild.FREESTYLE);
        Map<String, Float> edited = new HashMap<>(TuneMapping.effective(Map.of(), params));
        edited.put("pid.roll.p", edited.get("pid.roll.p") + 7.0F);
        Map<String, Float> custom = TuneMapping.applyEdit(Map.of(), edited, params);
        assertEquals(PidSource.CUSTOM.ordinal(), custom.get(TuneMapping.PID_SOURCE));
        Map<String, Float> original = TuneMapping.effective(Map.of(), params);
        Map<String, Float> restored = TuneMapping.applyEdit(custom, original, params);
        assertFalse(restored.containsKey(TuneMapping.PID_SOURCE));
        assertFalse(restored.containsKey("pid.roll.p"));
    }

    @Test
    void editWithoutPidKeysMeansDerived() {
        AirframeSpec params = AirframeSpec.of(DroneBuild.FREESTYLE);
        Map<String, Float> edited = new HashMap<>(TuneMapping.effective(Map.of(), params));
        edited.put("pid.yaw.i", edited.get("pid.yaw.i") + 3.0F);
        Map<String, Float> custom = TuneMapping.applyEdit(Map.of(), edited, params);
        Map<String, Float> sparse = TuneMapping.applyEdit(custom, Map.of("rates.roll.maxRateDps", 800.0F), params);
        assertFalse(sparse.containsKey(TuneMapping.PID_SOURCE));
        assertFalse(sparse.containsKey("pid.yaw.i"));
        assertEquals(800.0F, sparse.get("rates.roll.maxRateDps"), 1.0E-4F);
    }

    @Test
    void unknownKeysDroppedAndValuesClamped() {
        AirframeSpec params = AirframeSpec.of(DroneBuild.WHOOP);
        Map<String, Float> edited = new HashMap<>();
        edited.put("totally.unknown", 3.0F);
        edited.put("rates.roll.rcRate", 99.0F);
        edited.put("filters.pidLoop", 17.0F);
        edited.put(TuneMapping.CAMERA_UPTILT, 200.0F);
        Map<String, Float> stored = TuneMapping.applyEdit(Map.of(), edited, params);
        assertFalse(stored.containsKey("totally.unknown"));
        assertEquals(2.55F, stored.get("rates.roll.rcRate"), 1.0E-4F);
        assertEquals((float) PidLoopRate.HZ_500.ordinal(), stored.get("filters.pidLoop"));
        assertEquals(TuneMapping.CAMERA_UPTILT_MAX, stored.get(TuneMapping.CAMERA_UPTILT));
        assertEquals(TuneMapping.CAMERA_UPTILT_MAX, TuneMapping.cameraUptilt(stored, 10.0F));
        assertEquals(10.0F, TuneMapping.cameraUptilt(Map.of(), 10.0F));
    }
}
