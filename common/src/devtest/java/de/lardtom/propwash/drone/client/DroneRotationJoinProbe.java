package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.dev.DroneRotationJoinCheck;
import de.lardtom.propwash.drone.QuadcopterEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DroneRotationJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 60;
    private static final float EPS = 0.5F;
    private static final double RADIUS = 24.0;
    private static final int HOLD_TICKS = DroneRotationJoinCheck.STEP_TICKS + 40;
    private static final String START = "drone_rotation_start";
    private static final String DRONE = "drone_rotation_drone";
    private static final String PHASE = "drone_rotation_phase";
    private static final String MARK = "drone_rotation_mark";
    private static final String LAST = "drone_rotation_last";
    private static final String BASE_Y = "drone_rotation_base_y";
    private static final String LIFTED = "drone_rotation_lifted";
    private static final String LINES = "drone_rotation_lines";

    private DroneRotationJoinProbe() {
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        int start = (int) state.computeIfAbsent(START, key -> tick);
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return tick - start > TIMEOUT_TICKS ? "drone_rotation=fail no_player\n" : null;
        }
        String lines = (String) state.getOrDefault(LINES, "");
        int phase = (int) state.getOrDefault(PHASE, 0);
        int mark = (int) state.getOrDefault(MARK, start);
        if (phase == 0) {
            QuadcopterEntity before = find(mc, player, DroneRotationJoinCheck.BEFORE_NAME);
            if (before == null) {
                return tick - start > TIMEOUT_TICKS ? "drone_rotation=fail before_not_seen\n" : null;
            }
            float synced = QuadcopterEntity.yawDegrees(before.orientation());
            float render = QuadcopterEntity.yawDegrees(before.renderAttitude(1.0F, new Quaternionf()));
            String first = String.format(Locale.ROOT, "drone_rotation before_join yaw=%.1f render=%.1f\n", synced, render);
            if (off(synced, DroneRotationJoinCheck.FIRST_YAW) > EPS || off(render, DroneRotationJoinCheck.FIRST_YAW) > EPS) {
                return first + "drone_rotation=fail before_join\n";
            }
            next(state, 1, tick, lines + first);
            return null;
        }
        if (phase == 1) {
            QuadcopterEntity own = find(mc, player, DroneRotationJoinCheck.name(player.getPlainTextName()));
            if (own == null) {
                return tick - mark > TIMEOUT_TICKS ? lines + "drone_rotation=fail not_seen\n" : null;
            }
            state.put(DRONE, own.getId());
            next(state, 2, tick, lines + String.format(Locale.ROOT, "drone_rotation seen yaw=%.1f\n",
                    QuadcopterEntity.yawDegrees(own.orientation())));
            return null;
        }
        if (!(mc.level.getEntity((int) state.get(DRONE)) instanceof QuadcopterEntity drone)) {
            return lines + "drone_rotation=fail lost\n";
        }
        float renderYaw = QuadcopterEntity.yawDegrees(drone.renderAttitude(1.0F, new Quaternionf()));
        float syncedYaw = QuadcopterEntity.yawDegrees(drone.orientation());
        if (phase == 2 || phase == 3) {
            float target = phase == 2 ? DroneRotationJoinCheck.FIRST_YAW : DroneRotationJoinCheck.TURNED_YAW;
            String what = phase == 2 ? "tp" : "rotate";
            float renderOff = off(renderYaw, target);
            float last = (float) state.getOrDefault(LAST, Float.POSITIVE_INFINITY);
            if (renderOff > last + 0.01F) {
                return lines + String.format(Locale.ROOT, "drone_rotation=fail %s_jump %.2f->%.2f\n", what, last, renderOff);
            }
            state.put(LAST, renderOff);
            if (renderOff > EPS || off(syncedYaw, target) > EPS) {
                return tick - mark > TIMEOUT_TICKS ? lines + String.format(Locale.ROOT, "drone_rotation=fail %s yaw=%.1f render=%.1f\n", what,
                        syncedYaw, renderYaw) : null;
            }
            state.remove(LAST);
            state.put(BASE_Y, drone.getY());
            next(state, phase + 1, tick, lines + String.format(Locale.ROOT, "drone_rotation %s yaw=%.1f render=%.1f\n", what, syncedYaw,
                    renderYaw));
            return null;
        }
        if (off(renderYaw, DroneRotationJoinCheck.TURNED_YAW) > EPS || off(syncedYaw, DroneRotationJoinCheck.TURNED_YAW) > EPS) {
            return lines + String.format(Locale.ROOT, "drone_rotation=fail tp_turned yaw=%.1f render=%.1f\n", syncedYaw, renderYaw);
        }
        if (drone.getY() > (double) state.get(BASE_Y) + DroneRotationJoinCheck.LIFT * 0.5) {
            state.put(LIFTED, true);
        }
        if (tick - mark < HOLD_TICKS) {
            return null;
        }
        if (!state.containsKey(LIFTED)) {
            return lines + "drone_rotation=fail no_teleport\n";
        }
        return lines + String.format(Locale.ROOT, "drone_rotation tp_plain yaw=%.1f render=%.1f\ndrone_rotation=ok\n", syncedYaw, renderYaw);
    }

    private static void next(Map<String, Object> state, int phase, int tick, String lines) {
        state.put(PHASE, phase);
        state.put(MARK, tick);
        state.put(LINES, lines);
    }

    private static @Nullable QuadcopterEntity find(Minecraft mc, LocalPlayer player, String wanted) {
        if (mc.level == null) {
            return null;
        }
        List<QuadcopterEntity> drones = mc.level.getEntitiesOfClass(QuadcopterEntity.class, player.getBoundingBox().inflate(RADIUS),
                drone -> named(drone, wanted));
        return drones.isEmpty() ? null : drones.getFirst();
    }

    private static boolean named(QuadcopterEntity drone, String wanted) {
        Component name = drone.getCustomName();
        return name != null && wanted.equals(name.getString());
    }

    private static float off(float a, float b) {
        float d = (a - b) % 360.0F;
        if (d > 180.0F) {
            d -= 360.0F;
        } else if (d < -180.0F) {
            d += 360.0F;
        }
        return Math.abs(d);
    }
}
