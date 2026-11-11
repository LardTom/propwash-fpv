package de.lardtom.propwash.video.client.record;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.gpucompat.client.ClassicReadback;
import de.lardtom.propwash.ui.client.theme.ThemedToast;
import de.lardtom.propwash.video.record.ClipInfo;
import de.lardtom.propwash.video.record.FrameStats;
import de.lardtom.propwash.video.record.ImageGuard;
import de.lardtom.propwash.video.record.ImagePath;
import de.lardtom.propwash.video.record.RecordingFormat;
import de.lardtom.propwash.video.record.RecordingLibrary;
import de.lardtom.propwash.video.record.RgbaI420;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public final class ActionCamImageSelfTests {
    static final String PREFIX = "video: Action-Cam-Bildweg";
    private static final RecordingFormat FORMAT = RecordingFormat.of(720, 30);
    private static final long RECORD_NANOS = 2_000_000_000L;
    private static final long PROBE_NANOS = 1_000_000_000L;
    private static final int START_TIMEOUT = 600;
    private static final int SAVE_TIMEOUT = 1200;
    private static final int ABORT_TICKS = 60;
    private static final int CPU_TOLERANCE = 2;
    private static final String PHASE = "phase";
    private static final String SINCE = "since";
    private static final String FILES = "files";
    private static final String FAILURES = "failures";
    private static final String PROBE = "probe";

    private ActionCamImageSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(PREFIX + " – normale Aufnahme bleibt auf dem GPU-Weg, Ersatzweg und CPU-Umrechnung lesen dasselbe Bild", 2400,
                ActionCamImageSelfTests::normalStep);
        SelfTest.clientStep(PREFIX + " – Rücklesen ohne Wirkung (Fehlerbild Intel): Aufnahme mit Bild", 2400,
                ctx -> faultStep(ctx, ActionCamCapture.Fault.READBACK, ImagePath.READ, List.of(ImagePath.GPU)));
        SelfTest.clientStep(PREFIX + " – Pack-Durchgang ohne Wirkung: Aufnahme mit Bild", 2400,
                ctx -> faultStep(ctx, ActionCamCapture.Fault.PACK, ImagePath.CPU, List.of(ImagePath.GPU, ImagePath.READ)));
        SelfTest.clientStep(PREFIX + " – beide Durchgänge ohne Wirkung: Abbruch statt grünem Video", 2400,
                ctx -> abortStep(ctx, ActionCamCapture.Fault.PASSES));
    }

    private static StepResult normalStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        ActionCamRecorder recorder = ActionCamRecorder.INSTANCE;
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        switch (phase) {
            case 0 -> {
                StepResult started = start(ctx, ActionCamCapture.Fault.NONE);
                if (started != null) {
                    return started;
                }
                state.put(PHASE, 1);
                return StepResult.pending();
            }
            case 1 -> {
                if (recorder.phase() != ActionCamRecorder.Phase.RECORDING) {
                    return done(StepResult.fail("Aufnahme endete vorzeitig: " + recorder.lastError()));
                }
                if (recorder.recordedNanos() < PROBE_NANOS || recorder.capturedFrames() < 10) {
                    return ctx.tick() > START_TIMEOUT ? done(StepResult.fail("Aufnahmezeit läuft nicht")) : StepResult.pending();
                }
                List<String> failures = failures(ctx);
                ImageGuard guard = recorder.imageGuard();
                if (recorder.imagePath() != ImagePath.GPU || guard.blankFrames() != 0L || !guard.broken().isEmpty()) {
                    failures.add("normale Aufnahme verlässt den GPU-Weg: " + recorder.imagePath() + ", leer " + guard.blankFrames());
                }
                if (hudShows(recorder, ImagePath.READ) || hudShows(recorder, ImagePath.CPU)) {
                    failures.add("HUD meldet einen Ersatzweg ohne Fehler: " + RecordingHud.notices(recorder));
                }
                Probe probe = Probe.start(recorder.capturePass());
                if (probe == null) {
                    return done(StepResult.fail("Aufnahme-Texturen fehlen für den Vergleich"));
                }
                state.put(PROBE, probe);
                state.put(SINCE, ctx.tick());
                state.put(PHASE, 2);
                return StepResult.pending();
            }
            case 2 -> {
                Probe probe = (Probe) state.get(PROBE);
                byte[] gpu = probe.gpu.get();
                if (gpu == null) {
                    return ctx.tick() - (int) state.get(SINCE) > 100 ? done(StepResult.fail("copyTextureToBuffer kommt nicht an")) : StepResult.pending();
                }
                List<String> failures = failures(ctx);
                probe.compare(ctx, gpu, failures);
                recorder.stop(minecraft, ActionCamRecorder.StopReason.USER);
                state.put(SINCE, ctx.tick());
                state.put(PHASE, 3);
                return StepResult.pending();
            }
            default -> {
                if (recorder.phase() != ActionCamRecorder.Phase.IDLE || !recorder.saving().isEmpty()) {
                    return ctx.tick() - (int) state.get(SINCE) > SAVE_TIMEOUT ? done(StepResult.fail("Speichern hängt")) : StepResult.pending();
                }
                List<String> failures = failures(ctx);
                Path file = newFile(ctx, recorder);
                if (file == null) {
                    failures.add("kein Video gespeichert: " + recorder.lastError());
                } else {
                    deleteClip(file);
                }
                if (recorder.finishedDiagnosis().image() != ImagePath.GPU) {
                    failures.add("Diagnose vermerkt Bildweg " + recorder.finishedDiagnosis().image());
                }
                return done(failures.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", failures)));
            }
        }
    }

    static StepResult faultStep(SelfTest.StepContext ctx, ActionCamCapture.Fault fault, ImagePath expected, List<ImagePath> broken) {
        Minecraft minecraft = Minecraft.getInstance();
        ActionCamRecorder recorder = ActionCamRecorder.INSTANCE;
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        switch (phase) {
            case 0 -> {
                StepResult started = start(ctx, fault);
                if (started != null) {
                    return started;
                }
                state.put(PHASE, 1);
                return StepResult.pending();
            }
            case 1 -> {
                if (recorder.phase() != ActionCamRecorder.Phase.RECORDING) {
                    return done(StepResult.fail("Aufnahme endete vorzeitig: " + recorder.lastError()));
                }
                if (recorder.recordedNanos() < RECORD_NANOS) {
                    return ctx.tick() > START_TIMEOUT ? done(StepResult.fail("Aufnahmezeit läuft nicht")) : StepResult.pending();
                }
                List<String> failures = failures(ctx);
                ImageGuard guard = recorder.imageGuard();
                ctx.log("Fehler " + fault + ": Bildweg " + recorder.imagePath().key() + ", leer über " + guard.broken() + ", " + guard.blankFrames()
                        + " leere, " + guard.droppedFrames() + " verworfene, " + guard.acceptedFrames() + " gute Bilder");
                if (recorder.imagePath() != expected || !guard.broken().equals(broken)) {
                    failures.add("Bildweg " + recorder.imagePath() + " nach " + guard.broken() + " statt " + expected + " nach " + broken);
                }
                if (!hudShows(recorder, expected)) {
                    failures.add("HUD ohne Hinweis auf den Ersatzweg: " + RecordingHud.notices(recorder));
                }
                String chatLine = Component.translatable("propwash.video.record.image.switched." + expected.key()).getString();
                if (!chat().contains(chatLine)) {
                    failures.add("Chat ohne „" + chatLine + "“");
                }
                if (recorder.diagnosis().image() != expected) {
                    failures.add("Diagnose vermerkt Bildweg " + recorder.diagnosis().image() + " statt " + expected);
                }
                recorder.stop(minecraft, ActionCamRecorder.StopReason.USER);
                state.put(SINCE, ctx.tick());
                state.put(PHASE, 2);
                return StepResult.pending();
            }
            default -> {
                if (recorder.phase() != ActionCamRecorder.Phase.IDLE || !recorder.saving().isEmpty()) {
                    return ctx.tick() - (int) state.get(SINCE) > SAVE_TIMEOUT ? done(StepResult.fail("Speichern hängt")) : StepResult.pending();
                }
                List<String> failures = failures(ctx);
                FrameStats.Snapshot stats = recorder.stats().last();
                ctx.log(String.format(Locale.ROOT, "Fehler %s: %d Bilder gelesen, letztes Bild oben Y %.0f Cb %.0f Cr %.0f, unten Y %.0f Cb %.0f Cr %.0f, "
                                + "Spanne %.0f", fault, recorder.capturedFrames(), stats.topY(), stats.topCb(), stats.topCr(), stats.bottomY(),
                        stats.bottomCb(), stats.bottomCr(), stats.lumaSpread()));
                Path file = newFile(ctx, recorder);
                if (file == null) {
                    return done(StepResult.fail("kein Video gespeichert: " + recorder.lastError()));
                }
                deleteClip(file);
                if (stats.frames() == 0L || stats.topY() < 16.0 || stats.bottomY() < 16.0 || stats.lumaSpread() < 12.0) {
                    failures.add(String.format(Locale.ROOT, "Video ohne Bild (Y %.0f, Cb %.0f, Cr %.0f, Spanne %.0f): grünes Video wie im Discord",
                            stats.topY(), stats.topCb(), stats.topCr(), stats.lumaSpread()));
                }
                boolean skyAbove = stats.topCb() - stats.bottomCb() > 2.0 || stats.topY() - stats.bottomY() > 8.0;
                if (!skyAbove) {
                    failures.add("Himmel nicht oben im Bild (Ersatzweg spiegelt oder vertauscht Farben?)");
                }
                if (recorder.finishedDiagnosis().image() != expected) {
                    failures.add("Diagnose der fertigen Aufnahme vermerkt Bildweg " + recorder.finishedDiagnosis().image() + " statt " + expected);
                }
                return done(failures.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", failures)));
            }
        }
    }

    static StepResult abortStep(SelfTest.StepContext ctx, ActionCamCapture.Fault fault) {
        Minecraft minecraft = Minecraft.getInstance();
        ActionCamRecorder recorder = ActionCamRecorder.INSTANCE;
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        switch (phase) {
            case 0 -> {
                StepResult started = start(ctx, fault);
                if (started != null) {
                    return started;
                }
                state.put(SINCE, ctx.tick());
                state.put(PHASE, 1);
                return StepResult.pending();
            }
            case 1 -> {
                List<String> failures = failures(ctx);
                if (recorder.phase() == ActionCamRecorder.Phase.RECORDING && recorder.recordedNanos() >= RECORD_NANOS) {
                    recorder.stop(minecraft, ActionCamRecorder.StopReason.USER);
                    failures.add("Aufnahme lief trotz leerer Bilder " + RECORD_NANOS / 1_000_000L + " ms weiter");
                }
                if (recorder.phase() == ActionCamRecorder.Phase.RECORDING) {
                    return ctx.tick() > START_TIMEOUT ? done(StepResult.fail("Aufnahmezeit läuft nicht")) : StepResult.pending();
                }
                int ticks = ctx.tick() - (int) state.get(SINCE);
                ImageGuard guard = recorder.imageGuard();
                ctx.log("Abbruch nach " + ticks + " Ticks: leer über " + guard.broken() + ", " + guard.blankFrames() + " leere, " + guard.droppedFrames()
                        + " verworfene, " + guard.acceptedFrames() + " gute Bilder, Fehler „" + recorder.lastError() + "“");
                if (failures.isEmpty()) {
                    if (ticks > ABORT_TICKS) {
                        failures.add("Abbruch erst nach " + ticks + " Ticks");
                    }
                    if (!guard.broken().equals(List.of(ImagePath.GPU, ImagePath.READ, ImagePath.CPU))) {
                        failures.add("nicht jeder Bildweg versucht: " + guard.broken());
                    }
                    ThemedToast toast = ThemedToast.find(minecraft.gui.toastManager(), ActionCamRecorder.TOAST_TOKEN);
                    String title = Component.translatable("propwash.video.record.toast.aborted").getString();
                    String message = Component.translatable("propwash.video.record.stop.blank_image").getString();
                    if (toast == null || !toast.title().getString().equals(title) || toast.message() == null
                            || !toast.message().getString().equals(message)) {
                        failures.add("Toast „" + title + ": " + message + "“ fehlt: " + (toast == null ? "kein Toast"
                                : toast.title().getString() + ": " + (toast.message() == null ? "" : toast.message().getString())));
                    }
                    String chatLine = Component.translatable("propwash.video.record.image.aborted").getString();
                    if (!chat().contains(chatLine)) {
                        failures.add("Chat ohne „" + chatLine + "“");
                    }
                }
                state.put(SINCE, ctx.tick());
                state.put(PHASE, 2);
                return StepResult.pending();
            }
            default -> {
                if (recorder.phase() != ActionCamRecorder.Phase.IDLE || !recorder.saving().isEmpty()) {
                    return ctx.tick() - (int) state.get(SINCE) > SAVE_TIMEOUT ? done(StepResult.fail("Speichern hängt")) : StepResult.pending();
                }
                List<String> failures = failures(ctx);
                Path file = newFile(ctx, recorder);
                if (file != null) {
                    FrameStats.Snapshot stats = recorder.stats().last();
                    deleteClip(file);
                    failures.add(String.format(Locale.ROOT, "grünes Video gespeichert (%s, letztes Bild Y %.0f Cb %.0f Cr %.0f)", file.getFileName(),
                            stats.topY(), stats.topCb(), stats.topCr()));
                }
                if (recorder.finishedDiagnosis().image() != ImagePath.NONE) {
                    failures.add("Diagnose vermerkt den Abbruch nicht: Bildweg " + recorder.finishedDiagnosis().image());
                } else {
                    ctx.log("Diagnose: " + recorder.finishedDiagnosis().describe());
                }
                return done(failures.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", failures)));
            }
        }
    }

    private static @Nullable StepResult start(SelfTest.StepContext ctx, ActionCamCapture.Fault fault) {
        Minecraft minecraft = Minecraft.getInstance();
        ActionCamRecorder recorder = ActionCamRecorder.INSTANCE;
        if (recorder.phase() != ActionCamRecorder.Phase.IDLE || !recorder.saving().isEmpty()) {
            return ctx.tick() > 200 ? done(StepResult.fail("Rekorder nicht frei: " + recorder.phase())) : StepResult.pending();
        }
        if (!ActionCamCapture.fallbacksAvailable(minecraft.gameRenderer.mainRenderTarget())) {
            return done(StepResult.fail("kein OpenGL: Ersatzweg nicht verfügbar"));
        }
        ctx.state().put(FILES, files(recorder));
        ActionCamCapture.fault = fault;
        recorder.overrideFormat(FORMAT);
        ActionCamRecorder.Result result = recorder.start(minecraft, false);
        if (result != ActionCamRecorder.Result.STARTED) {
            return done(StepResult.fail("Aufnahme startet nicht: " + result));
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> failures(SelfTest.StepContext ctx) {
        return (List<String>) ctx.state().computeIfAbsent(FAILURES, key -> new ArrayList<String>());
    }

    private static boolean hudShows(ActionCamRecorder recorder, ImagePath path) {
        String expected = Component.translatable("propwash.video.record.hud.image." + path.key()).getString();
        for (Component line : RecordingHud.notices(recorder)) {
            if (line.getString().equals(expected)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> chat() {
        try {
            Field field = ChatComponent.class.getDeclaredField("allMessages");
            field.setAccessible(true);
            List<?> messages = (List<?>) field.get(Minecraft.getInstance().gui.hud.getChat());
            List<String> out = new ArrayList<>();
            for (Object message : messages) {
                if (message instanceof GuiMessage gui) {
                    out.add(gui.content().getString());
                }
            }
            return out;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return List.of("? " + e);
        }
    }

    private static Set<Path> files(ActionCamRecorder recorder) {
        Set<Path> out = new HashSet<>();
        for (ClipInfo clip : recorder.library().scanNow()) {
            out.add(clip.file());
        }
        return out;
    }

    private static @Nullable Path newFile(SelfTest.StepContext ctx, ActionCamRecorder recorder) {
        @SuppressWarnings("unchecked")
        Set<Path> before = (Set<Path>) ctx.state().get(FILES);
        for (ClipInfo clip : recorder.library().scanNow()) {
            if (before == null || !before.contains(clip.file())) {
                return clip.file();
            }
        }
        return null;
    }

    private static void deleteClip(Path file) {
        try {
            Files.deleteIfExists(file);
            Files.deleteIfExists(RecordingLibrary.thumbnailOf(file));
        } catch (IOException ignored) {
        }
    }

    private static StepResult done(StepResult result) {
        ActionCamCapture.fault = ActionCamCapture.Fault.NONE;
        ActionCamRecorder recorder = ActionCamRecorder.INSTANCE;
        if (recorder.phase() == ActionCamRecorder.Phase.RECORDING) {
            recorder.stop(Minecraft.getInstance(), ActionCamRecorder.StopReason.USER);
        }
        recorder.overrideFormat(null);
        return result;
    }

    private static final class Probe {
        final AtomicReference<byte @Nullable []> gpu = new AtomicReference<>();
        final RecordingFormat format;
        final ByteBuffer read;
        final ByteBuffer cpu;
        final boolean readOk;
        final boolean frameOk;
        final double readMillis;
        final double cpuMillis;

        private Probe(RecordingFormat format, ByteBuffer read, ByteBuffer cpu, boolean readOk, boolean frameOk, double readMillis, double cpuMillis) {
            this.format = format;
            this.read = read;
            this.cpu = cpu;
            this.readOk = readOk;
            this.frameOk = frameOk;
            this.readMillis = readMillis;
            this.cpuMillis = cpuMillis;
        }

        static @Nullable Probe start(ActionCamCapture capture) {
            GpuTexture packed = capture.packedTexture();
            GpuTexture frame = capture.frameTexture();
            RecordingFormat format = capture.format();
            if (packed == null || frame == null || format == null) {
                return null;
            }
            int bytes = format.frameBytes();
            int width = format.width();
            int height = format.height();
            GpuDevice device = RenderSystem.getDevice();
            GpuBuffer buffer = device.createBuffer(() -> "propwash selftest readback", GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, bytes);
            ByteBuffer read = ByteBuffer.allocateDirect(bytes);
            ByteBuffer rgba = ByteBuffer.allocateDirect(width * height * 4);
            ByteBuffer cpu = ByteBuffer.allocateDirect(bytes);
            Probe probe;
            ClassicReadback reader = new ClassicReadback();
            try {
                long started = System.nanoTime();
                boolean readOk = reader.read(packed, width / 4, height * 3 / 2, read);
                long between = System.nanoTime();
                boolean frameOk = reader.read(frame, width, height, rgba);
                RgbaI420.convert(rgba, width, height, cpu);
                long finished = System.nanoTime();
                probe = new Probe(format, read, cpu, readOk, frameOk, (between - started) / 1.0E6, (finished - between) / 1.0E6);
            } finally {
                reader.close();
            }
            device.createCommandEncoder().copyTextureToBuffer(packed, buffer, 0L, () -> {
                try (GpuBufferSlice.MappedView view = buffer.map(0L, bytes, true, false)) {
                    byte[] data = new byte[bytes];
                    view.data().get(0, data);
                    probe.gpu.set(data);
                } finally {
                    buffer.close();
                }
            }, 0);
            return probe;
        }

        void compare(SelfTest.StepContext ctx, byte[] gpu, List<String> failures) {
            int readDiff = 0;
            int cpuDiff = 0;
            int cpuMax = 0;
            for (int i = 0; i < gpu.length; i++) {
                int expected = gpu[i] & 0xFF;
                if ((this.read.get(i) & 0xFF) != expected) {
                    readDiff++;
                }
                int delta = Math.abs((this.cpu.get(i) & 0xFF) - expected);
                if (delta != 0) {
                    cpuDiff++;
                    cpuMax = Math.max(cpuMax, delta);
                }
            }
            ctx.log(String.format(Locale.ROOT, "%s: copyTextureToBuffer gegen Ersatzweg %d von %d Bytes anders (%.2f ms); CPU-Umrechnung %d Bytes anders, "
                            + "höchstens %d (Lesen und Umrechnen %.2f ms)", this.format.label(), readDiff, gpu.length, this.readMillis, cpuDiff, cpuMax,
                    this.cpuMillis));
            if (!this.readOk || !this.frameOk) {
                failures.add("Ersatzweg liest nicht (Pack " + this.readOk + ", Bild " + this.frameOk + ")");
            }
            if (ImageGuard.blank(ByteBuffer.wrap(gpu), this.format)) {
                failures.add("GPU-Weg liefert ein leeres Bild");
            }
            if (readDiff != 0) {
                failures.add("Ersatzweg liest " + readDiff + " Bytes anders als copyTextureToBuffer");
            }
            if (cpuMax > CPU_TOLERANCE) {
                failures.add("CPU-Umrechnung weicht um bis zu " + cpuMax + " vom Pack-Shader ab");
            }
        }
    }
}
