package de.lardtom.propwash.sim;

import de.lardtom.propwash.api.part.BatteryFitRule;
import de.lardtom.propwash.api.part.FrameDefinition;
import de.lardtom.propwash.api.part.FrameRole;
import de.lardtom.propwash.api.part.FrameShape;
import de.lardtom.propwash.api.part.MotorArm;
import de.lardtom.propwash.api.part.Mounts;
import de.lardtom.propwash.api.part.Vec3Mm;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.drone.CameraMount;
import de.lardtom.propwash.drone.client.EyeClearance;
import de.lardtom.propwash.video.FovMath;
import net.minecraft.resources.Identifier;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FpvEyeSurveyTest {
    static final double ASPECT = 16.0 / 9.0;
    static final double WIDEST_FOV = FovMath.MAX_HORIZONTAL_DEGREES;
    static final int[] YAWS = {0, 45, 90, 180};
    static final int[] PITCHES = {-90, -60, -30, 0, 30, 60, 90};
    static final int[] ROLLS = {0, 45, 90, 135, 180, 225, 270, 315};

    enum Scene {
        GROUND, SNOW_1, SNOW_4, CEILING, WALL, SLAB_SIDE, STAIRS, CARPET_ON_FENCE
    }

    record Drone(String name, DroneBuild build) {
    }

    record Count(int poses, int eyeInside, int nearOnly, double deepest, int guardFailures, double maxShift, double minNear, String firstFailure) {
    }

    static List<Drone> builtins() {
        List<Drone> out = new ArrayList<>();
        for (DroneBuild build : DroneBuild.PRESETS) {
            out.add(new Drone("propwash:" + build.frame().key(), build));
        }
        return out;
    }

    static List<Drone> exampleAddon() {
        StackType stack = StackType.byId(Identifier.fromNamespaceAndPath("propwash", "stack_30")).orElseThrow();
        MotorType m2207 = MotorType.byId(Identifier.fromNamespaceAndPath("propwash", "m2207_1750")).orElseThrow();
        MotorType m2306 = MotorType.byId(Identifier.fromNamespaceAndPath("propwash", "m2306_1860")).orElseThrow();
        PropType p51 = PropType.byId(Identifier.fromNamespaceAndPath("propwash", "p51_3b")).orElseThrow();
        BatteryType b6s = BatteryType.byId(Identifier.fromNamespaceAndPath("propwash", "b6s_1300")).orElseThrow();
        List<Drone> out = new ArrayList<>();
        out.add(new Drone("propwash_example:deadcat_5", new DroneBuild(AddonParts.deadcat(), stack, m2207, p51, VideoType.ANALOG_RACE, b6s)));
        FrameType stretched = AddonParts.part(FrameDefinition.builder(AddonParts.id("stretched_x"))
                .mass(92.0).role(FrameRole.RACE).shape(FrameShape.STRETCHED_X).wheelbase(227).maxProp(130)
                .motorMount(Mounts.MOTOR_M22).stackMount(Mounts.STACK_S30)
                .arms(List.of(MotorArm.of(-72.0, 0.0, -88.0, MotorArm.Spin.CW), MotorArm.of(72.0, 0.0, -88.0, MotorArm.Spin.CCW),
                        MotorArm.of(-72.0, 0.0, 88.0, MotorArm.Spin.CCW), MotorArm.of(72.0, 0.0, 88.0, MotorArm.Spin.CW)))
                .battery(true, 26.0).electronics(10.0).camera(new Vec3Mm(0.0, 18.5, -64.0)).cameraTilt(40.0)
                .aero(0.009, 0.72).impactTolerance(7.0)
                .batteryFit(new BatteryFitRule(4, 6, 230.0, 6, 0))
                .build());
        out.add(new Drone("propwash_example:stretched_x", new DroneBuild(stretched, stack, m2306, p51, VideoType.ANALOG_RACE, b6s)));
        out.add(new Drone("propwash_example:xclass_13", new DroneBuild(AddonParts.xClass13(), AddonParts.stack3to8(), AddonParts.motor4214(),
                AddonParts.prop13x7(), VideoType.DIGITAL_HD, AddonParts.lion8s4500())));
        return out;
    }

    static List<Drone> jmp() {
        List<Drone> out = new ArrayList<>();
        for (CalibrationHarness.Entry entry : CalibrationHarness.addons()) {
            out.add(new Drone(entry.name(), entry.build()));
        }
        return out;
    }

    static final class World implements EyeClearance.Solids {
        final List<double[]> boxes = new ArrayList<>();

        void box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.boxes.add(new double[]{minX, minY, minZ, maxX, maxY, maxZ});
        }

        void blocks(int x0, int x1, double y0, double y1, int z0, int z1) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    this.box(x, y0, z, x + 1, y1, z + 1);
                }
            }
        }

        @Override
        public void collect(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, EyeClearance sink) {
            for (double[] b : this.boxes) {
                if (b[3] > minX && b[0] < maxX && b[4] > minY && b[1] < maxY && b[5] > minZ && b[2] < maxZ) {
                    sink.addBox(b[0], b[1], b[2], b[3], b[4], b[5]);
                }
            }
        }

        boolean inside(double x, double y, double z) {
            for (double[] b : this.boxes) {
                if (x > b[0] && x < b[3] && y > b[1] && y < b[4] && z > b[2] && z < b[5]) {
                    return true;
                }
            }
            return false;
        }
    }

    record Placement(World world, double x, double y, double z) {
    }

    static Placement place(Scene scene, AirframeSpec p) {
        double half = 0.5 * p.hitboxWidth;
        double height = p.hitboxHeight;
        World w = new World();
        double cx = 0.5;
        double cz = 0.5;
        double y = 0.0;
        switch (scene) {
            case GROUND -> w.blocks(-4, 4, -1.0, 0.0, -4, 4);
            case SNOW_1 -> {
                w.blocks(-4, 4, -1.0, 0.0, -4, 4);
                w.blocks(-4, 4, 0.0, 0.125, -4, 4);
            }
            case SNOW_4 -> {
                w.blocks(-4, 4, -1.0, 0.0, -4, 4);
                w.blocks(-4, 4, 0.0, 0.5, -4, 4);
                y = 0.375;
            }
            case CEILING -> {
                w.blocks(-4, 4, 3.0, 4.0, -4, 4);
                y = 3.0 - height;
            }
            case WALL -> {
                w.blocks(-4, 4, -1.0, 0.0, -4, 4);
                w.blocks(-4, 4, 0.0, 3.0, -4, -1);
                cz = half;
            }
            case SLAB_SIDE -> {
                w.blocks(-4, 4, -1.0, 0.0, -4, 4);
                w.blocks(-4, 4, 0.0, 0.5, -4, -1);
                cz = half;
            }
            case STAIRS -> {
                w.blocks(-4, 4, -1.0, 0.0, -4, 4);
                w.blocks(-4, 4, 0.0, 0.5, -4, 0);
                w.blocks(-4, 4, 0.5, 1.0, -4, -1);
                for (int x = -4; x <= 4; x++) {
                    w.box(x, 0.5, 0.0, x + 1, 1.0, 0.5);
                }
                y = 0.5;
                cz = 0.5 + half;
            }
            case CARPET_ON_FENCE -> {
                w.blocks(-4, 4, -3.0, -2.0, -4, 4);
                w.blocks(-4, 4, 0.0, 0.0625, -4, 4);
                w.box(0.375, -2.0, 0.375, 0.625, 0.0, 0.625);
                y = 0.0625;
            }
        }
        return new Placement(w, cx, y, cz);
    }

    static Quaterniond pose(int yaw, int pitch, int roll) {
        return new Quaterniond().rotateY(Math.toRadians(yaw)).rotateX(Math.toRadians(pitch)).rotateZ(Math.toRadians(roll));
    }

    static double horizontalFov(DroneBuild build) {
        return FovMath.cameraHorizontalDegrees(build.video());
    }

    static final class Probe {
        final EyeClearance clearance = new EyeClearance();
        final Vector3d com = new Vector3d();
        final Vector3d eye = new Vector3d();
        final Vector3d f = new Vector3d();
        final Vector3d u = new Vector3d();
        final Vector3d l = new Vector3d();
        double tanX;
        double tanY;

        void setup(AirframeSpec p, Placement place, Quaterniond body, double uptilt, double hfov) {
            double com = -p.hitboxFootOffset;
            this.com.set(place.x(), place.y() + com, place.z());
            this.eye.set(p.lensRight, p.lensUp, -p.lensForward);
            body.transform(this.eye).add(this.com);
            Quaterniond camera = new Quaterniond(body).rotateX(Math.toRadians(uptilt));
            camera.transform(this.f.set(0.0, 0.0, -1.0));
            camera.transform(this.u.set(0.0, 1.0, 0.0));
            camera.transform(this.l.set(-1.0, 0.0, 0.0));
            double vfov = Math.toRadians(FovMath.verticalFromHorizontal(hfov, ASPECT));
            this.tanY = Math.tan(vfov / 2.0);
            this.tanX = this.tanY * ASPECT;
            this.clearance.view(this.f.x, this.f.y, this.f.z, this.u.x, this.u.y, this.u.z, this.l.x, this.l.y, this.l.z, this.tanX, this.tanY);
        }

        double shift;

        String guard(World world) {
            this.clearance.resolve(this.eye.x, this.eye.y, this.eye.z, this.com.x, this.com.y, this.com.z, EyeClearance.VANILLA_NEAR,
                    EyeClearance.MIN_NEAR, world);
            double x = this.clearance.x();
            double y = this.clearance.y();
            double z = this.clearance.z();
            this.shift = Math.sqrt((x - this.eye.x) * (x - this.eye.x) + (y - this.eye.y) * (y - this.eye.y) + (z - this.eye.z) * (z - this.eye.z));
            double near = this.clearance.near();
            if (world.inside(x, y, z)) {
                return "Auge im Block";
            }
            if (sightThroughSolid(world, this.com, x, y, z)) {
                return "Auge hinter einem Block";
            }
            if (this.cornersInside(world, x, y, z, near) || this.pyramidTouches(world, x, y, z, near)) {
                return "Nahebene im Block (near " + near + ")";
            }
            return null;
        }

        boolean cornersInside(World world, double x, double y, double z, double near) {
            for (int i = 0; i < 5; i++) {
                double sx = i == 4 ? 0.0 : (i == 0 || i == 3 ? 1.0 : -1.0) * this.tanX;
                double sy = i == 4 ? 0.0 : (i < 2 ? 1.0 : -1.0) * this.tanY;
                double cx = x + near * (this.f.x + sx * this.l.x + sy * this.u.x);
                double cy = y + near * (this.f.y + sx * this.l.y + sy * this.u.y);
                double cz = z + near * (this.f.z + sx * this.l.z + sy * this.u.z);
                if (world.inside(cx, cy, cz)) {
                    return true;
                }
            }
            return false;
        }

        boolean pyramidTouches(World world, double x, double y, double z, double near) {
            int steps = 6;
            for (int a = 0; a <= steps; a++) {
                for (int b = 0; b <= steps; b++) {
                    for (int t = 1; t <= steps; t++) {
                        double depth = near * t / steps;
                        double sx = (2.0 * a / steps - 1.0) * this.tanX;
                        double sy = (2.0 * b / steps - 1.0) * this.tanY;
                        if (world.inside(x + depth * (this.f.x + sx * this.l.x + sy * this.u.x),
                                y + depth * (this.f.y + sx * this.l.y + sy * this.u.y),
                                z + depth * (this.f.z + sx * this.l.z + sy * this.u.z))) {
                            return true;
                        }
                    }
                }
            }
            return world.inside(x, y, z);
        }
    }

    static boolean behind(World world, Vector3d from, Vector3d to) {
        int steps = 64;
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            if (world.inside(from.x + t * (to.x - from.x), from.y + t * (to.y - from.y), from.z + t * (to.z - from.z))) {
                return true;
            }
        }
        return false;
    }

    static boolean sightThroughSolid(World world, Vector3d from, double x, double y, double z) {
        int steps = 64;
        for (double[] b : world.boxes) {
            if (from.x >= b[0] && from.x <= b[3] && from.y >= b[1] && from.y <= b[4] && from.z >= b[2] && from.z <= b[5]) {
                continue;
            }
            for (int i = 1; i <= steps; i++) {
                double t = (double) i / steps;
                double px = from.x + t * (x - from.x);
                double py = from.y + t * (y - from.y);
                double pz = from.z + t * (z - from.z);
                if (px > b[0] && px < b[3] && py > b[1] && py < b[4] && pz > b[2] && pz < b[5]) {
                    return true;
                }
            }
        }
        return false;
    }

    static double depthInside(World world, double x, double y, double z) {
        double deepest = 0.0;
        for (double[] b : world.boxes) {
            if (x > b[0] && x < b[3] && y > b[1] && y < b[4] && z > b[2] && z < b[5]) {
                double d = Math.min(Math.min(Math.min(x - b[0], b[3] - x), Math.min(y - b[1], b[4] - y)), Math.min(z - b[2], b[5] - z));
                deepest = Math.max(deepest, d);
            }
        }
        return deepest;
    }

    static Map<Scene, Count> survey(DroneBuild build, double hfov, double uptilt, Probe probe) {
        AirframeSpec p = AirframeSpec.of(build);
        Map<Scene, Count> out = new LinkedHashMap<>();
        for (Scene scene : Scene.values()) {
            Placement place = place(scene, p);
            int poses = 0;
            int eye = 0;
            int near = 0;
            double deepest = 0.0;
            int failures = 0;
            double maxShift = 0.0;
            double minNear = EyeClearance.VANILLA_NEAR;
            String first = "";
            for (int yaw : YAWS) {
                for (int pitch : PITCHES) {
                    for (int roll : ROLLS) {
                        probe.setup(p, place, pose(yaw, pitch, roll), uptilt, hfov);
                        poses++;
                        Vector3d e = probe.eye;
                        if (place.world().inside(e.x, e.y, e.z) || behind(place.world(), probe.com, e)) {
                            eye++;
                            deepest = Math.max(deepest, depthInside(place.world(), e.x, e.y, e.z));
                        } else if (probe.cornersInside(place.world(), e.x, e.y, e.z, EyeClearance.VANILLA_NEAR)
                                || probe.pyramidTouches(place.world(), e.x, e.y, e.z, EyeClearance.VANILLA_NEAR)) {
                            near++;
                        }
                        String problem = probe.guard(place.world());
                        if (problem != null) {
                            failures++;
                            if (first.isEmpty()) {
                                first = scene + " yaw " + yaw + " pitch " + pitch + " roll " + roll + ": " + problem;
                            }
                        }
                        maxShift = Math.max(maxShift, probe.shift);
                        minNear = Math.min(minNear, probe.clearance.near());
                    }
                }
            }
            out.put(scene, new Count(poses, eye, near, deepest, failures, maxShift, minNear, first));
        }
        return out;
    }

    static String row(Drone drone, double hfov, double uptilt, Map<Scene, Count> counts) {
        AirframeSpec p = AirframeSpec.of(drone.build());
        StringBuilder line = new StringBuilder(String.format("%-36s fov %5.1f° tilt %4.1f° com %5.1f mm lens R%6.1f U%6.1f F%6.1f mm box %5.0fx%4.0f mm |",
                drone.name(), hfov, uptilt, -p.hitboxFootOffset * 1000.0, p.lensRight * 1000.0, p.lensUp * 1000.0, p.lensForward * 1000.0,
                p.hitboxWidth * 1000.0, p.hitboxHeight * 1000.0));
        for (Map.Entry<Scene, Count> e : counts.entrySet()) {
            Count c = e.getValue();
            line.append(String.format(" %s %d/%d+%d (%.0f mm) fix %d shift %.0f mm near %.3f", e.getKey().name().toLowerCase(), c.eyeInside(), c.poses(),
                    c.nearOnly(), c.deepest() * 1000.0, c.guardFailures(), c.maxShift() * 1000.0, c.minNear()));
            if (!c.firstFailure().isEmpty()) {
                line.append(" [").append(c.firstFailure()).append("]");
            }
        }
        return line.toString();
    }

    static final String PRINT_ENV = "PROPWASH_EYE_SURVEY";

    static List<String> fullSurvey(List<Drone> drones) {
        Probe probe = new Probe();
        List<String> lines = new ArrayList<>();
        for (Drone drone : drones) {
            CameraMount.Range range = CameraMount.range(drone.build().frame());
            double fov = horizontalFov(drone.build());
            for (double tilt : new double[]{range.defaultDeg(), range.minDeg(), range.maxDeg()}) {
                for (double hfov : new double[]{fov, WIDEST_FOV}) {
                    Map<Scene, Count> counts = survey(drone.build(), hfov, tilt, probe);
                    for (Map.Entry<Scene, Count> e : counts.entrySet()) {
                        assertEquals(0, e.getValue().guardFailures(), drone.name() + " " + e.getKey() + " Kippwinkel " + tilt + "° FOV " + hfov
                                + "°: " + e.getValue().firstFailure());
                    }
                    if (tilt == range.defaultDeg() && hfov == fov) {
                        assertEquals(counts.get(Scene.SNOW_1).poses(), counts.get(Scene.SNOW_1).eyeInside(), drone.name() + ": ohne Schutz im Schnee");
                        assertTrue(counts.get(Scene.GROUND).eyeInside() > 0, drone.name() + ": ohne Schutz Nase im Boden");
                        lines.add(row(drone, hfov, tilt, counts));
                    }
                }
            }
        }
        return lines;
    }

    static void print(List<String> lines) {
        String target = System.getenv(PRINT_ENV);
        if (target == null || target.isBlank()) {
            return;
        }
        try {
            Files.write(Path.of(target), lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    record Named(String label, Scene scene, int yaw, int pitch, int roll) {
    }

    static final List<Named> NAMED = List.of(
            new Named("aufrecht", Scene.GROUND, 0, 0, 0),
            new Named("seitlich", Scene.GROUND, 0, 0, 90),
            new Named("kopfüber", Scene.GROUND, 0, 0, 180),
            new Named("Nase unten", Scene.GROUND, 0, -90, 0),
            new Named("Nase 45° unten", Scene.GROUND, 0, -45, 0),
            new Named("Heck unten", Scene.GROUND, 0, 90, 0),
            new Named("Decke", Scene.CEILING, 0, 0, 0),
            new Named("Decke kopfüber", Scene.CEILING, 0, 0, 180),
            new Named("Wand", Scene.WALL, 0, 0, 0),
            new Named("Wand Nase rein", Scene.WALL, 0, -30, 0),
            new Named("Schnee 1", Scene.SNOW_1, 0, 0, 0),
            new Named("Schnee 4", Scene.SNOW_4, 0, 0, 0),
            new Named("Stufe", Scene.STAIRS, 0, 0, 0),
            new Named("Teppich kopfüber", Scene.CARPET_ON_FENCE, 0, 0, 180));

    static String namedRow(Drone drone, Probe probe) {
        AirframeSpec p = AirframeSpec.of(drone.build());
        double tilt = CameraMount.range(drone.build().frame()).defaultDeg();
        double fov = horizontalFov(drone.build());
        StringBuilder line = new StringBuilder(String.format("| %s |", drone.name()));
        for (Named named : NAMED) {
            Placement place = place(named.scene(), p);
            probe.setup(p, place, pose(named.yaw(), named.pitch(), named.roll()), tilt, fov);
            Vector3d e = probe.eye;
            String raw;
            if (place.world().inside(e.x, e.y, e.z)) {
                raw = String.format("**A %.0f**", depthInside(place.world(), e.x, e.y, e.z) * 1000.0);
            } else if (sightThroughSolid(place.world(), probe.com, e.x, e.y, e.z)) {
                raw = "**H**";
            } else if (probe.cornersInside(place.world(), e.x, e.y, e.z, EyeClearance.VANILLA_NEAR)
                    || probe.pyramidTouches(place.world(), e.x, e.y, e.z, EyeClearance.VANILLA_NEAR)) {
                raw = "N";
            } else {
                raw = "–";
            }
            String problem = probe.guard(place.world());
            String fixed = problem != null ? "FEHLER" : probe.clearance.moved() ? String.format("%.0f mm", probe.shift * 1000.0)
                    : probe.clearance.shortened() ? String.format("n %.0f", probe.clearance.near() * 1000.0) : "";
            line.append(' ').append(raw).append(fixed.isEmpty() ? "" : " → " + fixed).append(" |");
        }
        return line.toString();
    }

    static List<String> namedTable(List<Drone> drones) {
        Probe probe = new Probe();
        List<String> lines = new ArrayList<>();
        StringBuilder head = new StringBuilder("| Drohne |");
        StringBuilder rule = new StringBuilder("|---|");
        for (Named named : NAMED) {
            head.append(' ').append(named.label()).append(" |");
            rule.append("---|");
        }
        lines.add(head.toString());
        lines.add(rule.toString());
        for (Drone drone : drones) {
            lines.add(namedRow(drone, probe));
        }
        return lines;
    }

    @Test
    void everyBuiltinAndExampleDroneClipsWithoutTheGuardAndNeverWithIt() {
        List<Drone> drones = new ArrayList<>(builtins());
        drones.addAll(exampleAddon());
        print(fullSurvey(drones));
        print(namedTable(drones));
    }

    @Test
    @Tag(CalibrationHarness.ADDON_TAG)
    void everyJustMorePartsDroneClipsWithoutTheGuardAndNeverWithIt() {
        List<Drone> drones = jmp();
        assertFalse(drones.isEmpty(), CalibrationHarness.ADDON_HINT);
        print(fullSurvey(drones));
        print(namedTable(drones));
    }
}
