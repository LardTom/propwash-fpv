package de.lardtom.propwash.osd.client;

import de.lardtom.propwash.osd.FlightPathMarker;
import de.lardtom.propwash.osd.OsdConfig;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.OsdElement;
import de.lardtom.propwash.osd.OsdGrid;
import de.lardtom.propwash.osd.OsdProjection;
import de.lardtom.propwash.osd.OsdWarnings;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class CovFreecamOsdProbe {
    private CovFreecamOsdProbe() {
    }

    public record Marker(boolean visible, boolean inView, float x, float y) {
    }

    public static void show(OsdElement... elements) {
        OsdConfig config = OsdClient.CONFIG.get().copy();
        for (OsdElement element : elements) {
            config.slot(element).enabled = true;
        }
        OsdHud.overrideConfig(config);
    }

    public static void restore() {
        OsdHud.overrideConfig(null);
    }

    public static boolean drawn(OsdElement element) {
        return OsdHud.drewLastFrame(element);
    }

    public static String craftName() {
        return OsdHud.sampler().data().craftName;
    }

    public static OsdData data() {
        return OsdHud.sampler().data();
    }

    public static List<OsdWarnings.Warning> warnings() {
        List<OsdWarnings.Warning> warnings = new ArrayList<>();
        OsdWarnings.collect(OsdHud.sampler().data(), warnings);
        return warnings;
    }

    public static Marker marker() {
        FlightPathMarker.State state = OsdHud.renderer().flightPathState();
        return new Marker(state.visible, state.inView, state.x, state.y);
    }

    public static OsdGrid grid(Minecraft mc) {
        return OsdHud.grid(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
    }

    public static @Nullable Marker predict(Minecraft mc, double vx, double vy, double vz) {
        OsdRenderer.View view = OsdHud.liveView(mc);
        if (view == null) {
            return null;
        }
        OsdGrid grid = grid(mc);
        OsdProjection projection = new OsdProjection();
        if (view.fisheyeDeg() > 0.0F) {
            projection.setupFisheye(view.rotation(), view.fisheyeDeg(), view.fisheyeBend(), grid.centerX(), grid.centerY(), grid.width());
        } else {
            projection.setup(view.rotation(), view.fovDeg(), grid.centerX(), grid.centerY(), grid.height());
        }
        float margin = grid.cellHeight() * 1.5F;
        FlightPathMarker.State state = new FlightPathMarker().compute(vx, vy, vz, projection, grid.left() + margin, grid.top() + margin,
                grid.left() + grid.width() - margin, grid.top() + grid.height() - margin, new FlightPathMarker.State());
        return new Marker(state.visible, state.inView, state.x, state.y);
    }
}
