package de.lardtom.propwash.race.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.ui.client.FirstUseHints;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public final class GateHintJoinProbe {
    public static final String ACTIVE = "gate_hint_active";
    public static final int TIMEOUT_TICKS = 20 * 60;
    private static final int AIM_TICKS = 40;
    private static final int GATE_TICKS = 20 * 10;
    private static final int WINDOW_TICKS = 60;
    private static final int QUIET_TICKS = 40;
    private static final String START = "gate_hint_start";
    private static final String PHASE = "gate_hint_phase";
    private static final String SINCE = "gate_hint_since";
    private static final String RESULT = "gate_hint_result";
    private static final String LINES = "gate_hint_lines";
    private static final String DETECTED = "gate_hint_detected";
    private static final String CLICK = "gate_hint_click";
    private static final String HOME = "gate_hint_home";
    private static final String HELD = "gate_hint_held";
    private static final int SETTLE_TICKS = 20;

    private GateHintJoinProbe() {
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        if (state.containsKey(RESULT)) {
            return (String) state.get(RESULT);
        }
        int start = (int) state.computeIfAbsent(START, key -> tick);
        LocalPlayer player = mc.player;
        if (player == null || !GatePlaceHint.placesGate(player.getMainHandItem())) {
            state.remove(HELD);
            return tick - start > TIMEOUT_TICKS ? "gate_hint=no_item\n" : null;
        }
        if (tick - (int) state.computeIfAbsent(HELD, key -> tick) < SETTLE_TICKS) {
            return null;
        }
        GatePlaceHint.mute(false);
        GatePlaceHint.worldOverride(null);
        FirstUseHints.forget(GateHintMemory.HINT);
        if (GateHintMemory.forgetWorlds(GatePlaceHint.worlds(), FirstUseHints.playerKey())) {
            GatePlaceHint.WORLDS.save();
        }
        state.put(HOME, player.blockPosition());
        state.put(LINES, "gate_hint_world=" + GatePlaceHint.world(mc) + "\n");
        state.put(ACTIVE, true);
        next(state, tick, 0);
        return null;
    }

    public static void whileActive(Map<String, Object> state, int tick, Minecraft mc) {
        LocalPlayer player = mc.player;
        BlockPos home = (BlockPos) state.get(HOME);
        if (player == null || home == null) {
            finish(state, "gate_hint=no_player\n");
            return;
        }
        int phase = (int) state.get(PHASE);
        int waited = tick - (int) state.get(SINCE);
        boolean second = phase >= 4;
        BlockPos target = home.offset(0, -1, second ? 3 : -3);
        switch (phase) {
            case 0, 4 -> {
                lookAt(player, Vec3.atCenterOf(target).add(0.0, 0.5, 0.0));
                next(state, tick, phase + 1);
            }
            case 1, 5 -> {
                lookAt(player, Vec3.atCenterOf(target).add(0.0, 0.5, 0.0));
                boolean aimed = mc.gui.screen() == null && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                        && hit.getBlockPos().equals(target) && hit.getDirection() == Direction.UP;
                if (!aimed) {
                    if (waited > AIM_TICKS) {
                        finish(state, (second ? "gate_hint_second=no_aim\n" : "gate_hint=no_aim\n"));
                    }
                    return;
                }
                state.put(DETECTED, GatePlaceHint.detected());
                state.put(CLICK, tick);
                KeyMapping.click(InputConstants.getKey(mc.options.keyUse.saveString()));
                next(state, tick, phase + 1);
            }
            case 2, 6 -> {
                if (GatePlaceHint.detected() <= (int) state.get(DETECTED)) {
                    if (waited > GATE_TICKS) {
                        finish(state, second ? "gate_hint_second=no_gate\n" : "gate_hint=no_gate\n");
                    }
                    return;
                }
                append(state, (second ? "gate_hint_second_delay=" : "gate_hint_delay=") + (tick - (int) state.get(CLICK)) + "\n");
                next(state, tick, phase + 1);
            }
            case 3 -> {
                if (mc.gui.screen() instanceof GateHintScreen hint) {
                    append(state, "gate_hint=shown\ngate_hint_window=" + (tick - (int) state.get(CLICK)) + "\n");
                    hint.confirm();
                    next(state, tick, 4);
                } else if (waited > WINDOW_TICKS) {
                    finish(state, "gate_hint=missing " + GatePlaceHint.lastOutcome() + "\n");
                }
            }
            default -> {
                if (mc.gui.screen() instanceof GateHintScreen) {
                    mc.gui.setScreen(null);
                    finish(state, "gate_hint_second=shown\n");
                } else if (waited > QUIET_TICKS) {
                    finish(state, "gate_hint_second=none " + GatePlaceHint.lastOutcome() + "\n");
                }
            }
        }
    }

    private static void next(Map<String, Object> state, int tick, int phase) {
        state.put(PHASE, phase);
        state.put(SINCE, tick);
    }

    private static void append(Map<String, Object> state, String line) {
        state.put(LINES, state.getOrDefault(LINES, "") + line);
    }

    private static void finish(Map<String, Object> state, String line) {
        append(state, line);
        state.put(RESULT, state.get(LINES));
        state.remove(ACTIVE);
        GatePlaceHint.worldOverride(null);
    }

    private static void lookAt(LocalPlayer player, Vec3 target) {
        Vec3 eye = player.getEyePosition();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.setYHeadRot(yaw);
    }
}
