package de.lardtom.propwash.dev.client;

import de.lardtom.propwash.dev.MigrationValues;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateOpenings;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.RaceLine;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationLegsTest {
    private static final double GROUND = 64.0;
    private static final double FLAT = -60.0;

    private static List<GateGeometry> generatedRow() {
        List<GateGeometry> gates = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            gates.add(GateGeometry.of(10.5, GROUND, 4.5 + i * 8, 0.0F, GateSize.MEDIUM));
        }
        return gates;
    }

    private static List<GateGeometry> fixtureStacks() {
        return List.of(
                GateGeometry.of(-32.5, FLAT, 16.5, 0.0F, GateSize.MEDIUM),
                GateGeometry.of(-32.5, FLAT + 3.5, 16.5, 180.0F, GateSize.SMALL),
                GateGeometry.of(-32.5, FLAT + 6.0, 16.5, 0.0F, GateSize.SMALL),
                GateGeometry.of(-22.5, FLAT, 16.5, 0.0F, GateSize.MEDIUM),
                GateGeometry.of(-22.5, FLAT + 3.5, 16.5, 0.0F, GateSize.MEDIUM));
    }

    private static void assertCountedOnlyWithLegs(List<GateGeometry> gates, List<Integer> legs) {
        List<Vec3> path = MigrationLegs.path(gates, legs);
        GateOpenings openings = legs::contains;
        List<LapTracker.Event> events = MigrationLegs.fly(1, gates, path, openings);
        assertTrue(MigrationLegs.counted(events, gates.size()), MigrationLegs.describe(events));
        List<LapTracker.Event> plain = MigrationLegs.fly(1, gates, path, GateOpenings.NONE);
        assertFalse(plain.stream().anyMatch(e -> e.type() == LapTracker.EventType.LAP), MigrationLegs.describe(plain));
    }

    @Test
    void generatedRowReturnsBesideTheCourseInsteadOfBackThroughTheGateOnLegs() {
        List<GateGeometry> gates = generatedRow();
        List<Integer> legs = List.of(0);
        assertCountedOnlyWithLegs(gates, legs);
        List<Vec3> path = MigrationLegs.path(gates, legs);
        for (int p = 1; p < path.size(); p++) {
            Vec3 a = path.get(p - 1);
            Vec3 b = path.get(p);
            for (GateGeometry gate : gates) {
                assertTrue(gate.crossing(a.x, a.y, a.z, b.x, b.y, b.z, false, true) < 0.0, "Weg kreuzt ein Gate rückwärts: " + a + " → " + b);
            }
        }
    }

    @Test
    void generatedRowWithoutLegsOnTheLowLineCountsNothing() {
        List<GateGeometry> gates = generatedRow();
        List<Vec3> path = MigrationLegs.path(gates, List.of(0));
        assertTrue(MigrationLegs.fly(1, gates, path, GateOpenings.NONE).stream().noneMatch(e -> e.type() == LapTracker.EventType.START));
    }

    @Test
    void fixtureStacksKeepTheirPathAndCount() {
        List<GateGeometry> gates = fixtureStacks();
        List<Integer> legs = List.of(0, 3);
        assertCountedOnlyWithLegs(gates, legs);
        assertEquals(sameAsBefore(gates, legs), MigrationLegs.path(gates, legs));
    }

    private static List<Vec3> sameAsBefore(List<GateGeometry> gates, List<Integer> legs) {
        List<Vec3> path = new ArrayList<>();
        for (int i = 0; i <= gates.size(); i++) {
            int index = i % gates.size();
            GateGeometry gate = gates.get(index);
            double height = MigrationValues.height(gate, index, legs);
            if (i > 0) {
                GateGeometry previous = gates.get(i - 1);
                double before = MigrationValues.height(previous, i - 1, legs);
                if (RaceLine.needsDetour(previous, gate)) {
                    double reach = Math.max(previous.width(), gate.width()) * 0.5 + 2.0;
                    Vec3 exit = MigrationValues.point(previous, 2.0, reach, before);
                    path.add(exit);
                    path.add(new Vec3(exit.x, height, exit.z));
                    path.add(MigrationValues.point(gate, -2.0, reach, height));
                }
            }
            path.add(MigrationValues.point(gate, -2.0, 0.0, height));
            path.add(MigrationValues.point(gate, 2.0, 0.0, height));
        }
        return path;
    }
}
