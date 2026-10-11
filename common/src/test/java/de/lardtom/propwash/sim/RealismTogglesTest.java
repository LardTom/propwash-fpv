package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleValues;
import org.junit.jupiter.api.Test;

import java.util.function.UnaryOperator;

import static de.lardtom.propwash.server.rules.WorldRuleSpecs.BATTERY_SAG;
import static de.lardtom.propwash.server.rules.WorldRuleSpecs.BATTERY_SCALE;
import static de.lardtom.propwash.server.rules.WorldRuleSpecs.CRASH_DISARM;
import static de.lardtom.propwash.server.rules.WorldRuleSpecs.GROUND_EFFECT;
import static de.lardtom.propwash.server.rules.WorldRuleSpecs.PROP_STRIKES;
import static de.lardtom.propwash.server.rules.WorldRuleSpecs.PROP_WASH;
import static de.lardtom.propwash.server.rules.WorldRuleSpecs.TURBULENCE;
import static de.lardtom.propwash.server.rules.WorldRuleSpecs.WIND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealismTogglesTest {
    static final double FPS = 144.0;
    static final double ALTITUDE = 40.0;

    static SimTuning tuning(UnaryOperator<RuleValues> change) {
        return PhysicsRules.of(change.apply(RuleValues.DEFAULTS)).toSimTuning();
    }

    static FlatWorld hover(SimTuning tuning) {
        return Tests.hovering(DroneBuild.FREESTYLE, tuning, ALTITUDE, StabMode.ANGLE, 1.0);
    }

    static SimSnapshot hoverInWind(SimTuning tuning, double windX, double windZ, double gusts) {
        FlatWorld w = hover(tuning);
        w.env.windX = windX;
        w.env.windZ = windZ;
        w.env.gustIntensity = gusts;
        AltitudeHold hold = new AltitudeHold(ALTITUDE);
        double dt = 1.0 / FPS;
        for (int f = 0; f < (int) (6.0 * FPS); f++) {
            hold.apply(w, dt);
            w.frame(dt);
        }
        SimSnapshot s = new SimSnapshot();
        w.sim.copyStateTo(s);
        return s;
    }

    static void assertSameFlight(SimSnapshot a, SimSnapshot b) {
        assertEquals(a.px, b.px, 0.0);
        assertEquals(a.py, b.py, 0.0);
        assertEquals(a.pz, b.pz, 0.0);
        assertEquals(a.vx, b.vx, 0.0);
        assertEquals(a.vy, b.vy, 0.0);
        assertEquals(a.vz, b.vz, 0.0);
        assertEquals(a.qw, b.qw, 0.0);
        assertEquals(a.qx, b.qx, 0.0);
        assertEquals(a.qy, b.qy, 0.0);
        assertEquals(a.qz, b.qz, 0.0);
    }

    @Test
    void switchesMapOntoTheTuning() {
        SimTuning off = tuning(v -> v.with(PROP_WASH, 0).with(GROUND_EFFECT, 0).with(BATTERY_SAG, 0).with(BATTERY_SCALE, 0)
                .with(CRASH_DISARM, 0).with(PROP_STRIKES, 0).with(WIND, 0).with(TURBULENCE, 0));
        assertFalse(off.propWash || off.wakeField || off.groundEffect || off.batterySag || off.crashDisarm || off.propStrikes);
        assertTrue(off.infiniteBattery);
        assertEquals(0.0, off.windScale, 0.0);
        assertEquals(0.0, off.turbulenceScale, 0.0);
        SimTuning on = tuning(v -> v);
        assertTrue(on.propWash && on.wakeField && on.groundEffect && on.batterySag && on.crashDisarm && on.propStrikes && !on.infiniteBattery);
        assertEquals(1.0, on.windScale, 0.0);
        assertEquals(1.0, on.turbulenceScale, 0.0);
    }

    @Test
    void windOffIgnoresWorldWindAndGusts() {
        SimTuning off = tuning(v -> v.with(WIND, 0));
        assertSameFlight(hoverInWind(off, 0.0, 0.0, 0.0), hoverInWind(off, 6.0, -4.0, 0.8));
        SimTuning on = tuning(v -> v);
        assertTrue(Math.abs(hoverInWind(on, 6.0, -4.0, 0.8).px - hoverInWind(on, 0.0, 0.0, 0.0).px) > 0.3,
                "Wind wirkt mit eingeschaltetem Wind gar nicht");
    }

    @Test
    void turbulenceOffKeepsSteadyWindWithoutGusts() {
        SimTuning off = tuning(v -> v.with(TURBULENCE, 0));
        SimSnapshot steady = hoverInWind(off, 5.0, 0.0, 0.0);
        assertSameFlight(steady, hoverInWind(off, 5.0, 0.0, 0.8));
        assertTrue(Math.abs(steady.px - hoverInWind(off, 0.0, 0.0, 0.0).px) > 0.3, "stetiger Wind fehlt ohne Turbulenz");
        SimTuning on = tuning(v -> v);
        assertNotEquals(hoverInWind(on, 5.0, 0.0, 0.0).px, hoverInWind(on, 5.0, 0.0, 0.8).px, "Böen wirken mit Turbulenz gar nicht");
    }

    static double sinkThroughOwnWash(SimTuning tuning, boolean requireNoWash) {
        FlatWorld w = hover(tuning);
        double dt = 1.0 / FPS;
        double hoverStick = w.sim.telemetry().hoverThrottleStick;
        double kv = 0.15 * hoverStick;
        double integral = 0.0;
        double peak = 0.0;
        for (double time = 0.0; time < 4.0; time += dt) {
            double e = -5.0 * Math.min(1.0, time) - w.sim.body.vy;
            integral = SimMath.clamp(integral + kv * e * dt, -0.5, 0.5);
            w.input.throttle = SimMath.clamp01(hoverStick + kv * e + integral);
            w.frame(dt);
            Telemetry t = w.sim.telemetry();
            peak = Math.max(peak, Math.max(t.propWash, Math.max(t.propWashVrs, t.propWashWake)));
            if (requireNoWash) {
                assertEquals(0.0, t.propWash, 0.0);
                assertEquals(0.0, t.propWashVrs, 0.0);
                assertEquals(0.0, t.propWashWake, 0.0);
            }
        }
        return peak;
    }

    @Test
    void propWashOffRemovesWashWhileSinking() {
        assertEquals(0.0, sinkThroughOwnWash(tuning(v -> v.with(PROP_WASH, 0)), true), 0.0);
        assertTrue(sinkThroughOwnWash(tuning(v -> v), false) >= 0.5, "Prop-Wash wirkt eingeschaltet nicht");
    }

    static double[] punch(SimTuning tuning) {
        FlatWorld w = hover(tuning);
        double rest = w.sim.telemetry().packVoltage;
        double lowest = rest;
        double highest = rest;
        w.input.throttle = 1.0;
        for (int f = 0; f < (int) FPS; f++) {
            w.frame(1.0 / FPS);
            lowest = Math.min(lowest, w.sim.telemetry().packVoltage);
            highest = Math.max(highest, w.sim.telemetry().packVoltage);
        }
        return new double[]{rest, lowest, highest, w.sim.telemetry().chargeFraction};
    }

    @Test
    void voltageSagOffKeepsTheLoadDropOfTheInternalResistance() {
        double[] off = punch(tuning(v -> v.with(BATTERY_SAG, 0).with(BATTERY_SCALE, 0)));
        assertTrue(off[0] - off[1] > 1.0, "ohne Spannungseinbruch fehlt der Innenwiderstand unter Last");
        assertTrue(off[2] <= off[0] + 1.0E-9);
        double[] on = punch(tuning(v -> v.with(BATTERY_SCALE, 0)));
        assertTrue(on[0] - on[1] > 1.0, "Spannungseinbruch wirkt eingeschaltet nicht");
    }

    @Test
    void infiniteBatteryKeepsTheCharge() {
        assertEquals(1.0, punch(tuning(v -> v.with(BATTERY_SCALE, 0)))[3], 0.0);
        assertTrue(punch(tuning(v -> v))[3] < 1.0);
    }

    @Test
    void groundEffectOffKeepsFullFactor() {
        FlatWorld off = Tests.hovering(DroneBuild.FREESTYLE, tuning(v -> v.with(GROUND_EFFECT, 0)), 0.3, StabMode.ANGLE, 1.0);
        assertEquals(1.0, off.sim.telemetry().groundEffectFactor, 0.0);
        FlatWorld on = Tests.hovering(DroneBuild.FREESTYLE, tuning(v -> v), 0.3, StabMode.ANGLE, 1.0);
        assertTrue(on.sim.telemetry().groundEffectFactor > 1.0, "Bodeneffekt wirkt eingeschaltet nicht");
    }

    static Telemetry wallImpact(SimTuning tuning, double tolerance) {
        FlatWorld w = hover(tuning);
        w.input.throttle = w.sim.telemetry().hoverThrottleStick;
        w.wallX = w.x + w.sim.params().hitboxWidth / 2.0 + 0.001;
        w.sim.body.vx = tolerance * w.sim.params().impactTolerance;
        w.sim.body.vz = 0.0;
        for (int f = 0; f < 50; f++) {
            w.frame(1_000_000L);
            if (w.report.hitX) {
                return w.sim.telemetry();
            }
        }
        throw new AssertionError("Wand nicht getroffen");
    }

    @Test
    void crashDisarmOffStaysArmed() {
        assertTrue(wallImpact(tuning(v -> v.with(CRASH_DISARM, 0)), 1.3).armed);
        assertFalse(wallImpact(tuning(v -> v), 1.3).armed);
    }

    @Test
    void propStrikesOffLeavesThePropsSpinning() {
        assertEquals(0, wallImpact(tuning(v -> v.with(PROP_STRIKES, 0).with(CRASH_DISARM, 0)), 0.7).propStrikeCount);
        assertTrue(wallImpact(tuning(v -> v.with(CRASH_DISARM, 0)), 0.7).propStrikeCount > 0, "Propellertreffer wirken eingeschaltet nicht");
    }
}
