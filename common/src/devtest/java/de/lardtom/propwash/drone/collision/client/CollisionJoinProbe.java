package de.lardtom.propwash.drone.collision.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.dev.CollisionJoinCheck;
import de.lardtom.propwash.dev.example.ExampleGhost;
import de.lardtom.propwash.dev.example.client.ExampleSwitchesClient;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.drone.collision.ServerCollision;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.SimSnapshot;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;

public final class CollisionJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 150;
    private static final String START = "collision_start";
    private static final String PROBE = "collision_probe";
    private static final double SPEED = 6.0;
    private static final int PUSH_DELAY = 10;
    private static final int HOLD_TICKS = 60;
    private static final int OFF_TICKS = 20;
    private static final int OFF_TIMEOUT_TICKS = 100;

    private CollisionJoinProbe() {
    }

    private static final class Probe {
        int stage;
        int mark;
        int epoch = Integer.MIN_VALUE;
        int pushedEpoch = Integer.MIN_VALUE;
        int resets;
        int pushes;
        boolean denied;
        boolean passed;
        int passedAt;
        int passedEpoch;
        double wallNear = Double.NaN;
        double wallFar = Double.NaN;
        double floor = Double.NaN;
        boolean forcedSeen;
        boolean switchSeen;
        boolean switchAfter;
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        int start = (int) state.computeIfAbsent(START, key -> tick);
        Probe p = (Probe) state.computeIfAbsent(PROBE, key -> new Probe());
        PilotSession session = PilotSession.INSTANCE;
        if (tick - start > TIMEOUT_TICKS) {
            unbind();
            return report(p, "timeout at stage " + p.stage);
        }
        if (overlay().equals(Component.translatable(ServerCollision.DENIED_KEY).getString())) {
            p.denied = true;
        }
        QuadcopterEntity drone = session.drone();
        DroneSim sim = session.sim();
        if (drone == null || sim == null || !session.isFpv()) {
            return null;
        }
        Telemetry t = sim.telemetry();
        int waited = tick - p.mark;
        switch (p.stage) {
            case 0 -> {
                p.floor = drone.getY();
                p.epoch = drone.resetEpoch();
                p.stage = 1;
                p.mark = tick;
            }
            case 1 -> {
                input(0.0F, RigArming.armSwitch(waited, t.armed, t.grounded || t.sleeping));
                if (t.armed) {
                    p.stage = 2;
                    p.mark = tick;
                }
            }
            case 2 -> {
                input((float) t.hoverThrottleStick, true);
                if (drone.resetEpoch() != p.epoch && drone.getY() > p.floor + CollisionJoinCheck.HOVER - 0.5) {
                    double x0 = Math.floor(drone.getX() - CollisionJoinCheck.START_DX + 1.0E-3);
                    p.wallNear = x0 + CollisionJoinCheck.WALL_DX;
                    p.wallFar = p.wallNear + CollisionJoinCheck.WALL_THICK;
                    p.epoch = drone.resetEpoch();
                    if (!toggleGhost(true)) {
                        return report(p, "no ghost key");
                    }
                    p.stage = 3;
                    p.mark = tick;
                }
            }
            case 3 -> {
                input((float) t.hoverThrottleStick, true);
                p.forcedSeen |= ExampleGhost.forced();
                p.switchSeen |= PropwashClientApi.input().switchActive(ExampleGhost.GHOST_SWITCH);
                int epoch = drone.resetEpoch();
                if (epoch != p.epoch) {
                    p.epoch = epoch;
                    p.resets++;
                    p.mark = tick;
                    p.passed = false;
                    return null;
                }
                double half = drone.getBbWidth() / 2.0;
                if (!p.passed && drone.getX() - half > p.wallFar + 0.3) {
                    p.passed = true;
                    p.passedAt = tick;
                    p.passedEpoch = epoch;
                    push(sim, 0.0);
                }
                if (p.passed) {
                    if (tick - p.passedAt >= HOLD_TICKS && p.passedEpoch == epoch) {
                        toggleGhost(false);
                        input(0.0F, false);
                        p.stage = 4;
                        p.mark = tick;
                    }
                    return null;
                }
                if (p.pushedEpoch != epoch && waited >= PUSH_DELAY) {
                    p.pushedEpoch = epoch;
                    p.pushes++;
                    push(sim, SPEED);
                }
            }
            case 4 -> {
                input(0.0F, false);
                p.switchAfter = PropwashClientApi.input().switchActive(ExampleGhost.GHOST_SWITCH);
                if (waited >= OFF_TICKS && !p.switchAfter || waited > OFF_TIMEOUT_TICKS) {
                    unbind();
                    return report(p, "");
                }
            }
            default -> {
            }
        }
        return null;
    }

    private static String report(Probe p, String problem) {
        StringBuilder out = new StringBuilder();
        if (!problem.isEmpty()) {
            out.append("collision ").append(problem).append('\n');
        }
        out.append(p.forcedSeen ? "collision ghost forced\n" : p.switchSeen ? "collision ghost switch\n" : "collision ghost off\n");
        out.append(p.denied ? "collision denied seen\n" : "collision denied missing\n");
        out.append(p.passed ? "collision passed\n" : "collision blocked\n");
        out.append(p.switchAfter ? "collision switch after on\n" : "collision switch after off\n");
        out.append(String.format(Locale.ROOT, "collision resets %d pushes %d wall %.0f-%.0f%n", p.resets, p.pushes, p.wallNear, p.wallFar));
        return out.toString();
    }

    private static boolean toggleGhost(boolean bind) {
        KeyMapping ghost = ExampleSwitchesClient.ghostKey();
        if (ghost == null) {
            return false;
        }
        InputConstants.Key key = InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_G);
        if (bind) {
            ghost.setKey(key);
            KeyMapping.resetMapping();
        }
        KeyMapping.click(key);
        return true;
    }

    private static void unbind() {
        KeyMapping ghost = ExampleSwitchesClient.ghostKey();
        if (ghost != null && !ghost.isUnbound()) {
            ghost.setKey(InputConstants.UNKNOWN);
            KeyMapping.resetMapping();
        }
    }

    private static void input(float throttle, boolean arm) {
        PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), arm, StabMode.ANGLE);
    }

    private static void push(DroneSim sim, double vx) {
        SimSnapshot snapshot = new SimSnapshot();
        sim.copyStateTo(snapshot);
        snapshot.vx = vx;
        snapshot.vy = 0.0;
        snapshot.vz = 0.0;
        snapshot.sleeping = false;
        sim.copyStateFrom(snapshot);
    }

    private static String overlay() {
        try {
            Field field = Hud.class.getDeclaredField("overlayMessageString");
            field.setAccessible(true);
            Object message = field.get(Minecraft.getInstance().gui.hud);
            return message instanceof Component component ? component.getString() : "";
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "";
        }
    }
}
