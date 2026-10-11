package de.lardtom.propwash.ui;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.drone.TuneMapping;
import de.lardtom.propwash.drone.led.LedSettings;
import de.lardtom.propwash.sim.Axis;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.sim.PidAutoTune;
import de.lardtom.propwash.sim.PidLoopRate;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TuneEditsTest {
    private final Map<String, Float> defaults = TuneDefaults.compute(DroneBuild.FREESTYLE, Map.of());

    @Test
    void defaultsMatchTheFlightModelDerivation() {
        FlightControllerConfig expected = FlightControllerConfig.defaultsFor(AirframeSpec.of(DroneBuild.FREESTYLE));
        assertEquals((float) expected.pid.roll.p, this.defaults.get("pid.roll.p"));
        assertEquals((float) expected.pid.yaw.i, this.defaults.get("pid.yaw.i"));
        assertEquals((float) expected.pid.angleStrength, this.defaults.get(TuneKeys.ANGLE_STRENGTH));
        assertEquals(TuneEdits.PID_DERIVED, this.defaults.get(TuneKeys.PID_SOURCE));
        assertEquals(TuneKeys.params().size(), this.defaults.size());
    }

    @Test
    void derivedDefaultsFollowTheFilterSettings() {
        Map<String, Float> slowLoop = TuneDefaults.compute(DroneBuild.FREESTYLE, Map.of(TuneKeys.PID_LOOP, (float) PidLoopRate.HZ_500.ordinal()));
        FlightControllerConfig c = new FlightControllerConfig();
        c.filters.pidLoop = PidLoopRate.HZ_500;
        double expected = PidAutoTune.derive(AirframeSpec.of(DroneBuild.FREESTYLE), c.filters, c.pid).roll.p;
        assertEquals((float) expected, slowLoop.get("pid.roll.p"));
        assertTrue(TuneDefaults.affectsDefaults(TuneKeys.GYRO_LPF));
        assertFalse(TuneDefaults.affectsDefaults(TuneKeys.ANGLE_LIMIT));
        assertEquals(this.defaults.get(TuneKeys.PID_LOOP), slowLoop.get(TuneKeys.PID_LOOP));
    }

    @Test
    void defaultsComeFromTheDroneMapping() {
        Map<String, Float> gyro = Map.of(TuneKeys.GYRO_LPF, 90.0F);
        Map<String, Float> computed = TuneDefaults.compute(DroneBuild.RACER, gyro);
        Map<String, Float> mapped = TuneMapping.effective(gyro, AirframeSpec.of(DroneBuild.RACER));
        for (String key : TuneKeys.pidTermKeys()) {
            assertEquals(mapped.get(key), computed.get(key), key);
        }
        assertEquals(TuneMapping.defaults(AirframeSpec.of(DroneBuild.RACER)).get(TuneKeys.GYRO_LPF), computed.get(TuneKeys.GYRO_LPF));
        assertEquals(Set.copyOf(TuneMapping.keys()), computed.keySet());
    }

    @Test
    void providedDefaultsOverrideAllButDerivedKeys() {
        Map<String, Float> merged = TuneDefaults.merge(this.defaults, Map.of(TuneKeys.ANGLE_LIMIT, 55.0F, "pid.roll.p", 1.0F, "extra.key", 3.0F));
        assertEquals(55.0F, merged.get(TuneKeys.ANGLE_LIMIT));
        assertEquals(this.defaults.get("pid.roll.p"), merged.get("pid.roll.p"));
        assertEquals(3.0F, merged.get("extra.key"));
    }

    @Test
    void editingAPidTermSwitchesToManualAndPinsAllTerms() {
        Map<String, Float> tune = TuneEdits.set(Map.of(), this.defaults, "pid.roll.p", 60);
        assertEquals(TuneEdits.PID_MANUAL, tune.get(TuneKeys.PID_SOURCE));
        assertEquals(60.0F, tune.get("pid.roll.p"));
        for (String key : TuneKeys.pidTermKeys()) {
            assertTrue(tune.containsKey(key), key);
            if (!key.equals("pid.roll.p")) {
                assertEquals(this.defaults.get(key), tune.get(key), key);
            }
        }
        assertTrue(TuneEdits.manualPid(tune, this.defaults));

        Map<String, Float> again = TuneEdits.set(tune, this.defaults, "pid.yaw.d", 5);
        assertEquals(60.0F, again.get("pid.roll.p"));
        assertEquals(5.0F, again.get("pid.yaw.d"));

        Map<String, Float> derived = TuneEdits.resetPidToDerived(again);
        assertFalse(TuneEdits.manualPid(derived, this.defaults));
        for (String key : TuneKeys.pidTermKeys()) {
            assertFalse(derived.containsKey(key), key);
        }
        assertEquals(this.defaults.get("pid.roll.p").doubleValue(), TuneEdits.value(derived, this.defaults, "pid.roll.p"));
    }

    @Test
    void settingTheSourceKeyBehavesLikeTheButtons() {
        Map<String, Float> manual = TuneEdits.set(Map.of(), this.defaults, TuneKeys.PID_SOURCE, TuneEdits.PID_MANUAL);
        assertTrue(TuneEdits.manualPid(manual, this.defaults));
        assertEquals(this.defaults.get("pid.pitch.i"), manual.get("pid.pitch.i"));
        Map<String, Float> back = TuneEdits.set(manual, this.defaults, TuneKeys.PID_SOURCE, TuneEdits.PID_DERIVED);
        assertTrue(back.isEmpty());
    }

    @Test
    void valuesEqualToTheDefaultAreNotStored() {
        Map<String, Float> tune = TuneEdits.set(Map.of(), this.defaults, TuneKeys.ANGLE_LIMIT, 45);
        assertEquals(45.0F, tune.get(TuneKeys.ANGLE_LIMIT));
        assertTrue(TuneEdits.isModified(tune, this.defaults, TuneKeys.ANGLE_LIMIT));
        Map<String, Float> back = TuneEdits.set(tune, this.defaults, TuneKeys.ANGLE_LIMIT, this.defaults.get(TuneKeys.ANGLE_LIMIT));
        assertFalse(back.containsKey(TuneKeys.ANGLE_LIMIT));
        Map<String, Float> reset = TuneEdits.reset(tune, this.defaults, TuneKeys.ANGLE_LIMIT);
        assertTrue(reset.isEmpty());
    }

    @Test
    void valuesAreClampedAndSnapped() {
        Map<String, Float> tune = TuneEdits.set(Map.of(), this.defaults, TuneKeys.ANGLE_LIMIT, 500);
        assertEquals(85.0F, tune.get(TuneKeys.ANGLE_LIMIT));
        tune = TuneEdits.set(tune, this.defaults, TuneKeys.MOTOR_IDLE, 4.349);
        assertEquals(4.3F, tune.get(TuneKeys.MOTOR_IDLE), 1e-5F);
        tune = TuneEdits.set(tune, this.defaults, TuneKeys.DTERM_LPF2, 7);
        assertFalse(tune.containsKey(TuneKeys.DTERM_LPF2));
        assertEquals(tune, TuneEdits.set(tune, this.defaults, TuneKeys.ANGLE_LIMIT, Double.NaN));
    }

    @Test
    void centerAndMaxRateStayOrdered() {
        String center = TuneKeys.rateKey(Axis.ROLL, "centerRateDps");
        String max = TuneKeys.rateKey(Axis.ROLL, "maxRateDps");
        Map<String, Float> tune = TuneEdits.set(Map.of(), this.defaults, center, 900);
        assertEquals(900.0F, tune.get(center));
        assertEquals(900.0F, tune.get(max));
        tune = TuneEdits.set(tune, this.defaults, max, 400);
        assertEquals(400.0F, tune.get(max));
        assertEquals(400.0F, tune.get(center));
    }

    @Test
    void stepMovesByTheParamStep() {
        Map<String, Float> tune = TuneEdits.step(Map.of(), this.defaults, TuneKeys.ANTI_GRAVITY, 3);
        assertEquals(this.defaults.get(TuneKeys.ANTI_GRAVITY) + 3.0F, tune.get(TuneKeys.ANTI_GRAVITY));
        Map<String, Float> pid = TuneEdits.step(Map.of(), this.defaults, "pid.pitch.d", -2);
        assertEquals(this.defaults.get("pid.pitch.d") - 2.0F, pid.get("pid.pitch.d"));
        assertTrue(TuneEdits.manualPid(pid, this.defaults));
    }

    @Test
    void templateOnlyCountsRealTuningAndKeepsTheCameraUptilt() {
        assertFalse(TuneEdits.hasOwnTuning(Map.of()));
        assertFalse(TuneEdits.hasOwnTuning(Map.of(TuneMapping.CAMERA_UPTILT, 30.0F)));
        assertTrue(TuneEdits.hasOwnTuning(Map.of(TuneMapping.CAMERA_UPTILT, 30.0F, TuneKeys.ANGLE_LIMIT, 50.0F)));
        Map<String, Float> template = Map.of(TuneKeys.ANGLE_LIMIT, 40.0F);
        Map<String, Float> applied = TuneEdits.templateFor(template, Map.of(TuneMapping.CAMERA_UPTILT, 25.0F));
        assertEquals(40.0F, applied.get(TuneKeys.ANGLE_LIMIT));
        assertEquals(25.0F, applied.get(TuneMapping.CAMERA_UPTILT));
        assertEquals(template, TuneEdits.templateFor(template, Map.of()));
    }

    @Test
    void ledSettingsAreDroneSettingsNotTuning() {
        assertFalse(TuneEdits.hasOwnTuning(Map.of(LedSettings.COLOR, 3.0F, LedSettings.MODE, 1.0F)));
        Map<String, Float> template = Map.of(TuneKeys.ANGLE_LIMIT, 40.0F);
        Map<String, Float> applied = TuneEdits.templateFor(template, Map.of(LedSettings.COLOR, 3.0F));
        assertEquals(3.0F, applied.get(LedSettings.COLOR));
        assertEquals(40.0F, applied.get(TuneKeys.ANGLE_LIMIT));
    }

    @Test
    void resetWhereAndCleanKeepForeignKeys() {
        Map<String, Float> tune = TuneEdits.set(Map.of("osd.something", 2.0F), this.defaults, TuneKeys.ANGLE_LIMIT, 50);
        tune = TuneEdits.set(tune, this.defaults, TuneKeys.rateKey(Axis.YAW, "actualExpo"), 0.4);
        Map<String, Float> noRates = TuneEdits.resetWhere(tune, k -> k.startsWith("rates."));
        assertTrue(noRates.containsKey(TuneKeys.ANGLE_LIMIT));
        assertTrue(noRates.containsKey("osd.something"));
        assertFalse(noRates.containsKey(TuneKeys.rateKey(Axis.YAW, "actualExpo")));

        Map<String, Float> dirty = new HashMap<>(tune);
        dirty.put("pid.roll.p", 300.0F);
        dirty.put(TuneKeys.ANGLE_LIMIT, 1.0F);
        Map<String, Float> cleaned = TuneEdits.clean(dirty, this.defaults);
        assertFalse(cleaned.containsKey("pid.roll.p"));
        assertEquals(10.0F, cleaned.get(TuneKeys.ANGLE_LIMIT));
        assertEquals(2.0F, cleaned.get("osd.something"));

        dirty.put(TuneKeys.PID_SOURCE, TuneEdits.PID_MANUAL);
        Map<String, Float> manual = TuneEdits.clean(dirty, this.defaults);
        assertEquals(250.0F, manual.get("pid.roll.p"));
        assertEquals(this.defaults.get("pid.yaw.ff"), manual.get("pid.yaw.ff"));
        assertNotNull(manual.get(TuneKeys.PID_SOURCE));
    }
}
