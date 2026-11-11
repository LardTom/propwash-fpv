package de.lardtom.propwash.drone.collision.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.dev.CovNoAddonJoinCheck;
import de.lardtom.propwash.dev.example.ExampleGhost;
import de.lardtom.propwash.dev.example.ExampleSwitches;
import de.lardtom.propwash.dev.example.client.ExampleSwitchesClient;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.input.addon.AddonInputs;
import de.lardtom.propwash.input.addon.client.AddonServerFeatures;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.SimSnapshot;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

public final class CovNoAddonJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 150;
    private static final String START = "cov_noaddon_start";
    private static final String PROBE = "cov_noaddon_probe";
    private static final double SPEED = 6.0;
    private static final int PUSH_TICKS = 80;
    private static final int SETTLE_TICKS = 40;

    private CovNoAddonJoinProbe() {
    }

    private static final class Probe {
        int stage;
        int mark;
        int epoch = Integer.MIN_VALUE;
        int resets;
        boolean passed;
        double furthest = Double.NEGATIVE_INFINITY;
        double wallNear = Double.NaN;
        double wallFar = Double.NaN;
        double floor = Double.NaN;
        String switchState = "-";
        final StringBuilder out = new StringBuilder();
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        int start = (int) state.computeIfAbsent(START, key -> tick);
        Probe p = (Probe) state.computeIfAbsent(PROBE, key -> new Probe());
        if (tick - start > TIMEOUT_TICKS) {
            ExampleGhost.force(false);
            return p.out + "collision timeout at stage " + p.stage + "\n";
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
                if (drone.resetEpoch() != p.epoch && drone.getY() > p.floor + CovNoAddonJoinCheck.HOVER - 0.5) {
                    double x0 = Math.floor(drone.getX() - CovNoAddonJoinCheck.START_DX + 1.0E-3);
                    p.wallNear = x0 + CovNoAddonJoinCheck.WALL_DX;
                    p.wallFar = p.wallNear + CovNoAddonJoinCheck.WALL_THICK;
                    p.epoch = drone.resetEpoch();
                    KeyMapping ghost = ExampleSwitchesClient.ghostKey();
                    if (ghost != null) {
                        ghost.setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_G));
                        KeyMapping.resetMapping();
                        KeyMapping.click(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_G));
                    }
                    p.stage = 3;
                    p.mark = tick;
                }
            }
            case 3 -> {
                input((float) t.hoverThrottleStick, true);
                if (waited < 10) {
                    return null;
                }
                boolean active = PropwashClientApi.input().switchActive(ExampleSwitches.GHOST);
                boolean known = AddonServerFeatures.knowsSwitch(ExampleSwitches.GHOST);
                p.switchState = active ? "active" : "inactive";
                p.out.append("ghost_switch=").append(p.switchState).append('\n').append("ghost_switch_server=").append(known ? "known" : "unknown").append('\n')
                        .append("collision_rule_server=").append(AddonServerFeatures.knowsCollisionRule(ExampleGhost.GHOST) ? "known" : "unknown")
                        .append('\n').append("addon_switches_local=").append(AddonInputs.switches().size()).append('\n');
                begin(p, drone, tick);
            }
            case 4, 6 -> {
                input((float) t.hoverThrottleStick, true);
                if (fly(p, drone, sim, tick)) {
                    String suffix = p.stage == 4 ? "" : "_forced";
                    p.out.append("collision").append(suffix).append('=').append(p.passed ? "passed" : "solid").append('\n')
                            .append("resets").append(suffix).append('=').append(p.resets).append('\n')
                            .append(String.format(Locale.ROOT, "collision%s_detail furthest %.2f wall %.0f-%.0f%n", suffix, p.furthest, p.wallNear, p.wallFar));
                    if (p.stage == 4) {
                        KeyMapping.click(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_G));
                        ExampleGhost.force(true);
                        p.stage = 5;
                        p.mark = tick;
                    } else {
                        ExampleGhost.force(false);
                        KeyMapping ghost = ExampleSwitchesClient.ghostKey();
                        if (ghost != null) {
                            ghost.setKey(InputConstants.UNKNOWN);
                            KeyMapping.resetMapping();
                        }
                        input(0.0F, false);
                        return p.out.toString();
                    }
                }
            }
            case 5 -> {
                input((float) t.hoverThrottleStick, true);
                push(sim, -SPEED);
                if (drone.getX() + drone.getBbWidth() / 2.0 < p.wallNear - 1.5 || waited > 60) {
                    push(sim, 0.0);
                    begin(p, drone, tick);
                    p.stage = 6;
                }
            }
            default -> {
            }
        }
        return null;
    }

    private static void begin(Probe p, QuadcopterEntity drone, int tick) {
        p.stage = 4;
        p.mark = tick;
        p.epoch = drone.resetEpoch();
        p.resets = 0;
        p.passed = false;
        p.furthest = Double.NEGATIVE_INFINITY;
    }

    private static boolean fly(Probe p, QuadcopterEntity drone, DroneSim sim, int tick) {
        int epoch = drone.resetEpoch();
        if (epoch != p.epoch) {
            p.epoch = epoch;
            p.resets++;
        }
        p.furthest = Math.max(p.furthest, drone.getX());
        double half = drone.getBbWidth() / 2.0;
        if (drone.getX() - half > p.wallFar + 0.3) {
            p.passed = true;
        }
        int waited = tick - p.mark;
        if (waited < PUSH_TICKS && !p.passed) {
            push(sim, SPEED);
            return false;
        }
        push(sim, 0.0);
        return waited >= PUSH_TICKS + SETTLE_TICKS || p.passed && waited >= SETTLE_TICKS;
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
}
