package de.lardtom.propwash.trick;

final class TrickVariantScripts {
    static final double FAST = 650.0;

    private TrickVariantScripts() {
    }

    static SimPilot cruise(SimPilot p) {
        return p.fly(35.0, 0.0, 0.0, 2.0);
    }

    static SimPilot matty(SimPilot p) {
        return cruise(p).rotate(0, FAST, 0, 0.1, 190).rotate(0, 200, 0, 1.0, 100).rotate(0, FAST, 0, 0.2, 80);
    }

    static SimPilot antiMatty(SimPilot p) {
        return cruise(p).hold(0, -650, 0, 0.1, 0.22).hold(0, 0, 0, 0.8, 0.5).hold(0, -650, 0, 0.1, 0.33);
    }

    static SimPilot halfMatty(SimPilot p) {
        return cruise(p).rotate(FAST, 0, 0, 0.2, 180).rotate(0, 250, 0, 1.0, 180);
    }

    static SimPilot powerMatty(SimPilot p) {
        return cruise(p).rotate(0, FAST, 0, 0.1, 540).rotate(0, 200, 0, 1.0, 100).rotate(0, FAST, 0, 0.2, 60);
    }

    static SimPilot split540(SimPilot p) {
        return cruise(p).rotate(FAST, 0, 0, 0.2, 540).rotate(0, -300, 0, 0.6, 180);
    }

    static SimPilot splitback(SimPilot p) {
        return cruise(p).rotate(FAST, 0, 0, 0.2, 180).rotate(0, -FAST, 0, 0.3, 360).rotate(0, -300, 0, 0.6, 180);
    }

    static SimPilot flipRewind(SimPilot p, double roll, double pitch) {
        return p.level(1.0).rotate(roll, pitch, 0, 0.3, 360).rotate(-roll, -pitch, 0, 0.3, 360);
    }

    static SimPilot segmented(SimPilot p, double roll, double pitch, double pause) {
        return p.level(1.0).rotate(roll, pitch, 0, 0.3, 180).hold(0, 0, 0, 0.1, pause).rotate(roll, pitch, 0, 0.3, 180);
    }

    static SimPilot powerFlip(SimPilot p, double sign) {
        return cruise(p).hold(0, -220, 0, 0.85, 0.8).rotate(0, sign * FAST, 0, 0.3, 360).hold(0, -220, 0, 0.85, 0.85);
    }

    static SimPilot powerRoll(SimPilot p) {
        return cruise(p).hold(0, -220, 0, 0.85, 0.8).rotate(FAST, 0, 0, 0.3, 360).hold(0, -220, 0, 0.85, 0.85);
    }

    static SimPilot inverted360Loop(SimPilot p) {
        return cruise(p).hold(0, -220, 0, 0.85, 0.8).rotate(0, 0, 700, 0.1, 360).hold(0, -220, 0, 0.85, 0.85);
    }

    static SimPilot sideLoop(SimPilot p, double sign) {
        return p.fly(0, sign * 35, 0, 2.0).hold(-sign * 220, 0, 0, 0.85, 1.65);
    }

    static SimPilot barani(SimPilot p) {
        return cruise(p).hold(0, -300, 0, 0.85, 0.6).rotate(0, 0, 650, 0.1, 180).rotate(0, 250, 0, 1.0, 180);
    }

    static SimPilot maverick(SimPilot p, double radius, double speed) {
        return p.fly(15, 0, 0, 2.0).maverickLoop(radius, speed, 1.0);
    }

    static SimPilot immelmann(SimPilot p) {
        return cruise(p).rotate(0, -350, 0, 0.75, 180).rotate(FAST, 0, 0, 0.3, 180);
    }

    static SimPilot immelmatt(SimPilot p) {
        return immelmann(p).hold(0, 0, 0, 0.4, 0.2).rotate(0, FAST, 0, 0.1, 190).rotate(0, 200, 0, 1.0, 100).rotate(0, FAST, 0, 0.2, 80);
    }

    static SimPilot vanny(SimPilot p, double sign) {
        return cruise(p).rotate(0, 0, sign * 600, 0.4, 180).rotate(FAST, 0, 0, 0.35, 360).rotate(0, 0, sign * 600, 0.4, 180);
    }

    static SimPilot coinDrop(SimPilot p, double degrees) {
        return p.flyThrottle(0, 0, 0, 0.9, 0.8).flyThrottle(0, 0, 0, 0.0, 2.5).rotate(0, 0, 500, 0.0, degrees);
    }

    static SimPilot tornado(SimPilot p, double degrees) {
        return TrickProTricksTest.apex(p).rotate(0, FAST, 0, 0.05, 80).hold(0, 0, 0, 0.05, 0.6).rotate(FAST, 0, 0, 0.05, degrees)
                .hold(0, 0, 0, 0.05, 0.3).rotate(0, -FAST, 0, 0.8, 80).flyThrottle(0, 0, 0, 1.0, 0.6);
    }

    static SimPilot backwardsDive(SimPilot p, double tilt, double seconds) {
        return TrickProTricksTest.apex(p).rotate(0, -FAST, 0, 0.05, tilt).hold(0, 0, 0, 0.05, seconds).rotate(0, FAST, 0, 1.0, tilt)
                .flyThrottle(0, 0, 0, 1.0, 0.8);
    }

    static SimPilot hammerhead(SimPilot p, double sign) {
        return p.fly(40, 0, 0, 2.0).rotate(0, -300, 0, 1.0, 100).flyThrottle(-60, 0, 0, 1.0, 0.3).hold(0, 0, 0, 0.0, 1.6)
                .rotate(0, 0, sign * 600, 0.0, 180).hold(0, 0, 0, 0.0, 0.8).rotate(0, -300, 0, 0.9, 70).flyThrottle(0, 0, 0, 0.8, 0.8);
    }

    static SimPilot reverseKnife(SimPilot p, double sign, double roll) {
        return p.fly(40, 0, 0, 2.5).level(0.25).rotate(0, 0, 700, 0.3, 180).rotate(sign * FAST, 0, 0, 0.0, roll).hold(0, 0, 0, 0.0, 0.8)
                .rotate(-sign * FAST, 0, 0, 0.4, roll);
    }

    static SimPilot wallRide(SimPilot p, double gap, double roll) {
        p.fly(40, 0, 0, 2.5).level(0.25);
        double x = p.position.x;
        double y = p.position.y;
        double z = p.position.z;
        p.box(x + gap, y - 10, z + 1, x + 4, y + 10, z + 40);
        return p.rotate(FAST, 0, 0, 0.0, roll).hold(0, 0, 0, 0.0, 0.8).rotate(-FAST, 0, 0, 0.4, roll);
    }

    static SimPilot invertedHover(SimPilot p, double seconds) {
        return p.mode3d().level(1.0).rotate(0, FAST, 0, -0.2, 180).invertedHover(seconds).rotate(FAST, 0, 0, 0.0, 180);
    }

    static SimPilot flip3d(SimPilot p, double roll, double pitch) {
        return p.mode3d().level(1.0).rotate(roll, pitch, 0, 0.3, 90).rotate(roll, pitch, 0, -0.4, 180).rotate(roll, pitch, 0, 0.3, 90);
    }

    static SimPilot powerLoop3d(SimPilot p) {
        return p.mode3d().fly(35, 0, 0, 2.0).hold(0, 220, 0, -0.85, 1.65);
    }
}
