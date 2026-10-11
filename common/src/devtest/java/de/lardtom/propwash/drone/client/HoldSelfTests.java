package de.lardtom.propwash.drone.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.input.DeviceProfile;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.input.SwitchAssignment;
import de.lardtom.propwash.input.SwitchEvaluator;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.client.FunctionKeys;
import de.lardtom.propwash.input.client.InputKeys;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.HoldMode;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import net.minecraft.client.KeyMapping;
import org.joml.Vector3d;

import java.util.Locale;

final class HoldSelfTests {
    private static final String STAGE = "hold.stage";
    private static final String SINCE = "hold.since";
    private static final String START = "hold.start";
    private static final String WORST = "hold.worst";
    private static final String THROTTLE = "hold.throttle";
    private static final String CALM = "hold.calm";
    private static final int SETTLE_TICKS = 200;
    private static final int CALM_TICKS = 10;
    private static final double CALM_SPEED = 0.5;
    private static final int WATCH_TICKS = 60;
    private static final int ENGAGE_TICKS = 40;
    private static final int GPS_TICKS = 400;

    private HoldSelfTests() {
    }

    static StepResult holdStep(SelfTest.StepContext ctx) {
        StepResult result = run(ctx);
        if (result.status() == StepResult.Status.FAIL) {
            PilotSession.INSTANCE.clearHoldRequests();
        }
        return result;
    }

