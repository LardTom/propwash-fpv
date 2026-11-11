package de.lardtom.propwash.rx.client;

import de.lardtom.propwash.dev.CovRxJoinCheck;
import de.lardtom.propwash.dev.JoinTestEnv;
import de.lardtom.propwash.input.ProfileDefaults;
import de.lardtom.propwash.input.client.SimulatedRadio;
import de.lardtom.propwash.input.client.TransmitterInput;
import de.lardtom.propwash.rx.RxBlock;
import de.lardtom.propwash.rx.RxBlockEntity;
import de.lardtom.propwash.rx.RxLinkState;
import de.lardtom.propwash.rx.RxSource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public final class CovRxJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 120;
    public static final int THROTTLE_AXIS = 2;
    public static final int TEN_MICROS = 1667;
    private static final int AXES = 8;
    private static final String START = "cov_rx_start";
    private static final String LINES = "cov_rx_lines";
    private static final String STAGE = "cov_rx_stage";
    private static final String SINCE = "cov_rx_since";
    private static final String OWN = "cov_rx_own";
    private static final String RADIO = "cov_rx_radio";
    private static final String STABLE = "cov_rx_stable";

    private CovRxJoinProbe() {
    }

    private static void line(Map<String, Object> state, String text) {
        state.put(LINES, state.getOrDefault(LINES, "") + text + "\n");
    }

    private static void stage(Map<String, Object> state, int stage, int tick) {
        state.put(STAGE, stage);
        state.put(SINCE, tick);
        state.remove(STABLE);
    }

    private static @Nullable BlockPos own(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return null;
        }
        BlockPos center = mc.player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-5, -2, -5), center.offset(5, 2, 5))) {
            if (mc.level.getBlockEntity(pos) instanceof RxBlockEntity entity && RxSource.GAS.equals(entity.settings().source())
                    && entity.settings().ownedBy(mc.player.getUUID())) {
                return pos.immutable();
            }
        }
        return null;
    }

    private static BlockState state(Minecraft mc, BlockPos pos) {
        return mc.level == null ? Blocks.AIR.defaultBlockState() : mc.level.getBlockState(pos);
    }

    private static int power(Minecraft mc, BlockPos pos) {
        BlockState state = state(mc, pos);
        return state.getBlock() instanceof RxBlock ? state.getValue(RxBlock.POWER) : -1;
    }

    private static @Nullable RxLinkState link(Minecraft mc, BlockPos pos) {
        BlockState state = state(mc, pos);
        return state.getBlock() instanceof RxBlock ? state.getValue(RxBlock.LINK) : null;
    }

    private static boolean settled(Map<String, Object> state, int tick, boolean ok) {
        if (!ok) {
            state.remove(STABLE);
            return false;
        }
        int since = (int) state.computeIfAbsent(STABLE, key -> tick);
        return tick - since >= 20;
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        int mode = JoinTestEnv.covRx();
        int start = (int) state.computeIfAbsent(START, key -> tick);
        int stage = (int) state.getOrDefault(STAGE, 0);
        int since = (int) state.getOrDefault(SINCE, tick);
        if (tick - start > TIMEOUT_TICKS) {
            line(state, "cov_rx timeout at stage " + stage + " (mode " + mode + ")");
            return done(state);
        }
        SimulatedRadio radio = (SimulatedRadio) state.computeIfAbsent(RADIO, key -> {
            SimulatedRadio fake = new SimulatedRadio("propwash-cov-rx-join", "Propwash-Testfunke (RX-Abdeckung)",
                    ProfileDefaults.radio("Propwash-Testfunke", AXES), AXES, 0);
            for (int axis = 0; axis < AXES; axis++) {
                fake.setMicros(axis, axis == THROTTLE_AXIS || axis >= ProfileDefaults.AUX_ARM ? 1000 : 1500);
            }
            if (mode >= 2) {
                fake.setMicros(THROTTLE_AXIS, TEN_MICROS);
            }
            TransmitterInput.INSTANCE.simulate(fake);
            return fake;
        });
        switch (stage) {
            case 0 -> {
                BlockPos own = own(mc);
                if (own == null || RxClient.demand().empty()) {
                    return null;
                }
                state.put(OWN, own);
                line(state, "cov_rx_mode=" + mode);
                stage(state, mode >= 2 ? 10 : 1, tick);
            }
            case 1 -> {
                BlockPos own = (BlockPos) state.get(OWN);
                radio.setMicros(THROTTLE_AXIS, TEN_MICROS);
                if (settled(state, tick, power(mc, own) == CovRxJoinCheck.HELD_POWER && link(mc, own) == RxLinkState.LINKED)) {
                    line(state, "cov_rx_set=" + power(mc, own));
                    return done(state);
                }
                if (tick - since > 600) {
                    line(state, "cov_rx_set=fail " + power(mc, own) + " " + link(mc, own));
                    return done(state);
                }
            }
            case 10 -> {
                BlockPos own = (BlockPos) state.get(OWN);
                BlockState flag = state(mc, own.west(CovRxJoinCheck.OWN_EAST + CovRxJoinCheck.SIGNAL_WEST));
                if (flag.is(Blocks.EMERALD_BLOCK) || flag.is(Blocks.REDSTONE_BLOCK)) {
                    line(state, flag.is(Blocks.EMERALD_BLOCK) ? "cov_rx_absent_held=ok" : "cov_rx_absent_held=fail (Server-Log)");
                    stage(state, 11, tick);
                } else if (tick - since > 400) {
                    line(state, "cov_rx_absent_held=fail no answer");
                    stage(state, 11, tick);
                }
            }
            case 11 -> {
                BlockPos own = (BlockPos) state.get(OWN);
                radio.setMicros(THROTTLE_AXIS, TEN_MICROS);
                if (settled(state, tick, power(mc, own) == CovRxJoinCheck.HELD_POWER && link(mc, own) == RxLinkState.LINKED)) {
                    line(state, "cov_rx_rejoin_linked=ok (" + power(mc, own) + ")");
                    stage(state, 12, tick);
                } else if (tick - since > 600) {
                    line(state, "cov_rx_rejoin_linked=fail " + power(mc, own) + " " + link(mc, own));
                    return done(state);
                }
            }
            case 12 -> {
                BlockPos own = (BlockPos) state.get(OWN);
                radio.setMicros(THROTTLE_AXIS, 2000);
                if (settled(state, tick, power(mc, own) == 15 && link(mc, own) == RxLinkState.LINKED)) {
                    stage(state, 13, tick);
                } else if (tick - since > 400) {
                    line(state, "cov_rx_follow_again=fail full " + power(mc, own) + " " + link(mc, own));
                    return done(state);
                }
            }
            case 13 -> {
                BlockPos own = (BlockPos) state.get(OWN);
                radio.setMicros(THROTTLE_AXIS, 1000);
                if (settled(state, tick, power(mc, own) == 0 && link(mc, own) == RxLinkState.LINKED)) {
                    line(state, "cov_rx_follow_again=ok (15, 0)");
                    return done(state);
                } else if (tick - since > 400) {
                    line(state, "cov_rx_follow_again=fail zero " + power(mc, own) + " " + link(mc, own));
                    return done(state);
                }
            }
            default -> {
                return done(state);
            }
        }
        return null;
    }

    private static String done(Map<String, Object> state) {
        TransmitterInput.INSTANCE.simulate(null);
        line(state, "cov_rx_sent=" + RxClient.sentCount());
        return (String) state.get(LINES);
    }
}
