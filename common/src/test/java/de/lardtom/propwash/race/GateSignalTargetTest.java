package de.lardtom.propwash.race;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GateSignalTargetTest {
    private static void assertTarget(GateSignalTarget target, int x, int y, int z, int face) {
        assertEquals(new GateSignalTarget(x, y, z, face), target);
    }

    @Test
    void floorGatesPowerTheBlockBelowTheirMiddle() {
        for (GateSize size : GateSize.values()) {
            for (float yaw : new float[]{0.0F, 90.0F, 180.0F, 270.0F}) {
                GateGeometry standing = GatePlacement.place(GateMount.STANDING, 10.5, 64.0, 20.5, yaw, false, size);
                assertTarget(GateSignalTarget.of(standing), 10, 63, 20, GateSignalTarget.UP);
            }
        }
        GateGeometry slab = GatePlacement.place(GateMount.STANDING, 3.5, 70.5, -4.5, 0.0F, false, GateSize.MEDIUM);
        assertTarget(GateSignalTarget.of(slab), 3, 70, -5, GateSignalTarget.UP);
    }

    @Test
    void ceilingGatesPowerTheBlockTheyHangFrom() {
        GateGeometry hanging = GatePlacement.place(GateMount.HANGING, 10.5, 70.0, 20.5, 90.0F, false, GateSize.MEDIUM);
        assertTarget(GateSignalTarget.of(hanging), 10, 70, 20, GateSignalTarget.DOWN);
    }

    @Test
    void lyingGatesPowerTheRimBlockUnderTheirBar() {
        for (GateSize size : GateSize.values()) {
            for (float yaw : new float[]{0.0F, 90.0F, 180.0F, 270.0F}) {
                for (GateMount mount : new GateMount[]{GateMount.FLOOR_FLAT, GateMount.CEILING_FLAT}) {
                    for (boolean flipped : new boolean[]{false, true}) {
                        double y = mount == GateMount.FLOOR_FLAT ? 64.0 : 70.0;
                        GateGeometry flat = GatePlacement.place(mount, 10.5, y, 20.5, yaw, flipped, size);
                        GateSignalTarget target = GateSignalTarget.of(flat);
                        String label = mount + " " + size + " " + yaw + " " + flipped;
                        assertEquals(mount == GateMount.FLOOR_FLAT ? 63 : 70, target.y(), label);
                        assertEquals(mount == GateMount.FLOOR_FLAT ? GateSignalTarget.UP : GateSignalTarget.DOWN, target.face(), label);
                        double barX = flat.worldX(0.0, flat.frame() * 0.5, 0.0);
                        double barZ = flat.worldZ(0.0, flat.frame() * 0.5, 0.0);
                        assertEquals((int) Math.floor(barX), target.x(), label);
                        assertEquals((int) Math.floor(barZ), target.z(), label);
                        double dx = target.x() + 0.5 - 10.5;
                        double dz = target.z() + 0.5 - 20.5;
                        double reach = Math.max(Math.abs(dx), Math.abs(dz));
                        assertTrue(reach >= size.openingHeight() * 0.5, label + ": Ziel liegt unter der Öffnung (" + reach + ")");
                        assertTrue(reach <= size.height() * 0.5, label + ": Ziel liegt außerhalb des Rahmens (" + reach + ")");
                    }
                }
            }
        }
        GateGeometry floor = GatePlacement.place(GateMount.FLOOR_FLAT, 10.5, 64.0, 20.5, 0.0F, false, GateSize.MEDIUM);
        assertTarget(GateSignalTarget.of(floor), 10, 63, 18, GateSignalTarget.UP);
        GateGeometry ceiling = GatePlacement.place(GateMount.CEILING_FLAT, 10.5, 70.0, 20.5, 90.0F, false, GateSize.MEDIUM);
        GateSignalTarget target = GateSignalTarget.of(ceiling);
        assertEquals(70, target.y());
        assertEquals(69, target.emitterY());
        assertEquals(20, target.z());
    }

    @Test
    void wallGatesPowerTheWallBlockBehindThem() {
        GateGeometry south = GatePlacement.place(GateMount.WALL_DIVE, 10.5, 66.0, 20.0, 0.0F, false, GateSize.MEDIUM);
        assertTarget(GateSignalTarget.of(south), 10, 66, 19, GateSignalTarget.SOUTH);
        GateGeometry west = GatePlacement.place(GateMount.WALL_SIDE, 11.0, 66.5, 20.5, 90.0F, false, GateSize.MEDIUM);
        assertTarget(GateSignalTarget.of(west), 11, 66, 20, GateSignalTarget.WEST);
        assertEquals(10, GateSignalTarget.of(west).emitterX());
        GateGeometry north = GatePlacement.place(GateMount.WALL_WINDOW, 10.5, 66.5, 20.0, 180.0F, false, GateSize.LARGE);
        assertTarget(GateSignalTarget.of(north), 10, 66, 20, GateSignalTarget.NORTH);
        GateGeometry east = GatePlacement.place(GateMount.WALL_DIVE, 10.0, 66.0, 20.5, 270.0F, true, GateSize.SMALL);
        assertTarget(GateSignalTarget.of(east), 9, 66, 20, GateSignalTarget.EAST);
    }

    @Test
    void tiltedAndResizedGatesKeepTheirMountBlock() {
        GateGeometry plain = GatePlacement.place(GateMount.STANDING, 10.5, 64.0, 20.5, 0.0F, false, GateSize.MEDIUM);
        GateGeometry tilted = GatePlacement.place(GateMount.STANDING, 10.5, 64.0, 20.5, 0.0F, false, 9.0, 6.0, 0.5, 0.5,
                new GateTilt(30.0F, 25.0F, 45.0F));
        assertEquals(GateSignalTarget.of(plain), GateSignalTarget.of(tilted));
        GateGeometry dive = GatePlacement.place(GateMount.WALL_DIVE, 10.5, 66.0, 20.0, 0.0F, false, 5.0, 5.0, 0.5, 0.5,
                new GateTilt(15.0F, 20.0F, 0.0F));
        assertTarget(GateSignalTarget.of(dive), 10, 66, 19, GateSignalTarget.SOUTH);
    }

    @Test
    void emitterIsTheCellInFrontOfTheMountFace() {
        GateSignalTarget target = new GateSignalTarget(1, 2, 3, GateSignalTarget.WEST);
        assertEquals(0, target.emitterX());
        assertEquals(2, target.emitterY());
        assertEquals(3, target.emitterZ());
        assertEquals(GateSignalTarget.EAST, GateSignalTarget.horizontal(0.9, 0.1));
        assertEquals(GateSignalTarget.NORTH, GateSignalTarget.horizontal(0.0, -1.0));
        assertEquals(GateSignalTarget.UP, GateSignalTarget.outward(GateMount.Surface.FLOOR, 1.0, 0.0));
    }

    @Test
    void freeformGatesPowerTheRimBelowTheMiddleOfTheirLowestRow() {
        boolean[] cells = {
                false, true, true, true, false,
                true, true, true, true, true};
        FreeformRegion region = FreeformRegion.of(FreeformRegion.AXIS_Z, 5, 2, 2, 1, cells);
        assertTarget(GateSignalTarget.freeform(region, 7, 100, 64), 102, 63, 7, GateSignalTarget.UP);
        boolean[] split = {true, false, false, true};
        FreeformRegion gap = FreeformRegion.of(FreeformRegion.AXIS_X, 4, 1, 0, 0, split);
        assertTarget(GateSignalTarget.freeform(gap, -3, 10, 70), -3, 69, 10, GateSignalTarget.UP);
        FreeformRegion shaft = FreeformRegion.of(FreeformRegion.AXIS_Y, 3, 3, 1, 1, new boolean[]{
                false, false, true,
                true, true, true,
                true, true, true});
        assertTarget(GateSignalTarget.freeform(shaft, 60, 5, -8), 7, 60, -9, GateSignalTarget.SOUTH);
    }

    @Test
    void freeformTargetFromAFilledOpeningTouchesTheOpening() {
        FreeformFill.Probe window = (x, y, z) -> {
            if (z != 0) {
                return FreeformFill.Cell.OPEN;
            }
            boolean inside = x >= 0 && x <= 3 && y >= 65 && y <= 67;
            return inside ? FreeformFill.Cell.OPEN : FreeformFill.Cell.SOLID;
        };
        FreeformFill.Result result = FreeformFill.fill(window, FreeformRegion.AXIS_Z, 1, 66, 0, 20);
        assertTrue(result.ok(), result.status().name());
        GateGeometry geometry = GateGeometry.freeform(result.anchorX(), result.anchorY(), result.anchorZ(), result.region(), 0);
        GateSignalTarget target = GateSignalTarget.of(geometry);
        assertEquals(new GateSignalTarget(1, 64, 0, GateSignalTarget.UP), target);
        assertTrue(FreeformBlocks.covers(geometry, target.emitterX(), target.emitterY(), target.emitterZ()));
        assertFalse(FreeformBlocks.covers(geometry, target.x(), target.y(), target.z()));
    }
}
