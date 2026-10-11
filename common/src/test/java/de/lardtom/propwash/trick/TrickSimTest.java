package de.lardtom.propwash.trick;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.sim.StabMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrickSimTest {
    private static List<TrickType> fly(DroneBuild build, Consumer<SimPilot> script) {
        SimPilot pilot = new SimPilot(build, 120.0);
        script.accept(pilot);
        pilot.level(1.0);
        assertEquals(0, pilot.telemetry().crashCount, "Absturz im Skript");
        assertTrue(pilot.telemetry().armed, "Drohne ist nicht mehr scharf");
        List<TrickType> types = new ArrayList<>();
        for (Trick trick : pilot.detected) {
            types.add(trick.type());
        }
        return types;
    }

    private static List<TrickType> fly(Consumer<SimPilot> script) {
        return fly(DroneBuild.FREESTYLE, script);
    }

    @Test
    void frontAndBackFlipsAreSingleFlips() {
        assertEquals(List.of(TrickType.FLIP), fly(p -> p.level(1.0).spin(0, 650, 0, 0.3, 0.55).level(2.0)));
        assertEquals(List.of(TrickType.FLIP), fly(p -> p.level(1.0).spin(0, -650, 0, 0.3, 0.55).level(2.0)));
        assertEquals(List.of(TrickType.FLIP), fly(p -> p.fly(35, 0, 0, 1.5).spin(0, 650, 0, 0.3, 0.55).level(2.0)));
    }

    @Test
    void doubleAndTripleFlipsCountTheTurns() {
        assertEquals(List.of(TrickType.DOUBLE_FLIP), fly(p -> p.level(1.0).spin(0, 700, 0, 0.3, 1.03).level(2.0)));
        assertEquals(List.of(TrickType.TRIPLE_FLIP), fly(p -> p.level(1.0).spin(0, 700, 0, 0.35, 1.55).level(2.0)));
    }

    @Test
    void rollsInBothDirections() {
        assertEquals(List.of(TrickType.ROLL), fly(p -> p.level(1.0).spin(650, 0, 0, 0.3, 0.55).level(2.0)));
        assertEquals(List.of(TrickType.ROLL), fly(p -> p.fly(30, 0, 0, 1.5).spin(-650, 0, 0, 0.3, 0.55).level(2.0)));
        assertEquals(List.of(TrickType.DOUBLE_ROLL), fly(p -> p.level(1.0).spin(700, 0, 0, 0.3, 1.03).level(2.0)));
    }

    @Test
    void flipsWorkOnWhoopsAndRacers() {
        assertEquals(List.of(TrickType.FLIP), fly(DroneBuild.WHOOP, p -> p.level(1.0).spin(0, 650, 0, 0.3, 0.55).level(2.0)));
        assertEquals(List.of(TrickType.ROLL), fly(DroneBuild.RACER, p -> p.level(1.0).spin(650, 0, 0, 0.3, 0.55).level(2.0)));
    }

    @Test
    void powerLoopNeedsTheWholeArc() {
        assertEquals(List.of(TrickType.POWER_LOOP), fly(p -> p.fly(35, 0, 0, 2.0).spin(0, -220, 0, 0.85, 1.65).level(2.0)));
        assertEquals(List.of(TrickType.POWER_LOOP), fly(p -> p.fly(35, 0, 0, 2.0).spin(0, -250, 0, 1.0, 1.45).level(2.0)));
    }

    @Test
    void splitSRollsInvertedAndPullsThrough() {
        assertEquals(List.of(TrickType.SPLIT_S),
                fly(p -> p.fly(35, 0, 0, 2.0).spin(650, 0, 0, 0.2, 0.28).spin(0, -300, 0, 0.6, 0.6).level(2.0)));
    }

    @Test
    void mattyFlipReversesWithAnInvertedPunch() {
        assertEquals(List.of(TrickType.MATTY_FLIP),
                fly(p -> p.fly(35, 0, 0, 2.0).spin(0, 650, 0, 0.1, 0.3).spin(0, 200, 0, 1.0, 0.5).spin(0, 650, 0, 0.2, 0.12).level(2.0)));
        assertEquals(List.of(TrickType.ANTI_MATTY),
                fly(p -> p.fly(35, 0, 0, 2.0).spin(0, -650, 0, 0.1, 0.22).spin(0, 0, 0, 0.8, 0.5).spin(0, -650, 0, 0.1, 0.33).level(2.0)));
    }

    @Test
    void diveNeedsASteepDropAndARecovery() {
        assertEquals(List.of(TrickType.DIVE), fly(p -> p.flyThrottle(85, 0, 0, 0.15, 3.0).level(3.0)));
        assertEquals(List.of(), fly(p -> p.flyThrottle(85, 0, 0, 0.15, 0.6).level(3.0)));
    }

    @Test
    void yawSpinsInPlace() {
        assertEquals(List.of(TrickType.YAW_SPIN), fly(p -> p.level(1.0).spin(0, 0, 600, p.hover(), 0.75).level(2.0)));
        assertEquals(List.of(TrickType.DOUBLE_YAW_SPIN), fly(p -> p.level(1.0).spin(0, 0, -600, p.hover(), 1.3).level(2.0)));
        assertEquals(List.of(), fly(p -> p.level(1.0).spin(0, 0, 600, p.hover(), 0.4).level(2.0)));
    }

    @Test
    void invertedHangNeedsTimeUpsideDown() {
        assertEquals(List.of(TrickType.INVERTED_HANG),
                fly(p -> p.level(1.0).spin(650, 0, 0, 0.3, 0.28).spin(0, 0, 0, 0.0, 1.2).spin(650, 0, 0, 0.3, 0.28).level(2.0)));
        assertEquals(List.of(TrickType.REWIND),
                fly(p -> p.level(1.0).spin(650, 0, 0, 0.3, 0.28).spin(0, 0, 0, 0.0, 0.3).spin(-650, 0, 0, 0.3, 0.28).level(2.0)));
    }

    @Test
    void halfRollAndBackIsARewindButAHalfFlipIsNothing() {
        assertEquals(List.of(TrickType.REWIND), fly(p -> p.level(1.0).spin(650, 0, 0, 0.3, 0.3).spin(-650, 0, 0, 0.3, 0.3).level(2.0)));
        assertEquals(List.of(), fly(p -> p.level(1.0).spin(0, 400, 0, 0.3, 0.3).level(2.0)));
    }

    @Test
    void gapThroughASlotBetweenBlocks() {
        assertEquals(List.of(TrickType.GAP), fly(p -> {
            p.fly(30, 0, 0, 1.5);
            double x = p.position.x;
            double y = p.position.y;
            double z = p.position.z + 8.0;
            p.box(x + 0.75, y - 10, z, x + 3, y + 10, z + 1).box(x - 3, y - 10, z, x - 0.75, y + 10, z + 1);
            p.fly(30, 0, 0, 2.0);
        }));
        assertEquals(List.of(TrickType.GAP), fly(p -> {
            p.fly(30, 0, 0, 1.5);
            double x = p.position.x;
            double y = p.position.y;
            double z = p.position.z + 8.0;
            p.box(x - 5, y - 10, z, x + 5, y - 0.7, z + 2).box(x - 5, y + 0.8, z, x + 5, y + 10, z + 2);
            p.fly(30, 0, 0, 2.0);
        }));
    }

    @Test
    void proximityAlongAWallButNotOverTheGround() {
        assertEquals(List.of(TrickType.PROXIMITY), fly(p -> {
            p.fly(35, 0, 0, 2.0);
            double x = p.position.x;
            double y = p.position.y;
            double z = p.position.z;
            p.box(x - 3, y - 10, z + 5, x - 0.6, y + 10, z + 18);
            p.fly(35, 0, 0, 2.0);
        }));
        assertEquals(List.of(), fly(p -> {
            p.box(-500, -100, -500, 500, 119.0, 500);
            p.fly(35, 0, 0, 5.0);
        }));
        assertEquals(List.of(), fly(p -> {
            p.box(-3, -100, -500, -2.0, 300, 500);
            p.fly(35, 0, 0, 5.0);
        }));
    }

    @Test
    void racingLinesAreNotTricks() {
        assertEquals(List.of(), fly(p -> p.fly(45, 0, 0, 2.0).turn(40, 60, 2.0).fly(40, 0, 0, 1.5).turn(40, -70, 4.0).fly(40, 0, 0, 1.0)
                .turn(30, 50, 0.8).turn(30, -50, 0.8).turn(30, 50, 0.8).turn(30, -50, 0.8).turn(40, 75, 3.0).level(2.0)));
        assertEquals(List.of(), fly(p -> p.fly(20, 0, 0, 1.0).turn(15, 80, 5.0).level(2.0)));
        assertEquals(List.of(), fly(p -> p.flyThrottle(20, 0, 0, 0.9, 1.5).flyThrottle(30, 0, 0, 0.1, 3.0).level(2.0)));
    }

    @Test
    void randomCruisingNeverScores() {
        for (long seed = 1; seed <= 6; seed++) {
            Random random = new Random(seed);
            List<TrickType> found = fly(p -> {
                for (int i = 0; i < 50; i++) {
                    double tilt = random.nextDouble() * 55.0;
                    double bank = (random.nextDouble() * 2.0 - 1.0) * 65.0;
                    double seconds = 0.4 + random.nextDouble() * 1.2;
                    if (random.nextBoolean()) {
                        p.turn(tilt, bank, seconds);
                    } else {
                        p.fly(tilt, bank * 0.3, (random.nextDouble() * 2.0 - 1.0) * 90.0, seconds);
                    }
                }
            });
            assertEquals(List.of(), found, "Seed " + seed);
        }
    }

    @Test
    void hoveringWithStickNoiseNeverScores() {
        Random random = new Random(3);
        assertEquals(List.of(), fly(p -> {
            for (int i = 0; i < 400; i++) {
                p.spin((random.nextDouble() - 0.5) * 160.0, (random.nextDouble() - 0.5) * 160.0, (random.nextDouble() - 0.5) * 160.0,
                        p.hover(), 0.05);
                p.level(0.02);
            }
        }));
    }

    @Test
    void flipOutOfAngleModeAsInTheSelfTest() {
        SimPilot p = SimPilot.freestyle();
        p.input.mode = StabMode.ANGLE;
        p.spin(0, 0, 0, p.hover(), 1.0);
        p.input.mode = StabMode.ACRO;
        p.spin(0, 650, 0, 0.3, 0.55);
        p.input.mode = StabMode.ANGLE;
        p.spin(0, 0, 0, p.hover(), 3.0);
        assertEquals(List.of(TrickType.FLIP), p.detected.stream().map(Trick::type).toList());
        assertEquals(TrickScore.Popup.TRICK, p.session.score().popup());
    }

    @Test
    void chainedTricksBuildACombo() {
        SimPilot p = SimPilot.freestyle();
        p.level(1.0).spin(0, 650, 0, 0.3, 0.55).level(1.2).spin(650, 0, 0, 0.3, 0.55).level(1.2).spin(0, 0, 600, p.hover(), 0.75).level(5.0);
        TrickScore score = p.session.score();
        assertEquals(3, score.bestCombo());
        assertEquals(0, score.comboCount());
        assertTrue(score.flightScore() > 300 * 1.5, "Punkte " + score.flightScore());
        assertEquals(TrickScore.Popup.COMBO, score.popup());
    }
}
