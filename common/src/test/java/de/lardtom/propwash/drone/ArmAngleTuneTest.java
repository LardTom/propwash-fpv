package de.lardtom.propwash.drone;

import de.lardtom.propwash.api.build.TuneGroup;
import de.lardtom.propwash.api.build.TuneSpec;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.ui.TuneKeys;
import de.lardtom.propwash.ui.TuneParam;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArmAngleTuneTest {
    private static final String KEY = TuneMapping.ARM_MAX_ANGLE;
    private static final AirframeSpec PARAMS = AirframeSpec.of(DroneBuild.FREESTYLE);

    @Test
    void keyIsPartOfTheTuneWithBetaflightRange() {
        assertEquals("pid.armMaxAngleDeg", KEY);
        assertTrue(TuneMapping.keys().contains(KEY));
        assertEquals(25.0F, TuneMapping.defaults(PARAMS).get(KEY));
        TuneParam param = TuneKeys.param(KEY);
        assertNotNull(param);
        assertEquals(0.0, param.min());
        assertEquals(180.0, param.max());
        assertEquals(1.0, param.step());
        assertEquals("°", param.unit());
    }

    @Test
    void storedValueReachesTheFlightController() {
        assertEquals(180.0, TuneMapping.toConfig(Map.of(KEY, 180.0F), PARAMS).armMaxTiltDeg);
        assertEquals(180.0, TuneMapping.toConfig(Map.of(KEY, 999.0F), PARAMS).armMaxTiltDeg);
        assertEquals(0.0, TuneMapping.toConfig(Map.of(KEY, -3.0F), PARAMS).armMaxTiltDeg);
        assertEquals(FlightControllerConfig.ARM_ANGLE_DEFAULT, TuneMapping.toConfig(Map.of(), PARAMS).armMaxTiltDeg);
    }

    @Test
    void onlyChangedAnglesAreStored() {
        assertFalse(TuneMapping.normalize(Map.of(KEY, 25.0F), PARAMS).containsKey(KEY));
        assertEquals(180.0F, TuneMapping.normalize(Map.of(KEY, 180.0F), PARAMS).get(KEY));
        assertEquals(180.0F, TuneMapping.normalize(Map.of(KEY, 250.0F), PARAMS).get(KEY));
    }

    @Test
    void sharecodesAndDefaultImportsCarryTheAngle() {
        assertEquals(TuneGroup.PID, TuneGroup.of(KEY));
        assertTrue(TuneGroup.FLIGHT.contains(TuneGroup.of(KEY)));
        TuneSpec held = new TuneSpec(Map.of("rates.type", 1.0F));
        TuneSpec imported = new TuneSpec(Map.of(KEY, 180.0F));
        assertEquals(180.0F, held.overlay(imported, TuneGroup.FLIGHT).values().get(KEY));
    }
}
