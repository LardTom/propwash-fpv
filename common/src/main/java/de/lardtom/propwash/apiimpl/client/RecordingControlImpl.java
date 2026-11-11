package de.lardtom.propwash.apiimpl.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.client.RecordingControl;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.operator.client.OperatorView;
import de.lardtom.propwash.gpucompat.client.GuardedScreenshot;
import de.lardtom.propwash.preview.PreviewBuild;
import de.lardtom.propwash.preview.client.PreviewMark;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemedToast;
import de.lardtom.propwash.video.client.CleanFrameTap;
import de.lardtom.propwash.video.client.record.ActionCamRecorder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class RecordingControlImpl implements RecordingControl {
    public static final RecordingControlImpl INSTANCE = new RecordingControlImpl();
    public static final String PHOTO_PREFIX = "propwash_photo_";
    public static final String PHOTO_EXTENSION = ".png";
    private static final DateTimeFormatter NAMES = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.ROOT);
    private static final Object TOAST_TOKEN = new Object();
    private static final long TOAST_MILLIS = 3500L;
    private static final long PENDING_NANOS = 5_000_000_000L;

    private final AtomicReference<@Nullable Path> lastPhoto = new AtomicReference<>();
    private final AtomicInteger photos = new AtomicInteger();
    private final AtomicInteger photoFailures = new AtomicInteger();
    private volatile long pendingSince;

    private RecordingControlImpl() {
    }

    @Override
    public boolean start(Entity drone) {
        Objects.requireNonNull(drone, "drone");
        Minecraft minecraft = Minecraft.getInstance();
        ActionCamRecorder recorder = ActionCamRecorder.INSTANCE;
        if (!OperatorView.recordable(drone) || recorder.phase() != ActionCamRecorder.Phase.IDLE) {
            return false;
        }
        return recorder.start(minecraft, false) == ActionCamRecorder.Result.STARTED;
    }

    @Override
    public void stop() {
        ActionCamRecorder recorder = ActionCamRecorder.INSTANCE;
        if (recorder.recording()) {
            recorder.stop(Minecraft.getInstance(), ActionCamRecorder.StopReason.USER);
        }
    }

    @Override
    public boolean photo(Entity drone) {
        Objects.requireNonNull(drone, "drone");
        Minecraft minecraft = Minecraft.getInstance();
        if (!(drone instanceof QuadcopterEntity) || !OperatorView.recordable(drone) || this.photoPending() || minecraft.getCameraEntity() != drone) {
            return false;
        }
        Path directory = ActionCamRecorder.directory(minecraft);
        Path file = directory.resolve(PHOTO_PREFIX + LocalDateTime.now().format(NAMES) + PHOTO_EXTENSION);
        for (int i = 2; Files.exists(file); i++) {
            file = directory.resolve(PHOTO_PREFIX + LocalDateTime.now().format(NAMES) + "_" + i + PHOTO_EXTENSION);
        }
        Path target = file;
        this.pendingSince = Math.max(1L, System.nanoTime());
        CleanFrameTap.request(frame -> GuardedScreenshot.take(frame, image -> Util.ioPool().execute(() -> {
            try (image) {
                Files.createDirectories(directory);
                if (PreviewBuild.ENABLED) {
                    PreviewMark.stampImage(image);
                }
                image.writeToFile(target);
                this.lastPhoto.set(target);
                this.photos.incrementAndGet();
                minecraft.execute(() -> this.saved(minecraft, target));
            } catch (IOException | RuntimeException e) {
                Constants.LOG.warn("Foto {} konnte nicht gespeichert werden", target.getFileName(), e);
            } finally {
                this.pendingSince = 0L;
            }
        }), problem -> {
            this.pendingSince = 0L;
            this.photoFailures.incrementAndGet();
            Constants.LOG.warn("Foto {} nicht gespeichert: {}", target.getFileName(), problem);
            if (minecraft.level != null) {
                minecraft.showDebugChat(Component.translatable("propwash.gpucompat.photo.failed").withStyle(ChatFormatting.RED));
            }
        }));
        return true;
    }

    @Override
    public boolean recording() {
        return ActionCamRecorder.INSTANCE.recording();
    }

    public @Nullable Path lastPhoto() {
        return this.lastPhoto.get();
    }

    public int photos() {
        return this.photos.get();
    }

    public int photoFailures() {
        return this.photoFailures.get();
    }

    public boolean photoPending() {
        long since = this.pendingSince;
        return since != 0L && System.nanoTime() - since < PENDING_NANOS;
    }

    private void saved(Minecraft minecraft, Path file) {
        MutableComponent link = Component.literal(file.getFileName().toString())
                .withStyle(ChatFormatting.UNDERLINE)
                .withStyle(style -> style.withClickEvent(new ClickEvent.OpenFile(file.toAbsolutePath())));
        if (minecraft.level != null) {
            minecraft.showDebugChat(Component.translatable("propwash.drone.operator.photo_saved", link));
        }
        if (minecraft.player != null) {
            ThemedToast.show(minecraft.gui.toastManager(), TOAST_TOKEN, Component.translatable("propwash.drone.operator.photo_title"),
                    Component.literal(file.getFileName().toString()), Palette.SUCCESS, TOAST_MILLIS);
        }
    }
}
