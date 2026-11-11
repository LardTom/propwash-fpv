package de.lardtom.propwash.preview.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.preview.PreviewBuild;
import de.lardtom.propwash.preview.PreviewFont;
import de.lardtom.propwash.preview.PreviewStamp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

public final class PreviewMark {
    public static final int TITLE_COPYRIGHT_LINE = 10;
    private static final PreviewFont.Raster RASTER = PreviewFont.raster(PreviewBuild.TEXT);
    private static final AtomicLong STAMPED_FRAMES = new AtomicLong();
    private static final AtomicLong STAMPED_IMAGES = new AtomicLong();
    private static long passes;
    private static long drawn;
    private static long drawnHudHidden;
    private static long drawnOnScreen;
    private static long drawnOnTitle;
    private static boolean announced;
    private static PreviewStamp.@Nullable Mask stampMask;
    private static int stampHeight;

    private PreviewMark() {
    }

    public static boolean enabled() {
        return PreviewBuild.ENABLED;
    }

    public static String text() {
        return PreviewBuild.TEXT;
    }

    public static void extract(GuiGraphicsExtractor graphics, boolean resourcesLoaded) {
        if (!PreviewBuild.ENABLED) {
            return;
        }
        passes++;
        if (!resourcesLoaded) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Window window = minecraft.getWindow();
        int guiScale = Math.max(1, window.getGuiScale());
        Screen screen = minecraft.gui.screen();
        int lift = screen instanceof TitleScreen ? TITLE_COPYRIGHT_LINE * guiScale : 0;
        PreviewStamp.Placement at = PreviewStamp.place(RASTER, window.getWidth(), window.getHeight(), lift);
        PreviewPipeline.compiled();
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.scale(1.0F / guiScale, 1.0F / guiScale);
        graphics.nextStratum();
        fill(graphics, at, at.scale(), PreviewStamp.SHADOW_ARGB);
        graphics.nextStratum();
        fill(graphics, at, 0, PreviewStamp.TEXT_ARGB);
        pose.popMatrix();
        drawn++;
        if (minecraft.level != null && minecraft.gui.hud.isHidden()) {
            drawnHudHidden++;
        }
        if (screen != null) {
            drawnOnScreen++;
        }
        if (screen instanceof TitleScreen) {
            drawnOnTitle++;
        }
        if (!announced) {
            announced = true;
            Constants.LOG.info("Vorschau-Build: Wasserzeichen „{}“ über Spiel, Menüs, Aufnahmen und Fotos, mit eigenem Shader aus dem Code",
                    PreviewBuild.TEXT);
        }
    }

    private static void fill(GuiGraphicsExtractor graphics, PreviewStamp.Placement at, int offset, int argb) {
        int scale = at.scale();
        int left = at.left() + offset;
        int top = at.top() + offset;
        for (int i = 0; i < RASTER.rectCount(); i++) {
            graphics.fill(PreviewPipeline.PIPELINE, left + RASTER.rect(i, 0) * scale, top + RASTER.rect(i, 1) * scale,
                    left + RASTER.rect(i, 2) * scale, top + RASTER.rect(i, 3) * scale, argb);
        }
    }

    public static boolean stampFrame(ByteBuffer frame, int width, int height) {
        if (!PreviewBuild.ENABLED) {
            return false;
        }
        PreviewStamp.stampI420(frame, width, height, mask(height));
        STAMPED_FRAMES.incrementAndGet();
        return true;
    }

    public static boolean stampImage(NativeImage image) {
        if (!PreviewBuild.ENABLED) {
            return false;
        }
        PreviewStamp.stampArgb(new PreviewStamp.Pixels() {
            @Override
            public int get(int x, int y) {
                return image.getPixel(x, y);
            }

            @Override
            public void set(int x, int y, int argb) {
                image.setPixel(x, y, argb);
            }
        }, image.getWidth(), image.getHeight(), mask(image.getHeight()));
        STAMPED_IMAGES.incrementAndGet();
        return true;
    }

    private static synchronized PreviewStamp.Mask mask(int height) {
        PreviewStamp.Mask known = stampMask;
        if (known != null && stampHeight == height) {
            return known;
        }
        known = PreviewStamp.mask(RASTER, height);
        stampMask = known;
        stampHeight = height;
        return known;
    }

    public static long passes() {
        return passes;
    }

    public static long drawn() {
        return drawn;
    }

    public static long drawnHudHidden() {
        return drawnHudHidden;
    }

    public static long drawnOnScreen() {
        return drawnOnScreen;
    }

    public static long drawnOnTitle() {
        return drawnOnTitle;
    }

    public static long stampedFrames() {
        return STAMPED_FRAMES.get();
    }

    public static long stampedImages() {
        return STAMPED_IMAGES.get();
    }
}
