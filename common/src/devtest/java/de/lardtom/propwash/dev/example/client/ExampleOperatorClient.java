package de.lardtom.propwash.dev.example.client;

import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.camera.GimbalContext;
import de.lardtom.propwash.api.client.camera.GimbalProvider;
import de.lardtom.propwash.dev.example.ExampleAddon;
import de.lardtom.propwash.dev.example.ExampleOperator;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class ExampleOperatorClient {
    public static final String GIMBAL = ExampleAddon.NAMESPACE + ":operator_gimbal";
    public static final double BASE_PITCH = -10.0;
    public static final double MIN_PITCH = -90.0;
    public static final double MAX_PITCH = 30.0;
    public static final double PAN_LIMIT = 120.0;
    public static final double TILT_RATE = 60.0;
    public static final double PAN_RATE = 90.0;
    public static final double DEADBAND = 0.05;
    public static final Gimbal PROVIDER = new Gimbal();
    private static boolean initialized;

    private ExampleOperatorClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PropwashClientApi.registerGimbal(GIMBAL, PROVIDER);
    }

    public static final class Gimbal implements GimbalProvider {
        private final Map<Integer, Aim> aims = new HashMap<>();
        private final Quaternionf world = new Quaternionf();
        private volatile double fov = Double.NaN;
        private volatile boolean fpvImage;
        private volatile double @Nullable [] forced;
        private volatile int poses;

        @Override
        public boolean appliesTo(Entity drone) {
            return ExampleOperator.carries(drone);
        }

        @Override
        public void orient(GimbalContext context, Quaternionf rotation) {
            Aim aim = this.aim(context);
            if (!this.fpvImage) {
                world(context.frame(), aim, rotation);
            }
        }

        @Override
        public boolean modelPose(GimbalContext context, Quaternionf rotation) {
            Aim aim = this.aim(context);
            world(context.frame(), aim, this.world);
            rotation.set(context.frame()).conjugate().mul(this.world).normalize();
            this.poses++;
            return true;
        }

        @Override
        public double fovDegrees(GimbalContext context) {
            return this.fpvImage ? Double.NaN : this.fov;
        }

        @Override
        public boolean stabilizesRecording() {
            return true;
        }

        @Override
        public boolean showsProps(GimbalContext context) {
            return this.fpvImage;
        }

        @Override
        public boolean gimbalRotation(GimbalContext context, Quaternionf rotation) {
            world(context.frame(), this.aim(context), rotation);
            return true;
        }

        @Override
        public double gimbalFovDegrees(GimbalContext context) {
            return this.fov;
        }

        public void fov(double degrees) {
            this.fov = degrees;
        }

        public void fpvImage(boolean fpv) {
            this.fpvImage = fpv;
        }

        public boolean fpvImage() {
            return this.fpvImage;
        }

        public synchronized Quaternionf expected(Entity drone, Quaternionfc frame, Quaternionf dest) {
            Aim aim = this.aims.get(drone.getId());
            world(frame, aim == null ? new Aim() : aim, dest);
            return dest;
        }

        public void force(@Nullable Double pitch, @Nullable Double pan) {
            this.forced = pitch == null || pan == null ? null : new double[]{pitch, pan};
        }

        public int poses() {
            return this.poses;
        }

        public double pitch(Entity drone) {
            Aim aim = this.aims.get(drone.getId());
            return aim == null ? BASE_PITCH : aim.pitch;
        }

        public double pan(Entity drone) {
            Aim aim = this.aims.get(drone.getId());
            return aim == null ? 0.0 : aim.pan;
        }

        private synchronized Aim aim(GimbalContext context) {
            Aim aim = this.aims.computeIfAbsent(context.drone().getId(), id -> new Aim());
            double[] fixed = this.forced;
            if (fixed != null) {
                aim.pitch = fixed[0];
                aim.pan = fixed[1];
                return aim;
            }
            long now = System.nanoTime();
            double dt = aim.nanos == 0L ? 0.0 : Math.min(0.25, (now - aim.nanos) / 1.0E9);
            aim.nanos = now;
            aim.pitch = clamp(aim.pitch + deadband(context.operatorAxis(1)) * TILT_RATE * dt, MIN_PITCH, MAX_PITCH);
            aim.pan = clamp(aim.pan + deadband(context.operatorAxis(0)) * PAN_RATE * dt, -PAN_LIMIT, PAN_LIMIT);
            return aim;
        }

        private static void world(Quaternionfc frame, Aim aim, Quaternionf dest) {
            Vector3f forward = frame.transform(new Vector3f(0.0F, 0.0F, -1.0F));
            double heading = Math.toDegrees(Math.atan2(forward.x, -forward.z));
            dest.rotationY((float) -Math.toRadians(heading + aim.pan)).rotateX((float) Math.toRadians(aim.pitch));
        }

        private static double deadband(double value) {
            double v = Double.isFinite(value) ? Math.max(-1.0, Math.min(1.0, value)) : 0.0;
            return Math.abs(v) <= DEADBAND ? 0.0 : Math.copySign((Math.abs(v) - DEADBAND) / (1.0 - DEADBAND), v);
        }

        private static double clamp(double value, double min, double max) {
            return Math.max(min, Math.min(max, value));
        }
    }

    private static final class Aim {
        private double pitch = BASE_PITCH;
        private double pan;
        private long nanos;
    }
}
