package de.lardtom.propwash.interact.client;

import de.lardtom.propwash.accessory.AccessoryBehaviors;
import de.lardtom.propwash.accessory.display.ItemDisplays;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.accessory.ItemDisplay;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.example.ExampleDispenser;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.TuneMapping;
import de.lardtom.propwash.drone.client.EyeClearance;
import de.lardtom.propwash.drone.client.FpvEyeGuard;
import de.lardtom.propwash.drone.client.FpvJoltProbe;
import de.lardtom.propwash.drone.client.FpvShake;
import de.lardtom.propwash.drone.client.FpvView;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.drone.client.render.AccessoryDisplayState;
import de.lardtom.propwash.drone.client.render.DroneAssembly;
import de.lardtom.propwash.drone.client.render.DroneRenderConfig;
import de.lardtom.propwash.drone.client.render.DroneRenderResources;
import de.lardtom.propwash.drone.client.render.DroneRenderState;
import de.lardtom.propwash.drone.client.render.FpvProps;
import de.lardtom.propwash.video.ImageArea;
import de.lardtom.propwash.video.client.CleanFrameTap;
import de.lardtom.propwash.video.client.FpvImage;
import de.lardtom.propwash.video.client.VideoState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

final class AccessoryFpvSelfTests {
    static final double CHANGED = 0.001;
    static final double NOISE_FACTOR = 3.0;
    static final int CHANNEL = 24;
    static final int SETTLE_TICKS = 20;
    static final int AGAIN_TICKS = 5;
    static final int WAIT_TICKS = 300;
    static final double CONTROL_BOX = 0.5;
    static final String CONTROL_TAG = "propwash_fpv_spear_control";
    static final int OFFSET_X = -40;
    static final int OFFSET_Z = 60;
    static final int ROOM_AHEAD = 3;
    static final int ROOM_BACK = 6;
    static final int ROOM_HALF_WIDTH = 4;
    static final int ROOM_HEIGHT = 5;
    static final int ROOM_DEPTH = 3;
    static final double PILOT_BACK = 4.0;
    static final float FREE_UPTILT = 0.0F;
    static final double FREE_NEAR = EyeClearance.VANILLA_NEAR - 1.0E-4;
    static final double CLOSED_RATIO = 0.9;
    static final int CLEANUP = 15;
    static final int FREE_CLEANUP = 17;
    static final int JOLT_CLEANUP = 6;
    static final double JOLT_SPEED = 10.0;
    static final double JOLT_MIN_DEG = 0.5;
    static final int JOLT_PAUSE = 20;
    static final int JOLT_TRIES = 8;
    private static final String PHASE = "phase";
    private static final String SINCE = "since";
    private static final String FUTURE = "future";
    private static final String FRAME = "frame";
    private static final String CONFIG = "config";
    private static final String HUD = "hud";
    private static final String VERDICT = "verdict";
    private static final String WITH = "with";
    private static final String BARE_WITH = "bare.with";
    private static final String BARE_WITHOUT = "bare.without";
    private static final String WITHOUT = "without";
    private static final String AGAIN = "again";
    private static final String CONTROL = "control";
    private static final String PLACEMENT = "placement";
    private static final String SHOWN = "shown";
    private static final String END = "end";
    private static final String NEAR = "near.";
    private static final String SCALE = "scale.";
    private static final String STILL = "still";
    private static final String STILL_AGAIN = "still.again";
    private static final String JOLTED = "jolted";
    private static final String JOLT = "jolt";
    private static final String TRIES = "tries";
    private static final String TURN = "turn";
    private static final String REF_WITH = "ref.with";
    private static final String REF_WITHOUT = "ref.without";
    private static final String FREE_WITH = "free.with";
    private static final String FREE_BARE_WITH = "free.bare.with";
    private static final String FREE_BARE_WITHOUT = "free.bare.without";
    private static final String FREE_WITHOUT = "free.without";
    private static final String FREE_AGAIN = "free.again";
    private static final AtomicInteger DRONE = new AtomicInteger(-1);
    private static final AtomicReference<@Nullable BlockPos> BASE = new AtomicReference<>();
    private static final AtomicReference<@Nullable Vec3> RETURN = new AtomicReference<>();
    private static final AtomicReference<@Nullable GameType> MODE = new AtomicReference<>();

    private AccessoryFpvSelfTests() {
    }

    record Frame(int width, int height, int[] pixels, ImageArea area) {
    }

    record Placement(Vec3 center, Quaternionf rotation, float scale, String detail) {
    }

    record Change(double fraction, int minX, int minY, int maxX, int maxY) {
        String box(ImageArea area) {
            if (this.maxX < this.minX) {
                return "kein Bereich";
            }
            return String.format(Locale.ROOT, "x %.0f–%.0f %%, y %.0f–%.0f %%", 100.0 * this.minX / area.width(), 100.0 * (this.maxX + 1) / area.width(),
                    100.0 * this.minY / area.height(), 100.0 * (this.maxY + 1) / area.height());
        }
    }

