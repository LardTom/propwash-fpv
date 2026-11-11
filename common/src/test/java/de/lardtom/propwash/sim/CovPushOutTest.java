package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.interact.BreakMath;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CovPushOutTest {
    private static final double GLASS_HARDNESS = 0.3;

    private static DroneSim moving(DroneBuild build, double vx, double vy, double vz) {
        DroneSim sim = new DroneSim(build, new FlightControllerConfig(), Tests.quietTuning(), 11L);
        sim.reset(0.0, 5.0, 0.0, 0.0);
        SimSnapshot snapshot = new SimSnapshot();
        sim.copyStateTo(snapshot);
        snapshot.vx = vx;
        snapshot.vy = vy;
        snapshot.vz = vz;
        snapshot.sleeping = false;
        sim.copyStateFrom(snapshot);
        return sim;
    }

    @Test
    void pushOutMovesBodyAndPictureAndDropsOnlyTheInwardSpeed() {
        DroneSim sim = moving(DroneBuild.FREESTYLE, 4.0, -6.0, 1.5);
        Vector3d before = new Vector3d();
        sim.getPosition(before);
        sim.pushOut(0.0, 0.03, 0.0);
        Vector3d after = new Vector3d();
        sim.getPosition(after);
        Vector3d v = new Vector3d();
        sim.getVelocity(v);
        assertEquals(before.y + 0.03, after.y, 1.0E-12);
        assertEquals(before.x, after.x, 1.0E-12);
        assertEquals(0.0, v.y, 1.0E-12, "kein Tempo mehr in den Boden");
        assertEquals(4.0, v.x, 1.0E-12, "Tempo längs der Fläche bleibt");
        assertEquals(1.5, v.z, 1.0E-12);
        assertEquals(1, sim.pushOuts());
        assertEquals(0, sim.events().count(SimEventType.CRASH), "Herausdrücken ist kein Absturz");
        StepResult out = new StepResult();
        PilotInput input = new PilotInput();
        sim.step(0L, input, new SimEnvironment(), out);
        assertEquals(0.0, out.dy, 1.0E-9, "das Bild springt nicht ein zweites Mal");
    }

    @Test
    void pushOutKeepsOutwardSpeedAndIgnoresNonsense() {
        DroneSim sim = moving(DroneBuild.FREESTYLE, -3.0, 2.0, 0.0);
        sim.pushOut(-0.4, 0.0, 0.0);
        Vector3d v = new Vector3d();
        sim.getVelocity(v);
        assertEquals(-3.0, v.x, 1.0E-12, "fliegt schon hinaus");
        assertEquals(2.0, v.y, 1.0E-12);
        sim.pushOut(0.0, 0.0, 0.0);
        sim.pushOut(Double.NaN, 1.0, 0.0);
        assertEquals(1, sim.pushOuts());
    }

    @Test
    void everyGlassBlockCostsItsEnergySoTheDroneCannotTunnel() {
        for (DroneBuild build : new DroneBuild[]{DroneBuild.WHOOP, DroneBuild.FREESTYLE, DroneBuild.LONG_RANGE}) {
            for (double speed : new double[]{10.0, 18.0, 25.0, 40.0}) {
                DroneSim sim = moving(build, speed, 0.0, 0.0);
                double mass = sim.params().massKg;
                double threshold = BreakMath.threshold(GLASS_HARDNESS, 1.0);
                double energy = 0.5 * mass * speed * speed;
                int broken = 0;
                Vector3d v = new Vector3d();
                while (true) {
                    sim.getVelocity(v);
                    BreakMath.Impact impact = BreakMath.of(mass, v.x, threshold);
                    if (!impact.breaks()) {
                        break;
                    }
                    double kinetic = 0.5 * mass * v.x * v.x;
                    sim.breakThrough(-1.0, 0.0, 0.0, impact.absorbed());
                    sim.getVelocity(v);
                    assertTrue(0.5 * mass * v.x * v.x <= kinetic - threshold + 1.0E-9,
                            build + " mit " + speed + " m/s: Block " + (broken + 1) + " kostet nicht seine Energie");
                    broken++;
                    assertTrue(broken <= 64, "endloser Tunnel");
                }
                assertTrue(broken <= Math.floor(energy / threshold), build + " mit " + speed + " m/s bricht " + broken + " Blöcke aus "
                        + energy + " J bei " + threshold + " J je Block");
                assertFalse(BreakMath.of(mass, v.x, threshold).breaks());
            }
        }
    }
}
