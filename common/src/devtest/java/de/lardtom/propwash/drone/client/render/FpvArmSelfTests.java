package de.lardtom.propwash.drone.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.creative.CreativeParam;
import de.lardtom.propwash.creative.CreativeSpec;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.CameraMount;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.rescue.client.RescueController;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.video.FovMath;
import de.lardtom.propwash.video.ImageArea;
import de.lardtom.propwash.video.client.CleanFrameTap;
import de.lardtom.propwash.video.client.FpvImage;
import de.lardtom.propwash.video.client.VideoState;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class FpvArmSelfTests {
    public static final String GEOMETRY_STEP = "drone-render: Arme und Motoren im FPV-Bild (alle Rahmen, Positionen, Schalter)";
    public static final String STEP = "drone-render: Arme und Motoren im FPV-Bild – Props hängen sichtbar an den Armen";
    public static final int KEY_RGB = FpvGuardSelfTests.KEY_RGB;
    static final float[] LOW_TILTS = {0.0F};
    static final double MOTOR_TOLERANCE_MM = 0.6;
    static final double MAX_PERCENT = 40.0;
    static final double HUB_MARGIN = 0.97;
    private static final int TICKS_PER_SHOT = FpvGuardSelfTests.TICKS_PER_SHOT;
    private static final int SETTLE_TICKS = 6;
    private static final String PHASE = "phase";
    private static final String INDEX = "index";
    private static final String SINCE = "since";
    private static final String FUTURE = "future";
    private static final String SHOT = "shot";
    private static final String CLEANUP = "cleanup";
    private static final String FAILURES = "failures";
    private static final String CONFIG = "config";
    private static final String HUBS = "hubs";
    private static final String ARMS_SEEN = "armsSeen";
    private static final List<String> REPORT = new ArrayList<>();

    private FpvArmSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GEOMETRY_STEP, 1, ctx -> geometryStep(ctx));
        SelfTest.clientStep(STEP, TICKS_PER_SHOT * 200, FpvArmSelfTests::step);
    }

    public static List<String> report() {
        return List.copyOf(REPORT);
    }

    private record Shot(DroneBuild build, float tilt) {
        String label() {
            return String.format(Locale.ROOT, "%s bei %.0f°", FpvCoverageSelfTests.label(this.build), this.tilt);
        }
    }

    private record Key(boolean[] pixels, int width, int height, int hits) {
    }

    static List<DroneBuild> builds() {
        List<DroneBuild> out = new ArrayList<>();
        for (FrameType frame : FrameType.values()) {
            PartsRenderSelfTests.buildFor(frame).map(FpvGuardSelfTests::withCreativeFc).ifPresent(out::add);
        }
        return out;
    }

    private static List<Shot> shots() {
        List<Shot> out = new ArrayList<>();
        for (DroneBuild build : builds()) {
            float standard = CameraMount.defaultUptilt(build.frame());
            DroneBuild wide = build.withCreative(CreativeSpec.EMPTY.with(CreativeParam.CAMERA_FOV, FovMath.MAX_HORIZONTAL_DEGREES));
            out.add(new Shot(build, standard));
            out.add(new Shot(wide, standard));
            for (float tilt : LOW_TILTS) {
                out.add(new Shot(wide, tilt));
            }
        }
        return out;
    }

    private static StepResult geometryStep(SelfTest.StepContext ctx) {
        DroneRenderResources res = DroneVisuals.resources();
        List<String> problems = new ArrayList<>();
        int frames = 0;
        double worstMotor = 0.0;
        for (FrameType frame : FrameType.values()) {
            Optional<DroneBuild> found = PartsRenderSelfTests.buildFor(frame);
            if (found.isEmpty()) {
                continue;
            }
            frames++;
            DroneBuild build = found.get();
            DroneAssembly assembly = res.assembly(build);
            FpvGuardMesh arms = FpvGuards.arms(assembly.frame(), frame);
            if (arms.isEmpty()) {
                problems.add(frame.id() + ": keine Arme im FPV-Bild");
            }
            FpvPropGeometry geometry = FpvProps.geometry(build, 20.0F);
            AirframeSpec spec = AirframeSpec.of(build);
            Vector3fc lens = FpvProps.lens(build);
            for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
                if (geometry == null || !geometry.attached(slot)) {
                    problems.add(frame.id() + ": Prop " + slot + " ohne zeichenbaren Arm");
                }
                if (FpvGuards.part(assembly.motor(slot)).isEmpty()) {
                    problems.add(frame.id() + ": Motor " + slot + " ohne Flächen");
                }
                int motor = DroneLayout.motorForSlot(slot);
                Vector3fc hub = assembly.prop(slot).position();
                double sideways = Math.abs(-(spec.motorX(motor) + spec.lensRight) * 1000.0 - (hub.x() - lens.x()));
                double along = Math.abs(-(spec.motorZ(motor) - spec.lensForward) * 1000.0 - (hub.z() - lens.z()));
                worstMotor = Math.max(worstMotor, Math.max(sideways, along));
                if (sideways > MOTOR_TOLERANCE_MM || along > MOTOR_TOLERANCE_MM) {
                    problems.add(String.format(Locale.ROOT, "%s, Prop %d: %.2f mm seitlich, %.2f mm längs neben dem Motor der Flugphysik", frame.id(), slot,
                            sideways, along));
                }
            }
        }
        ctx.log(String.format(Locale.ROOT, "%d Rahmen, größte Abweichung Nabe zu Motor der Flugphysik %.3f mm", frames, worstMotor));
        creativeBase(res, problems);
        switches(problems);
        armless(problems);
        return StepResult.check(problems.isEmpty() && frames > 0, String.join("; ", problems));
    }

    private static void creativeBase(DroneRenderResources res, List<String> problems) {
        DroneBuild creative = FpvGuardSelfTests.withCreativeFc(DroneBuild.RACER)
                .withCreative(CreativeSpec.EMPTY.withBase(PartKind.FRAME, FrameType.LONG_RANGE_7.key()));
        if (creative.flight().frame().wheelbaseMm != FrameType.LONG_RANGE_7.wheelbaseMm) {
            problems.add("Kreativ-FC: Flugmodell rechnet nicht mit dem Basis-Rahmen");
        }
        DroneAssembly assembly = res.assembly(creative);
        DroneAssembly installed = res.assembly(DroneBuild.RACER);
        FpvPropGeometry geometry = FpvProps.geometry(creative, 20.0F);
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            if (assembly.prop(slot).position().distance(installed.prop(slot).position()) > 0.01F || geometry == null || !geometry.attached(slot)) {
                problems.add("Kreativ-FC: Prop " + slot + " sitzt nicht am Arm des eingebauten Rahmens");
            }
        }
        if (FpvGuards.arms(assembly.frame(), creative.frame()).quads() != FpvGuards.arms(installed.frame(), DroneBuild.RACER.frame()).quads()) {
            problems.add("Kreativ-FC: Arme sind nicht die des eingebauten Rahmens");
        }
    }

    private static CameraRenderState camera() {
        CameraRenderState camera = new CameraRenderState();
        camera.projectionMatrix.setPerspective((float) Math.toRadians(100.0), 16.0F / 9.0F, 0.05F, 256.0F);
        return camera;
    }

    private static DroneRenderState state(DroneBuild build) {
        DroneRenderState state = new DroneRenderState();
        DroneVisuals.fill(state, build, true, false, new float[]{0.1F, 1.2F, 2.3F, 3.4F}, new float[]{0.0F, 0.0F, 0.0F, 0.0F}, 10.0F);
        DroneVisuals.placeInWorld(state);
        state.hidden = true;
        return state;
    }

    private static void switches(List<String> problems) {
        DroneBuild build = DroneBuild.RACER;
        float tilt = 0.0F;
        DroneRenderState state = state(build);
        DroneRenderConfig saved = DroneRenderConfig.get().copy();
        try {
            DroneRenderConfig both = saved.copy();
            both.fpvProps = true;
            both.fpvDucts = true;
            DroneRenderConfig.CONFIG.set(both);
            if (!FpvProps.prepare(state, tilt) || state.fpvArms.isEmpty() || state.fpvMotors[0].isEmpty()) {
                problems.add("Arme und Motoren nicht vorbereitet");
            }
            FpvPropGeometry geometry = state.fpvGeometry;
            if (geometry != null && !geometry.armsVisible(1.0F / camera().projectionMatrix.m00(), 1.0F / camera().projectionMatrix.m11())) {
                problems.add("Race 5 bei 0°: Arme nicht im Test-Sichtfeld");
            }
            int frames = FpvProps.armFrames();
            PoseStack poseStack = new PoseStack();
            FpvProps.submit(state, poseStack, new SubmitNodeStorage(), camera());
            if (FpvProps.armFrames() != frames + 1) {
                problems.add("Arme im FPV-Bild nicht übergeben");
            }
            if (!poseStack.isEmpty()) {
                problems.add("PoseStack nach den Armen nicht leer");
            }
            for (int mask = 0; mask < 2; mask++) {
                if (mask == 0) {
                    FpvProps.maskColor(FpvCoverageSelfTests.KEY_RGB);
                } else {
                    FpvProps.guardMaskColor(FpvGuardSelfTests.KEY_RGB);
                }
                try {
                    int masked = FpvProps.armFrames();
                    FpvProps.submit(state, new PoseStack(), new SubmitNodeStorage(), camera());
                    if (FpvProps.armFrames() != masked) {
                        problems.add(mask == 0 ? "Arme überdecken die Messung der Prop-Scheiben" : "Arme überdecken die Messung der Ducts");
                    }
                } finally {
                    FpvProps.maskColor(-1);
                    FpvProps.guardMaskColor(-1);
                }
            }
            DroneRenderConfig noDucts = saved.copy();
            noDucts.fpvProps = true;
            noDucts.fpvDucts = false;
            DroneRenderConfig.CONFIG.set(noDucts);
            if (!FpvProps.prepare(state, tilt) || state.fpvArms.isEmpty()) {
                problems.add("Schalter „Ducts im FPV-Bild“ aus nimmt die Arme mit");
            }
            DroneRenderConfig noProps = saved.copy();
            noProps.fpvProps = false;
            noProps.fpvDucts = true;
            DroneRenderConfig.CONFIG.set(noProps);
            if (FpvProps.prepare(state, tilt) && !state.fpvArms.isEmpty()) {
                problems.add("Schalter „Props im FPV-Bild“ aus, Arme trotzdem vorbereitet");
            }
            int before = FpvProps.armFrames();
            FpvProps.submit(state, new PoseStack(), new SubmitNodeStorage(), camera());
            if (FpvProps.armFrames() != before) {
                problems.add("Schalter „Props im FPV-Bild“ aus, Arme trotzdem gezeichnet");
            }
        } finally {
            DroneRenderConfig.CONFIG.set(saved);
        }
    }

    private static void armless(List<String> problems) {
        DroneBuild build = DroneBuild.FREESTYLE;
        DroneRenderState state = state(build);
        FpvPropGeometry full = FpvProps.geometry(build, 20.0F);
        if (full == null) {
            problems.add("keine FPV-Geometrie");
            return;
        }
        Vector3fc[] hubs = new Vector3fc[DroneLayout.SLOTS];
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            hubs[slot] = full.hub(slot);
        }
        CameraRenderState camera = camera();
        if (!full.anyVisible(1.05F / camera.projectionMatrix.m00(), 1.05F / camera.projectionMatrix.m11())) {
            problems.add("ohne Arm: Props im Test-Sichtfeld nicht sichtbar");
        }
        state.fpvGeometry = new FpvPropGeometry(hubs, full.radiusMm(), full.lensMm(), full.tiltDegrees(), new float[0], new float[0], new boolean[DroneLayout.SLOTS]);
        int before = FpvProps.propsDrawn();
        FpvProps.submit(state, new PoseStack(), new SubmitNodeStorage(), camera);
        if (FpvProps.propsDrawn() != before) {
            problems.add("Prop ohne zeichenbaren Arm trotzdem gezeichnet");
        }
        FpvProps.maskColor(FpvCoverageSelfTests.KEY_RGB);
        try {
            FpvProps.submit(state, new PoseStack(), new SubmitNodeStorage(), camera);
            if (FpvProps.propsDrawn() <= before) {
                problems.add("Messung der Prop-Abdeckung hängt vom Arm ab");
            }
        } finally {
            FpvProps.maskColor(-1);
        }
    }

    @SuppressWarnings("unchecked")
    private static StepResult step(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        List<Shot> shots = shots();
        int index = (int) state.computeIfAbsent(INDEX, key -> 0);
        List<String> failures = (List<String>) state.computeIfAbsent(FAILURES, key -> new ArrayList<String>());
        if (index == 0 && !state.containsKey(PHASE) && !state.containsKey(CONFIG)) {
            REPORT.clear();
            DroneRenderConfig saved = DroneRenderConfig.get().copy();
            state.put(CONFIG, saved);
            DroneRenderConfig config = saved.copy();
            config.fpvProps = true;
            config.fpvDucts = true;
            DroneRenderConfig.CONFIG.set(config);
        }
        PilotSession session = PilotSession.INSTANCE;
        if (index >= shots.size()) {
            FpvProps.armMaskColor(-1);
            if (session.isActive()) {
                session.requestStop();
                return StepResult.pending();
            }
            CompletableFuture<?> cleanup = (CompletableFuture<?>) state.computeIfAbsent(CLEANUP, key -> ctx.onServer(FpvGuardSelfTests::cleanup));
            if (!cleanup.isDone()) {
                return StepResult.pending();
            }
            for (String line : REPORT) {
                ctx.log(line);
            }
            if (state.get(CONFIG) instanceof DroneRenderConfig saved) {
                DroneRenderConfig.CONFIG.set(saved);
            }
            session.clearTestInput();
            RescueController.INSTANCE.clearHome();
            int hubs = (int) state.getOrDefault(HUBS, 0);
            int seen = (int) state.getOrDefault(ARMS_SEEN, 0);
            ctx.log(String.format(Locale.ROOT, "%d Bilder von %d Rahmen, %d Naben im Bild, %d Bilder mit Armen oder Motoren", shots.size(), builds().size(), hubs, seen));
            if (hubs == 0 || seen == 0) {
                failures.add("kein Bild zeigt eine Nabe mit Arm");
            }
            return StepResult.check(failures.isEmpty(), String.join("; ", failures));
        }
        Shot shot = shots.get(index);
        DroneBuild build = shot.build();
        float tilt = shot.tilt();
        String label = shot.label();
        String phase = (String) state.getOrDefault(PHASE, "spawn");
        switch (phase) {
            case "spawn" -> {
                FpvProps.armMaskColor(-1);
                if (session.isActive()) {
                    session.requestStop();
                    return StepResult.pending();
                }
                if (!FpvGuardSelfTests.serverReleased(ctx, state)) {
                    return StepResult.pending();
                }
                state.put(FUTURE, ctx.onServer(server -> FpvGuardSelfTests.start(server, build, tilt)));
                state.put(SINCE, ctx.tick());
                state.put(PHASE, "fly");
                return StepResult.pending();
            }
            case "fly" -> {
                CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
                if (future.isCompletedExceptionally()) {
                    return next(state, failures, label + ": FPV-Start " + future.exceptionNow());
                }
                QuadcopterEntity drone = session.drone();
                if (!future.isDone() || !session.isFpv() || drone == null || !build.equals(drone.build())
                        || Math.abs(FpvProps.cameraTilt(drone) - tilt) > 0.01F) {
                    return timeout(ctx, state, failures, label + ": keine FPV-Sitzung mit dem Kamerawinkel");
                }
                FpvProps.armMaskColor(KEY_RGB);
                state.put(SINCE, ctx.tick());
                state.put(PHASE, "settle");
                return StepResult.pending();
            }
            case "settle" -> {
                if (ctx.tick() - (int) state.get(SINCE) < SETTLE_TICKS) {
                    return StepResult.pending();
                }
                AtomicReference<Key> captured = new AtomicReference<>();
                state.put(SHOT, captured);
                CleanFrameTap.request(target -> {
                    ImageArea area = FpvImage.area(target.width, target.height);
                    Screenshot.takeScreenshot(target, 1, image -> {
                        try (image) {
                            captured.set(key(image, area));
                        }
                    });
                });
                state.put(SINCE, ctx.tick());
                state.put(PHASE, "capture");
                return StepResult.pending();
            }
            case "capture" -> {
                Key key = ((AtomicReference<Key>) state.get(SHOT)).get();
                if (key == null) {
                    return timeout(ctx, state, failures, label + ": kein Bild vor dem Videoeffekt");
                }
                FpvProps.armMaskColor(-1);
                return next(state, failures, evaluate(state, build, label, tilt, key));
            }
            default -> {
                return StepResult.fail("unbekannte Phase " + phase);
            }
        }
    }

    private static StepResult timeout(SelfTest.StepContext ctx, Map<String, Object> state, List<String> failures, String what) {
        if (ctx.tick() - (int) state.get(SINCE) > TICKS_PER_SHOT) {
            FpvProps.armMaskColor(-1);
            return next(state, failures, what);
        }
        return StepResult.pending();
    }

    private static StepResult next(Map<String, Object> state, List<String> failures, String problem) {
        if (problem != null) {
            failures.add(problem);
        }
        state.put(INDEX, (int) state.get(INDEX) + 1);
        state.put(PHASE, "spawn");
        return StepResult.pending();
    }

    private static Key key(NativeImage image, ImageArea area) {
        if (area.screenWidth() != image.getWidth() || area.screenHeight() != image.getHeight()) {
            area = ImageArea.full(image.getWidth(), image.getHeight());
        }
        int width = area.width();
        int height = area.height();
        boolean[] pixels = new boolean[width * height];
        int hits = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (FpvGuardSelfTests.isKey(image.getPixel(area.x() + x, area.y() + y))) {
                    pixels[y * width + x] = true;
                    hits++;
                }
            }
        }
        return new Key(pixels, width, height, hits);
    }

    private static float[] armAndMotorQuads(DroneBuild build) {
        DroneAssembly assembly = DroneVisuals.resources().assembly(build);
        float[] arms = FpvGuards.arms(assembly.frame(), build.frame()).pointsMm();
        List<float[]> motors = new ArrayList<>();
        int total = arms.length;
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            float[] points = FpvGuards.part(assembly.motor(slot)).pointsMm();
            Vector3fc at = assembly.motor(slot).position();
            for (int i = 0; i + 2 < points.length; i += 3) {
                points[i] += at.x();
                points[i + 1] += at.y();
                points[i + 2] += at.z();
            }
            motors.add(points);
            total += points.length;
        }
        float[] out = new float[total];
        System.arraycopy(arms, 0, out, 0, arms.length);
        int to = arms.length;
        for (float[] points : motors) {
            System.arraycopy(points, 0, out, to, points.length);
            to += points.length;
        }
        return out;
    }

    private static String evaluate(Map<String, Object> state, DroneBuild build, String label, float tilt, Key key) {
        double fov = VideoState.INSTANCE.horizontalFov();
        double rendered = 100.0 * key.hits() / ((double) key.width() * key.height());
        double analysis = FpvGuardSelfTests.analyticPercent(armAndMotorQuads(build), FpvProps.lens(build), tilt, fov, key.width(), key.height());
        FpvPropGeometry geometry = FpvProps.geometry(build, tilt);
        if (geometry == null) {
            return label + ": keine FPV-Geometrie";
        }
        double tanH = Math.tan(Math.toRadians(Math.clamp(fov, 1.0, 179.0)) / 2.0);
        double tanV = tanH / ((double) key.width() / key.height());
        int visible = 0;
        int hanging = 0;
        List<String> floating = new ArrayList<>();
        Vector3f camera = new Vector3f();
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            Vector3fc hub = geometry.hub(slot);
            geometry.toCamera(hub.x(), hub.y(), hub.z(), camera);
            double depth = -camera.z;
            if (depth <= FpvPropGeometry.MIN_DEPTH_MM) {
                continue;
            }
            double u = camera.x / (depth * tanH);
            double v = camera.y / (depth * tanV);
            if (Math.abs(u) >= HUB_MARGIN || Math.abs(v) >= HUB_MARGIN) {
                continue;
            }
            visible++;
            if (reachesEdge(key, u, v)) {
                hanging++;
            } else {
                floating.add(String.format(Locale.ROOT, "Prop %d bei (%.2f, %.2f)", slot, u, v));
            }
        }
        state.merge(HUBS, visible, (a, b) -> (int) a + (int) b);
        if (key.hits() > 0) {
            state.merge(ARMS_SEEN, 1, (a, b) -> (int) a + (int) b);
        }
        String line = String.format(Locale.ROOT, "%s: Arme und Motoren %.2f %%, Analyse %.2f %% (%.0f°, %d×%d), Naben im Bild %d, angebunden %d", label, rendered,
                analysis, fov, key.width(), key.height(), visible, hanging);
        REPORT.add(line);
        Constants.LOG.info("FPV-Arme {}", line);
        if (!floating.isEmpty()) {
            return line + " – schwebt: " + String.join(", ", floating);
        }
        if (rendered > MAX_PERCENT) {
            return line + " – verdeckt zu viel vom Bild";
        }
        if (!FpvGuardSelfTests.matches(rendered, analysis)) {
            return line + " – Bild und Analyse weichen ab";
        }
        return null;
    }

    private static boolean reachesEdge(Key key, double u, double v) {
        int width = key.width();
        int height = key.height();
        int cx = (int) Math.round((u + 1.0) * 0.5 * width);
        int cy = (int) Math.round((1.0 - v) * 0.5 * height);
        int radius = Math.max(4, width / 64);
        boolean[] seen = new boolean[key.pixels().length];
        ArrayDeque<Integer> open = new ArrayDeque<>();
        for (int y = Math.max(0, cy - radius); y <= Math.min(height - 1, cy + radius); y++) {
            for (int x = Math.max(0, cx - radius); x <= Math.min(width - 1, cx + radius); x++) {
                if (key.pixels()[y * width + x]) {
                    seen[y * width + x] = true;
                    open.add(y * width + x);
                }
            }
        }
        while (!open.isEmpty()) {
            int at = open.poll();
            int x = at % width;
            int y = at / width;
            if (x == 0 || y == 0 || x == width - 1 || y == height - 1) {
                return true;
            }
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int next = (y + dy) * width + x + dx;
                    if (key.pixels()[next] && !seen[next]) {
                        seen[next] = true;
                        open.add(next);
                    }
                }
            }
        }
        return false;
    }
}
