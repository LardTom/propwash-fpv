package de.lardtom.propwash.dev.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.accessory.AccessoryMenu;
import de.lardtom.propwash.accessory.client.AccessoryScreen;
import de.lardtom.propwash.dev.MenusJoinCheck;
import de.lardtom.propwash.drone.DroneServiceMenu;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.DroneServiceScreen;
import de.lardtom.propwash.race.client.RaceDataChat;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;

public final class MenusJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 150;
    public static final String OPEN = "menus_open";
    public static final String KEY = "menus";
    private static final int OPEN_WAIT_TICKS = 20 * 10;
    private static final int CLICK_EVERY = 40;
    private static final int ENTITY_WAIT_TICKS = 20 * 10;
    private static final String START = "menus_start";
    private static final String LINES = "menus_lines";
    private static final String STAGE = "menus_stage";
    private static final String QUEUE = "menus_queue";
    private static final String DRONE = "menus_drone";
    private static final String SENT = "menus_sent";
    private static final String FAILED = "menus_failed";
    private static final String COUNT = "menus_count";

    private MenusJoinProbe() {
    }

    private static void line(Map<String, Object> state, String text) {
        state.put(LINES, state.getOrDefault(LINES, "") + text + "\n");
    }

    private static void fail(Map<String, Object> state, String what) {
        state.put(FAILED, state.getOrDefault(FAILED, "") + what + " ");
    }

    private static String done(Map<String, Object> state) {
        state.put(STAGE, 99);
        Object failed = state.get(FAILED);
        line(state, failed == null ? "menus=ok" : "menus=fail " + failed);
        return (String) state.get(LINES);
    }

    @SuppressWarnings("unchecked")
    private static Deque<Integer> queue(Map<String, Object> state) {
        return (Deque<Integer>) state.computeIfAbsent(QUEUE, key -> new ArrayDeque<Integer>());
    }

    private static @Nullable QuadcopterEntity drone(Minecraft mc, int id) {
        return mc.level != null && mc.level.getEntity(id) instanceof QuadcopterEntity drone && !drone.isRemoved() ? drone : null;
    }

    private static String screenName(Minecraft mc) {
        Screen screen = mc.gui.screen();
        return screen == null ? "keins" : screen.getClass().getSimpleName();
    }

    private static void aim(LocalPlayer player, Vec3 target) {
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

    private static boolean aimed(Minecraft mc, QuadcopterEntity drone) {
        return mc.hitResult instanceof EntityHitResult hit && hit.getEntity() == drone;
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        int start = (int) state.computeIfAbsent(START, key -> tick);
        int stage = (int) state.getOrDefault(STAGE, 0);
        if (stage == 99) {
            return (String) state.get(LINES);
        }
        if (tick - start > TIMEOUT_TICKS) {
            fail(state, "timeout@" + stage);
            return done(state);
        }
        if (mc.player == null || mc.gameMode == null) {
            return null;
        }
        switch (stage) {
            case 0 -> {
                Optional<String> server = RaceDataChat.line(mc, MenusJoinCheck.RESULT);
                if (server.isEmpty()) {
                    return null;
                }
                String[] parts = server.get().substring(MenusJoinCheck.RESULT.length()).trim().split(" ");
                if (parts.length == 0 || !parts[0].matches("\\d+")) {
                    line(state, "menus server " + server.get());
                    fail(state, "server");
                    return done(state);
                }
                Deque<Integer> queue = queue(state);
                Arrays.stream(parts).skip(1).map(Integer::parseInt).forEach(queue::addLast);
                state.put(COUNT, queue.size());
                line(state, "menus drones=" + queue.size());
                state.put(STAGE, 1);
                state.put(SENT, tick);
                return null;
            }
            case 1 -> {
                mc.options.keyShift.setDown(false);
                Deque<Integer> queue = queue(state);
                if (queue.isEmpty()) {
                    return done(state);
                }
                int id = queue.peekFirst();
                QuadcopterEntity drone = drone(mc, id);
                if (drone == null) {
                    if (tick - (int) state.get(SENT) > ENTITY_WAIT_TICKS) {
                        line(state, "menus drone " + id + " missing");
                        fail(state, "missing:" + id);
                        queue.removeFirst();
                        state.put(SENT, tick);
                    }
                    return null;
                }
                queue.removeFirst();
                state.put(DRONE, id);
                state.put(SENT, tick);
                state.put(OPEN, Boolean.TRUE);
                state.put(STAGE, 2);
                return null;
            }
            default -> {
                return state.containsKey(OPEN) ? null : done(state);
            }
        }
    }

    public static void whileOpen(Map<String, Object> state, int tick, Minecraft mc) {
        int start = (int) state.computeIfAbsent(START, key -> tick);
        int stage = (int) state.getOrDefault(STAGE, 0);
        LocalPlayer player = mc.player;
        int id = (int) state.getOrDefault(DRONE, -1);
        QuadcopterEntity drone = drone(mc, id);
        if (tick - start > TIMEOUT_TICKS || player == null || mc.gameMode == null) {
            fail(state, "timeout@" + stage);
            close(state, mc, 1, tick);
            return;
        }
        int waited = tick - (int) state.get(SENT);
        String label = "menus drone " + (drone == null ? String.valueOf(id) : drone.build().frame().id() + drone.build().accessories().byMount().values().stream()
                .map(type -> "+" + type.id()).reduce("", String::concat));
        switch (stage) {
            case 2 -> {
                if (mc.gui.screen() instanceof DroneServiceScreen screen && screen.getMenu().droneId() == id) {
                    mc.options.keyShift.setDown(false);
                    int[] sizes = drone == null ? new int[0] : AccessoryMenu.sizes(drone);
                    boolean slots = Arrays.stream(sizes).sum() > 0;
                    line(state, label + " service=" + screen.getClass().getSimpleName() + (slots ? "" : " accessory=-"));
                    if (!slots) {
                        close(state, mc, 1, tick);
                        return;
                    }
                    mc.gameMode.handleInventoryButtonClick(screen.getMenu().containerId, DroneServiceMenu.ACCESSORY_BUTTON);
                    state.put(SENT, tick);
                    state.put(STAGE, 3);
                    return;
                }
                if (drone == null || waited > OPEN_WAIT_TICKS) {
                    line(state, label + " service=fail (Fenster " + screenName(mc) + ")");
                    fail(state, "service:" + id);
                    close(state, mc, 1, tick);
                    return;
                }
                if (mc.gui.screen() == null) {
                    mc.options.keyShift.setDown(true);
                    aim(player, drone.getBoundingBox().getCenter());
                    if (waited % CLICK_EVERY == 10 && aimed(mc, drone) && player.isShiftKeyDown()) {
                        KeyMapping.click(InputConstants.getKey(mc.options.keyUse.saveString()));
                    }
                }
            }
            case 3 -> {
                if (mc.gui.screen() instanceof AccessoryScreen screen && screen.getMenu().droneId() == id) {
                    line(state, label + " accessory=" + screen.getClass().getSimpleName() + " slots=" + (screen.getMenu().slots.size() - 36));
                    close(state, mc, 1, tick);
                    return;
                }
                if (waited > OPEN_WAIT_TICKS) {
                    line(state, label + " accessory=fail (Fenster " + screenName(mc) + ")");
                    fail(state, "accessory:" + id);
                    close(state, mc, 1, tick);
                }
            }
            default -> close(state, mc, 1, tick);
        }
    }

    private static void close(Map<String, Object> state, Minecraft mc, int stage, int tick) {
        mc.options.keyShift.setDown(false);
        if (mc.player != null && mc.gui.screen() != null) {
            mc.player.closeContainer();
        }
        state.remove(OPEN);
        state.put(STAGE, stage);
        state.put(SENT, tick);
    }
}
