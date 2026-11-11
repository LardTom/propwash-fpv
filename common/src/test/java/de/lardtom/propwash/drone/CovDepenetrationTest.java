package de.lardtom.propwash.drone;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CovDepenetrationTest {
    private static final double HALF = 0.2;
    private static final double TALL = 0.09;
    private static final double EPS = 1.0e-3;
    private static final VoxelShape SLAB = Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5, 1.0);

    private static final class World implements Depenetration.Solids {
        final Map<BlockPos, VoxelShape> shapes = new HashMap<>();

        World with(int x, int y, int z, VoxelShape shape) {
            this.shapes.put(new BlockPos(x, y, z), shape);
            return this;
        }

        World fill(int x0, int y0, int z0, int x1, int y1, int z1) {
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        this.with(x, y, z, Shapes.block());
                    }
                }
            }
            return this;
        }

        World ground(int top) {
            return this.fill(-4, top - 3, -4, 4, top - 1, 4);
        }

        @Override
        public boolean collect(AABB area, List<AABB> out) {
            for (int x = Mth.floor(area.minX); x <= Mth.floor(area.maxX); x++) {
                for (int y = Mth.floor(area.minY); y <= Mth.floor(area.maxY); y++) {
                    for (int z = Mth.floor(area.minZ); z <= Mth.floor(area.maxZ); z++) {
                        VoxelShape shape = this.shapes.get(new BlockPos(x, y, z));
                        if (shape == null) {
                            continue;
                        }
                        for (AABB part : shape.toAabbs()) {
                            AABB placed = part.move(x, y, z);
                            if (placed.intersects(area)) {
                                out.add(placed);
                            }
                        }
                    }
                }
            }
            return true;
        }

        boolean free(AABB box) {
            List<AABB> found = new ArrayList<>();
            this.collect(box, found);
            return found.isEmpty();
        }
    }

    private static AABB drone(double x, double bottom, double z) {
        return new AABB(x - HALF, bottom, z - HALF, x + HALF, bottom + TALL, z + HALF);
    }

    private static Vec3 resolved(AABB box, World world) {
        Vec3 out = Depenetration.resolve(box, world);
        assertNotNull(out, "Drohne im Block, aber kein Ausweg gefunden");
        assertTrue(world.free(box.move(out)), "Ausweg liegt wieder im Block: " + box.move(out));
        return out;
    }

    @Test
    void aFreeOrTouchingDroneStaysWhereItIs() {
        World world = new World().ground(64);
        assertNull(Depenetration.resolve(drone(0.5, 66.0, 0.5), world), "frei in der Luft");
        assertNull(Depenetration.resolve(drone(0.5, 64.0, 0.5), world), "liegt genau auf dem Gras");
        assertNull(Depenetration.resolve(drone(0.5, 64.0 - 0.0005, 0.5), world), "0,5 mm Rechenfehler sind noch Berührung");
        assertNull(Depenetration.resolve(drone(0.5, 64.0, 0.5), new World()), "leere Welt, Ranken und Gras haben keine Kollision");
    }

    @Test
    void threeCentimetresInTheGroundGoStraightUp() {
        Vec3 out = resolved(drone(0.5, 63.97, 0.5), new World().ground(64));
        assertEquals(0.0, out.x, 1.0e-12);
        assertEquals(0.0, out.z, 1.0e-12);
        assertEquals(0.03, out.y, EPS);
    }

    @Test
    void deepInTheTopBlockComesOutOnTheGrassNotFurtherDown() {
        Vec3 out = resolved(drone(0.5, 63.4, 0.5), new World().ground(64));
        assertEquals(0.6, out.y, EPS);
        assertEquals(0.0, out.horizontalDistance(), 1.0e-12);
    }

    @Test
    void underALidOverAHollowItNeverPassesThroughForeignBlocks() {
        World world = new World().fill(-3, 62, -3, 3, 63, 3).with(0, 64, 0, Shapes.block());
        assertNull(Depenetration.resolve(drone(0.5, 63.4, 0.5), world),
                "weder durch den Deckel noch durch den Boden oder den Nachbarblock – bleibt für Überlagerung und Reset");
        World open = new World().fill(-3, 62, -3, 3, 63, 3);
        Vec3 out = resolved(drone(0.5, 63.4, 0.5), open);
        assertEquals(0.6, out.y, EPS, "ohne Deckel nach oben, nicht durch den Boden in den Hohlraum");
    }

    @Test
    void partlyInAThickWallItStepsBackOutOfTheWall() {
        World world = new World().ground(64).fill(2, 64, -3, 3, 66, 3);
        Vec3 out = resolved(drone(2.0 - HALF + 0.1, 64.6, 0.5), world);
        assertEquals(-0.1, out.x, EPS, "zurück vor die Mauer statt hinein oder darüber");
        assertEquals(0.0, out.y, 1.0e-12);
    }

    @Test
    void aStonePlacedIntoAHoveringDroneLetsItOutAtTheNearestFace() {
        World world = new World().ground(64).with(2, 66, 0, Shapes.block());
        Vec3 west = resolved(drone(2.15, 66.4, 0.5), world);
        assertEquals(-(2.15 + HALF - 2.0), west.x, EPS, "zur nahen Westseite");
        assertEquals(0.0, west.y, 1.0e-12);
        Vec3 down = resolved(drone(2.5, 66.05, 0.5), world);
        assertEquals(-(0.05 + TALL), down.y, EPS, "aus der Unterseite, Luft darunter");
        Vec3 up = resolved(drone(2.5, 66.95, 0.5), world);
        assertEquals(0.05, up.y, EPS, "aus der Oberseite");
    }

    @Test
    void aCeilingOrAWallPushesAwayFromItself() {
        World ceiling = new World().fill(-3, 70, -3, 3, 72, 3);
        Vec3 down = resolved(drone(0.5, 70.0 - TALL + 0.02, 0.5), ceiling);
        assertEquals(-0.02, down.y, EPS);
        World wall = new World().fill(1, 60, -3, 3, 70, 3);
        Vec3 back = resolved(drone(1.0 - HALF + 0.05, 65.0, 0.5), wall);
        assertEquals(-0.05, back.x, EPS);
        assertEquals(0.0, back.y, 1.0e-12);
    }

    @Test
    void slabsCountWithTheirOwnHeight() {
        Vec3 out = resolved(drone(0.5, 64.48, 0.5), new World().ground(64).with(0, 64, 0, SLAB));
        assertEquals(0.02, out.y, EPS);
    }

    @Test
    void inTheMiddleOfACellUpWinsTheTie() {
        World world = new World().with(0, 66, 0, Shapes.block());
        Vec3 out = resolved(new AABB(0.3, 66.3, 0.3, 0.7, 66.7, 0.7), world);
        assertTrue(out.y > 0.0, "bei gleichem Weg nach oben: " + out);
    }

    @Test
    void walledInWithoutRoomItStaysForTheResetAndTheOverlay() {
        World world = new World().fill(-3, 55, -3, 3, 70, 3);
        assertNull(Depenetration.resolve(drone(0.5, 63.4, 0.5), world), "kein freier Platz in Reichweite");
    }

    @Test
    void nearTheTopOfAThinWallItRisesOverTheWallInsteadOfTunnellingSideways() {
        World world = new World().ground(64).fill(0, 64, -3, 0, 64, 3);
        Vec3 out = resolved(drone(0.5, 64.3, 0.5), world);
        assertTrue(out.y > 0.0 || Math.abs(out.x) <= 0.5 + HALF + EPS, "kein Weg durch fremde Blöcke: " + out);
        assertEquals(0.0, out.z, 1.0e-12);
    }
}
