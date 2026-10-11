package de.lardtom.propwash.video.client;

import com.mojang.blaze3d.platform.Window;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.drone.client.FpvView;
import de.lardtom.propwash.ui.client.DroneContext;
import de.lardtom.propwash.video.ImageArea;
import de.lardtom.propwash.video.ImageFormat;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

public final class FpvImage {
    private static volatile @Nullable ImageFormat draft;

    private FpvImage() {
    }

    public static ImageFormat setting() {
        return VideoSettings.get().imageFormat;
    }

    public static boolean active() {
        return FpvView.active();
    }

    public static ImageFormat format() {
        return setting().resolve(transmitter());
    }

    public static @Nullable VideoSystem transmitter() {
        VideoState state = VideoState.INSTANCE;
        VideoSystem system = state.system();
        if (system != null) {
            return system;
        }
        VideoType camera = state.camera();
        return camera == null ? null : camera.system;
    }

    public static ImageArea area() {
        Window window = Minecraft.getInstance().getWindow();
        return area(window.getWidth(), window.getHeight());
    }

    public static ImageArea area(int width, int height) {
        return active() ? ImageArea.of(format(), width, height) : ImageArea.full(width, height);
    }

    public static ImageFormat editorFormat() {
        ImageFormat chosen = draft;
        if (chosen == null) {
            chosen = setting();
        }
        if (chosen != ImageFormat.AUTO) {
            return chosen;
        }
        return chosen.resolve(active() ? transmitter() : contextSystem());
    }

    public static ImageFormat formatFor(@Nullable VideoSystem transmitter) {
        return setting().resolve(transmitter);
    }

    public static void draft(@Nullable ImageFormat format) {
        draft = format;
    }

    public static @Nullable VideoSystem contextSystem() {
        Minecraft minecraft = Minecraft.getInstance();
        VideoType live = VideoState.INSTANCE.camera();
        if (live != null) {
            return live.system;
        }
        DroneContext context = DroneContext.resolve(minecraft);
        if (context.hasDrone()) {
            return context.build().video().system;
        }
        return minecraft.player == null ? null : ContentIds.wornGogglesSystem(minecraft.player).orElse(null);
    }

    public static float guiScale() {
        return Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
    }
}
