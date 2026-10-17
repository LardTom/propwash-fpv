package de.lardtom.propwash.osd.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.drone.client.FpvView;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.osd.OsdConfig;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.OsdElement;
import de.lardtom.propwash.osd.OsdGrid;
import de.lardtom.propwash.osd.OsdSampler;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.video.FisheyeLens;
import de.lardtom.propwash.video.FovMath;
import de.lardtom.propwash.video.ImageArea;
import de.lardtom.propwash.video.client.FpvImage;
import de.lardtom.propwash.video.client.FpvLens;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

public final class OsdHud {
    public static final Identifier ID = Constants.id("osd");
    private static BooleanSupplier replacement = () -> false;
    private static final long ERROR_LOG_INTERVAL_NANOS = 10_000_000_000L;

    private static final OsdSampler SAMPLER = new OsdSampler(new LiveOsdSource());
    private static final OsdRenderer RENDERER = new OsdRenderer();
    private static int renderedFrames;
    private static int errors;
    private static long lastErrorLogNanos = Long.MIN_VALUE;
    private static @Nullable Consumer<GuiGraphicsExtractor> probe;
    private static @Nullable OsdConfig configOverride;
    private static @Nullable FrameTiming timing;

    record FrameTiming(LongSupplier clock, LongConsumer sink) {
    }

    private OsdHud() {
    }

    public static void setReplacement(BooleanSupplier check) {
        replacement = Objects.requireNonNull(check, "check");
    }

    public static void onFrame(Minecraft minecraft, DeltaTracker deltaTracker) {
        try {
            if (minecraft.isPaused()) {
                SAMPLER.hold(Util.getNanos());
                return;
            }
            SAMPLER.sample(Util.getNanos());
        } catch (RuntimeException e) {
            reportError("Datenquelle", e);
        }
    }

    public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        try {
            OsdData data = SAMPLER.data();
            Minecraft minecraft = Minecraft.getInstance();
            if (data.active && !(minecraft.gui.screen() instanceof ThemedScreen) && !replacement.getAsBoolean()) {
                graphics.nextStratum();
                OsdConfig config = config();
                OsdGrid grid = grid(graphics.guiWidth(), graphics.guiHeight());
                int guiScale = minecraft.getWindow().getGuiScale();
                float scale = OsdGrid.snapToPixels(grid.textScale(config.scale), guiScale);
                RENDERER.setPixelScale(guiScale);
                FrameTiming currentTiming = timing;
                long start = currentTiming != null ? currentTiming.clock().getAsLong() : 0L;
                RENDERER.render(graphics, minecraft.font, data, config, grid, scale, liveView(minecraft), false);
                if (currentTiming != null) {
                    currentTiming.sink().accept(currentTiming.clock().getAsLong() - start);
                }
                renderedFrames++;
            }
        } catch (RuntimeException e) {
            reportError("Zeichnen", e);
        }
        Consumer<GuiGraphicsExtractor> currentProbe = probe;
        if (currentProbe != null) {
            probe = null;
            try {
                currentProbe.accept(graphics);
            } catch (RuntimeException e) {
                reportError("Probe", e);
            }
        }
    }

    public static OsdGrid grid(int guiWidth, int guiHeight) {
        ImageArea area = FpvImage.area();
        if (area.full()) {
            return OsdGrid.screen(guiWidth, guiHeight);
        }
        float scale = FpvImage.guiScale();
        return new OsdGrid(area.x() / scale, area.y() / scale, area.width() / scale, area.height() / scale);
    }

    static OsdRenderer.@Nullable View liveView(Minecraft minecraft) {
        Camera camera = minecraft.gameRenderer.mainCamera();
        if (!camera.isInitialized()) {
            return null;
        }
        Vec3 position = camera.position();
        FisheyeLens lens = FpvLens.INSTANCE.rendered();
        float fov = FovMath.verticalWithin(lens != null ? (float) lens.centreEquivalentVerticalDeg() : camera.getFov(), FpvImage.area().heightShare());
        return new OsdRenderer.View(camera.rotation(), fov, position.x, position.y, position.z, lens != null ? (float) lens.horizontalDeg() : 0.0F,
                lens != null ? (float) lens.bend() : 0.0F);
    }

    public static boolean showsVideoSignal() {
        return SAMPLER.data().active && config().enabled(OsdElement.VIDEO_SIGNAL);
    }

    public static boolean hideVanilla() {
        return config().hideVanillaHud && (PilotSession.INSTANCE.isFpv() || FpvView.spectating() || SAMPLER.hasOverride());
    }

    public static OsdConfig config() {
        OsdConfig override = configOverride;
        return override != null ? override : OsdClient.CONFIG.get();
    }

    static void overrideConfig(@Nullable OsdConfig config) {
        configOverride = config;
    }

    static void timeFrames(@Nullable FrameTiming frameTiming) {
        timing = frameTiming;
    }

    static OsdRenderer renderer() {
        return RENDERER;
    }

    public static void onDisconnect(Minecraft minecraft) {
        SAMPLER.setOverride(null);
        probe = null;
        configOverride = null;
        timing = null;
    }

    public static OsdSampler sampler() {
        return SAMPLER;
    }

    public static int renderedFrames() {
        return renderedFrames;
    }

    public static boolean drewLastFrame(OsdElement element) {
        return RENDERER.wasDrawn(element);
    }

    public static int errors() {
        return errors;
    }

    public static void probeNextFrame(@Nullable Consumer<GuiGraphicsExtractor> action) {
        probe = action;
    }

    private static void reportError(String where, RuntimeException e) {
        errors++;
        long now = Util.getNanos();
        if (lastErrorLogNanos == Long.MIN_VALUE || now - lastErrorLogNanos > ERROR_LOG_INTERVAL_NANOS) {
            lastErrorLogNanos = now;
            Constants.LOG.error("OSD-Fehler ({}), {} bisher", where, errors, e);
        }
    }
}
