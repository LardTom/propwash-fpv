package de.lardtom.propwash.drone.client.render;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.part.PropDefinition;
import de.lardtom.propwash.api.part.PropMaterial;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.creative.CreativeParam;
import de.lardtom.propwash.creative.CreativeSpec;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.CameraMount;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.rescue.client.RescueController;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.video.FovMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static de.lardtom.propwash.drone.client.render.DroneVisualScale.MM_TO_UNITS;

public final class CovPropBlurCenterSelfTests {
    public static final String STEP = "drone-render: drehende Props behalten die Mitte – Nabe und Mutter wie im Stand, Scheibe um die Motorachse "
            + "(FPV-Bild und außen, alle Props)";
    static final String SHOTS = "bildschirmfotos: Mitte drehender Props – ";
    static final float TILT = 20.0F;
    static final float CAMERA_FOV = 170.0F;
    static final float THRESHOLD = 0.12F;
    static final float MATCH = 1.0E-4F;
    static final float AXIS_SHARE = 0.05F;
    static final float AXIS_MIN_MM = 0.3F;
    static final float RIM_SHARE = 0.05F;
    static final float RING_MM = 0.5F;
    static final float LEVEL_MM = 0.5F;
    static final float MOTOR_AXIS_MM = 0.01F;
    static final float[] ANGLES = {0.4F, 1.3F, 2.2F, 3.1F};
    static final PropType NO_MODEL = (PropType) PartIndex.detached(PropDefinition.builder(Identifier.fromNamespaceAndPath(RenderExampleParts.NS,
            "cov_blur_center")).mass(5.0).size(127, 4.3, 3).coefficients(0.09, 0.04).material(PropMaterial.POLYCARBONATE).build());
    private static final Identifier JMP_RACE_SLIM = Identifier.fromNamespaceAndPath("justmoreparts", "race_5_stretched");
    private static final Identifier JMP_WHOOP_75 = Identifier.fromNamespaceAndPath("justmoreparts", "whoop_75_duct");
    private static final int OFFSET = 56;
    private static final float HOVER_HEIGHT = 2.2F;
    private static final double SIDE_DISTANCE = 1.1;
    private static final double SIDE_HEIGHT = 1.0;
    private static final int SIDE_FOV = 30;
    private static final int STOP_SETTLE_TICKS = 5;
    private static final String FUTURE = "future";
    private static final String SINCE = "since";
    private static final String LAST_Y = "last_y";
    private static final String INTEGRAL = "integral";
    private static final String TARGET_Y = "target_y";
    private static final AtomicReference<BlockPos> BASE = new AtomicReference<>();
    private static final AtomicInteger DRONE = new AtomicInteger(-1);
    private static final List<String> REPORT = new ArrayList<>();
    private static int savedFov = -1;
    private static @Nullable DroneRenderConfig savedConfig;

