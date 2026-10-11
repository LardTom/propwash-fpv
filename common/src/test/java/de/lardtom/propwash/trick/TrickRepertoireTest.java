package de.lardtom.propwash.trick;

import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static de.lardtom.propwash.trick.TrickType.ANTI_MATTY;
import static de.lardtom.propwash.trick.TrickType.CORKSCREW;
import static de.lardtom.propwash.trick.TrickType.DOUBLE_FLIP;
import static de.lardtom.propwash.trick.TrickType.DOUBLE_ROLL;
import static de.lardtom.propwash.trick.TrickType.FLIP;
import static de.lardtom.propwash.trick.TrickType.FLIP_REWIND;
import static de.lardtom.propwash.trick.TrickType.IMMELMANN;
import static de.lardtom.propwash.trick.TrickType.INVERTED_YAW_SPIN;
import static de.lardtom.propwash.trick.TrickType.JUICY_FLICK;
import static de.lardtom.propwash.trick.TrickType.KNIFE_EDGE;
import static de.lardtom.propwash.trick.TrickType.MATTY_FLIP;
import static de.lardtom.propwash.trick.TrickType.MCTWIST;
import static de.lardtom.propwash.trick.TrickType.POWER_LOOP;
import static de.lardtom.propwash.trick.TrickType.REWIND;
import static de.lardtom.propwash.trick.TrickType.ROLL;
import static de.lardtom.propwash.trick.TrickType.RUBIKS_CUBE;
import static de.lardtom.propwash.trick.TrickType.SNAP_ROLL;
import static de.lardtom.propwash.trick.TrickType.SPLIT_S;
import static de.lardtom.propwash.trick.TrickType.TRIPPY_SPIN;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrickRepertoireTest {
    private static final double FAST = 650.0;
    private static final double SLOW = 320.0;
    private static final double SLOPPY = 140.0;

    private record Case(String name, DroneBuild build, List<TrickType> expected, Consumer<SimPilot> script) {
    }

    private final List<Case> cases = new ArrayList<>();

    private void add(String name, List<TrickType> expected, Consumer<SimPilot> script) {
        this.cases.add(new Case(name, DroneBuild.FREESTYLE, expected, script));
    }

    private void add(String name, DroneBuild build, List<TrickType> expected, Consumer<SimPilot> script) {
        this.cases.add(new Case(name, build, expected, script));
    }

    private void check() {
        List<Executable> checks = new ArrayList<>();
        for (Case c : this.cases) {
            checks.add(() -> {
                SimPilot p = new SimPilot(c.build, 120.0);
                c.script.accept(p);
                p.clean().level(1.5);
                List<TrickType> found = p.detected.stream().map(Trick::type).toList();
                assertEquals(0, p.telemetry().crashCount, c.name + ": Absturz im Skript");
                assertEquals(c.expected, found, c.name + "\n" + p.report());
            });
        }
        assertAll(checks);
    }

    private static SimPilot cruise(SimPilot p) {
        return p.fly(30.0, 0.0, 0.0, 1.5);
    }

    @Test
    void flipsAndRollsSurviveSloppyInputs() {
        this.add("Flip sauber", List.of(FLIP), p -> p.level(1.0).rotate(0, FAST, 0, 0.3, 360));
        this.add("Flip rückwärts", List.of(FLIP), p -> p.level(1.0).rotate(0, -FAST, 0, 0.3, 360));
        this.add("Flip langsam", List.of(FLIP), p -> p.level(1.0).rotate(0, SLOW, 0, 0.35, 360));
        this.add("Flip schnell (hohe Rates)", List.of(FLIP), p -> p.maxRate(1100).level(1.0).rotate(0, 1050, 0, 0.3, 360));
        this.add("Flip unsauber", List.of(FLIP), p -> p.level(1.0).sloppy(3, SLOPPY).rotate(0, FAST, 0, 0.3, 360));
        this.add("Flip mit Roll-Anteil", List.of(FLIP), p -> p.level(1.0).rotate(220, FAST, 0, 0.3, 380));
        this.add("Flip mit Gier-Kopplung", List.of(FLIP), p -> p.level(1.0).rotate(0, FAST, 180, 0.3, 380));
        this.add("Flip mit Gasstoß", List.of(FLIP), p -> p.level(1.0).rotate(0, FAST, 0, 0.9, 120).rotate(0, FAST, 0, 0.1, 240));
        this.add("Flip aus schnellem Flug", List.of(FLIP), p -> p.fly(65, 0, 0, 3.0).rotate(0, FAST, 0, 0.4, 360).fly(65, 0, 0, 1.0));
        this.add("Flip zu kurz gedreht", List.of(FLIP), p -> p.level(1.0).rotate(0, FAST, 0, 0.3, 290));
        this.add("Flip in den Sturz", List.of(FLIP), p -> p.level(1.0).rotate(0, FAST, 0, 0.3, 430).spin(0, 0, 0, 0.2, 0.3));
        this.add("Flip 60 Hz", List.of(FLIP), p -> p.frameRate(60).level(1.0).rotate(0, FAST, 0, 0.3, 360));
        this.add("Flip 240 Hz", List.of(FLIP), p -> p.frameRate(240).level(1.0).rotate(0, FAST, 0, 0.3, 360));
        this.add("Rubik's Cube 500 Hz", List.of(RUBIKS_CUBE),
                p -> p.frameRate(500).fly(30, 0, 0, 1.5).rotate(0, FAST, 0, 0.4, 180).rotate(FAST, 0, 0, 0.2, 360).rotate(0, FAST, 0, 0.4, 180));
        this.add("Doppelflip", List.of(DOUBLE_FLIP), p -> p.level(1.0).rotate(0, FAST, 0, 0.35, 720));
        this.add("Doppelflip unsauber", List.of(DOUBLE_FLIP), p -> p.level(1.0).sloppy(5, SLOPPY).rotate(0, FAST, 0, 0.35, 720));
        this.add("Rolle sauber", List.of(ROLL), p -> p.level(1.0).rotate(FAST, 0, 0, 0.3, 360));
        this.add("Rolle links", List.of(ROLL), p -> cruise(p).rotate(-FAST, 0, 0, 0.3, 360));
        this.add("Rolle langsam", List.of(ROLL), p -> p.level(1.0).rotate(SLOW, 0, 0, 0.3, 360));
        this.add("Rolle unsauber", List.of(ROLL), p -> p.level(1.0).sloppy(7, SLOPPY).rotate(FAST, 0, 0, 0.3, 360));
        this.add("Rolle mit Nick-Anteil", List.of(ROLL), p -> p.level(1.0).rotate(FAST, -200, 0, 0.3, 380));
        this.add("Doppelrolle", List.of(DOUBLE_ROLL), p -> p.level(1.0).rotate(FAST, 0, 0, 0.3, 720));
        this.add("Flip Whoop", DroneBuild.WHOOP, List.of(FLIP), p -> p.level(1.0).rotate(0, FAST, 0, 0.35, 360));
        this.add("Rolle Racer", DroneBuild.RACER, List.of(ROLL), p -> p.level(1.0).rotate(FAST, 0, 0, 0.3, 360));
        this.add("Flip Cinewhoop", DroneBuild.CINEWHOOP, List.of(FLIP), p -> p.level(1.0).rotate(0, 500, 0, 0.4, 360));
        this.check();
    }

    @Test
    void snapRollAndCorkscrew() {
        this.add("Snap Roll Knüppel in die Ecke", List.of(SNAP_ROLL), p -> p.level(1.0).rotate(FAST, -FAST, 0, 0.3, 360));
        this.add("Snap Roll vorwärts", List.of(SNAP_ROLL), p -> cruise(p).rotate(-FAST, FAST, 0, 0.3, 360));
        this.add("Snap Roll unsauber", List.of(SNAP_ROLL), p -> p.level(1.0).sloppy(11, SLOPPY).rotate(FAST, -FAST, 0, 0.3, 370));
        this.add("Korkenzieher", List.of(CORKSCREW), p -> p.fly(30, 0, 0, 2.0).rotate(300, 0, 0, 0.5, 720));
        this.add("Korkenzieher eine Windung", List.of(CORKSCREW), p -> p.fly(30, 0, 0, 2.0).rotate(260, 0, 0, 0.55, 360));
        this.add("Korkenzieher unsauber", List.of(CORKSCREW), p -> p.fly(30, 0, 0, 2.0).sloppy(13, 80).rotate(-300, 0, 0, 0.5, 720));
        this.add("Langsame Rolle ohne Gas ist kein Korkenzieher", List.of(ROLL), p -> p.level(1.0).rotate(300, 0, 0, 0.12, 360));
        this.check();
    }

    @Test
    void rewindsGoBackTheWayTheyCame() {
        this.add("Rewind halbe Rolle", List.of(REWIND), p -> p.level(1.0).rotate(FAST, 0, 0, 0.3, 180).rotate(-FAST, 0, 0, 0.3, 180));
        this.add("Rewind mit Pause", List.of(REWIND),
                p -> p.level(1.0).rotate(FAST, 0, 0, 0.3, 180).spin(0, 0, 0, 0.1, 0.3).rotate(-FAST, 0, 0, 0.3, 180));
        this.add("Rewind halber Flip", List.of(REWIND), p -> cruise(p).rotate(0, FAST, 0, 0.3, 200).rotate(0, -FAST, 0, 0.3, 200));
        this.add("Flip-Rewind", List.of(FLIP_REWIND), p -> p.level(1.0).rotate(0, FAST, 0, 0.3, 360).rotate(0, -FAST, 0, 0.3, 360));
        this.add("Rewind unsauber", List.of(REWIND),
                p -> p.level(1.0).sloppy(17, SLOPPY).rotate(FAST, 0, 0, 0.3, 190).rotate(-FAST, 0, 0, 0.3, 160));
        this.check();
    }

    @Test
    void juicyFlickAndRubiksCube() {
        this.add("Juicy Flick", List.of(JUICY_FLICK), p -> cruise(p).rotate(0, FAST, 0, 0.6, 180).rotate(FAST, 0, 0, 0.3, 180));
        this.add("Juicy Flick links", List.of(JUICY_FLICK), p -> cruise(p).rotate(0, FAST, 0, 0.6, 180).rotate(-FAST, 0, 0, 0.3, 180));
        this.add("Juicy Flick im Kreis gerührt", List.of(JUICY_FLICK),
                p -> cruise(p).rotate(0, FAST, 0, 0.6, 150).rotate(FAST * 0.7, FAST * 0.7, 0, 0.4, 50).rotate(FAST, 0, 0, 0.3, 150));
        this.add("Juicy Flick langsam", List.of(JUICY_FLICK), p -> cruise(p).rotate(0, SLOW, 0, 0.6, 180).rotate(SLOW, 0, 0, 0.3, 180));
        this.add("Juicy Flick unsauber", List.of(JUICY_FLICK),
                p -> cruise(p).sloppy(19, SLOPPY).rotate(0, FAST, 0, 0.6, 170).rotate(FAST, 0, 0, 0.3, 190));
        this.add("Rubik's Cube", List.of(RUBIKS_CUBE),
                p -> cruise(p).rotate(0, FAST, 0, 0.4, 180).rotate(FAST, 0, 0, 0.2, 360).rotate(0, FAST, 0, 0.4, 180));
        this.add("Rubik's Cube mit Rollen außen", List.of(RUBIKS_CUBE),
                p -> cruise(p).rotate(FAST, 0, 0, 0.4, 180).rotate(0, FAST, 0, 0.2, 360).rotate(FAST, 0, 0, 0.4, 180));
        this.add("Rubik's Cube aus vier Hälften", List.of(RUBIKS_CUBE),
                p -> cruise(p).rotate(0, FAST, 0, 0.4, 180).rotate(FAST, 0, 0, 0.3, 180).rotate(0, FAST, 0, 0.4, 180).rotate(FAST, 0, 0, 0.3, 180));
        this.add("Rubik's Cube unsauber", List.of(RUBIKS_CUBE),
                p -> cruise(p).sloppy(23, SLOPPY).rotate(0, FAST, 0, 0.4, 170).rotate(FAST, 0, 0, 0.2, 370).rotate(0, FAST, 0, 0.4, 190));
        this.check();
    }

    @Test
    void loopsAndReversals() {
        this.add("Power Loop", List.of(POWER_LOOP), p -> p.fly(35, 0, 0, 2.0).spin(0, -220, 0, 0.85, 1.65));
        this.add("Power Loop unsauber", List.of(POWER_LOOP), p -> p.fly(35, 0, 0, 2.0).sloppy(29, 90).rotate(0, -230, 0, 0.85, 360));
        this.add("Power Loop mit Gaswechsel", List.of(POWER_LOOP),
                p -> p.fly(35, 0, 0, 2.0).rotate(0, -220, 0, 1.0, 120).rotate(0, -260, 0, 0.6, 120).rotate(0, -220, 0, 0.9, 120));
        this.add("Anti Matty (Matty Flip rückwärts)", List.of(ANTI_MATTY),
                p -> p.fly(35, 0, 0, 2.0).spin(0, -650, 0, 0.1, 0.22).spin(0, 0, 0, 0.8, 0.5).spin(0, -650, 0, 0.1, 0.33));
        this.add("Matty Flip vorwärts", List.of(MATTY_FLIP),
                p -> p.fly(35, 0, 0, 2.0).rotate(0, FAST, 0, 0.1, 190).rotate(0, 200, 0, 1.0, 100).rotate(0, FAST, 0, 0.2, 80));
        this.add("Split-S", List.of(SPLIT_S), p -> p.fly(35, 0, 0, 2.0).rotate(FAST, 0, 0, 0.2, 180).rotate(0, -300, 0, 0.6, 180));
        this.add("Split-S gleichzeitig", List.of(SPLIT_S),
                p -> p.fly(35, 0, 0, 2.0).rotate(FAST, 0, 0, 0.2, 110).rotate(FAST * 0.6, -300, 0, 0.6, 120).rotate(0, -300, 0, 0.6, 110));
        this.add("Split-S unsauber", List.of(SPLIT_S),
                p -> p.fly(35, 0, 0, 2.0).sloppy(31, SLOPPY).rotate(FAST, 0, 0, 0.2, 190).rotate(0, -300, 0, 0.6, 170));
        this.add("Immelmann", List.of(IMMELMANN), p -> p.fly(35, 0, 0, 2.0).rotate(0, -350, 0, 0.75, 180).rotate(FAST, 0, 0, 0.3, 180));
        this.add("Immelmann unsauber", List.of(IMMELMANN),
                p -> p.fly(35, 0, 0, 2.0).sloppy(37, SLOPPY).rotate(0, -350, 0, 0.75, 190).rotate(-FAST, 0, 0, 0.3, 170));
        this.check();
    }

    @Test
    void spinsUpsideDown() {
        this.add("Inverted Yaw Spin", List.of(INVERTED_YAW_SPIN),
                p -> p.level(0.5).spin(0, 0, 0, 0.9, 0.5).rotate(FAST, 0, 0, 0.2, 180).rotate(0, 0, 750, 0.15, 360).rotate(FAST, 0, 0, 0.3, 180));
        this.add("Inverted Yaw Spin mit Flip hinein", List.of(INVERTED_YAW_SPIN),
                p -> p.level(0.5).spin(0, 0, 0, 0.9, 0.5).rotate(0, FAST, 0, 0.2, 180).rotate(0, 0, -750, 0.15, 360).rotate(FAST, 0, 0, 0.3, 180));
        this.add("Inverted Yaw Spin unsauber", List.of(INVERTED_YAW_SPIN),
                p -> p.level(0.5).spin(0, 0, 0, 0.9, 0.5).sloppy(41, 100).rotate(FAST, 0, 0, 0.2, 180).rotate(0, 0, 750, 0.15, 370)
                        .rotate(-FAST, 0, 0, 0.3, 180));
        this.add("Trippy Spin", List.of(TRIPPY_SPIN),
                p -> p.level(0.5).rotate(FAST, 0, 0, 0.2, 180).rotate(0, 0, 500, 0.05, 720).rotate(FAST, 0, 0, 0.4, 180));
        this.add("Trippy Spin langsam", List.of(TRIPPY_SPIN),
                p -> p.level(0.5).rotate(FAST, 0, 0, 0.2, 180).rotate(0, 0, 330, 0.05, 380).rotate(-FAST, 0, 0, 0.4, 180));
        this.add("McTwist", List.of(MCTWIST),
                p -> p.fly(20, 0, 0, 1.5).rotate(0, FAST, 0, 0.3, 180).rotate(0, 0, 700, 0.1, 360).rotate(0, FAST, 0, 0.3, 180));
        this.add("McTwist unsauber", List.of(MCTWIST),
                p -> p.fly(20, 0, 0, 1.5).sloppy(43, 100).rotate(0, FAST, 0, 0.3, 190).rotate(0, 0, 700, 0.1, 380).rotate(0, FAST, 0, 0.3, 170));
        this.check();
    }

    @Test
    void knifeEdgeHoldsTheLine() {
        this.add("Knife Edge", List.of(KNIFE_EDGE),
                p -> p.fly(40, 0, 0, 2.5).level(0.25).rotate(FAST, 0, 0, 0.3, 90).spin(0, 0, 0, 0.1, 0.8).rotate(-FAST, 0, 0, 0.4, 90));
        this.add("Knife Edge links", List.of(KNIFE_EDGE),
                p -> p.fly(40, 0, 0, 2.5).level(0.25).rotate(-FAST, 0, 0, 0.3, 90).spin(0, 0, 0, 0.08, 1.0).rotate(FAST, 0, 0, 0.4, 90));
        this.add("Knife Edge unsauber", List.of(KNIFE_EDGE),
                p -> p.fly(40, 0, 0, 2.5).level(0.25).sloppy(47, 60).rotate(FAST, 0, 0, 0.3, 85).spin(0, 0, 0, 0.1, 0.8)
                        .rotate(-FAST, 0, 0, 0.4, 85));
        this.check();
    }

    @Test
    void humanSticksAndUnevenFramesStillWork() {
        this.add("Flip mit weichen Knüppeln", List.of(FLIP), p -> p.human(0.07).level(1.0).rotate(0, FAST, 0, 0.3, 340));
        this.add("Rolle mit weichen Knüppeln", List.of(ROLL), p -> p.human(0.07).level(1.0).rotate(FAST, 0, 0, 0.3, 340));
        this.add("Juicy Flick mit weichen Knüppeln", List.of(JUICY_FLICK),
                p -> cruise(p).human(0.07).rotate(0, FAST, 0, 0.6, 160).rotate(FAST, 0, 0, 0.3, 160));
        this.add("Rubik's Cube mit weichen Knüppeln", List.of(RUBIKS_CUBE),
                p -> cruise(p).human(0.06).rotate(0, FAST, 0, 0.4, 165).rotate(FAST, 0, 0, 0.2, 340).rotate(0, FAST, 0, 0.4, 165));
        this.add("Split-S mit weichen Knüppeln", List.of(SPLIT_S),
                p -> p.fly(35, 0, 0, 2.0).human(0.07).rotate(FAST, 0, 0, 0.2, 165).rotate(0, -300, 0, 0.6, 165));
        this.add("Inverted Yaw Spin mit weichen Knüppeln", List.of(INVERTED_YAW_SPIN),
                p -> p.level(0.5).spin(0, 0, 0, 0.9, 0.5).human(0.07).rotate(FAST, 0, 0, 0.2, 165).rotate(0, 0, 750, 0.15, 345)
                        .rotate(FAST, 0, 0, 0.3, 165));
        this.add("Flip bei 30 bis 200 Hz", List.of(FLIP), p -> p.unevenFrames(53, 30, 200).level(1.0).rotate(0, FAST, 0, 0.3, 360));
        this.add("Rubik's Cube bei 25 bis 90 Hz", List.of(RUBIKS_CUBE),
                p -> p.unevenFrames(59, 25, 90).fly(30, 0, 0, 1.5).rotate(0, FAST, 0, 0.4, 180).rotate(FAST, 0, 0, 0.2, 360)
                        .rotate(0, FAST, 0, 0.4, 180));
        this.add("Juicy Flick bei 30 bis 144 Hz", List.of(JUICY_FLICK),
                p -> p.unevenFrames(61, 30, 144).fly(30, 0, 0, 1.5).rotate(0, FAST, 0, 0.6, 180).rotate(FAST, 0, 0, 0.3, 180));
        this.check();
    }

    @Test
    void chainedTricksAreAllCountedOnce() {
        this.add("Flip direkt in Rolle", List.of(FLIP, ROLL), p -> p.level(1.0).rotate(0, FAST, 0, 0.3, 360).rotate(FAST, 0, 0, 0.3, 360));
        this.add("Juicy Flick direkt in Flip", List.of(JUICY_FLICK, FLIP),
                p -> cruise(p).rotate(0, FAST, 0, 0.6, 180).rotate(FAST, 0, 0, 0.3, 180).spin(0, 0, 0, 0.35, 0.15).rotate(0, FAST, 0, 0.3, 360));
        this.add("Split-S und Power Loop", List.of(SPLIT_S, POWER_LOOP),
                p -> p.fly(35, 0, 0, 2.0).rotate(FAST, 0, 0, 0.2, 180).rotate(0, -300, 0, 0.6, 180).fly(35, 0, 0, 1.0)
                        .spin(0, -220, 0, 0.85, 1.65));
        this.add("Rolle, Flip, Rolle", List.of(ROLL, FLIP, ROLL),
                p -> p.level(1.0).rotate(FAST, 0, 0, 0.3, 360).rotate(0, FAST, 0, 0.3, 360).rotate(FAST, 0, 0, 0.3, 360));
        this.check();
    }

    @Test
    void normalFlyingNeverScores() {
        this.add("Hoher Schnellflug mit Korrekturen", List.of(), p -> {
            p.fly(75, 0, 0, 3.0).fly(78, 15, 20, 1.0).fly(72, -20, -30, 1.0).fly(80, 0, 0, 1.0);
        });
        this.add("Enge Kurven bis 80 Grad", List.of(), p -> p.fly(40, 0, 0, 1.5).turn(30, 80, 2.5).turn(30, -80, 2.5));
        this.add("Schnelle Kurvenwechsel", List.of(),
                p -> p.fly(45, 0, 0, 1.5).turn(40, 75, 0.6).turn(40, -75, 0.6).turn(40, 75, 0.6).turn(40, -75, 0.6));
        this.add("Rückblick ohne Rückenlage", List.of(), p -> cruise(p).rotate(0, 0, 500, 0.3, 180).spin(0, 0, 0, 0.3, 0.3).rotate(0, 0, -500, 0.3, 180));
        this.add("Halbe Rolle 120 Grad und zurück", List.of(), p -> p.level(1.0).rotate(FAST, 0, 0, 0.3, 120).rotate(-FAST, 0, 0, 0.3, 120));
        this.add("Wackeln mit 90 Grad", List.of(), p -> {
            p.level(1.0);
            for (int i = 0; i < 4; i++) {
                p.rotate(FAST, 0, 0, 0.3, 80).rotate(-FAST, 0, 0, 0.3, 160).rotate(FAST, 0, 0, 0.3, 80);
            }
        });
        this.add("Bremsen mit 100 Grad Nick", List.of(), p -> p.fly(50, 0, 0, 2.0).rotate(0, -FAST, 0, 0.6, 140).fly(-30, 0, 0, 0.6));
        this.add("Steigen, Fallenlassen, Abfangen", List.of(), p -> p.flyThrottle(0, 0, 0, 0.9, 1.0).flyThrottle(0, 0, 0, 0.0, 1.0).level(1.0));
        this.add("Langsame Gierkurve", List.of(), p -> p.fly(25, 0, 120, 4.0));
        this.add("Kurve mit Schräglage und Gieren", List.of(), p -> p.fly(30, 0, 0, 1.0).fly(30, 25, 70, 3.0).fly(40, -30, -90, 2.0));
        this.check();
    }

    @Test
    void sloppyFreestyleCruisingNeverScores() {
        for (long seed = 1; seed <= 4; seed++) {
            long s = seed;
            this.add("Cruisen mit Knüppelrauschen " + seed, List.of(), p -> {
                java.util.Random random = new java.util.Random(s);
                p.sloppy(s, 90);
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
        }
        this.check();
    }

    @Test
    void aggressiveFlyingWithoutTricksNeverScores() {
        for (long seed = 11; seed <= 16; seed++) {
            long s = seed;
            this.add("Aggressiv ohne Tricks " + seed, List.of(), p -> {
                java.util.Random random = new java.util.Random(s);
                p.fly(40, 0, 0, 2.0).sloppy(s, 60);
                for (int i = 0; i < 30; i++) {
                    double sign = random.nextBoolean() ? 1.0 : -1.0;
                    switch (random.nextInt(4)) {
                        case 0 -> p.rotate(sign * FAST, 0, 0, 0.3 + random.nextDouble() * 0.4, 30 + random.nextDouble() * 70).level(0.4).fly(40, 0, 0, 0.4);
                        case 1 -> p.rotate(0, sign * FAST, 0, 0.3 + random.nextDouble() * 0.4, 30 + random.nextDouble() * 70).level(0.4).fly(30, 0, 0, 0.4);
                        case 2 -> p.rotate(0, 0, sign * 500, 0.35, 40 + random.nextDouble() * 200).level(0.3).fly(30, 0, 0, 0.3);
                        default -> p.fly(40, 0, 0, 1.0).turn(30 + random.nextDouble() * 20, sign * (30 + random.nextDouble() * 25), 0.5 + random.nextDouble());
                    }
                }
            });
        }
        this.check();
    }

    @Test
    void chainBonusRewardsFlowingLines() {
        SimPilot linked = SimPilot.freestyle();
        linked.level(1.0).rotate(0, FAST, 0, 0.3, 360).rotate(FAST, 0, 0, 0.3, 360).level(5.0);
        SimPilot apart = SimPilot.freestyle();
        apart.level(1.0).rotate(0, FAST, 0, 0.3, 360).level(1.2).rotate(FAST, 0, 0, 0.3, 360).level(5.0);
        assertEquals(List.of(FLIP, ROLL), linked.detected.stream().map(Trick::type).toList(), linked.report());
        assertEquals(List.of(FLIP, ROLL), apart.detected.stream().map(Trick::type).toList(), apart.report());
        assertTrue(linked.session.score().flightScore() > apart.session.score().flightScore(),
                linked.session.score().flightScore() + " ≤ " + apart.session.score().flightScore());
        assertEquals(2, linked.session.score().bestCombo());
    }

    @Test
    void chainPopupNamesEveryLinkedTrick() {
        SimPilot p = SimPilot.freestyle();
        p.level(1.0).rotate(0, FAST, 0, 0.3, 360).rotate(FAST, 0, 0, 0.3, 360).level(1.0);
        TrickScore score = p.session.score();
        assertEquals(TrickScore.Popup.TRICK, score.popup(), p.report());
        assertEquals(2, score.popupTypeCount(), p.report());
        assertEquals(FLIP, score.popupType(0));
        assertEquals(ROLL, score.popupType(1));
        assertTrue(score.popupChain());
        assertEquals(score.flightScore(), score.popupPoints());
    }
}
