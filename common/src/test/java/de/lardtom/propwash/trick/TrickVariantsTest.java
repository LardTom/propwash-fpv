package de.lardtom.propwash.trick;

import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static de.lardtom.propwash.trick.TrickType.ANTI_MATTY;
import static de.lardtom.propwash.trick.TrickType.BACKWARDS_DIVE;
import static de.lardtom.propwash.trick.TrickType.BARANI;
import static de.lardtom.propwash.trick.TrickType.COIN_DROP;
import static de.lardtom.propwash.trick.TrickType.DIVE;
import static de.lardtom.propwash.trick.TrickType.DOUBLE_FLIP;
import static de.lardtom.propwash.trick.TrickType.DOUBLE_ROLL;
import static de.lardtom.propwash.trick.TrickType.DOUBLE_YAW_SPIN;
import static de.lardtom.propwash.trick.TrickType.FLIP;
import static de.lardtom.propwash.trick.TrickType.FLIP_3D;
import static de.lardtom.propwash.trick.TrickType.FLIP_REWIND;
import static de.lardtom.propwash.trick.TrickType.HALF_MATTY;
import static de.lardtom.propwash.trick.TrickType.HAMMERHEAD;
import static de.lardtom.propwash.trick.TrickType.IMMELMANN;
import static de.lardtom.propwash.trick.TrickType.IMMELMATT;
import static de.lardtom.propwash.trick.TrickType.INVERTED_360_POWER_LOOP;
import static de.lardtom.propwash.trick.TrickType.INVERTED_FLIGHT;
import static de.lardtom.propwash.trick.TrickType.INVERTED_HANG;
import static de.lardtom.propwash.trick.TrickType.INVERTED_HOVER;
import static de.lardtom.propwash.trick.TrickType.KNIFE_EDGE;
import static de.lardtom.propwash.trick.TrickType.MATTY_FLIP;
import static de.lardtom.propwash.trick.TrickType.MAVERICK_LOOP;
import static de.lardtom.propwash.trick.TrickType.MCTWIST;
import static de.lardtom.propwash.trick.TrickType.POWER_FLIP;
import static de.lardtom.propwash.trick.TrickType.POWER_LOOP;
import static de.lardtom.propwash.trick.TrickType.POWER_LOOP_3D;
import static de.lardtom.propwash.trick.TrickType.POWER_MATTY;
import static de.lardtom.propwash.trick.TrickType.POWER_ROLL;
import static de.lardtom.propwash.trick.TrickType.PROXIMITY;
import static de.lardtom.propwash.trick.TrickType.REVERSE_KNIFE_EDGE;
import static de.lardtom.propwash.trick.TrickType.REWIND;
import static de.lardtom.propwash.trick.TrickType.ROLL;
import static de.lardtom.propwash.trick.TrickType.ROLL_3D;
import static de.lardtom.propwash.trick.TrickType.SEGMENTED;
import static de.lardtom.propwash.trick.TrickType.SIDE_LOOP;
import static de.lardtom.propwash.trick.TrickType.SPLITBACK;
import static de.lardtom.propwash.trick.TrickType.SPLIT_S;
import static de.lardtom.propwash.trick.TrickType.SPLIT_S_540;
import static de.lardtom.propwash.trick.TrickType.TORNADO;
import static de.lardtom.propwash.trick.TrickType.VANNY_ROLL;
import static de.lardtom.propwash.trick.TrickType.WALL_RIDE;
import static de.lardtom.propwash.trick.TrickType.YAW_SPIN;
import static de.lardtom.propwash.trick.TrickVariantScripts.FAST;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrickVariantsTest {
    static final double SLOPPY = 100.0;

    record Case(String name, DroneBuild build, List<TrickType> expected, Consumer<SimPilot> script) {
    }

    private final List<Case> cases = new ArrayList<>();

    private void add(String name, List<TrickType> expected, Consumer<SimPilot> script) {
        this.cases.add(new Case(name, DroneBuild.FREESTYLE, expected, script));
    }

    private void add(String name, DroneBuild build, List<TrickType> expected, Consumer<SimPilot> script) {
        this.cases.add(new Case(name, build, expected, script));
    }

    private void variants(String name, List<TrickType> expected, Consumer<SimPilot> script) {
        this.add(name, expected, script);
        this.add(name + " unsauber", expected, p -> script.accept(p.sloppy(name.hashCode(), SLOPPY)));
        this.add(name + " mit weichen Knüppeln", expected, p -> script.accept(p.human(0.06)));
        this.add(name + " bei 30 bis 144 Hz", expected, p -> script.accept(p.unevenFrames(name.hashCode(), 30, 144)));
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

    @Test
    void mattyFamilyKeepsItsDirections() {
        this.variants("Matty Flip vorwärts", List.of(MATTY_FLIP), TrickVariantScripts::matty);
        this.variants("Anti Matty", List.of(ANTI_MATTY), TrickVariantScripts::antiMatty);
        this.variants("Half Matty", List.of(HALF_MATTY), TrickVariantScripts::halfMatty);
        this.variants("Power Matty", List.of(POWER_MATTY), TrickVariantScripts::powerMatty);
        this.add("Half Matty Whoop", DroneBuild.WHOOP, List.of(HALF_MATTY), TrickVariantScripts::halfMatty);
        this.add("Doppelflip ohne Gasstoß bleibt Doppelflip", List.of(DOUBLE_FLIP),
                p -> TrickVariantScripts.cruise(p).rotate(0, FAST, 0, 0.2, 720));
        this.add("Halbe Rolle, halber Flip vorwärts ohne Gas ist nichts", List.of(),
                p -> TrickVariantScripts.cruise(p).rotate(FAST, 0, 0, 0.2, 180).rotate(0, 250, 0, 0.1, 180));
        this.add("Split-S bleibt Split-S", List.of(SPLIT_S), p -> TrickVariantScripts.cruise(p).rotate(FAST, 0, 0, 0.2, 180).rotate(0, -300, 0, 0.6, 180));
        this.check();
    }

    @Test
    void splitSVariantsAndRewinds() {
        this.variants("540-Split-S", List.of(SPLIT_S_540), TrickVariantScripts::split540);
        this.variants("Splitback", List.of(SPLITBACK), TrickVariantScripts::splitback);
        this.variants("Flip-Rewind", List.of(FLIP_REWIND), p -> TrickVariantScripts.flipRewind(p, 0, FAST));
        this.variants("Rollen-Rewind", List.of(FLIP_REWIND), p -> TrickVariantScripts.flipRewind(p, FAST, 0));
        this.variants("Segmentierter Flip", List.of(SEGMENTED), p -> TrickVariantScripts.segmented(p, 0, FAST, 0.6));
        this.variants("Segmentierte Rolle", List.of(SEGMENTED), p -> TrickVariantScripts.segmented(p, FAST, 0, 0.6));
        this.add("Anderthalb Rollen und eine halbe Rolle sind eine Doppelrolle", List.of(DOUBLE_ROLL),
                p -> TrickVariantScripts.cruise(p).rotate(FAST, 0, 0, 0.2, 540).rotate(FAST, 0, 0, 0.2, 180));
        this.add("Halbe Rolle hin und zurück bleibt Rewind", List.of(REWIND), p -> p.level(1.0).rotate(FAST, 0, 0, 0.3, 180).rotate(-FAST, 0, 0, 0.3, 180));
        this.add("Halber Flip mit kurzer Pause bleibt Flip", List.of(FLIP), p -> TrickVariantScripts.segmented(p, 0, FAST, 0.15));
        this.add("Halbe Rolle, lange kopfüber, halbe Rolle bleibt Rolle und Kopfüber hängen", List.of(ROLL, INVERTED_HANG),
                p -> TrickVariantScripts.segmented(p, FAST, 0, 1.3));
        this.check();
    }

    @Test
    void powerLoopVariants() {
        this.variants("Power Flip", List.of(POWER_FLIP), p -> TrickVariantScripts.powerFlip(p, -1.0));
        this.variants("Reversed Power Flip", List.of(POWER_FLIP), p -> TrickVariantScripts.powerFlip(p, 1.0));
        this.variants("Power Roll", List.of(POWER_ROLL), TrickVariantScripts::powerRoll);
        this.variants("Inverted 360 Power Loop", List.of(INVERTED_360_POWER_LOOP), TrickVariantScripts::inverted360Loop);
        this.variants("Side Loop", List.of(SIDE_LOOP), p -> TrickVariantScripts.sideLoop(p, 1.0));
        this.add("Side Loop links", List.of(SIDE_LOOP), p -> TrickVariantScripts.sideLoop(p, -1.0));
        this.variants("Barani", List.of(BARANI), TrickVariantScripts::barani);
        this.variants("Maverick Loop", List.of(MAVERICK_LOOP), p -> TrickVariantScripts.maverick(p, 4.0, 5.0));
        this.add("Maverick Loop groß", List.of(MAVERICK_LOOP), p -> TrickVariantScripts.maverick(p, 6.0, 6.5));
        this.variants("Immelmatt", List.of(IMMELMANN, MATTY_FLIP, IMMELMATT), TrickVariantScripts::immelmatt);
        this.add("Power Loop bleibt Power Loop", List.of(POWER_LOOP), p -> TrickVariantScripts.cruise(p).hold(0, -220, 0, 0.85, 1.65));
        this.add("McTwist bleibt McTwist", List.of(MCTWIST),
                p -> p.fly(20, 0, 0, 1.5).rotate(0, FAST, 0, 0.3, 180).rotate(0, 0, 700, 0.1, 360).rotate(0, FAST, 0, 0.3, 180));
        this.add("Immelmann mit Pause ist keine Immelmatt", List.of(IMMELMANN, MATTY_FLIP),
                p -> TrickVariantScripts.immelmann(p).level(1.2).fly(35, 0, 0, 1.0).rotate(0, FAST, 0, 0.1, 190).rotate(0, 200, 0, 1.0, 100)
                        .rotate(0, FAST, 0, 0.2, 80));
        this.add("Kleiner Hüpfer über ein Hindernis ist kein Maverick Loop", List.of(), p -> TrickVariantScripts.maverick(p, 1.0, 2.5));
        this.check();
    }

    @Test
    void spinsAndDiveVariants() {
        this.variants("Vanny Roll", List.of(VANNY_ROLL), p -> TrickVariantScripts.vanny(p, 1.0));
        this.add("Vanny Roll links", List.of(VANNY_ROLL), p -> TrickVariantScripts.vanny(p, -1.0));
        this.add("Coin Drop", List.of(COIN_DROP), p -> TrickVariantScripts.coinDrop(p, 720));
        this.add("Coin Drop eine Drehung", List.of(COIN_DROP), p -> TrickVariantScripts.coinDrop(p, 380));
        this.add("Coin Drop unsauber", List.of(COIN_DROP), p -> TrickVariantScripts.coinDrop(p.sloppy(71, 40), 720));
        this.add("Coin Drop mit weichen Knüppeln", List.of(COIN_DROP), p -> TrickVariantScripts.coinDrop(p.human(0.06), 720));
        this.add("Coin Drop bei 30 bis 144 Hz", List.of(COIN_DROP), p -> TrickVariantScripts.coinDrop(p.unevenFrames(73, 30, 144), 720));
        this.add("Yaw-Spins mit Gas sind kein Coin Drop", List.of(DOUBLE_YAW_SPIN), p -> p.level(1.0).rotate(0, 0, 500, p.hover(), 720));
        this.variants("Tornado", List.of(TORNADO, DIVE), p -> TrickVariantScripts.tornado(p, 720));
        this.add("Tornado eine Rolle", List.of(TORNADO, DIVE), p -> TrickVariantScripts.tornado(p, 380));
        this.variants("Rückwärts-Sturzflug", List.of(BACKWARDS_DIVE), p -> TrickVariantScripts.backwardsDive(p, 45, 2.0));
        this.add("Flach fallen lassen ist kein Rückwärts-Sturzflug", List.of(),
                p -> TrickProTricksTest.apex(p).hold(0, 0, 0, 0.05, 2.0).flyThrottle(0, 0, 0, 1.0, 0.8));
        this.add("Sturzflug bleibt Sturzflug", List.of(DIVE),
                p -> TrickProTricksTest.apex(p).rotate(0, FAST, 0, 0.05, 75).hold(0, 0, 0, 0.05, 1.8).rotate(0, -FAST, 0, 0.8, 80)
                        .flyThrottle(0, 0, 0, 1.0, 0.6));
        this.variants("Hammerhead", List.of(HAMMERHEAD), p -> TrickVariantScripts.hammerhead(p, 1.0));
        this.add("Hammerhead links", List.of(HAMMERHEAD), p -> TrickVariantScripts.hammerhead(p, -1.0));
        this.add("Hochziehen und vorwärts abkippen ist kein Hammerhead", List.of(),
                p -> p.fly(40, 0, 0, 2.0).rotate(0, -300, 0, 1.0, 100).flyThrottle(-60, 0, 0, 1.0, 0.4).hold(0, 0, 0, 0.1, 0.4)
                        .rotate(0, 300, 0, 0.1, 150).hold(0, 0, 0, 0.0, 0.5).rotate(0, -300, 0, 0.9, 70).flyThrottle(0, 0, 0, 0.8, 0.6));
        this.add("Hochziehen und 90° gieren ist kein Hammerhead", List.of(),
                p -> p.fly(40, 0, 0, 2.0).rotate(0, -300, 0, 1.0, 100).flyThrottle(-60, 0, 0, 1.0, 0.4).hold(0, 0, 0, 0.1, 0.4)
                        .rotate(0, 0, 600, 0.1, 90).level(1.5));
        this.check();
    }

    @Test
    void knifeEdgeVariants() {
        this.add("Reverse Knife Edge", List.of(REVERSE_KNIFE_EDGE), p -> TrickVariantScripts.reverseKnife(p, 1.0, 80));
        this.add("Reverse Knife Edge links", List.of(REVERSE_KNIFE_EDGE), p -> TrickVariantScripts.reverseKnife(p, -1.0, 70));
        this.add("Reverse Knife Edge unsauber", List.of(REVERSE_KNIFE_EDGE), p -> TrickVariantScripts.reverseKnife(p.sloppy(79, 60), 1.0, 75));
        this.add("Reverse Knife Edge bei 30 bis 144 Hz", List.of(REVERSE_KNIFE_EDGE),
                p -> TrickVariantScripts.reverseKnife(p.unevenFrames(83, 30, 144), 1.0, 70));
        this.add("Wall Ride", List.of(WALL_RIDE), p -> TrickVariantScripts.wallRide(p, 0.5, 80));
        this.add("Wall Ride mit 0,7 m Abstand", List.of(WALL_RIDE), p -> TrickVariantScripts.wallRide(p, 0.7, 75));
        this.add("Wall Ride bei 30 bis 144 Hz", List.of(WALL_RIDE), p -> TrickVariantScripts.wallRide(p.unevenFrames(89, 30, 144), 0.5, 60));
        this.add("Knife Edge mit 2 m Abstand zur Wand", List.of(KNIFE_EDGE), p -> TrickVariantScripts.wallRide(p, 2.0, 80));
        this.add("Dicht an der Wand ohne Seitenlage bleibt Proximity", List.of(PROXIMITY), p -> {
            p.fly(35, 0, 0, 2.0);
            double x = p.position.x;
            double y = p.position.y;
            double z = p.position.z;
            p.box(x - 3, y - 10, z + 5, x - 0.6, y + 10, z + 18);
            p.fly(35, 0, 0, 2.0);
        });
        this.check();
    }

    @Test
    void threeDVariantsOnlyCountInThreeDMode() {
        this.variants("Schweben kopfüber 3D", List.of(INVERTED_HOVER), p -> TrickVariantScripts.invertedHover(p, 2.0));
        this.add("Schweben kopfüber 3D lang", List.of(INVERTED_HOVER), p -> TrickVariantScripts.invertedHover(p, 4.0));
        this.add("Rückenflug 3D mit Fahrt bleibt Rückenflug", List.of(INVERTED_FLIGHT),
                p -> p.mode3d().fly(25, 0, 0, 1.5).rotate(0, FAST, 0, -0.2, 180).invertedHold(0, 0, 0, 2.0, 0.0).rotate(FAST, 0, 0, 0.0, 180));
        this.variants("3D-Flip", List.of(FLIP_3D), p -> TrickVariantScripts.flip3d(p, 0, FAST));
        this.variants("3D-Rolle", List.of(ROLL_3D), p -> TrickVariantScripts.flip3d(p, FAST, 0));
        this.variants("Power Loop 3D", List.of(POWER_LOOP_3D), TrickVariantScripts::powerLoop3d);
        this.add("Flip im 3D-Modus ohne Umkehrschub bleibt Flip", List.of(FLIP), p -> p.mode3d().level(1.0).rotate(0, FAST, 0, 0.3, 360));
        this.add("Rolle im 3D-Modus ohne Umkehrschub bleibt Rolle", List.of(ROLL), p -> p.mode3d().level(1.0).rotate(FAST, 0, 0, 0.3, 360));
        this.add("Flip mit wenig Gas ohne 3D bleibt Flip", List.of(FLIP),
                p -> p.level(1.0).rotate(0, FAST, 0, 0.3, 90).rotate(0, FAST, 0, 0.0, 180).rotate(0, FAST, 0, 0.3, 90));
        this.check();
    }

    @Test
    void scaledVariantsGrowWithTheirMagnitude() {
        assertTrue(TORNADO.points(2.0, Double.POSITIVE_INFINITY) > TORNADO.points(1.0, Double.POSITIVE_INFINITY));
        assertTrue(COIN_DROP.points(3.0, Double.POSITIVE_INFINITY) > COIN_DROP.points(1.0, Double.POSITIVE_INFINITY));
        assertEquals(COIN_DROP.points(3.0, Double.POSITIVE_INFINITY), COIN_DROP.points(30.0, Double.POSITIVE_INFINITY));
        assertTrue(BACKWARDS_DIVE.points(30.0, Double.POSITIVE_INFINITY) > BACKWARDS_DIVE.points(12.0, Double.POSITIVE_INFINITY));
        assertTrue(WALL_RIDE.points(2.0, 0.5) > WALL_RIDE.points(0.5, 0.5));
        assertTrue(INVERTED_HOVER.points(3.0, Double.POSITIVE_INFINITY) > INVERTED_HOVER.points(1.0, Double.POSITIVE_INFINITY));
        assertTrue(FLIP_3D.points(2.0, Double.POSITIVE_INFINITY) > FLIP_3D.points(1.0, Double.POSITIVE_INFINITY));
        assertTrue(POWER_FLIP.basePoints() > FLIP.basePoints() + ANTI_MATTY.basePoints());
        assertTrue(POWER_ROLL.basePoints() > ROLL.basePoints() + ANTI_MATTY.basePoints());
        assertTrue(BARANI.basePoints() > POWER_LOOP.basePoints());
        assertTrue(INVERTED_360_POWER_LOOP.basePoints() > MCTWIST.basePoints());
    }

    @Test
    void tornadoCountsEveryRoll() {
        SimPilot one = run(new Case("", DroneBuild.FREESTYLE, List.of(), p -> TrickVariantScripts.tornado(p, 380)));
        SimPilot two = run(new Case("", DroneBuild.FREESTYLE, List.of(), p -> TrickVariantScripts.tornado(p, 720)));
        assertEquals(1.0, one.detected.getFirst().magnitude(), one.report());
        assertEquals(2.0, two.detected.getFirst().magnitude(), two.report());
    }

    @Test
    void yawSpinWhileHoveringStaysAYawSpin() {
        SimPilot p = run(new Case("", DroneBuild.FREESTYLE, List.of(), q -> q.level(1.0).rotate(0, 0, 500, q.hover(), 370)));
        assertEquals(List.of(YAW_SPIN), p.detected.stream().map(Trick::type).toList(), p.report());
    }
}
