package de.lardtom.propwash.accessory.client;

import de.lardtom.propwash.dev.AccFixJoinCheck;
import de.lardtom.propwash.dev.AccFixSpeedRecorder;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.SimSnapshot;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AccFixJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 150;
    private static final String START = "accfix_start";
    private static final String PROBE = "accfix_probe";
    private static final double HOVER = 2.0;
    private static final double HOLD_GAIN = 0.15;
    private static final double HOLD_DAMPING = 0.08;
    private static final int ARM_DELAY = 3;
    private static final int REARM_TICKS = 20;
    private static final int PRESS_EVERY = 7;
    private static final int FLIGHT_LIMIT_TICKS = 20 * 30;

    private AccFixJoinProbe() {
    }

    private static final class Probe {
        int stage;
        int mark;
        double floor = Double.NaN;
        int pushed;
        int presses;
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        int start = (int) state.computeIfAbsent(START, key -> tick);
        Probe p = (Probe) state.computeIfAbsent(PROBE, key -> new Probe());
        String server = serverLine(mc);
        if (server != null) {
            finish();
            return server + String.format(Locale.ROOT, "accfix client pushed %d ticks, %d presses%n", p.pushed, p.presses);
        }
        if (tick - start > TIMEOUT_TICKS) {
            finish();
            return "accfix speed=timeout stage " + p.stage + "\n";
        }
        PilotSession session = PilotSession.INSTANCE;
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
                p.stage = 1;
                p.mark = tick;
            }
            case 1 -> {
                boolean resting = t.grounded || t.sleeping;
                input(0.0F, t.armed || resting && waited >= ARM_DELAY && waited % REARM_TICKS != 0);
                if (t.armed) {
                    p.stage = 2;
                    p.mark = tick;
                }
            }
            case 2 -> {
                input((float) Math.min(1.0, t.hoverThrottleStick + 0.15), true);
                if (drone.getY() > p.floor + HOVER) {
                    p.stage = 3;
                    p.mark = tick;
                }
            }
            case 3 -> {
                double error = p.floor + HOVER - drone.getY();
                input((float) Math.max(0.0, Math.min(1.0, t.hoverThrottleStick + HOLD_GAIN * error - HOLD_DAMPING * t.verticalSpeed)), true);
                push(sim, drone);
                p.pushed++;
                boolean press = waited > 10 && waited % PRESS_EVERY == 0;
                AccessoryActionInput.INSTANCE.setTestBits(press ? 1 : 0);
                if (press) {
                    p.presses++;
                }
                if (waited > FLIGHT_LIMIT_TICKS) {
                    finish();
                    return "accfix speed=missing server result after " + waited + " flight ticks\n";
                }
            }
            default -> {
            }
        }
        return null;
    }

    private static void finish() {
        AccessoryActionInput.INSTANCE.setTestBits(null);
        PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), false, StabMode.ANGLE);
    }

    private static void input(float throttle, boolean arm) {
        PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), arm, StabMode.ANGLE);
    }

    private static void push(DroneSim sim, QuadcopterEntity drone) {
        Vector3f forward = new Quaternionf(drone.orientation()).transform(new Vector3f(0.0F, 0.0F, -1.0F));
        double length = Math.hypot(forward.x, forward.z);
        if (length < 1.0E-6) {
            return;
        }
        SimSnapshot snapshot = new SimSnapshot();
        sim.copyStateTo(snapshot);
        snapshot.vx = forward.x / length * AccFixSpeedRecorder.SPEED;
        snapshot.vz = forward.z / length * AccFixSpeedRecorder.SPEED;
        snapshot.sleeping = false;
        sim.copyStateFrom(snapshot);
    }

    private static @Nullable String serverLine(Minecraft mc) {
        try {
            Field field = ChatComponent.class.getDeclaredField("allMessages");
            field.setAccessible(true);
            List<?> messages = (List<?>) field.get(mc.gui.hud.getChat());
            for (Object message : new ArrayList<>(messages)) {
                if (message instanceof GuiMessage gui) {
                    String text = gui.content().getString();
                    if (text.startsWith(AccFixJoinCheck.PREFIX)) {
                        return text + "\n";
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
        return null;
    }
}
