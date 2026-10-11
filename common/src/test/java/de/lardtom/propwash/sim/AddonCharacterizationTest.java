package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddonCharacterizationTest {
    record Hover(double maxTilt, double maxRateDps, double drift, double altitudeError, double attitudeChange, int faults) {
    }

    static Hover hover(DroneBuild build, StabMode mode, double seconds) {
        FlatWorld w = Tests.hovering(build, Tests.quietTuning(), 20.0, mode, 1.0);
        AltitudeHold hold = new AltitudeHold(20.0);
        double dt = 1.0 / 144.0;
        double startX = w.sim.body.px;
        double startZ = w.sim.body.pz;
        double startRoll = w.sim.telemetry().rollDeg;
        double startPitch = w.sim.telemetry().pitchDeg;
        double maxTilt = 0.0;
        double maxRate = 0.0;
        double altitude = 0.0;
        double change = 0.0;
        int frames = (int) Math.round(seconds * 144.0);
        for (int k = 0; k < frames; k++) {
            hold.apply(w, dt);
            w.frame(dt);
            Telemetry t = w.sim.telemetry();
            assertTrue(Double.isFinite(t.rollDeg + t.pitchDeg + t.packVoltage + t.motorRpm[0]), build.toString());
            maxTilt = Math.max(maxTilt, t.tiltDeg);
            maxRate = Math.max(maxRate, SimMath.hypot3(t.gyroDps[0], t.gyroDps[1], t.gyroDps[2]));
            altitude = Math.max(altitude, Math.abs(w.sim.body.py - 20.0));
            change = Math.max(change, Math.max(Math.abs(t.rollDeg - startRoll), Math.abs(t.pitchDeg - startPitch)));
        }
        double drift = Math.hypot(w.sim.body.px - startX, w.sim.body.pz - startZ) / seconds;
        return new Hover(maxTilt, maxRate, drift, altitude, change, w.sim.telemetry().faults);
    }

    @Test
    void xClassThirteenInchEightCellHoversCalmly() {
        DroneBuild build = AddonParts.xClassBuild();
        assertTrue(build.buildable(), build.problems().toString());
        AirframeSpec p = AirframeSpec.of(build);
        BuildAnalysis a = BuildAnalysis.of(build);
        assertFalse(a.warnings().contains(BuildWarning.CANNOT_HOVER));
        assertTrue(a.hoverThrottlePercent() >= 25.0 && a.hoverThrottlePercent() <= 45.0, "Schwebegas " + a.hoverThrottlePercent());
        assertTrue(a.hoverFlightTimeMin() >= 6.0 && a.hoverFlightTimeMin() <= 15.0, "Flugzeit " + a.hoverFlightTimeMin());
        assertTrue(a.thrustToWeight() > 2.5, "Schub/Gewicht " + a.thrustToWeight());
        assertTrue(p.motorTau <= ModelConstants.MOTOR_TAU_MAX + 1e-12);
        assertEquals(13.0, build.frame().definition().nominalPropInches());
        Hover h = hover(build, StabMode.ANGLE, 10.0);
        assertTrue(h.maxTilt < 2.0, "Neigung " + h.maxTilt);
        assertTrue(h.altitudeError < 1.0, "Höhe " + h.altitudeError);
        assertTrue(h.drift < 0.3, "Drift " + h.drift);
        assertEquals(0, h.faults);
    }

    @Test
    void xClassFollowsARollCommand() {
        FlatWorld w = Tests.hovering(AddonParts.xClassBuild(), Tests.quietTuning(), 30.0, StabMode.ACRO, 1.0);
        w.input.throttle = w.sim.telemetry().hoverThrottleStick;
        w.input.roll = 0.4;
        double peak = 0.0;
        for (int k = 0; k < 72; k++) {
            w.frame(1.0 / 144.0);
            peak = Math.max(peak, Math.abs(Tests.rollRateDps(w.sim)));
        }
        double setpoint = Math.abs(w.sim.telemetry().setpointDps[0]);
        assertTrue(setpoint > 50.0);
        assertTrue(peak > 0.7 * setpoint, "Rollrate " + peak + " von " + setpoint);
    }

    @Test
    void sevenInchEightCellLiIonHoversOverTwentyFiveMinutes() {
        DroneBuild build = AddonParts.longRange8sBuild();
        assertTrue(build.buildable(), build.problems().toString());
        BuildAnalysis a = BuildAnalysis.of(build);
        assertTrue(a.hoverFlightTimeMin() >= 25.0, "Schwebezeit " + a.hoverFlightTimeMin());
        assertEquals(8, AirframeSpec.of(build).cells);
        assertFalse(a.warnings().contains(BuildWarning.ESC_OVERVOLTAGE));
        Hover h = hover(build, StabMode.ANGLE, 6.0);
        assertTrue(h.maxTilt < 2.0, "Neigung " + h.maxTilt);
        assertEquals(0, h.faults);
    }

    @Test
    void twoInchToothpickFliesOnOneAndTwoCells() {
        DroneBuild one = AddonParts.toothpickBuild(BatteryType.B1S_300_HV);
        DroneBuild two = AddonParts.toothpickBuild(AddonParts.lipo(2, 300, 16.0, 0.03, 45));
        assertTrue(one.buildable(), one.problems().toString());
        assertTrue(two.buildable(), two.problems().toString());
        BuildAnalysis a1 = BuildAnalysis.of(one);
        BuildAnalysis a2 = BuildAnalysis.of(two);
        assertFalse(a1.warnings().contains(BuildWarning.CANNOT_HOVER));
        assertFalse(a2.warnings().contains(BuildWarning.CANNOT_HOVER));
        assertTrue(a2.thrustToWeight() > a1.thrustToWeight());
        for (DroneBuild build : new DroneBuild[]{one, two}) {
            Hover h = hover(build, StabMode.ANGLE, 5.0);
            assertTrue(h.maxTilt < 3.0, build + " Neigung " + h.maxTilt);
            assertEquals(0, h.faults);
        }
    }

    @Test
    void deadcatAndStretchedFramesHoldTheirAttitudeWithoutDrift() {
        DroneBuild fs = DroneBuild.FREESTYLE;
        for (FrameType frame : new FrameType[]{AddonParts.deadcat(), AddonParts.stretched(70.0, 95.0), AddonParts.stretched(95.0, 70.0)}) {
            DroneBuild build = new DroneBuild(frame, fs.stack(), fs.motor(), fs.prop(), fs.video(), fs.battery());
            Hover angle = hover(build, StabMode.ANGLE, 8.0);
            assertTrue(angle.maxTilt < 1.5, frame.key() + " Neigung " + angle.maxTilt);
            assertTrue(angle.drift < 0.3, frame.key() + " Drift " + angle.drift);
            Hover acro = hover(build, StabMode.ACRO, 8.0);
            assertTrue(acro.attitudeChange < 3.0, frame.key() + " Lageänderung " + acro.attitudeChange);
            assertTrue(acro.maxRateDps < 20.0, frame.key() + " Drehrate " + acro.maxRateDps);
        }
    }

    @Test
    void addonRolesDriveTricksAndSizeClass() {
        assertEquals(13.0, AddonParts.xClass13().definition().nominalPropInches());
        assertFalse(AddonParts.xClass13().definition().role().tricksByDefault());
        assertTrue(AddonParts.toothpick().definition().role().tricksByDefault());
    }
}
