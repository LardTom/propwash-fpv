package de.lardtom.propwash.audio.client;

import com.mojang.blaze3d.audio.ListenerTransform;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.mixin.audio.client.SoundManagerAccessor;
import de.lardtom.propwash.mixin.dev.client.SoundEngineAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

public final class HearingProbe {
    public static final double CAMERA_TOLERANCE = 0.25;
    public static final double DRONE_TOLERANCE = 1.5;
    public static final double MIN_BODY_DISTANCE = 150.0;
    public static final double MIN_DOT = 0.99;

    private HearingProbe() {
    }

    public record Result(boolean measured, boolean ok, String line) {
    }

    public static Result check(Minecraft mc, QuadcopterEntity drone) {
        if (!SoundEngineAccess.engineLoaded(mc.getSoundManager()) || mc.player == null) {
            return new Result(false, true, "Hörposition: kein Audiogerät, übersprungen");
        }
        SoundEngine engine = ((SoundManagerAccessor) (Object) mc.getSoundManager()).propwash$soundEngine();
        ListenerTransform transform = ((SoundEngineAccessor) (Object) engine).propwash$listener().getTransform();
        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 at = transform.position();
        double toCamera = at.distanceTo(camera.position());
        double toDrone = at.distanceTo(drone.position());
        double toBody = at.distanceTo(mc.player.position());
        double forward = transform.forward().dot(new Vec3(camera.forwardVector()));
        double up = transform.up().dot(new Vec3(camera.upVector()));
        boolean ok = toCamera <= CAMERA_TOLERANCE && toDrone <= DRONE_TOLERANCE && toBody >= MIN_BODY_DISTANCE && forward >= MIN_DOT && up >= MIN_DOT;
        String line = String.format(Locale.ROOT, "Hörposition: %.2f zur Kamera, %.2f zur Drohne, %.1f zum Körper, Blick %.4f, oben %.4f", toCamera, toDrone, toBody,
                forward, up);
        return new Result(true, ok, line);
    }
}
