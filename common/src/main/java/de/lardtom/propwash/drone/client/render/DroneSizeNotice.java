package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemedToast;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class DroneSizeNotice {
    public static final Object TOAST_TOKEN = new Object();
    private static final long TOAST_MILLIS = 12000L;

    private DroneSizeNotice() {
    }

    static void onClientTick(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null || minecraft.gui.screen() != null) {
            return;
        }
        DroneRenderConfig config = DroneRenderConfig.get();
        if (!config.sizeNotice) {
            return;
        }
        DroneRenderConfig cleared = config.copy();
        cleared.sizeNotice = false;
        DroneRenderConfig.CONFIG.set(cleared);
        ThemedToast.show(minecraft.gui.toastManager(), TOAST_TOKEN, Component.translatable("propwash.drone_render.size_notice.title"),
                Component.translatable("propwash.drone_render.size_notice.message"), Palette.ACCENT, TOAST_MILLIS);
    }
}
