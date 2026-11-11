package de.lardtom.propwash.video.client;

import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.config.StepSlider;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

public final class CameraAngleJoinProbe {
    public static final float ANGLE = 40.0F;
    public static final String OPEN = "camangle_open";
    private static final int OPEN_TICKS = 20 * 10;
    private static final int TIMEOUT_TICKS = 20 * 30;
    private static final String START = "camangle_start";
    private static final String LIVE = "camangle_live";

    private CameraAngleJoinProbe() {
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        int start = (int) state.computeIfAbsent(START, key -> tick);
        if (!state.containsKey(LIVE)) {
            String problem = open(mc);
            if (problem == null) {
                state.put(OPEN, tick);
                state.put(LIVE, "pending");
                return null;
            }
            return tick - start > TIMEOUT_TICKS ? "camangle=" + problem + "\n" : null;
        }
        float now = PilotSession.INSTANCE.cameraUptiltDeg();
        if (now == ANGLE) {
            return "camangle=" + Math.round(now) + "\ncamangle_live=" + state.get(LIVE) + "\n";
        }
        return tick - start > TIMEOUT_TICKS ? String.format(Locale.ROOT, "camangle=timeout %.1f%ncamangle_live=%s%n", now, state.get(LIVE)) : null;
    }

    public static void whileOpen(Map<String, Object> state, int tick, Minecraft mc) {
        boolean tilted = PilotSession.INSTANCE.cameraUptiltDeg() == ANGLE;
        boolean late = tick - (int) state.get(OPEN) > OPEN_TICKS;
        if (!tilted && !late && mc.gui.screen() instanceof ConfiguratorScreen) {
            return;
        }
        state.put(LIVE, tilted && mc.gui.screen() instanceof ConfiguratorScreen ? "menu_open" : "after_close");
        state.remove(OPEN);
        if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
            screen.saveAndClose();
        }
    }

    private static @Nullable String open(Minecraft mc) {
        if (!PilotSession.INSTANCE.isFpv()) {
            return "no_fpv";
        }
        ConfigTabs.open(null, "video");
        if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
            return "menu_closed";
        }
        CameraAngleSection section = screen.activeTab() instanceof VideoConfigTab tab ? tab.cameraSection() : null;
        StepSlider slider = section == null ? null : section.slider();
        if (slider == null) {
            screen.discardAndClose();
            return "no_slider";
        }
        slider.select(ANGLE);
        return null;
    }
}
