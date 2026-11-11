package de.lardtom.propwash.race;

import de.lardtom.propwash.testutil.TestPaths;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GateLegsOpeningTest {
    private static final double Y = 64.0;
    private static final int LADDER_LAYOUT_052 = -432900886;
    private static final int ARENA_LAYOUT_052 = 1462053130;

    private static GateGeometry medium() {
        return GateGeometry.of(0.0, Y, 0.0, 0.0F, GateSize.MEDIUM);
    }

    @Test
    void bottomStripCountsOnlyWithLegs() {
        GateGeometry plain = medium();
        GateGeometry legs = plain.withLegs(true);
        double frame = plain.frame();
        for (double up : new double[]{0.01, frame * 0.25, frame * 0.5, frame}) {
            assertTrue(legs.insideOpening(0.0, up), "Gate auf Beinen zählt bei " + up);
            assertFalse(plain.insideOpening(0.0, up), "Gate mit Balken zählt bei " + up);
        }
        assertFalse(legs.insideOpening(0.0, 0.0), "Boden selbst ist keine Öffnung");
        assertFalse(legs.insideOpening(0.0, -0.01));
        for (double up : new double[]{frame + 0.01, plain.height() * 0.5, plain.height() - frame - 0.01}) {
            assertTrue(legs.insideOpening(0.0, up));
            assertTrue(plain.insideOpening(0.0, up));
        }
        assertFalse(legs.insideOpening(0.0, plain.height() - frame), "oberer Balken bleibt Rahmen");
        assertFalse(legs.insideOpening(plain.innerHalfWidth(), frame * 0.5), "Pfosten bleiben Rahmen");
        assertTrue(legs.insideOpening(plain.innerHalfWidth() - 0.01, frame * 0.5));
        assertEquals(0.0, legs.openingFloor());
        assertEquals(frame, plain.openingFloor());
        assertTrue(legs.legs());
        assertFalse(plain.legs());
    }

    @Test
    void barAndStackedGatesKeepTheirFrame() {
        GateGeometry plain = medium();
        assertSame(plain, plain.withLegs(false));
        GateGeometry back = plain.withLegs(true).withLegs(false);
        assertEquals(plain, back);
        assertEquals(plain.hashCode(), back.hashCode());
        for (GateStacking.Base base : GateStacking.Base.values()) {
            List<double[]> without = boxes(plain, base);
            List<double[]> with = boxes(plain.withLegs(true), base);
            assertEquals(without.size(), with.size(), base.name());
            for (int i = 0; i < without.size(); i++) {
                assertArrayEquals(without.get(i), with.get(i), base + " Box " + i);
            }
        }
        GateGeometry seat = GateGeometry.of(0.0, GateStacking.seat(plain), 0.0, 0.0F, GateSize.SMALL);
        assertTrue(GateStacking.restsOn(seat, plain.withLegs(true)), "Stapeln sieht das Flag nicht");
        assertFalse(seat.legs());
        assertFalse(seat.insideOpening(0.0, seat.frame() * 0.5));
    }

    private static List<double[]> boxes(GateGeometry gate, GateStacking.Base base) {
        List<double[]> out = new ArrayList<>();
        gate.boxes((x0, y0, z0, x1, y1, z1) -> out.add(new double[]{x0, y0, z0, x1, y1, z1}), base);
        return out;
    }

    @Test
    void legsOnlyForFramedGates() {
        GateGeometry pole = GateGeometry.element(0.0, Y, 0.0, 0.0F, CourseElement.POLE, 0, 1.0, 4.0, GateTilt.NONE);
        assertSame(pole, pole.withLegs(true));
        GateGeometry freeform = GateGeometry.freeform(0.0, Y, 0.0, FreeformRegion.single(FreeformRegion.AXIS_Z), 0);
        assertSame(freeform, freeform.withLegs(true));
        assertFalse(freeform.legs());
    }

    @Test
    void crossingWithAndWithoutFlag() {
        GateGeometry plain = medium();
        GateGeometry legs = plain.withLegs(true);
        double low = Y + plain.frame() * 0.5;
        double mid = Y + plain.height() * 0.5;
        assertEquals(GateGeometry.NO_CROSSING, plain.crossing(0.0, low, -1.0, 0.0, low, 1.0, true));
        assertEquals(0.5, plain.crossing(0.0, low, -1.0, 0.0, low, 1.0, true, true), 1.0E-9);
        assertEquals(0.5, legs.crossing(0.0, low, -1.0, 0.0, low, 1.0, true), 1.0E-9);
        assertEquals(GateGeometry.NO_CROSSING, legs.crossing(0.0, low, -1.0, 0.0, low, 1.0, true, false));
        assertEquals(GateGeometry.NO_CROSSING, plain.crossing(0.0, low, 1.0, 0.0, low, -1.0, false));
        assertEquals(0.5, plain.crossing(0.0, low, 1.0, 0.0, low, -1.0, false, true), 1.0E-9);
        assertEquals(0.5, legs.crossing(0.0, low, 1.0, 0.0, low, -1.0, false), 1.0E-9);
        assertEquals(GateGeometry.NO_CROSSING, legs.crossing(0.0, low, 1.0, 0.0, low, -1.0, true));
        assertEquals(GateGeometry.NO_CROSSING, legs.crossing(0.0, low, -1.0, 0.0, low, 1.0, false));
        assertEquals(0.5, plain.crossing(0.0, mid, -1.0, 0.0, mid, 1.0, true), 1.0E-9);
        assertEquals(0.5, legs.crossing(0.0, mid, -1.0, 0.0, mid, 1.0, true), 1.0E-9);
        assertEquals(GateGeometry.NO_CROSSING, legs.crossing(0.0, Y - 0.1, -1.0, 0.0, Y - 0.1, 1.0, true), "unter dem Boden");
        assertEquals(0.5, legs.stageCrossing(0, 0.0, low, -1.0, 0.0, low, 1.0), 1.0E-9);
        assertEquals(GateGeometry.NO_CROSSING, plain.stageCrossing(0, 0.0, low, -1.0, 0.0, low, 1.0));
        GateGeometry turned = GateGeometry.of(5.0, Y, 5.0, 90.0F, GateSize.LARGE).withLegs(true);
        double lowTurned = Y + turned.frame() * 0.5;
        double t = turned.crossing(5.0 - turned.normalX(), lowTurned, 5.0 - turned.normalZ(), 5.0 + turned.normalX(), lowTurned,
                5.0 + turned.normalZ(), true);
        assertEquals(0.5, t, 1.0E-9);
    }

    @Test
    void equalsAndHashCodeTellLegsApart() {
        GateGeometry plain = medium();
        GateGeometry legs = plain.withLegs(true);
        assertNotEquals(plain, legs);
        assertNotEquals(legs, plain);
        assertEquals(legs, medium().withLegs(true));
        assertEquals(legs.hashCode(), medium().withLegs(true).hashCode());
        assertNotEquals(plain.hashCode(), legs.hashCode());
        assertEquals(medium().hashCode(), plain.hashCode());
        assertTrue(plain.sameAs(legs), "sameAs vergleicht nur die Lage");
    }

    @Test
    void ghostSplitsCountTheBottomStripOnlyWithOpenings() {
        long ms = 1_000_000L;
        int samples = 81;
        double[] positions = new double[samples * 3];
        float[] rotations = new float[samples * 4];
        for (int i = 0; i < samples; i++) {
            double z = i * 0.5 - 0.25;
            positions[i * 3 + 1] = z > 5.0 && z < 15.0 ? Y + GateSize.MEDIUM.frame() * 0.5 : Y + 1.5;
            positions[i * 3 + 2] = z;
            rotations[i * 4 + 3] = 1.0F;
        }
        GhostTrack track = new GhostTrack(GhostTrack.INTERVAL_NANOS, positions, rotations, 4_000L * ms, "", 0);
        List<GateGeometry> gates = List.of(
                GateGeometry.of(0.0, Y, 0.0, 0.0F, GateSize.MEDIUM),
                GateGeometry.of(0.0, Y, 10.0, 0.0F, GateSize.MEDIUM),
                GateGeometry.of(0.0, Y, 20.0, 0.0F, GateSize.MEDIUM));
        long[] plain = track.splits(gates);
        assertEquals(LapTracker.NONE, plain[1]);
        assertEquals(LapTracker.NONE, plain[2]);
        assertArrayEquals(plain, track.splits(gates, GateOpenings.NONE));
        int[] asked = new int[1];
        long[] counted = track.splits(gates, index -> {
            asked[0]++;
            return index == 1;
        });
        assertTrue(GhostTrack.completeSplits(counted), java.util.Arrays.toString(counted));
        assertEquals(1_025L * ms, counted[1], 2 * ms);
        assertEquals(2_025L * ms, counted[2], 2 * ms);
        assertEquals(1, asked[0], "nur der Durchflug unten fragt");
    }

    private static List<CourseData.Gate> ladder() {
        return List.of(
                new CourseData.Gate(0, 0.5, Y, 0.5, 0.0F, GateSize.MEDIUM),
                new CourseData.Gate(1, 0.5, Y + GateSize.MEDIUM.height() - GateSize.MEDIUM.frame(), 0.5, 0.0F, GateSize.MEDIUM),
                new CourseData.Gate(2, 12.5, Y, 0.5, 90.0F, GateSize.SMALL));
    }

    @Test
    void courseDataNeverSetsLegsAndKeepsLayout() {
        CourseData course = new CourseData(9, "minecraft:overworld", ladder());
        for (GateGeometry gate : course.geometry()) {
            assertFalse(gate.legs());
        }
        for (CourseData.Gate gate : course.gates()) {
            assertFalse(gate.geometry().legs());
        }
        List<GateGeometry> geometry = course.geometry();
        assertEquals(GateStacking.Base.LEGS, GateStacking.base(geometry, 0));
        assertEquals(GateStacking.Base.STACKED, GateStacking.base(geometry, 1));
        assertEquals(GateStacking.Base.BAR, GateStacking.base(geometry, 2));
        assertEquals(LADDER_LAYOUT_052, course.layoutHash(), "Layout-Hash wie in 0.5.2");
        assertEquals(geometry, course.geometry());
        List<GateGeometry> entities = new ArrayList<>(geometry);
        entities.set(0, geometry.getFirst().withLegs(true));
        assertNotEquals(entities, geometry, "Entity-Geometrie mit Beinen ist nicht die Kurs-Geometrie");
        assertEquals(LADDER_LAYOUT_052, new CourseData(9, "minecraft:overworld", ladder()).layoutHash());
    }

    @Test
    void arenaLadderKeepsItsLayoutHash() throws IOException {
        Path file = TestPaths.mainResources().resolve("data/propwash/structure/world_course/drone_arena.nbt");
        CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        ListTag entities = root.getListOrEmpty("entities");
        CourseData.Gate[] slots = new CourseData.Gate[entities.size()];
        for (int i = 0; i < entities.size(); i++) {
            CompoundTag entity = entities.getCompoundOrEmpty(i);
            CompoundTag nbt = entity.getCompoundOrEmpty("nbt");
            ListTag pos = entity.getListOrEmpty("pos");
            int slot = nbt.getCompoundOrEmpty("WorldCourse").getIntOr("Slot", -1);
            slots[slot] = new CourseData.Gate(slot, pos.getDoubleOr(0, 0), pos.getDoubleOr(1, 0), pos.getDoubleOr(2, 0),
                    nbt.getListOrEmpty("Rotation").getFloatOr(0, 0.0F), GateSize.byKey(nbt.getStringOr("Size", "")));
        }
        CourseData course = new CourseData(1, "minecraft:overworld", List.of(slots));
        List<GateGeometry> gates = course.geometry();
        List<Integer> legs = new ArrayList<>();
        List<Integer> stacked = new ArrayList<>();
        for (int i = 0; i < gates.size(); i++) {
            GateStacking.Base base = GateStacking.base(gates, i);
            if (base == GateStacking.Base.LEGS) {
                legs.add(i);
            } else if (base == GateStacking.Base.STACKED) {
                stacked.add(i);
            }
            assertFalse(gates.get(i).legs());
        }
        assertEquals(1, legs.size(), "Leiter mit genau einem Gate auf Beinen");
        assertEquals(2, stacked.size(), "Leiter aus drei Gates");
        GateGeometry bottom = gates.get(legs.getFirst());
        double low = bottom.y() + bottom.frame() * 0.5;
        double x0 = bottom.x() - bottom.normalX();
        double z0 = bottom.z() - bottom.normalZ();
        double x1 = bottom.x() + bottom.normalX();
        double z1 = bottom.z() + bottom.normalZ();
        assertEquals(GateGeometry.NO_CROSSING, bottom.crossing(x0, low, z0, x1, low, z1, true));
        assertTrue(bottom.withLegs(true).crossing(x0, low, z0, x1, low, z1, true) >= 0.0);
        assertEquals(ARENA_LAYOUT_052, course.layoutHash(), "Layout-Hash der Drohnen-Arena wie in 0.5.2");
    }
}
