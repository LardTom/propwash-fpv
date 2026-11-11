package de.lardtom.propwash.sim;

import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.AnchorSpec;
import de.lardtom.propwash.api.part.BatteryFitRule;
import de.lardtom.propwash.api.part.FrameDefinition;
import de.lardtom.propwash.api.part.FrameRole;
import de.lardtom.propwash.api.part.Mounts;
import de.lardtom.propwash.api.part.PartBasics;
import de.lardtom.propwash.api.part.Vec3Mm;
import de.lardtom.propwash.build.AccessoryAnchor;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.VideoType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessoryGroundTest {
    private static final double EPS = 1.0E-9;
    private static final double TOUCH = 0.002;
    private static final double LEVEL_DEG = 1.0;
    private static final double DROP = 0.3;
    private static final double FPS = 120.0;

    static AccessoryType dispenser() {
        return AddonParts.part(AccessoryDefinition.builder(AddonParts.id("dispenser_mount"))
                .basics(PartBasics.of(150.0)).slot(AccessorySlot.FRONT).size(62.0, 58.0, 64.0).flightBatteryWatts(1.2).frontalArea(0.0036)
                .build());
    }

    static AccessoryType spear() {
        return AddonParts.part(AccessoryDefinition.builder(AddonParts.id("spear_mount"))
                .basics(PartBasics.of(40.0)).slot(AccessorySlot.BOTTOM).size(24.0, 18.0, 90.0).frontalArea(0.0004)
                .build());
    }

    static AccessoryType cineGimbal() {
        return AddonParts.part(AccessoryDefinition.builder(AddonParts.id("gimbal_cine"))
                .basics(PartBasics.of(420.0)).slot(AccessorySlot.FRONT).size(110.0, 125.0, 110.0).flightBatteryWatts(7.0).frontalArea(0.01)
                .build());
    }

    static FrameType cineFrame() {
        return AddonParts.part(FrameDefinition.builder(AddonParts.id("cine_frame"))
                .mass(1900.0).role(FrameRole.CINELIFTER).wheelbase(695).props(330, 385)
                .motorMount(Mounts.MOTOR_M28).stackMount(Mounts.STACK_S30)
                .accessoryMount(AccessorySlot.TOP, AccessorySlot.FRONT)
                .battery(true, 30.0).electronics(24.0).camera(new Vec3Mm(0.0, 10.0, -190.0)).cameraTilt(0.0)
                .anchor(AccessorySlot.TOP, new AnchorSpec(0.0, 80.0, -60.0, 0.0, 0.0))
                .anchor(AccessorySlot.FRONT, new AnchorSpec(0.0, -40.0, -150.0, 0.0, 0.0))
                .aero(0.03, 0.7).impactTolerance(5.0)
                .batteryFit(new BatteryFitRule(6, 12, 1400.0, 6, 6000))
                .build());
    }

    static DroneBuild freestyleWithDispenser() {
        return DroneBuild.FREESTYLE.withAccessory(dispenser()).withAccessory(spear());
    }

    static DroneBuild cineWithGimbal() {
        DroneBuild cine = new DroneBuild(cineFrame(), AddonParts.stack3to8(), AddonParts.motor4214(), AddonParts.prop13x7(), VideoType.DIGITAL_HD,
                AddonParts.lipo(6, 6000, 820.0, 0.004, 25));
        return cine.withAccessory(cineGimbal());
    }

    static List<DroneBuild> mobDefenseBuilds() {
        List<DroneBuild> out = new ArrayList<>();
        for (DroneBuild preset : DroneBuild.PRESETS) {
            for (List<AccessoryType> set : List.of(List.of(dispenser()), List.of(spear()), List.of(dispenser(), spear()))) {
                DroneBuild build = preset.withoutAccessories();
                boolean fits = true;
                for (AccessoryType type : set) {
                    fits &= preset.frame().accessoryMounts.contains(type.mount);
                    build = build.withAccessory(type);
                }
                if (fits) {
                    out.add(build);
                }
            }
        }
        out.add(cineWithGimbal());
        return out;
    }

    static String label(DroneBuild build) {
        StringBuilder out = new StringBuilder(build.frame().key());
        for (AccessoryMount mount : build.accessories().mounts()) {
            out.append('+').append(build.accessories().at(mount).orElseThrow().key());
        }
        return out.toString();
    }

    static double tiltDeg(RigidBody b) {
        return Math.toDegrees(Math.acos(Math.clamp(b.r11, -1.0, 1.0)));
    }

    static void tilt(FlatWorld w, double pitchDeg, double rollDeg) {
        double hp = Math.toRadians(pitchDeg) * 0.5;
        double hr = Math.toRadians(rollDeg) * 0.5;
        RigidBody b = w.sim.body;
        b.qw = Math.cos(hp) * Math.cos(hr);
        b.qx = Math.sin(hp) * Math.cos(hr);
        b.qy = -Math.sin(hp) * Math.sin(hr);
        b.qz = Math.cos(hp) * Math.sin(hr);
        b.updateDerived();
    }

    static double lowestCornerInWorld(DroneBuild build, FlatWorld w) {
        RigidBody b = w.sim.body;
        double lowest = Double.POSITIVE_INFINITY;
        for (double[] c : accessoryCorners(build, w.sim.params())) {
            lowest = Math.min(lowest, w.y + b.r10 * c[0] + b.r11 * c[1] + b.r12 * c[2]);
        }
        return lowest;
    }

    static List<double[]> accessoryCorners(DroneBuild build, AirframeSpec p) {
        List<double[]> out = new ArrayList<>();
        for (AccessoryMount mount : build.accessories().mounts()) {
            if (mount == AccessoryMount.TOP) {
                continue;
            }
            AccessoryType type = build.accessories().at(mount).orElseThrow();
            for (double[] c : AccessoryAnchor.of(build.frame(), mount).corners(type)) {
                out.add(new double[]{-c[0] / 1000.0 - p.comX, c[1] / 1000.0 - p.comHeight, -c[2] / 1000.0 - p.comZ});
            }
        }
        return out;
    }

    static double lowestCorner(DroneBuild build, AirframeSpec p) {
        double low = Double.POSITIVE_INFINITY;
        for (double[] c : accessoryCorners(build, p)) {
            low = Math.min(low, c[1]);
        }
        return low;
    }

    static double lowestGround(AirframeSpec p) {
        double low = Double.POSITIVE_INFINITY;
        for (int k = 0; k < p.groundCount(); k++) {
            if (p.groundUsed(k)) {
                low = Math.min(low, p.groundY(k));
            }
        }
        return low;
    }

    static double lowestHull(AirframeSpec p) {
        double low = Double.POSITIVE_INFINITY;
        for (int k = 0; k < AirframeSpec.HULL_POINTS; k++) {
            low = Math.min(low, p.hullY(k));
        }
        return low;
    }

    @Test
    void lowAccessoryCornersAreGroundContacts() {
        for (DroneBuild build : List.of(freestyleWithDispenser(), cineWithGimbal())) {
            AirframeSpec p = AirframeSpec.of(build);
            double corner = lowestCorner(build, p);
            assertTrue(corner < lowestHull(p) - 0.03, build + ": Zubehör ragt nicht unter den Rahmen (" + corner + ")");
            assertEquals(corner, lowestGround(p), EPS, build.frame().key() + ": tiefster Bodenpunkt ist nicht die Zubehör-Ecke");
        }
    }

    @Test
    void dronesStandOnTheirAccessoriesAfterLanding() {
        for (DroneBuild build : List.of(freestyleWithDispenser(), cineWithGimbal())) {
            FlatWorld w = FlatWorld.of(build);
            w.place(0.0, 0.45, 0.0, 0.0);
            w.run(5.0, 120.0);
            AirframeSpec p = w.sim.params();
            RigidBody b = w.sim.body;
            double lowest = Double.POSITIVE_INFINITY;
            for (double[] c : accessoryCorners(build, p)) {
                lowest = Math.min(lowest, w.y + b.r10 * c[0] + b.r11 * c[1] + b.r12 * c[2]);
            }
            assertTrue(lowest >= w.groundY - TOUCH, build.frame().key() + ": Zubehör steckt " + (w.groundY - lowest) * 1000.0 + " mm im Boden");
            assertTrue(lowest <= w.groundY + TOUCH, build.frame().key() + ": Zubehör schwebt " + (lowest - w.groundY) * 1000.0 + " mm über dem Boden");
            assertTrue(Math.abs(w.sim.telemetry().speed) < 0.05, build.frame().key() + ": Drohne kommt nicht zur Ruhe");
        }
    }

    @Test
    void hitboxStaysFrameSized() {
        for (DroneBuild build : List.of(freestyleWithDispenser(), cineWithGimbal())) {
            AirframeSpec p = AirframeSpec.of(build);
            double high = Double.NEGATIVE_INFINITY;
            for (int k = 0; k < AirframeSpec.HULL_POINTS; k++) {
                high = Math.max(high, p.hullY(k));
            }
            assertEquals(lowestHull(p), p.hitboxFootOffset, EPS, build.frame().key() + ": Fuß der Hitbox");
            assertEquals(high - lowestHull(p), p.hitboxHeight, EPS, build.frame().key() + ": Höhe der Hitbox");
            assertTrue(p.hitboxFootOffset > lowestCorner(build, p), build.frame().key());
        }
    }

    @Test
    void flatLedStripsLeaveTheGroundPointsAlone() {
        for (DroneBuild preset : DroneBuild.PRESETS) {
            DroneBuild plain = preset.accessories().isEmpty() ? preset : preset.withoutAccessories();
            DroneBuild lit = plain.withAccessory(AccessoryType.LED_STRIP);
            assertEquals(AirframeSpec.of(plain).groundCount(), AirframeSpec.of(lit).groundCount(), preset.frame().key());
        }
    }

    @Test
    void buildsWithHangingAccessoriesRestLevelOnThemAndArm() {
        for (DroneBuild build : mobDefenseBuilds()) {
            FlatWorld w = FlatWorld.of(build);
            w.place(0.0, w.groundY - w.sim.params().hitboxFootOffset + DROP, 0.0, 0.0);
            w.run(4.0, FPS);
            String what = label(build);
            double lowest = lowestCornerInWorld(build, w);
            assertTrue(tiltDeg(w.sim.body) <= LEVEL_DEG, what + ": steht " + tiltDeg(w.sim.body) + "° schräg");
            assertTrue(w.sim.body.r11 > 0.0, what + ": liegt auf dem Rücken");
            assertTrue(Math.abs(lowest - w.groundY) <= TOUCH, what + ": tiefste Zubehör-Ecke " + (lowest - w.groundY) * 1000.0 + " mm über dem Boden");
            w.arm();
            assertTrue(w.sim.telemetry().armed, what + ": nicht scharf (Sperren " + w.sim.telemetry().armBlockers + ")");
        }
    }

    static int flips(DroneBuild build) {
        int flips = 0;
        for (double speed : new double[]{0.0, 1.5}) {
            for (double height : new double[]{DROP, 1.0}) {
                for (double degrees : new double[]{-12.0, -6.0, 0.0, 6.0, 12.0}) {
                    for (boolean roll : new boolean[]{false, true}) {
                        FlatWorld w = FlatWorld.of(build);
                        w.place(0.0, w.groundY - w.sim.params().hitboxFootOffset + height, 0.0, 0.0);
                        tilt(w, roll ? 0.0 : degrees, roll ? degrees : 0.0);
                        w.sim.body.vz = speed;
                        w.run(4.0, FPS);
                        if (w.sim.body.r11 < 0.5) {
                            flips++;
                        }
                    }
                }
            }
        }
        return flips;
    }

    @Test
    void hangingAccessoriesDoNotFlipDropsMoreOftenThanWithout() {
        Map<FrameType, Integer> bare = new HashMap<>();
        for (DroneBuild build : mobDefenseBuilds()) {
            int without = bare.computeIfAbsent(build.frame(), frame -> flips(build.withoutAccessories()));
            int with = flips(build);
            assertTrue(with <= without, label(build) + ": " + with + " von 40 Fällen aus 0,3 und 1 m (bis 12° schräg, bis 1,5 m/s) liegen auf dem Rücken, "
                    + "ohne Zubehör " + without);
        }
    }

    @Test
    void standingLevelOnTheAccessoryKeepsTheFrameFootprint() {
        for (DroneBuild build : List.of(freestyleWithDispenser(), cineWithGimbal(), DroneBuild.WHOOP.withoutAccessories().withAccessory(spear()))) {
            AirframeSpec p = AirframeSpec.of(build);
            double corner = lowestCorner(build, p);
            int level = 0;
            double front = Double.NEGATIVE_INFINITY;
            double back = Double.POSITIVE_INFINITY;
            double left = Double.POSITIVE_INFINITY;
            double right = Double.NEGATIVE_INFINITY;
            for (int k = 0; k < p.groundCount(); k++) {
                if (p.groundUsed(k) && Math.abs(p.groundY(k) - corner) < EPS) {
                    level++;
                    front = Math.max(front, p.groundZ(k));
                    back = Math.min(back, p.groundZ(k));
                    left = Math.min(left, p.groundX(k));
                    right = Math.max(right, p.groundX(k));
                }
            }
            String what = label(build);
            assertTrue(level >= 3, what + ": nur " + level + " Bodenpunkte in der Ebene des Zubehörs");
            assertTrue(front > 0.0 && back < 0.0 && left < 0.0 && right > 0.0, what + ": Schwerpunkt liegt nicht über der Standfläche");
        }
    }
}
