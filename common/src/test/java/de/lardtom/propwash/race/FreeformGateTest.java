package de.lardtom.propwash.race;

import com.google.gson.Gson;
import de.lardtom.propwash.race.client.FreeformView;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RulePreset;
import de.lardtom.propwash.server.rules.RuleValues;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FreeformGateTest {
    private static final long MS = 1_000_000L;

    static final class Grid implements FreeformFill.Probe {
        final Set<Long> solid = new HashSet<>();
        final Set<Long> unloaded = new HashSet<>();
        int minY = -64;
        int maxY = 64;

        static long key(int x, int y, int z) {
            return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
        }

        Grid fill(int x0, int y0, int z0, int x1, int y1, int z1) {
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                    for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                        this.solid.add(key(x, y, z));
                    }
                }
            }
            return this;
        }

        Grid carve(int x0, int y0, int z0, int x1, int y1, int z1) {
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                    for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                        this.solid.remove(key(x, y, z));
                    }
                }
            }
            return this;
        }

        @Override
        public FreeformFill.Cell at(int x, int y, int z) {
            if (y < this.minY || y > this.maxY) {
                return FreeformFill.Cell.OUTSIDE;
            }
            long k = key(x, y, z);
            if (this.unloaded.contains(k)) {
                return FreeformFill.Cell.UNLOADED;
            }
            return this.solid.contains(k) ? FreeformFill.Cell.SOLID : FreeformFill.Cell.OPEN;
        }
    }

    private static Grid wall() {
        return new Grid().fill(-40, -40, 0, 40, 40, 0);
    }

    private static Grid corridor() {
        Grid grid = new Grid();
        grid.fill(-1, 0, 0, 3, 0, 40);
        grid.fill(-1, 4, 0, 3, 4, 40);
        grid.fill(-1, 0, 0, -1, 4, 40);
        grid.fill(3, 0, 0, 3, 4, 40);
        return grid;
    }

    private static GateGeometry geometry(FreeformFill.Result result, int variant) {
        assertTrue(result.ok(), result.status().name());
        return GateGeometry.freeform(result.anchorX(), result.anchorY(), result.anchorZ(), result.region(), variant);
    }

    @Test
    void corridorCrossSectionBecomesTheGate() {
        FreeformFill.Result result = FreeformFill.fill(corridor(), FreeformRegion.AXIS_Z, 1, 2, 10, 20);
        assertEquals(FreeformFill.Status.OK, result.status());
        FreeformRegion region = result.region();
        assertNotNull(region);
        assertEquals(3, region.width());
        assertEquals(3, region.height());
        assertEquals(9, region.cells());
        assertEquals(1.5, result.anchorX(), 1.0E-9);
        assertEquals(2.5, result.anchorY(), 1.0E-9);
        assertEquals(10.5, result.anchorZ(), 1.0E-9);
        assertEquals(1, region.targetA());
        assertEquals(1, region.targetB());
        GateGeometry gate = geometry(result, 0);
        assertEquals(1.5, gate.centerX(), 1.0E-9);
        assertEquals(2.5, gate.centerY(), 1.0E-9);
        assertEquals(10.5, gate.centerZ(), 1.0E-9);
        assertEquals(0.0F, gate.heading(), 1.0E-6F);
    }

    @Test
    void lShapedOpeningKeepsOnlyItsCells() {
        Grid grid = wall();
        grid.carve(0, 1, 0, 2, 1, 0);
        grid.carve(0, 2, 0, 0, 3, 0);
        FreeformFill.Result result = FreeformFill.fill(grid, FreeformRegion.AXIS_Z, 0, 1, 0, 20);
        FreeformRegion region = result.region();
        assertNotNull(region);
        assertEquals(3, region.width());
        assertEquals(3, region.height());
        assertEquals(5, region.cells());
        assertTrue(region.contains(2, 0));
        assertTrue(region.contains(0, 2));
        assertFalse(region.contains(2, 2));
        assertFalse(region.contains(1, 1));
        assertTrue(region.contains(region.targetA(), region.targetB()));
        GateGeometry gate = geometry(result, 0);
        assertTrue(gate.stageCrossing(0, 2.5, 1.5, -1.0, 2.5, 1.5, 1.0) >= 0.0, "Fuß des L zählt");
        assertEquals(GateGeometry.NO_CROSSING, gate.stageCrossing(0, 2.5, 3.5, -1.0, 2.5, 3.5, 1.0), "leere Ecke der Box zählt nicht");
        assertEquals(GateGeometry.NO_CROSSING, gate.stageCrossing(0, 1.5, 2.5, -1.0, 1.5, 2.5, 1.0), "Block in der Ecke zählt nicht");
    }

    @Test
    void roundCaveMouth() {
        Grid grid = wall();
        int expected = 0;
        for (int x = -4; x <= 4; x++) {
            for (int y = 6; y <= 14; y++) {
                double dx = x + 0.5 - 0.5;
                double dy = y + 0.5 - 10.5;
                if (dx * dx + dy * dy <= 16.0) {
                    grid.carve(x, y, 0, x, y, 0);
                    expected++;
                }
            }
        }
        FreeformFill.Result result = FreeformFill.fill(grid, FreeformRegion.AXIS_Z, 0, 10, 0, 20);
        FreeformRegion region = result.region();
        assertNotNull(region);
        assertEquals(expected, region.cells());
        assertEquals(9, region.width());
        assertEquals(9, region.height());
        assertEquals(4, region.targetA());
        assertEquals(4, region.targetB());
    }

    @Test
    void pillarInsideStaysAHole() {
        Grid grid = wall();
        grid.carve(0, 1, 0, 4, 4, 0);
        grid.fill(2, 2, 0, 2, 2, 0);
        FreeformFill.Result result = FreeformFill.fill(grid, FreeformRegion.AXIS_Z, 0, 1, 0, 20);
        FreeformRegion region = result.region();
        assertNotNull(region);
        assertEquals(19, region.cells());
        assertFalse(region.contains(2, 1));
        GateGeometry gate = geometry(result, 0);
        assertEquals(GateGeometry.NO_CROSSING, gate.stageCrossing(0, 2.5, 2.5, -2.0, 2.5, 2.5, 2.0), "durch den Pfeiler zählt nicht");
        assertTrue(gate.stageCrossing(0, 3.5, 2.5, -2.0, 3.5, 2.5, 2.0) >= 0.0);
        assertEquals(22, region.edgeCount(), "Außenrand 18 Kanten und 4 um den Pfeiler");
    }

    @Test
    void openingsThatLeakAreRefused() {
        Grid slot = wall();
        slot.maxY = 30;
        slot.carve(0, 1, 0, 1, 40, 0);
        FreeformFill.Result toSky = FreeformFill.fill(slot, FreeformRegion.AXIS_Z, 0, 2, 0, 20);
        assertEquals(FreeformFill.Status.OPEN, toSky.status());
        assertNull(toSky.region());
        Grid field = new Grid().fill(-200, 0, -200, 200, 0, 200);
        FreeformFill.Result open = FreeformFill.fill(field, FreeformRegion.AXIS_Z, 0, 1, 0, 20);
        assertEquals(FreeformFill.Status.OPEN, open.status());
        FreeformFill.Result blocked = FreeformFill.fill(wall(), FreeformRegion.AXIS_Z, 0, 0, 0, 20);
        assertEquals(FreeformFill.Status.START_BLOCKED, blocked.status());
        Grid unloaded = wall();
        unloaded.carve(0, 1, 0, 2, 2, 0);
        unloaded.unloaded.add(Grid.key(2, 2, 0));
        assertEquals(FreeformFill.Status.UNLOADED, FreeformFill.fill(unloaded, FreeformRegion.AXIS_Z, 0, 1, 0, 20).status());
    }

    @Test
    void limitIsInclusiveAtTwentyAndRefusesTwentyOne() {
        Grid twenty = wall();
        twenty.carve(0, 1, 0, 19, 20, 0);
        FreeformFill.Result ok = FreeformFill.fill(twenty, FreeformRegion.AXIS_Z, 5, 5, 0, 20);
        assertEquals(FreeformFill.Status.OK, ok.status());
        assertEquals(400, ok.region().cells());
        Grid wide = wall();
        wide.carve(0, 1, 0, 20, 20, 0);
        FreeformFill.Result tooWide = FreeformFill.fill(wide, FreeformRegion.AXIS_Z, 5, 5, 0, 20);
        assertEquals(FreeformFill.Status.TOO_BIG, tooWide.status());
        assertEquals(21, tooWide.width());
        assertEquals(20, tooWide.height());
        assertEquals(FreeformFill.Status.OK, FreeformFill.fill(wide, FreeformRegion.AXIS_Z, 5, 5, 0, 21).status());
        Grid tall = wall();
        tall.carve(0, 1, 0, 19, 21, 0);
        assertEquals(FreeformFill.Status.TOO_BIG, FreeformFill.fill(tall, FreeformRegion.AXIS_Z, 5, 5, 0, 20).status());
        Grid tiny = wall();
        tiny.carve(0, 1, 0, 1, 1, 0);
        assertEquals(FreeformFill.Status.TOO_BIG, FreeformFill.fill(tiny, FreeformRegion.AXIS_Z, 0, 1, 0, 1).status());
        assertEquals(FreeformFill.Status.OK, FreeformFill.fill(tiny, FreeformRegion.AXIS_Z, 0, 1, 0, 2).status());
    }

    @Test
    void cellsTouchingOnlyAtCornersDoNotLeak() {
        Grid grid = wall();
        grid.carve(0, 1, 0, 0, 1, 0);
        grid.carve(1, 2, 0, 1, 2, 0);
        grid.carve(2, 3, 0, 2, 3, 0);
        FreeformFill.Result result = FreeformFill.fill(grid, FreeformRegion.AXIS_Z, 0, 1, 0, 20);
        assertEquals(1, result.region().cells(), "4er-Nachbarschaft: diagonal ist zu");
        Grid staircase = wall();
        staircase.carve(0, 1, 0, 3, 3, 0);
        staircase.carve(4, 4, 0, 6, 8, 0);
        FreeformFill.Result stairs = FreeformFill.fill(staircase, FreeformRegion.AXIS_Z, 1, 2, 0, 20);
        assertEquals(12, stairs.region().cells());
    }

    @Test
    void verticalAndHorizontalPlanes() {
        Grid hallX = new Grid();
        hallX.fill(0, 0, -1, 40, 0, 2);
        hallX.fill(0, 3, -1, 40, 3, 2);
        hallX.fill(0, 0, -1, 40, 3, -1);
        hallX.fill(0, 0, 2, 40, 3, 2);
        FreeformFill.Result x = FreeformFill.fill(hallX, FreeformRegion.AXIS_X, 10, 1, 0, 20);
        assertEquals(2, x.region().width());
        assertEquals(2, x.region().height());
        GateGeometry gx = geometry(x, 1);
        assertEquals(-1.0, gx.normalX(), 1.0E-9);
        assertEquals(10.5, gx.x(), 1.0E-9);
        assertTrue(gx.stageCrossing(0, 12.0, 1.5, 0.5, 9.0, 1.5, 0.5) >= 0.0);
        assertEquals(GateGeometry.NO_CROSSING, gx.stageCrossing(0, 9.0, 1.5, 0.5, 12.0, 1.5, 0.5));
        Grid shaft = new Grid();
        shaft.fill(-1, 0, -1, 2, 20, 2);
        shaft.carve(0, 1, 0, 1, 20, 1);
        FreeformFill.Result y = FreeformFill.fill(shaft, FreeformRegion.AXIS_Y, 0, 10, 0, 20);
        assertEquals(4, y.region().cells());
        GateGeometry gy = geometry(y, 1);
        assertEquals(-1.0, gy.normalY(), 1.0E-9);
        assertTrue(gy.stageCrossing(0, 1.0, 12.0, 1.0, 1.0, 8.0, 1.0) >= 0.0, "Sturz durch den Schacht");
    }

    @Test
    void aimFollowsTheClickedFaceAndTheLook() {
        FreeformFill.Aim floor = FreeformFill.aim(0, 0, 0, 0, 1, 0, false, 0.1, -0.7, 0.7, false);
        assertEquals(FreeformRegion.AXIS_Z, floor.axis());
        assertEquals(0, floor.variant());
        assertEquals(1, floor.seedY());
        FreeformFill.Aim back = FreeformFill.aim(0, 0, 0, 0, 1, 0, false, -0.9, -0.3, 0.1, false);
        assertEquals(FreeformRegion.AXIS_X, back.axis());
        assertEquals(1, back.variant());
        FreeformFill.Aim reversed = FreeformFill.aim(0, 0, 0, 0, 1, 0, false, -0.9, -0.3, 0.1, true);
        assertEquals(0, reversed.variant());
        FreeformFill.Aim sideWall = FreeformFill.aim(3, 2, 10, -1, 0, 0, false, -0.3, -0.2, 0.9, false);
        assertEquals(FreeformRegion.AXIS_Z, sideWall.axis(), "Seitenwand im Gang: Ebene quer zum Gang");
        assertEquals(2, sideWall.seedX());
        FreeformFill.Aim shaftWall = FreeformFill.aim(-1, 10, 0, 1, 0, 0, false, 0.2, -0.95, 0.1, false);
        assertEquals(FreeformRegion.AXIS_Y, shaftWall.axis(), "Innenwand eines Schachts beim Blick nach unten: Tauch-Ebene");
        assertEquals(1, shaftWall.variant());
        FreeformFill.Aim grass = FreeformFill.aim(4, 1, 4, 0, 1, 0, true, 0.0, -0.5, -0.8, false);
        assertEquals(4, grass.seedX());
        assertEquals(1, grass.seedY());
        assertEquals(FreeformRegion.AXIS_Z, grass.axis());
        assertEquals(1, grass.variant());
    }

    @Test
    void crossingCountsOnlyInsideStoredCellsAndOnlyForward() {
        GateGeometry gate = geometry(FreeformFill.fill(corridor(), FreeformRegion.AXIS_Z, 1, 2, 10, 20), 0);
        Passage passage = gate.stages().getFirst();
        assertEquals(0.5, gate.stageCrossing(0, 1.5, 2.5, 9.0, 1.5, 2.5, 12.0), 1.0E-9);
        assertEquals(GateGeometry.NO_CROSSING, gate.stageCrossing(0, 1.5, 2.5, 12.0, 1.5, 2.5, 9.0));
        assertTrue(passage.crossing(1.5, 2.5, 12.0, 1.5, 2.5, 9.0, false) >= 0.0);
        assertEquals(GateGeometry.NO_CROSSING, gate.stageCrossing(0, 4.5, 2.5, 9.0, 4.5, 2.5, 12.0), "neben dem Gang");
        assertEquals(GateGeometry.NO_CROSSING, gate.stageCrossing(0, 1.5, 4.5, 9.0, 1.5, 4.5, 12.0), "in der Decke");
        assertTrue(gate.stageCrossing(0, 0.01, 1.01, 9.0, 0.01, 1.01, 12.0) >= 0.0, "Rand der ersten Zelle zählt");
        assertEquals(GateGeometry.NO_CROSSING, gate.stageCrossing(0, -0.01, 1.5, 9.0, -0.01, 1.5, 12.0));
        GateGeometry flipped = gate.withVariant(1);
        assertEquals(GateGeometry.NO_CROSSING, flipped.stageCrossing(0, 1.5, 2.5, 9.0, 1.5, 2.5, 12.0));
        assertTrue(flipped.stageCrossing(0, 1.5, 2.5, 12.0, 1.5, 2.5, 9.0) >= 0.0);
        assertEquals(gate.at(5.0, 6.0, 7.0).region(), gate.region());
        assertNotEquals(gate, flipped);
        assertEquals(1, gate.waypoints().size());
        assertEquals(1.5, gate.waypoints().getFirst().x(), 1.0E-9);
        assertEquals(1.0, gate.waypoints().getFirst().dz(), 1.0E-9);
        double[] box = new double[6];
        gate.bounds((minX, minY, minZ, maxX, maxY, maxZ) -> {
            box[0] = minX;
            box[1] = minY;
            box[2] = minZ;
            box[3] = maxX;
            box[4] = maxY;
            box[5] = maxZ;
        });
        assertEquals(0.0, box[0], 1.0E-9);
        assertEquals(1.0, box[1], 1.0E-9);
        assertEquals(10.0, box[2], 1.0E-9);
        assertEquals(3.0, box[3], 1.0E-9);
        assertEquals(4.0, box[4], 1.0E-9);
        assertEquals(11.0, box[5], 1.0E-9);
        int[] parts = {0};
        gate.boxes((minX, minY, minZ, maxX, maxY, maxZ) -> parts[0]++);
        assertEquals(0, parts[0], "kein Rahmen, keine Kollision");
    }

    @Test
    void regionTextAndBytesRoundTrip() {
        Grid grid = wall();
        grid.carve(0, 1, 0, 4, 4, 0);
        grid.fill(2, 2, 0, 2, 2, 0);
        FreeformRegion region = FreeformFill.fill(grid, FreeformRegion.AXIS_Z, 0, 1, 0, 20).region();
        String text = region.encode();
        assertTrue(text.startsWith("f1:z:5x4:0,0:"), text);
        assertEquals(region, FreeformRegion.decode(text));
        assertEquals(region, FreeformRegion.of(region.axis(), region.width(), region.height(), region.seedA(), region.seedB(), region.packed()));
        assertNull(FreeformRegion.decode("f1:z:5x4:2,1:" + text.substring(text.lastIndexOf(':') + 1)), "Saat im Pfeiler ist ungültig");
        assertNull(FreeformRegion.decode("f1:q:5x4:0,0:AAAA"));
        assertNull(FreeformRegion.decode("f1:z:0x4:0,0:"));
        assertNull(FreeformRegion.decode("garbage"));
        assertNull(FreeformRegion.decode(null));
        assertNull(FreeformRegion.of(FreeformRegion.AXIS_X, 129, 1, 0, 0, new byte[17]));
    }

    @Test
    void courseDataCarriesTheRegionAndTheLayoutNoticesChanges() {
        GateGeometry gate = geometry(FreeformFill.fill(corridor(), FreeformRegion.AXIS_Z, 1, 2, 10, 20), 1);
        List<CourseData.Gate> gates = new ArrayList<>();
        gates.add(new CourseData.Gate(0, gate.x(), gate.y(), gate.z(), 0.0F, GateSize.MEDIUM, GateMount.STANDING, false, GateTilt.NONE, 0.0, 0.0,
                CourseElement.FREEFORM, 1, gate.region()));
        gates.add(new CourseData.Gate(1, 0.5, 64.0, 30.5, 0.0F, GateSize.MEDIUM));
        CourseData data = new CourseData(3, "minecraft:overworld", gates);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        CourseData.STREAM_CODEC.encode(buf, data);
        CourseData decoded = CourseData.STREAM_CODEC.decode(buf);
        assertFalse(buf.isReadable());
        assertEquals(data, decoded);
        assertEquals(gate, decoded.geometry().getFirst(), "Server- und Client-Geometrie sind gleich");
        assertEquals(3.0, decoded.gates().getFirst().width(), 1.0E-9);
        Grid grid = corridor();
        grid.fill(0, 1, 10, 0, 1, 10);
        FreeformRegion smaller = FreeformFill.fill(grid, FreeformRegion.AXIS_Z, 1, 2, 10, 20).region();
        List<CourseData.Gate> changed = new ArrayList<>(gates);
        changed.set(0, new CourseData.Gate(0, gate.x(), gate.y(), gate.z(), 0.0F, GateSize.MEDIUM, GateMount.STANDING, false, GateTilt.NONE, 0.0,
                0.0, CourseElement.FREEFORM, 1, smaller));
        assertNotEquals(data.layoutHash(), new CourseData(3, "minecraft:overworld", changed).layoutHash());
        assertEquals(data.layoutHash(), decoded.layoutHash());
        CourseData.Gate plain = new CourseData.Gate(0, 0.5, 64.0, 0.5, 0.0F, GateSize.MEDIUM, GateMount.STANDING, false, GateTilt.NONE, 0.0, 0.0,
                CourseElement.POLE, 0, gate.region());
        assertNull(plain.region(), "nur Freiform-Gates tragen eine Fläche");
    }

    @Test
    void worldDataStoresTheRegionAsText() {
        FreeformRegion region = FreeformFill.fill(corridor(), FreeformRegion.AXIS_Z, 1, 2, 10, 20).region();
        CourseRegistry.GateRecord record = new CourseRegistry.GateRecord();
        record.uuid = "00000000-0000-0000-0000-000000000001";
        record.x = 1.5;
        record.y = 2.5;
        record.z = 10.5;
        record.kind = CourseElement.FREEFORM.key();
        record.variant = 1;
        record.region = region.encode();
        Gson gson = new Gson();
        String json = gson.toJson(record);
        CourseRegistry.GateRecord loaded = gson.fromJson(json, CourseRegistry.GateRecord.class);
        assertEquals(CourseElement.FREEFORM, loaded.kind());
        assertEquals(region, loaded.region());
        CourseRegistry.GateRecord gateRecord = gson.fromJson("{\"uuid\":\"00000000-0000-0000-0000-000000000002\",\"size\":\"medium\"}",
                CourseRegistry.GateRecord.class);
        assertNull(gateRecord.region());
        assertFalse(gson.toJson(gateRecord).contains("region"), "Gates aus 0.4.0 bleiben Byte für Byte gleich");
    }

    @Test
    void lapsCountThroughFreeformGatesOnClientAndServer() {
        Grid grid = new Grid();
        grid.fill(-1, 0, 0, 13, 0, 30);
        grid.fill(-1, 4, 0, 13, 4, 30);
        grid.fill(-1, 0, 10, 3, 4, 10).carve(0, 1, 10, 2, 3, 10);
        grid.fill(9, 0, 10, 13, 4, 10).carve(10, 1, 10, 12, 3, 10);
        grid.fill(4, 1, 10, 8, 3, 10);
        GateGeometry first = geometry(FreeformFill.fill(grid, FreeformRegion.AXIS_Z, 1, 2, 10, 20), 0);
        GateGeometry second = geometry(FreeformFill.fill(grid, FreeformRegion.AXIS_Z, 11, 2, 10, 20), 1);
        LapTracker tracker = new LapTracker();
        tracker.setLapTarget(1);
        List<GateGeometry> course = List.of(first, second);
        tracker.setCourse(5, course, 0L);
        long[] t = {0L};
        double[][] path = {{1.5, 2.5, 8.0}, {1.5, 2.5, 12.0}, {6.0, 2.5, 14.0}, {11.5, 2.5, 12.0}, {11.5, 2.5, 8.0}, {6.0, 2.5, 6.0},
                {1.5, 2.5, 8.0}, {1.5, 2.5, 12.0}};
        List<LapTracker.Event> events = new ArrayList<>();
        for (double[] p : path) {
            t[0] += 400 * MS;
            tracker.update(t[0], p[0], p[1], p[2]);
            events.addAll(tracker.drainEvents());
        }
        assertEquals(List.of(LapTracker.EventType.START, LapTracker.EventType.GATE, LapTracker.EventType.LAP, LapTracker.EventType.FINISH),
                events.stream().map(LapTracker.Event::type).toList());
        LapVerifier verifier = new LapVerifier();
        List<CourseElement> kinds = CourseCheckpoints.kinds(course);
        assertEquals(2, CourseCheckpoints.count(kinds));
        long now = 0L;
        double[] last = null;
        for (double[] p : path) {
            now += 400 * MS;
            if (last != null) {
                for (int g = 0; g < course.size(); g++) {
                    GateGeometry gate = course.get(g);
                    for (int s = 0; s < gate.stages().size(); s++) {
                        double hit = gate.stages().get(s).crossing(last[0], last[1], last[2], p[0], p[1], p[2], true);
                        if (hit >= 0.0) {
                            verifier.record(5, CourseCheckpoints.index(kinds, g, s), now);
                        }
                    }
                }
            }
            last = p;
        }
        assertNotNull(verifier.find(5, 2, LapVerifier.NO_SEQUENCE, 0L), "Server zählt dieselbe Runde");
    }

    @Test
    void wrongWayAndMissedGatesAreReported() {
        Grid grid = new Grid();
        grid.fill(-1, 0, 10, 3, 4, 10).carve(0, 1, 10, 2, 3, 10);
        grid.fill(-1, 0, 20, 3, 4, 20).carve(0, 1, 20, 2, 3, 20);
        GateGeometry first = geometry(FreeformFill.fill(grid, FreeformRegion.AXIS_Z, 1, 2, 10, 20), 0);
        GateGeometry second = geometry(FreeformFill.fill(grid, FreeformRegion.AXIS_Z, 1, 2, 20, 20), 1);
        LapTracker tracker = new LapTracker();
        tracker.setCourse(6, List.of(first, second), 0L);
        tracker.update(100 * MS, 1.5, 2.5, 8.0);
        tracker.update(200 * MS, 1.5, 2.5, 12.0);
        tracker.update(300 * MS, 1.5, 2.5, 18.0);
        tracker.update(400 * MS, 1.5, 2.5, 22.0);
        List<LapTracker.EventType> types = tracker.drainEvents().stream().map(LapTracker.Event::type).toList();
        assertEquals(List.of(LapTracker.EventType.START, LapTracker.EventType.WRONG_WAY), types);
        tracker.update(500 * MS, 5.0, 2.5, 22.0);
        tracker.update(600 * MS, 5.0, 2.5, 8.0);
        tracker.update(700 * MS, 1.5, 2.5, 8.0);
        tracker.update(800 * MS, 1.5, 2.5, 12.0);
        assertEquals(List.of(LapTracker.EventType.MISSED), tracker.drainEvents().stream().map(LapTracker.Event::type).toList());
    }

    @Test
    void integrityNoticesOpenedAndBlockedCells() {
        Grid grid = corridor();
        FreeformFill.Result result = FreeformFill.fill(grid, FreeformRegion.AXIS_Z, 1, 2, 10, 20);
        FreeformRegion region = result.region();
        assertTrue(FreeformFill.check(grid, region, result.plane(), result.minA(), result.minB()).intact());
        grid.carve(3, 2, 10, 3, 2, 10);
        FreeformFill.Integrity opened = FreeformFill.check(grid, region, result.plane(), result.minA(), result.minB());
        assertEquals(1, opened.openEdges());
        assertFalse(opened.intact());
        grid.fill(1, 2, 10, 1, 2, 10);
        assertEquals(1, FreeformFill.check(grid, region, result.plane(), result.minA(), result.minB()).blockedCells());
        GateGeometry gate = geometry(result, 0);
        assertTrue(gate.stageCrossing(0, 1.5, 2.5, 9.0, 1.5, 2.5, 12.0) >= 0.0, "gespeicherte Fläche gilt weiter");
        assertEquals(GateGeometry.NO_CROSSING, gate.stageCrossing(0, 3.5, 2.5, 9.0, 3.5, 2.5, 12.0), "neue Lücke zählt nicht");
    }

    @Test
    void outlineMergesIntoLedRuns() {
        Grid grid = wall();
        grid.carve(0, 1, 0, 19, 20, 0);
        FreeformRegion square = FreeformFill.fill(grid, FreeformRegion.AXIS_Z, 3, 3, 0, 20).region();
        FreeformView view = FreeformView.of(square);
        assertEquals(4, view.segmentCount());
        assertEquals(20, view.runCount());
        assertTrue(view.intact());
        assertEquals(80, square.edgeCount());
        FreeformView leaking = FreeformView.of(square, (a, b) -> a == 20 && b == 5, 0);
        assertEquals(1, leaking.leaks());
        assertEquals(6, leaking.segmentCount(), "die offene Kante leuchtet als eigenes Stück");
        long start = System.nanoTime();
        for (int i = 0; i < 200; i++) {
            FreeformView.of(square);
        }
        assertTrue(System.nanoTime() - start < 2_000_000_000L, "400 Felder: Aufbau schnell genug");
    }

    @Test
    void syncBatchesStayBelowThePacketLimit() {
        FreeformRegion big = FreeformRegion.of(FreeformRegion.AXIS_Z, 64, 64, 0, 0, new boolean[64 * 64]);
        assertNull(big);
        boolean[] cells = new boolean[64 * 64];
        Arrays.fill(cells, true);
        big = FreeformRegion.of(FreeformRegion.AXIS_Z, 64, 64, 0, 0, cells);
        assertNotNull(big);
        List<CourseData> courses = new ArrayList<>();
        for (int c = 0; c < 40; c++) {
            List<CourseData.Gate> gates = new ArrayList<>();
            for (int g = 0; g < CourseData.MAX_GATES; g++) {
                gates.add(new CourseData.Gate(g, g * 70.5, 64.0, 0.5, 0.0F, GateSize.MEDIUM, GateMount.STANDING, false, GateTilt.NONE, 0.0, 0.0,
                        CourseElement.FREEFORM, 0, big));
            }
            courses.add(new CourseData(c + 1, "minecraft:overworld", gates));
        }
        List<RacePayloads.CourseSyncS2C> batches = CourseRegistry.joinBatches(courses);
        assertTrue(batches.size() > 1);
        int total = 0;
        for (RacePayloads.CourseSyncS2C batch : batches) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            RacePayloads.CourseSyncS2C.CODEC.encode(buf, batch);
            assertTrue(buf.writerIndex() < 1_048_576, "Paket " + buf.writerIndex() + " Bytes");
            total += batch.courses().size();
        }
        assertEquals(40, total);
    }

    @Test
    void worldRuleLimitsTheSizeAndIsNotPhysics() {
        assertEquals(20, WorldRuleSpecs.FREEFORM_GATE_MAX_SIZE.defaultValue());
        assertEquals(FreeformFill.MIN_LIMIT, WorldRuleSpecs.FREEFORM_GATE_MAX_SIZE.min());
        assertEquals(FreeformFill.MAX_LIMIT, WorldRuleSpecs.FREEFORM_GATE_MAX_SIZE.max());
        assertEquals("propwash:freeform_gate_max_size", WorldRuleSpecs.FREEFORM_GATE_MAX_SIZE.id());
        for (RulePreset preset : RulePreset.values()) {
            assertEquals(20, preset.value(WorldRuleSpecs.FREEFORM_GATE_MAX_SIZE), preset.id());
        }
        RuleValues bigger = RuleValues.DEFAULTS.with(WorldRuleSpecs.FREEFORM_GATE_MAX_SIZE, 64);
        assertFalse(PhysicsRules.of(bigger).modified());
        assertEquals(64, bigger.get(WorldRuleSpecs.FREEFORM_GATE_MAX_SIZE));
    }

    @Test
    void freeformGatesMayStartACourseAndCountStrictly() {
        assertTrue(CourseElement.FREEFORM.startable());
        assertTrue(CourseElement.GATE.startable());
        assertFalse(CourseElement.POLE.startable());
        assertTrue(CourseElement.FREEFORM.strictMiss());
        assertEquals(1, CourseElement.FREEFORM.stages());
        assertEquals("freeform_gate", CourseElement.FREEFORM.itemId());
        assertEquals(CourseElement.FREEFORM, CourseElement.byKey("freeform"));
        assertEquals(1, CourseElement.FREEFORM.opposite(0));
    }
}
