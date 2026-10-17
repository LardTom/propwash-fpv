package de.lardtom.propwash.accessory.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.client.camera.GimbalContext;
import de.lardtom.propwash.api.client.camera.GimbalProvider;
import de.lardtom.propwash.drone.QuadcopterEntity;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public final class AddonCamera {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Map<String, GimbalProvider> PROVIDERS = new LinkedHashMap<>();
    private static final Quaternionf FRAME = new Quaternionf();
    private static final Quaternionf SAVED = new Quaternionf();
    private static volatile boolean any;
    private static long lastNanos;
    private static int lastDrone = -1;
    private static int applied;
    private static boolean failureLogged;

    private AddonCamera() {
    }

    public static synchronized void register(String id, GimbalProvider provider) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(provider, "provider");
        if (!ID.matcher(id).matches() || id.startsWith(Constants.MOD_ID + ":")) {
            throw new IllegalArgumentException("Gimbal-Id braucht einen eigenen Namensraum: " + id);
        }
        if (PROVIDERS.putIfAbsent(id, provider) != null) {
            throw new IllegalArgumentException("Gimbal doppelt angemeldet: " + id);
        }
        any = true;
    }

    public static int appliedFrames() {
        return applied;
    }

    public static synchronized boolean apply(QuadcopterEntity drone, Quaternionfc frame, float uptiltDegrees, Quaternionf rotation, boolean spectating) {
        if (!any) {
            return false;
        }
        GimbalProvider provider = null;
        for (GimbalProvider candidate : PROVIDERS.values()) {
            try {
                if (candidate.appliesTo(drone)) {
                    provider = candidate;
                    break;
                }
            } catch (RuntimeException e) {
                logOnce(e);
            }
        }
        if (provider == null) {
            return false;
        }
        long now = System.nanoTime();
        double dt = drone.getId() == lastDrone && lastNanos != 0L ? (now - lastNanos) / 1.0E9 : 0.0;
        if (dt < 0.0 || dt > 0.25) {
            dt = 0.0;
        }
        lastNanos = now;
        lastDrone = drone.getId();
        FRAME.set(frame);
        SAVED.set(rotation);
        Context context = new Context(drone, new Quaternionf(FRAME), uptiltDegrees, dt, spectating);
        try {
            provider.orient(context, rotation);
        } catch (RuntimeException e) {
            logOnce(e);
            rotation.set(SAVED);
            return false;
        }
        if (!Float.isFinite(rotation.x + rotation.y + rotation.z + rotation.w) || rotation.lengthSquared() < 1.0E-6F) {
            rotation.set(SAVED);
            return false;
        }
        rotation.normalize();
        applied++;
        return provider.suppressesShake();
    }

    private static void logOnce(RuntimeException e) {
        if (!failureLogged) {
            failureLogged = true;
            Constants.LOG.warn("Gimbal eines Add-ons fehlgeschlagen", e);
        }
    }

    private record Context(Entity drone, Quaternionfc frame, float uptiltDegrees, double deltaSeconds, boolean spectating)
            implements GimbalContext {
    }
}
