package de.lardtom.propwash.rx.client;

import de.lardtom.propwash.dev.RxJoinCheck;
import de.lardtom.propwash.input.ProfileDefaults;
import de.lardtom.propwash.input.client.SimulatedRadio;
import de.lardtom.propwash.input.client.TransmitterInput;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.rx.RxBlock;
import de.lardtom.propwash.rx.RxBlockEntity;
import de.lardtom.propwash.rx.RxConfigC2S;
import de.lardtom.propwash.rx.RxLinkState;
import de.lardtom.propwash.rx.RxMapping;
import de.lardtom.propwash.rx.RxSource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public final class RxJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 150;
    public static final int THROTTLE_AXIS = 2;
    private static final int AXES = 8;
    private static final String START = "rx_start";
    private static final String LINES = "rx_lines";
    private static final String STAGE = "rx_stage";
    private static final String SINCE = "rx_since";
    private static final String OWN = "rx_own";
    private static final String FOREIGN = "rx_foreign";
    private static final String RADIO = "rx_radio";

    private RxJoinProbe() {
    }

    private static void line(Map<String, Object> state, String text) {
        state.put(LINES, state.getOrDefault(LINES, "") + text + "\n");
    }

    private static void stage(Map<String, Object> state, int stage, int tick) {
        state.put(STAGE, stage);
        state.put(SINCE, tick);
    }

    private static @Nullable BlockPos find(Minecraft mc, boolean own) {
        if (mc.level == null || mc.player == null) {
            return null;
        }
        BlockPos center = mc.player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-5, -2, -5), center.offset(5, 2, 5))) {
            if (mc.level.getBlockEntity(pos) instanceof RxBlockEntity entity && RxSource.GAS.equals(entity.settings().source())) {
                boolean mine = entity.settings().ownedBy(mc.player.getUUID());
                if (mine == own && entity.settings().bound()) {
                    return pos.immutable();
                }
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

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        int start = (int) state.computeIfAbsent(START, key -> tick);
        int stage = (int) state.getOrDefault(STAGE, 0);
        int since = (int) state.getOrDefault(SINCE, tick);
        if (tick - start > TIMEOUT_TICKS) {
            line(state, "rx timeout at stage " + stage);
            return done(state);
        }
        SimulatedRadio radio = (SimulatedRadio) state.computeIfAbsent(RADIO, key -> {
            SimulatedRadio fake = new SimulatedRadio("propwash-rx-join", "Propwash-Testfunke (RX)", ProfileDefaults.radio("Propwash-Testfunke", AXES),
                    AXES, 0);
            for (int axis = 0; axis < AXES; axis++) {
                fake.setMicros(axis, axis == THROTTLE_AXIS || axis >= ProfileDefaults.AUX_ARM ? 1000 : 1500);
            }
            TransmitterInput.INSTANCE.simulate(fake);
            return fake;
        });
        switch (stage) {
            case 0 -> {
                BlockPos own = find(mc, true);
                BlockPos foreign = find(mc, false);
                if (own == null || foreign == null || RxClient.demand().empty()) {
                    return null;
                }
                state.put(OWN, own);
                state.put(FOREIGN, foreign);
                line(state, "rx_demand=" + RxClient.demand().describe());
                stage(state, 1, tick);
            }
            case 1 -> {
                BlockPos own = (BlockPos) state.get(OWN);
                if (power(mc, own) == 0 && link(mc, own) == RxLinkState.LINKED && tick - since > 20) {
                    radio.setMicros(THROTTLE_AXIS, 1500);
                    stage(state, 2, tick);
                } else if (tick - since > 400) {
                    line(state, "rx_follow=fail zero power " + power(mc, own) + " " + link(mc, own));
                    stage(state, 5, tick);
                }
            }
            case 2 -> {
                BlockPos own = (BlockPos) state.get(OWN);
                int power = power(mc, own);
                if (power == RxMapping.CENTRE && tick - since > 10) {
                    state.put("rx_half", power);
                    radio.setMicros(THROTTLE_AXIS, 2000);
                    stage(state, 3, tick);
                } else if (tick - since > 400) {
                    line(state, "rx_follow=fail half power " + power);
                    stage(state, 5, tick);
                }
            }
            case 3 -> {
                BlockPos own = (BlockPos) state.get(OWN);
                if (power(mc, own) == 15 && tick - since > 10) {
                    line(state, "rx_follow=ok (0, " + state.get("rx_half") + ", 15)");
                    ClientNet.sendToServer(RxConfigC2S.range(own, 1));
                    stage(state, 4, tick);
                } else if (tick - since > 400) {
                    line(state, "rx_follow=fail full power " + power(mc, own));
                    stage(state, 5, tick);
                }
            }
            case 4 -> {
                BlockPos own = (BlockPos) state.get(OWN);
                if (link(mc, own) == RxLinkState.LOST && !state.containsKey("rx_lost")) {
                    state.put("rx_lost", tick);
                    radio.setMicros(THROTTLE_AXIS, 1000);
                }
                if (state.containsKey("rx_lost") && tick - (int) state.get("rx_lost") > 40) {
                    boolean held = power(mc, own) == 15 && link(mc, own) == RxLinkState.LOST;
                    line(state, held ? "rx_hold=ok" : "rx_hold=fail power " + power(mc, own) + " " + link(mc, own));
                    stage(state, 5, tick);
                } else if (tick - since > 400) {
                    line(state, "rx_hold=fail no loss " + link(mc, own));
                    stage(state, 5, tick);
                }
            }
            case 5 -> {
                BlockPos foreign = (BlockPos) state.get(FOREIGN);
                if (!state.containsKey("rx_foreign_sent")) {
                    ClientNet.sendToServer(RxConfigC2S.source(foreign, RxSource.axis(6)));
                    ClientNet.sendToServer(RxConfigC2S.unbind(foreign));
                    state.put("rx_foreign_sent", tick);
                    return null;
                }
                if (tick - (int) state.get("rx_foreign_sent") < 40) {
                    return null;
                }
                RxBlockEntity entity = RxClient.blockEntity(foreign);
                boolean denied = entity != null && entity.settings().bound() && RxSource.GAS.equals(entity.settings().source()) && mc.player != null
                        && !entity.settings().ownedBy(mc.player.getUUID());
                line(state, denied ? "rx_foreign_denied=ok" : "rx_foreign_denied=fail " + (entity == null ? "kein Block" : entity.settings()));
                stage(state, 6, tick);
            }
            case 6 -> {
                BlockPos own = (BlockPos) state.get(OWN);
                BlockPos signal = own.west(RxJoinCheck.OWN_EAST + RxJoinCheck.SIGNAL_WEST);
                BlockState flag = state(mc, signal);
                if (flag.is(Blocks.EMERALD_BLOCK)) {
                    line(state, "rx_unloaded_untouched=ok");
                    return done(state);
                }
                if (flag.is(Blocks.REDSTONE_BLOCK)) {
                    line(state, "rx_unloaded_untouched=fail (Server-Log)");
                    return done(state);
                }
                if (tick - since > RxJoinCheck.TIMEOUT_TICKS + 200) {
                    line(state, "rx_unloaded_untouched=fail no answer");
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
        line(state, "rx_sent=" + RxClient.sentCount());
        return (String) state.get(LINES);
    }
}