    static void register(String group) {
        SelfTest.clientStep(group + "Speer im eigenen FPV-Bild: 7″ mit Speer in einem dunklen Raum, mit Speer ändert sich das Bild, auch mit Props und "
                + "Ducts im FPV-Bild aus; ein Speer-Item an der Halterung zur Kontrolle auch", 2400, AccessoryFpvSelfTests::step);
        SelfTest.clientStep(group + "Speer im eigenen FPV-Bild frei in der Luft: 7″ mit Speer und Kamera 0°, über Glas bleibt die Nahebene 0,05, der "
                + "Speer ist so vollständig wie über Barrieren mit abgesenkter Nahebene, auch mit Props und Ducts im FPV-Bild aus", 2400,
                AccessoryFpvSelfTests::freeStep);
        SelfTest.clientStep(group + "Speer im eigenen FPV-Bild bleibt beim Ruck der Kamera fest im Bild wie die Props: Kamera-Vibration 1,0, Ruck bis 3°, "
                + "Bild mit Ruck gleich dem Bild ohne", 1200, AccessoryFpvSelfTests::joltStep);
    }

    static DroneBuild build() {
        return DroneBuild.LONG_RANGE.withAccessory(AccessoryGapsSelfTests.type(ExampleDispenser.SPEAR_HOLDER));
    }

    private static StepResult step(SelfTest.StepContext ctx) {
        if (!AccessoryGapsSelfTests.ready()) {
            return StepResult.pass();
        }
        Map<String, Object> state = ctx.state();
        PilotSession session = PilotSession.INSTANCE;
        int phase = (int) state.getOrDefault(PHASE, 0);
        switch (phase) {
            case 0 -> {
                if (session.isActive()) {
                    session.requestStop();
                    return StepResult.pending();
                }
                state.put(FUTURE, ctx.onServer(server -> rig(server, null)));
                return next(ctx);
            }
            case 1 -> {
                StepResult server = await(ctx, "FPV-Start");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                QuadcopterEntity drone = session.drone();
                if (!session.isFpv() || drone == null || drone.getId() != DRONE.get() || !hasSpear(drone)) {
                    return waited(ctx) > WAIT_TICKS ? abort(ctx, "keine FPV-Sitzung mit Speer") : StepResult.pending();
                }
                Minecraft mc = Minecraft.getInstance();
                if (!mc.gui.hud.isHidden()) {
                    mc.gui.hud.toggle();
                    state.put(HUD, true);
                }
                DroneRenderConfig saved = DroneRenderConfig.get().copy();
                state.put(CONFIG, saved);
                fpvParts(saved, true);
                return next(ctx);
            }
            case 2 -> {
                StepResult grabbed = grab(ctx, WITH, SETTLE_TICKS);
                QuadcopterEntity drone = session.drone();
                if (state.containsKey(WITH) && !state.containsKey(SHOWN) && drone != null) {
                    DroneRenderState shown = AccessoryGapsSelfTests.extract(drone);
                    state.put(SHOWN, "Render-Zustand im FPV: " + AccessoryGapsSelfTests.describe(shown));
                }
                return grabbed;
            }
            case 3 -> {
                fpvParts((DroneRenderConfig) state.get(CONFIG), false);
                return next(ctx);
            }
            case 4 -> {
                return grab(ctx, BARE_WITH, SETTLE_TICKS);
            }
            case 5 -> {
                state.put(FUTURE, ctx.onServer(server -> spearHolder(server).ifPresent(drone -> PropwashApi.accessories()
                        .inventory(drone, AccessorySlot.BOTTOM).orElseThrow().set(0, ItemStack.EMPTY))));
                return next(ctx);
            }
            case 6 -> {
                StepResult server = await(ctx, "Speer heraus");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                QuadcopterEntity drone = session.drone();
                if (drone == null || hasSpear(drone)) {
                    return waited(ctx) > WAIT_TICKS ? abort(ctx, "Speer bleibt am Client in der Halterung") : StepResult.pending();
                }
                return next(ctx);
            }
            case 7 -> {
                return grab(ctx, BARE_WITHOUT, SETTLE_TICKS);
            }
            case 8 -> {
                fpvParts((DroneRenderConfig) state.get(CONFIG), true);
                return next(ctx);
            }
            case 9 -> {
                return grab(ctx, WITHOUT, SETTLE_TICKS);
            }
            case 10 -> {
                return grab(ctx, AGAIN, AGAIN_TICKS);
            }
            case 11 -> {
                QuadcopterEntity drone = session.drone();
                Placement placement = drone == null ? null : placement(drone);
                if (placement == null) {
                    return abort(ctx, "Lage der Halterung im FPV-Bild unbekannt");
                }
                state.put(PLACEMENT, placement);
                state.put(FUTURE, ctx.onServer(server -> control(server, placement)));
                return next(ctx);
            }
            case 12 -> {
                StepResult server = await(ctx, "Kontrolle setzen");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                Minecraft mc = Minecraft.getInstance();
                Vec3 center = ((Placement) state.get(PLACEMENT)).center();
                if (mc.level == null || mc.level.getEntitiesOfClass(Display.ItemDisplay.class, new AABB(center, center).inflate(CONTROL_BOX)).isEmpty()) {
                    return waited(ctx) > WAIT_TICKS ? abort(ctx, "Speer-Item zur Kontrolle kam nicht beim Client an") : StepResult.pending();
                }
                return next(ctx);
            }
            case 13 -> {
                return grab(ctx, CONTROL, SETTLE_TICKS);
            }
            case 14 -> {
                state.put(VERDICT, verdict(ctx));
                return next(ctx);
            }
            case CLEANUP -> {
                restoreClient(state);
                if (session.isActive()) {
                    session.requestStop();
                    return waited(ctx) > WAIT_TICKS ? StepResult.fail("Sitzung endet nicht") : StepResult.pending();
                }
                state.put(FUTURE, ctx.onServer(AccessoryFpvSelfTests::cleanup));
                return next(ctx);
            }
            default -> {
                StepResult server = await(ctx, "Aufräumen");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                Object verdict = state.get(VERDICT);
                return verdict instanceof StepResult result ? result : StepResult.fail("kein Ergebnis");
            }
        }
    }

