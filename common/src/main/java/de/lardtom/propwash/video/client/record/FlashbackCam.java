package de.lardtom.propwash.video.client.record;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.compat.flashback.client.FlashbackBridge;
import de.lardtom.propwash.compat.flashback.client.FlashbackScene;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemedToast;
import de.lardtom.propwash.video.FovMath;
import de.lardtom.propwash.video.client.VideoSettings;
import de.lardtom.propwash.video.client.VideoState;
import de.lardtom.propwash.video.record.ClipInfo;
import de.lardtom.propwash.video.record.path.CameraPathBuffer;
import de.lardtom.propwash.video.record.path.CameraTrack;
import de.lardtom.propwash.video.record.path.KeyframeDensity;
import de.lardtom.propwash.video.record.path.PathSample;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Avatar;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class FlashbackCam {
    public static final FlashbackCam INSTANCE = new FlashbackCam();
    public static final String TOAST_TOKEN = "propwash_flashback_cam";
    public static final float EXPORT_ASPECT = 16.0F / 9.0F;
    public static final int MAX_TICKS = 15 * 60 * 20;
    public static final int TRACK_COLOUR = Palette.ACCENT & 0xFFFFFF;
    private static final long START_TOAST_MILLIS = 3000L;
    private static final long STOP_TOAST_MILLIS = 6000L;
    private static final long FAIL_TOAST_MILLIS = 5000L;
    private static final long READY_TIMEOUT_NANOS = 15_000_000_000L;

    private CameraPathBuffer buffer = new CameraPathBuffer();
    private final Map<UUID, Integer> flights = new HashMap<>();
    private final Vector3d position = new Vector3d();
    private final Quaternionf rotation = new Quaternionf();
    private final Vector3f lens = new Vector3f();
    private Phase phase = Phase.IDLE;
    private @Nullable QuadcopterEntity drone;
    private @Nullable Object recorder;
    private @Nullable UUID replay;
    private @Nullable String droneName;
    private boolean startedFlashback;
    private boolean automatic;
    private boolean lastArmed;
    private boolean waiting;
    private long startNanos;
    private @Nullable CompletableFuture<Handover> saving;
    private @Nullable Handover lastHandover;
    private @Nullable CameraPathBuffer lastPath;
    private @Nullable CameraTrack lastTrack;
    private @Nullable String lastError;
    private @Nullable Result lastResult;
    private long samples;

    private FlashbackCam() {
    }

    public static boolean mounted(@Nullable QuadcopterEntity drone) {
        return drone != null && drone.build().accessories().flashbackCam().isPresent();
    }

    public Phase phase() {
        return this.phase;
    }

    public boolean recording() {
        return this.phase == Phase.RECORDING;
    }

    public boolean waiting() {
        return this.phase == Phase.RECORDING && this.waiting;
    }

    public boolean paused() {
        return this.phase == Phase.RECORDING && (this.waiting || FlashbackBridge.paused());
    }

    public boolean saving() {
        return this.saving != null;
    }

    public double recordedSeconds() {
        return this.buffer.spanTicks() / 20.0;
    }

    public long samples() {
        return this.samples;
    }

    public @Nullable UUID replay() {
        return this.replay;
    }

    public @Nullable Handover lastHandover() {
        return this.lastHandover;
    }

    public @Nullable CameraPathBuffer lastPath() {
        return this.lastPath;
    }

    public @Nullable CameraTrack lastTrack() {
        return this.lastTrack;
    }

    public @Nullable String lastError() {
        return this.lastError;
    }

    public @Nullable Result lastResult() {
        return this.lastResult;
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
        this.lastResult = result;
        if (result != Result.STARTED && !automatic) {
            toast(minecraft, Component.translatable(result.titleKey()), Component.translatable(result.translationKey(),
                    FlashbackBridge.problem() == null ? "" : FlashbackBridge.problem()), result.accent(), FAIL_TOAST_MILLIS);
        }
        return result;
    }

    private Result tryStart(Minecraft minecraft, boolean automatic) {
        if (this.phase == Phase.RECORDING) {
            return Result.ALREADY;
        }
        if (this.saving != null) {
            return Result.BUSY;
        }
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity flown = session.drone();
        if (!session.isActive() || flown == null) {
            return Result.NO_SESSION;
        }
        if (!mounted(flown)) {
            return Result.NO_CAMERA;
        }
        switch (FlashbackBridge.status()) {
            case ABSENT -> {
                return Result.NEEDS_FLASHBACK;
            }
            case INCOMPATIBLE -> {
                return Result.INCOMPATIBLE;
            }
            case READY -> {
            }
        }
        if (FlashbackBridge.inReplay()) {
            return Result.IN_REPLAY;
        }
        boolean started = false;
        if (!FlashbackBridge.recording()) {
            if (!RecordingConfig.get().flashbackAutoRecord) {
                return Result.NOT_RECORDING;
            }
            if (!FlashbackBridge.startRecording()) {
                return Result.FLASHBACK_REFUSED;
            }
            started = true;
        }
        Object current = FlashbackBridge.recorder();
        UUID id = current == null ? null : FlashbackBridge.replayId(current);
        if (current == null || id == null) {
            return Result.FLASHBACK_REFUSED;
        }
        this.phase = Phase.RECORDING;
        this.recorder = current;
        this.replay = id;
        this.drone = flown;
        this.droneName = flown.hasCustomName() && flown.getCustomName() != null ? flown.getCustomName().getString() : null;
        this.startedFlashback = started;
        this.automatic = automatic;
        this.buffer.clear();
        this.samples = 0L;
        this.waiting = true;
        this.lastError = null;
        this.startNanos = Util.getNanos();
        int flight = this.flights.getOrDefault(id, 0) + 1;
        FlashbackBridge.addMarker(TRACK_COLOUR, Component.translatable("propwash.video.flashback.marker", flight).getString());
        toast(minecraft, Component.translatable(automatic ? "propwash.video.flashback.toast.started_auto" : "propwash.video.flashback.toast.started"),
                Component.translatable(started ? "propwash.video.flashback.toast.started_with_recording" : "propwash.video.flashback.toast.started_detail"),
                ActionCamRecorder.REC_ACCENT, START_TOAST_MILLIS);
        return Result.STARTED;
    }

    public void stop(Minecraft minecraft, StopReason reason) {
        if (this.phase != Phase.RECORDING) {
            return;
        }
        UUID id = this.replay;
        boolean finishFlashback = this.startedFlashback && RecordingConfig.get().flashbackAutoRecord && reason != StopReason.FLASHBACK_STOPPED
                && FlashbackBridge.recorder() == this.recorder;
        Object stoppedRecorder = this.recorder;
        CameraPathBuffer captured = this.buffer;
        this.buffer = new CameraPathBuffer();
        this.lastPath = captured;
        String name = this.droneName;
        this.reset();
        if (id == null || captured.spanTicks() < 1.0) {
            if (finishFlashback) {
                FlashbackBridge.finishRecording();
            }
            this.lastError = "too short";
            toast(minecraft, Component.translatable("propwash.video.flashback.toast.discarded"),
                    Component.translatable("propwash.video.flashback.toast.too_short"), Palette.WARNING, STOP_TOAST_MILLIS);
            return;
        }
        int flight = this.flights.merge(id, 1, Integer::sum);
        KeyframeDensity density = RecordingConfig.get().flashbackDensity;
        String sceneName = name == null
                ? Component.translatable("propwash.video.flashback.scene", flight).getString()
                : Component.translatable("propwash.video.flashback.scene_named", name, flight).getString();
        String cameraName = Component.translatable("propwash.video.flashback.track.camera").getString();
        String fovName = Component.translatable("propwash.video.flashback.track.fov").getString();
        double eye = Avatar.DEFAULT_EYE_HEIGHT;
        CompletableFuture<Handover> future = CompletableFuture
                .supplyAsync(() -> CameraTrack.of(captured, density), Util.backgroundExecutor())
                .thenApplyAsync(track -> this.handOver(minecraft, id, new FlashbackScene(sceneName, cameraName, fovName, TRACK_COLOUR, eye, track),
                        reason, finishFlashback), minecraft);
        this.saving = future;
        future.whenCompleteAsync((handover, error) -> {
            if (this.saving == future) {
                this.saving = null;
            }
            if (error != null) {
                if (finishFlashback && stoppedRecorder != null && FlashbackBridge.recorder() == stoppedRecorder) {
                    FlashbackBridge.finishRecording();
                }
                this.lastError = error.toString();
                Constants.LOG.error("Action-Cam (Flashback): Kamerapfad konnte nicht übergeben werden", error);
                toast(minecraft, Component.translatable("propwash.video.flashback.toast.failed"), Component.literal(String.valueOf(error.getMessage())),
                        Palette.ERROR, FAIL_TOAST_MILLIS);
            }
        }, minecraft);
    }

    private Handover handOver(Minecraft minecraft, UUID id, FlashbackScene scene, StopReason reason, boolean finishFlashback) {
        CameraTrack track = scene.track();
        this.lastTrack = track;
        if (!track.usable()) {
            if (finishFlashback) {
                FlashbackBridge.finishRecording();
            }
            throw new IllegalStateException("camera path too short");
        }
        int index;
        try {
            index = FlashbackBridge.writeScene(id, scene);
        } catch (FlashbackBridge.FlashbackException e) {
            if (finishFlashback) {
                FlashbackBridge.finishRecording();
            }
            throw new IllegalStateException(e.getMessage(), e);
        }
        if (finishFlashback) {
            FlashbackBridge.finishRecording();
        }
        Handover handover = new Handover(id, index, scene.name(), track.startTick(), track.endTick(), track.camera().size(), track.fov().size(),
                finishFlashback, reason);
        this.lastHandover = handover;
        Constants.LOG.info("Action-Cam (Flashback): Szene \"{}\" an Flashback übergeben (Aufnahme {}, Ticks {}–{}, {} Keyframes)", scene.name(), id,
                track.startTick(), track.endTick(), track.camera().size());
        Component detail = Component.translatable(finishFlashback ? "propwash.video.flashback.toast.saved_finished" : "propwash.video.flashback.toast.saved_detail",
                scene.name(), ClipInfo.duration(Math.round(track.durationSeconds())), track.camera().size());
        toast(minecraft, Component.translatable(reason.error() ? "propwash.video.flashback.toast.saved_after" : "propwash.video.flashback.toast.saved",
                Component.translatable(reason.translationKey())), detail, Palette.ACCENT, STOP_TOAST_MILLIS);
        return handover;
    }

    private void reset() {
        this.phase = Phase.IDLE;
        this.buffer.clear();
        this.drone = null;
        this.recorder = null;
        this.replay = null;
        this.droneName = null;
        this.startedFlashback = false;
        this.automatic = false;
        this.waiting = false;
    }

    @Nullable PathSample latestSample() {
        return this.buffer.isEmpty() ? null : this.buffer.get(this.buffer.size() - 1);
    }

    public void onFrame(Minecraft minecraft, DeltaTracker deltaTracker) {
        if (this.phase != Phase.RECORDING) {
            return;
        }
        Object current = FlashbackBridge.recorder();
        if (current == null || current != this.recorder || minecraft.level == null || minecraft.isPaused() || !FlashbackBridge.writing()) {
            return;
        }
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity flown = session.drone();
        if (!session.isActive() || flown == null || flown != this.drone) {
            return;
        }
        int ticks = FlashbackBridge.writtenTicks(current);
        if (ticks < 0) {
            return;
        }
        double tick = ticks + deltaTracker.getGameTimeDeltaPartialTick(false);
        session.comPosition(this.position);
        session.bodyOrientation(this.rotation);
        this.lens.set((float) session.lensRightOffset(), (float) session.lensUpOffset(), (float) -session.lensForwardOffset());
        this.rotation.transform(this.lens);
        this.rotation.rotateX((float) Math.toRadians(session.cameraUptiltDeg()));
        double fov = FovMath.verticalFromHorizontal(horizontalFov(minecraft, session, flown), EXPORT_ASPECT);
        boolean added = this.buffer.add(new PathSample(tick, this.position.x + this.lens.x, this.position.y + this.lens.y, this.position.z + this.lens.z,
                this.rotation.x, this.rotation.y, this.rotation.z, this.rotation.w, fov));
        if (added) {
            this.samples++;
            this.waiting = false;
        }
    }

    static double horizontalFov(Minecraft minecraft, PilotSession session, QuadcopterEntity flown) {
        if (session.isFpv()) {
            return VideoState.INSTANCE.horizontalFov();
        }
        return VideoSettings.get().horizontalFov(minecraft.player == null ? null : ContentIds.wornGogglesSystem(minecraft.player).orElse(null),
                flown.build().flight().video());
    }

    public void onClientTick(Minecraft minecraft) {
        this.autoRecord(minecraft);
        if (this.phase != Phase.RECORDING) {
            return;
        }
        Object current = FlashbackBridge.recorder();
        if (current == null || current != this.recorder) {
            this.stop(minecraft, StopReason.FLASHBACK_STOPPED);
            return;
        }
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity flown = session.drone();
        if (!session.isActive() || flown == null || flown != this.drone || flown.isRemoved()) {
            this.stop(minecraft, StopReason.SESSION_END);
            return;
        }
        if (!mounted(flown)) {
            this.stop(minecraft, StopReason.SESSION_END);
            return;
        }
        if (this.buffer.spanTicks() >= MAX_TICKS) {
            this.stop(minecraft, StopReason.LIMIT);
            return;
        }
        if (this.waiting && this.buffer.isEmpty() && Util.getNanos() - this.startNanos > READY_TIMEOUT_NANOS && !FlashbackBridge.paused()
                && !minecraft.isPaused()) {
            this.lastError = "flashback not writing";
            this.stop(minecraft, StopReason.FLASHBACK_STOPPED);
        }
    }

    private void autoRecord(Minecraft minecraft) {
        PilotSession session = PilotSession.INSTANCE;
        boolean armed = session.isActive() && session.armed();
        if (RecordingConfig.get().autoRecord && mounted(session.drone()) && FlashbackBridge.ready()) {
            if (armed && !this.lastArmed && this.phase == Phase.IDLE && this.saving == null) {
                this.start(minecraft, true);
            } else if (!armed && this.lastArmed && this.phase == Phase.RECORDING && this.automatic) {
                this.stop(minecraft, StopReason.DISARMED);
            }
        }
        this.lastArmed = armed;
    }

    public void onDisconnect(Minecraft minecraft) {
        if (this.phase == Phase.RECORDING) {
            this.stop(minecraft, StopReason.DISCONNECT);
        }
        this.lastArmed = false;
    }

    public static Optional<AccessoryType> cameraOf(@Nullable QuadcopterEntity drone) {
        return drone == null ? Optional.empty() : drone.build().accessories().flashbackCam();
    }

    private static void toast(Minecraft minecraft, Component title, Component message, int accent, long millis) {
        ThemedToast.show(minecraft.gui.toastManager(), TOAST_TOKEN, title, message, accent, millis);
    }

    public enum Phase {
        IDLE,
        RECORDING
    }

    public enum Result {
        STARTED("started"),
        STOPPED("stopped"),
        ALREADY("already"),
        BUSY("busy"),
        NO_SESSION("no_session"),
        NO_CAMERA("no_camera"),
        NEEDS_FLASHBACK("needs_flashback"),
        INCOMPATIBLE("incompatible"),
        IN_REPLAY("in_replay"),
        NOT_RECORDING("not_recording"),
        FLASHBACK_REFUSED("refused");

        private final String key;

        Result(String key) {
            this.key = key;
        }

        public String translationKey() {
            return "propwash.video.flashback.result." + this.key;
        }

        public String titleKey() {
            return this == NEEDS_FLASHBACK ? "propwash.video.flashback.toast.needs_title" : "propwash.video.flashback.toast.not_started";
        }

        public int accent() {
            return this == NEEDS_FLASHBACK || this == NOT_RECORDING ? Palette.WARNING : Palette.ERROR;
        }
    }

    public enum StopReason {
        USER("user", false),
        DISARMED("disarmed", false),
        SESSION_END("session_end", false),
        FLASHBACK_STOPPED("flashback_stopped", true),
        LIMIT("limit", true),
        DISCONNECT("disconnect", false);

        private final String key;
        private final boolean error;

        StopReason(String key, boolean error) {
            this.key = key;
            this.error = error;
        }

        public String translationKey() {
            return "propwash.video.flashback.stop." + this.key;
        }

        public boolean error() {
            return this.error;
        }
    }

    public record Handover(UUID replay, int sceneIndex, String scene, int startTick, int endTick, int keyframes, int fovKeyframes,
                           boolean finishedRecording, StopReason reason) {
    }
}
