package de.lardtom.propwash.compat.flashback.client;

import com.mojang.blaze3d.platform.Window;
import de.lardtom.propwash.video.record.path.FlashbackBudget;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLVideo;

public final class FlashbackUiScale {
    private FlashbackUiScale() {
    }

    public static float contentScale(Minecraft minecraft) {
        try {
            Window window = minecraft.getWindow();
            long handle = window.handle();
            if (handle == 0L) {
                return 1.0F;
            }
            float display = SDLVideo.SDL_GetWindowDisplayScale(handle);
            float density = window.getPixelDensity();
            if (!(display > 0.0F)) {
                return 1.0F;
            }
            return FlashbackBudget.quantize(display / (density > 0.0F ? density : 1.0F));
        } catch (RuntimeException | LinkageError e) {
            return 1.0F;
        }
    }

    public static float estimate(Minecraft minecraft) {
        return contentScale(minecraft) * Math.max(0.25F, Math.min(4.0F, FlashbackBudget.quantize(FlashbackBridge.editorZoom())));
    }

    public static float budgetScale(Minecraft minecraft) {
        return FlashbackBudget.assumedScale(estimate(minecraft));
    }
}