    private static StepResult freeStep(SelfTest.StepContext ctx) {
        if (!AccessoryGapsSelfTests.ready()) {
            return StepResult.pass();
        }
        Map<String, Object> state = ctx.state();
        PilotSession session = PilotSession.INSTANCE;
        int phase = (int) state.getOrDefault(PHASE, 0);
        switch (phase) {
            case 0 -> {
                state.put(END, FREE_CLEANUP);
                if (session.isActive()) {
                    session.requestStop();
                    return StepResult.pending();
                }
                state.put(FUTURE, ctx.onServer(server -> rig(server, FREE_UPTILT)));
                return next(ctx);
            }
            case 1 -> {
                StepResult server = await(ctx, "FPV-Start");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                QuadcopterEntity drone = session.drone();
                if (!session.isFpv() || drone == null || drone.getId() != DRONE.get() || !hasSpear(drone)) {
                    return waited(ctx) > WAIT_TICKS ? abort(ctx, "keine FPV-Sitzung mit Speer") : StepResult.pending();
                }
                if (Math.abs(session.cameraUptiltDeg() - FREE_UPTILT) > 0.01F) {
                    return waited(ctx) > WAIT_TICKS ? abort(ctx, "Kamera nicht auf " + FREE_UPTILT + "°: " + session.cameraUptiltDeg()) : StepResult.pending();
                }
                Minecraft mc = Minecraft.getInstance();
                if (!mc.gui.hud.isHidden()) {
                    mc.gui.hud.toggle();
                    state.put(HUD, true);
                }
                DroneRenderConfig saved = DroneRenderConfig.get().copy();
                state.put(CONFIG, saved);
                fpvParts(saved, true);
                return next(ctx);
            }
            case 2 -> {
                return grab(ctx, REF_WITH, SETTLE_TICKS);
            }
            case 3 -> {
                state.put(FUTURE, ctx.onServer(server -> spear(server, false)));
                return next(ctx);
            }
            case 4 -> {
                return spearArrived(ctx, false);
            }
            case 5 -> {
                return grab(ctx, REF_WITHOUT, SETTLE_TICKS);
            }
            case 6 -> {
                state.put(FUTURE, ctx.onServer(server -> {
                    floor(server, Blocks.TINTED_GLASS.defaultBlockState());
                    spear(server, true);
                }));
                return next(ctx);
            }
            case 7 -> {
                return spearArrived(ctx, true);
            }
            case 8 -> {
                return grab(ctx, FREE_WITH, SETTLE_TICKS);
            }
            case 9 -> {
                fpvParts((DroneRenderConfig) state.get(CONFIG), false);
                return next(ctx);
            }
            case 10 -> {
                return grab(ctx, FREE_BARE_WITH, SETTLE_TICKS);
            }
            case 11 -> {
                state.put(FUTURE, ctx.onServer(server -> spear(server, false)));
                return next(ctx);
            }
            case 12 -> {
                return spearArrived(ctx, false);
            }
            case 13 -> {
                return grab(ctx, FREE_BARE_WITHOUT, SETTLE_TICKS);
            }
            case 14 -> {
                fpvParts((DroneRenderConfig) state.get(CONFIG), true);
                return next(ctx);
            }
            case 15 -> {
                return grab(ctx, FREE_WITHOUT, SETTLE_TICKS);
            }
            case 16 -> {
                StepResult grabbed = grab(ctx, FREE_AGAIN, AGAIN_TICKS);
                if (state.containsKey(FREE_AGAIN)) {
                    state.put(VERDICT, freeVerdict(ctx));
                }
                return grabbed;
            }
            case FREE_CLEANUP -> {
                restoreClient(state);
                if (session.isActive()) {
                    session.requestStop();
                    return waited(ctx) > WAIT_TICKS ? StepResult.fail("Sitzung endet nicht") : StepResult.pending();
                }
                state.put(FUTURE, ctx.onServer(AccessoryFpvSelfTests::cleanup));
                return next(ctx);
            }
            default -> {
                StepResult server = await(ctx, "Aufräumen");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                Object verdict = state.get(VERDICT);
                return verdict instanceof StepResult result ? result : StepResult.fail("kein Ergebnis");
            }
        }
    }

