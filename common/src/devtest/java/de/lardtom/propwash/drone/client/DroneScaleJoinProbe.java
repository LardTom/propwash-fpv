package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.dev.DroneScaleJoinCheck;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.render.DroneRenderState;
import de.lardtom.propwash.sim.AirframeSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DroneScaleJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 60;
    private static final double SIZE_EPS = 1.0E-4;
    private static final String START = "drone_scale_start";
    private static final String SEEN = "drone_scale_seen";
    private static final String DRONE = "drone_scale_drone";

    private DroneScaleJoinProbe() {
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        int start = (int) state.computeIfAbsent(START, key -> tick);
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return tick - start > TIMEOUT_TICKS ? "drone_scale=fail no_player\n" : null;
        }
        if (!state.containsKey(DRONE)) {
            List<QuadcopterEntity> drones = mc.level.getEntitiesOfClass(QuadcopterEntity.class, player.getBoundingBox().inflate(16.0),
                    drone -> drone.scale() == DroneScaleJoinCheck.SUMMONED);
            if (drones.isEmpty()) {
                return tick - start > TIMEOUT_TICKS ? "drone_scale=fail not_seen\n" : null;
            }
            QuadcopterEntity drone = drones.getFirst();
            String size = size(mc, drone, DroneScaleJoinCheck.SUMMONED);
            if (size == null) {
                return tick - start > TIMEOUT_TICKS ? "drone_scale=fail size " + describe(drone) + "\n" : null;
            }
            state.put(DRONE, drone.getId());
            state.put(SEEN, "drone_scale initial " + size + "\n");
            return null;
        }
        int id = (int) state.get(DRONE);
        if (!(mc.level.getEntity(id) instanceof QuadcopterEntity drone)) {
            return "drone_scale=fail lost\n" + state.get(SEEN);
        }
        if (drone.scale() != DroneScaleJoinCheck.CHANGED) {
            return tick - start > TIMEOUT_TICKS ? "drone_scale=fail no_update " + describe(drone) + "\n" + state.get(SEEN) : null;
        }
        String size = size(mc, drone, DroneScaleJoinCheck.CHANGED);
        if (size == null) {
            return tick - start > TIMEOUT_TICKS ? "drone_scale=fail size " + describe(drone) + "\n" + state.get(SEEN) : null;
        }
        return state.get(SEEN) + "drone_scale later " + size + "\ndrone_scale=ok\n";
    }

    private static @Nullable String size(Minecraft mc, QuadcopterEntity drone, float scale) {
        AirframeSpec spec = AirframeSpec.of(drone.build());
        if (Math.abs(drone.getBbWidth() - spec.hitboxWidth * scale) > SIZE_EPS || Math.abs(drone.getBbHeight() - spec.hitboxHeight * scale) > SIZE_EPS) {
            return null;
        }
        EntityRenderState state = mc.getEntityRenderDispatcher().getRenderer(drone).createRenderState(drone, 1.0F);
        if (!(state instanceof DroneRenderState drawn) || drawn.entityScale != scale) {
            return null;
        }
        return String.format(Locale.ROOT, "scale=%.3f width=%.4f height=%.4f model=%.3f", drone.scale(), drone.getBbWidth(), drone.getBbHeight(),
                drawn.entityScale);
    }

    private static String describe(QuadcopterEntity drone) {
        return String.format(Locale.ROOT, "scale=%.3f width=%.4f height=%.4f", drone.scale(), drone.getBbWidth(), drone.getBbHeight());
    }
}