    private static StepResult run(SelfTest.StepContext ctx) {
        PilotSession session = PilotSession.INSTANCE;
        DroneSim sim = session.sim();
        if (sim == null) {
            return StepResult.fail("keine aktive Sitzung");
        }
        Telemetry t = sim.telemetry();
        int tick = ctx.tick();
        int stage = (int) ctx.state().getOrDefault(STAGE, 0);
        int since = (int) ctx.state().getOrDefault(SINCE, 0);
        float throttle = (float) ctx.state().computeIfAbsent(THROTTLE,
                k -> (float) Math.clamp(t.hoverThrottleStick, 0.15, 0.6));
        float stick = stage == 6 ? (float) Math.clamp(throttle - 0.04 * t.verticalSpeed, 0.0, 1.0) : throttle;
        session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, stick), true, StabMode.ANGLE);
        Vector3d pos = session.comPosition(new Vector3d());
        switch (stage) {
            case 0 -> {
                String switches = checkSwitchAssignment();
                if (switches != null) {
                    return StepResult.fail(switches);
                }
                if (!t.armed) {
                    return StepResult.fail("Drohne nicht scharf");
                }
                next(ctx, 6, tick);
            }
            case 6 -> {
                int calm = Math.abs(t.verticalSpeed) < CALM_SPEED ? (int) ctx.state().getOrDefault(CALM, 0) + 1 : 0;
                ctx.state().put(CALM, calm);
                if (calm >= CALM_TICKS || tick - since > SETTLE_TICKS) {
                    ctx.log(String.format(Locale.ROOT, "vor ALTHOLD: Steigrate %.2f m/s, Gas %.2f", t.verticalSpeed, throttle));
                    click(InputKeys.ALT_HOLD);
                    next(ctx, 1, tick);
                }
            }
            case 1 -> {
                if (t.holdMode == HoldMode.ALTHOLD) {
                    ctx.state().put(START, new double[]{pos.x, pos.y, pos.z});
                    ctx.state().put(WORST, new double[1]);
                    next(ctx, 2, tick);
                } else if (tick - since > ENGAGE_TICKS) {
                    return StepResult.fail("ALTHOLD per Taste nicht aktiv: " + t.holdMode);
                }
            }
            case 2 -> {
                double[] start = (double[]) ctx.state().get(START);
                double[] worst = (double[]) ctx.state().get(WORST);
                worst[0] = Math.max(worst[0], Math.abs(pos.y - start[1]));
                if (t.holdMode != HoldMode.ALTHOLD) {
                    return StepResult.fail("ALTHOLD fiel ab: " + t.holdMode);
                }
                if (tick - since >= WATCH_TICKS) {
                    if (worst[0] > 3.0) {
                        return StepResult.fail(String.format(Locale.ROOT, "ALTHOLD hält die Höhe nicht: %.2f m", worst[0]));
                    }
                    ctx.log(String.format(Locale.ROOT, "ALTHOLD: größte Abweichung %.2f m, Satelliten %d", worst[0], t.gpsSatellites));
                    click(InputKeys.POS_HOLD);
                    next(ctx, 3, tick);
                }
            }
            case 3 -> {
                if (t.holdMode == HoldMode.POSHOLD) {
                    ctx.state().put(START, new double[]{pos.x, pos.y, pos.z});
                    ctx.state().put(WORST, new double[1]);
                    next(ctx, 4, tick);
                } else if (t.holdMode != HoldMode.ALTHOLD) {
                    return StepResult.fail("POSHOLD angefordert, aber " + t.holdMode);
                } else if (tick - since > GPS_TICKS) {
                    return StepResult.fail("POSHOLD ohne GPS-Fix unter freiem Himmel: " + t.gpsSatellites + " Satelliten");
                }
            }
            case 4 -> {
                double[] start = (double[]) ctx.state().get(START);
                double[] worst = (double[]) ctx.state().get(WORST);
                worst[0] = Math.max(worst[0], Math.hypot(pos.x - start[0], pos.z - start[2]));
                if (t.holdMode != HoldMode.POSHOLD) {
                    return StepResult.fail("POSHOLD fiel ab: " + t.holdMode + ", Satelliten " + t.gpsSatellites);
                }
                if (tick - since >= WATCH_TICKS) {
                    if (worst[0] > 6.0) {
                        return StepResult.fail(String.format(Locale.ROOT, "POSHOLD hält die Stelle nicht: %.2f m", worst[0]));
                    }
                    ctx.log(String.format(Locale.ROOT, "POSHOLD: größte Abweichung %.2f m, %d Satelliten", worst[0], t.gpsSatellites));
                    click(InputKeys.ALT_HOLD);
                    next(ctx, 5, tick);
                }
            }
            default -> {
                if (t.holdMode == HoldMode.OFF && !session.altHoldRequested() && !session.posHoldRequested()) {
                    return StepResult.pass();
                }
                if (tick - since > ENGAGE_TICKS) {
                    return StepResult.fail("Halten lässt sich nicht ausschalten: " + t.holdMode);
                }
            }
        }
        return StepResult.pending();
    }

    private static void next(SelfTest.StepContext ctx, int stage, int tick) {
        ctx.state().put(STAGE, stage);
        ctx.state().put(SINCE, tick);
    }

    private static void click(KeyMapping mapping) {
        KeyMapping.click(InputConstants.getKey(mapping.saveString()));
    }

    private static String checkSwitchAssignment() {
        for (SwitchFunction function : new SwitchFunction[]{SwitchFunction.ALT_HOLD, SwitchFunction.POS_HOLD}) {
            if (FunctionKeys.mapping(function) == null) {
                return "keine Taste für " + function;
            }
        }
        DeviceProfile profile = new DeviceProfile();
        profile.setBinding(SwitchFunction.ALT_HOLD, SwitchAssignment.axisMicros(5, 1300, 2100));
        profile.setBinding(SwitchFunction.POS_HOLD, SwitchAssignment.axisMicros(5, 1700, 2100));
        SwitchEvaluator evaluator = new SwitchEvaluator();
        int mid = evaluator.evaluate(profile, new float[]{0, 0, 0, 0, 0, 0.0F}, new boolean[0]);
        int high = evaluator.evaluate(profile, new float[]{0, 0, 0, 0, 0, 1.0F}, new boolean[0]);
        if (!SwitchEvaluator.isSet(mid, SwitchFunction.ALT_HOLD) || SwitchEvaluator.isSet(mid, SwitchFunction.POS_HOLD)
                || !SwitchEvaluator.isSet(high, SwitchFunction.POS_HOLD)) {
            return "Dreistufenschalter ALTHOLD/POSHOLD wertet falsch aus";
        }
        return null;
    }
}