    private CovPropBlurCenterSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(STEP, 1, ctx -> step(ctx));
        if (!Screenshots.enabled()) {
            return;
        }
        SelfTest.clientStep(SHOTS + "Fenster und Platz vorbereiten", 400, CovPropBlurCenterSelfTests::prepareStep);
        shots("Race 5 Zoll", "race_5", () -> Optional.of(DroneBuild.RACER));
        shots("Freestyle 5 Zoll", "freestyle_5", () -> Optional.of(DroneBuild.FREESTYLE));
        shots("Race 5 Zoll (Slim) aus Just More Parts", "jmp_race_slim", () -> PartsRenderShowcase.addonPreset(JMP_RACE_SLIM));
        shots("Whoop 75 mm aus Just More Parts", "jmp_whoop_75", () -> PartsRenderShowcase.addonPreset(JMP_WHOOP_75));
        SelfTest.clientStep(SHOTS + "aufräumen", 200, CovPropBlurCenterSelfTests::cleanupStep);
    }

    public static List<String> report() {
        return List.copyOf(REPORT);
    }

    private static void shots(String label, String file, Supplier<Optional<DroneBuild>> build) {
        Supplier<DroneBuild> wide = () -> FpvGuardSelfTests.withCreativeFc(build.get().orElseThrow())
                .withCreative(CreativeSpec.EMPTY.with(CreativeParam.CAMERA_FOV, FovMath.MAX_HORIZONTAL_DEGREES));
        for (boolean blur : new boolean[]{false, true}) {
            String look = blur ? "drehend" : "stehend";
            Screenshots.optionalStep(SHOTS + label + ", FPV-Bild 160° und 0°, " + look, "cov_prop_blur_center_fpv_" + file + "_" + (blur ? "blur" : "still"),
                    20, () -> build.get().isPresent(), new HoverScene(wide, true, blur));
            Screenshots.optionalStep(SHOTS + label + ", von außen, " + look, "cov_prop_blur_center_side_" + file + "_" + (blur ? "blur" : "still"),
                    20, () -> build.get().isPresent(), new HoverScene(() -> build.get().orElseThrow(), false, blur));
        }
    }

    record Case(String label, DroneBuild build) {
    }

    record Quad(float[] xyz, boolean opaque, String material) {
    }

    static List<Case> cases() {
        List<Case> out = new ArrayList<>();
        for (PropType prop : PropType.all()) {
            out.add(new Case(prop.id().toString(), DroneBuild.FREESTYLE.withProp(prop)));
        }
        out.add(new Case(RenderExampleParts.P13_STILL.id() + " ohne _blur-Modell", RenderExampleParts.STILL_PROP_BUILD));
        out.add(new Case(NO_MODEL.id() + " ohne Modelle", DroneBuild.FREESTYLE.withProp(NO_MODEL)));
        return out;
    }

    private static StepResult step(SelfTest.StepContext ctx) {
        REPORT.clear();
        DroneRenderConfig saved = DroneRenderConfig.get().copy();
        List<String> problems = new ArrayList<>();
        int checked = 0;
        int identical = 0;
        int discRays = 0;
        int elements = 0;
        try {
            DroneRenderConfig config = saved.copy();
            config.fpvProps = true;
            config.fpvDucts = false;
            config.propBlurThreshold = THRESHOLD;
            DroneRenderConfig.CONFIG.set(config);
            for (Case c : cases()) {
                int[] counts = check(c, problems);
                checked += counts[0];
                identical += counts[1];
                discRays += counts[2];
                elements += counts[3];
            }
        } finally {
            DroneRenderConfig.CONFIG.set(saved);
        }
        String summary = String.format(Locale.ROOT, "%d Props (%d Fälle je Ansicht und Drehrichtung), davon %d mit gleicher Mitte wie im Stand, "
                + "%d Strahlen der FPV-Scheibe auf dem Kreisring, %d Modellpaare mit gleichen Elementen der Mitte", cases().size(), checked, identical, discRays,
                elements);
        ctx.log(summary);
        for (String line : REPORT) {
            ctx.log(line);
        }
        for (String problem : problems) {
            Constants.LOG.warn("Prop-Mitte: {}", problem);
        }
        int shown = Math.min(12, problems.size());
        String detail = String.join("; ", problems.subList(0, shown)) + (problems.size() > shown ? "; … (" + problems.size() + " insgesamt)" : "");
        return StepResult.check(problems.isEmpty() && checked > 0, problems.isEmpty() ? "keine Fälle geprüft" : detail);
    }

    private static int[] check(Case c, List<String> problems) {
        DroneRenderResources res = DroneVisuals.resources();
        DroneBuild build = c.build();
        DroneAssembly assembly = res.assembly(build);
        PropType prop = build.prop();
        int checked = 0;
        int identical = 0;
        int rays = 0;
        int elements = 0;
        CameraRenderState camera = camera();
        FpvPropGeometry geometry = fullGeometry(build, assembly);
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            if (!DroneLayout.isFront(slot)) {
                continue;
            }
            String where = c.label() + ", Prop " + slot + (res.layout(build.frame()).counterClockwise(slot) ? " (ccw)" : " (cw)");
            Vector3fc hub = assembly.prop(slot).position();
            Vector3fc motor = assembly.motor(slot).position();
            if (Math.abs(hub.x() - motor.x()) > MOTOR_AXIS_MM || Math.abs(hub.z() - motor.z()) > MOTOR_AXIS_MM) {
                problems.add(String.format(Locale.ROOT, "%s: Nabe %.2f/%.2f mm neben der Motorachse", where, hub.x() - motor.x(), hub.z() - motor.z()));
            }
            boolean same = (assembly.prop(slot).item() != null) == (assembly.blur(slot).item() != null);
            DroneRenderState sharp = state(build, false);
            DroneRenderState blur = state(build, true);
            if (sharp.propBlur[slot] || !blur.propBlur[slot]) {
                problems.add(where + ": Unschärfe-Schwelle greift nicht (stehend " + sharp.propBlur[slot] + ", drehend " + blur.propBlur[slot] + ")");
                continue;
            }
            List<Quad> outsideSharp = outside(sharp, slot);
            List<Quad> outsideBlur = outside(blur, slot);
            Matrix4f outsideFrame = outsideFrame(blur, slot);
            identical += center(where + " außen", outsideSharp, outsideBlur, outsideFrame, same, problems) ? 1 : 0;
            elements += models(where, assembly.prop(slot), assembly.blur(slot), problems);
            outsideDisc(where + " außen", outsideBlur, outsideFrame, prop, problems);
            checked++;
            if (!geometry.visible(slot, tan(camera.projectionMatrix.m00()), tan(camera.projectionMatrix.m11()))) {
                problems.add(where + ": im Test-Sichtfeld nicht zu sehen");
                continue;
            }
            List<Quad> fpvSharp = fpv(sharp, slot, geometry, camera);
            List<Quad> fpvBlur = fpv(blur, slot, geometry, camera);
            identical += center(where + " im FPV-Bild", fpvSharp, fpvBlur, fpvFrame(geometry, hub), same, problems) ? 1 : 0;
            rays += fpvDisc(where + " im FPV-Bild", fpvBlur, geometry, hub, prop, problems);
            checked++;
        }
        return new int[]{checked, identical, rays, elements};
    }

    private static float tan(float m) {
        return m > 1.0E-4F ? 1.0F / m : FpvPropGeometry.tanHalfFromDegrees(CAMERA_FOV);
    }

    static CameraRenderState camera() {
        CameraRenderState camera = new CameraRenderState();
        camera.projectionMatrix.setPerspective((float) Math.toRadians(CAMERA_FOV), 16.0F / 9.0F, 0.05F, 256.0F);
        return camera;
    }

    static FpvPropGeometry fullGeometry(DroneBuild build, DroneAssembly assembly) {
        Vector3fc[] hubs = new Vector3fc[DroneLayout.SLOTS];
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            hubs[slot] = assembly.prop(slot).position();
        }
        return new FpvPropGeometry(hubs, build.prop().diameterMm * 0.5F, FpvProps.lens(build), TILT);
    }

    static DroneRenderState state(DroneBuild build, boolean spinning) {
        DroneRenderState state = new DroneRenderState();
        float output = spinning ? 1.0F : 0.0F;
        DroneVisuals.fill(state, build, true, false, ANGLES, new float[]{output, output, output, output}, 10.0F);
        return state;
    }

    static List<Quad> outside(DroneRenderState state, int slot) {
        state.hidden = false;
        DroneVisuals.placeInWorld(state);
        isolate(state, slot);
        Recorder recorder = new Recorder();
        DroneVisuals.submit(state, new PoseStack(), recorder);
        return recorder.quads;
    }

    static void isolate(DroneRenderState state, int slot) {
        for (DroneRenderState.PartSlot part : List.of(state.frame, state.stack, state.battery, state.video, state.transmitter, state.antenna)) {
            part.set(DroneAssembly.Part.EMPTY);
        }
        state.batteryMissing = true;
        for (int i = 0; i < DroneLayout.SLOTS; i++) {
            state.motors[i].set(DroneAssembly.Part.EMPTY);
            if (i != slot) {
                state.props[i].set(DroneAssembly.Part.EMPTY);
            }
        }
        state.leds = Shape.EMPTY;
        Arrays.fill(state.ledColor, 0);
        Arrays.fill(state.motorGlowColor, 0);
        Arrays.fill(state.accessoryType, null);
        state.hasCargo = false;
    }

    static Matrix4f outsideFrame(DroneRenderState state, int slot) {
        Vector3fc hub = state.props[slot].position;
        return new Matrix4f().translate(0.0F, state.originY, 0.0F).rotate(state.orientation).scale(state.poseScale)
                .translate(-state.pivot.x * MM_TO_UNITS, -state.pivot.y * MM_TO_UNITS, -state.pivot.z * MM_TO_UNITS)
                .translate(hub.x() * MM_TO_UNITS, hub.y() * MM_TO_UNITS, hub.z() * MM_TO_UNITS).rotateY(state.rotorAngle[slot]);
    }

    static List<Quad> fpv(DroneRenderState state, int slot, FpvPropGeometry full, CameraRenderState camera) {
        Vector3fc[] hubs = new Vector3fc[DroneLayout.SLOTS];
        boolean[] only = new boolean[DroneLayout.SLOTS];
        for (int i = 0; i < DroneLayout.SLOTS; i++) {
            hubs[i] = full.hub(i);
        }
        only[slot] = true;
        state.hidden = true;
        state.fpvGeometry = new FpvPropGeometry(hubs, full.radiusMm(), full.lensMm(), full.tiltDegrees(), new float[0], new float[0], only);
        state.fpvGuards = FpvGuardMesh.EMPTY;
        FpvProps.clearArms(state);
        Recorder recorder = new Recorder();
        FpvProps.submit(state, new PoseStack(), recorder, camera);
        return recorder.quads;
    }

    static Matrix4f fpvFrame(FpvPropGeometry geometry, Vector3fc hub) {
        Vector3fc lens = geometry.lensMm();
        float near = geometry.nearScale();
        float unit = DroneVisualScale.MODEL_UNIT_SCALE;
        return new Matrix4f().scale(near).rotateX((float) Math.toRadians(-geometry.tiltDegrees())).scale(unit)
                .translate(-lens.x() * MM_TO_UNITS, -lens.y() * MM_TO_UNITS, -lens.z() * MM_TO_UNITS)
                .translate(hub.x() * MM_TO_UNITS, hub.y() * MM_TO_UNITS, hub.z() * MM_TO_UNITS);
    }

    static boolean center(String where, List<Quad> sharp, List<Quad> blur, Matrix4f frame, boolean same, List<String> problems) {
        List<Quad> center = new ArrayList<>();
        for (Quad quad : blur) {
            if (quad.opaque()) {
                center.add(quad);
            }
        }
        if (center.isEmpty()) {
            problems.add(where + ": Mitte fehlt (keine deckende Nabe, Mutter oder Schraube über der Motorachse)");
            return false;
        }
        Matrix4f inverse = new Matrix4f(frame).invert();
        Vector3f local = new Vector3f();
        double sumX = 0.0;
        double sumZ = 0.0;
        int count = 0;
        float radius = 0.0F;
        float low = Float.POSITIVE_INFINITY;
        float high = Float.NEGATIVE_INFINITY;
        for (Quad quad : center) {
            for (int v = 0; v < 4; v++) {
                toLocalMm(inverse, quad, v, local);
                sumX += local.x;
                sumZ += local.z;
                count++;
                radius = Math.max(radius, (float) Math.hypot(local.x, local.z));
                low = Math.min(low, local.y);
                high = Math.max(high, local.y);
            }
        }
        float off = (float) Math.hypot(sumX / count, sumZ / count);
        boolean ok = true;
        if (off > Math.max(AXIS_MIN_MM, AXIS_SHARE * radius)) {
            problems.add(String.format(Locale.ROOT, "%s: Mitte %.2f mm neben der Motorachse (Radius %.2f mm)", where, off, radius));
            ok = false;
        }
        if (!same) {
            REPORT.add(String.format(Locale.ROOT, "%s: Mitte als Ersatzform, %d Flächen, Radius %.2f mm, Höhe %.2f bis %.2f mm", where, center.size(), radius,
                    low, high));
            return false;
        }
        List<Quad> standing = new ArrayList<>();
        for (Quad quad : sharp) {
            if (quad.opaque()) {
                standing.add(quad);
            }
        }
        int foreign = 0;
        for (Quad quad : center) {
            if (!contains(standing, quad)) {
                foreign++;
            }
        }
        if (foreign > 0) {
            problems.add(String.format(Locale.ROOT, "%s: %d von %d Flächen der Mitte stehen nicht wie beim stehenden Prop", where, foreign, center.size()));
            ok = false;
        }
        return ok;
    }

    static int models(String where, DroneAssembly.Part sharp, DroneAssembly.Part blur, List<String> problems) {
        if (sharp.model() == null || blur.model() == null || sharp.model().modelFile() == null || blur.model().modelFile() == null) {
            return 0;
        }
        CovPropBlurCenterModels.Result result = compare(sharp.model().modelFile(), blur.model().modelFile());
        if (result == null) {
            REPORT.add(where + ": Modelle ohne Elemente bzw. _blur-Modell ohne durchscheinende Scheibe (force_translucent), Vergleich der Elemente übersprungen");
            return 0;
        }
        if (!result.same()) {
            problems.add(String.format(Locale.ROOT, "%s: Mitte im Modell %s weicht von %s ab (%d Elemente fehlen%s, %d zusätzlich%s)", where,
                    blur.model().name(), sharp.model().name(), result.missing().size(), names(result.missing()), result.extra().size(),
                    names(result.extra())));
        }
        return 1;
    }

    private static String names(List<String> elements) {
        return elements.isEmpty() ? "" : ": " + String.join(", ", elements);
    }

    static CovPropBlurCenterModels.@Nullable Result compare(Identifier standing, Identifier blur) {
        JsonObject standingModel = json(standing);
        JsonObject blurModel = json(blur);
        if (standingModel == null || blurModel == null) {
            return null;
        }
        try {
            return CovPropBlurCenterModels.compare(standingModel, blurModel);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static @Nullable JsonObject json(Identifier file) {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(file);
        if (resource.isEmpty()) {
            return null;
        }
        try (Reader reader = resource.get().openAsReader()) {
            JsonElement root = JsonParser.parseReader(reader);
            return root.isJsonObject() ? root.getAsJsonObject() : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static void toLocalMm(Matrix4f inverse, Quad quad, int vertex, Vector3f dest) {
        float[] xyz = quad.xyz();
        inverse.transformPosition(xyz[vertex * 3], xyz[vertex * 3 + 1], xyz[vertex * 3 + 2], dest);
        dest.div(MM_TO_UNITS);
    }

    static boolean contains(List<Quad> quads, Quad wanted) {
        for (Quad quad : quads) {
            if (same(quad, wanted)) {
                return true;
            }
        }
        return false;
    }

    static boolean same(Quad a, Quad b) {
        if (a.opaque() != b.opaque() || !a.material().equals(b.material())) {
            return false;
        }
        for (int i = 0; i < 12; i++) {
            if (Math.abs(a.xyz()[i] - b.xyz()[i]) > MATCH * Math.max(1.0F, Math.abs(a.xyz()[i]))) {
                return false;
            }
        }
        return true;
    }

    static void outsideDisc(String where, List<Quad> blur, Matrix4f frame, PropType prop, List<String> problems) {
        Matrix4f inverse = new Matrix4f(frame).invert();
        Vector3f local = new Vector3f();
        double sumX = 0.0;
        double sumZ = 0.0;
        int count = 0;
        float rim = 0.0F;
        for (Quad quad : blur) {
            if (quad.opaque()) {
                continue;
            }
            for (int v = 0; v < 4; v++) {
                toLocalMm(inverse, quad, v, local);
                sumX += local.x;
                sumZ += local.z;
                count++;
                rim = Math.max(rim, Math.max(Math.abs(local.x), Math.abs(local.z)));
            }
        }
        float radius = prop.diameterMm * 0.5F;
        if (count == 0) {
            problems.add(where + ": keine Scheibe");
            return;
        }
        float off = (float) Math.hypot(sumX / count, sumZ / count);
        if (off > Math.max(AXIS_MIN_MM, AXIS_SHARE * radius)) {
            problems.add(String.format(Locale.ROOT, "%s: Scheibe %.2f mm neben der Motorachse", where, off));
        }
        if (Math.abs(rim - radius) > RIM_SHARE * radius + RING_MM) {
            problems.add(String.format(Locale.ROOT, "%s: Scheibe reicht %.1f mm weit statt %.1f mm", where, rim, radius));
        }
    }

    static int fpvDisc(String where, List<Quad> blur, FpvPropGeometry geometry, Vector3fc hub, PropType prop, List<String> problems) {
        Matrix4f inverse = new Matrix4f().rotateX((float) Math.toRadians(-geometry.tiltDegrees())).scale(DroneVisualScale.MODEL_UNIT_SCALE).invert();
        Vector3fc lens = geometry.lensMm();
        float plane = hub.y() + FallbackShapes.hubHeight(prop) * 0.55F - lens.y();
        float centerX = hub.x() - lens.x();
        float centerZ = hub.z() - lens.z();
        float radius = prop.diameterMm * 0.5F;
        float inner = FallbackShapes.hubDiameter(prop) * 0.5F;
        Vector3f w = new Vector3f();
        int rays = 0;
        int outside = 0;
        float worst = 0.0F;
        boolean disc = false;
        for (Quad quad : blur) {
            if (quad.opaque()) {
                continue;
            }
            disc = true;
            if (Math.abs(plane) < LEVEL_MM) {
                continue;
            }
            for (int v = 0; v < 4; v++) {
                float[] xyz = quad.xyz();
                inverse.transformPosition(xyz[v * 3], xyz[v * 3 + 1], xyz[v * 3 + 2], w);
                w.div(MM_TO_UNITS);
                if (w.y / plane <= 0.0F) {
                    continue;
                }
                w.mul(plane / w.y);
                float r = (float) Math.hypot(w.x - centerX, w.z - centerZ);
                rays++;
                if (r > radius * (1.0F + RIM_SHARE) + RING_MM || r < inner - RING_MM) {
                    outside++;
                    worst = Math.max(worst, Math.max(r - radius, inner - r));
                }
            }
        }
        if (!disc) {
            problems.add(where + ": keine Scheibe");
        } else if (outside > 0) {
            problems.add(String.format(Locale.ROOT, "%s: %d von %d Punkten der Scheibe liegen nicht auf dem Kreisring um die Motorachse (bis %.1f mm)", where,
                    outside, rays, worst));
        }
        return rays;
    }

    static final class Capture implements VertexConsumer {
        final List<float[]> vertices = new ArrayList<>();
        final List<Integer> colors = new ArrayList<>();

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.vertices.add(new float[]{x, y, z});
            this.colors.add(0);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this.setColor((a << 24) | (r << 16) | (g << 8) | b);
        }

        @Override
        public VertexConsumer setColor(int color) {
            if (!this.colors.isEmpty()) {
                this.colors.set(this.colors.size() - 1, color);
            }
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv3(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }
    }

    static final class Recorder implements SubmitNodeCollector {
        final List<Quad> quads = new ArrayList<>();

        @Override
        public OrderedSubmitNodeCollector order(int order) {
            return this;
        }

        @Override
        public void submitItem(PoseStack poseStack, ItemDisplayContext displayContext, int lightCoords, int overlayCoords, int outlineColor,
                               int[] tintLayers, ItemQuads itemQuads, ItemStackRenderState.FoilType foilType) {
            Matrix4f pose = new Matrix4f(poseStack.last().pose());
            Set<BakedQuad> translucent = Collections.newSetFromMap(new IdentityHashMap<>());
            translucent.addAll(itemQuads.translucent());
            Vector3f p = new Vector3f();
            for (BakedQuad quad : itemQuads.all()) {
                float[] xyz = new float[12];
                for (int v = 0; v < 4; v++) {
                    pose.transformPosition(quad.position(v), p);
                    xyz[v * 3] = p.x;
                    xyz[v * 3 + 1] = p.y;
                    xyz[v * 3 + 2] = p.z;
                }
                BakedQuad.MaterialInfo material = quad.materialInfo();
                this.quads.add(new Quad(xyz, !translucent.contains(quad), "item " + material.sprite().contents().name() + " #" + material.tintIndex()));
            }
        }

        @Override
        public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer) {
            boolean opaque = !AfterWaterRenderTypes.drawsAfterWater(renderType);
            Capture capture = new Capture();
            customGeometryRenderer.render(poseStack.last(), capture);
            List<float[]> vertices = capture.vertices;
            for (int i = 0; i + 3 < vertices.size(); i += 4) {
                float[] xyz = new float[12];
                StringBuilder colors = new StringBuilder("form");
                for (int v = 0; v < 4; v++) {
                    float[] vertex = vertices.get(i + v);
                    xyz[v * 3] = vertex[0];
                    xyz[v * 3 + 1] = vertex[1];
                    xyz[v * 3 + 2] = vertex[2];
                    colors.append(' ').append(Integer.toHexString(capture.colors.get(i + v)));
                }
                this.quads.add(new Quad(xyz, opaque, colors.toString()));
            }
        }

        @Override
        public void submitShadow(PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces) {
        }

        @Override
        public void submitNameTag(PoseStack poseStack, @Nullable Vec3 nameTagAttachment, int offset, Component name, boolean seeThrough, int lightCoords,
                                  CameraRenderState camera) {
        }

        @Override
        public void submitText(PoseStack poseStack, float x, float y, FormattedCharSequence string, boolean dropShadow, Font.DisplayMode displayMode,
                               int lightCoords, int color, int backgroundColor, int outlineColor) {
        }

        @Override
        public void submitTextBackground(PoseStack poseStack, float x0, float y0, float x1, float y1, int color, Font.DisplayMode displayMode,
                                         int lightCoords) {
        }

        @Override
        public void submitFlame(PoseStack poseStack, EntityRenderState renderState, Quaternionf rotation) {
        }

        @Override
        public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leashState) {
        }

        @Override
        public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int lightCoords, int overlayCoords,
                                    int tintedColor, @Nullable UvMapping uvMapping, int outlineColor) {
        }

        @Override
        public <S> void submitCrumblingOverlay(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int lightCoords,
                                               int overlayCoords, int tintedColor, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        }

        @Override
        public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState movingBlockRenderState, int outlineColor) {
        }

        @Override
        public void submitBlockModel(PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> parts, int[] tintLayers, int lightCoords,
                                     int overlayCoords, int outlineColor) {
        }

        @Override
        public void submitBreakingBlockModel(PoseStack poseStack, List<BlockStateModelPart> parts, int progress, boolean isBlockTranslucent) {
        }

        @Override
        public void submitShapeOutline(PoseStack poseStack, VoxelShape shape, RenderType renderType, int color, float width, boolean afterTerrain) {
        }

        @Override
        public void submitQuadParticleGroup(QuadParticleRenderState particles) {
        }

        @Override
        public void submitGizmoPrimitives(DrawableGizmoPrimitives.Group group, CameraRenderState camera, boolean onTop) {
        }
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static BlockPos base() {
        BlockPos pos = BASE.get();
        if (pos == null) {
            throw new IllegalStateException("Platz nicht vorbereitet");
        }
        return pos;
    }

    private static StepResult awaitServer(SelfTest.StepContext ctx, String what) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null) {
            return StepResult.fail(what + ": keine Server-Aktion");
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(what + ": " + future.exceptionNow());
        }
        return future.isDone() ? StepResult.pass() : StepResult.pending();
    }

    private static StepResult prepareStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(FUTURE)) {
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            StepResult resized = Screenshots.resizeWindow(ctx);
            if (resized.status() != StepResult.Status.PASS) {
                return resized;
            }
            Screenshots.hideMarker(true);
            savedFov = mc.options.fov().get();
            savedConfig = DroneRenderConfig.get().copy();
            state.put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                ServerLevel level = player.level();
                int x = player.getBlockX() + OFFSET;
                int z = player.getBlockZ() + OFFSET;
                level.getChunk(x >> 4, z >> 4);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                BASE.set(new BlockPos(x, y, z));
                player.setGameMode(GameType.CREATIVE);
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                player.teleportTo(level, x + 0.5, y, z + 0.5, Set.of(), 0.0F, 0.0F, true);
            }));
            return StepResult.pending();
        }
        StepResult server = awaitServer(ctx, "Platz vorbereiten");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        BlockPos base = BASE.get();
        if (mc.player == null || base == null || mc.player.blockPosition().distManhattan(base) > 2) {
            return ctx.tick() > 380 ? StepResult.fail("Spieler kam nicht am Platz an") : StepResult.pending();
        }
        return StepResult.pass();
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            PilotSession.INSTANCE.clearTestInput();
            if (PilotSession.INSTANCE.isActive()) {
                PilotSession.INSTANCE.requestStop();
            }
            if (savedFov > 0) {
                mc.options.fov().set(savedFov);
            }
            if (savedConfig != null) {
                DroneRenderConfig.CONFIG.set(savedConfig);
            }
            if (mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
            Screenshots.hideMarker(false);
            RescueController.INSTANCE.clearHome();
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                discardDrone(server);
                ServerPlayer player = player(server);
                player.getInventory().setItem(0, ItemStack.EMPTY);
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            }));
            return StepResult.pending();
        }
        StepResult server = awaitServer(ctx, "Aufräumen");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        if (PilotSession.INSTANCE.isActive()) {
            return StepResult.pending();
        }
        return Screenshots.restoreWindow(ctx);
    }

    private static void discardDrone(MinecraftServer server) {
        int id = DRONE.getAndSet(-1);
        if (id < 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof QuadcopterEntity drone) {
                if (drone.pilot().isPresent()) {
                    DronePiloting.release(drone);
                }
                drone.discard();
            }
        }
    }

    private static void start(MinecraftServer server, DroneBuild build, boolean fpv) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        discardDrone(server);
        BlockPos base = base();
        double distance = fpv ? 1.5 : SIDE_DISTANCE;
        player.teleportTo(level, base.getX() + 0.5, base.getY(), base.getZ() + 1.5 - distance, Set.of(), 0.0F, 0.0F, true);
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), build);
        drone.loadFromStack(stack);
        if (fpv) {
            drone.setTune(new FlightTune(CameraMount.withUptilt(drone.tune().values(), build, 0.0F)));
        }
        Vec3 pos = new Vec3(base.getX() + 0.5, base.getY() + 0.1, base.getZ() + 1.5);
        float yaw = fpv ? 0.0F : 150.0F;
        drone.snapTo(pos.x, pos.y, pos.z, yaw, 0.0F);
        level.addFreshEntity(drone);
        drone.resetTo(pos, new Quaternionf().rotationY((float) Math.toRadians(-yaw) + (float) Math.PI));
        DRONE.set(drone.getId());
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.getInventory().setItem(0, remote);
        player.getInventory().setSelectedSlot(0);
        player.connection.send(new ClientboundSetHeldSlotPacket(0));
        ItemStack goggles = ItemStack.EMPTY;
        if (fpv) {
            goggles = ContentIds.item(build.video().system == VideoSystem.DIGITAL ? ContentIds.GOGGLES_DIGITAL : ContentIds.GOGGLES_ANALOG)
                    .map(ItemStack::new).orElse(ItemStack.EMPTY);
            goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        }
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, fpv ? PilotMode.FPV : PilotMode.LINE_OF_SIGHT);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("Start verweigert: " + result);
        }
    }

    private static float holdAltitude(SelfTest.StepContext ctx, PilotSession session) {
        Map<String, Object> state = ctx.state();
        double y = session.comPosition(new Vector3d()).y;
        double target = (double) state.getOrDefault(TARGET_Y, y);
        double last = (double) state.getOrDefault(LAST_Y, y);
        double integral = (double) state.getOrDefault(INTEGRAL, 0.0);
        double vy = (y - last) * 20.0;
        double error = target - y;
        integral = Math.max(-0.2, Math.min(0.2, integral + error * 0.004));
        state.put(LAST_Y, y);
        state.put(INTEGRAL, integral);
        return (float) Math.max(0.05, Math.min(0.7, 0.28 + integral + error * 0.12 - vy * 0.08));
    }

    private static void look(LocalPlayer player, double x, double y, double z) {
        Vec3 eye = player.getEyePosition();
        double dx = x - eye.x;
        double dy = y - eye.y;
        double dz = z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.yHeadRot = yaw;
        player.yHeadRotO = yaw;
    }

    private static final class HoverScene implements Screenshots.Scene {
        private final Supplier<DroneBuild> build;
        private final boolean fpv;
        private final boolean blur;

        private HoverScene(Supplier<DroneBuild> build, boolean fpv, boolean blur) {
            this.build = build;
            this.fpv = fpv;
            this.blur = blur;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            PilotSession session = PilotSession.INSTANCE;
            if (!state.containsKey(FUTURE)) {
                session.clearTestInput();
                if (session.isActive()) {
                    session.requestStop();
                    return StepResult.pending();
                }
                if (ctx.tick() < STOP_SETTLE_TICKS) {
                    return StepResult.pending();
                }
                DroneRenderConfig config = (savedConfig != null ? savedConfig : DroneRenderConfig.get()).copy();
                config.fpvProps = true;
                config.fpvDucts = true;
                config.propBlurThreshold = this.blur ? THRESHOLD : 1.0F;
                DroneRenderConfig.CONFIG.set(config);
                DroneBuild shown = this.build.get();
                state.put(FUTURE, ctx.onServer(server -> start(server, shown, this.fpv)));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Start");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            PilotMode mode = this.fpv ? PilotMode.FPV : PilotMode.LINE_OF_SIGHT;
            if (!session.isActive() || session.mode() != mode) {
                return ctx.tick() > 200 ? StepResult.fail("keine Sitzung " + mode) : StepResult.pending();
            }
            DroneSim sim = session.sim();
            if (sim == null) {
                return StepResult.fail("keine Sitzung");
            }
            int since = (int) state.computeIfAbsent(SINCE, key -> ctx.tick());
            int elapsed = ctx.tick() - since;
            if (elapsed < 20 || !sim.telemetry().armed) {
                session.setTestInput(StickPositions.IDLE, sim.telemetry().armed || (elapsed >= 10 && sim.telemetry().armBlockers == 0 && ctx.tick() % 10 >= 5),
                        StabMode.ANGLE);
                return elapsed > 120 ? StepResult.fail("nicht scharf: " + sim.telemetry().armBlockers) : StepResult.pending();
            }
            double target = base().getY() + (this.fpv ? HOVER_HEIGHT : SIDE_HEIGHT);
            state.put(TARGET_Y, target);
            this.hold(ctx);
            double y = session.comPosition(new Vector3d()).y;
            if (Math.abs(y - target) < 0.2 && Math.abs((double) state.getOrDefault(LAST_Y, y) - y) < 0.01 && elapsed > 80) {
                if (!this.fpv) {
                    mc.options.fov().set(SIDE_FOV);
                    if (!mc.gui.hud.isHidden()) {
                        mc.gui.hud.toggle();
                    }
                }
                return StepResult.pass();
            }
            return elapsed > 600 ? StepResult.fail("Höhe nicht erreicht: " + y + " statt " + target) : StepResult.pending();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            PilotSession session = PilotSession.INSTANCE;
            float throttle = holdAltitude(ctx, session);
            session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), true, StabMode.ANGLE);
            Minecraft mc = Minecraft.getInstance();
            if (!this.fpv && mc.player != null && session.isActive()) {
                Vector3d drone = session.comPosition(new Vector3d());
                look(mc.player, drone.x, drone.y, drone.z);
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (savedFov > 0) {
                mc.options.fov().set(savedFov);
            }
            if (mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
            if (savedConfig != null) {
                DroneRenderConfig.CONFIG.set(savedConfig);
            }
            PilotSession.INSTANCE.clearTestInput();
            if (PilotSession.INSTANCE.isActive()) {
                PilotSession.INSTANCE.requestStop();
            }
            RescueController.INSTANCE.clearHome();
            ctx.onServer(CovPropBlurCenterSelfTests::discardDrone);
        }
    }
}
