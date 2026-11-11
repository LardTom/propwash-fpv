package de.lardtom.propwash.video.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.dev.CovWatchRescueJoinCheck;
import de.lardtom.propwash.video.net.ChannelListS2C;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

public final class CovWatchRescueJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 300;
    private static final String KEY = "cov_watchrescue";
    private static final int MIN_WATCH_TICKS = 100;
    private static final double MIN_WATCH_MOVED = 20.0;
    private static final int GOGGLES_WAIT = 20 * 150;
    private static final int CHANNEL_WAIT = 20 * 90;
    private static final int SCAN = 24;

    private CovWatchRescueJoinProbe() {
    }

    private enum Phase { GOGGLES, CHANNEL, WATCH, RESCUE }

    private static final class Run {
        Phase phase = Phase.GOGGLES;
        int since;
        int start;
        int watchTicks;
        double moved;
        @Nullable Vec3 lastCamera;
        @Nullable String pilotName;
        @Nullable BlockPos signal;
        int rescueTicks;
        int rescueWatched;
        double rescueMoved;
        @Nullable String failure;

        void enter(Phase next, int tick) {
            this.phase = next;
            this.since = tick;
            Constants.LOG.info("[Beitrittstest] Zuschauen mit GPS Rescue (Client): {}", next);
        }
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        Run run = (Run) state.computeIfAbsent(KEY, k -> {
            Run fresh = new Run();
            fresh.since = tick;
            fresh.start = tick;
            return fresh;
        });
        if (tick - run.start > TIMEOUT_TICKS) {
            run.failure = "timeout in " + run.phase;
            return finish(run);
        }
        SpectateSession session = SpectateSession.INSTANCE;
        int in = tick - run.since;
        if (run.signal == null && tick % 20 == 0) {
            run.signal = findSignal(mc);
        }
        switch (run.phase) {
            case GOGGLES -> {
                if (SpectateSession.goggles(mc) == VideoSystem.ANALOG) {
                    run.enter(Phase.CHANNEL, tick);
                } else if (in > GOGGLES_WAIT) {
                    run.failure = "no goggles";
                    return finish(run);
                }
            }
            case CHANNEL -> {
                ChannelListS2C.Entry entry = null;
                for (ChannelListS2C.Entry candidate : session.watchable(mc)) {
                    if (candidate.pilotName().equals(CovWatchRescueJoinCheck.PILOT)) {
                        entry = candidate;
                    }
                }
                if (session.watching() && session.targetEntry() != null && CovWatchRescueJoinCheck.PILOT.equals(session.targetEntry().pilotName())) {
                    run.pilotName = session.targetEntry().pilotName();
                    run.enter(Phase.WATCH, tick);
                } else if (entry != null && in % 40 == 10) {
                    KeyMapping.click(InputConstants.getKey(SpectateKeys.CYCLE.saveString()));
                } else if (in > CHANNEL_WAIT) {
                    run.failure = "no channel";
                    return finish(run);
                }
            }
            case WATCH -> {
                count(run, mc, session, false);
                BlockState flag = signal(mc, run);
                if (flag != null && !flag.is(Blocks.GOLD_BLOCK)) {
                    run.enter(Phase.RESCUE, tick);
                }
            }
            case RESCUE -> {
                count(run, mc, session, true);
                BlockState flag = signal(mc, run);
                if (flag != null && (flag.is(Blocks.EMERALD_BLOCK) || flag.is(Blocks.REDSTONE_BLOCK))) {
                    if (flag.is(Blocks.REDSTONE_BLOCK)) {
                        run.failure = "server says not landed at home";
                    }
                    return finish(run);
                }
            }
        }
        return null;
    }

    private static void count(Run run, Minecraft mc, SpectateSession session, boolean rescue) {
        boolean watching = session.watching() && session.drone() != null;
        if (rescue) {
            run.rescueTicks++;
            if (watching) {
                run.rescueWatched++;
            }
        }
        if (!watching) {
            run.lastCamera = null;
            return;
        }
        run.watchTicks++;
        Vec3 camera = mc.gameRenderer.mainCamera().position();
        if (run.lastCamera != null) {
            double step = camera.distanceTo(run.lastCamera);
            run.moved += step;
            if (rescue) {
                run.rescueMoved += step;
            }
        }
        run.lastCamera = camera;
    }

    private static @Nullable BlockState signal(Minecraft mc, Run run) {
        BlockPos pos = run.signal;
        return pos == null || mc.level == null ? null : mc.level.getBlockState(pos);
    }

    private static @Nullable BlockPos findSignal(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return null;
        }
        BlockPos center = mc.player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-SCAN, -8, -SCAN), center.offset(SCAN, 8, SCAN))) {
            BlockState state = mc.level.getBlockState(pos);
            if ((state.is(Blocks.GOLD_BLOCK) || state.is(Blocks.LAPIS_BLOCK)) && mc.level.getBlockState(pos.east()).is(Blocks.GOLD_BLOCK)) {
                return pos.immutable();
            }
        }
        return null;
    }

    private static String finish(Run run) {
        StringBuilder out = new StringBuilder();
        if (run.failure != null) {
            out.append("cov_watchrescue failed ").append(run.failure).append('\n');
        }
        out.append("cov_watchrescue watch pilot=").append(run.pilotName).append(" ticks=").append(run.watchTicks).append(" moved=")
                .append(String.format(Locale.ROOT, "%.1f", run.moved)).append('\n');
        out.append("cov_watchrescue rescue_watched=").append(run.rescueWatched).append('/').append(run.rescueTicks).append(" moved=")
                .append(String.format(Locale.ROOT, "%.1f", run.rescueMoved)).append('\n');
        boolean watched = CovWatchRescueJoinCheck.PILOT.equals(run.pilotName) && run.watchTicks >= MIN_WATCH_TICKS && run.moved >= MIN_WATCH_MOVED;
        boolean stayed = run.rescueTicks > 0 && run.rescueWatched >= run.rescueTicks - 2;
        if (watched && stayed && run.failure == null) {
            out.append("multi watched rescue\n");
        }
        return out.toString();
    }
}
