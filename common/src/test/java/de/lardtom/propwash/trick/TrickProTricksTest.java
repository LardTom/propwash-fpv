package de.lardtom.propwash.trick;

import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import static de.lardtom.propwash.trick.TrickType.ANTI_MATTY;
import static de.lardtom.propwash.trick.TrickType.DIVE;
import static de.lardtom.propwash.trick.TrickType.EJECT;
import static de.lardtom.propwash.trick.TrickType.FLIP;
import static de.lardtom.propwash.trick.TrickType.FUNKY_SPLIT;
import static de.lardtom.propwash.trick.TrickType.GUILLOTINE;
import static de.lardtom.propwash.trick.TrickType.INVERTED_FLIGHT;
import static de.lardtom.propwash.trick.TrickType.INVERTED_HANG;
import static de.lardtom.propwash.trick.TrickType.INVERTED_YAW_SPIN;
import static de.lardtom.propwash.trick.TrickType.JUICY_FLICK;
import static de.lardtom.propwash.trick.TrickType.POWER_LOOP;
import static de.lardtom.propwash.trick.TrickType.REWIND;
import static de.lardtom.propwash.trick.TrickType.RUBIKS_CUBE;
import static de.lardtom.propwash.trick.TrickType.SBANG;
import static de.lardtom.propwash.trick.TrickType.SHINOBI;
import static de.lardtom.propwash.trick.TrickType.SIDE_TRIPPY;
import static de.lardtom.propwash.trick.TrickType.SPLIT_S;
import static de.lardtom.propwash.trick.TrickType.TIC_TOC;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrickProTricksTest {
    static final double FAST = 650.0;
    static final double SLOW = 320.0;
    static final double SLOPPY = 140.0;

    record Case(String name, DroneBuild build, List<TrickType> expected, Consumer<SimPilot> script) {
    }

    private final List<Case> cases = new ArrayList<>();

    private void add(String name, List<TrickType> expected, Consumer<SimPilot> script) {
        this.cases.add(new Case(name, DroneBuild.FREESTYLE, expected, script));
    }

    private void add(String name, DroneBuild build, List<TrickType> expected, Consumer<SimPilot> script) {
        this.cases.add(new Case(name, build, expected, script));
    }

    static SimPilot run(Case c) {
        SimPilot p = new SimPilot(c.build(), 120.0);
        c.script().accept(p);
        p.clean().level(1.5);
        return p;
    }

    private void check() {
        List<Executable> checks = new ArrayList<>();
        for (Case c : this.cases) {
            checks.add(() -> {
                SimPilot p = run(c);
                List<TrickType> found = p.detected.stream().map(Trick::type).toList();
                assertEquals(0, p.telemetry().crashCount, c.name() + ": Absturz im Skript");
                assertEquals(c.expected(), found, c.name() + "\n" + p.report());
            });
        }
        assertAll(checks);
    }

    static SimPilot cruise(SimPilot p) {
        return p.fly(30.0, 0.0, 0.0, 1.5);
    }

    static SimPilot eject(SimPilot p, double rate) {
        return eject(p, rate, 70);
    }

    static SimPilot eject(SimPilot p, double rate, double tilt) {
        return p.rotate(0, rate, 0, 1.0, tilt).spin(0, 0, 0, 1.0, 0.25).rotate(0, -rate, 0, 0.3, tilt - 5);
    }

    static SimPilot apex(SimPilot p) {
        return p.flyThrottle(0, 0, 0, 0.9, 0.8).flyThrottle(0, 0, 0, 0.0, 1.8);
    }

    static SimPilot guillotine(SimPilot p, double dive) {
        return guillotine(p, 75, dive);
    }

    static SimPilot guillotine(SimPilot p, double push, double dive) {
        return apex(p).rotate(0, FAST, 0, 0.05, push).spin(0, 0, 0, 0.05, dive).rotate(0, FAST, 0, 0.05, 245 - push).rotate(0, FAST, 0, 1.0, 90)
                .flyThrottle(0, 0, 0, 1.0, 0.5);
    }

    static SimPilot splitS(SimPilot p) {
        return p.rotate(FAST, 0, 0, 0.2, 180).rotate(0, -300, 0, 0.6, 180);
    }

    static SimPilot funkySplit(SimPilot p, double sign) {
        return funkySplit(p, sign, 180);
    }

    static SimPilot funkySplit(SimPilot p, double sign, double half) {
        return p.rotate(sign * FAST, 0, 0, 0.2, half).rotate(0, 0, sign * 600, 0.15, 180).rotate(sign * FAST, 0, 0, 0.3, half);
    }

    static SimPilot rubikAndMatty(SimPilot p) {
        return p.rotate(0, FAST, 0, 0.4, 180).rotate(FAST, 0, 0, 0.2, 360).rotate(0, FAST, 0, 0.4, 180)
                .spin(0, -650, 0, 0.1, 0.22).spin(0, 0, 0, 0.8, 0.5).spin(0, -650, 0, 0.1, 0.33);
    }

    static SimPilot sideTrippy(SimPilot p, double sign, double bank, double degrees) {
        return cruise(p).rotate(sign * FAST, 0, 0, 0.6, bank).rotate(0, 320, 0, 0.8, degrees).rotate(-sign * FAST, 0, 0, 0.5, bank);
    }

    static SimPilot invertedFlight(SimPilot p, double seconds) {
        return p.mode3d().level(1.0).rotate(0, FAST, 0, -0.2, 180).invertedHold(0, 0, 0, seconds, 0.0).rotate(FAST, 0, 0, 0.0, 180);
    }

    static SimPilot ticToc(SimPilot p, int halves, double rollDps, double pitchDps) {
        p.mode3d().level(1.0);
        for (int i = 0; i < halves; i++) {
            double sign = i % 2 == 0 ? 1.0 : -1.0;
            p.rotate(sign * rollDps, sign * pitchDps, 0, sign > 0 ? -0.3 : 0.3, 170);
        }
        return p;
    }

    @Test
    void ejectIsTheSbangBaseMove() {
        this.add("Eject", List.of(EJECT), p -> eject(cruise(p).level(0.5), FAST));
        this.add("Eject langsam", List.of(EJECT), p -> eject(cruise(p).level(0.5), SLOW));
        this.add("Eject seitlich", List.of(EJECT),
                p -> cruise(p).level(0.5).rotate(FAST, 0, 0, 1.0, 70).spin(0, 0, 0, 1.0, 0.25).rotate(-FAST, 0, 0, 0.3, 65));
        this.add("Eject unsauber", List.of(EJECT), p -> eject(cruise(p).level(0.5).sloppy(101, SLOPPY), FAST));
        this.add("Eject mit weichen Knüppeln", List.of(EJECT), p -> eject(cruise(p).level(0.5).human(0.06), FAST, 55));
        this.add("Eject bei 30 bis 144 Hz", List.of(EJECT), p -> eject(p.unevenFrames(103, 30, 144).fly(30, 0, 0, 1.5).level(0.5), FAST));
        this.add("Eject aus dem Schweben", List.of(EJECT), p -> eject(p.level(1.0), FAST));
        this.add("Eject Whoop", DroneBuild.WHOOP, List.of(EJECT), p -> eject(cruise(p).level(0.5), FAST));
        this.check();
    }

    @Test
    void ejectNeedsPunchAndSnap() {
        this.add("Kippen ohne Gasstoß", List.of(),
                p -> cruise(p).level(0.5).rotate(0, FAST, 0, 0.5, 70).spin(0, 0, 0, 0.5, 0.25).rotate(0, -FAST, 0, 0.3, 65));
        this.add("Gasstoß ohne Zurückschnappen", List.of(),
                p -> cruise(p).level(0.5).rotate(0, FAST, 0, 1.0, 70).spin(0, 0, 0, 1.0, 0.25).fly(70, 0, 0, 1.5));
        this.add("Senkrechter Punch-out", List.of(), p -> p.level(1.0).flyThrottle(0, 0, 0, 1.0, 1.0));
        this.add("Beschleunigen wie im Rennen", List.of(), p -> p.level(0.5).fly(60, 0, 0, 1.5).level(1.0));
        this.add("Langsam ankippen mit Gas", List.of(), p -> p.level(0.5).spin(0, 60, 0, 1.0, 0.9).spin(0, 0, 0, 1.0, 0.2).rotate(0, -FAST, 0, 0.3, 50));
        this.check();
    }

    @Test
    void sbangCombosNeedAnEjectAndQuickReversals() {
        this.add("Sbang-Kombo: Eject, Split-S, Juicy Flick", List.of(EJECT, SPLIT_S, JUICY_FLICK, SBANG), p -> {
            eject(cruise(p).level(0.4), FAST).spin(0, 0, 0, 0.3, 0.15);
            splitS(p).spin(0, 0, 0, 0.35, 0.3).rotate(0, FAST, 0, 0.6, 180).rotate(FAST, 0, 0, 0.3, 180);
        });
        this.add("Sbang-Kombo unsauber", List.of(EJECT, SPLIT_S, JUICY_FLICK, SBANG), p -> {
            eject(cruise(p).level(0.4).sloppy(107, 100), FAST).spin(0, 0, 0, 0.3, 0.15);
            p.rotate(FAST, 0, 0, 0.2, 175).rotate(0, -300, 0, 0.6, 185).spin(0, 0, 0, 0.35, 0.3).rotate(0, FAST, 0, 0.6, 175)
                    .rotate(FAST, 0, 0, 0.3, 185);
        });
        this.add("Sbang-Kombo: Eject, Rewind, Yaw-Spin kopfüber", List.of(EJECT, REWIND, INVERTED_YAW_SPIN, SBANG), p -> {
            eject(cruise(p).level(0.4), FAST).spin(0, 0, 0, 0.3, 0.15);
            p.rotate(FAST, 0, 0, 0.3, 180).spin(0, 0, 0, 0.2, 0.2).rotate(-FAST, 0, 0, 0.3, 180).spin(0, 0, 0, 0.6, 0.3)
                    .rotate(FAST, 0, 0, 0.2, 180).rotate(0, 0, 750, 0.15, 360).rotate(FAST, 0, 0, 0.3, 180);
        });
        this.add("Ohne Eject keine Sbang-Kombo", List.of(SPLIT_S, JUICY_FLICK), p -> {
            p.fly(35, 0, 0, 2.0);
            splitS(p).spin(0, 0, 0, 0.35, 0.3).rotate(0, FAST, 0, 0.6, 180).rotate(FAST, 0, 0, 0.3, 180);
        });
        this.add("Mit Pause keine Sbang-Kombo", List.of(EJECT, SPLIT_S, JUICY_FLICK), p -> {
            eject(cruise(p).level(0.4), FAST).spin(0, 0, 0, 0.3, 0.15);
            splitS(p).level(1.4).rotate(0, FAST, 0, 0.6, 180).rotate(FAST, 0, 0, 0.3, 180);
        });
        this.check();
    }

    @Test
    void dexStyleFunkySplitAndShinobi() {
        this.add("Funky Split", List.of(FUNKY_SPLIT), p -> funkySplit(cruise(p), 1.0));
        this.add("Funky Split links", List.of(FUNKY_SPLIT), p -> funkySplit(cruise(p), -1.0));
        this.add("Funky Split unsauber", List.of(FUNKY_SPLIT), p -> funkySplit(cruise(p).sloppy(109, 60), 1.0));
        this.add("Funky Split mit weichen Knüppeln", List.of(FUNKY_SPLIT), p -> funkySplit(cruise(p).human(0.06), 1.0, 160));
        this.add("Kopfüber nur 90 Grad gieren", List.of(),
                p -> cruise(p).rotate(FAST, 0, 0, 0.2, 180).rotate(0, 0, 600, 0.15, 90).rotate(FAST, 0, 0, 0.3, 180));
        this.add("Shinobi Flip", List.of(SPLIT_S, RUBIKS_CUBE, ANTI_MATTY, SHINOBI), p -> rubikAndMatty(splitS(p.fly(35, 0, 0, 2.0))));
        this.add("Shinobi Flip mit Funky Split", List.of(FUNKY_SPLIT, RUBIKS_CUBE, FLIP, SHINOBI),
                p -> rubikAndMatty(funkySplit(p.fly(35, 0, 0, 2.0), 1.0)));
        this.add("Shinobi Flip unsauber", List.of(SPLIT_S, RUBIKS_CUBE, ANTI_MATTY, SHINOBI),
                p -> rubikAndMatty(splitS(p.fly(35, 0, 0, 2.0).sloppy(113, 100))));
        this.add("Rubik's Cube und Flip ohne Split-S", List.of(RUBIKS_CUBE, FLIP), p -> rubikAndMatty(p.fly(35, 0, 0, 2.0)));
        this.add("Split-S, Pause, Rubik's Cube und Flip", List.of(SPLIT_S, RUBIKS_CUBE, FLIP),
                p -> rubikAndMatty(splitS(p.fly(35, 0, 0, 2.0)).level(1.5).fly(35, 0, 0, 1.5)));
        this.check();
    }

    @Test
    void guillotineFlipsOutOfADive() {
        this.add("Guillotine", List.of(GUILLOTINE), p -> guillotine(p, 1.6));
        this.add("Guillotine aus großer Höhe", List.of(GUILLOTINE), p -> guillotine(p, 2.4));
        this.add("Guillotine unsauber", List.of(GUILLOTINE), p -> guillotine(p.sloppy(127, 80), 1.6));
        this.add("Guillotine mit weichen Knüppeln", List.of(GUILLOTINE), p -> guillotine(p.human(0.06), 60, 1.6));
        this.add("Guillotine bei 30 bis 144 Hz", List.of(GUILLOTINE), p -> guillotine(p.unevenFrames(131, 30, 144), 1.6));
        this.add("Guillotine über die Senkrechte gedrückt", List.of(GUILLOTINE), p -> guillotine(p, 85, 1.6));
        this.add("Sturzflug normal abgefangen", List.of(DIVE),
                p -> apex(p).rotate(0, FAST, 0, 0.05, 75).spin(0, 0, 0, 0.05, 1.8).rotate(0, -FAST, 0, 0.8, 80).flyThrottle(0, 0, 0, 1.0, 0.6));
        this.add("Sturzflug, abgefangen, dann Flip", List.of(DIVE, FLIP),
                p -> apex(p).rotate(0, FAST, 0, 0.05, 75).spin(0, 0, 0, 0.05, 1.8).rotate(0, -FAST, 0, 0.8, 80).flyThrottle(0, 0, 0, 1.0, 0.6)
                        .level(0.6).rotate(0, FAST, 0, 0.3, 360));
        this.add("Großer Power Loop", List.of(POWER_LOOP), p -> p.fly(35, 0, 0, 2.0).spin(0, -150, 0, 1.0, 2.5));
        this.add("Flip im flachen Fallen", List.of(FLIP), p -> apex(p).flyThrottle(0, 0, 0, 0.0, 1.4).rotate(0, FAST, 0, 0.1, 360));
        this.check();
    }

    @Test
    void sideTrippyCirclesOnItsSide() {
        this.add("Side Trippy", List.of(SIDE_TRIPPY), p -> sideTrippy(p, 1.0, 90, 420));
        this.add("Side Trippy links", List.of(SIDE_TRIPPY), p -> sideTrippy(p, -1.0, 90, 420));
        this.add("Side Trippy mit 75° Schräglage", List.of(SIDE_TRIPPY), p -> sideTrippy(p, 1.0, 75, 420));
        this.add("Side Trippy unsauber", List.of(SIDE_TRIPPY), p -> sideTrippy(p.sloppy(137, 80), 1.0, 90, 430));
        this.add("Side Trippy zwei Runden", List.of(SIDE_TRIPPY), p -> sideTrippy(p, 1.0, 90, 780));
        this.add("Langsam auf der Seite kreisen", List.of(),
                p -> cruise(p).rotate(FAST, 0, 0, 0.6, 85).spin(0, 150, 0, 0.8, 2.6).rotate(-FAST, 0, 0, 0.5, 85));
        this.add("Halbe Runde auf der Seite", List.of(), p -> sideTrippy(p, 1.0, 90, 200));
        this.check();
    }

    @Test
    void sideTrippyCountsEveryLap() {
        SimPilot one = run(new Case("", DroneBuild.FREESTYLE, List.of(), p -> sideTrippy(p, 1.0, 90, 420)));
        SimPilot two = run(new Case("", DroneBuild.FREESTYLE, List.of(), p -> sideTrippy(p, 1.0, 90, 780)));
        assertEquals(1.0, one.detected.getFirst().magnitude(), one.report());
        assertEquals(2.0, two.detected.getFirst().magnitude(), two.report());
    }

    @Test
    void threeDTricksOnlyCountInThreeDMode() {
        this.add("Rückenflug 3D", List.of(INVERTED_FLIGHT), p -> invertedFlight(p, 2.0));
        this.add("Rückenflug 3D lang", List.of(INVERTED_FLIGHT), p -> invertedFlight(p, 5.0));
        this.add("Rückenflug 3D unsauber", List.of(INVERTED_FLIGHT), p -> invertedFlight(p.sloppy(139, 60), 2.0));
        this.add("Rückenflug 3D mit Fahrt", List.of(INVERTED_FLIGHT),
                p -> p.mode3d().fly(25, 0, 0, 1.5).rotate(0, FAST, 0, -0.2, 180).invertedHold(0, 0, 0, 2.0, 0.0).rotate(FAST, 0, 0, 0.0, 180));
        this.add("Kopfüber ohne Umkehrschub im 3D-Modus", List.of(INVERTED_HANG),
                p -> p.mode3d().level(1.0).rotate(0, FAST, 0, 0.0, 180).spin(0, 0, 0, 0.0, 3.0).rotate(FAST, 0, 0, 0.3, 180));
        this.add("Kopfüber ohne 3D-Modus", List.of(INVERTED_HANG),
                p -> p.level(1.0).rotate(0, FAST, 0, 0.1, 180).spin(0, 0, 0, 0.1, 3.0).rotate(FAST, 0, 0, 0.3, 180));
        this.add("Tic-Toc 3D", List.of(TIC_TOC), p -> ticToc(p, 4, 0, FAST));
        this.add("Tic-Toc 3D sechs Hälften", List.of(TIC_TOC), p -> ticToc(p, 6, 0, FAST));
        this.add("Tic-Toc 3D gerollt", List.of(TIC_TOC), p -> ticToc(p, 4, FAST, 0));
        this.add("Tic-Toc 3D unsauber", List.of(TIC_TOC), p -> ticToc(p.sloppy(149, 100), 4, 0, FAST));
        this.add("Halbe Flips hin und her ohne 3D", List.of(REWIND, REWIND), p -> {
            p.level(1.0);
            for (int i = 0; i < 4; i++) {
                double sign = i % 2 == 0 ? 1.0 : -1.0;
                p.rotate(0, sign * FAST, 0, 0.2, 170);
            }
        });
        this.check();
    }

    @Test
    void ticTocCountsEveryExtraHalf() {
        SimPilot four = run(new Case("", DroneBuild.FREESTYLE, List.of(), p -> ticToc(p, 4, 0, FAST)));
        SimPilot six = run(new Case("", DroneBuild.FREESTYLE, List.of(), p -> ticToc(p, 6, 0, FAST)));
        assertEquals(4.0, four.detected.getFirst().magnitude(), four.report());
        assertEquals(6.0, six.detected.getFirst().magnitude(), six.report());
        assertTrue(TIC_TOC.points(6.0, Double.POSITIVE_INFINITY) > TIC_TOC.points(4.0, Double.POSITIVE_INFINITY));
    }

    @Test
    void normalAndThreeDCruisingNeverScoresNewTricks() {
        for (long seed = 21; seed <= 26; seed++) {
            long s = seed;
            this.add("Cruisen im 3D-Modus " + seed, List.of(), p -> {
                Random random = new Random(s);
                p.mode3d().sloppy(s, 90);
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
            });
            this.add("Schnelles Kurven und Beschleunigen " + seed, List.of(), p -> {
                Random random = new Random(s);
                p.fly(40, 0, 0, 1.5).sloppy(s, 60);
                for (int i = 0; i < 25; i++) {
                    double bank = (random.nextBoolean() ? 1.0 : -1.0) * (50 + random.nextDouble() * 30);
                    p.fly(30 + random.nextDouble() * 40, 0, 0, 0.4 + random.nextDouble() * 0.8).turn(30, bank, 0.4 + random.nextDouble() * 0.9)
                            .level(0.2 + random.nextDouble() * 0.4);
                }
            });
        }
        this.check();
    }
}
