package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.drone.LosAim;
import de.lardtom.propwash.drone.PilotLook;
import de.lardtom.propwash.drone.PilotMode;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

public final class LosView {
    private static final long MAX_FRAME_NANOS = 100_000_000L;
    private static final Vector3d TARGET = new Vector3d();
    private static long lastNanos;

    private LosView() {
    }

    public static void onFrame(Minecraft mc, DeltaTracker deltaTracker) {
        long now = Util.getNanos();
        long frame = lastNanos == 0L ? 0L : now - lastNanos;
        lastNanos = now;
        PilotSession session = PilotSession.INSTANCE;
        LocalPlayer player = mc.player;
        PilotMode mode = session.mode();
        if (!PilotLook.viewFollowsDrone(mode) || player == null || mc.isPaused()) {
            return;
        }
        session.comPosition(TARGET);
        Vec3 eye = player.getEyePosition();
        double dt = Math.max(0L, Math.min(MAX_FRAME_NANOS, frame)) * 1.0E-9;
        LosAim.Angles angles = PilotLook.view(mode, player.getYRot(), player.getXRot(), TARGET.x - eye.x, TARGET.y - eye.y, TARGET.z - eye.z, dt);
        apply(player, angles.yaw(), angles.pitch());
    }

    public static void snapTo(LocalPlayer player, double x, double y, double z) {
        Vec3 eye = player.getEyePosition();
        double dx = x - eye.x;
        double dy = y - eye.y;
        double dz = z - eye.z;
        if (dx * dx + dy * dy + dz * dz < LosAim.HOLD_DISTANCE * LosAim.HOLD_DISTANCE) {
            return;
        }
        apply(player, LosAim.yawTo(dx, dz), LosAim.pitchTo(dx, dy, dz));
    }

    private static void apply(LocalPlayer player, float yaw, float pitch) {
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.setYHeadRot(yaw);
    }
}
