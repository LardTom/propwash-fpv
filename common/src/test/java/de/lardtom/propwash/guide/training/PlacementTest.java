package de.lardtom.propwash.guide.training;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementTest {
    private static final int GROUND = 63;

    private static final class World implements Placement.Terrain {
        private final Map<Long, double[]> solid = new HashMap<>();
        private int loadedRadius = Integer.MAX_VALUE;
        private int groundLevel = GROUND;

        private static long key(int x, int y, int z) {
            return ((long) x * 73856093L) ^ ((long) y * 19349663L) ^ ((long) z * 83492791L);
        }

        World put(int x, int y, int z, double low, double high) {
            this.solid.put(key(x, y, z), new double[]{low, high, x, y, z});
            return this;
        }

        World wall(int x0, int x1, int y0, int y1, int z0, int z1) {
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        this.put(x, y, z, 0.0, 1.0);
                    }
                }
            }
            return this;
        }

        private double[] at(int x, int y, int z) {
            double[] box = this.solid.get(key(x, y, z));
            if (box != null && box[2] == x && box[3] == y && box[4] == z) {
                return box;
            }
            return y <= this.groundLevel ? new double[]{0.0, 1.0} : null;
        }

        @Override
        public boolean loaded(int x, int z) {
            return Math.abs(x) <= this.loadedRadius && Math.abs(z) <= this.loadedRadius;
        }

        @Override
        public double solidBottom(int x, int y, int z) {
            double[] box = this.at(x, y, z);
            return box == null ? Double.NaN : box[0];
        }

        @Override
        public double solidTop(int x, int y, int z) {
            double[] box = this.at(x, y, z);
            return box == null ? Double.NaN : box[1];
        }
    }

    private static Placement.Result place(String lesson, World world, float yaw) {
        Lesson l = Curriculum.lesson(lesson);
        assertNotNull(l);
        return Placement.place(l, 0.5, GROUND + 1.0, 0.5, yaw, TrainingScale.FULL, world);
    }

    @Test
    void everyLessonFitsOnOpenFlatGround() {
        World world = new World();
        for (Lesson lesson : Curriculum.lessons()) {
            Placement.Result result = Placement.place(lesson, 0.5, GROUND + 1.0, 0.5, 180.0F, TrainingScale.FULL, world);
            assertEquals(Placement.Status.OK, result.status(), lesson.id() + " " + result);
            assertEquals(0.0, result.turn(), lesson.id());
            assertNotNull(result.course());
        }
    }

    @Test
    void aWallAheadTurnsTheCourse() {
        World world = new World().wall(-20, 20, GROUND + 1, GROUND + 12, -12, -12);
        Placement.Result result = place("straight", world, 180.0F);
        assertEquals(Placement.Status.OK, result.status());
        assertTrue(result.turn() != 0.0, "Kurs nicht gedreht");
        assertNotNull(result.course());
        assertEquals(TrainingFrame.wrap(180.0 + result.turn()), result.course().frame().yaw(), 1.0E-4);
    }

    @Test
    void enclosedAreaIsBlockedWithTheFirstObstacle() {
        World world = new World()
                .wall(-12, 12, GROUND + 1, GROUND + 6, -12, -12)
                .wall(-12, 12, GROUND + 1, GROUND + 6, 12, 12)
                .wall(-12, -12, GROUND + 1, GROUND + 6, -12, 12)
                .wall(12, 12, GROUND + 1, GROUND + 6, -12, 12);
        Placement.Result result = place("straight", world, 180.0F);
        assertEquals(Placement.Status.BLOCKED, result.status());
        assertTrue(result.blocked() > 0);
        assertTrue(Math.abs(result.blockedX()) == 12 || Math.abs(result.blockedZ()) == 12, result.toString());
    }

    @Test
    void groundLevelBlocksAndSlabsUnderTheAnchorDoNotCount() {
        World world = new World().put(0, GROUND + 1, 0, 0.0, 0.5);
        Lesson hover = Curriculum.lesson("hover");
        Placement.Result result = Placement.place(hover, 0.5, GROUND + 1.5, 0.5, 0.0F, TrainingScale.FULL, world);
        assertEquals(Placement.Status.OK, result.status());
    }

    @Test
    void padNeedsGroundWithinOneAndAHalfBlocks() {
        World hole = new World();
        for (int x = -3; x <= 3; x++) {
            for (int z = -14; z <= -6; z++) {
                for (int y = GROUND - 4; y <= GROUND; y++) {
                    hole.put(x, y, z, Double.NaN, Double.NaN);
                }
            }
        }
        Placement.Result result = place("land", hole, 180.0F);
        assertEquals(Placement.Status.OK, result.status(), "andere Richtung hätte Boden");
        assertTrue(result.turn() != 0.0);

        World step = new World();
        for (int x = -3; x <= 3; x++) {
            for (int z = -14; z <= -6; z++) {
                step.put(x, GROUND + 1, z, 0.0, 0.5);
            }
        }
        Placement.Result raised = place("land", step, 180.0F);
        assertEquals(Placement.Status.OK, raised.status());
        assertEquals(0.0, raised.turn());
        Course course = raised.course();
        assertNotNull(course);
        assertEquals(GROUND + 1.5, course.padHeight(1), 1.0E-9);
        assertEquals(GROUND + 1.0, course.padHeight(0), 1.0E-9);
    }

    @Test
    void unloadedChunksAreReported() {
        World world = new World();
        world.loadedRadius = 10;
        Placement.Result result = place("straight", world, 180.0F);
        assertEquals(Placement.Status.NOT_LOADED, result.status());
    }

    @Test
    void scaledCoursesNeedLessRoom() {
        World world = new World().wall(-20, 20, GROUND + 1, GROUND + 12, -32, -32).wall(-20, 20, GROUND + 1, GROUND + 12, 32, 32)
                .wall(-32, -32, GROUND + 1, GROUND + 12, -20, 20).wall(32, 32, GROUND + 1, GROUND + 12, -20, 20);
        Lesson straight = Curriculum.lesson("straight");
        assertEquals(Placement.Status.BLOCKED,
                Placement.place(straight, 0.5, GROUND + 1.0, 0.5, 180.0F, TrainingScale.FULL, world).status());
        assertEquals(Placement.Status.OK,
                Placement.place(straight, 0.5, GROUND + 1.0, 0.5, 180.0F, TrainingScale.SMALL, world).status());
    }

    @Test
    void surfaceFindsTheTopOfTheHighestBlock() {
        World world = new World().put(5, GROUND + 1, 5, 0.0, 0.5);
        assertEquals(GROUND + 1.5, Placement.surface(world, 5, 5, GROUND + 2.0, GROUND - 1.0), 1.0E-9);
        assertEquals(GROUND + 1.0, Placement.surface(world, 6, 5, GROUND + 2.0, GROUND - 1.0), 1.0E-9);
        assertTrue(Double.isNaN(Placement.surface(world, 6, 5, GROUND + 0.5, GROUND - 1.0)), "Wand über dem Fenster");
    }
}
