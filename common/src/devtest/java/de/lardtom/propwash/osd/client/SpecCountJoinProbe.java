package de.lardtom.propwash.osd.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.dev.SpecCountJoinCheck;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.osd.OsdConfig;
import de.lardtom.propwash.osd.OsdElement;
import de.lardtom.propwash.osd.OsdPreset;
import de.lardtom.propwash.video.client.SpectateKeys;
import de.lardtom.propwash.video.client.SpectateSession;
import de.lardtom.propwash.video.net.ChannelListS2C;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class SpecCountJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 240;
    public static final String KEY = "speccount";
    private static final String STATE = "speccount_probe";
    private static final int SETTLE_FRAMES = 10;
    private static final int HOLD_TICKS = SpecCountJoinCheck.STEP_TICKS + 60;
    private static final int KEY_EVERY = 40;

    private SpecCountJoinProbe() {
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        Probe probe = (Probe) state.computeIfAbsent(STATE, key -> new Probe(tick, SpecCountJoinCheck.watchMode()));
        return probe.tick(tick, mc);
    }

    private static final class Probe {
        final int start;
        final boolean watch;
        final List<Integer> seen = new ArrayList<>();
        boolean ready;
        int count;
        int changedAt;
        int drawn;
        int missing;
        int hidden;
        int shownAtZero;
        int keys;

        Probe(int tick, boolean watch) {
            this.start = tick;
            this.watch = watch;
            this.changedAt = tick;
        }

        @Nullable String tick(int tick, Minecraft mc) {
            if (tick - this.start > TIMEOUT_TICKS) {
                return this.report("timeout");
            }
            SpectateSession spectate = SpectateSession.INSTANCE;
            QuadcopterEntity drone = this.watch ? spectate.drone() : PilotSession.INSTANCE.drone();
            boolean live = this.watch ? spectate.watching() && drone != null : PilotSession.INSTANCE.isFpv() && drone != null;
            if (!live) {
                if (this.ready) {
                    return this.report(this.watch ? "watching ended" : "flight ended");
                }
                if (this.watch && !spectate.active() && (tick - this.start) % KEY_EVERY == KEY_EVERY / 2 && this.channel(mc)) {
                    KeyMapping.click(InputConstants.getKey(SpectateKeys.CYCLE.saveString()));
                    this.keys++;
                }
                return null;
            }
            if (!this.ready) {
                this.ready = true;
                OsdConfig standard = OsdConfig.defaults();
                standard.applyPreset(OsdPreset.STANDARD);
                OsdHud.overrideConfig(standard);
                this.changedAt = tick;
                Constants.LOG.info("[Beitrittstest] Zuschauerzahl: {} Drohne {}, OSD mit der Vorlage Standard", this.watch ? "schaue zu," : "fliege",
                        drone.getId());
            }
            int now = SpectatorCountClient.count(drone);
            if (now != this.count) {
                this.count = now;
                this.changedAt = tick;
                this.seen.add(now);
                Constants.LOG.info("[Beitrittstest] Zuschauerzahl im OSD: {} (bisher {}, {} Pakete)", now, this.sequence(), SpectatorCountClient.received());
            }
            if (tick - this.changedAt >= SETTLE_FRAMES) {
                boolean shown = OsdHud.drewLastFrame(OsdElement.SPECTATORS);
                if (now > 0) {
                    if (shown) {
                        this.drawn++;
                    } else {
                        this.missing++;
                    }
                } else if (shown) {
                    this.shownAtZero++;
                } else {
                    this.hidden++;
                }
            }
            List<Integer> expected = SpecCountJoinCheck.EXPECTED;
            if (this.seen.size() > expected.size() || !expected.subList(0, this.seen.size()).equals(this.seen)) {
                return this.report("unexpected sequence");
            }
            if (this.seen.equals(expected) && tick - this.changedAt >= HOLD_TICKS) {
                return this.report("done");
            }
            return null;
        }

        private boolean channel(Minecraft mc) {
            for (ChannelListS2C.Entry entry : SpectateSession.INSTANCE.watchable(mc)) {
                if (SpecCountJoinCheck.TRANSMITTER.equals(entry.pilotName())) {
                    return true;
                }
            }
            return false;
        }

        private String sequence() {
            return this.seen.stream().map(String::valueOf).collect(Collectors.joining(","));
        }

        private String report(String end) {
            int packets = SpectatorCountClient.received();
            boolean sequence = this.seen.equals(SpecCountJoinCheck.EXPECTED);
            boolean shown = this.drawn > 0 && this.missing == 0;
            boolean hidden = this.shownAtZero == 0 && (this.watch || this.hidden > 0);
            boolean ok = end.equals("done") && sequence && packets == SpecCountJoinCheck.EXPECTED.size() && shown && hidden;
            StringBuilder out = new StringBuilder();
            out.append("speccount role=").append(this.watch ? "watcher" : "pilot").append('\n');
            out.append("speccount seen=").append(this.sequence()).append('\n');
            out.append("speccount packets=").append(packets).append('\n');
            out.append("speccount drawn=").append(shown ? "yes" : "no").append(" ticks=").append(this.drawn).append(" missing=").append(this.missing).append('\n');
            out.append("speccount hidden=").append(hidden ? "yes" : "no").append(" ticks=").append(this.hidden).append(" shown_at_zero=").append(this.shownAtZero)
                    .append('\n');
            out.append("speccount end=").append(end).append(" keys=").append(this.keys).append('\n');
            if (ok) {
                out.append("speccount ok\n");
            }
            return out.toString();
        }
    }
}
