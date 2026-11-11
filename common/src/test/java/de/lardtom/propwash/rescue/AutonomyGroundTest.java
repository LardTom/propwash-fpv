package de.lardtom.propwash.rescue;

import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.AnchorSpec;
import de.lardtom.propwash.api.part.BatteryDefinition;
import de.lardtom.propwash.api.part.BatteryFitRule;
import de.lardtom.propwash.api.part.FrameDefinition;
import de.lardtom.propwash.api.part.FrameRole;
import de.lardtom.propwash.api.part.FrameShape;
import de.lardtom.propwash.api.part.MotorDefinition;
import de.lardtom.propwash.api.part.MotorThermal;
import de.lardtom.propwash.api.part.Mounts;
import de.lardtom.propwash.api.part.PartBasics;
import de.lardtom.propwash.api.part.PartDefinition;
import de.lardtom.propwash.api.part.PropDefinition;
import de.lardtom.propwash.api.part.PropMaterial;
import de.lardtom.propwash.api.part.StackDefinition;
import de.lardtom.propwash.api.part.Vec3Mm;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.sim.AirframeSpec;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutonomyGroundTest {
    private static final double EPS = 1.0E-6;
    private static final double HOLE_DEPTH = 1.0;
    private static final double HOLE_MARGIN = 0.005;
    private static final double TALL = 0.08;
    private static final double[] HOVER = {0.03, 0.05, 0.1, 0.25};
    private static final AtomicInteger SERIAL = new AtomicInteger();

    private record Case(String name, DroneBuild build) {
    }

    private record Hole(double minX, double maxX, double minZ, double maxZ) {
        static final Hole NONE = new Hole(0.0, 0.0, 0.0, 0.0);

        boolean contains(double x, double z) {
            return x > this.minX && x < this.maxX && z > this.minZ && z < this.maxZ;
        }

        double top(double x, double z) {
            return this.contains(x, z) ? -HOLE_DEPTH : 0.0;
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("propwash_groundtest", path + "_" + SERIAL.incrementAndGet());
    }

    @SuppressWarnings("unchecked")
    private static <T extends PartInfo> T part(PartDefinition definition) {
        return (T) PartIndex.detached(definition);
    }

    static AccessoryType cineGimbal() {
        return part(AccessoryDefinition.builder(id("gimbal_cine"))
                .basics(PartBasics.of(420.0)).slot(AccessorySlot.FRONT).size(110.0, 125.0, 110.0).flightBatteryWatts(7.0).frontalArea(0.01)
                .build());
    }

    static AccessoryType spear() {
        return part(AccessoryDefinition.builder(id("spear"))
                .basics(PartBasics.of(60.0)).slot(AccessorySlot.BOTTOM).size(8.0, 120.0, 8.0).frontalArea(0.0006)
                .build());
    }

    static FrameType cineFrame() {
        return part(FrameDefinition.builder(id("cine_frame"))
                .mass(1900.0).role(FrameRole.CINELIFTER).shape(FrameShape.TRUE_X).wheelbase(695).props(330, 385)
                .motorMount(Mounts.MOTOR_M28).stackMount(Mounts.STACK_S30)
                .accessoryMount(AccessorySlot.TOP, AccessorySlot.FRONT)
                .battery(true, 30.0).electronics(24.0).camera(new Vec3Mm(0.0, 10.0, -190.0)).cameraTilt(0.0)
                .anchor(AccessorySlot.TOP, new AnchorSpec(0.0, 80.0, -60.0, 0.0, 0.0))
                .anchor(AccessorySlot.FRONT, new AnchorSpec(0.0, -40.0, -150.0, 0.0, 0.0))
                .aero(0.03, 0.7).impactTolerance(5.0)
                .batteryFit(new BatteryFitRule(6, 12, 1400.0, 6, 6000))
                .build());
    }

    static FrameType foldFrame(String name, double mass, int wheelbase, int minProp, int maxProp, double plate, double electronics,
                               Vec3Mm camera, AnchorSpec top, AnchorSpec front, double area, double impact, BatteryFitRule fit) {
        return part(FrameDefinition.builder(id(name))
                .mass(mass).role(FrameRole.LONG_RANGE).shape(FrameShape.TRUE_X).wheelbase(wheelbase).props(minProp, maxProp)
                .motorMount(Mounts.MOTOR_M14, Mounts.MOTOR_M22).stackMount(Mounts.STACK_S30, Mounts.STACK_S20)
                .accessoryMount(AccessorySlot.TOP, AccessorySlot.FRONT)
                .battery(true, plate).electronics(electronics).camera(camera).cameraTilt(0.0)
                .anchor(AccessorySlot.TOP, top).anchor(AccessorySlot.FRONT, front)
                .aero(area, 0.6).impactTolerance(impact).batteryFit(fit)
                .build());
    }

    static DroneBuild kino() {
        DroneBuild cine = new DroneBuild(cineFrame(),
                part(StackDefinition.builder(id("stack_3_8")).mass(20.0).mount(Mounts.STACK_S30).escContinuous(60.0).cells(3, 8).build()),
                part(MotorDefinition.builder(id("m4214_400")).mass(128.0).stator("4214").kv(400).mount(Mounts.MOTOR_M28).maxCurrent(60.0)
                        .resistance(0.045).cells(6, 12).thermal(MotorThermal.limits(140.0, 160.0)).build()),
                part(PropDefinition.builder(id("p13_7_2b")).mass(28.0).size(330, 7.0, 2).coefficients(0.095, 0.040)
                        .material(PropMaterial.CARBON_FIBER_NYLON).build()),
                VideoType.DIGITAL_HD,
                part(BatteryDefinition.builder(id("b6s_6000")).mass(820.0).cells(6).capacity(6000).cellResistance(0.004).cRating(25)
                        .chemistry(BatteryDefinition.LIPO).build()));
        return cine.withAccessory(cineGimbal());
    }

    static DroneBuild onFrame(FrameType frame, AccessoryType accessory) {
        DroneBuild parts = DroneBuild.LONG_RANGE;
        return new DroneBuild(frame, parts.stack(), parts.motor(), parts.prop(), parts.video(), parts.battery()).withAccessory(accessory);
    }

    static List<Case> cases() {
        List<Case> out = new ArrayList<>();
        for (DroneBuild preset : DroneBuild.PRESETS) {
            String name = preset.frame().id().getPath();
            out.add(new Case(name, preset.withoutAccessories()));
            for (AccessoryType tall : List.of(spear(), cineGimbal())) {
                if (preset.frame().accessoryMounts.contains(tall.mount)) {
                    out.add(new Case(name + " mit " + tall.id().getPath(), preset.withoutAccessories().withAccessory(tall)));
                }
            }
        }
        out.add(new Case("Kino mit 360°-Gimbal", kino()));
        out.add(new Case("Compact mit 360°-Gimbal", onFrame(foldFrame("compact_fold_frame", 52.0, 250, 140, 155, 6.0, 10.0,
                new Vec3Mm(0.0, -6.0, -78.0), new AnchorSpec(0.0, 24.0, -20.0, 0.0, 0.0), new AnchorSpec(0.0, -12.0, -70.0, 0.0, 0.0),
                0.006, 5.0, new BatteryFitRule(2, 3, 160.0, 2, 2400)), cineGimbal())));
        out.add(new Case("Tour mit 360°-Gimbal", onFrame(foldFrame("tour_fold_frame", 215.0, 380, 200, 235, 10.0, 14.0,
                new Vec3Mm(0.0, -10.0, -118.0), new AnchorSpec(0.0, 40.0, -30.0, 0.0, 0.0), new AnchorSpec(0.0, -18.0, -106.0, 0.0, 0.0),
                0.011, 5.5, new BatteryFitRule(3, 6, 420.0, 4, 3000)), cineGimbal())));
        return out;
    }

    private static double rest(AirframeSpec spec, Quaternionfc orientation) {
        return spec.accessoryDepth(orientation);
    }

    private static double gap(AirframeSpec spec, Quaternionfc orientation, double foot, Hole hole) {
        double hitbox = Math.max(0.0, Math.min(GroundContact.PROBE, foot));
        return GroundContact.gap(spec, orientation, 1.0, hitbox, GroundContact.PROBE,
                (dx, dz, reach) -> Math.max(0.0, Math.min(reach, foot - hole.top(dx, dz))));
    }

    private static Vector3f point(AirframeSpec spec, Quaternionfc orientation, int k) {
        return orientation.transform(new Vector3f((float) -spec.accessoryX(k), (float) spec.accessoryY(k), (float) -spec.accessoryZ(k)));
    }

    private static Hole underAccessory(AirframeSpec spec, Quaternionfc orientation) {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (int k = 0; k < spec.accessoryPointCount(); k++) {
            Vector3f p = point(spec, orientation, k);
            minX = Math.min(minX, p.x);
            maxX = Math.max(maxX, p.x);
            minZ = Math.min(minZ, p.z);
            maxZ = Math.max(maxZ, p.z);
        }
        return new Hole(minX - HOLE_MARGIN, maxX + HOLE_MARGIN, minZ - HOLE_MARGIN, maxZ + HOLE_MARGIN);
    }

    @Test
    void droneRestingOnTallAccessoryOrGimbalIsGrounded() {
        List<String> misses = new ArrayList<>();
        int tall = 0;
        for (Quaternionfc orientation : List.of(new Quaternionf(), new Quaternionf().rotateY((float) Math.toRadians(90.0)))) {
            for (Case c : cases()) {
                AirframeSpec spec = AirframeSpec.of(c.build());
                double foot = rest(spec, orientation);
                double gap = gap(spec, orientation, foot, Hole.NONE);
                if (foot >= TALL) {
                    tall++;
                }
                if (!(gap < GroundContact.TOUCH) || !GroundContact.grounded(gap, 0.0)) {
                    misses.add(String.format(Locale.ROOT, "%s: Hitbox %.3f m über dem Boden, Abstand der Kollisionsform %.3f m", c.name(), foot, gap));
                }
            }
        }
        assertTrue(tall >= 8, "zu wenige Bauten auf hohem Zubehör: " + tall);
        assertTrue(misses.isEmpty(), "liegt auf, gilt aber nicht als am Boden:\n" + String.join("\n", misses));
    }

    @Test
    void kinoStandsOnItsGimbalHighAboveTheHitbox() {
        AirframeSpec spec = AirframeSpec.of(kino());
        double foot = rest(spec, new Quaternionf());
        assertTrue(foot > 0.15 && foot < 0.18, "Kino steht " + foot + " m hoch auf dem Gimbal");
        assertEquals(0.0, gap(spec, new Quaternionf(), foot, Hole.NONE), EPS);
        assertTrue(GroundContact.grounded(gap(spec, new Quaternionf(), foot, Hole.NONE), 0.0));
    }

    @Test
    void droneRestingOnItsStandLegsIsGrounded() {
        List<String> misses = new ArrayList<>();
        int checked = 0;
        Quaternionf level = new Quaternionf();
        for (Case c : cases()) {
            AirframeSpec spec = AirframeSpec.of(c.build());
            if (spec.standPointCount() <= spec.accessoryPointCount()) {
                continue;
            }
            Hole hole = underAccessory(spec, level);
            boolean legsOutside = true;
            for (int k = spec.accessoryPointCount(); k < spec.standPointCount(); k++) {
                Vector3f leg = point(spec, level, k);
                legsOutside &= !hole.contains(leg.x, leg.z);
            }
            if (!legsOutside) {
                continue;
            }
            checked++;
            double foot = rest(spec, level);
            double gap = gap(spec, level, foot, hole);
            if (!GroundContact.grounded(gap, 0.0)) {
                misses.add(String.format(Locale.ROOT, "%s: Zubehör über einem Loch, Standbeine auf dem Boden, Hitbox %.3f m, Abstand %.3f m",
                        c.name(), foot, gap));
            }
            double above = gap(spec, level, foot + 0.05, hole);
            assertEquals(0.05, above, EPS, c.name() + " 5 cm über dem Boden auf den Standbeinen");
        }
        assertTrue(checked >= 5, "zu wenige Bauten mit Standbeinen geprüft: " + checked);
        assertTrue(misses.isEmpty(), "steht auf den Standbeinen, gilt aber nicht als am Boden:\n" + String.join("\n", misses));
    }

    @Test
    void droneHoveringJustAboveTheGroundIsNotGrounded() {
        List<String> misses = new ArrayList<>();
        Quaternionf level = new Quaternionf();
        for (Case c : cases()) {
            AirframeSpec spec = AirframeSpec.of(c.build());
            double rest = rest(spec, level);
            for (double hover : HOVER) {
                double gap = gap(spec, level, rest + hover, Hole.NONE);
                if (Math.abs(gap - hover) > EPS || GroundContact.grounded(gap, 0.0)) {
                    misses.add(String.format(Locale.ROOT, "%s %.2f m über dem Boden: Abstand %.3f m, am Boden %s", c.name(), hover, gap,
                            GroundContact.grounded(gap, 0.0)));
                }
            }
        }
        assertTrue(misses.isEmpty(), "schwebt knapp über dem Boden, gilt aber als am Boden:\n" + String.join("\n", misses));
    }

    @Test
    void droneThatTouchesButMovesIsNotResting() {
        assertTrue(GroundContact.grounded(0.0, 0.0));
        assertTrue(GroundContact.grounded(0.005, 0.1));
        assertFalse(GroundContact.grounded(0.0, GroundContact.REST_SPEED));
        assertFalse(GroundContact.grounded(0.0, 1.0));
        assertFalse(GroundContact.grounded(0.0, Double.NaN));
        assertFalse(GroundContact.grounded(Double.NaN, 0.0));
        assertFalse(GroundContact.grounded(GroundContact.TOUCH, 0.0));
    }
}
