package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.core.client.ClientBus;
import net.minecraft.client.Minecraft;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.concurrent.atomic.AtomicBoolean;

public final class FpvJoltProbe {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final Quaternionf BODY = new Quaternionf();
    private static final Quaternionf EXPECTED = new Quaternionf();
    private static final Vector3d COM = new Vector3d();
    private static volatile boolean recording;
    private static volatile double max;
    private static volatile int frames;

    private FpvJoltProbe() {
    }

    public static void register() {
        if (REGISTERED.compareAndSet(false, true)) {
            ClientBus.FRAME_START.register((minecraft, deltaTracker) -> {
                if (recording) {
                    max = Math.max(max, deviation());
                    frames++;
                }
            });
        }
    }

    public static void intensity(float value) {
        FpvShake.INSTANCE.testIntensity(value);
    }

    public static void start() {
        max = 0.0;
        frames = 0;
        recording = true;
    }

    public static double stop() {
        recording = false;
        return max;
    }

    public static double max() {
        return max;
    }

    public static int frames() {
        return frames;
    }

    public static int hits() {
        return FpvShake.INSTANCE.jolt().hits();
    }

    public static double lastAmplitude() {
        return FpvShake.INSTANCE.jolt().lastAmplitude();
    }

    public static double deviation() {
        if (!FpvView.displayedBody(COM, BODY)) {
            return 0.0;
        }
        EXPECTED.set(BODY).rotateX((float) Math.toRadians(PilotSession.INSTANCE.cameraUptiltDeg()));
        Quaternionf camera = Minecraft.getInstance().gameRenderer.mainCamera().rotation();
        double ax = -EXPECTED.x;
        double ay = -EXPECTED.y;
        double az = -EXPECTED.z;
        double aw = EXPECTED.w;
        double bx = camera.x();
        double by = camera.y();
        double bz = camera.z();
        double bw = camera.w();
        double rw = aw * bw - ax * bx - ay * by - az * bz;
        double rx = aw * bx + ax * bw + ay * bz - az * by;
        double ry = aw * by - ax * bz + ay * bw + az * bx;
        double rz = aw * bz + ax * by - ay * bx + az * bw;
        return Math.toDegrees(2.0 * Math.atan2(Math.sqrt(rx * rx + ry * ry + rz * rz), Math.abs(rw)));
    }
}
