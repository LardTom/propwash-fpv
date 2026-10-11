package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleValues;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GravityScaleTest {
    static final DroneBuild[] PRESETS = DerivationTest.PRESETS;
    static final double DT = 1.0 / 144.0;

    static SimTuning scaled(double scale) {
        SimTuning t = new SimTuning();
        t.gravityScale = scale;
        return t;
    }

    static double measuredHoverStick(DroneBuild b, double scale) {
        FlatWorld w = FlightCharacterization.hover(b, new FlightControllerConfig(), scaled(scale), 200.0, 0.8, 144.0);
        AltitudeHold hold = new AltitudeHold(200.0);
        double sum = 0.0;
        int n = 0;
        for (int f = 0; f < 144 * 8; f++) {
            hold.apply(w, DT);
            w.frame(DT);
            if (f >= 144 * 3) {
                sum += w.input.throttle;
                n++;
            }
        }
        return sum / n;
    }

    @Test
    void defaultGravityIsTheRealOneAndFactorOneChangesNothing() {
        SimTuning t = new SimTuning();
        assertEquals(1.0, t.gravityScale, 0.0);
        assertEquals(9.81, t.gravity(), 0.0);
        assertEquals(1.0, PhysicsRules.DEFAULT.toSimTuning().gravityScale, 0.0);
        SimTuning explicit = new SimTuning();
        explicit.gravityScale = 1.0;
        for (DroneBuild b : PRESETS) {
            FlatWorld a = FallCharacterization.hover(b, new SimTuning());
            FlatWorld c = FallCharacterization.hover(b, explicit);
            a.input.throttle = 0.0;
            c.input.throttle = 0.0;
            for (int f = 0; f < 144 * 2; f++) {
                a.frame(DT);
                c.frame(DT);
            }
            assertEquals(Double.doubleToLongBits(a.sim.body.py), Double.doubleToLongBits(c.sim.body.py), b.frame().key());
            assertEquals(Double.doubleToLongBits(a.sim.body.vy), Double.doubleToLongBits(c.sim.body.vy), b.frame().key());
        }
    }

    @Test
    void freeFallFollowsTheFactor() {
        for (double scale : new double[]{0.0, 0.01, 0.8, 1.2, 1.5, 5.0, 10.0}) {
            SimTuning t = Tests.quietTuning();
            t.dragScale = 0.0;
            t.gravityScale = scale;
            DroneSim sim = new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), t, 42L);
            sim.reset(0.0, 100.0, 0.0, 0.0);
            StepResult out = new StepResult();
            MoveReport report = new MoveReport();
            for (int f = 0; f < 20; f++) {
                sim.step(50_000_000L, new PilotInput(), new SimEnvironment(), out);
                report.clear();
                report.requestedY = out.dy;
                report.actualY = out.dy;
                sim.afterMove(report);
            }
            assertEquals(-9.81 * scale, sim.body.vy, 0.005, "Faktor " + scale);
            assertEquals(-4.905 * scale, sim.body.py - 100.0, 0.01 * Math.max(1.0, scale), "Faktor " + scale);
        }
    }

    @Test
    void moreGravityNeedsProportionallyMoreHoverThrust() {
        for (DroneBuild b : PRESETS) {
            String n = b.frame().key();
            double one = measuredHoverStick(b, 1.0);
            double more = measuredHoverStick(b, 1.2);
            assertTrue(more > one + 0.01, n + " Schwebegas " + one + " -> " + more);
            for (double scale : new double[]{1.0, 1.2}) {
                FlatWorld w = FlightCharacterization.hover(b, new FlightControllerConfig(), scaled(scale), 200.0, 0.8, 144.0);
                AltitudeHold hold = new AltitudeHold(200.0);
                double thrust = 0.0;
                int k = 0;
                for (int f = 0; f < 144 * 6; f++) {
                    hold.apply(w, DT);
                    w.frame(DT);
                    if (f >= 144 * 3) {
                        thrust += w.sim.telemetry().thrustToWeight;
                        k++;
                    }
                }
                assertEquals(1.0, thrust / k, 0.03, n + " Schub/Gewicht im Schweben bei " + scale);
                double estimate = w.sim.telemetry().hoverThrottleStick;
                double measured = scale == 1.0 ? one : more;
                assertEquals(measured, estimate, 0.06, n + " Schwebegas-Schätzung bei " + scale);
            }
            AirframeSpec p = AirframeSpec.of(b);
            OperatingPoints.Point base = new OperatingPoints.Point();
            OperatingPoints.steadyMotor(p, p.hoverOmega, OperatingPoints.hoverCurrent(p, 1.0), 0.8, false, 0.0, true, base);
            OperatingPoints.Point heavy = new OperatingPoints.Point();
            OperatingPoints.steadyMotor(p, p.hoverOmega * Math.sqrt(1.2), OperatingPoints.hoverCurrent(p, 1.2), 0.8, false, 0.0, true, heavy);
            assertEquals(1.2, heavy.thrustPerMotor / base.thrustPerMotor, 1e-9, n);
        }
    }

    @Test
    void moreGravityDropsFaster() {
        for (DroneBuild b : PRESETS) {
            String n = b.frame().key();
            FallCharacterization.Result one = new FallCharacterization.Result();
            FallCharacterization.Result more = new FallCharacterization.Result();
            AirframeSpec p = AirframeSpec.of(b);
            FallCharacterization.drop(b, 1.0, p.massKg * ModelConstants.G, one);
            FallCharacterization.drop(b, 1.2, p.massKg * ModelConstants.G * 1.2, more);
            double gain = more.dropHeight[1] / one.dropHeight[1];
            assertTrue(gain > 1.10 && gain < 1.21, n + " Fallhöhe nach 1 s " + one.dropHeight[1] + " -> " + more.dropHeight[1]);
            assertEquals(Math.sqrt(1.2), more.bellyIdle / one.bellyIdle, 0.03, n + " Endgeschwindigkeit");
        }
    }

    @Test
    void changingTheFactorInFlightUpdatesTheHoverEstimateAtOnce() {
        FlatWorld w = FallCharacterization.hover(DroneBuild.FREESTYLE, new SimTuning());
        double before = w.sim.telemetry().hoverThrottleStick;
        w.sim.setTuning(scaled(1.3));
        w.frame(DT);
        double after = w.sim.telemetry().hoverThrottleStick;
        assertTrue(after > before + 0.02, before + " -> " + after);
        assertEquals(9.81 * 1.3, w.sim.gravity(), 1e-12);
    }

    @Test
    void altitudeHoldKeepsHeightWithHeavierGravity() {
        FlatWorld w = new FlatWorld(new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), scaled(1.4), 3L));
        w.place(0.0, 20.0, 0.0, 0.0);
        w.input.mode = StabMode.ANGLE;
        w.arm();
        AltitudeHold hold = new AltitudeHold(20.0);
        for (int i = 0; i < 144 * 12; i++) {
            hold.apply(w, DT);
            w.frame(DT);
        }
        w.input.altHold = true;
        double y0 = w.sim.body.py;
        for (int i = 0; i < 144 * 20; i++) {
            w.frame(DT);
            assertEquals(HoldMode.ALTHOLD, w.sim.telemetry().holdMode);
            assertEquals(y0, w.sim.body.py, 2.0);
        }
    }

    @Test
    void settingIsClampedSnappedAndMarksModifiedPhysics() {
        SimTuning t = new SimTuning();
        t.gravityScale = 30.0;
        t.sanitize();
        assertEquals(SimTuning.GRAVITY_MAX, t.gravityScale, 0.0);
        t.gravityScale = -0.5;
        t.sanitize();
        assertEquals(SimTuning.GRAVITY_MIN, t.gravityScale, 0.0);
        t.gravityScale = 0.0;
        t.sanitize();
        assertEquals(0.0, t.gravityScale, 0.0);
        t.gravityScale = 7.5;
        t.sanitize();
        assertEquals(7.5, t.gravityScale, 0.0);
        t.gravityScale = Double.NaN;
        t.sanitize();
        assertEquals(1.0, t.gravityScale, 0.0);
        assertEquals(1.15, PhysicsRules.of(RuleValues.DEFAULTS.with(WorldRuleSpecs.GRAVITY_SCALE, 115)).toSimTuning().gravityScale, 0.0);
        assertEquals(9.0, PhysicsRules.of(RuleValues.DEFAULTS.with(WorldRuleSpecs.GRAVITY_SCALE, 900)).toSimTuning().gravityScale, 0.0);
        assertEquals(1.01, PhysicsRules.of(RuleValues.DEFAULTS.with(WorldRuleSpecs.GRAVITY_SCALE, 101)).toSimTuning().gravityScale, 0.0);
        assertEquals(10.0, PhysicsRules.of(RuleValues.DEFAULTS.with(WorldRuleSpecs.GRAVITY_SCALE, 5000)).toSimTuning().gravityScale, 0.0);
        assertEquals(0.0, PhysicsRules.of(RuleValues.DEFAULTS.with(WorldRuleSpecs.GRAVITY_SCALE, -5)).toSimTuning().gravityScale, 0.0);
        assertEquals(1.0, PhysicsRules.of(new RuleValues(Map.of("gravity_scale", 5000))).toSimTuning().gravityScale, 0.0);
        assertTrue(PhysicsRules.of(RuleValues.DEFAULTS.with(WorldRuleSpecs.GRAVITY_SCALE, 99)).modified());
        assertTrue(PhysicsRules.of(RuleValues.DEFAULTS.with(WorldRuleSpecs.GRAVITY_SCALE, 0)).modified());
        assertFalse(PhysicsRules.DEFAULT.modified());
        PhysicsRules heavier = PhysicsRules.of(RuleValues.DEFAULTS.with(WorldRuleSpecs.GRAVITY_SCALE, 125));
        assertTrue(heavier.modified());
        assertEquals(1.25, heavier.toSimTuning().gravityScale, 0.0);
        assertEquals(1.25, heavier.toSimTuning().copy().gravityScale, 0.0);
    }

    @Test
    void extremeGravityStaysFiniteInEveryMode() {
        for (DroneBuild b : PRESETS) {
            for (double scale : new double[]{0.0, 0.01, 0.3, 3.0, 10.0}) {
                String n = b.frame().key() + " bei " + scale;
                FlatWorld w = FlightCharacterization.hover(b, new FlightControllerConfig(), scaled(scale), 200.0, 0.8, 144.0);
                Telemetry tm = w.sim.telemetry();
                assertTrue(tm.hoverThrottleStick >= 0.0 && tm.hoverThrottleStick <= 1.0, n + " Schwebegas " + tm.hoverThrottleStick);
                w.input.altHold = true;
                for (int f = 0; f < 144 * 2; f++) {
                    w.frame(DT);
                }
                w.input.altHold = false;
                w.input.mode = StabMode.ACRO;
                w.input.throttle = 1.0;
                for (int f = 0; f < 144; f++) {
                    w.frame(DT);
                }
                w.input.throttle = 0.0;
                for (int f = 0; f < 144; f++) {
                    w.frame(DT);
                }
                RigidBody body = w.sim.body;
                assertTrue(Double.isFinite(body.px) && Double.isFinite(body.py) && Double.isFinite(body.vy), n);
                assertTrue(Double.isFinite(w.sim.telemetry().thrustToWeight), n);
                assertTrue(w.sim.telemetry().hoverThrottleStick >= 0.0 && w.sim.telemetry().hoverThrottleStick <= 1.0, n);
            }
        }
    }

    @Test
    void zeroGravityCargoKeepsTheThrustReserve() {
        double atZero = de.lardtom.propwash.interact.GripperSpec.capacityGrams(500.0, 2000.0, 0.0);
        double atOne = de.lardtom.propwash.interact.GripperSpec.capacityGrams(500.0, 2000.0, 1.0);
        assertTrue(atZero >= atOne && atZero > 0.0, atZero + " / " + atOne);
    }
}
