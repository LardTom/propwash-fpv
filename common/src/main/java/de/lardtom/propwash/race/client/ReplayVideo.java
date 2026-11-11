package de.lardtom.propwash.race.client;

import de.lardtom.propwash.race.ReplayExport;
import de.lardtom.propwash.video.client.record.ActionCamRecorder;
import de.lardtom.propwash.video.client.record.RecordingConfig;
import de.lardtom.propwash.video.record.RecordingFormat;
import de.lardtom.propwash.video.record.RecordingJob;
import de.lardtom.propwash.video.record.external.HardwareEncoders;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

public final class ReplayVideo {
    public static final ReplayVideo INSTANCE = new ReplayVideo();
    private static final long RESULT_MILLIS = 8000L;

    public enum Stage {
        IDLE,
        RECORDING,
        SAVING,
        SAVED,
        CANCELLED,
        FAILED
    }

    private Stage stage = Stage.IDLE;
    private @Nullable Path savedBefore;
    private HardwareEncoders.@Nullable State forcedState;
    private long resultSince;

    private ReplayVideo() {
    }

    public Stage stage() {
        return this.stage;
    }

    public boolean recording() {
        return this.stage == Stage.RECORDING;
    }

    public void forceFfmpegState(HardwareEncoders.@Nullable State state) {
        this.forcedState = state;
    }

    public ReplayExport.Block block(Minecraft minecraft) {
        ActionCamRecorder recorder = ActionCamRecorder.INSTANCE;
        if (recorder.phase() == ActionCamRecorder.Phase.RECORDING) {
            return ReplayExport.Block.BUSY;
        }
        if (recorder.phase() != ActionCamRecorder.Phase.IDLE || !recorder.savingJobs().isEmpty()) {
            return ReplayExport.Block.SAVING;
        }
        return ReplayExport.ffmpeg(this.ffmpegState(minecraft));
    }

    private HardwareEncoders.State ffmpegState(Minecraft minecraft) {
        HardwareEncoders.State forced = this.forcedState;
        if (forced != null) {
            return forced;
        }
        RecordingFormat format = ActionCamRecorder.INSTANCE.replayFormat();
        HardwareEncoders.Settings settings = new HardwareEncoders.Settings(true, RecordingConfig.get().ffmpegPath, ActionCamRecorder.ffmpegFolders(minecraft));
        return HardwareEncoders.status(settings, format).state();
    }

    public boolean start(Minecraft minecraft) {
        ReplaySession session = ReplaySession.INSTANCE;
        if (!session.active() || this.block(minecraft) != ReplayExport.Block.NONE) {
            return false;
        }
        ActionCamRecorder recorder = ActionCamRecorder.INSTANCE;
        this.savedBefore = recorder.lastSaved();
        if (recorder.startReplay(minecraft, session::active) != ActionCamRecorder.Result.STARTED) {
            this.result(Stage.FAILED);
            return false;
        }
        session.beginExport(recorder::replayFrameNanos);
        this.stage = Stage.RECORDING;
        return true;
    }

    public void cancel() {
        if (this.stage != Stage.RECORDING) {
            return;
        }
        ActionCamRecorder.INSTANCE.cancelReplay();
        ReplaySession.INSTANCE.endExport();
        this.result(Stage.CANCELLED);
    }

    private void result(Stage result) {
        this.stage = result;
        this.resultSince = Util.getMillis();
    }

    public void forget() {
        this.cancel();
        this.stage = Stage.IDLE;
    }

    public void onFrame(Minecraft minecraft) {
        ActionCamRecorder recorder = ActionCamRecorder.INSTANCE;
        ReplaySession session = ReplaySession.INSTANCE;
        switch (this.stage) {
            case RECORDING -> {
                if (!recorder.replayRecording()) {
                    session.endExport();
                    this.stage = Stage.SAVING;
                } else if (session.time() >= session.end()) {
                    recorder.finishReplay(minecraft);
                    session.endExport();
                    this.stage = Stage.SAVING;
                }
            }
            case SAVING -> {
                if (recorder.phase() == ActionCamRecorder.Phase.IDLE && recorder.savingJobs().isEmpty()) {
                    this.result(recorder.lastSaved() != null && recorder.lastSaved() != this.savedBefore ? Stage.SAVED : Stage.FAILED);
                }
            }
            case SAVED, CANCELLED, FAILED -> {
                if (Util.getMillis() - this.resultSince > RESULT_MILLIS) {
                    this.stage = Stage.IDLE;
                }
            }
            case IDLE -> {
            }
        }
    }

    public float progress() {
        ReplaySession session = ReplaySession.INSTANCE;
        return ReplayExport.fraction(session.time(), session.begin(), session.end());
    }

    public RecordingJob.@Nullable Progress saving() {
        List<ActionCamRecorder.SavingJob> jobs = ActionCamRecorder.INSTANCE.savingJobs();
        return jobs.isEmpty() ? null : jobs.getLast().progress();
    }

    public String savedName() {
        Path saved = ActionCamRecorder.INSTANCE.lastSaved();
        return saved == null ? "" : saved.getFileName().toString();
    }

    public String error() {
        String error = ActionCamRecorder.INSTANCE.lastError();
        return error == null ? "" : error;
    }
}
