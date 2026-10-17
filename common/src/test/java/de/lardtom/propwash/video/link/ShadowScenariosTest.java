package de.lardtom.propwash.video.link;

import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.video.LinkBudget;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadowScenariosTest {
    private static final double EYE = 1.62;
    private static final double ANALOG_RANGE = VideoType.ANALOG_RACE.rangeBlocks;
    private static final double DIGITAL_RANGE = VideoType.DIGITAL_HD.rangeBlocks;

    record Result(String name, double distance, double oldDb, double newDb, int rays, int cells) {
        double analog(double db) {
            return LinkBudget.quality(VideoSystem.ANALOG, this.distance, ANALOG_RANGE, db);
        }

        double digital(double db) {
            return LinkBudget.quality(VideoSystem.DIGITAL, this.distance, DIGITAL_RANGE, db);
        }

        String row() {
            return String.format(Locale.ROOT, "| %s | %.0f m | %.1f dB | %.1f dB | %.2f → %.2f | %.2f → %.2f | %d / %d |", this.name, this.distance,
                    this.oldDb, this.newDb, this.analog(this.oldDb), this.analog(this.newDb), this.digital(this.oldDb), this.digital(this.newDb),
                    this.rays, this.cells);
        }
    }

    static Result measure(String name, ScenarioWorld world, double droneX, double droneY, double droneZ) {
        world.set((int) Math.floor(droneX), (int) Math.floor(droneY), (int) Math.floor(droneZ), Medium.AIR);
        world.set(0, 0, 0, Medium.AIR).set(0, 1, 0, Medium.AIR);
        double oldDb = world.legacyDb(0.5, EYE, 0.5, droneX, droneY, droneZ);
        ObstructionScan scan = new ObstructionScan();
        scan.start(0.5, EYE, 0.5, droneX, droneY, droneZ);
        double newDb = scan.evaluate(world);
        double distance = Math.sqrt((droneX - 0.5) * (droneX - 0.5) + (droneY - EYE) * (droneY - EYE) + (droneZ - 0.5) * (droneZ - 0.5));
        return new Result(name, distance, oldDb, newDb, scan.roundRays(), scan.roundCells());
    }

    static ObstructionScan scanTo(ScenarioWorld world, double droneX, double droneY, double droneZ) {
        world.set((int) Math.floor(droneX), (int) Math.floor(droneY), (int) Math.floor(droneZ), Medium.AIR);
        world.set(0, 0, 0, Medium.AIR).set(0, 1, 0, Medium.AIR);
        ObstructionScan scan = new ObstructionScan();
        scan.start(0.5, EYE, 0.5, droneX, droneY, droneZ);
        scan.evaluate(world);
        return scan;
    }

    static ObstructionScan pastTree(int distance, double offset) {
        return scanTo(ScenarioWorld.flat().oak(distance - 5, 0, 5), distance + 0.5, 1.5, 0.5 + offset);
    }

    static double aroundHouse(int distance, int degrees) {
        double angle = Math.toRadians(degrees);
        ScenarioWorld world = ScenarioWorld.flat().house(distance - 10, -3, 7, 7, 4, ScenarioWorld.COBBLE, true);
        return scanTo(world, distance - 6.5 + 7.0 * Math.cos(angle), 2.0, 0.5 + 7.0 * Math.sin(angle)).lossDb();
    }

    static double[] forestSpread(int distance, double height, boolean legacy) {
        double[] losses = new double[21];
        for (int i = 0; i < losses.length; i++) {
            double z = i - 9.5;
            ScenarioWorld world = ScenarioWorld.flat().forest(distance - 15, distance + 12, -30, 30, 5);
            int cell = (int) Math.floor(z);
            world.set(distance, (int) Math.floor(height), cell, Medium.AIR).set(0, 0, cell, Medium.AIR).set(0, 1, cell, Medium.AIR);
            if (legacy) {
                losses[i] = world.legacyDb(0.5, EYE, z, distance + 0.5, height, z);
            } else {
                ObstructionScan scan = new ObstructionScan();
                scan.start(0.5, EYE, z, distance + 0.5, height, z);
                losses[i] = scan.evaluate(world);
            }
        }
        Arrays.sort(losses);
        return losses;
    }

    static Result behindTree(int distance, double droneHeight) {
        ScenarioWorld world = ScenarioWorld.flat().oak(distance - 5, 0, 5);
        return measure(String.format(Locale.ROOT, "1 tree, drone %.1f m high, 5 m behind it", droneHeight), world, distance + 0.5, droneHeight, 0.5);
    }

    static Result throughCrown(int distance) {
        ScenarioWorld world = ScenarioWorld.flat().oak(distance - 5, 0, 5);
        return measure("1 tree, crown only (1.5 m beside the trunk), drone 3.5 m high", world, distance + 0.5, 3.5, 2.0);
    }

    static Result behindWall(int distance) {
        ScenarioWorld world = ScenarioWorld.flat();
        for (int z = -3; z <= 3; z++) {
            for (int y = 0; y < 6; y++) {
                world.set(distance - 5, y, z, ScenarioWorld.STONE);
            }
        }
        return measure("stone wall 7 m wide, 6 m high, 5 m before the drone", world, distance + 0.5, 1.5, 0.5);
    }

    static Result behindHouse(int distance, Medium wall, String material) {
        ScenarioWorld world = ScenarioWorld.flat().house(distance - 10, -3, 7, 7, 4, wall, true);
        return measure("house of " + material + " with windows, drone 3.5 m behind it", world, distance + 0.5, 2.0, 0.5);
    }

    static Result throughForest(int distance, int depth) {
        ScenarioWorld world = ScenarioWorld.flat().forest(distance - depth, distance + 12, -24, 24, 5);
        return measure("forest, drone " + depth + " m deep inside, 2.5 m high", world, distance + 0.5, 2.5, 0.5);
    }

    static List<Result> all() {
        List<Result> results = new ArrayList<>();
        for (int d : new int[]{20, 50, 100}) {
            results.add(behindTree(d, 1.5));
        }
        for (int d : new int[]{20, 50, 100}) {
            results.add(behindTree(d, 3.5));
        }
        for (int d : new int[]{20, 50, 100}) {
            results.add(throughCrown(d));
        }
        for (int d : new int[]{20, 50, 100}) {
            results.add(behindWall(d));
        }
        for (int d : new int[]{30, 60, 100}) {
            results.add(behindHouse(d, ScenarioWorld.PLANKS, "planks"));
        }
        for (int d : new int[]{30, 60, 100}) {
            results.add(behindHouse(d, ScenarioWorld.COBBLE, "cobblestone"));
        }
        for (int d : new int[]{50, 100}) {
            results.add(throughForest(d, 15));
        }
        for (int d : new int[]{20, 100, 300, 600}) {
            ScenarioWorld open = ScenarioWorld.flat();
            results.add(measure("open field, drone 10 m high", open, d + 0.5, 10.0, 0.5));
            results.add(measure("open field, drone 2 m high", ScenarioWorld.flat(), d + 0.5, 2.0, 0.5));
        }
        return results;
    }

    @Test
    void printTable() {
        StringBuilder table = new StringBuilder("| scenario | distance | before | after | analog quality | digital quality | rays / cells |\n|---|---|---|---|---|---|---|\n");
        for (Result r : all()) {
            table.append(r.row()).append('\n');
        }
        System.out.println(table);
        assertTrue(table.length() > 0);
    }

    @Test
    void openFieldKeepsTheCalibratedRangeExactly() {
        for (double height : new double[]{5.0, 10.0, 30.0}) {
            for (int distance : new int[]{5, 20, 50, 100, 200, 380, 600, 760}) {
                ObstructionScan scan = new ObstructionScan();
                scan.start(0.5, EYE, 0.5, distance + 0.5, height, 0.5);
                double loss = scan.evaluate(ScenarioWorld.flat());
                assertEquals(0.0, loss, 0.0, "open field " + distance + " m at " + height + " m");
                for (VideoType type : VideoType.values()) {
                    assertEquals(LinkBudget.quality(type, distance, 0.0), LinkBudget.quality(type, distance, loss), 0.0);
                }
            }
        }
    }

    @Test
    void openFieldCostsAtMostTheOldLossAtLowAltitude() {
        for (int distance : new int[]{50, 100, 200, 300, 400}) {
            ScenarioWorld world = ScenarioWorld.flat();
            ObstructionScan scan = new ObstructionScan();
            scan.start(0.5, EYE, 0.5, distance + 0.5, 2.0, 0.5);
            double loss = scan.evaluate(world);
            double old = world.legacyDb(0.5, EYE, 0.5, distance + 0.5, 2.0, 0.5);
            assertTrue(loss <= old + 1.0E-9, "low flight " + distance + " m: " + loss + " > " + old);
        }
    }

    @Test
    void behindATreeTheVideoSuffersMoreTheFurtherOut() {
        Result near = behindTree(20, 1.5);
        Result mid = behindTree(50, 1.5);
        Result far = behindTree(100, 1.5);
        for (Result r : List.of(near, mid, far)) {
            assertTrue(r.newDb > 8.0 && r.newDb < 20.0, r.row());
            assertTrue(r.oldDb < r.newDb - 5.0, "the old model barely noticed a tree: " + r.row());
        }
        assertTrue(near.analog(near.newDb) > 0.9, near.row());
        assertTrue(mid.analog(mid.newDb) < 0.85, mid.row());
        assertTrue(far.analog(far.newDb) < 0.45, far.row());
        assertTrue(far.digital(far.newDb) > far.analog(far.newDb), far.row());
    }

    @Test
    void aOneBlockWallCostsAboutOneHouseWallAndATreeKeepsItsCalibration() {
        for (int d : new int[]{20, 50, 100}) {
            Result trunk = behindTree(d, 1.5);
            Result wall = behindWall(d);
            assertTrue(wall.newDb > 6.5 && wall.newDb < 10.0, wall.row());
            assertEquals(13.8, trunk.newDb, 1.0, trunk.row());
        }
    }

    @Test
    void housesAndForestsBlockMoreThanATree() {
        Result tree = behindTree(60, 1.5);
        Result planks = behindHouse(60, ScenarioWorld.PLANKS, "planks");
        Result cobble = behindHouse(60, ScenarioWorld.COBBLE, "cobblestone");
        Result forest = throughForest(50, 15);
        assertTrue(cobble.newDb > planks.newDb + 4.0, planks.row() + " vs " + cobble.row());
        assertTrue(cobble.newDb > tree.newDb - 1.0, cobble.row());
        assertTrue(forest.newDb > tree.newDb, forest.row());
        assertTrue(cobble.analog(cobble.newDb) > 0.2 && cobble.analog(cobble.newDb) < 0.6, cobble.row());
        assertTrue(cobble.digital(cobble.newDb) > 0.6, cobble.row());
        assertTrue(forest.analog(forest.newDb) < 0.2, forest.row());
    }

    @Test
    void flyingPastATrunkFadesInAndOutAndNearMissesAlreadyCost() {
        double[] offsets = {0.0, 0.25, 0.5, 0.75, 1.0};
        for (int d : new int[]{20, 50, 100}) {
            double previous = Double.POSITIVE_INFINITY;
            for (double offset : offsets) {
                double right = pastTree(d, offset).lossDb();
                double left = pastTree(d, -offset).lossDb();
                assertEquals(right, left, 0.5, "trunk at " + d + " m, offset " + offset);
                assertTrue(right <= previous + 1.0E-9, "trunk at " + d + " m: loss grows away from the trunk at " + offset);
                previous = right;
            }
            assertTrue(pastTree(d, 0.0).lossDb() > 12.0);
            ObstructionScan nearMiss = pastTree(d, 0.75);
            assertEquals(0.0, nearMiss.centerLossDb(), 0.0, "the straight ray passes the trunk");
            assertTrue(nearMiss.lossDb() > 0.5 && nearMiss.lossDb() < 8.0, "near miss at " + d + " m: " + nearMiss.lossDb());
            for (double clear : new double[]{1.5, 2.0, 3.0}) {
                assertEquals(0.0, pastTree(d, clear).lossDb(), 0.0, "clear of the trunk at " + d + " m, " + clear + " m beside");
                assertEquals(0.0, pastTree(d, -clear).lossDb(), 0.0);
            }
        }
    }

    @Test
    void circlingAHouseShadowsOnlyTheFarSide() {
        for (int d : new int[]{30, 60}) {
            StringBuilder row = new StringBuilder("around a cobblestone house at " + d + " m:");
            int shadowed = 0;
            for (int degrees = 0; degrees < 360; degrees += 45) {
                double loss = aroundHouse(d, degrees);
                row.append(String.format(Locale.ROOT, " %d° %.1f dB", degrees, loss));
                if (degrees >= 90 && degrees <= 270) {
                    assertEquals(0.0, loss, 0.0, "pilot side of the house at " + degrees + "°");
                }
                if (loss > 0.0) {
                    shadowed++;
                }
            }
            System.out.println(row);
            assertTrue(aroundHouse(d, 0) > 12.0, row.toString());
            assertTrue(shadowed <= 3, row.toString());
        }
    }

    @Test
    void forestShadowDependsOnWhereTheDroneHides() {
        for (int d : new int[]{50, 100}) {
            double[] now = forestSpread(d, 2.5, false);
            double[] before = forestSpread(d, 2.5, true);
            System.out.println(String.format(Locale.ROOT, "forest 15 m deep at %d m, 21 positions: before %.1f / %.1f / %.1f dB, after %.1f / %.1f / %.1f dB (min / median / max)",
                    d, before[0], before[10], before[20], now[0], now[10], now[20]));
            assertTrue(now[10] > 8.0 && now[10] < 20.0, "median " + now[10]);
            assertTrue(now[20] > before[20], "behind a trunk deep in the forest it costs more than before 0.5.0");
            assertTrue(now[20] - now[0] > 10.0, "a forest is not uniform");
        }
    }
}
