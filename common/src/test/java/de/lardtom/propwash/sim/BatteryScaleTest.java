package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleValues;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatteryScaleTest {
    static final double FPS = 144.0;
    static final double ALTITUDE = 40.0;

    static FlatWorld hover(SimTuning tuning, double seconds) {
        FlatWorld w = Tests.hovering(DroneBuild.FREESTYLE, tuning, ALTITUDE, StabMode.ANGLE, 1.0);
        AltitudeHold hold = new AltitudeHold(ALTITUDE);
        double dt = 1.0 / FPS;
        for (int f = 0; f < (int) (seconds * FPS); f++) {
            hold.apply(w, dt);
            w.frame(dt);
        }
        return w;
    }

    static SimTuning battery(int percent) {
        return PhysicsRules.of(RuleValues.DEFAULTS.with(WorldRuleSpecs.BATTERY_SCALE, percent)).toSimTuning();
    }

    @Test
    void twiceTheBatteryDrainsHalfAsFast() {
        double normal = 1.0 - hover(battery(100), 20.0).sim.telemetry().chargeFraction;
        double twice = 1.0 - hover(battery(200), 20.0).sim.telemetry().chargeFraction;
        double half = 1.0 - hover(battery(50), 20.0).sim.telemetry().chargeFraction;
        assertTrue(normal > 0.01, "Akku entlädt sich im Schwebeflug nicht: " + normal);
        assertEquals(0.5, twice / normal, 0.02, "200 %: " + twice + " statt der Hälfte von " + normal);
        assertEquals(2.0, half / normal, 0.08, "50 %: " + half + " statt des Doppelten von " + normal);
    }

    @Test
    void zeroMeansInfinite() {
        Telemetry t = hover(battery(0), 10.0).sim.telemetry();
        assertEquals(1.0, t.chargeFraction, 0.0);
    }

    @Test
    void defaultRulesFlyBitIdentical() {
        FlatWorld a = hover(new SimTuning(), 6.0);
        FlatWorld b = hover(PhysicsRules.DEFAULT.toSimTuning(), 6.0);
        SimSnapshot sa = new SimSnapshot();
        SimSnapshot sb = new SimSnapshot();
        a.sim.copyStateTo(sa);
        b.sim.copyStateTo(sb);
        assertEquals(Double.doubleToLongBits(sa.py), Double.doubleToLongBits(sb.py));
        assertEquals(Double.doubleToLongBits(sa.vy), Double.doubleToLongBits(sb.vy));
        assertEquals(Double.doubleToLongBits(sa.qx), Double.doubleToLongBits(sb.qx));
        assertEquals(Double.doubleToLongBits(sa.mAhUsed), Double.doubleToLongBits(sb.mAhUsed));
        assertEquals(Double.doubleToLongBits(sa.soc), Double.doubleToLongBits(sb.soc));
    }
}