    private static StepResult joltStep(SelfTest.StepContext ctx) {
        if (!AccessoryGapsSelfTests.ready()) {
            return StepResult.pass();
        }
        Map<String, Object> state = ctx.state();
        PilotSession session = PilotSession.INSTANCE;
        int phase = (int) state.getOrDefault(PHASE, 0);
        switch (phase) {
            case 0 -> {
                state.put(END, JOLT_CLEANUP);
                if (session.isActive()) {
                    session.requestStop();
                    return StepResult.pending();
                }
                state.put(FUTURE, ctx.onServer(server -> rig(server, null)));
                return next(ctx);
            }
            case 1 -> {
                StepResult server = await(ctx, "FPV-Start");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                QuadcopterEntity drone = session.drone();
                if (!session.isFpv() || drone == null || drone.getId() != DRONE.get() || !hasSpear(drone)) {
                    return waited(ctx) > WAIT_TICKS ? abort(ctx, "keine FPV-Sitzung mit Speer") : StepResult.pending();
                }
                Minecraft mc = Minecraft.getInstance();
                if (!mc.gui.hud.isHidden()) {
                    mc.gui.hud.toggle();
                    state.put(HUD, true);
                }
                DroneRenderConfig saved = DroneRenderConfig.get().copy();
                state.put(CONFIG, saved);
                fpvParts(saved, true);
                FpvJoltProbe.intensity(1.0F);
                state.put(JOLT, true);
                return next(ctx);
            }
            case 2 -> {
                return grab(ctx, STILL, SETTLE_TICKS);
            }
            case 3 -> {
                return grab(ctx, STILL_AGAIN, AGAIN_TICKS);
            }
            case 4 -> {
                return jolted(ctx);
            }
            case 5 -> {
                state.put(VERDICT, joltVerdict(ctx));
                return next(ctx);
            }
            case JOLT_CLEANUP -> {
                restoreClient(state);
                if (session.isActive()) {
                    session.requestStop();
                    return waited(ctx) > WAIT_TICKS ? StepResult.fail("Sitzung endet nicht") : StepResult.pending();
                }
                state.put(FUTURE, ctx.onServer(AccessoryFpvSelfTests::cleanup));
                return next(ctx);
            }
            default -> {
                StepResult server = await(ctx, "Aufräumen");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                Object verdict = state.get(VERDICT);
                return verdict instanceof StepResult result ? result : StepResult.fail("kein Ergebnis");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static StepResult jolted(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        AtomicReference<Object> frame = (AtomicReference<Object>) state.get(FRAME);
        if (frame == null) {
            if (waited(ctx) < JOLT_PAUSE) {
                return StepResult.pending();
            }
            int tries = (int) state.getOrDefault(TRIES, 0);
            if (tries >= JOLT_TRIES) {
                return abort(ctx, "Ruck dreht die Kamera nicht: höchstens " + String.format(Locale.ROOT, "%.2f°", (double) state.getOrDefault(TURN, 0.0)));
            }
            state.put(TRIES, tries + 1);
            AtomicReference<Object> pending = new AtomicReference<>();
            state.put(FRAME, pending);
            FpvShake.INSTANCE.jolt().hit(VideoState.INSTANCE.lastSampleNanos(), JOLT_SPEED, 7L + tries);
            CleanFrameTap.request(target -> {
                ImageArea area = FpvImage.area(target.width, target.height);
                double turn = FpvJoltProbe.deviation();
                Screenshot.takeScreenshot(target, 1, image -> {
                    try (image) {
                        pending.set(new Object[]{new Frame(image.getWidth(), image.getHeight(), image.getPixels(), area), turn});
                    } catch (RuntimeException e) {
                        pending.set(e);
                    }
                });
            });
            return StepResult.pending();
        }
        Object value = frame.get();
        if (value == null) {
            return waited(ctx) > JOLT_PAUSE + WAIT_TICKS ? abort(ctx, "kein Bild mit Ruck") : StepResult.pending();
        }
        if (value instanceof RuntimeException e) {
            return abort(ctx, "Bild mit Ruck: " + e);
        }
        Object[] shot = (Object[]) value;
        double turn = (double) shot[1];
        state.put(TURN, Math.max(turn, (double) state.getOrDefault(TURN, 0.0)));
        state.remove(FRAME);
        if (turn < JOLT_MIN_DEG) {
            state.put(SINCE, ctx.tick());
            return StepResult.pending();
        }
        state.put(JOLTED, shot[0]);
        state.put(TURN, turn);
        return next(ctx);
    }

    private static StepResult joltVerdict(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        Frame still = (Frame) state.get(STILL);
        Change moved = changed(still, (Frame) state.get(JOLTED));
        Change noise = changed(still, (Frame) state.get(STILL_AGAIN));
        double limit = Math.max(CHANGED, NOISE_FACTOR * noise.fraction());
        ImageArea area = still.area();
        String summary = String.format(Locale.ROOT, "Bild %dx%d: Kamera beim Ruck um %.2f° gedreht (Versuch %d), Bild mit Ruck %.2f %% anders (%s), Rauschen "
                        + "%.2f %%, Grenze %.2f %%", area.width(), area.height(), (double) state.get(TURN), (int) state.get(TRIES), 100.0 * moved.fraction(),
                moved.box(area), 100.0 * noise.fraction(), 100.0 * limit);
        ctx.log(summary);
        if (moved.fraction() >= limit) {
            return StepResult.fail("Speer bewegt sich beim Ruck der Kamera gegen das Bild | " + summary);
        }
        return StepResult.pass();
    }

    private static StepResult spearArrived(SelfTest.StepContext ctx, boolean in) {
        StepResult server = await(ctx, in ? "Speer hinein" : "Speer heraus");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        QuadcopterEntity drone = PilotSession.INSTANCE.drone();
        if (drone == null || hasSpear(drone) != in) {
            return waited(ctx) > WAIT_TICKS ? abort(ctx, in ? "Speer kommt am Client nicht an" : "Speer bleibt am Client in der Halterung") : StepResult.pending();
        }
        return next(ctx);
    }

    private static StepResult freeVerdict(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        Change ref = changed((Frame) state.get(REF_WITH), (Frame) state.get(REF_WITHOUT));
        Change free = changed((Frame) state.get(FREE_WITH), (Frame) state.get(FREE_WITHOUT));
        Change bare = changed((Frame) state.get(FREE_BARE_WITH), (Frame) state.get(FREE_BARE_WITHOUT));
        Change noise = changed((Frame) state.get(FREE_WITHOUT), (Frame) state.get(FREE_AGAIN));
        double limit = Math.max(CHANGED, NOISE_FACTOR * noise.fraction());
        float refNear = (float) state.getOrDefault(NEAR + REF_WITH, Float.NaN);
        float freeNear = Math.min((float) state.getOrDefault(NEAR + FREE_WITH, Float.NaN), (float) state.getOrDefault(NEAR + FREE_BARE_WITH, Float.NaN));
        ImageArea area = ((Frame) state.get(REF_WITH)).area();
        float scale = (float) state.getOrDefault(SCALE + FREE_WITH, Float.NaN);
        String summary = String.format(Locale.ROOT, "Bild %dx%d, Kamera %.0f°: über Barrieren (Nahebene %.4f) mit Speer %.2f %% anders (%s), über Glas "
                        + "(Nahebene %.4f) %.2f %% (%s), mit Props und Ducts aus %.2f %% (%s), Rauschen %.2f %%, Grenze %.2f %%, verlangt %.0f %% des Werts über "
                        + "Barrieren; Ausgleich an der Linse ×%.2f", area.width(), area.height(), FREE_UPTILT, refNear, 100.0 * ref.fraction(), ref.box(area),
                freeNear, 100.0 * free.fraction(), free.box(area), 100.0 * bare.fraction(), bare.box(area), 100.0 * noise.fraction(), 100.0 * limit,
                100.0 * CLOSED_RATIO, scale);
        ctx.log(summary);
        if (!(refNear < FREE_NEAR)) {
            return StepResult.fail("Messung ungültig: über Barrieren ist die Nahebene nicht abgesenkt | " + summary);
        }
        if (!(freeNear >= FREE_NEAR)) {
            return StepResult.fail("Messung ungültig: über Glas ist die Nahebene nicht 0,05 | " + summary);
        }
        if (ref.fraction() < limit) {
            return StepResult.fail("Messung ungültig: über Barrieren ist kein Speer im Bild | " + summary);
        }
        if (free.fraction() < CLOSED_RATIO * ref.fraction()) {
            return StepResult.fail("Speer frei in der Luft abgeschnitten | " + summary);
        }
        if (bare.fraction() < CLOSED_RATIO * ref.fraction()) {
            return StepResult.fail("Speer frei in der Luft mit Props und Ducts aus abgeschnitten | " + summary);
        }
        return StepResult.pass();
    }

    private static StepResult next(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        state.put(PHASE, (int) state.getOrDefault(PHASE, 0) + 1);
        state.put(SINCE, ctx.tick());
        state.remove(FRAME);
        return StepResult.pending();
    }

    private static int waited(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(SINCE, ctx.tick());
    }

    private static StepResult abort(SelfTest.StepContext ctx, String reason) {
        Map<String, Object> state = ctx.state();
        state.put(VERDICT, StepResult.fail(reason));
        state.put(PHASE, (int) state.getOrDefault(END, CLEANUP));
        state.put(SINCE, ctx.tick());
        return StepResult.pending();
    }

    private static StepResult await(SelfTest.StepContext ctx, String what) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null) {
            return StepResult.pass();
        }
        if (future.isCompletedExceptionally()) {
            ctx.state().remove(FUTURE);
            if ((int) ctx.state().getOrDefault(PHASE, 0) >= (int) ctx.state().getOrDefault(END, CLEANUP)) {
                return StepResult.fail(what + ": " + future.exceptionNow());
            }
            return abort(ctx, what + ": " + future.exceptionNow());
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        ctx.state().remove(FUTURE);
        return StepResult.pass();
    }

    private static void fpvParts(DroneRenderConfig saved, boolean on) {
        DroneRenderConfig config = saved.copy();
        config.fpvProps = on;
        config.fpvDucts = on;
        DroneRenderConfig.CONFIG.set(config);
    }

    private static void restoreClient(Map<String, Object> state) {
        if (state.remove(JOLT) != null) {
            FpvJoltProbe.intensity(-1.0F);
        }
        if (state.remove(CONFIG) instanceof DroneRenderConfig saved) {
            DroneRenderConfig.CONFIG.set(saved);
        }
        Minecraft mc = Minecraft.getInstance();
        if (state.remove(HUD) != null && mc.gui.hud.isHidden()) {
            mc.gui.hud.toggle();
        }
    }

    static boolean hasSpear(QuadcopterEntity drone) {
        return drone.accessoryContents().at(AccessoryMount.BOTTOM).stream().anyMatch(stack -> stack.is(Items.IRON_SPEAR));
    }

    @SuppressWarnings("unchecked")
    private static StepResult grab(SelfTest.StepContext ctx, String key, int settle) {
        Map<String, Object> state = ctx.state();
        if (waited(ctx) < settle) {
            return StepResult.pending();
        }
        AtomicReference<Object> frame = (AtomicReference<Object>) state.get(FRAME);
        if (frame == null) {
            AtomicReference<Object> pending = new AtomicReference<>();
            state.put(FRAME, pending);
            state.put(NEAR + key, FpvEyeGuard.INSTANCE.active() ? FpvEyeGuard.INSTANCE.near(Float.NaN) : Float.NaN);
            state.put(SCALE + key, AccessoryDisplayState.lastNearScale());
            CleanFrameTap.request(target -> {
                ImageArea area = FpvImage.area(target.width, target.height);
                Screenshot.takeScreenshot(target, 1, image -> {
                    try (image) {
                        pending.set(new Frame(image.getWidth(), image.getHeight(), image.getPixels(), area));
                    } catch (RuntimeException e) {
                        pending.set(e);
                    }
                });
            });
            return StepResult.pending();
        }
        Object value = frame.get();
        if (value == null) {
            return waited(ctx) > settle + WAIT_TICKS ? abort(ctx, "kein Bild für " + key) : StepResult.pending();
        }
        if (value instanceof RuntimeException e) {
            return abort(ctx, "Bild für " + key + ": " + e);
        }
        state.put(key, value);
        return next(ctx);
    }

    static Change changed(Frame a, Frame b) {
        if (a.width() != b.width() || a.height() != b.height()) {
            throw new IllegalStateException("Bildgröße geändert: " + a.width() + "x" + a.height() + " und " + b.width() + "x" + b.height());
        }
        ImageArea area = a.area();
        int total = 0;
        int differ = 0;
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < area.height(); y++) {
            for (int x = 0; x < area.width(); x++) {
                int i = (area.y() + y) * a.width() + area.x() + x;
                total++;
                if (differs(a.pixels()[i], b.pixels()[i])) {
                    differ++;
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        return new Change(total == 0 ? 0.0 : (double) differ / total, minX, minY, maxX, maxY);
    }

    static boolean differs(int a, int b) {
        for (int shift = 0; shift <= 16; shift += 8) {
            if (Math.abs(((a >>> shift) & 0xFF) - ((b >>> shift) & 0xFF)) > CHANNEL) {
                return true;
            }
        }
        return false;
    }

    private static StepResult verdict(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        Frame with = (Frame) state.get(WITH);
        Frame bareWith = (Frame) state.get(BARE_WITH);
        Frame bareWithout = (Frame) state.get(BARE_WITHOUT);
        Frame without = (Frame) state.get(WITHOUT);
        Frame again = (Frame) state.get(AGAIN);
        Frame control = (Frame) state.get(CONTROL);
        Placement placement = (Placement) state.get(PLACEMENT);
        Change noise = changed(without, again);
        Change spear = changed(with, without);
        Change bare = changed(bareWith, bareWithout);
        Change check = changed(control, again);
        double limit = Math.max(CHANGED, NOISE_FACTOR * noise.fraction());
        ImageArea area = with.area();
        String summary = String.format(Locale.ROOT, "Bild %dx%d: mit Speer %.2f %% anders (%s), mit Props und Ducts aus %.2f %% (%s), Kontrolle %.2f %% (%s), "
                        + "Rauschen %.2f %%, Grenze %.2f %%", area.width(), area.height(), 100.0 * spear.fraction(), spear.box(area), 100.0 * bare.fraction(),
                bare.box(area), 100.0 * check.fraction(), check.box(area), 100.0 * noise.fraction(), 100.0 * limit) + "; " + placement.detail() + "; " + state.getOrDefault(SHOWN, "Render-Zustand im FPV unbekannt");
        ctx.log(summary);
        if (check.fraction() < limit) {
            return StepResult.fail("Messung ungültig: das Speer-Item an der Halterung ist nicht im Bild | " + summary);
        }
        if (spear.fraction() < limit) {
            return StepResult.fail("Speer im eigenen FPV-Bild nicht gezeichnet | " + summary);
        }
        if (bare.fraction() < limit) {
            return StepResult.fail("Speer im eigenen FPV-Bild fehlt, wenn Props und Ducts im FPV-Bild aus sind | " + summary);
        }
        return StepResult.pass();
    }

    static @Nullable Placement placement(QuadcopterEntity drone) {
        Vector3d com = new Vector3d();
        Quaternionf body = new Quaternionf();
        if (!FpvView.displayedBody(com, body)) {
            return null;
        }
        DroneBuild build = drone.build();
        DroneAssembly.Accessory holder = DroneRenderResources.INSTANCE.assembly(build).accessory(AccessoryMount.BOTTOM);
        if (!holder.present()) {
            return null;
        }
        Optional<ItemDisplay> display = ItemDisplays.display(ExampleDispenser.SPEAR_HOLDER, AccessoryBehaviors.INSTANCE.of(holder.type()), 0);
        if (display.isEmpty()) {
            return null;
        }
        Vector3fc anchor = holder.body().position();
        Vector3fc lens = FpvProps.lens(build);
        float toBlocks = drone.scale() / 1000.0F;
        Matrix4f model = ItemDisplays.model(anchor.x(), anchor.y(), anchor.z(), holder.tiltDegrees(), new Quaternionf(), display.get(), 1.0F, new Matrix4f());
        Matrix4f world = new Matrix4f().rotation(body).scale(toBlocks).translate(-lens.x(), -lens.y(), -lens.z()).mul(model);
        Vec3 eye = Minecraft.getInstance().gameRenderer.mainCamera().position();
        Vector3f center = world.transformPosition(new Vector3f());
        Quaternionf rotation = new Quaternionf(body).mul(model.getUnnormalizedRotation(new Quaternionf()).normalize()).rotateY((float) -Math.PI).normalize();
        float scale = toBlocks * display.get().scale() * ItemDisplays.ITEM_SIZE_MM;
        Vector3f mid = model.transformPosition(new Vector3f());
        String detail = String.format(Locale.ROOT, "Linse %.0f/%.0f/%.0f mm, Anbaupunkt %.0f/%.0f/%.0f mm, Mitte des Speers %.0f/%.0f/%.0f mm", lens.x(), lens.y(),
                lens.z(), anchor.x(), anchor.y(), anchor.z(), mid.x, mid.y, mid.z);
        return new Placement(new Vec3(eye.x + center.x, eye.y + center.y, eye.z + center.z), rotation, scale, detail);
    }

    private static Optional<QuadcopterEntity> spearHolder(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(DRONE.get()) instanceof QuadcopterEntity drone) {
                return Optional.of(drone);
            }
        }
        return Optional.empty();
    }

    private static void rig(MinecraftServer server, @Nullable Float uptilt) {
        cleanup(server);
        ServerPlayer player = InteractSelfTests.player(server);
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        ServerLevel level = player.level();
        int x = player.getBlockX() + OFFSET_X;
        int z = player.getBlockZ() + OFFSET_Z;
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 1;
        BlockPos base = new BlockPos(x, y, z);
        BASE.set(base);
        RETURN.set(player.position());
        room(level, base, true);
        MODE.compareAndSet(null, player.gameMode());
        player.setGameMode(GameType.CREATIVE);
        Vec3 pos = Vec3.atBottomCenterOf(base);
        player.teleportTo(level, pos.x, pos.y, pos.z - PILOT_BACK, Set.of(), 0.0F, 10.0F, true);
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        DroneBuild build = build();
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), build);
        stack.set(CoreComponents.BATTERY_CHARGE.get(), 1.0F);
        drone.loadFromStack(stack);
        if (uptilt != null) {
            drone.setTune(new FlightTune(Map.of(TuneMapping.CAMERA_UPTILT, uptilt)));
        }
        drone.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        level.addFreshEntity(drone);
        drone.resetTo(pos, new Quaternionf().rotationY((float) Math.PI));
        DRONE.set(drone.getId());
        PropwashApi.accessories().inventory(drone, AccessorySlot.BOTTOM).orElseThrow(() -> new IllegalStateException("Speer-Halterung ohne Inventar"))
                .set(0, new ItemStack(Items.IRON_SPEAR));
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.getInventory().setItem(0, remote);
        player.getInventory().setSelectedSlot(0);
        player.connection.send(new ClientboundSetHeldSlotPacket(0));
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_DIGITAL).map(ItemStack::new).orElse(ItemStack.EMPTY);
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("FPV-Start verweigert: " + result);
        }
    }

    private static void spear(MinecraftServer server, boolean in) {
        QuadcopterEntity drone = spearHolder(server).orElseThrow(() -> new IllegalStateException("Drohne fehlt"));
        PropwashApi.accessories().inventory(drone, AccessorySlot.BOTTOM).orElseThrow().set(0, in ? new ItemStack(Items.IRON_SPEAR) : ItemStack.EMPTY);
    }

    private static void floor(MinecraftServer server, BlockState block) {
        BlockPos base = BASE.get();
        if (base == null) {
            throw new IllegalStateException("kein Raum");
        }
        ServerLevel level = InteractSelfTests.player(server).level();
        for (int dx = -ROOM_HALF_WIDTH; dx <= ROOM_HALF_WIDTH; dx++) {
            for (int dz = -ROOM_BACK; dz < ROOM_AHEAD; dz++) {
                level.setBlockAndUpdate(base.offset(dx, -1, dz), block);
            }
        }
    }

    private static void room(ServerLevel level, BlockPos base, boolean build) {
        int side = ROOM_HALF_WIDTH + 1;
        int floor = -ROOM_DEPTH - 1;
        for (int dx = -side; dx <= side; dx++) {
            for (int dz = -ROOM_BACK; dz <= ROOM_AHEAD; dz++) {
                for (int dy = floor; dy <= ROOM_HEIGHT; dy++) {
                    boolean wall = Math.abs(dx) == side || dz == ROOM_AHEAD || dy == floor || dy == ROOM_HEIGHT;
                    boolean light = !wall && dy == ROOM_HEIGHT - 1 && Math.abs(dx) == ROOM_HALF_WIDTH && (dz == -ROOM_BACK || dz == ROOM_AHEAD - 1);
                    BlockState block;
                    if (!build) {
                        block = dy < -1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    } else if (wall) {
                        block = Blocks.CONCRETE.pick(DyeColor.BLACK).defaultBlockState();
                    } else if (dy == -1) {
                        block = Blocks.BARRIER.defaultBlockState();
                    } else if (light) {
                        block = Blocks.LIGHT.defaultBlockState();
                    } else {
                        block = Blocks.AIR.defaultBlockState();
                    }
                    level.setBlockAndUpdate(base.offset(dx, dy, dz), block);
                }
            }
        }
    }

    private static void control(MinecraftServer server, Placement placement) {
        Vec3 at = placement.center();
        Quaternionf q = placement.rotation();
        float k = placement.scale();
        String command = String.format(Locale.ROOT, "summon minecraft:item_display %.6f %.6f %.6f {Tags:[\"%s\"],item:{id:\"minecraft:iron_spear\",count:1},"
                        + "item_display:\"fixed\",brightness:{sky:15,block:15},width:%.2ff,height:%.2ff,transformation:{translation:[0f,0f,0f],left_rotation:[%.6ff,%.6ff,%.6ff,%.6ff],"
                        + "scale:[%.6ff,%.6ff,%.6ff],right_rotation:[0f,0f,0f,1f]}}", at.x, at.y, at.z, CONTROL_TAG, CONTROL_BOX, CONTROL_BOX, q.x, q.y, q.z, q.w,
                k, k, k);
        command(server, command);
    }

    private static void command(MinecraftServer server, String command) {
        AtomicInteger result = new AtomicInteger(-1);
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput()
                .withCallback((success, value) -> result.set(success ? value : 0)), command);
        if (result.get() <= 0) {
            throw new IllegalStateException("Befehl fehlgeschlagen: " + command);
        }
    }

    private static void cleanup(MinecraftServer server) {
        ServerPlayer player = InteractSelfTests.player(server);
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                "kill @e[type=minecraft:item_display,tag=" + CONTROL_TAG + "]");
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(DRONE.get()) instanceof QuadcopterEntity drone) {
                if (drone.pilot().isPresent()) {
                    DronePiloting.release(drone);
                }
                drone.discard();
            }
        }
        DRONE.set(-1);
        BlockPos base = BASE.getAndSet(null);
        if (base != null) {
            room(player.level(), base, false);
        }
        Vec3 back = RETURN.getAndSet(null);
        if (back != null) {
            player.teleportTo(player.level(), back.x, back.y, back.z, Set.of(), player.getYRot(), player.getXRot(), true);
        }
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        player.getInventory().setItem(0, ItemStack.EMPTY);
        GameType mode = MODE.getAndSet(null);
        if (mode != null) {
            player.setGameMode(mode);
        }
    }
}
