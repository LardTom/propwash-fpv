package de.lardtom.propwash.osd.addon.client;

import de.lardtom.propwash.api.client.hud.FlightTelemetry;
import de.lardtom.propwash.api.client.osd.OsdContext;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.FpvView;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.sim.HoldMode;
import de.lardtom.propwash.video.client.SpectateSession;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

public record AddonOsdContext(Optional<Entity> drone, FlightTelemetry telemetry, boolean fpv, boolean lineOfSight, boolean preview, long gameTime,
                              float partialTick) implements OsdContext {
    public static OsdContext create(OsdData data, boolean preview) {
        Minecraft minecraft = Minecraft.getInstance();
        long gameTime = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        if (preview) {
            return new AddonOsdContext(Optional.empty(), telemetry(data), true, false, true, gameTime, 0.0F);
        }
        PilotSession session = PilotSession.INSTANCE;
        boolean spectating = !session.isActive() && FpvView.spectating();
        QuadcopterEntity drone = session.isActive() ? session.drone() : spectating ? SpectateSession.INSTANCE.drone() : null;
        return new AddonOsdContext(Optional.ofNullable(drone), telemetry(data), session.isFpv() || spectating, session.isLineOfSight(), false, gameTime,
                minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false));
    }

    public static FlightTelemetry telemetry(OsdData d) {
        String mode = d.rescueActive ? "RESCUE" : d.holdMode == null || d.holdMode == HoldMode.OFF ? (d.mode == null ? "ACRO" : d.mode.name()) : d.holdMode.name();
        return new FlightTelemetry(d.armed, mode, Optional.ofNullable(d.assistLabel), d.x, d.y, d.z, d.speed, d.verticalSpeed, d.compassDeg,
                -d.pitchDeg, d.rollDeg, d.throttle, d.packVoltage, d.cellVoltage, d.chargeFraction, d.hasHome, d.homeX, d.homeY, d.homeZ,
                d.gpsFix, d.gpsSatellites, d.linkStrength, d.videoSignal, d.flightSeconds, d.stickRoll, d.stickPitch, d.stickYaw, d.stickThrottle);
    }
}
