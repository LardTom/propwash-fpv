package de.lardtom.propwash.video.link;

import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.drone.RadioLink;
import de.lardtom.propwash.video.AnalogBreakup;
import de.lardtom.propwash.video.DigitalBreakup;
import de.lardtom.propwash.video.LinkBudget;
import de.lardtom.propwash.video.VtxTables;
import de.lardtom.propwash.video.vtx.VtxAuto;
import de.lardtom.propwash.video.vtx.VtxTable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkScenariosTest {
    static final int BANDO_FRONT = 28;
    static final double FIXED_RATE_RANGE = 380.0;
    static final String[] ANALOG_NAMES = {"sauber", "Störstreifen", "Farbverlust", "Schnee", "Rauschen"};
    static final String[] DIGITAL_NAMES = {"sauber", "weicher", "Blöcke", "Standbilder", "verloren"};

    record Scene(String name, double distance, double db) {
    }

    static double lossTo(ScenarioWorld world, double x, double y, double z) {
        return ShadowScenariosTest.scanTo(world, x, y, z).lossDb();
    }

    static Scene open(int distance) {
        return new Scene("freies Feld, Drohne 2 m hoch", distance + 0.5, lossTo(ScenarioWorld.flat(), distance + 0.5, 2.0, 0.5));
    }

    static Scene house(int distance, Medium wall, String material) {
        ScenarioWorld world = ScenarioWorld.flat().house(distance - 10, -3, 7, 7, 4, wall, false);
        return new Scene("hinter einem Haus aus " + material + " (7 × 7 × 4), Drohne 3,5 m dahinter", distance, lossTo(world, distance + 0.5, 2.0, 0.5));
    }

    static Scene corner(int distance, double inside) {
        ScenarioWorld world = ScenarioWorld.flat().house(distance - 10, -3, 7, 7, 4, ScenarioWorld.COBBLE, false);
        double z = 0.5 + (4.0 - inside - 0.5) * distance / (distance - 10.5);
        String where = inside > 0.0 ? String.format(Locale.ROOT, "%.1f m im Schatten", inside)
                : String.format(Locale.ROOT, "%.1f m frei", -inside);
        return new Scene("an der Ecke des Steinhauses, Sichtlinie " + where, distance, lossTo(world, distance + 0.5, 2.0, z));
    }

    static ScenarioWorld bando(int shift) {
        int x0 = BANDO_FRONT + shift;
        ScenarioWorld world = ScenarioWorld.flat();
        world.fill(x0, 0, -8, x0 + 12, 6, 8, ScenarioWorld.CONCRETE);
        world.fill(x0 + 1, 0, -7, x0 + 11, 5, 7, Medium.AIR);
        world.fill(x0 + 4, 0, -7, x0 + 4, 5, 7, ScenarioWorld.CONCRETE);
        world.fill(x0 + 8, 0, -7, x0 + 8, 5, 7, ScenarioWorld.CONCRETE);
        return world;
    }

    static Scene bando(int shift, int walls) {
        double x = BANDO_FRONT + shift + 2.5 + 4.0 * (walls - 1);
        return new Scene("im Bando aus Beton hinter " + walls + (walls == 1 ? " Wand" : " Wänden"), x, lossTo(bando(shift), x, 2.0, 0.5));
    }

    static Scene hill(int distance) {
        ScenarioWorld world = ScenarioWorld.flat().ridge(distance - 20, 10, 12, -40, 40);
        return new Scene("hinter einem Hügel (10 m hoch, 20 m vor der Drohne), Drohne 2 m hoch", distance, lossTo(world, distance + 0.5, 2.0, 0.5));
    }

    static Scene mesa(int distance) {
        ScenarioWorld world = ScenarioWorld.flat().fill(distance - 45, 0, -60, distance - 6, 19, 59, ScenarioWorld.STONE);
        return new Scene("hinter einem Tafelberg (40 m tief, 20 m hoch, 120 m breit), Drohne 5 m dahinter, 2 m hoch", distance,
                lossTo(world, distance + 0.5, 2.0, 0.5));
    }

    static Scene cave(int distance, int depth) {
        ScenarioWorld world = ScenarioWorld.flat().fill(distance - 1, -depth - 1, -1, distance + 1, -depth + 1, 1, Medium.AIR);
        return new Scene("unter Tage in einer Höhle, " + depth + " m unter der Oberfläche", distance, lossTo(world, distance + 0.5, -depth + 0.5, 0.5));
    }

    static Scene forest(int distance) {
        double[] spread = ShadowScenariosTest.forestSpread(distance, 2.5, false);
        return new Scene("Wald, Drohne 15 m tief, 2,5 m hoch (Median aus 21 Stellen)", distance, spread[spread.length / 2]);
    }

    static Scene glass(int distance) {
        ScenarioWorld world = ScenarioWorld.flat().house(distance - 10, -3, 7, 7, 4, ScenarioWorld.GLASS, false);
        return new Scene("hinter einem Glashaus (zwei Glaswände)", distance, lossTo(world, distance + 0.5, 2.0, 0.5));
    }

    static Scene tree(int distance) {
        ScenarioWorld world = ScenarioWorld.flat().oak(distance - 5, 0, 5);
        return new Scene("hinter einer Eiche (Stamm), Drohne 5 m dahinter", distance, lossTo(world, distance + 0.5, 1.5, 0.5));
    }

    static List<Scene> scenes() {
        List<Scene> list = new ArrayList<>();
        list.add(open(100));
        for (int d : new int[]{30, 100, 250}) {
            list.add(house(d, ScenarioWorld.COBBLE, "Bruchstein"));
        }
        for (int d : new int[]{30, 100, 250}) {
            list.add(house(d, ScenarioWorld.PLANKS, "Brettern"));
        }
        for (double inside : new double[]{-1.0, -0.5, 0.0, 0.25, 0.5, 1.0, 2.0, 3.0}) {
            list.add(corner(100, inside));
        }
        for (double inside : new double[]{-0.5, 0.0, 0.5, 1.0, 2.0}) {
            list.add(corner(30, inside));
        }
        for (int shift : new int[]{0, 50}) {
            for (int walls = 1; walls <= 3; walls++) {
                list.add(bando(shift, walls));
            }
        }
        list.add(hill(70));
        list.add(hill(150));
        list.add(mesa(70));
        list.add(mesa(300));
        list.add(cave(30, 10));
        list.add(forest(50));
        list.add(glass(100));
        list.add(tree(50));
        return list;
    }

    static double analog(Scene s) {
        return LinkBudget.quality(VideoSystem.ANALOG, s.distance, VideoType.ANALOG_RACE.rangeBlocks, s.db);
    }

    static double digital(VideoType type, Scene s, boolean auto) {
        double margin = LinkBudget.marginDb(s.distance, type.rangeBlocks) - s.db;
        double gain = 0.0;
        if (auto) {
            VtxTable table = VtxTables.of(type);
            VtxAuto control = new VtxAuto();
            control.reset(table.ratedIndex());
            for (int i = 0; i < 400; i++) {
                control.update(0.05, margin, table);
            }
            gain = table.gainDb(control.index());
        }
        return LinkBudget.quality(VideoSystem.DIGITAL, margin + gain);
    }

    static double fixedRate(Scene s) {
        return LinkBudget.quality(VideoSystem.DIGITAL, s.distance, FIXED_RATE_RANGE, s.db);
    }

    static float lq(Scene s) {
        return RadioLink.quality(s.distance, Double.POSITIVE_INFINITY, false, 0.0, s.db);
    }

    static String a(double q) {
        return String.format(Locale.ROOT, "%.2f %s", q, ANALOG_NAMES[AnalogBreakup.stage(q).ordinal()]);
    }

    static String d(double q) {
        return String.format(Locale.ROOT, "%.2f %s", q, DIGITAL_NAMES[DigitalBreakup.stage(q).ordinal()]);
    }

    static String row(Scene s) {
        return String.format(Locale.ROOT, "| %s | %.0f m | %.1f dB | %s | %s / %s | %s / %s | %s | %d %% / %d dBm |", s.name, s.distance, s.db,
                a(analog(s)), d(digital(VideoType.DIGITAL_LITE, s, false)), d(digital(VideoType.DIGITAL_LITE, s, true)),
                d(digital(VideoType.DIGITAL_HD, s, false)), d(digital(VideoType.DIGITAL_HD, s, true)), d(fixedRate(s)),
                Math.round(lq(s) * 100.0F), Math.round(RadioLink.rssiDbm(s.distance, s.db)));
    }

    @Test
    void printScenarioTable() {
        StringBuilder out = new StringBuilder();
        for (Scene s : scenes()) {
            out.append(row(s)).append(System.lineSeparator());
        }
        System.out.print(out);
        assertTrue(out.length() > 0);
    }

    @Test
    void oneWallIsLikeAHouseWallAndEachFurtherWallCostsMore() {
        Scene one = bando(0, 1);
        Scene two = bando(0, 2);
        Scene three = bando(0, 3);
        assertEquals(RfMaterial.CONCRETE.straightBlockDb(), one.db, 1.0, row(one));
        assertTrue(two.db > one.db + 6.0 && three.db > two.db + 6.0, row(two) + " " + row(three));
        assertTrue(analog(one) >= AnalogBreakup.COLOUR_BELOW, row(one));
        assertEquals(DigitalBreakup.Stage.CLEAN, DigitalBreakup.stage(digital(VideoType.DIGITAL_LITE, one, false)), row(one));
        assertTrue(analog(two) > AnalogBreakup.STATIC_BELOW, row(two));
        assertTrue(DigitalBreakup.stage(digital(VideoType.DIGITAL_HD, two, false)).compareTo(DigitalBreakup.Stage.BLOCKY) <= 0, row(two));
        Scene farThree = bando(50, 3);
        assertEquals(DigitalBreakup.Stage.LOST, DigitalBreakup.stage(digital(VideoType.DIGITAL_HD, farThree, true)), row(farThree));
        assertEquals(AnalogBreakup.Stage.STATIC, AnalogBreakup.stage(analog(farThree)), row(farThree));
    }

    @Test
    void behindAHouseThePictureDegradesWithDistanceInsteadOfDyingAtOnce() {
        Scene near = house(30, ScenarioWorld.COBBLE, "Bruchstein");
        Scene mid = house(100, ScenarioWorld.COBBLE, "Bruchstein");
        Scene far = house(250, ScenarioWorld.COBBLE, "Bruchstein");
        assertTrue(near.db > 11.0 && near.db < 16.0, row(near));
        assertTrue(analog(near) >= AnalogBreakup.COLOUR_BELOW, row(near));
        assertEquals(DigitalBreakup.Stage.CLEAN, DigitalBreakup.stage(digital(VideoType.DIGITAL_LITE, near, false)), row(near));
        assertTrue(analog(mid) < AnalogBreakup.SNOW_BELOW && analog(mid) > 0.0, row(mid));
        assertEquals(DigitalBreakup.Stage.BLOCKY, DigitalBreakup.stage(digital(VideoType.DIGITAL_HD, mid, false)), row(mid));
        assertEquals(DigitalBreakup.Stage.LOST, DigitalBreakup.stage(digital(VideoType.DIGITAL_HD, far, true)), row(far));
        Scene planks = house(100, ScenarioWorld.PLANKS, "Brettern");
        assertTrue(planks.db < mid.db - 3.0, row(planks));
        assertEquals(DigitalBreakup.Stage.CLEAN, DigitalBreakup.stage(digital(VideoType.DIGITAL_HD, planks, false)), row(planks));
    }

    @Test
    void justBehindAHouseCornerThePictureFadesInsteadOfCutting() {
        double house = house(100, ScenarioWorld.COBBLE, "Bruchstein").db;
        double previous = -1.0;
        for (double inside : new double[]{-1.0, -0.5, 0.0, 0.25, 0.5, 1.0, 2.0, 3.0}) {
            Scene s = corner(100, inside);
            assertTrue(s.db <= house + 1.0, row(s));
            if (inside <= 0.5) {
                assertTrue(s.db >= previous - 0.5, row(s));
            }
            previous = s.db;
        }
        assertEquals(0.0, corner(100, -1.0).db, 0.5);
        Scene edge = corner(100, 0.0);
        assertTrue(edge.db > 2.0 && edge.db < 8.0, row(edge));
        assertTrue(DigitalBreakup.stage(digital(VideoType.DIGITAL_HD, corner(100, 0.5), false)).compareTo(DigitalBreakup.Stage.BLOCKY) <= 0);
    }

    @Test
    void terrainStillCutsTheLink() {
        Scene hill = hill(70);
        Scene cave = cave(30, 10);
        assertTrue(hill.db > 25.0, row(hill));
        assertEquals(LinkBudget.MAX_LOSS_DB, cave.db, 1.0E-9, row(cave));
        for (Scene s : new Scene[]{hill, cave}) {
            assertEquals(AnalogBreakup.Stage.STATIC, AnalogBreakup.stage(analog(s)), row(s));
            assertEquals(DigitalBreakup.Stage.LOST, DigitalBreakup.stage(digital(VideoType.DIGITAL_HD, s, true)), row(s));
            assertTrue(RadioLink.rssiDbm(s.distance, s.db) < RadioLink.rssiDbm(s.distance, 0.0) - 15.0, row(s));
        }
        assertEquals(0.0F, lq(cave), 1.0E-6F, row(cave));
        assertEquals(0.0F, lq(hill(150)), 1.0E-6F, row(hill(150)));
    }

    @Test
    void aMesaOfTerrainBlocksCutsTheLinkAtEveryDistance() {
        for (int distance : new int[]{70, 150, 300}) {
            Scene mesa = mesa(distance);
            assertTrue(mesa.db > 50.0, row(mesa));
            assertEquals(AnalogBreakup.Stage.STATIC, AnalogBreakup.stage(analog(mesa)), row(mesa));
            assertEquals(DigitalBreakup.Stage.LOST, DigitalBreakup.stage(digital(VideoType.DIGITAL_HD, mesa, true)), row(mesa));
            assertEquals(DigitalBreakup.Stage.LOST, DigitalBreakup.stage(digital(VideoType.DIGITAL_HD, mesa, false)), row(mesa));
            assertEquals(0.0F, lq(mesa), 1.0E-6F, row(mesa));
        }
    }

    @Test
    void lightObstaclesBarelyMatter() {
        Scene glass = glass(100);
        assertTrue(glass.db < 8.0, row(glass));
        assertEquals(DigitalBreakup.Stage.CLEAN, DigitalBreakup.stage(digital(VideoType.DIGITAL_HD, glass, false)), row(glass));
        assertTrue(analog(glass) >= AnalogBreakup.COLOUR_BELOW, row(glass));
        Scene forest = forest(50);
        assertTrue(forest.db > 6.0 && forest.db < 20.0, row(forest));
        assertTrue(analog(forest) > AnalogBreakup.SNOW_BELOW, row(forest));
    }

    @Test
    void theControlLinkSharesTheShadowButHasMoreReserve() {
        for (Scene s : scenes()) {
            float control = lq(s);
            if (s.db < 30.0) {
                assertEquals(1.0F, control, 1.0E-6F, row(s));
            }
            assertTrue(RadioLink.rssiDbm(s.distance, s.db) <= RadioLink.rssiDbm(s.distance, 0.0), row(s));
        }
        assertEquals(RadioLink.rssiDbm(100.0, 0.0) - 0.6 * 20.0, RadioLink.rssiDbm(100.0, 20.0), 1.0E-9);
    }

    @Test
    void autoNeverPicksLessThanTheRatedPowerBehindAnObstacle() {
        for (Scene s : scenes()) {
            for (VideoType type : new VideoType[]{VideoType.DIGITAL_LITE, VideoType.DIGITAL_HD}) {
                assertTrue(digital(type, s, true) >= digital(type, s, false) - 1.0E-9 || LinkBudget.marginDb(s.distance, type.rangeBlocks) - s.db
                        >= VtxAuto.DIGITAL_TARGET_DB, type.name() + " " + row(s));
            }
        }
    }
}
