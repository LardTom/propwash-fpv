package de.lardtom.propwash.video.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.device.DeviceInfo;
import de.lardtom.propwash.Constants;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

public final class GpuInfo {
    private static @Nullable String described;
    private static boolean logged;

    private GpuInfo() {
    }

    public static String describe() {
        String text = described;
        if (text != null) {
            return text;
        }
        try {
            DeviceInfo info = RenderSystem.getDevice().getDeviceInfo();
            text = String.format("%s / %s / %s (%s, Tiefe %s)", clean(info.vendorName()), clean(info.name()), clean(info.driverInfo()),
                    clean(info.backendName()), info.isZZeroToOne() ? "0..1" : "-1..1");
        } catch (RuntimeException exception) {
            return "unbekannt (" + exception.getClass().getSimpleName() + ")";
        }
        described = text;
        return text;
    }

    static void logOnce(Minecraft minecraft, DeltaTracker deltaTracker) {
        if (logged) {
            return;
        }
        logged = true;
        Constants.LOG.info("Grafik: {}", describe());
    }

    private static String clean(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return "?";
        }
        return value.strip().replaceAll("\\s+", " ");
    }
}
