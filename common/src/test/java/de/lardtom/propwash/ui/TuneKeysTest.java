package de.lardtom.propwash.ui;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.drone.TuneMapping;
import de.lardtom.propwash.sim.Axis;
import de.lardtom.propwash.sim.AirframeSpec;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TuneKeysTest {
    @Test
    void paramsCoverExactlyTheDroneTuneKeys() {
        assertEquals(Set.copyOf(TuneMapping.keys()), TuneKeys.params().keySet());
        assertEquals(Set.copyOf(TuneMapping.pidAxisKeys()), Set.copyOf(TuneKeys.pidTermKeys()));
        assertEquals(TuneMapping.PID_SOURCE, TuneKeys.PID_SOURCE);
    }

    @Test
    void defaultsOfEveryPresetLieInsideTheParamRanges() {
        for (DroneBuild build : DroneBuild.PRESETS) {
            Map<String, Float> defaults = TuneMapping.defaults(AirframeSpec.of(build));
            for (Map.Entry<String, TuneParam> e : TuneKeys.params().entrySet()) {
                TuneParam p = e.getValue();
                double v = defaults.get(e.getKey());
                boolean inside = p.isOff(v) || (v >= p.min() - 1e-6 && v <= p.max() + 1e-6);
                assertTrue(inside, build + " " + e.getKey() + " = " + v);
            }
        }
    }

    @Test
    void containsTheDocumentedKeys() {
        for (String key : new String[]{"rates.type", "rates.roll.rcRate", "rates.yaw.actualExpo", "rates.rcDeadband", "pid.source",
                "pid.pitch.ff", "pid.airmode", "pid.throttleLimitType", "filters.pidLoop", "filters.rcSmoothingHz"}) {
            assertTrue(TuneKeys.params().containsKey(key), key);
        }
        assertEquals(12, TuneKeys.pidTermKeys().size());
        assertTrue(TuneKeys.isPidTermKey("pid.yaw.d"));
        assertFalse(TuneKeys.isPidTermKey("pid.angleStrength"));
        assertFalse(TuneKeys.isPidTermKey("pid.roll.x"));
    }

    @Test
    void paramsSanitizeAndStep() {
        TuneParam gyro = TuneKeys.param(TuneKeys.GYRO_LPF);
        assertEquals(0.0, gyro.sanitize(3));
        assertEquals(20.0, gyro.sanitize(18));
        assertEquals(255.0, gyro.sanitize(253));
        assertEquals(0.0, gyro.stepBy(20, -1));
        assertEquals(20.0, gyro.stepBy(0, 1));
        assertTrue(gyro.isOff(0));
        TuneParam rc = TuneKeys.param(TuneKeys.rateKey(Axis.ROLL, "rcRate"));
        assertEquals(1.23, rc.sanitize(1.2345), 1e-9);
        assertEquals(2.55, rc.sanitize(9));
        assertEquals(1.24, rc.stepBy(1.23, 1), 1e-9);
        TuneParam deadband = TuneKeys.param(TuneKeys.RC_DEADBAND);
        assertEquals("1.5", deadband.formatNumber(0.015));
        TuneParam type = TuneKeys.param(TuneKeys.RATES_TYPE);
        assertEquals(1.0, type.sanitize(7));
        assertEquals(2, type.constants().size());
    }
}
