package de.lardtom.propwash.video.client.record;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.compat.flashback.client.FlashbackBridge;
import de.lardtom.propwash.compat.flashback.client.FlashbackProbe;
import de.lardtom.propwash.compat.flashback.client.ReplayView;
import de.lardtom.propwash.content.BuildSlots;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.content.DroneAssembly;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.osd.OsdElement;
import de.lardtom.propwash.osd.client.OsdHud;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.ui.client.DroneContext;
import de.lardtom.propwash.ui.client.config.ConfigTabHost;
import de.lardtom.propwash.ui.client.theme.ThemedToast;
import de.lardtom.propwash.video.record.path.CameraAngles;
import de.lardtom.propwash.video.record.path.CameraPathBuffer;
import de.lardtom.propwash.video.record.path.CameraTrack;
import de.lardtom.propwash.video.record.path.KeyframeDensity;
import de.lardtom.propwash.video.record.path.PathResampler;
import de.lardtom.propwash.video.record.path.PathSample;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class FlashbackCamSelfTests {
    private static final double DRONE_DISTANCE = 6.0;
    private static final String FUTURE = "future";
    private static final String PHASE = "phase";
    private static final String REPLAY = "replay";
    private static final String HUD = "hud";
    private static final String MARK = "mark";
    private static final String POSE_WORST = "pose_worst";
    private static final String SINCE = "since";
    private static final String TARGET_Y = "target_y";
    private static final String LAST_Y = "last_y";
    private static final String INTEGRAL = "integral";
    private static final String FLIP_SINCE = "flip_since";
    private static final String UP = "up";
    private static final String SHOT = "shot";
    private static final String FPV_SHOT = "fpv_shot";
    private static final String SIZING = "sizing";
    private static final String FOV = "fov";
    private static final int FPV_SHOT_TICK = 4;
    private static final String FPV_TARGET = "fpv_target";
    private static final double PILOT_VIEW_DEGREES = 50.0;
    private static final double OVERVIEW_TICK = 30.0;
    private static final double CLIMB = 3.0;
    private static final double FLIP_CLIMB = 4.0;
    private static final String CLIMBED = "climbed";
    private static final int FLIP_TICKS = 14;
    private static final double EXACT_POSITION_LIMIT = 0.08;
    private static final double REDUCED_POSITION_LIMIT = 0.3;
    private static final double FLIP_POSITION_LIMIT = 0.4;
    private static final double SPATIAL_LIMIT = 0.03;
    private static final double ANGLE_LIMIT = 6.0;
    private static final int POSE_SETTLE_TICKS = 20;
    private static final int POSE_PROBE_TICKS = 20;
    private static final double POSE_POSITION_LIMIT = 0.15;
    private static final double POSE_ANGLE_LIMIT = 3.0;
    private static final double POSE_UPTILT_LIMIT = 3.0;
    private static final double POSE_FOV_LIMIT = 0.5;
    private static final double FLIP_ANGLE_LIMIT = 25.0;

    private static volatile int droneId = -1;
    private static volatile @Nullable ItemStack previousHead;
    private static volatile @Nullable ItemStack previousHand;
    private static @Nullable RecordingConfig savedConfig;
    private static @Nullable UUID recordedReplay;
    private static @Nullable CameraTrack recordedTrack;
    private static @Nullable CameraPathBuffer recordedPath;
    private static @Nullable UUID recordedDrone;
    private static volatile @Nullable Vec3 pilotEye;

    private FlashbackCamSelfTests() {
    }

    public static boolean flashbackPresent() {
        return Services.PLATFORM.isModLoaded(FlashbackBridge.MOD_ID);
    }

    public static void registerReplay() {
        if (!flashbackPresent()) {
            return;
        }
        if (Screenshots.enabled()) {
            SelfTest.afterLeaveStep("bildschirmfotos: Fenster für die Flashback-Wiederholung", 200, Screenshots::resizeWindow);
        }
        SelfTest.afterLeaveStep("video: Action-Cam (Flashback) – Flashback-Wiederholung folgt dem Kamerapfad", 2400,
                FlashbackCamSelfTests::replayStep);
        if (Screenshots.enabled()) {
            SelfTest.afterLeaveStep("bildschirmfotos: Fenster nach der Flashback-Wiederholung zurück", 20, Screenshots::restoreWindow);
        }
    }

    public static void register() {
        SelfTest.clientStep("video: Action-Cam (Flashback) als Bauteil, Tooltip und Tab", 20, FlashbackCamSelfTests::partStep);
        SelfTest.clientStep("video: Drohne mit Action-Cam (Flashback) im FPV", 400, FlashbackCamSelfTests::setupStep);
        SelfTest.clientStep("video: Tab Action-Cam passt zur Action-Cam (Flashback) der Drohne", 20, ctx -> flyingTabStep());
        if (flashbackPresent()) {
            SelfTest.clientStep("video: Action-Cam (Flashback) zeichnet den Flug als Kamerapfad für Flashback auf", 2400,
                    FlashbackCamSelfTests::recordStep);
            SelfTest.clientStep("video: Action-Cam (Flashback) hängt Flüge an eine laufende Flashback-Aufnahme an", 2400,
                    FlashbackCamSelfTests::appendStep);
        } else {
            SelfTest.clientStep("video: Action-Cam (Flashback) ohne Flashback zeigt nur einen Hinweis", 200, FlashbackCamSelfTests::absentStep);
        }
        SelfTest.clientStep("video: Action-Cam (Flashback) aufräumen", 200, FlashbackCamSelfTests::cleanupStep);
        SelfTest.afterLeaveStep("video: Action-Cam (Flashback) nach dem Verlassen im Leerlauf", 200, FlashbackCamSelfTests::afterLeaveStep);
    }

    private static StepResult partStep(SelfTest.StepContext ctx) {
        AccessoryType cam = AccessoryType.ACTION_CAM_FLASHBACK;
        if (!cam.fits(AccessoryMount.TOP) || cam.isCamera() || !cam.isFlashbackCam() || cam.flightBatteryWatts <= 0.0) {
            return StepResult.fail("Action-Cam (Flashback) falsch definiert: oben " + cam.fits(AccessoryMount.TOP) + ", Video " + cam.isCamera());
        }
        DroneBuild build = DroneBuild.FREESTYLE.withAccessory(cam);
        if (!build.problems().isEmpty() || build.accessories().flashbackCam().orElse(null) != cam || build.accessories().camera().isPresent()) {
            return StepResult.fail("Freestyle mit Action-Cam (Flashback) nicht baubar: " + build.problems());
        }
        if (DroneBuild.WHOOP.withAccessory(cam).problems().isEmpty()) {
            return StepResult.fail("Whoop ohne obere Halterung nimmt die Action-Cam (Flashback) an");
        }
        ItemStack item = DroneAssembly.disassemble(DroneAssembly.preset(build)).get(BuildSlots.accessorySlot(AccessoryMount.TOP));
        if (DroneAssembly.part(item).orElse(null) != cam) {
            return StepResult.fail("Zerlegen liefert keine Action-Cam (Flashback): " + item);
        }
        List<Component> lines = new ArrayList<>();
        item.getItem().appendHoverText(item, Item.TooltipContext.EMPTY, TooltipDisplay.DEFAULT, lines::add, TooltipFlag.NORMAL);
        String status = Component.translatable(flashbackPresent() ? "propwash.content.tooltip.flashback_found"
                : "propwash.content.tooltip.flashback_missing").getString();
        if (lines.stream().noneMatch(line -> line.getString().equals(status))) {
            return StepResult.fail("Tooltip ohne Flashback-Hinweis „" + status + "“: " + lines);
        }
        Language language = Language.getInstance();
        if (!language.has("propwash.help.action_cam_flashback.use.1") || !language.getOrDefault("propwash.help.action_cam_flashback.use.1").contains("Fabric")) {
            return StepResult.fail("Shift-Tooltip nennt Flashback (Fabric) nicht");
        }
        if (flashbackPresent() != FlashbackBridge.installed()) {
            return StepResult.fail("Flashback geladen " + flashbackPresent() + ", Brücke meint " + FlashbackBridge.status());
        }
        if (flashbackPresent() && !FlashbackBridge.ready()) {
            return StepResult.fail("Flashback " + FlashbackBridge.version().orElse("?") + " nicht angebunden: " + FlashbackBridge.problem());
        }
        List<AbstractWidget> widgets = new ArrayList<>();
        ActionCamTab tab = new ActionCamTab();
        tab.attach(host(widgets), 0, 0, 480, 280);
        String autoLabel = Component.translatable("propwash.video.record.tab.flashback_auto").getString();
        String densityLabel = Component.translatable("propwash.video.record.tab.flashback_density").getString();
        boolean shown = widgets.stream().anyMatch(widget -> widget.getMessage().getString().startsWith(autoLabel))
                && widgets.stream().anyMatch(widget -> widget.getMessage().getString().startsWith(densityLabel));
        if (shown != flashbackPresent()) {
            return StepResult.fail("Flashback-Abschnitt im Tab " + (shown ? "ohne" : "trotz") + " Flashback: " + widgets.size() + " Bedienelemente");
        }
        String hintPart = Component.translatable("propwash.video.record.tab.hint.flashback").getString();
        if (tab.hint().getString().contains(hintPart) != flashbackPresent()) {
            return StepResult.fail("Flashback-Hinweis im Tab falsch: " + tab.hint().getString());
        }
        ctx.log("Flashback " + (flashbackPresent() ? FlashbackBridge.version().orElse("?") + " angebunden" : "nicht installiert") + ", Tab mit "
                + widgets.size() + " Bedienelementen");
        return StepResult.pass();
    }

    private static StepResult flyingTabStep() {
        ActionCamTab tab = new ActionCamTab();
        tab.attach(host(new ArrayList<>()), 0, 0, 480, 280);
        if (!tab.flashbackDrone()) {
            return StepResult.fail("Tab erkennt die Action-Cam (Flashback) der geflogenen Drohne nicht");
        }
        String hint = tab.hint().getString();
        String flashbackHint = Component.translatable("propwash.video.record.tab.hint.flashback_cam", RecordingKeys.RECORD.getTranslatedKeyMessage(),
                Component.translatable(SwitchFunction.RECORD.translationKey())).getString();
        if (!hint.equals(flashbackHint)) {
            return StepResult.fail("Hinweis im Tab passt nicht zur Action-Cam (Flashback): " + hint);
        }
        Component name = DroneContext.resolve(Minecraft.getInstance()).name();
        String missing = Component.translatable("propwash.video.record.tab.status.flashback_missing", name).getString();
        String ready = Component.translatable("propwash.video.record.tab.status.flashback_cam", name, "\u0000").getString().split("\u0000", 2)[0];
        List<String> lines = tab.statusLines().stream().map(Component::getString).toList();
        boolean missingShown = lines.contains(missing);
        boolean readyShown = lines.stream().anyMatch(line -> line.startsWith(ready));
        if (missingShown == readyShown || readyShown != flashbackPresent()) {
            return StepResult.fail("Statuszeilen widersprechen sich (Flashback " + flashbackPresent() + "): " + lines);
        }
        return StepResult.pass();
    }

    static ConfigTabHost host(List<AbstractWidget> widgets) {
        return new ConfigTabHost() {
            @Override
            public <T extends AbstractWidget> T addWidget(T widget) {
                widgets.add(widget);
                return widget;
            }

            @Override
            public Font font() {
                return Minecraft.getInstance().font;
            }

            @Override
            public Minecraft minecraft() {
                return Minecraft.getInstance();
            }

            @Override
            public Screen screen() {
                return Minecraft.getInstance().gui.screen();
            }

            @Override
            public void rebuild() {
            }

            @Override
            public void changed() {
            }
        };
    }

    private static StepResult setupStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            savedConfig = RecordingConfig.get().copy();
            ctx.state().put(FUTURE, ctx.onServer(FlashbackCamSelfTests::spawnAndFly));
            return StepResult.pending();
        }
        CompletableFuture<?> future = future(ctx);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Aufbau fehlgeschlagen: " + future.exceptionNow().getMessage());
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        if (drone == null || drone.getId() != droneId || !session.isFpv() || minecraft.getCameraEntity() != drone) {
            return ctx.tick() > 300 ? StepResult.fail("keine FPV-Sitzung mit der Flashback-Drohne") : StepResult.pending();
        }
        if (!FlashbackCam.mounted(drone)) {
            return StepResult.fail("Drohne auf dem Client ohne Action-Cam (Flashback): " + drone.build().accessories());
        }
        return ctx.tick() > 20 ? StepResult.pass() : StepResult.pending();
    }

    private static StepResult absentStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            KeyMapping.click(InputConstants.getKey(RecordingKeys.RECORD.saveString()));
            return StepResult.pending();
        }
        if (ctx.tick() < 3) {
            return StepResult.pending();
        }
        FlashbackCam cam = FlashbackCam.INSTANCE;
        if (cam.lastResult() != FlashbackCam.Result.NEEDS_FLASHBACK) {
            return StepResult.fail("Aufnahme-Taste ohne Flashback: " + cam.lastResult());
        }
        if (cam.phase() != FlashbackCam.Phase.IDLE || ActionCamRecorder.INSTANCE.phase() != ActionCamRecorder.Phase.IDLE) {
            return StepResult.fail("ohne Flashback läuft trotzdem eine Aufnahme: " + cam.phase() + ", " + ActionCamRecorder.INSTANCE.phase());
        }
        StepResult toast = toastTitled(minecraft, "propwash.video.flashback.toast.needs_title");
        if (toast.status() == StepResult.Status.FAIL) {
            return toast;
        }
        ctx.log("Hinweis ohne Flashback: " + Component.translatable(FlashbackCam.Result.NEEDS_FLASHBACK.translationKey()).getString());
        return StepResult.pass();
    }

    @SuppressWarnings("unchecked")
    private static StepResult recordStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        FlashbackCam cam = FlashbackCam.INSTANCE;
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        try {
            switch (phase) {
                case 0 -> {
                    if (!state.containsKey(UP)) {
                        StepResult up = takeOff(ctx);
                        if (up.status() != StepResult.Status.PASS) {
                            return up;
                        }
                        state.put(UP, Boolean.TRUE);
                    }
                    if (FlashbackBridge.recording()) {
                        return StepResult.fail("Flashback nimmt vor dem Test schon auf");
                    }
                    FlashbackProbe.quicksave(true);
                    RecordingConfig config = RecordingConfig.get().copy();
                    config.flashbackAutoRecord = true;
                    config.flashbackDensity = KeyframeDensity.EVERY_TICK;
                    RecordingConfig.CONFIG.set(config);
                    KeyMapping.click(InputConstants.getKey(RecordingKeys.RECORD.saveString()));
                    state.put(PHASE, 1);
                    return StepResult.pending();
                }
                case 1 -> {
                    hover(ctx);
                    if (!cam.recording()) {
                        return ctx.tick() > 900 ? StepResult.fail("Aufnahme-Taste startet keinen Kamerapfad: " + cam.lastResult())
                                : StepResult.pending();
                    }
                    if (!FlashbackBridge.recording()) {
                        return StepResult.fail("Flashback-Aufnahme nicht mitgestartet");
                    }
                    StepResult toast = toastTitled(minecraft, "propwash.video.flashback.toast.started");
                    if (toast.status() == StepResult.Status.FAIL) {
                        return toast;
                    }
                    state.put(REPLAY, cam.replay());
                    state.put(HUD, OsdHud.renderedFrames());
                    state.put(MARK, ctx.tick());
                    state.put(POSE_WORST, new double[4]);
                    state.put(PHASE, 4);
                    return StepResult.pending();
                }
                case 4 -> {
                    hover(ctx);
                    int since = ctx.tick() - (int) state.get(MARK);
                    if (since < POSE_SETTLE_TICKS) {
                        return StepResult.pending();
                    }
                    double[] worst = (double[]) state.get(POSE_WORST);
                    String problem = comparePose(minecraft, cam, worst);
                    if (problem != null) {
                        return StepResult.fail(problem);
                    }
                    if (since < POSE_SETTLE_TICKS + POSE_PROBE_TICKS) {
                        return StepResult.pending();
                    }
                    if (worst[0] > POSE_POSITION_LIMIT || worst[1] > POSE_ANGLE_LIMIT || worst[2] > POSE_UPTILT_LIMIT || worst[3] > POSE_FOV_LIMIT) {
                        return StepResult.fail(String.format(Locale.ROOT, "Kamerapfad weicht von der FPV-Kamera ab: Position %.3f, Blickrichtung %.2f°, "
                                + "Uptilt gegen die Drohne %.2f°, Sichtfeld %.2f°", worst[0], worst[1], worst[2], worst[3]));
                    }
                    ctx.log(String.format(Locale.ROOT, "Kamerapfad gleich FPV-Kamera: Position %.3f, Blickrichtung %.2f°, Uptilt %.2f° (Soll %.1f°), "
                            + "Sichtfeld %.2f°", worst[0], worst[1], worst[2], PilotSession.INSTANCE.cameraUptiltDeg(), worst[3]));
                    state.put(PHASE, 2);
                    return StepResult.pending();
                }
                case 2 -> {
                    fly(ctx);
                    if (cam.recordedSeconds() < 3.5) {
                        return ctx.tick() > 1400 ? StepResult.fail("Kamerapfad wächst nicht: " + cam.samples() + " Proben, Flashback schreibt "
                                + FlashbackBridge.writing()) : StepResult.pending();
                    }
                    if (OsdHud.config().slot(OsdElement.RECORDING).enabled && OsdHud.renderedFrames() > (int) state.get(HUD)
                            && !OsdHud.drewLastFrame(OsdElement.RECORDING)) {
                        return StepResult.fail("REC-Anzeige fehlt im OSD während des Kamerapfads");
                    }
                    ctx.log("Kamerapfad " + String.format(Locale.ROOT, "%.2f", cam.recordedSeconds()) + " s, " + cam.samples()
                            + " Proben, Flashback-Ticks " + FlashbackProbe.writtenTicks());
                    KeyMapping.click(InputConstants.getKey(RecordingKeys.RECORD.saveString()));
                    state.put(MARK, ctx.tick());
                    state.put(PHASE, 3);
                    return StepResult.pending();
                }
                case 3 -> {
                    hover(ctx);
                    FlashbackCam.Handover handover = cam.lastHandover();
                    UUID replay = (UUID) state.get(REPLAY);
                    if (cam.recording() || cam.saving() || handover == null || !handover.replay().equals(replay)) {
                        return ctx.tick() - (int) state.get(MARK) > 400 ? StepResult.fail("Kamerapfad nicht übergeben: " + cam.lastError())
                                : StepResult.pending();
                    }
                    StepResult toast = toastTitled(minecraft, "propwash.video.flashback.toast.saved");
                    if (toast.status() == StepResult.Status.FAIL) {
                        return toast;
                    }
                    if (FlashbackBridge.recording()) {
                        return StepResult.fail("mitgestartete Flashback-Aufnahme läuft nach dem Kamerapfad weiter");
                    }
                    StepResult scene = checkScene(ctx, replay, handover, 1, true, ANGLE_LIMIT, EXACT_POSITION_LIMIT);
                    if (scene.status() == StepResult.Status.PASS) {
                        recordedReplay = replay;
                        recordedTrack = cam.lastTrack();
                        recordedPath = cam.lastPath();
                        QuadcopterEntity drone = PilotSession.INSTANCE.drone();
                        recordedDrone = drone == null ? null : drone.getUUID();
                    }
                    return scene;
                }
                default -> {
                    return StepResult.fail("unbekannte Phase " + phase);
                }
            }
        } catch (ReflectiveOperationException | IOException e) {
            return StepResult.fail("Flashback nicht lesbar: " + e);
        }
    }

    private static @Nullable String comparePose(Minecraft minecraft, FlashbackCam cam, double[] worst) {
        PathSample sample = cam.latestSample();
        QuadcopterEntity drone = PilotSession.INSTANCE.drone();
        if (sample == null || drone == null) {
            return "kein Kamerapfad oder keine Drohne beim Vergleich mit der FPV-Kamera";
        }
        Camera camera = minecraft.gameRenderer.mainCamera();
        Quaternionf rotation = new Quaternionf((float) sample.qx(), (float) sample.qy(), (float) sample.qz(), (float) sample.qw());
        Vector3f forward = rotation.transform(new Vector3f(0.0F, 0.0F, -1.0F));
        Vector3f up = rotation.transform(new Vector3f(0.0F, 1.0F, 0.0F));
        Vec3 position = camera.position();
        double dx = sample.x() - position.x;
        double dy = sample.y() - position.y;
        double dz = sample.z() - position.z;
        worst[0] = Math.max(worst[0], Math.sqrt(dx * dx + dy * dy + dz * dz));
        worst[1] = Math.max(worst[1], Math.max(degreesBetween(forward, camera.forwardVector()), degreesBetween(up, camera.upVector())));
        Quaternionf body = new Quaternionf(drone.orientation());
        Vector3f bodyForward = body.transform(new Vector3f(0.0F, 0.0F, -1.0F));
        Vector3f bodyUp = body.transform(new Vector3f(0.0F, 1.0F, 0.0F));
        double uptilt = PilotSession.INSTANCE.cameraUptiltDeg();
        double tilt = Math.toDegrees(Math.atan2(forward.dot(bodyUp), forward.dot(bodyForward)));
        worst[2] = Math.max(worst[2], Math.abs(tilt - uptilt));
        double windowAspect = (double) minecraft.getWindow().getWidth() / Math.max(1, minecraft.getWindow().getHeight());
        double sampleHorizontal = 2.0 * Math.toDegrees(Math.atan(Math.tan(Math.toRadians(sample.fov()) * 0.5) * 16.0 / 9.0));
        double cameraHorizontal = 2.0 * Math.toDegrees(Math.atan(Math.tan(Math.toRadians(camera.getFov()) * 0.5) * windowAspect));
        worst[3] = Math.max(worst[3], Math.abs(sampleHorizontal - cameraHorizontal));
        return null;
    }

    private static double degreesBetween(Vector3fc a, Vector3fc b) {
        double cos = a.dot(b) / Math.max(1.0E-9, a.length() * b.length());
        return Math.toDegrees(Math.acos(Math.clamp(cos, -1.0, 1.0)));
    }

    private static StepResult checkScene(SelfTest.StepContext ctx, UUID replay, FlashbackCam.Handover handover, int flight, boolean exact,
                                         double angleLimit, double positionLimit) throws ReflectiveOperationException, IOException {
        Path file = FlashbackProbe.editorState(replay);
        if (!Files.isRegularFile(file)) {
            return StepResult.fail("Flashback-Editorzustand fehlt: " + file);
        }
        FlashbackProbe.Editor editor = FlashbackProbe.load(file);
        if (editor == null) {
            return StepResult.fail("Flashback kann den Editorzustand nicht laden: " + file);
        }
        int scene = handover.sceneIndex();
        String expectedName = Component.translatable("propwash.video.flashback.scene", flight).getString();
        if (scene >= editor.scenes() || !editor.name(scene).equals(expectedName) || !handover.scene().equals(expectedName)) {
            return StepResult.fail("Szene „" + expectedName + "“ fehlt in Flashback: " + editor.scenes() + " Szenen, Index " + scene);
        }
        if (editor.trackCount(scene) != 2 || !editor.trackType(scene, 0).equals("CameraKeyframeType") || !editor.trackType(scene, 1).equals("FOVKeyframeType")) {
            return StepResult.fail("Szene ohne Kamera- und Sichtfeld-Spur: " + editor.trackCount(scene));
        }
        if (!editor.trackName(scene, 0).equals(Component.translatable("propwash.video.flashback.track.camera").getString())) {
            return StepResult.fail("Kameraspur falsch benannt: " + editor.trackName(scene, 0));
        }
        CameraTrack track = FlashbackCam.INSTANCE.lastTrack();
        CameraPathBuffer path = FlashbackCam.INSTANCE.lastPath();
        if (track == null || path == null) {
            return StepResult.fail("letzter Kamerapfad nicht gemerkt");
        }
        if (editor.keyframes(scene, 0) != track.camera().size() || editor.keyframes(scene, 1) != track.fov().size()
                || track.camera().size() != handover.keyframes()) {
            return StepResult.fail("Keyframes in Flashback " + editor.keyframes(scene, 0) + "/" + editor.keyframes(scene, 1) + ", geschrieben "
                    + track.camera().size() + "/" + track.fov().size());
        }
        int[] range = editor.exportRange(scene);
        if (range[0] != track.startTick() || range[1] != track.endTick()) {
            return StepResult.fail("Export-Bereich " + range[0] + "–" + range[1] + " statt " + track.startTick() + "–" + track.endTick());
        }
        double worstPosition = 0.0;
        double worstAngle = 0.0;
        double worstFov = 0.0;
        double worstSpatial = 0.0;
        double turned = 0.0;
        PathSample first = path.get(0);
        Quaterniond start = new Quaterniond(first.qx(), first.qy(), first.qz(), first.qw());
        int probes = 0;
        for (double t = track.startTick() + 0.25; t < track.endTick(); t += 0.5) {
            PathSample truth = PathResampler.at(path, t).orElse(null);
            FlashbackProbe.Camera camera = editor.camera(scene, 0, (float) t);
            if (truth == null || camera == null) {
                continue;
            }
            double dx = camera.x() - truth.x();
            double dy = camera.y() + Avatar.DEFAULT_EYE_HEIGHT - truth.y();
            double dz = camera.z() - truth.z();
            worstPosition = Math.max(worstPosition, Math.sqrt(dx * dx + dy * dy + dz * dz));
            worstSpatial = Math.max(worstSpatial, spatialError(path, t, camera.x(), camera.y() + Avatar.DEFAULT_EYE_HEIGHT, camera.z()));
            Quaterniond expected = new Quaterniond(truth.qx(), truth.qy(), truth.qz(), truth.qw());
            turned = Math.max(turned, CameraAngles.degreesBetween(start, expected));
            worstAngle = Math.max(worstAngle, CameraAngles.degreesBetween(new CameraAngles(camera.yaw(), camera.pitch(), camera.roll()).rotation(), expected));
            float fov = editor.fov(scene, 1, (float) Math.floor(t));
            if (!Float.isNaN(fov)) {
                worstFov = Math.max(worstFov, Math.abs(fov - truth.fov()));
            }
            probes++;
        }
        ctx.log("Szene „" + editor.name(scene) + "“: Ticks " + track.startTick() + "–" + track.endTick() + ", " + track.camera().size()
                + " Kamera-Keyframes, " + track.fov().size() + " Sichtfeld-Keyframes, " + probes + " Prüfpunkte, Abweichung höchstens "
                + String.format(Locale.ROOT, "%.3f Blöcke zur gleichen Zeit, %.3f Blöcke neben der Flugbahn, %.2f°, Sichtfeld %.2f°, "
                + "größte Drehung gegen den Start %.0f°", worstPosition, worstSpatial, worstAngle, worstFov, turned));
        if (probes < 20) {
            return StepResult.fail("zu wenige Prüfpunkte im Kamerapfad: " + probes);
        }
        if (worstPosition < 1.0E-6 && worstAngle < 1.0E-6 && turned < 1.0) {
            return StepResult.fail("die Drohne hat sich während des Kamerapfads nicht bewegt");
        }
        if (angleLimit == FLIP_ANGLE_LIMIT && turned < 120.0) {
            return StepResult.fail("Flip im Kamerapfad fehlt: höchstens " + Math.round(turned) + "° gedreht");
        }
        if (worstPosition > positionLimit || worstSpatial > SPATIAL_LIMIT || worstAngle > angleLimit || worstFov > 0.2) {
            return StepResult.fail(String.format(Locale.ROOT, "Flashback weicht vom Flug ab: %.3f Blöcke zur gleichen Zeit, %.3f neben der Bahn, "
                    + "%.2f°, Sichtfeld %.2f°", worstPosition, worstSpatial, worstAngle, worstFov));
        }
        if (exact && editor.sceneIndex() != 0) {
            return StepResult.fail("Flashback öffnet nicht die Propwash-Szene: Index " + editor.sceneIndex());
        }
        if (handover.finishedRecording()) {
            Path zip = FlashbackProbe.replayWith(replay);
            if (zip == null) {
                return StepResult.fail("Flashback hat die Aufnahme " + replay + " nicht gespeichert");
            }
            ctx.log("Flashback-Aufnahme gespeichert: " + zip.getFileName());
        }
        return StepResult.pass();
    }

    private static StepResult replayStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        UUID replay = recordedReplay;
        CameraTrack track = recordedTrack;
        CameraPathBuffer path = recordedPath;
        if (replay == null || track == null || path == null) {
            return StepResult.fail("keine Flashback-Aufnahme aus dem Aufnahme-Schritt");
        }
        try {
            switch (phase) {
                case 0 -> {
                    Path zip = FlashbackProbe.replayWith(replay);
                    if (zip == null) {
                        return StepResult.fail("Flashback-Aufnahme " + replay + " nicht gefunden");
                    }
                    FlashbackProbe.openReplay(zip);
                    state.put(PHASE, 1);
                    return StepResult.pending();
                }
                case 1 -> {
                    if (!FlashbackBridge.inReplay() || minecraft.level == null || minecraft.player == null || !FlashbackProbe.replayReady()) {
                        return ctx.tick() > 1500 ? StepResult.fail("Flashback öffnet die Wiederholung nicht") : StepResult.pending();
                    }
                    int target = track.startTick() + 5;
                    FlashbackProbe.goToTick(target);
                    state.put(MARK, ctx.tick());
                    state.put(PHASE, 2);
                    return StepResult.pending();
                }
                case 2 -> {
                    if (ctx.tick() - (int) state.get(MARK) < 40) {
                        return StepResult.pending();
                    }
                    FlashbackProbe.playing(true);
                    state.put(MARK, ctx.tick());
                    state.put(PHASE, 3);
                    return StepResult.pending();
                }
                case 3 -> {
                    double tick = FlashbackProbe.partialReplayTick();
                    Vec3 eye = minecraft.gameRenderer.mainCamera().position();
                    double[] best = nearestOnPath(path, tick, eye.x, eye.y, eye.z);
                    QuadcopterEntity drone = replayDrone(minecraft);
                    String droneInfo = "keine Drohne";
                    if (drone != null && drone.position().distanceTo(eye) < ReplayView.CORE_RADIUS
                            && !ReplayView.hides(drone, eye.x, eye.y, eye.z, minecraft.gameRenderer.mainCamera().forwardVector())) {
                        return StepResult.fail("Drohne um die Kamera wird in der Wiederholung gezeichnet");
                    }
                    if (drone != null) {
                        double[] droneBest = nearestOnPath(path, tick, drone.getX(), drone.getY() + 0.05, drone.getZ());
                        droneInfo = String.format(Locale.ROOT, "Drohne %.2f Blöcke von der Kamera, auf dem Pfad bei Tick %.2f (%.2f daneben), %s",
                                drone.position().distanceTo(eye), droneBest[0], droneBest[1], ReplayView.hides(drone, eye.x, eye.y, eye.z, minecraft.gameRenderer.mainCamera().forwardVector()) ? "ausgeblendet" : "sichtbar");
                    }
                    ctx.log(String.format(Locale.ROOT, "Wiederholung Tick %.2f: Kamera auf dem Pfad bei Tick %.2f, %.3f Blöcke daneben; %s", tick, best[0],
                            best[1], droneInfo));
                    if (tick > track.startTick() + 2 && tick < track.endTick() - 2) {
                        int samples = (int) state.getOrDefault("samples", 0) + 1;
                        state.put("samples", samples);
                        if (best[1] > 0.1 || Math.abs(best[0] - tick) > 1.5) {
                            return StepResult.fail(String.format(Locale.ROOT, "Kamera folgt dem Pfad nicht: Tick %.2f, Pfad-Tick %.2f, %.3f Blöcke daneben", tick,
                                    best[0], best[1]));
                        }
                    }
                    if (Screenshots.enabled() && !state.containsKey(FPV_SHOT) && tick >= (int) state.computeIfAbsent(FPV_TARGET,
                            key -> pilotInViewTick(path, track))) {
                        FlashbackProbe.playing(false);
                        if (!minecraft.gui.hud.isHidden()) {
                            minecraft.gui.hud.toggle();
                        }
                        Screenshots.hideMarker(true);
                        state.put(FPV_SHOT, ctx.tick());
                        return StepResult.pending();
                    }
                    if (state.get(FPV_SHOT) instanceof Integer at) {
                        if (ctx.tick() - at < 20) {
                            return StepResult.pending();
                        }
                        state.put(FPV_SHOT, Screenshots.capture("flashback_replay_fpv"));
                        return StepResult.pending();
                    }
                    if (state.get(FPV_SHOT) instanceof CompletableFuture<?> fpv) {
                        if (!fpv.isDone()) {
                            return StepResult.pending();
                        }
                        state.put(FPV_SHOT, Boolean.TRUE);
                        minecraft.gui.hud.toggle();
                        Screenshots.hideMarker(false);
                        FlashbackProbe.playing(true);
                        return StepResult.pending();
                    }
                    boolean shotPending = Screenshots.enabled() && !Boolean.TRUE.equals(state.get(FPV_SHOT));
                    if ((tick < track.startTick() + 30 || shotPending) && ctx.tick() - (int) state.get(MARK) < 600) {
                        return StepResult.pending();
                    }
                    FlashbackProbe.playing(false);
                    if ((int) state.getOrDefault("samples", 0) < 10) {
                        return StepResult.fail("zu wenige Prüfungen in der Wiederholung");
                    }
                    state.put(MARK, ctx.tick());
                    state.put(PHASE, 4);
                    return StepResult.pending();
                }
                case 4 -> {
                    StepResult shots = replayShots(ctx, minecraft, path, track);
                    if (shots.status() != StepResult.Status.PASS) {
                        return shots;
                    }
                    minecraft.schedule(() -> minecraft.disconnectFromWorld(Component.literal("Flashback-Prüfung beendet")));
                    state.put(PHASE, 5);
                    return StepResult.pending();
                }
                case 5 -> {
                    if (minecraft.level != null || FlashbackBridge.inReplay()) {
                        return ctx.tick() - (int) state.get(MARK) > 600 ? StepResult.fail("Wiederholung lässt sich nicht verlassen") : StepResult.pending();
                    }
                    return StepResult.pass();
                }
                default -> {
                    return StepResult.fail("unbekannte Phase " + phase);
                }
            }
        } catch (ReflectiveOperationException | IOException e) {
            return StepResult.fail("Flashback nicht steuerbar: " + e);
        }
    }

    private static StepResult replayShots(SelfTest.StepContext ctx, Minecraft minecraft, CameraPathBuffer path, CameraTrack track)
            throws ReflectiveOperationException {
        Map<String, Object> state = ctx.state();
        int since = ctx.tick() - (int) state.get(MARK);
        if (!Screenshots.enabled()) {
            return since < 20 ? StepResult.pending() : StepResult.pass();
        }
        int shot = (int) state.getOrDefault(SHOT, 0);
        switch (shot) {
            case 0 -> {
                double middle = track.startTick() + OVERVIEW_TICK;
                PathSample at = PathResampler.at(path, middle).orElse(null);
                PathSample before = PathResampler.at(path, middle - 5.0).orElse(null);
                PathSample after = PathResampler.at(path, middle + 5.0).orElse(null);
                if (at == null || before == null || after == null || minecraft.player == null) {
                    return StepResult.fail("Kamerapfad für die Übersicht zu kurz");
                }
                Vec3 target = new Vec3(at.x(), at.y(), at.z());
                Vec3 travel = new Vec3(after.x() - before.x(), 0.0, after.z() - before.z()).normalize();
                Vec3 side = new Vec3(-travel.z, 0.0, travel.x);
                Vec3 eye = target.add(side.scale(1.9)).add(travel.scale(-1.1)).add(0.0, 0.7, 0.0);
                Vec3 look = target.subtract(eye);
                float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
                float pitch = (float) Math.toDegrees(-Math.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)));
                state.put(SIZING, FlashbackProbe.sizing(FlashbackProbe.underlay()));
                state.put(FOV, FlashbackProbe.overrideFov(false));
                minecraft.player.snapTo(eye.x, eye.y - minecraft.player.getEyeHeight(), eye.z, yaw, pitch);
                Screenshots.hideMarker(true);
                state.put(SHOT, 1);
                state.put(MARK, ctx.tick());
                return StepResult.pending();
            }
            case 1 -> {
                if (since < 30) {
                    return StepResult.pending();
                }
                state.put(FUTURE, Screenshots.capture("flashback_replay_path"));
                state.put(SHOT, 2);
                return StepResult.pending();
            }
            default -> {
                if (!future(ctx).isDone()) {
                    return StepResult.pending();
                }
                FlashbackProbe.sizing(state.get(SIZING));
                FlashbackProbe.overrideFov(Boolean.TRUE.equals(state.get(FOV)));
                Screenshots.hideMarker(false);
                return StepResult.pass();
            }
        }
    }

    static int pilotInViewTick(CameraPathBuffer path, CameraTrack track) {
        Vec3 pilot = pilotEye;
        int best = track.startTick() + FPV_SHOT_TICK;
        if (pilot == null) {
            return best;
        }
        double bestAngle = PILOT_VIEW_DEGREES;
        for (int tick = track.startTick() + FPV_SHOT_TICK; tick < track.endTick() - 2; tick++) {
            PathSample sample = PathResampler.at(path, tick).orElse(null);
            if (sample == null) {
                continue;
            }
            Vector3d forward = new Quaterniond(sample.qx(), sample.qy(), sample.qz(), sample.qw()).transform(new Vector3d(0.0, 0.0, -1.0));
            Vector3d toPilot = new Vector3d(pilot.x - sample.x(), pilot.y - 0.6 - sample.y(), pilot.z - sample.z());
            if (toPilot.length() < 2.0) {
                continue;
            }
            double angle = Math.toDegrees(forward.angle(toPilot));
            if (angle < bestAngle) {
                bestAngle = angle;
                best = tick;
            }
        }
        return best;
    }

    static double[] nearestOnPath(CameraPathBuffer path, double around, double x, double y, double z) {
        double bestTick = Double.NaN;
        double best = Double.MAX_VALUE;
        for (double s = around - 6.0; s <= around + 6.0; s += 0.02) {
            PathSample truth = PathResampler.at(path, s).orElse(null);
            if (truth == null) {
                continue;
            }
            double dx = x - truth.x();
            double dy = y - truth.y();
            double dz = z - truth.z();
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d < best) {
                best = d;
                bestTick = s;
            }
        }
        return new double[]{bestTick, best == Double.MAX_VALUE ? Double.NaN : best};
    }

    private static @Nullable QuadcopterEntity replayDrone(Minecraft minecraft) {
        if (minecraft.level == null) {
            return null;
        }
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof QuadcopterEntity drone && (recordedDrone == null || recordedDrone.equals(drone.getUUID()))) {
                return drone;
            }
        }
        return null;
    }

    static double spatialError(CameraPathBuffer path, double t, double x, double y, double z) {
        double best = Double.MAX_VALUE;
        for (double s = t - 2.0; s <= t + 2.0; s += 0.02) {
            PathSample truth = PathResampler.at(path, s).orElse(null);
            if (truth == null) {
                continue;
            }
            double dx = x - truth.x();
            double dy = y - truth.y();
            double dz = z - truth.z();
            best = Math.min(best, Math.sqrt(dx * dx + dy * dy + dz * dz));
        }
        return best == Double.MAX_VALUE ? 0.0 : best;
    }

    private static StepResult appendStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        FlashbackCam cam = FlashbackCam.INSTANCE;
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        try {
            switch (phase) {
                case 0 -> {
                    RecordingConfig config = RecordingConfig.get().copy();
                    config.flashbackAutoRecord = false;
                    config.flashbackDensity = KeyframeDensity.REDUCED;
                    RecordingConfig.CONFIG.set(config);
                    FlashbackCam.Result refused = cam.start(minecraft, false);
                    if (refused != FlashbackCam.Result.NOT_RECORDING) {
                        return StepResult.fail("ohne laufende Flashback-Aufnahme und ohne Mitstarten: " + refused);
                    }
                    StepResult toast = toastTitled(minecraft, "propwash.video.flashback.toast.not_started");
                    if (toast.status() == StepResult.Status.FAIL) {
                        return toast;
                    }
                    if (!FlashbackBridge.startRecording()) {
                        return StepResult.fail("Flashback startet keine Aufnahme");
                    }
                    state.put(PHASE, 1);
                    return StepResult.pending();
                }
                case 1, 4, 7 -> {
                    if (phase == 7 && !state.containsKey(CLIMBED)) {
                        state.put(TARGET_Y, PilotSession.INSTANCE.comPosition(new Vector3d()).y + FLIP_CLIMB);
                        state.put(CLIMBED, Boolean.FALSE);
                    }
                    hover(ctx);
                    if (phase == 7 && Boolean.FALSE.equals(state.get(CLIMBED))) {
                        double y = PilotSession.INSTANCE.comPosition(new Vector3d()).y;
                        if (Math.abs(y - (double) state.get(TARGET_Y)) > 0.5) {
                            return ctx.tick() > 1500 ? StepResult.fail("Höhe für den Flip nicht erreicht") : StepResult.pending();
                        }
                        state.put(CLIMBED, Boolean.TRUE);
                    }
                    if (!FlashbackBridge.writing()) {
                        return ctx.tick() > 600 ? StepResult.fail("Flashback schreibt nicht") : StepResult.pending();
                    }
                    FlashbackCam.Result result = cam.start(minecraft, false);
                    if (result != FlashbackCam.Result.STARTED) {
                        return StepResult.fail("Kamerapfad startet in laufender Flashback-Aufnahme nicht: " + result);
                    }
                    if (phase == 1) {
                        state.put(REPLAY, cam.replay());
                    } else if (!cam.replay().equals(state.get(REPLAY))) {
                        return StepResult.fail("zweiter Flug landet in einer anderen Flashback-Aufnahme");
                    }
                    state.put(PHASE, phase + 1);
                    return StepResult.pending();
                }
                case 2, 5, 8 -> {
                    if (phase == 8) {
                        if (!flip(ctx)) {
                            return StepResult.pending();
                        }
                    } else {
                        fly(ctx);
                    }
                    if (cam.recordedSeconds() < 1.5) {
                        return ctx.tick() > 2000 ? StepResult.fail("Kamerapfad wächst nicht") : StepResult.pending();
                    }
                    if (phase == 8) {
                        FlashbackBridge.finishRecording();
                    } else {
                        cam.stop(minecraft, FlashbackCam.StopReason.USER);
                    }
                    state.put(MARK, ctx.tick());
                    state.put(PHASE, phase + 1);
                    return StepResult.pending();
                }
                case 3, 6, 9 -> {
                    hover(ctx);
                    FlashbackCam.Handover handover = cam.lastHandover();
                    int flight = phase / 3;
                    String expected = Component.translatable("propwash.video.flashback.scene", flight).getString();
                    if (cam.recording() || cam.saving() || handover == null || !handover.scene().equals(expected)) {
                        return ctx.tick() - (int) state.get(MARK) > 400 ? StepResult.fail("Flug " + flight + " nicht übergeben: " + cam.lastError())
                                : StepResult.pending();
                    }
                    if (handover.finishedRecording()) {
                        return StepResult.fail("Kamerapfad beendet eine Flashback-Aufnahme, die er nicht gestartet hat");
                    }
                    if (phase == 9) {
                        if (handover.reason() != FlashbackCam.StopReason.FLASHBACK_STOPPED) {
                            return StepResult.fail("Ende der Flashback-Aufnahme nicht als Grund erkannt: " + handover.reason());
                        }
                    } else if (!FlashbackBridge.recording()) {
                        return StepResult.fail("Flashback-Aufnahme endet mit dem Kamerapfad, obwohl der Spieler sie gestartet hat");
                    }
                    UUID replay = (UUID) state.get(REPLAY);
                    StepResult scene = checkScene(ctx, replay, handover, flight, false, phase == 9 ? FLIP_ANGLE_LIMIT : ANGLE_LIMIT,
                            phase == 9 ? FLIP_POSITION_LIMIT : REDUCED_POSITION_LIMIT);
                    if (scene.status() != StepResult.Status.PASS) {
                        return scene;
                    }
                    if (handover.sceneIndex() != flight - 1) {
                        return StepResult.fail("Flug " + flight + " ist Szene " + handover.sceneIndex());
                    }
                    if (phase == 9) {
                        Path zip = FlashbackProbe.replayWith(replay);
                        return StepResult.check(zip != null, "Flashback hat die Aufnahme nicht gespeichert");
                    }
                    state.put(PHASE, phase + 1);
                    return StepResult.pending();
                }
                default -> {
                    return StepResult.fail("unbekannte Phase " + phase);
                }
            }
        } catch (ReflectiveOperationException | IOException e) {
            return StepResult.fail("Flashback nicht lesbar: " + e);
        }
    }

    static StepResult takeOff(SelfTest.StepContext ctx) {
        PilotSession session = PilotSession.INSTANCE;
        DroneSim sim = session.sim();
        if (sim == null) {
            return StepResult.fail("keine Flugsitzung");
        }
        Map<String, Object> state = ctx.state();
        int since = (int) state.computeIfAbsent(SINCE, key -> ctx.tick());
        int elapsed = ctx.tick() - since;
        if (elapsed < 20 || !sim.telemetry().armed) {
            boolean arm = sim.telemetry().armed || (elapsed >= 10 && sim.telemetry().armBlockers == 0 && ctx.tick() % 10 >= 5);
            session.setTestInput(StickPositions.IDLE, arm, StabMode.ANGLE);
            return elapsed > 160 ? StepResult.fail("Drohne wird nicht scharf: " + sim.telemetry().armBlockers) : StepResult.pending();
        }
        double y = session.comPosition(new Vector3d()).y;
        double target = (double) state.computeIfAbsent(TARGET_Y, key -> y + CLIMB);
        session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, altitude(state, y, target)), true, StabMode.ANGLE);
        if (Math.abs(y - target) < 0.4) {
            return StepResult.pass();
        }
        return elapsed > 700 ? StepResult.fail("Höhe nicht erreicht: " + y + " statt " + target) : StepResult.pending();
    }

    static float altitude(Map<String, Object> state, double y, double target) {
        double last = (double) state.getOrDefault(LAST_Y, y);
        double integral = (double) state.getOrDefault(INTEGRAL, 0.0);
        double vy = (y - last) * 20.0;
        double error = target - y;
        integral = Math.max(-0.2, Math.min(0.2, integral + error * 0.004));
        state.put(LAST_Y, y);
        state.put(INTEGRAL, integral);
        return (float) Math.max(0.05, Math.min(0.7, 0.3 + integral + error * 0.12 - vy * 0.08));
    }

    static void fly(SelfTest.StepContext ctx) {
        PilotSession session = PilotSession.INSTANCE;
        Map<String, Object> state = ctx.state();
        double y = session.comPosition(new Vector3d()).y;
        double target = (double) state.getOrDefault(TARGET_Y, y);
        float yaw = (ctx.tick() / 30) % 2 == 0 ? 0.3F : -0.2F;
        float roll = (ctx.tick() / 45) % 2 == 0 ? 0.15F : -0.1F;
        session.setTestInput(new StickPositions(roll, 0.3F, yaw, altitude(state, y, target) + 0.04F), true, StabMode.ANGLE);
    }

    static boolean flip(SelfTest.StepContext ctx) {
        PilotSession session = PilotSession.INSTANCE;
        Map<String, Object> state = ctx.state();
        int since = (int) state.computeIfAbsent(FLIP_SINCE, key -> ctx.tick());
        int elapsed = ctx.tick() - since;
        if (elapsed < FLIP_TICKS) {
            session.setTestInput(new StickPositions(0.12F, 1.0F, 0.0F, 0.45F), true, StabMode.ACRO);
            return false;
        }
        double y = session.comPosition(new Vector3d()).y;
        double target = (double) state.getOrDefault(TARGET_Y, y);
        session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, altitude(state, y, target)), true, StabMode.ANGLE);
        return elapsed > FLIP_TICKS + 20;
    }

    static void hover(SelfTest.StepContext ctx) {
        PilotSession session = PilotSession.INSTANCE;
        Map<String, Object> state = ctx.state();
        double y = session.comPosition(new Vector3d()).y;
        double target = (double) state.getOrDefault(TARGET_Y, y);
        session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, altitude(state, y, target)), true, StabMode.ANGLE);
    }

    static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            FlashbackCam cam = FlashbackCam.INSTANCE;
            if (cam.recording()) {
                cam.stop(minecraft, FlashbackCam.StopReason.USER);
            }
            if (FlashbackBridge.recording()) {
                FlashbackBridge.finishRecording();
            }
            PilotSession.INSTANCE.clearTestInput();
            RecordingConfig saved = savedConfig;
            if (saved != null) {
                RecordingConfig.CONFIG.set(saved);
            }
            ctx.state().put(FUTURE, ctx.onServer(FlashbackCamSelfTests::releaseDrone));
            return StepResult.pending();
        }
        CompletableFuture<?> future = future(ctx);
        if (!future.isDone()) {
            return StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Aufräumen fehlgeschlagen: " + future.exceptionNow().getMessage());
        }
        return PilotSession.INSTANCE.isActive() ? (ctx.tick() > 150 ? StepResult.fail("Sitzung endet nicht") : StepResult.pending()) : StepResult.pass();
    }

    private static StepResult afterLeaveStep(SelfTest.StepContext ctx) {
        FlashbackCam cam = FlashbackCam.INSTANCE;
        if (cam.recording() || cam.saving()) {
            return ctx.tick() > 150 ? StepResult.fail("Kamerapfad nach dem Verlassen nicht beendet") : StepResult.pending();
        }
        if (FlashbackBridge.recording()) {
            return StepResult.fail("Flashback-Aufnahme nach dem Verlassen noch offen");
        }
        return StepResult.pass();
    }

    static void releaseDrone(MinecraftServer server) {
        ServerPlayer player = firstPlayer(server);
        if (player != null) {
            DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getEntity(droneId) instanceof QuadcopterEntity drone) {
                    drone.discard();
                }
            }
            ItemStack head = previousHead;
            player.setItemSlot(EquipmentSlot.HEAD, head == null ? ItemStack.EMPTY : head);
            ItemStack hand = previousHand;
            player.setItemSlot(EquipmentSlot.MAINHAND, hand == null ? ItemStack.EMPTY : hand);
        }
        droneId = -1;
    }

    static void spawnAndFly(MinecraftServer server) {
        ServerPlayer player = firstPlayer(server);
        if (player == null) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        ServerLevel level = player.level();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("EntityType.create lieferte null");
        }
        Vec3 feet = player.position();
        pilotEye = player.getEyePosition();
        drone.snapTo(feet.x, feet.y, feet.z + DRONE_DISTANCE, 0.0F, 0.0F);
        drone.setAttitude(new Quaternionf());
        drone.setBuild(DroneBuild.FREESTYLE.withAccessory(AccessoryType.ACTION_CAM_FLASHBACK));
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("addFreshEntity fehlgeschlagen");
        }
        droneId = drone.getId();
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (goggles.isEmpty()) {
            throw new IllegalStateException("Kreativ-Brille nicht registriert");
        }
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        previousHead = player.getItemBySlot(EquipmentSlot.HEAD).copy();
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        previousHand = player.getMainHandItem().copy();
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("FPV-Start verweigert: " + result);
        }
    }

    static @Nullable ServerPlayer firstPlayer(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        return players.isEmpty() ? null : players.getFirst();
    }

    static StepResult toastTitled(Minecraft minecraft, String key) {
        ThemedToast toast = ThemedToast.find(minecraft.gui.toastManager(), FlashbackCam.TOAST_TOKEN);
        String expected = Component.translatable(key).getString();
        if (toast == null || !toast.title().getString().startsWith(expected)) {
            return StepResult.fail("Toast „" + expected + "“ fehlt: " + (toast == null ? "kein Toast" : toast.title().getString()));
        }
        return StepResult.pass();
    }

    private static CompletableFuture<?> future(SelfTest.StepContext ctx) {
        Object value = ctx.state().get(FUTURE);
        return value instanceof CompletableFuture<?> future ? future : CompletableFuture.failedFuture(new IllegalStateException("kein Auftrag"));
    }
}
