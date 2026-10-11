package de.lardtom.propwash.video.client.record;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.audio.client.MotorAudioTap;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.ActionCamSpec;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.BuildSlots;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.content.DroneAssembly;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.client.FunctionKeys;
import de.lardtom.propwash.input.client.KeyDefaults;
import de.lardtom.propwash.osd.OsdElement;
import de.lardtom.propwash.osd.client.OsdHud;
import de.lardtom.propwash.ui.client.config.ConfigTabHost;
import de.lardtom.propwash.ui.client.theme.ThemedToast;
import de.lardtom.propwash.video.record.CameraLook;
import de.lardtom.propwash.video.record.ClipInfo;
import de.lardtom.propwash.video.record.FrameStats;
import de.lardtom.propwash.video.record.RecordingFormat;
import de.lardtom.propwash.video.record.RecordingJob;
import de.lardtom.propwash.video.record.RecordingLibrary;
import de.lardtom.propwash.video.record.RecordingQuality;
import de.lardtom.propwash.video.record.Stabilizer;
import de.lardtom.propwash.video.record.aac.AacEncoder;
import de.lardtom.propwash.video.record.mp4.Mp4Info;
import de.lardtom.propwash.video.record.mp4.Mp4Reader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class ActionCamSelfTests {
    private static final double DRONE_DISTANCE = 6.0;
    private static final long RECORD_NANOS = 3_000_000_000L;
    private static final RecordingFormat TEST_FORMAT = RecordingFormat.of(720, 30);
    private static final String FUTURE = "future";
    private static final String PHASE = "phase";
    private static final String CHARGE = "charge";
    private static final String HUD = "hud";
    private static final String CLIP = "clip";

    private static volatile int droneId = -1;
    private static volatile @Nullable ItemStack previousHead;
    private static volatile @Nullable ItemStack previousHand;

    private ActionCamSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("video: Action-Cam-Tab, Taste und Schalter", 20, ActionCamSelfTests::tabStep);
        SelfTest.clientStep("video: Kamera-Akku übersteht Werkbank und Item", 5, ActionCamSelfTests::assemblyStep);
        SelfTest.clientStep("video: Drohne mit Action-Cam Heavy im FPV", 400, ActionCamSelfTests::setupStep);
        SelfTest.clientStep("video: Action-Cam nimmt 3 Sekunden als MP4 auf", 2400, ActionCamSelfTests::recordStep);
        SelfTest.clientStep("video: Aufnahmen-Liste zeigt das Video mit Vorschaubild und löscht es", 400, ActionCamSelfTests::recordingsStep);
        SelfTest.clientStep("video: Kamera-Akku der Heavy sinkt beim Aufnehmen", 200, ActionCamSelfTests::drainStep);
        SelfTest.clientStep("video: Heavy-Cam lädt abgestellt per USB aus dem Flugakku", 400, ActionCamSelfTests::usbStep);
        SelfTest.afterLeaveStep("video: Action-Cam nach dem Verlassen aufgeräumt", 200, ActionCamSelfTests::afterLeaveStep);
    }

    private static StepResult tabStep(SelfTest.StepContext ctx) {
        List<AbstractWidget> widgets = new ArrayList<>();
        ConfigTabHost host = new ConfigTabHost() {
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
        ActionCamTab tab = new ActionCamTab();
        tab.attach(host, 0, 0, 480, 280);
        if (widgets.size() < 10) {
            return StepResult.fail("Tab „Action-Cam“ hat nur " + widgets.size() + " Bedienelemente");
        }
        String qualityLabel = ActionCamTab.qualityLabel(RecordingQuality.STANDARD, RecordingFormat.DEFAULT).getString();
        if (!qualityLabel.contains("MB/min")) {
            return StepResult.fail("Qualität zeigt keine Dateigröße: " + qualityLabel);
        }
        boolean qualityShown = widgets.stream().anyMatch(widget -> widget.getMessage().getString().contains("MB/min"));
        if (!qualityShown) {
            return StepResult.fail("Tab „Action-Cam“ ohne Qualitätsauswahl");
        }
        RecordingFormat full = RecordingFormat.of(1080, 60);
        if (RecordingQuality.HIGH.typicalMegabytesPerMinute(full) <= RecordingQuality.STANDARD.typicalMegabytesPerMinute(full)
                || RecordingQuality.STANDARD.typicalMegabytesPerMinute(full) <= RecordingQuality.SMALL.typicalMegabytesPerMinute(full)) {
            return StepResult.fail("Qualitätsstufen nicht nach Größe geordnet");
        }
        float light = CameraLook.grain(ActionCamSpec.LIGHT, true);
        float heavy = CameraLook.grain(ActionCamSpec.HEAVY, true);
        if (!(light > heavy && heavy > 0.0F && light < 0.02F) || CameraLook.grain(ActionCamSpec.LIGHT, false) != 0.0F) {
            return StepResult.fail("Kamerarauschen nicht dezent oder nicht abschaltbar: Light " + light + ", Heavy " + heavy);
        }
        if (CameraLook.grainWeight(0.5) != 0.0 || CameraLook.grainWeight(0.0) != 1.0) {
            return StepResult.fail("Kamerarauschen nicht nur bei wenig Licht");
        }
        ctx.log("Qualität: " + qualityLabel + ", Rauschen Light " + light + ", Heavy " + heavy);
        KeyMapping key = KeyMapping.get(KeyDefaults.ACTION_CAM_RECORD.mappingName());
        if (key == null || key != RecordingKeys.RECORD) {
            return StepResult.fail("Aufnahme-Taste nicht registriert");
        }
        if (key.getDefaultKey().getType() != InputConstants.Type.KEYBOARD || key.getDefaultKey().getValue() != InputConstants.KEY_F9) {
            return StepResult.fail("Aufnahme-Taste nicht auf F9: " + key.getDefaultKey().getName());
        }
        for (KeyMapping other : Minecraft.getInstance().options.keyMappings) {
            if (other != key && other.getDefaultKey().equals(key.getDefaultKey())) {
                return StepResult.fail("Standardtaste der Aufnahme kollidiert mit " + other.getName());
            }
        }
        if (FunctionKeys.entry(SwitchFunction.RECORD) != KeyDefaults.ACTION_CAM_RECORD || !SwitchFunction.RECORD.supportsToggle()) {
            return StepResult.fail("Schalterfunktion „Aufnahme“ nicht mit der Aufnahme-Taste verbunden");
        }
        if (ActionCamRecorder.stabilizationFor(ActionCamSpec.LIGHT) != Stabilizer.Mode.OFF) {
            return StepResult.fail("Light-Cam darf nicht stabilisieren");
        }
        if (ActionCamRecorder.formatFor(ActionCamSpec.LIGHT).height() > 1080) {
            return StepResult.fail("Light-Cam nimmt über 1080p auf");
        }
        return StepResult.pass();
    }

    private static StepResult assemblyStep(SelfTest.StepContext ctx) {
        if (!DroneAssembly.droneItemAvailable()) {
            return StepResult.fail("Drohnen-Item nicht registriert");
        }
        DroneBuild build = DroneBuild.FREESTYLE.withAccessory(AccessoryType.ACTION_CAM_HEAVY);
        List<ItemStack> parts = DroneAssembly.disassemble(DroneAssembly.preset(build));
        ItemStack camera = parts.get(BuildSlots.accessorySlot(AccessoryMount.TOP));
        if (DroneAssembly.part(camera).orElse(null) != AccessoryType.ACTION_CAM_HEAVY) {
            return StepResult.fail("Zerlegen liefert keine Heavy-Cam: " + camera);
        }
        if (camera.has(CoreComponents.CAMERA_CHARGE.get())) {
            return StepResult.fail("volle Heavy-Cam trägt einen Ladestand und stapelt nicht mehr mit neuen");
        }
        DroneAssembly.storeCameraCharge(camera, 0.4F);
        ItemStack drone = DroneAssembly.createDrone(build, parts.get(BuildSlots.STACK), parts.get(BuildSlots.FRAME), parts.get(BuildSlots.BATTERY),
                camera);
        if (Math.abs(DroneAssembly.cameraCharge(drone) - 0.4F) > 0.011F) {
            return StepResult.fail("Drohne übernimmt den Kamera-Akku nicht: " + DroneAssembly.cameraCharge(drone));
        }
        ItemStack back = DroneAssembly.disassemble(drone).get(BuildSlots.accessorySlot(AccessoryMount.TOP));
        if (Math.abs(DroneAssembly.cameraCharge(back) - 0.4F) > 0.011F) {
            return StepResult.fail("Zerlegen gibt den Kamera-Akku nicht zurück: " + DroneAssembly.cameraCharge(back));
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            QuadcopterEntity entity = DroneModule.DRONE.get().create(minecraft.level, EntitySpawnReason.COMMAND);
            if (entity != null) {
                entity.loadFromStack(drone);
                if (Math.abs(entity.cameraCharge() - 0.4F) > 0.011F) {
                    return StepResult.fail("Drohne lädt den Kamera-Akku nicht aus dem Item: " + entity.cameraCharge());
                }
                if (Math.abs(DroneAssembly.cameraCharge(entity.toStack()) - 0.4F) > 0.011F) {
                    return StepResult.fail("Aufheben verliert den Kamera-Akku");
                }
            }
        }
        List<Component> lines = new ArrayList<>();
        back.getItem().appendHoverText(back, Item.TooltipContext.EMPTY, TooltipDisplay.DEFAULT, lines::add, TooltipFlag.NORMAL);
        String expected = Component.translatable("propwash.content.tooltip.camera_charge", 40, 36).getString();
        boolean shown = lines.stream().anyMatch(line -> line.getString().contains(expected));
        return StepResult.check(shown, "Tooltip der Heavy-Cam zeigt den Kamera-Akku nicht (" + expected + "): " + lines);
    }

    private static StepResult setupStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            ActionCamRecorder.INSTANCE.cancelAll();
            ctx.state().put(FUTURE, ctx.onServer(ActionCamSelfTests::spawnAndFly));
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
            return ctx.tick() > 300 ? StepResult.fail("keine FPV-Sitzung mit der Action-Cam-Drohne (Sitzungsdrohne "
                    + (drone == null ? "keine" : drone.getId()) + ", erwartet " + droneId + ", FPV " + session.isFpv()
                    + ", Kamera " + minecraft.getCameraEntity() + ")") : StepResult.pending();
        }
        if (ActionCamRecorder.cameraOf(drone).orElse(null) != AccessoryType.ACTION_CAM_HEAVY) {
            return StepResult.fail("Drohne auf dem Client ohne Action-Cam Heavy: " + drone.build().accessories());
        }
        return ctx.tick() > 20 ? StepResult.pass() : StepResult.pending();
    }

    private static StepResult recordStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        ActionCamRecorder recorder = ActionCamRecorder.INSTANCE;
        int phase = (int) ctx.state().getOrDefault(PHASE, 0);
        switch (phase) {
            case 0 -> {
                recorder.overrideFormat(TEST_FORMAT);
                ActionCamRecorder.Result result = recorder.start(minecraft, false);
                if (result != ActionCamRecorder.Result.STARTED) {
                    return StepResult.fail("Aufnahme startet nicht: " + result);
                }
                if (recorder.quality() != RecordingConfig.get().quality) {
                    return StepResult.fail("Aufnahme folgt nicht der eingestellten Qualität: " + recorder.quality());
                }
                if (recorder.stabilization() != RecordingConfig.get().stabilization) {
                    return StepResult.fail("Heavy-Cam folgt nicht der eingestellten Stabilisierung: " + recorder.stabilization());
                }
                StepResult toast = toastTitled(minecraft, "propwash.video.record.toast.started");
                if (toast.status() == StepResult.Status.FAIL) {
                    return toast;
                }
                ctx.state().put(HUD, OsdHud.renderedFrames());
                ctx.state().put(PHASE, 1);
                return StepResult.pending();
            }
            case 1 -> {
                if (recorder.phase() != ActionCamRecorder.Phase.RECORDING) {
                    return StepResult.fail("Aufnahme endete vorzeitig: " + recorder.lastError());
                }
                if (recorder.recordedNanos() < RECORD_NANOS) {
                    return ctx.tick() > 1200 ? StepResult.fail("Aufnahmezeit läuft nicht: " + recorder.recordedNanos() / 1_000_000L + " ms")
                            : StepResult.pending();
                }
                if (OsdHud.renderedFrames() <= (int) ctx.state().get(HUD)) {
                    return ctx.tick() > 1400 ? StepResult.fail("OSD wird während der Aufnahme nicht gezeichnet") : StepResult.pending();
                }
                if (OsdHud.config().slot(OsdElement.RECORDING).enabled && !OsdHud.drewLastFrame(OsdElement.RECORDING)) {
                    return StepResult.fail("REC-Anzeige fehlt im OSD");
                }
                if (!RecordingHud.notices(recorder).isEmpty()) {
                    ctx.log("Aufnahme-Hinweise während der Aufnahme: " + RecordingHud.notices(recorder));
                }
                long expectedAudio = recorder.recordedNanos() * AudioCapture.RATE / 1_000_000_000L;
                if (RecordingConfig.get().audio && recorder.audioSubmitted() < expectedAudio - AudioCapture.RATE / 4) {
                    return StepResult.fail("Tonspur hinkt hinterher: " + recorder.audioSubmitted() + " von " + expectedAudio + " Samples");
                }
                ctx.state().put(CHARGE, recorder.cameraCharge());
                recorder.stop(minecraft, ActionCamRecorder.StopReason.USER);
                StepResult stopped = toastTitled(minecraft, "propwash.video.record.toast.stopped");
                if (stopped.status() == StepResult.Status.FAIL) {
                    return stopped;
                }
                ctx.log("Aufnahme gestoppt nach " + recorder.recordedNanos() / 1_000_000L + " ms, " + recorder.capturedFrames()
                        + " Bilder gelesen");
                ctx.state().put(PHASE, 2);
                return StepResult.pending();
            }
            case 2 -> {
                if (recorder.phase() != ActionCamRecorder.Phase.IDLE || !recorder.saving().isEmpty()) {
                    return StepResult.pending();
                }
                Path file = recorder.lastSaved();
                if (file == null) {
                    return StepResult.fail("kein Video gespeichert: " + recorder.lastError());
                }
                StepResult checked = verify(ctx, file, recorder);
                recorder.overrideFormat(null);
                if (checked.status() != StepResult.Status.PASS) {
                    return checked;
                }
                StepResult saved = toastTitled(minecraft, "propwash.video.record.saved.title");
                if (saved.status() == StepResult.Status.FAIL) {
                    return saved;
                }
                ctx.state().put(PHASE, 3);
                ctx.state().put(FUTURE, RecordingThumbnails.flushed());
                return StepResult.pending();
            }
            case 3 -> {
                if (!future(ctx).isDone()) {
                    return StepResult.pending();
                }
                Path file = recorder.lastSaved();
                if (file == null) {
                    return StepResult.fail("gespeichertes Video vergessen");
                }
                Path thumbnail = RecordingLibrary.thumbnailOf(file);
                if (!Files.isRegularFile(thumbnail)) {
                    return StepResult.fail("kein Vorschaubild unter " + thumbnail);
                }
                List<ClipInfo> clips = recorder.library().scanNow();
                ClipInfo clip = clips.stream().filter(info -> info.file().equals(file)).findFirst().orElse(null);
                if (clip == null || !clip.readable() || Math.abs(clip.seconds() - 3.0) > 0.2 || clip.height() != TEST_FORMAT.height()) {
                    return StepResult.fail("Aufnahmen-Liste kennt das Video nicht richtig: " + clip);
                }
                if (clip.audio() != RecordingConfig.get().audio) {
                    return StepResult.fail("Tonspur " + (clip.audio() ? "vorhanden" : "fehlt") + ", Einstellung Ton = " + RecordingConfig.get().audio);
                }
                ctx.log("Aufnahmen-Liste: " + clip.fileName() + ", " + clip.duration() + ", " + clip.format() + ", " + clip.size()
                        + (clip.audio() ? ", mit Ton" : ""));
                return StepResult.pass();
            }
            default -> {
                return StepResult.fail("unbekannte Phase " + phase);
            }
        }
    }

    private static StepResult verify(SelfTest.StepContext ctx, Path file, ActionCamRecorder recorder) {
        if (!Files.isRegularFile(file) || !file.getFileName().toString().matches("propwash_\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}(_\\d+)?\\.mp4")) {
            return StepResult.fail("Videodatei fehlt oder falscher Name: " + file);
        }
        if (!file.getParent().endsWith(Path.of("propwash", "recordings"))) {
            return StepResult.fail("Video nicht in propwash/recordings: " + file);
        }
        Mp4Info info;
        try {
            info = Mp4Reader.read(file);
        } catch (IOException e) {
            return StepResult.fail("MP4 lässt sich nicht lesen: " + e.getMessage());
        }
        ctx.log("MP4: " + info.width() + "x" + info.height() + ", " + info.sampleCount() + " Bilder, " + info.durationSeconds() + " s, Level "
                + info.level() + ", " + recorder.capturedFrames() + " Bilder aufgenommen");
        if (!info.codec().equals("avc1") || !info.handler().equals("vide") || info.profile() != 66 && info.profile() != 77 && info.profile() != 100) {
            return StepResult.fail("kein H.264-Video: " + info.codec() + "/" + info.handler() + "/" + info.profile());
        }
        if (info.width() != TEST_FORMAT.width() || info.height() != TEST_FORMAT.height()) {
            return StepResult.fail("falsche Größe: " + info.width() + "x" + info.height());
        }
        double frame = 1.0 / TEST_FORMAT.fps();
        if (info.durationSeconds() < 3.0 - frame * 0.5 || info.durationSeconds() > 3.0 + frame * 3.0) {
            return StepResult.fail("Dauer " + info.durationSeconds() + " s statt 3 s");
        }
        if (Math.abs(info.movieSeconds() - info.durationSeconds()) > 0.002) {
            return StepResult.fail("Filmdauer " + info.movieSeconds() + " passt nicht zur Spurdauer " + info.durationSeconds());
        }
        if (Math.abs(info.frameRate() - TEST_FORMAT.fps()) > 0.01 || !info.startsWithSync()) {
            return StepResult.fail("Bildrate " + info.frameRate() + " oder erstes Bild kein Schlüsselbild");
        }
        if (RecordingConfig.get().audio) {
            if (!info.hasAudio() || !info.audioCodec().equals("mp4a") || info.audioObjectType() != AacEncoder.OBJECT_TYPE_LC
                    || info.audioSampleRate() != AacEncoder.SAMPLE_RATE) {
                return StepResult.fail("keine AAC-Tonspur: " + info.audioCodec() + "/" + info.audioObjectType() + "/" + info.audioSampleRate());
            }
            if (Math.abs(info.audioSeconds() - info.durationSeconds()) > 0.01 || info.audioEditMediaTime() != AacEncoder.PRIMING) {
                return StepResult.fail("Tonspur " + info.audioSeconds() + " s passt nicht zum Bild " + info.durationSeconds() + " s");
            }
        } else if (info.hasAudio()) {
            return StepResult.fail("Tonspur trotz „Ton aufnehmen: Aus“");
        }
        long bytes;
        try {
            bytes = Files.size(file);
        } catch (IOException e) {
            return StepResult.fail("Dateigröße nicht lesbar: " + e.getMessage());
        }
        double bitrate = bytes * 8.0 / Math.max(0.1, info.durationSeconds());
        long cap = recorder.quality().maxBitrate(TEST_FORMAT);
        ctx.log(String.format(Locale.ROOT, "Qualität %s: %.1f Mbit/s (Obergrenze %.1f Mbit/s)", recorder.quality().key(), bitrate / 1.0E6,
                cap / 1.0E6));
        if (bitrate > cap * 1.35 + 300_000.0) {
            return StepResult.fail(String.format(Locale.ROOT, "Video %.1f Mbit/s über der Obergrenze %.1f Mbit/s", bitrate / 1.0E6, cap / 1.0E6));
        }
        if (recorder.capturedFrames() < 15) {
            return StepResult.fail("nur " + recorder.capturedFrames() + " Bilder von der GPU gelesen");
        }
        FrameStats.Snapshot stats = recorder.stats().last();
        ctx.log(String.format(Locale.ROOT, "Bild oben Y %.0f Cb %.0f Cr %.0f, unten Y %.0f Cb %.0f Cr %.0f, Spanne %.0f", stats.topY(),
                stats.topCb(), stats.topCr(), stats.bottomY(), stats.bottomCb(), stats.bottomCr(), stats.lumaSpread()));
        if (stats.frames() == 0L || stats.lumaSpread() < 12.0) {
            return StepResult.fail("aufgenommenes Bild ist leer oder einfarbig (Spanne " + stats.lumaSpread() + ")");
        }
        if (stats.topY() < 16.0 || stats.topY() > 235.5 || stats.bottomY() < 16.0 || stats.bottomY() > 235.5) {
            return StepResult.fail("Helligkeit außerhalb des TV-Bereichs: " + stats.topY() + " / " + stats.bottomY());
        }
        boolean skyAbove = stats.topCb() - stats.bottomCb() > 2.0 || stats.topY() - stats.bottomY() > 8.0;
        if (!skyAbove) {
            return StepResult.fail("Himmel nicht oben im Bild (Bild gespiegelt oder Farben vertauscht?)");
        }
        return StepResult.pass();
    }

    private static StepResult drainStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !(minecraft.level.getEntity(droneId) instanceof QuadcopterEntity drone)) {
            return StepResult.fail("Drohne auf dem Client verschwunden");
        }
        float charge = drone.cameraCharge();
        if (charge < 1.0F && charge > 0.99F) {
            ctx.log("Kamera-Akku nach 3 s Aufnahme: " + charge);
            return StepResult.pass();
        }
        return ctx.tick() > 160 ? StepResult.fail("Kamera-Akku auf dem Server nicht gesunken: " + charge) : StepResult.pending();
    }

    private static StepResult usbStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            AtomicReference<float[]> before = new AtomicReference<>();
            ctx.state().put(CHARGE, before);
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                QuadcopterEntity drone = serverDrone(server);
                if (drone == null) {
                    throw new IllegalStateException("Drohne fehlt");
                }
                if (drone.pilot().isPresent()) {
                    DronePiloting.release(drone);
                }
                drone.setCameraCharge(0.5F);
                drone.setBatteryCharge(0.9F);
                before.set(new float[]{drone.cameraCharge(), drone.batteryCharge()});
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> future = future(ctx);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Pilot nicht gelöst: " + future.exceptionNow().getMessage());
        }
        if (!future.isDone() || ctx.tick() < 60) {
            return StepResult.pending();
        }
        if (!ctx.state().containsKey(PHASE)) {
            AtomicReference<float[]> after = new AtomicReference<>();
            ctx.state().put(PHASE, after);
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                QuadcopterEntity drone = serverDrone(server);
                if (drone != null) {
                    after.set(new float[]{drone.cameraCharge(), drone.batteryCharge()});
                    drone.discard();
                }
                ServerPlayer player = firstPlayer(server);
                ItemStack head = previousHead;
                if (player != null) {
                    player.setItemSlot(EquipmentSlot.HEAD, head == null ? ItemStack.EMPTY : head);
                    ItemStack hand = previousHand;
                    player.setItemSlot(EquipmentSlot.MAINHAND, hand == null ? ItemStack.EMPTY : hand);
                }
                droneId = -1;
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> done = future(ctx);
        if (!done.isDone()) {
            return StepResult.pending();
        }
        @SuppressWarnings("unchecked")
        float[] before = ((AtomicReference<float[]>) ctx.state().get(CHARGE)).get();
        @SuppressWarnings("unchecked")
        float[] after = ((AtomicReference<float[]>) ctx.state().get(PHASE)).get();
        if (before == null || after == null) {
            return StepResult.fail("Ladestände nicht gelesen");
        }
        ctx.log("USB-Laden: Kamera " + before[0] + " → " + after[0] + ", Flugakku " + before[1] + " → " + after[1]);
        if (!(after[0] > before[0] + 0.005F)) {
            return StepResult.fail("Heavy-Cam lädt nicht: " + before[0] + " → " + after[0]);
        }
        if (!(after[1] < before[1])) {
            return StepResult.fail("USB-Laden zieht keinen Strom aus dem Flugakku: " + before[1] + " → " + after[1]);
        }
        return StepResult.pass();
    }

    private static StepResult recordingsStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        ActionCamRecorder recorder = ActionCamRecorder.INSTANCE;
        if (ctx.tick() == 0) {
            Path saved = recorder.lastSaved();
            if (saved == null || !Files.isRegularFile(saved)) {
                return StepResult.fail("kein Video aus dem Aufnahme-Schritt: " + saved);
            }
            ctx.state().put(CLIP, saved);
            RecordingsScreen.open(null);
            return StepResult.pending();
        }
        if (!(minecraft.gui.screen() instanceof RecordingsScreen screen)) {
            return StepResult.fail("Aufnahmen-Liste nicht offen: " + minecraft.gui.screen());
        }
        Path file = (Path) ctx.state().get(CLIP);
        if (file == null) {
            return StepResult.fail("Video aus dem Aufnahme-Schritt vergessen");
        }
        int phase = (int) ctx.state().getOrDefault(PHASE, 0);
        switch (phase) {
            case 0 -> {
                boolean listed = screen.clips().stream().anyMatch(clip -> clip.file().equals(file));
                if (!listed || screen.loadedThumbnails() == 0 || screen.frames() < 3) {
                    return ctx.tick() > 200 ? StepResult.fail("Liste zeigt das Video nicht mit Vorschaubild: " + screen.clips().size()
                            + " Einträge, " + screen.loadedThumbnails() + " Vorschaubilder") : StepResult.pending();
                }
                if (!file.equals(screen.selected())) {
                    return StepResult.fail("neuestes Video nicht ausgewählt: " + screen.selected());
                }
                screen.deleteSelected();
                if (!screen.deleteArmed() || !Files.exists(file)) {
                    return StepResult.fail("Löschen fragt nicht nach");
                }
                screen.deleteSelected();
                ctx.state().put(PHASE, 1);
                return StepResult.pending();
            }
            case 1 -> {
                if (Files.exists(file) || Files.exists(RecordingLibrary.thumbnailOf(file))) {
                    return StepResult.fail("Video oder Vorschaubild nach dem Löschen noch da");
                }
                if (screen.clips().stream().anyMatch(clip -> clip.file().equals(file))) {
                    return StepResult.fail("gelöschtes Video noch in der Liste");
                }
                StepResult toast = toastTitled(minecraft, "propwash.video.recordings.deleted");
                minecraft.gui.setScreen(null);
                return toast;
            }
            default -> {
                return StepResult.fail("unbekannte Phase " + phase);
            }
        }
    }

    private static StepResult toastTitled(Minecraft minecraft, String key) {
        ThemedToast toast = ThemedToast.find(minecraft.gui.toastManager(), ActionCamRecorder.TOAST_TOKEN);
        String expected = Component.translatable(key).getString();
        if (toast == null || !toast.title().getString().equals(expected)) {
            return StepResult.fail("Toast „" + expected + "“ fehlt: " + (toast == null ? "kein Toast" : toast.title().getString()));
        }
        return StepResult.pass();
    }

    private static StepResult afterLeaveStep(SelfTest.StepContext ctx) {
        ActionCamRecorder recorder = ActionCamRecorder.INSTANCE;
        if (MotorAudioTap.listening()) {
            return StepResult.fail("Motorton-Abgriff nach dem Verlassen noch aktiv");
        }
        if (recorder.phase() != ActionCamRecorder.Phase.IDLE) {
            return ctx.tick() > 100 ? StepResult.fail("Rekorder nach dem Verlassen nicht im Leerlauf: " + recorder.phase()) : StepResult.pending();
        }
        for (RecordingJob.Progress progress : recorder.saving()) {
            if (!progress.finished()) {
                return ctx.tick() > 180 ? StepResult.fail("Speichern hängt: " + progress.state()) : StepResult.pending();
            }
        }
        if (recorder.capturePass().holdsResources()) {
            return ctx.tick() > 100 ? StepResult.fail("GPU-Ressourcen der Aufnahme nicht freigegeben") : StepResult.pending();
        }
        return StepResult.pass();
    }

    private static void spawnAndFly(MinecraftServer server) {
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
        drone.snapTo(feet.x, feet.y, feet.z + DRONE_DISTANCE, 0.0F, 0.0F);
        drone.setAttitude(new Quaternionf().rotationY((float) Math.PI));
        drone.setBuild(DroneBuild.FREESTYLE.withAccessory(AccessoryType.ACTION_CAM_HEAVY));
        drone.setCameraCharge(1.0F);
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

    private static @Nullable ServerPlayer firstPlayer(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        return players.isEmpty() ? null : players.getFirst();
    }

    private static @Nullable QuadcopterEntity serverDrone(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(droneId);
            if (entity instanceof QuadcopterEntity drone) {
                return drone;
            }
        }
        return null;
    }

    private static CompletableFuture<?> future(SelfTest.StepContext ctx) {
        Object value = ctx.state().get(FUTURE);
        return value instanceof CompletableFuture<?> future ? future : CompletableFuture.failedFuture(new IllegalStateException("kein Auftrag"));
    }
}
