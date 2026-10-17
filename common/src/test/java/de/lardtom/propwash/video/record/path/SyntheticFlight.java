package de.lardtom.propwash.video.record.path;

import org.joml.Quaterniond;

import java.util.ArrayList;
import java.util.List;

final class SyntheticFlight {
    static final double FPS = 60.0;

    private SyntheticFlight() {
    }

    static CameraPathBuffer fpv(double seconds) {
        CameraPathBuffer buffer = new CameraPathBuffer();
        double x = 10.0;
        double y = 80.0;
        double z = -4.0;
        double yaw = 30.0;
        double climb = 0.0;
        double dt = 1.0 / FPS;
        double start = 3.37;
        for (int frame = 0; frame <= (int) (seconds * FPS); frame++) {
            double t = frame * dt;
            double speed = 12.0 + 8.0 * Math.sin(0.3 * t);
            double yawRate = 55.0 * Math.sin(0.7 * t) + 20.0 * Math.sin(2.3 * t + 1.0);
            yaw += yawRate * dt;
            double pitch = 18.0 + 6.0 * Math.sin(1.1 * t);
            double roll = -yawRate * 0.45;
            double flip = flipAngle(t, 8.0, 0.6);
            double loop = loopAngle(t, 13.0, 2.0);
            double loopPitch = loop;
            climb = 2.5 * Math.sin(0.5 * t);
            double rad = Math.toRadians(yaw);
            double horizontal = speed * Math.cos(Math.toRadians(loopPitch));
            double vertical = speed * Math.sin(Math.toRadians(loopPitch)) + climb;
            if (flip != 0.0) {
                vertical += 4.0 - 9.0 * (t % 8.0);
            }
            x += -Math.sin(rad) * horizontal * dt;
            z += Math.cos(rad) * horizontal * dt;
            y += vertical * dt;
            Quaterniond q = new CameraAngles(yaw, pitch - loopPitch, roll).rotation();
            if (flip != 0.0) {
                q.rotateX(Math.toRadians(flip));
            }
            buffer.add(new PathSample(start + t * 20.0, x, y, z, q.x, q.y, q.z, q.w, 100.0));
        }
        return buffer;
    }

    private static double flipAngle(double t, double every, double duration) {
        double phase = t % every;
        double begin = every - duration - 1.0;
        if (t < every || phase < begin || phase > begin + duration) {
            return 0.0;
        }
        return 360.0 * (phase - begin) / duration;
    }

    private static double loopAngle(double t, double every, double duration) {
        double phase = t % every;
        double begin = every - duration - 2.0;
        if (t < every || phase < begin || phase > begin + duration) {
            return 0.0;
        }
        return 360.0 * (phase - begin) / duration;
    }

    static List<TickPose> poses(double seconds) {
        return PathResampler.perTick(fpv(seconds));
    }

    static List<TickPose> straight(int ticks, double speedPerTick) {
        List<TickPose> poses = new ArrayList<>();
        Quaterniond q = new CameraAngles(0.0, 10.0, 0.0).rotation();
        for (int i = 0; i < ticks; i++) {
            poses.add(new TickPose(i, 0.0, 70.0, i * speedPerTick, q, 100.0));
        }
        return poses;
    }
}
