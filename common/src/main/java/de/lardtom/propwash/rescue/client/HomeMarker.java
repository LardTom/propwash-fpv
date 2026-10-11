package de.lardtom.propwash.rescue.client;

import com.mojang.blaze3d.vertex.PoseStack;
import de.lardtom.propwash.compat.iris.client.IrisCompat;
import de.lardtom.propwash.drone.client.FpvView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;

public final class HomeMarker {
    private static final int IDLE_COLOR = 0xB040FF90;
    private static final int ACTIVE_TINT = 0xE0FFC040;
    private static final int OPAQUE = 0xFF000000;
    private static final double POLE_HEIGHT = 3.0;
    private static final double MAX_DISTANCE_SQ = 256.0 * 256.0;

    private HomeMarker() {
    }

    public static void submit(SubmitNodeCollector collector, PoseStack poseStack, LevelRenderState levelState) {
        Vec3 home = RescueState.INSTANCE.home();
        if (home == null || Minecraft.getInstance().gui.hud.isHidden() || FpvView.spectating()) {
            return;
        }
        Vec3 camera = levelState.cameraRenderState.pos;
        if (camera == null || camera.distanceToSqr(home) > MAX_DISTANCE_SQ) {
            return;
        }
        int color = RescueState.INSTANCE.active() ? ACTIVE_TINT : IDLE_COLOR;
        if (IrisCompat.shaderPackActive(Util.getNanos())) {
            color |= OPAQUE;
        }
        Vec3 base = home.add(0.0, 0.02, 0.0);
        Gizmos.circle(base, 0.75F, GizmoStyle.stroke(color, 2.5F));
        Gizmos.circle(base, 0.3F, GizmoStyle.stroke(color, 2.0F));
        Gizmos.line(base, base.add(0.0, POLE_HEIGHT, 0.0), color, 2.0F);
    }
}
