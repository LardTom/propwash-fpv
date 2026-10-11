package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BreakThroughTest {
    private static DroneSim moving(DroneBuild build, double vx, double vz) {
        DroneSim sim = new DroneSim(build, new FlightControllerConfig(), Tests.quietTuning(), 7L);
        sim.reset(0.0, 5.0, 0.0, 0.0);
        SimSnapshot snapshot = new SimSnapshot();
        sim.copyStateTo(snapshot);
        snapshot.vx = vx;
        snapshot.vy = 0.0;
        snapshot.vz = vz;
        snapshot.sleeping = false;
        sim.copyStateFrom(snapshot);
        return sim;
    }

    @Test
    void absorbedEnergyOnlySlowsTheNormalComponent() {
        DroneSim sim = moving(DroneBuild.FREESTYLE, 25.0, 4.0);
        double m = sim.params().massKg;
        double lost = sim.breakThrough(-1.0, 0.0, 0.0, 36.0);
        Vector3d v = new Vector3d();
        sim.getVelocity(v);
        assertEquals(Math.sqrt(625.0 - 72.0 / m), v.x, 1.0E-9);
        assertEquals(25.0 - v.x, lost, 1.0E-9);
        assertEquals(4.0, v.z, 1.0E-12, "Tempo längs der Scheibe bleibt");
        assertEquals(0.0, v.y, 1.0E-12);
        assertEquals(1, sim.impacts().size(), "Bruch zählt als Aufprall mit dem verlorenen Tempo");
        assertEquals((float) lost, sim.impacts().get(0).speed(), 1.0E-5F);
        assertEquals(1, sim.events().count(SimEventType.BUMP));
        assertEquals(0, sim.events().count(SimEventType.CRASH));
    }

    @Test
    void barelyBreakingIsACrash() {
        DroneSim sim = moving(DroneBuild.FREESTYLE, 12.0, 0.0);
        double m = sim.params().massKg;
        double joules = 0.5 * m * 144.0 * 0.99;
        double lost = sim.breakThrough(-2.0, 0.0, 0.0, joules);
        assertTrue(lost >= sim.params().impactTolerance, "verliert fast alles: " + lost);
        assertEquals(1, sim.events().count(SimEventType.CRASH));
    }

    @Test
    void nothingHappensWhenMovingAwayOrWithoutEnergy() {
        DroneSim sim = moving(DroneBuild.FREESTYLE, 25.0, 0.0);
        assertEquals(0.0, sim.breakThrough(1.0, 0.0, 0.0, 36.0));
        assertEquals(0.0, sim.breakThrough(-1.0, 0.0, 0.0, 0.0));
        assertEquals(0.0, sim.breakThrough(-1.0, 0.0, 0.0, Double.NaN));
        assertEquals(0.0, sim.breakThrough(0.0, 0.0, 0.0, 36.0));
        Vector3d v = new Vector3d();
        sim.getVelocity(v);
        assertEquals(25.0, v.x, 1.0E-12);
        assertEquals(0, sim.impacts().size());
        assertEquals(0, sim.events().size());
    }

    @Test
    void neverReversesTheDrone() {
        DroneSim sim = moving(DroneBuild.WHOOP, 5.0, 0.0);
        sim.breakThrough(-1.0, 0.0, 0.0, 500.0);
        Vector3d v = new Vector3d();
        sim.getVelocity(v);
        assertEquals(0.0, v.x, 1.0E-12);
    }
}
