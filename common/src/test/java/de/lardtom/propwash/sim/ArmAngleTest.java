package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArmAngleTest {
    private static FlatWorld world(double maxAngle) {
        FlightControllerConfig config = new FlightControllerConfig();
        config.armMaxTiltDeg = maxAngle;
        FlatWorld w = new FlatWorld(new DroneSim(DroneBuild.FREESTYLE, config, Tests.quietTuning(), 42L));
        w.placeOnGround(0.0, 0.0, 0.0);
        w.run(0.1, 144.0);
        return w;
    }

    private static void tilt(FlatWorld w, double degreesAboutX) {
        double half = Math.toRadians(degreesAboutX) * 0.5;
        w.sim.body.setUpright(0.0);
        w.sim.body.qw = Math.cos(half);
        w.sim.body.qx = Math.sin(half);
        w.sim.body.qy = 0.0;
        w.sim.body.qz = 0.0;
        w.sim.body.updateDerived();
    }

    private static int blocked(FlatWorld w) {
        EventQueue q = w.sim.events();
        for (int e = 0; e < q.size(); e++) {
            if (q.type(e) == SimEventType.ARM_BLOCKED) {
                return (int) q.value(e);
            }
        }
        return 0;
    }

    @Test
    void defaultStaysAtTwentyFiveDegrees() {
        assertEquals(25.0, new FlightControllerConfig().armMaxTiltDeg);
        assertEquals(FlightControllerConfig.ARM_ANGLE_DEFAULT, new FlightControllerConfig().armMaxTiltDeg);
        FlatWorld steep = world(FlightControllerConfig.ARM_ANGLE_DEFAULT);
        tilt(steep, 30.0);
        steep.arm();
        assertFalse(steep.sim.telemetry().armed);
        assertEquals(ArmBlocker.TILTED.bit(), blocked(steep));
        FlatWorld mild = world(FlightControllerConfig.ARM_ANGLE_DEFAULT);
        tilt(mild, 20.0);
        mild.arm();
        assertTrue(mild.sim.telemetry().armed);
    }

    @ParameterizedTest
    @ValueSource(doubles = {30.0, 90.0, 135.0, 179.0, 180.0})
    void oneEightyArmsInAnyAttitude(double degrees) {
        FlatWorld w = world(180.0);
        tilt(w, degrees);
        w.arm();
        assertTrue(w.sim.telemetry().armed, "bei " + degrees + "° nicht scharf");
    }

    @Test
    void armsInTheAirWhileFallingUpsideDown() {
        for (double limit : new double[]{25.0, 180.0}) {
            FlatWorld w = world(limit);
            w.place(0.0, 20.0, 0.0, 0.0);
            tilt(w, 150.0);
            w.run(0.3, 144.0);
            assertTrue(w.y < 20.0, "Drohne fällt nicht");
            w.arm();
            assertEquals(limit >= 180.0, w.sim.telemetry().armed, "Grenze " + limit);
        }
    }

    @Test
    void customLimitSitsBetween() {
        FlatWorld w = world(60.0);
        tilt(w, 55.0);
        w.arm();
        assertTrue(w.sim.telemetry().armed);
        FlatWorld over = world(60.0);
        tilt(over, 65.0);
        over.arm();
        assertFalse(over.sim.telemetry().armed);
    }

    @Test
    void sanitizeKeepsBetaflightRange() {
        FlightControllerConfig c = new FlightControllerConfig();
        c.armMaxTiltDeg = -10.0;
        c.sanitize();
        assertEquals(0.0, c.armMaxTiltDeg);
        c.armMaxTiltDeg = 500.0;
        c.sanitize();
        assertEquals(180.0, c.armMaxTiltDeg);
        c.armMaxTiltDeg = Double.NaN;
        c.sanitize();
        assertEquals(25.0, c.armMaxTiltDeg);
        c.armMaxTiltDeg = 3.0;
        c.sanitize();
        assertEquals(3.0, c.armMaxTiltDeg);
        assertEquals(3.0, c.copy().armMaxTiltDeg);
    }

    @Test
    void threeDModeUsesTheSameLimit() {
        FlatWorld w = world(180.0);
        tilt(w, 100.0);
        w.input.mode3d = true;
        w.input.throttle = 0.5;
        w.arm();
        assertTrue(w.sim.telemetry().armed);
        FlatWorld narrow = world(25.0);
        tilt(narrow, 100.0);
        narrow.input.mode3d = true;
        narrow.input.throttle = 0.5;
        narrow.arm();
        assertFalse(narrow.sim.telemetry().armed);
    }
}
