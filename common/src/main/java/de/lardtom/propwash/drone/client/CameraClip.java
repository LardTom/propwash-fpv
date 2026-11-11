package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.drone.collision.client.ClientCollision;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

public final class CameraClip {
    private static final List<BooleanSupplier> SOURCES = new CopyOnWriteArrayList<>();

    private CameraClip() {
    }

    public static void register(BooleanSupplier source) {
        SOURCES.add(Objects.requireNonNull(source, "source"));
    }

    public static boolean active() {
        if (ClientCollision.INSTANCE.cameraClip()) {
            return true;
        }
        for (BooleanSupplier source : SOURCES) {
            try {
                if (source.getAsBoolean()) {
                    return true;
                }
            } catch (RuntimeException e) {
                SOURCES.remove(source);
                Constants.LOG.warn("Kamera-Quelle entfernt: Abfrage fehlgeschlagen", e);
            }
        }
        return false;
    }
}
