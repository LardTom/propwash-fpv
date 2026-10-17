package de.lardtom.propwash.video.client.record;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.compat.flashback.client.FlashbackUiScale;
import de.lardtom.propwash.video.record.path.CameraPathBuffer;
import de.lardtom.propwash.video.record.path.PathFrames;
import de.lardtom.propwash.video.record.path.PathPreview;
import de.lardtom.propwash.video.record.path.PathResampler;
import de.lardtom.propwash.video.record.path.SamplePath;
import de.lardtom.propwash.video.record.path.Simplification;
import de.lardtom.propwash.video.record.path.TickPose;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class PathPreviewModel {
    public static final long DEBOUNCE_NANOS = 100_000_000L;
    public static final double MIN_LAST_SECONDS = 10.0;

    public enum Source {
        LAST,
        SAMPLE,
        SCENE
    }

    private final Source source;
    private final String sceneName;
    private final CompletableFuture<PathFrames> loading;
    private final float scale;
    private double slider;
    private boolean limit;
    private boolean dirty = true;
    private long changedAt;
    private @Nullable PathPreview result;
    private @Nullable CompletableFuture<PathPreview> pending;
    private double pendingSlider;
    private boolean pendingLimit;
    private @Nullable String error;
    private int generation;

    private PathPreviewModel(Source source, String sceneName, Supplier<PathFrames> frames, double slider, boolean limit, float scale) {
        this.source = source;
        this.sceneName = sceneName;
        this.slider = slider;
        this.limit = limit;
        this.scale = scale;
        this.loading = CompletableFuture.supplyAsync(frames, Util.backgroundExecutor());
    }

    public static PathPreviewModel forFlight(double slider, boolean limit) {
        float scale = FlashbackUiScale.budgetScale(Minecraft.getInstance());
        CameraPathBuffer last = FlashbackCam.INSTANCE.lastPath();
        if (last != null && last.spanTicks() / 20.0 >= MIN_LAST_SECONDS) {
            return new PathPreviewModel(Source.LAST, "", () -> PathFrames.of(PathResampler.perTick(last)), slider, limit, scale);
        }
        return new PathPreviewModel(Source.SAMPLE, "", PathPreviewModel::sample, slider, limit, scale);
    }

    public static PathPreviewModel forScene(String name, List<TickPose> poses, double slider, float scale) {
        return new PathPreviewModel(Source.SCENE, name, () -> PathFrames.of(poses), slider, true, scale);
    }

    private static PathFrames sample() {
        try {
            return PathFrames.of(SamplePath.load());
        } catch (IOException e) {
            Constants.LOG.warn("Action-Cam (Flashback): Beispielflug nicht lesbar ({})", e.toString());
            return new PathFrames();
        }
    }

    public Source source() {
        return this.source;
    }

    public float scale() {
        return this.scale;
    }

    public double slider() {
        return this.slider;
    }

    public boolean limit() {
        return this.limit;
    }

    public void settings(double slider, boolean limit) {
        if (slider != this.slider || limit != this.limit) {
            this.slider = slider;
            this.limit = limit;
            this.dirty = true;
            this.changedAt = Util.getNanos();
        }
    }

    public @Nullable PathFrames frames() {
        return this.loading.isDone() && !this.loading.isCompletedExceptionally() ? this.loading.join() : null;
    }

    public void tick() {
        CompletableFuture<PathPreview> running = this.pending;
        if (running != null && running.isDone()) {
            this.pending = null;
            try {
                this.result = running.join();
                this.error = null;
                this.generation++;
            } catch (RuntimeException e) {
                this.error = e.toString();
                Constants.LOG.warn("Action-Cam (Flashback): Pfad-Vorschau fehlgeschlagen ({})", e.toString());
            }
        }
        PathFrames frames = this.frames();
        if (frames == null || frames.size() < 2 || this.pending != null || !this.dirty) {
            return;
        }
        if (this.result != null && Util.getNanos() - this.changedAt < DEBOUNCE_NANOS) {
            return;
        }
        this.dirty = false;
        this.pendingSlider = this.slider;
        this.pendingLimit = this.limit;
        Simplification wanted = Simplification.ofSlider(this.slider);
        boolean capped = this.limit;
        float budgetScale = this.scale;
        this.pending = CompletableFuture.supplyAsync(() -> PathPreview.of(frames, wanted, capped, budgetScale), Util.backgroundExecutor());
    }

    public @Nullable PathPreview result() {
        return this.result;
    }

    public int generation() {
        return this.generation;
    }

    public boolean ready() {
        return this.result != null && this.pending == null && !this.dirty;
    }

    public boolean busy() {
        return this.pending != null || this.dirty || !this.loading.isDone();
    }

    public boolean empty() {
        PathFrames frames = this.frames();
        return this.loading.isDone() && (frames == null || frames.size() < 2);
    }

    public @Nullable String error() {
        return this.error;
    }

    public Component sourceLine() {
        PathFrames frames = this.frames();
        Component length = SimplificationText.duration(frames == null || frames.size() < 2 ? Double.NaN : (frames.size() - 1) / 20.0);
        return switch (this.source) {
            case LAST -> Component.translatable(SimplificationText.PREVIEW + "source.last", length);
            case SAMPLE -> Component.translatable(SimplificationText.PREVIEW + "source.sample", length);
            case SCENE -> Component.translatable(SimplificationText.PREVIEW + "source.scene", this.sceneName, length);
        };
    }

    public double pendingSlider() {
        return this.pendingSlider;
    }

    public boolean pendingLimit() {
        return this.pendingLimit;
    }
}
