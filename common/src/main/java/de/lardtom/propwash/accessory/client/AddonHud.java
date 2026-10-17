package de.lardtom.propwash.accessory.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.client.hud.FlightTelemetry;
import de.lardtom.propwash.api.client.hud.HudContext;
import de.lardtom.propwash.api.client.hud.HudElement;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.FpvView;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.client.LiveOsdSource;
import de.lardtom.propwash.osd.client.OsdHud;
import de.lardtom.propwash.sim.HoldMode;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.video.ImageArea;
import de.lardtom.propwash.video.client.FpvImage;
import de.lardtom.propwash.video.client.SpectateSession;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public final class AddonHud {
    public static final Identifier ID = Constants.id("addon_hud");
    private static final Pattern ELEMENT_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Map<String, HudElement> ELEMENTS = new LinkedHashMap<>();
    private static final OsdData LOS_DATA = new OsdData();
    private static final LiveOsdSource LOS_SOURCE = new LiveOsdSource();
    private static volatile boolean any;
    private static int renderedFrames;
    private static boolean failureLogged;

    private AddonHud() {
    }

    public static synchronized void register(String id, HudElement element) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(element, "element");
        if (!ELEMENT_ID.matcher(id).matches() || id.startsWith(Constants.MOD_ID + ":")) {
            throw new IllegalArgumentException("HUD-Element braucht einen eigenen Namensraum: " + id);
        }
        if (ELEMENTS.putIfAbsent(id, element) != null) {
            throw new IllegalArgumentException("HUD-Element doppelt angemeldet: " + id);
        }
        any = true;
    }

    public static int renderedFrames() {
        return renderedFrames;
    }

    public static synchronized boolean replacesOsd() {
        if (!any) {
            return false;
        }
        Context context = context(Minecraft.getInstance(), 0.0F);
        if (context == null) {
            return false;
        }
        for (HudElement element : ELEMENTS.values()) {
            try {
                if (element.active(context) && element.replacesOsd(context)) {
                    return true;
                }
            } catch (RuntimeException e) {
                logOnce(e);
            }
        }
        return false;
    }

    public static synchronized void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (!any) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() instanceof ThemedScreen) {
            return;
        }
        Context context = context(minecraft, deltaTracker.getGameTimeDeltaPartialTick(false));
        if (context == null) {
            return;
        }
        boolean drew = false;
        for (HudElement element : ELEMENTS.values()) {
            try {
                if (element.active(context)) {
                    graphics.nextStratum();
                    element.render(graphics, context);
                    drew = true;
                }
            } catch (RuntimeException e) {
                logOnce(e);
            }
        }
        if (drew) {
            renderedFrames++;
        }
    }

    private static @Nullable Context context(Minecraft minecraft, float partialTick) {
        PilotSession session = PilotSession.INSTANCE;
        boolean fpv = session.isFpv();
        boolean los = session.isLineOfSight();
        boolean spectating = !session.isActive() && FpvView.spectating();
        QuadcopterEntity drone = session.isActive() ? session.drone() : spectating ? SpectateSession.INSTANCE.drone() : null;
        if (drone == null) {
            return null;
        }
        OsdData data;
        if (fpv || spectating) {
            data = OsdHud.sampler().data();
        } else {
            LOS_DATA.reset();
            LOS_SOURCE.fill(LOS_DATA);
            data = LOS_DATA;
        }
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        int x0 = 0;
        int y0 = 0;
        int x1 = width;
        int y1 = height;
        if (fpv || spectating) {
            ImageArea area = FpvImage.area();
            if (!area.full()) {
                float scale = FpvImage.guiScale();
                x0 = Math.round(area.x() / scale);
                y0 = Math.round(area.y() / scale);
                x1 = Math.round((area.x() + area.width()) / scale);
                y1 = Math.round((area.y() + area.height()) / scale);
            }
        }
        return new Context(drone, fpv, los, spectating, x0, y0, x1, y1, partialTick, telemetry(data), minecraft.font);
    }

    static FlightTelemetry telemetry(OsdData d) {
        String mode = d.rescueActive ? "RESCUE" : d.holdMode == HoldMode.OFF ? (d.mode == null ? "ACRO" : d.mode.name()) : d.holdMode.name();
        return new FlightTelemetry(d.armed, mode, Optional.ofNullable(d.assistLabel), d.x, d.y, d.z, d.speed, d.verticalSpeed, d.compassDeg,
                -d.pitchDeg, d.rollDeg, d.throttle, d.packVoltage, d.cellVoltage, d.chargeFraction, d.hasHome, d.homeX, d.homeY, d.homeZ,
                d.gpsFix, d.gpsSatellites, d.linkStrength, d.videoSignal, d.flightSeconds, d.stickRoll, d.stickPitch, d.stickYaw, d.stickThrottle);
    }

    private static void logOnce(RuntimeException e) {
        if (!failureLogged) {
            failureLogged = true;
            Constants.LOG.warn("HUD-Element eines Add-ons fehlgeschlagen", e);
        }
    }

    private record Context(Entity drone, boolean fpv, boolean lineOfSight, boolean spectating, int imageX0, int imageY0, int imageX1,
                           int imageY1, float partialTick, FlightTelemetry telemetry, Font font) implements HudContext {
    }
}
