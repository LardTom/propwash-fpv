package de.lardtom.propwash.video.client.record;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.ActionCamSpec;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.drone.net.CameraChargeC2S;
import de.lardtom.propwash.input.DeviceProfile;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.KeyRepeatFilter;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.input.SwitchAssignment;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.client.InputKeys;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemedToast;
import de.lardtom.propwash.video.FisheyeLens;
import de.lardtom.propwash.video.ImageArea;
import de.lardtom.propwash.video.client.FpvImage;
import de.lardtom.propwash.video.client.FpvLens;
import de.lardtom.propwash.video.record.Backpressure;
import de.lardtom.propwash.video.record.BuiltinEncoder;
import de.lardtom.propwash.video.record.CamPower;
import de.lardtom.propwash.video.record.CameraLook;
import de.lardtom.propwash.video.record.ClipInfo;
import de.lardtom.propwash.video.record.EncoderChoice;
import de.lardtom.propwash.video.record.FrameEncoder;
import de.lardtom.propwash.video.record.FrameStats;
import de.lardtom.propwash.video.record.FrameClock;
import de.lardtom.propwash.video.record.PipelineStats;
import de.lardtom.propwash.video.record.RecordTrigger;
import de.lardtom.propwash.video.record.RecordingDiagnosis;
import de.lardtom.propwash.video.record.RecordingFormat;
import de.lardtom.propwash.video.record.RecordingJob;
import de.lardtom.propwash.video.record.RecordingQuality;
import de.lardtom.propwash.video.record.RecordingLibrary;
import de.lardtom.propwash.video.record.Stabilizer;
import de.lardtom.propwash.video.record.Thumbnail;
import de.lardtom.propwash.video.record.external.HardwareEncoders;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class ActionCamRecorder {
    public static final ActionCamRecorder INSTANCE = new ActionCamRecorder();
    public static final String TOAST_TOKEN = "propwash_action_cam";
    public static final float ASPECT = 16.0F / 9.0F;
    private static final long CHARGE_SYNC_NANOS = 2_000_000_000L;
    private static final long HOOK_TIMEOUT_NANOS = 1_500_000_000L;
    private static final String NO_FRAMES = "no frames captured";
    private static final long SPOOL_CAP_BYTES = 16L * 1024L * 1024L * 1024L;
    private static final long SPOOL_FREE_RESERVE = 2L * 1024L * 1024L * 1024L;
    private static final float FLIGHT_BATTERY_EMPTY = 0.001F;
    private static final DateTimeFormatter NAMES = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.ROOT);
    public static final int REC_ACCENT = 0xFFE53935;
    private static final long START_TOAST_MILLIS = 3000L;
    private static final long STOP_TOAST_MILLIS = 3500L;
    private static final long FAIL_TOAST_MILLIS = 4500L;
    private static final long SAVED_TOAST_MILLIS = 6000L;
    private static final long AUDIO_SLACK = AudioCapture.RATE * 12L / 100L;
    private static final long DIAGNOSIS_MILLIS = 1000L;

    private final ActionCamCapture capture = new ActionCamCapture();
    private final RecordTrigger trigger = new RecordTrigger();
    private final AudioCapture audioCapture = new AudioCapture();
    private final short[] audioScratch = new short[AudioCapture.RATE * 2];
    private long audioSubmitted;
    private long audioSkip;
    private @Nullable RecordingLibrary library;
    private @Nullable Path thumbnail;
    private boolean thumbnailFirst;
    private boolean thumbnailFinal;
    private final Stabilizer stabilizer = new Stabilizer();
    private final float[] tangents = new float[4];
    private final List<RecordingJob> saving = new ArrayList<>();
    private final FrameStats stats = new FrameStats();
    private final KeyRepeatFilter recordKey = new KeyRepeatFilter();
    private Phase phase = Phase.IDLE;
    private @Nullable RecordingJob job;
    private @Nullable FrameClock clock;
    private @Nullable RecordingFormat format;
    private @Nullable AccessoryType camera;
    private @Nullable QuadcopterEntity drone;
    private Stabilizer.Mode stabilization = Stabilizer.Mode.OFF;
    private RecordingQuality quality = RecordingQuality.DEFAULT;
    private boolean automatic;
    private boolean lastArmed;
    private long lastCaptureElapsed;
    private long lastDrainElapsed;
    private long lastChargeSync;
    private float cameraCharge = CamPower.FULL;
    private float sentCharge = CamPower.FULL;
    private long stopEndSlot;
    private long capturedFrames;
    private @Nullable Path lastSaved;
    private @Nullable String lastError;
    private @Nullable RecordingFormat testFormat;
    private boolean cleaned;
    private boolean shutdownHook;
    private long lastHookNanos;
    private long startNanos;
    private long renderFrame;
    private long lastActiveFrameNanos;
    private long lastTakenSlot = -1L;
    private long lastHeapSample;
    private long lastDiagnosis;
    private PipelineStats.@Nullable Snapshot diagnosisBase;
    private int diagnosisBacklog;
    private RecordingDiagnosis diagnosis = RecordingDiagnosis.NONE;
    private RecordingDiagnosis worstDiagnosis = RecordingDiagnosis.NONE;
    private RecordingDiagnosis lastWorst = RecordingDiagnosis.NONE;
    private @Nullable RecordingJob lastFinishedJob;
    private float strength = Stabilizer.DEFAULT_STRENGTH;
    private @Nullable RecordingFormat warmedFormat;
    private boolean prewarm;
    private HardwareEncoders.Status startStatus = HardwareEncoders.Status.NONE;

    private ActionCamRecorder() {
    }

    public Phase phase() {
        return this.phase;
    }

    public boolean recording() {
        return this.phase == Phase.RECORDING;
    }

    public boolean paused() {
        FrameClock c = this.clock;
        return this.phase == Phase.RECORDING && c != null && c.paused();
    }

    public long recordedNanos() {
        FrameClock c = this.clock;
        return c == null ? 0L : c.elapsedNanos(Util.getNanos());
    }

    public @Nullable RecordingFormat format() {
        return this.format;
    }

    public @Nullable AccessoryType camera() {
        return this.camera;
    }

    public RecordingQuality quality() {
        return this.quality;
    }

    public Stabilizer.Mode stabilization() {
        return this.stabilization;
    }

    public float cameraCharge() {
        return this.cameraCharge;
    }

    public float[] lastTangents() {
        return this.tangents.clone();
    }

    public long capturedFrames() {
        return this.capturedFrames;
    }

    public long droppedFrames() {
        RecordingJob current = this.job;
        return current == null ? 0L : current.progress().droppedFrames();
    }

    public @Nullable Path lastSaved() {
        return this.lastSaved;
    }

    public @Nullable String lastError() {
        return this.lastError;
    }

    public FrameStats stats() {
        return this.stats;
    }

    public ActionCamCapture capturePass() {
        return this.capture;
    }

    public List<RecordingJob.Progress> saving() {
        List<RecordingJob.Progress> out = new ArrayList<>(this.saving.size());
        for (RecordingJob pending : this.saving) {
            out.add(pending.progress());
        }
        return out;
    }

    public RecordingJob.@Nullable Progress current() {
        RecordingJob current = this.job;
        return current == null ? null : current.progress();
    }

    public RecordingDiagnosis diagnosis() {
        return this.diagnosis;
    }

    public float strength() {
        return this.strength;
    }

    public String encoderLabel() {
        RecordingJob current = this.job;
        return current == null ? "" : current.encoderLabel();
    }

    public HardwareEncoders.Status startStatus() {
        return this.startStatus;
    }

    public boolean hardwareRecording() {
        RecordingJob current = this.job;
        return current != null && current.externalEncoder();
    }

    public static HardwareEncoders.Settings hardwareSettings(Minecraft minecraft) {
        RecordingConfig config = RecordingConfig.get();
        return new HardwareEncoders.Settings(config.encoder == EncoderChoice.AUTO, config.ffmpegPath, ffmpegFolders(minecraft));
    }

    public static List<Path> ffmpegFolders(Minecraft minecraft) {
        return List.of(minecraft.gameDirectory.toPath().resolve(Constants.MOD_ID).resolve("ffmpeg"));
    }

    public static HardwareEncoders.Status hardwareStatus(Minecraft minecraft, RecordingFormat format) {
        return HardwareEncoders.status(hardwareSettings(minecraft), format);
    }

    public @Nullable PipelineStats pipelineStats() {
        RecordingJob current = this.job;
        return current == null ? null : current.stats();
    }

    public @Nullable Path currentFile() {
        RecordingJob current = this.job;
        return current == null ? null : current.file();
    }

    public List<SavingJob> savingJobs() {
        List<SavingJob> out = new ArrayList<>(this.saving.size());
        for (RecordingJob pending : this.saving) {
            out.add(new SavingJob(pending.file(), pending.format(), pending.progress()));
        }
        return out;
    }

    public RecordingLibrary library() {
        RecordingLibrary existing = this.library;
        if (existing == null) {
            existing = new RecordingLibrary(directory(Minecraft.getInstance()), Util.ioPool());
            this.library = existing;
        }
        return existing;
    }

    public void overrideFormat(@Nullable RecordingFormat format) {
        this.testFormat = format;
    }

    public static Path directory(Minecraft minecraft) {
        return minecraft.gameDirectory.toPath().resolve(Constants.MOD_ID).resolve("recordings");
    }

    public static Optional<AccessoryType> cameraOf(@Nullable QuadcopterEntity drone) {
        if (drone == null) {
            return Optional.empty();
        }
        return drone.build().accessories().camera();
    }

    public static RecordingFormat formatFor(ActionCamSpec spec) {
        RecordingConfig config = RecordingConfig.get();
        return RecordingFormat.forCamera(spec, config.height, config.fps);
    }

    public static Stabilizer.Mode stabilizationFor(ActionCamSpec spec) {
        return spec.stabilization() ? RecordingConfig.get().stabilization : Stabilizer.Mode.OFF;
    }

    public Result toggle(Minecraft minecraft) {
        if (this.phase == Phase.RECORDING) {
            this.stop(minecraft, StopReason.USER);
            return Result.STOPPED;
        }
        return this.start(minecraft, false);
    }

    public Result start(Minecraft minecraft, boolean automatic) {
        Result result = this.tryStart(minecraft, automatic);
        if (result != Result.STARTED && !automatic) {
            toast(minecraft, Component.translatable("propwash.video.record.toast.not_started"), Component.translatable(result.translationKey()),
                    Palette.WARNING, FAIL_TOAST_MILLIS);
        }
        return result;
    }

    private Result tryStart(Minecraft minecraft, boolean automatic) {
        if (this.phase != Phase.IDLE) {
            return this.phase == Phase.RECORDING ? Result.ALREADY : Result.BUSY;
        }
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity flown = session.drone();
        if (!session.isActive() || flown == null) {
            return Result.NO_SESSION;
        }
        Optional<AccessoryType> type = cameraOf(flown);
        Optional<ActionCamSpec> spec = type.flatMap(AccessoryType::camera);
        if (type.isEmpty() || spec.isEmpty()) {
            return Result.NO_CAMERA;
        }
        if (!session.isFpv()) {
            return Result.NOT_FPV;
        }
        float charge = flown.cameraCharge();
        if (CamPower.empty(charge, spec.get())) {
            return Result.CAMERA_EMPTY;
        }
        if (!spec.get().ownBattery() && flown.batteryCharge() <= FLIGHT_BATTERY_EMPTY) {
            return Result.NO_POWER;
        }
        if (this.capture.failure() != null) {
            this.capture.retry();
        }
        RecordingFormat chosen = this.testFormat != null ? this.testFormat : formatFor(spec.get());
        Path dir = directory(minecraft);
        this.cleanOnce(dir);
        Path file = dir.resolve("propwash_" + LocalDateTime.now().format(NAMES) + ".mp4");
        RecordingConfig config = RecordingConfig.get();
        long memory = config.memoryCapBytes();
        if (!this.saving.isEmpty()) {
            memory = Math.max(RecordingConfig.MIN_MEMORY_MB * 1024L * 1024L, memory / 2L);
        }
        HardwareEncoders.Status hardware = hardwareStatus(minecraft, chosen);
        FrameEncoder.Factory encoder = HardwareEncoders.factory(hardware).orElse(BuiltinEncoder.FACTORY);
        RecordingJob started;
        try {
            started = RecordingJob.start(new RecordingJob.Options(file, dir.resolve(".spool"), chosen, config.threads(), memory,
                    spoolCap(dir), config.audio, config.quality, encoder));
        } catch (IOException | RuntimeException e) {
            Constants.LOG.error("Action-Cam: Aufnahme konnte nicht starten", e);
            this.lastError = e.getMessage();
            return Result.FAILED;
        }
        this.ensureShutdownHook();
        this.job = started;
        this.startStatus = hardware;
        this.format = chosen;
        this.camera = type.get();
        this.drone = flown;
        this.stabilization = stabilizationFor(spec.get());
        this.strength = config.strength();
        this.quality = started.quality();
        this.diagnosis = RecordingDiagnosis.NONE;
        this.worstDiagnosis = RecordingDiagnosis.NONE;
        this.diagnosisBase = null;
        this.diagnosisBacklog = 0;
        this.lastDiagnosis = Util.getMillis();
        this.stabilizer.reset();
        this.automatic = automatic;
        this.cameraCharge = charge;
        this.sentCharge = charge;
        this.capturedFrames = 0L;
        this.lastError = null;
        FrameClock c = new FrameClock(chosen.fps());
        long now = Util.getNanos();
        c.start(now);
        this.startNanos = now;
        this.clock = c;
        this.lastCaptureElapsed = 0L;
        this.lastActiveFrameNanos = 0L;
        this.lastTakenSlot = -1L;
        this.lastDrainElapsed = 0L;
        this.lastChargeSync = now;
        this.stats.reset();
        this.thumbnail = RecordingLibrary.thumbnailOf(file);
        this.thumbnailFirst = false;
        this.thumbnailFinal = false;
        this.audioSubmitted = 0L;
        this.audioSkip = 0L;
        if (started.hasAudio()) {
            this.audioCapture.listen(flown.getId());
        } else {
            this.audioCapture.stop();
        }
        this.phase = Phase.RECORDING;
        Component detail = Component.translatable("propwash.video.record.started", Component.translatable(type.get().translationKey()),
                chosen.label(), Component.translatable(this.stabilization.translationKey()));
        toast(minecraft, Component.translatable(automatic ? "propwash.video.record.toast.started_auto" : "propwash.video.record.toast.started"),
                detail, REC_ACCENT, START_TOAST_MILLIS);
        return Result.STARTED;
    }

    public void stop(Minecraft minecraft, StopReason reason) {
        if (this.phase != Phase.RECORDING) {
            return;
        }
        FrameClock c = this.clock;
        this.pumpAudio();
        this.audioCapture.stop();
        this.stopEndSlot = c == null ? 0L : c.endSlot(Util.getNanos());
        this.syncCharge(true);
        this.phase = Phase.STOPPING;
        if (reason != StopReason.DISCONNECT) {
            toast(minecraft, Component.translatable(reason.error ? "propwash.video.record.toast.aborted" : "propwash.video.record.toast.stopped"),
                    Component.translatable(reason.translationKey()), reason.accent(), STOP_TOAST_MILLIS);
        }
        if (this.capture.inFlight() == 0) {
            this.completeStop();
        }
    }

    public void onRenderedFrame(RenderTarget main) {
        this.lastHookNanos = Util.getNanos();
        this.renderFrame++;
        switch (this.phase) {
            case RECORDING -> this.captureFrame(main);
            case STOPPING -> {
                if (this.capture.inFlight() == 0) {
                    this.completeStop();
                }
            }
            case IDLE -> {
                if (this.capture.holdsResources() && this.capture.inFlight() == 0) {
                    this.capture.release();
                }
                if (this.prewarm && !this.capture.warmed()) {
                    this.capture.prewarm(main);
                }
            }
        }
    }

    private void captureFrame(RenderTarget main) {
        RecordingJob current = this.job;
        FrameClock c = this.clock;
        RecordingFormat target = this.format;
        AccessoryType type = this.camera;
        if (current == null || c == null || target == null || type == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        long now = Util.getNanos();
        if (!this.canCapture(minecraft)) {
            c.pause(now);
            this.lastActiveFrameNanos = 0L;
            return;
        }
        c.resume(now);
        PipelineStats pipeline = current.stats();
        if (this.lastActiveFrameNanos > 0L) {
            pipeline.renderFrame(now - this.lastActiveFrameNanos);
        }
        this.lastActiveFrameNanos = now;
        if (!c.due(now)) {
            return;
        }
        ActionCamSpec spec = type.camera().orElse(ActionCamSpec.LIGHT);
        long elapsed = c.elapsedNanos(now);
        double seconds = Math.max(0L, elapsed - this.lastCaptureElapsed) * 1.0E-9;
        this.lastCaptureElapsed = elapsed;
        Camera camera = minecraft.gameRenderer.mainCamera();
        float fovY = camera.getFov();
        if (!(fovY > 1.0F && fovY < 179.0F)) {
            fovY = 70.0F;
        }
        float aspect = main.height > 0 ? main.width / (float) main.height : ASPECT;
        float tanSourceY = (float) Math.tan(Math.toRadians(fovY) * 0.5);
        float tanSourceX = tanSourceY * aspect;
        float zoom = Stabilizer.zoom(this.stabilization, this.strength);
        ImageArea area = FpvImage.area(main.width, main.height);
        FisheyeLens lens = FpvLens.INSTANCE.rendered();
        float imageX = lens != null ? (float) lens.flatTangent() : (float) area.imageTangentX(tanSourceX);
        float wideY = lens != null ? imageX * main.height / main.width : (float) area.wideTangentY(tanSourceX, tanSourceY);
        float baseY = Math.min(wideY, imageX / ASPECT);
        float tanOutY = baseY / zoom;
        float tanOutX = tanOutY * ASPECT;
        this.stabilizer.update(this.stabilization, this.strength, camera.rotation(), seconds, tanSourceX, tanSourceY, tanOutX, tanOutY);
        this.tangents[0] = tanOutX;
        this.tangents[1] = tanOutY;
        this.tangents[2] = tanSourceX;
        this.tangents[3] = tanSourceY;
        long slot = c.take(now);
        if (slot < 0L) {
            return;
        }
        pipeline.slot(this.lastTakenSlot < 0L ? 0L : slot - this.lastTakenSlot - 1L);
        this.lastTakenSlot = slot;
        if (!Backpressure.keep(slot, current.captureStride())) {
            pipeline.decimated();
            pipeline.captured(Util.getNanos() - now);
            return;
        }
        float sharpness = CameraLook.sharpness(spec);
        float noise = CameraLook.grain(spec, RecordingConfig.get().grain);
        float seed = (float) (slot % 997L + 1L);
        long issuedFrame = this.renderFrame;
        boolean issued = this.capture.capture(main, target, this.stabilizer.warp(), this.tangents, sharpness, noise, seed,
                view -> this.accept(current, target, slot, issuedFrame, view));
        pipeline.captured(Util.getNanos() - now);
        if (!issued && this.capture.ringFull()) {
            pipeline.ringFull();
            current.countDrop();
        }
        if (this.capture.failure() != null) {
            this.lastError = this.capture.failure();
            this.stop(minecraft, StopReason.ERROR);
        }
    }

    private void accept(RecordingJob target, RecordingFormat frameFormat, long slot, long issuedFrame, GpuBufferSlice.MappedView view) {
        long started = Util.getNanos();
        ByteBuffer buffer = target.acquire();
        if (buffer == null) {
            if (target.recording()) {
                target.stats().memoryFull();
            }
            return;
        }
        buffer.put(0, view.data(), 0, frameFormat.frameBytes());
        this.stats.sample(buffer, frameFormat, slot);
        this.thumbnailFrom(buffer, frameFormat, slot);
        this.capturedFrames++;
        target.submit(slot, buffer);
        target.stats().readback(Util.getNanos() - started, this.renderFrame - issuedFrame);
    }

    private void thumbnailFrom(ByteBuffer i420, RecordingFormat frameFormat, long slot) {
        Path target = this.thumbnail;
        if (target == null || this.thumbnailFinal) {
            return;
        }
        boolean late = slot >= frameFormat.fps();
        if (this.thumbnailFirst && !late) {
            return;
        }
        this.thumbnailFirst = true;
        this.thumbnailFinal = late;
        int[] pixels;
        try {
            pixels = Thumbnail.fromI420(i420, frameFormat.width(), frameFormat.height(), Thumbnail.WIDTH, Thumbnail.HEIGHT);
        } catch (RuntimeException e) {
            Constants.LOG.warn("Action-Cam: Vorschaubild nicht erzeugt: {}", e.getMessage());
            return;
        }
        RecordingThumbnails.write(pixels, Thumbnail.WIDTH, Thumbnail.HEIGHT, target);
    }

    private boolean canCapture(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.isPaused()) {
            return false;
        }
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity flown = session.drone();
        return session.isFpv() && flown != null && flown == this.drone && minecraft.getCameraEntity() == flown;
    }

    private void completeStop() {
        RecordingJob current = this.job;
        this.lastFinishedJob = current;
        this.lastWorst = this.worstDiagnosis;
        this.diagnosis = RecordingDiagnosis.NONE;
        if (current != null) {
            current.finish(this.stopEndSlot);
            this.saving.add(current);
        }
        this.job = null;
        this.clock = null;
        this.drone = null;
        this.phase = Phase.IDLE;
        this.stabilizer.reset();
        this.capture.release();
        this.audioCapture.stop();
    }

    public void onClientTick(Minecraft minecraft) {
        if (InputKeys.pressed(RecordingKeys.RECORD, this.recordKey)) {
            if (this.flashbackCamMounted()) {
                FlashbackCam.INSTANCE.toggle(minecraft);
            } else {
                this.toggle(minecraft);
            }
        }
        this.radioSwitch(minecraft);
        this.warmUp(minecraft);
        this.autoRecord(minecraft);
        if (this.phase == Phase.RECORDING) {
            this.pumpAudio();
            this.sampleHeap();
            this.diagnose();
            this.watchRecording(minecraft);
        } else if (this.phase == Phase.STOPPING && this.capture.inFlight() == 0) {
            this.completeStop();
        }
        this.pollSaving(minecraft);
    }

    private void pumpAudio() {
        RecordingJob current = this.job;
        FrameClock c = this.clock;
        if (current == null || c == null || !current.hasAudio() || !this.audioCapture.active()) {
            return;
        }
        int count = this.audioCapture.drain(this.audioScratch);
        if (c.paused()) {
            return;
        }
        int start = 0;
        if (this.audioSkip > 0L) {
            start = (int) Math.min(count, this.audioSkip);
            this.audioSkip -= start;
        }
        if (count > start) {
            current.submitAudio(this.audioScratch, start, count - start);
            this.audioSubmitted += count - start;
        }
        long expected = c.elapsedNanos(Util.getNanos()) * AudioCapture.RATE / 1_000_000_000L;
        long lag = expected - this.audioSubmitted;
        if (lag > AUDIO_SLACK) {
            int silence = (int) Math.min(Integer.MAX_VALUE, lag - AUDIO_SLACK / 2);
            current.submitSilence(silence);
            this.audioSubmitted += silence;
        } else if (lag < -AUDIO_SLACK) {
            this.audioSkip = -lag - AUDIO_SLACK / 2;
        }
    }

    private void diagnose() {
        RecordingJob current = this.job;
        long now = Util.getMillis();
        if (current == null || now - this.lastDiagnosis < DIAGNOSIS_MILLIS) {
            return;
        }
        this.lastDiagnosis = now;
        PipelineStats.Snapshot snapshot = current.stats().snapshot();
        PipelineStats.Snapshot base = this.diagnosisBase;
        this.diagnosisBase = snapshot;
        if (base == null || this.paused()) {
            return;
        }
        RecordingJob.Backlog backlog = current.backlog();
        RecordingDiagnosis next = RecordingDiagnosis.of(snapshot.since(base), backlog, this.diagnosisBacklog, encoderTip(current));
        this.diagnosisBacklog = backlog.queued();
        if (next.limit() != this.diagnosis.limit() && next.limit() != RecordingDiagnosis.Limit.NONE) {
            Constants.LOG.info("Action-Cam: Engpass {}", next.describe());
        }
        this.diagnosis = next;
        if (severity(next) > severity(this.worstDiagnosis)) {
            this.worstDiagnosis = next;
        }
    }

    private static RecordingDiagnosis.Tip encoderTip(RecordingJob current) {
        EncoderChoice choice = RecordingConfig.get().encoder;
        boolean ffmpegMissing = !current.hardware() && choice == EncoderChoice.AUTO
                && hardwareStatus(Minecraft.getInstance(), current.format()).state() == HardwareEncoders.State.NO_FFMPEG;
        return RecordingDiagnosis.encoderTip(current.hardware(), choice, ffmpegMissing, current.format());
    }

    private static int severity(RecordingDiagnosis diagnosis) {
        return switch (diagnosis.limit()) {
            case NONE -> 0;
            case GAME -> 1;
            case ENCODER -> 2;
            case READBACK, DISK, MEMORY -> 3;
        };
    }

    private void warmUp(Minecraft minecraft) {
        if (this.phase != Phase.IDLE) {
            return;
        }
        PilotSession session = PilotSession.INSTANCE;
        if (!session.isActive() || !session.isFpv()) {
            return;
        }
        Optional<ActionCamSpec> spec = cameraOf(session.drone()).flatMap(AccessoryType::camera);
        if (spec.isEmpty()) {
            return;
        }
        this.prewarm = true;
        RecordingFormat planned = this.testFormat != null ? this.testFormat : formatFor(spec.get());
        if (RecordingConfig.get().encoder == EncoderChoice.AUTO && !planned.equals(this.warmedFormat)) {
            this.warmedFormat = planned;
            hardwareStatus(minecraft, planned);
        }
    }

    private void sampleHeap() {
        RecordingJob current = this.job;
        long now = Util.getMillis();
        if (current != null && now - this.lastHeapSample >= 500L) {
            this.lastHeapSample = now;
            current.stats().sampleHeap();
        }
    }

    public long audioSubmitted() {
        return this.audioSubmitted;
    }

    private void radioSwitch(Minecraft minecraft) {
        RadioState radio = RadioState.INSTANCE;
        RecordTrigger.Action action = this.trigger.update(recordControl(), PilotSession.INSTANCE.isActive(), radio.isActive(SwitchFunction.RECORD));
        if (action != RecordTrigger.Action.NONE && this.flashbackCamMounted()) {
            FlashbackCam cam = FlashbackCam.INSTANCE;
            switch (action) {
                case START -> {
                    if (!cam.recording()) {
                        cam.start(minecraft, false);
                    }
                }
                case STOP -> cam.stop(minecraft, FlashbackCam.StopReason.USER);
                case TOGGLE -> cam.toggle(minecraft);
                case NONE -> {
                }
            }
            return;
        }
        switch (action) {
            case START -> {
                if (this.phase == Phase.IDLE) {
                    this.start(minecraft, false);
                }
            }
            case STOP -> {
                if (this.phase == Phase.RECORDING) {
                    this.stop(minecraft, StopReason.USER);
                }
            }
            case TOGGLE -> this.toggle(minecraft);
            case NONE -> {
            }
        }
    }

    private boolean flashbackCamMounted() {
        PilotSession session = PilotSession.INSTANCE;
        return this.phase == Phase.IDLE && session.isActive() && FlashbackCam.mounted(session.drone());
    }

    public static RecordTrigger.Kind recordControl() {
        RadioState radio = RadioState.INSTANCE;
        if (!radio.connected() || radio.deviceGuid() == null || radio.deviceGuid().isEmpty()) {
            return RecordTrigger.Kind.NONE;
        }
        DeviceProfile profile = InputConfig.get().profile(radio.deviceGuid());
        if (profile == null) {
            return RecordTrigger.Kind.SWITCH;
        }
        SwitchAssignment binding = profile.binding(SwitchFunction.RECORD);
        if (binding == null) {
            return RecordTrigger.Kind.NONE;
        }
        return switch (binding.source) {
            case NONE -> RecordTrigger.Kind.NONE;
            case BUTTON -> binding.toggle ? RecordTrigger.Kind.LATCH : RecordTrigger.Kind.BUTTON;
            case AXIS -> binding.toggle ? RecordTrigger.Kind.LATCH : RecordTrigger.Kind.SWITCH;
        };
    }

    private void autoRecord(Minecraft minecraft) {
        PilotSession session = PilotSession.INSTANCE;
        boolean armed = session.isActive() && session.armed();
        if (RecordingConfig.get().autoRecord && session.isFpv() && cameraOf(session.drone()).isPresent()) {
            if (armed && !this.lastArmed && this.phase == Phase.IDLE) {
                this.start(minecraft, true);
            } else if (!armed && this.lastArmed && this.phase == Phase.RECORDING && this.automatic) {
                this.stop(minecraft, StopReason.DISARMED);
            }
        }
        this.lastArmed = armed;
    }

    private void watchRecording(Minecraft minecraft) {
        RecordingJob current = this.job;
        if (current == null || !current.running()) {
            RecordingJob.Progress progress = current == null ? null : current.progress();
            this.lastError = progress == null ? "" : progress.error();
            this.phase = Phase.IDLE;
            this.audioCapture.stop();
            if (current != null) {
                this.saving.add(current);
            }
            this.job = null;
            this.clock = null;
            return;
        }
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity flown = session.drone();
        if (!session.isActive() || flown == null || flown != this.drone || flown.isRemoved()) {
            this.stop(minecraft, StopReason.SESSION_END);
            return;
        }
        AccessoryType type = this.camera;
        ActionCamSpec spec = type == null ? null : type.camera().orElse(null);
        if (spec == null || cameraOf(flown).orElse(null) != type) {
            this.stop(minecraft, StopReason.SESSION_END);
            return;
        }
        FrameClock c = this.clock;
        long now = Util.getNanos();
        long elapsed = c == null ? 0L : c.elapsedNanos(now);
        if (elapsed > HOOK_TIMEOUT_NANOS && this.lastHookNanos < this.startNanos) {
            this.lastError = "render hook missing";
            Constants.LOG.warn("Action-Cam: Render-Hook läuft nicht (anderer Renderer?), Aufnahme gestoppt");
            this.stop(minecraft, StopReason.ERROR);
            return;
        }
        if (spec.ownBattery()) {
            RecordingFormat target = this.format;
            double seconds = Math.max(0L, elapsed - this.lastDrainElapsed) * 1.0E-9;
            this.lastDrainElapsed = elapsed;
            this.cameraCharge = CamPower.drain(this.cameraCharge, spec, target == null ? RecordingFormat.DEFAULT.height() : target.height(), seconds);
            this.syncCharge(false);
            if (CamPower.empty(this.cameraCharge, spec)) {
                this.stop(minecraft, StopReason.CAMERA_BATTERY);
            }
        } else if (flown.batteryCharge() <= FLIGHT_BATTERY_EMPTY || !flown.hasBattery()) {
            this.stop(minecraft, StopReason.FLIGHT_BATTERY);
        }
    }

    private void syncCharge(boolean force) {
        QuadcopterEntity flown = this.drone;
        AccessoryType type = this.camera;
        if (flown == null || type == null || !type.camera().map(ActionCamSpec::ownBattery).orElse(false)) {
            return;
        }
        long now = Util.getNanos();
        if (!force && now - this.lastChargeSync < CHARGE_SYNC_NANOS) {
            return;
        }
        this.lastChargeSync = now;
        if (this.cameraCharge < this.sentCharge) {
            this.sentCharge = this.cameraCharge;
            ClientNet.sendToServer(new CameraChargeC2S(flown.getId(), this.cameraCharge));
        }
    }

    private void pollSaving(Minecraft minecraft) {
        for (int i = this.saving.size() - 1; i >= 0; i--) {
            RecordingJob pending = this.saving.get(i);
            RecordingJob.Progress progress = pending.progress();
            if (!progress.finished()) {
                continue;
            }
            this.saving.remove(i);
            this.announce(minecraft, pending, progress);
        }
    }

    private void announce(Minecraft minecraft, RecordingJob finished, RecordingJob.Progress progress) {
        this.library().refresh();
        Path plannedThumbnail = RecordingLibrary.thumbnailOf(finished.file());
        if (progress.state() == RecordingJob.State.DONE && progress.result() != null) {
            Path file = progress.result();
            this.lastSaved = file;
            RecordingThumbnails.move(plannedThumbnail, RecordingLibrary.thumbnailOf(file));
            RecordingFormat saved = finished.format();
            long seconds = progress.totalSlots() / Math.max(1, saved.fps());
            String duration = ClipInfo.duration(seconds);
            String size = ClipInfo.size(progress.bytesWritten());
            MutableComponent link = Component.literal(file.getFileName().toString())
                    .withStyle(ChatFormatting.UNDERLINE)
                    .withStyle(style -> style.withClickEvent(new ClickEvent.OpenFile(file.toAbsolutePath())));
            Component message = Component.translatable("propwash.video.record.saved", link, duration, saved.label(), size);
            if (minecraft.level != null) {
                minecraft.showDebugChat(message);
            }
            toast(minecraft, Component.translatable("propwash.video.record.saved.title"),
                    Component.translatable("propwash.video.record.toast.saved", duration, saved.label(), size), Palette.SUCCESS, SAVED_TOAST_MILLIS);
            PipelineStats.Snapshot measured = finished.stats().snapshot();
            Constants.LOG.info("Action-Cam: {} {} mit {}: {}", saved.label(), file.getFileName(), finished.encoderLabel(), measured.summary());
            if (finished == this.lastFinishedJob && this.lastWorst.limit() != RecordingDiagnosis.Limit.NONE) {
                Constants.LOG.info("Action-Cam: stärkster Engpass {}", this.lastWorst.describe());
            }
            if (finished.encoderFailed()) {
                HardwareEncoders.markBroken(progress.error());
                Constants.LOG.warn("Action-Cam: Hardware-Encoder ausgefallen, Video bis dahin gespeichert: {}", progress.error());
                toast(minecraft, Component.translatable("propwash.video.record.toast.hardware_failed"),
                        Component.translatable("propwash.video.record.toast.hardware_failed.detail"), Palette.WARNING, FAIL_TOAST_MILLIS);
            }
            return;
        }
        RecordingThumbnails.delete(plannedThumbnail);
        if (progress.state() == RecordingJob.State.FAILED && progress.error().contains(NO_FRAMES)) {
            toast(minecraft, Component.translatable("propwash.video.record.failed.title"), Component.translatable("propwash.video.record.too_short"),
                    Palette.WARNING, FAIL_TOAST_MILLIS);
            return;
        }
        if (progress.state() == RecordingJob.State.FAILED && finished.externalEncoder()) {
            HardwareEncoders.markBroken(progress.error());
        }
        if (progress.state() == RecordingJob.State.FAILED) {
            this.lastError = progress.error();
            Constants.LOG.error("Action-Cam: Video konnte nicht gespeichert werden: {}", progress.error());
            if (minecraft.level != null) {
                minecraft.showDebugChat(Component.translatable("propwash.video.record.failed", progress.error()).withStyle(ChatFormatting.RED));
            }
            toast(minecraft, Component.translatable("propwash.video.record.failed.title"), Component.literal(progress.error()), Palette.ERROR,
                    SAVED_TOAST_MILLIS);
        }
    }

    private static void toast(Minecraft minecraft, Component title, Component message, int accent, long millis) {
        ThemedToast.show(minecraft.gui.toastManager(), TOAST_TOKEN, title, message, accent, millis);
    }

    public void onDisconnect(Minecraft minecraft) {
        if (this.phase == Phase.RECORDING) {
            this.stop(minecraft, StopReason.DISCONNECT);
        }
        if (this.phase == Phase.STOPPING && this.capture.inFlight() == 0) {
            this.completeStop();
        }
        this.lastArmed = false;
    }

    public void cancelAll() {
        RecordingJob current = this.job;
        if (current != null) {
            current.cancel();
            RecordingThumbnails.delete(RecordingLibrary.thumbnailOf(current.file()));
        }
        for (RecordingJob pending : this.saving) {
            pending.cancel();
            RecordingThumbnails.delete(RecordingLibrary.thumbnailOf(pending.file()));
        }
        this.saving.clear();
        this.job = null;
        this.clock = null;
        this.drone = null;
        this.phase = Phase.IDLE;
        this.audioCapture.stop();
    }

    private void ensureShutdownHook() {
        if (this.shutdownHook) {
            return;
        }
        this.shutdownHook = true;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            List<RecordingJob> pending = new ArrayList<>(this.saving);
            RecordingJob current = this.job;
            if (current != null) {
                current.finish(0L);
                pending.add(current);
            }
            long deadline = System.currentTimeMillis() + 20_000L;
            for (RecordingJob waiting : pending) {
                try {
                    waiting.awaitDone(Math.max(1L, deadline - System.currentTimeMillis()));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "Propwash video shutdown"));
    }

    private void cleanOnce(Path dir) {
        if (this.cleaned) {
            return;
        }
        this.cleaned = true;
        try {
            Files.createDirectories(dir);
            try (DirectoryStream<Path> parts = Files.newDirectoryStream(dir, "*.part")) {
                for (Path part : parts) {
                    Files.deleteIfExists(part);
                }
            }
            Path spool = dir.resolve(".spool");
            if (Files.isDirectory(spool)) {
                try (DirectoryStream<Path> frames = Files.newDirectoryStream(spool, "*.frames")) {
                    for (Path frame : frames) {
                        Files.deleteIfExists(frame);
                    }
                }
            }
            RecordingLibrary.pruneThumbnails(dir);
        } catch (IOException e) {
            Constants.LOG.warn("Action-Cam: alte Teildateien nicht gelöscht: {}", e.getMessage());
        }
    }

    private static long spoolCap(Path dir) {
        try {
            long usable = Files.getFileStore(dir).getUsableSpace();
            return Math.max(0L, Math.min(SPOOL_CAP_BYTES, usable - SPOOL_FREE_RESERVE));
        } catch (IOException e) {
            return 0L;
        }
    }

    public enum Phase {
        IDLE,
        RECORDING,
        STOPPING
    }

    public enum Result {
        STARTED("started"),
        STOPPED("stopped"),
        ALREADY("already"),
        BUSY("busy"),
        NO_SESSION("no_session"),
        NO_CAMERA("no_camera"),
        NOT_FPV("not_fpv"),
        CAMERA_EMPTY("camera_empty"),
        NO_POWER("no_power"),
        FAILED("failed_start");

        private final String key;

        Result(String key) {
            this.key = key;
        }

        public String translationKey() {
            return "propwash.video.record.result." + this.key;
        }
    }

    public enum StopReason {
        USER("user", false),
        DISARMED("disarmed", false),
        SESSION_END("session_end", false),
        CAMERA_BATTERY("camera_battery", true),
        FLIGHT_BATTERY("flight_battery", true),
        DISCONNECT("disconnect", false),
        ERROR("error", true);

        private final String key;
        private final boolean error;

        StopReason(String key, boolean error) {
            this.key = key;
            this.error = error;
        }

        public String translationKey() {
            return "propwash.video.record.stop." + this.key;
        }

        public int accent() {
            return switch (this) {
                case ERROR -> Palette.ERROR;
                case CAMERA_BATTERY, FLIGHT_BATTERY -> Palette.WARNING;
                default -> Palette.ACCENT;
            };
        }
    }

    public record SavingJob(Path file, RecordingFormat format, RecordingJob.Progress progress) {
    }
}
