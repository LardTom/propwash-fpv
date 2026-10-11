package de.lardtom.propwash.trick;

import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import static de.lardtom.propwash.trick.TrickType.FLIP;
import static de.lardtom.propwash.trick.TrickType.INVERTED_YAW_SPIN;
import static de.lardtom.propwash.trick.TrickType.JUICY_FLICK;
import static de.lardtom.propwash.trick.TrickType.POWER_LOOP;
import static de.lardtom.propwash.trick.TrickType.ROLL;
import static de.lardtom.propwash.trick.TrickType.RUBIKS_CUBE;
import static de.lardtom.propwash.trick.TrickType.SPLIT_S;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrickRobustnessTest {
    private static final double FAST = 650.0;
    private static final double SLOPPY = 140.0;
    private static final int SEEDS = 40;

    private record Case(String name, DroneBuild build, List<TrickType> expected, Consumer<SimPilot> script, List<TrickType> alternative) {
        Case(String name, DroneBuild build, List<TrickType> expected, Consumer<SimPilot> script) {
            this(name, build, expected, script, expected);
        }
    }

    private static String run(Case c) {
        SimPilot p = new SimPilot(c.build(), 120.0);
        c.script().accept(p);
        p.clean().level(1.5);
        List<TrickType> found = p.detected.stream().map(Trick::type).toList();
        if (!(c.expected().equals(found) || c.alternative().equals(found)) || p.telemetry().crashCount > 0) {
            return c.name() + ": erwartet " + c.expected() + ", erkannt " + found + ", Abstürze " + p.telemetry().crashCount;
        }
        return null;
    }

    private static void check(List<Case> cases) {
        List<String> wrong = new ArrayList<>();
        for (Case c : cases) {
            String problem = run(c);
            if (problem != null) {
                wrong.add(problem);
            }
        }
        assertTrue(wrong.isEmpty(), wrong.size() + " von " + cases.size() + " Fällen falsch:\n" + String.join("\n", wrong));
    }

    private static SimPilot cruise(SimPilot p) {
        return p.fly(30.0, 0.0, 0.0, 1.5);
    }

    private static void aggressive(SimPilot p, long seed) {
        Random random = new Random(seed);
        p.fly(40, 0, 0, 2.0).sloppy(seed, 60);
        for (int i = 0; i < 30; i++) {
            double sign = random.nextBoolean() ? 1.0 : -1.0;
            switch (random.nextInt(4)) {
                case 0 -> p.rotate(sign * FAST, 0, 0, 0.3 + random.nextDouble() * 0.4, 30 + random.nextDouble() * 70).level(0.4).fly(40, 0, 0, 0.4);
                case 1 -> p.rotate(0, sign * FAST, 0, 0.3 + random.nextDouble() * 0.4, 30 + random.nextDouble() * 70).level(0.4).fly(30, 0, 0, 0.4);
                case 2 -> p.rotate(0, 0, sign * 500, 0.35, 40 + random.nextDouble() * 200).level(0.3).fly(30, 0, 0, 0.3);
                default -> p.fly(40, 0, 0, 1.0).turn(30 + random.nextDouble() * 20, sign * (30 + random.nextDouble() * 25), 0.5 + random.nextDouble());
            }
        }
    }

    @Test
    void formerMisdetectionsStayFixed() {
        assertEquals(null, run(new Case("Rubik's Cube unsauber mit kurzem Aufrichten (Seed 102)", DroneBuild.FREESTYLE, List.of(RUBIKS_CUBE),
                p -> cruise(p).sloppy(102, SLOPPY).rotate(0, FAST, 0, 0.4, 170).rotate(FAST, 0, 0, 0.2, 370).rotate(0, FAST, 0, 0.4, 190))));
        assertEquals(null, run(new Case("Aggressiv ohne Tricks: 112° Nick aus Schräglage, Rolle zum Aufrichten (Seed 138)", DroneBuild.FREESTYLE,
                List.of(), p -> aggressive(p, 138))));
        assertEquals(null, run(new Case("Mehrfacher Power Loop bei 30–90 Hz (Seed 212)", DroneBuild.FREESTYLE, List.of(POWER_LOOP),
                p -> p.unevenFrames(212, 30, 90).fly(35, 0, 0, 2.0).spin(0, -220, 0, 0.85, 1.65))));
    }

    @Test
    void sloppyTricksWithRandomSeeds() {
        List<Case> cases = new ArrayList<>();
        for (long s = 1000; s < 1000 + SEEDS; s++) {
            long seed = s;
            cases.add(new Case("Flip unsauber " + s, DroneBuild.FREESTYLE, List.of(FLIP),
                    p -> p.level(1.0).sloppy(seed, SLOPPY).rotate(0, FAST, 0, 0.3, 360)));
            cases.add(new Case("Rolle unsauber " + s, DroneBuild.FREESTYLE, List.of(ROLL),
                    p -> p.level(1.0).sloppy(seed, SLOPPY).rotate(FAST, 0, 0, 0.3, 360)));
            cases.add(new Case("Juicy Flick unsauber " + s, DroneBuild.FREESTYLE, List.of(JUICY_FLICK),
                    p -> cruise(p).sloppy(seed, SLOPPY).rotate(0, FAST, 0, 0.6, 170).rotate(FAST, 0, 0, 0.3, 190)));
            cases.add(new Case("Rubik's Cube unsauber " + s, DroneBuild.FREESTYLE, List.of(RUBIKS_CUBE),
                    p -> cruise(p).sloppy(seed, SLOPPY).rotate(0, FAST, 0, 0.4, 170).rotate(FAST, 0, 0, 0.2, 370).rotate(0, FAST, 0, 0.4, 190)));
            cases.add(new Case("Split-S unsauber " + s, DroneBuild.FREESTYLE, List.of(SPLIT_S),
                    p -> p.fly(35, 0, 0, 2.0).sloppy(seed, SLOPPY).rotate(FAST, 0, 0, 0.2, 190).rotate(0, -300, 0, 0.6, 170)));
            cases.add(new Case("Power Loop unsauber " + s, DroneBuild.FREESTYLE, List.of(POWER_LOOP),
                    p -> p.fly(35, 0, 0, 2.0).sloppy(seed, 90).rotate(0, -230, 0, 0.85, 360)));
            cases.add(new Case("Flip und Rolle bei 30–200 Hz " + s, DroneBuild.FREESTYLE, List.of(FLIP, ROLL),
                    p -> p.unevenFrames(seed, 30, 200).level(1.0).rotate(0, FAST, 0, 0.3, 360).rotate(FAST, 0, 0, 0.3, 360)));
            cases.add(new Case("Flip Whoop unsauber " + s, DroneBuild.WHOOP, List.of(FLIP),
                    p -> p.level(1.0).sloppy(seed, SLOPPY).rotate(0, FAST, 0, 0.35, 360)));
            cases.add(new Case("Gierdrehung kopfüber unsauber " + s, DroneBuild.FREESTYLE, List.of(INVERTED_YAW_SPIN),
                    p -> p.level(0.5).spin(0, 0, 0, 0.9, 0.5).sloppy(seed, 100).rotate(FAST, 0, 0, 0.2, 180).rotate(0, 0, 750, 0.15, 370)
                            .rotate(-FAST, 0, 0, 0.3, 180)));
            cases.add(new Case("Power Loop bei 30–90 Hz " + s, DroneBuild.FREESTYLE, List.of(POWER_LOOP),
                    p -> p.unevenFrames(seed, 30, 90).fly(35, 0, 0, 2.0).spin(0, -220, 0, 0.85, 1.65)));
            cases.add(new Case("Juicy Flick direkt in Flip " + s, DroneBuild.FREESTYLE, List.of(JUICY_FLICK, FLIP),
                    p -> cruise(p).sloppy(seed, 100).rotate(0, FAST, 0, 0.6, 180).rotate(FAST, 0, 0, 0.3, 180).spin(0, 0, 0, 0.35, 0.15)
                            .rotate(0, FAST, 0, 0.3, 360)));
        }
        check(cases);
    }

    @Test
    void newSchoolTricksWithRandomSeeds() {
        List<Case> cases = new ArrayList<>();
        for (long s = 3000; s < 3000 + SEEDS; s++) {
            long seed = s;
            cases.add(new Case("Eject unsauber " + s, DroneBuild.FREESTYLE, List.of(TrickType.EJECT),
                    p -> TrickProTricksTest.eject(cruise(p).level(0.5).sloppy(seed, SLOPPY), FAST)));
            cases.add(new Case("Sbang-Kombo unsauber " + s, DroneBuild.FREESTYLE,
                    List.of(TrickType.EJECT, SPLIT_S, JUICY_FLICK, TrickType.SBANG), p -> {
                        TrickProTricksTest.eject(cruise(p).level(0.4).sloppy(seed, 100), FAST).spin(0, 0, 0, 0.3, 0.15);
                        TrickProTricksTest.splitS(p).spin(0, 0, 0, 0.35, 0.3).rotate(0, FAST, 0, 0.6, 180).rotate(FAST, 0, 0, 0.3, 180);
                    }));
            cases.add(new Case("Funky Split unsauber " + s, DroneBuild.FREESTYLE, List.of(TrickType.FUNKY_SPLIT),
                    p -> TrickProTricksTest.funkySplit(cruise(p).sloppy(seed, 100), 1.0)));
            cases.add(new Case("Shinobi Flip unsauber " + s, DroneBuild.FREESTYLE,
                    List.of(SPLIT_S, RUBIKS_CUBE, TrickType.ANTI_MATTY, TrickType.SHINOBI),
                    p -> TrickProTricksTest.rubikAndMatty(TrickProTricksTest.splitS(p.fly(35, 0, 0, 2.0).sloppy(seed, 80))),
                    List.of(SPLIT_S, RUBIKS_CUBE, FLIP, TrickType.SHINOBI)));
            cases.add(new Case("Guillotine unsauber " + s, DroneBuild.FREESTYLE, List.of(TrickType.GUILLOTINE),
                    p -> TrickProTricksTest.guillotine(p.sloppy(seed, 100), 1.6)));
            cases.add(new Case("Side Trippy unsauber " + s, DroneBuild.FREESTYLE, List.of(TrickType.SIDE_TRIPPY),
                    p -> TrickProTricksTest.sideTrippy(p.sloppy(seed, 80), 1.0, 90, 430)));
            cases.add(new Case("Rückenflug 3D unsauber " + s, DroneBuild.FREESTYLE, List.of(TrickType.INVERTED_FLIGHT),
                    p -> TrickProTricksTest.invertedFlight(p.sloppy(seed, 60), 2.0)));
            cases.add(new Case("Tic-Toc 3D unsauber " + s, DroneBuild.FREESTYLE, List.of(TrickType.TIC_TOC),
                    p -> TrickProTricksTest.ticToc(p.sloppy(seed, 100), 4, 0, FAST)));
        }
        check(cases);
    }

    @Test
    void catalogueTricksWithRandomSeeds() {
        List<Case> cases = new ArrayList<>();
        for (long s = 4000; s < 4000 + SEEDS; s++) {
            long seed = s;
            catalogue(cases, "Anti Matty", seed, 80, List.of(TrickType.ANTI_MATTY), TrickVariantScripts::antiMatty);
            catalogue(cases, "Half Matty", seed, 100, List.of(TrickType.HALF_MATTY), TrickVariantScripts::halfMatty);
            catalogue(cases, "Power Matty", seed, 100, List.of(TrickType.POWER_MATTY), TrickVariantScripts::powerMatty);
            catalogue(cases, "540-Split-S", seed, 100, List.of(TrickType.SPLIT_S_540), TrickVariantScripts::split540);
            catalogue(cases, "Splitback", seed, 100, List.of(TrickType.SPLITBACK), TrickVariantScripts::splitback);
            catalogue(cases, "Flip-Rewind", seed, 100, List.of(TrickType.FLIP_REWIND), p -> TrickVariantScripts.flipRewind(p, 0, FAST));
            catalogue(cases, "Segmentierter Flip", seed, 100, List.of(TrickType.SEGMENTED), p -> TrickVariantScripts.segmented(p, 0, FAST, 0.6));
            catalogue(cases, "Power Flip", seed, 100, List.of(TrickType.POWER_FLIP), p -> TrickVariantScripts.powerFlip(p, -1.0));
            catalogue(cases, "Reversed Power Flip", seed, 100, List.of(TrickType.POWER_FLIP), p -> TrickVariantScripts.powerFlip(p, 1.0));
            catalogue(cases, "Power Roll", seed, 100, List.of(TrickType.POWER_ROLL), TrickVariantScripts::powerRoll);
            catalogue(cases, "Inverted 360 Power Loop", seed, 80, List.of(TrickType.INVERTED_360_POWER_LOOP), TrickVariantScripts::inverted360Loop);
            catalogue(cases, "Side Loop", seed, 100, List.of(TrickType.SIDE_LOOP), p -> TrickVariantScripts.sideLoop(p, 1.0));
            catalogue(cases, "Barani", seed, 100, List.of(TrickType.BARANI), TrickVariantScripts::barani);
            catalogue(cases, "Maverick Loop", seed, 100, List.of(TrickType.MAVERICK_LOOP), p -> TrickVariantScripts.maverick(p, 4.0, 5.0));
            catalogue(cases, "Immelmatt", seed, 100, List.of(TrickType.IMMELMANN, TrickType.MATTY_FLIP, TrickType.IMMELMATT), TrickVariantScripts::immelmatt);
            catalogue(cases, "Vanny Roll", seed, 100, List.of(TrickType.VANNY_ROLL), p -> TrickVariantScripts.vanny(p, 1.0));
            catalogue(cases, "Coin Drop", seed, 40, List.of(TrickType.COIN_DROP), p -> TrickVariantScripts.coinDrop(p, 720));
            catalogue(cases, "Tornado", seed, 100, List.of(TrickType.TORNADO, TrickType.DIVE), p -> TrickVariantScripts.tornado(p, 720));
            catalogue(cases, "Rückwärts-Sturzflug", seed, 70, List.of(TrickType.BACKWARDS_DIVE), p -> TrickVariantScripts.backwardsDive(p, 45, 2.0));
            catalogue(cases, "Hammerhead", seed, 100, List.of(TrickType.HAMMERHEAD), p -> TrickVariantScripts.hammerhead(p, 1.0));
            cases.add(new Case("Wall Ride unsauber " + seed, DroneBuild.FREESTYLE, List.of(TrickType.WALL_RIDE),
                    p -> TrickVariantScripts.wallRide(p.sloppy(seed, 30), 0.5, 75), List.of(TrickType.KNIFE_EDGE)));
            catalogue(cases, "Schweben kopfüber 3D", seed, 100, List.of(TrickType.INVERTED_HOVER), p -> TrickVariantScripts.invertedHover(p, 2.0));
            catalogue(cases, "3D-Flip", seed, 100, List.of(TrickType.FLIP_3D), p -> TrickVariantScripts.flip3d(p, 0, FAST));
            catalogue(cases, "3D-Rolle", seed, 100, List.of(TrickType.ROLL_3D), p -> TrickVariantScripts.flip3d(p, FAST, 0));
            catalogue(cases, "Power Loop 3D", seed, 100, List.of(TrickType.POWER_LOOP_3D), TrickVariantScripts::powerLoop3d);
        }
        check(cases);
    }

    private static void catalogue(List<Case> cases, String name, long seed, double sloppy, List<TrickType> expected, Consumer<SimPilot> script) {
        cases.add(new Case(name + " unsauber " + seed, DroneBuild.FREESTYLE, expected, p -> script.accept(p.sloppy(seed, sloppy))));
    }

    @Test
    void normalFlyingWithRandomSeedsNeverScores() {
        List<Case> cases = new ArrayList<>();
        for (long s = 2000; s < 2000 + SEEDS; s++) {
            long seed = s;
            cases.add(new Case("Aggressiv ohne Tricks " + s, DroneBuild.FREESTYLE, List.of(), p -> aggressive(p, seed)));
            cases.add(new Case("Aggressiv im 3D-Modus " + s, DroneBuild.FREESTYLE, List.of(), p -> aggressive(p.mode3d(), seed)));
            cases.add(new Case("Cruisen mit Rauschen " + s, DroneBuild.FREESTYLE, List.of(), p -> {
                Random random = new Random(seed);
                p.sloppy(seed, 90);
                for (int i = 0; i < 40; i++) {
                    double tilt = random.nextDouble() * 70.0;
                    double bank = (random.nextDouble() * 2.0 - 1.0) * 75.0;
                    double seconds = 0.3 + random.nextDouble() * 1.0;
                    if (random.nextBoolean()) {
                        p.turn(tilt, bank, seconds);
                    } else {
                        p.fly(tilt, bank * 0.3, (random.nextDouble() * 2.0 - 1.0) * 140.0, seconds);
                    }
                }
            }));
        }
        check(cases);
    }
}
