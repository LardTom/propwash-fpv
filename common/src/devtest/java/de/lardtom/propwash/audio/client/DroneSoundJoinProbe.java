package de.lardtom.propwash.audio.client;

import de.lardtom.propwash.audio.DroneSoundStage.Phase;
import de.lardtom.propwash.dev.DroneSoundJoinCheck;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class DroneSoundJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 150;
    public static final int STILL_TICKS = 40;
    public static final double FAR_ENOUGH = 150.0;
    private static final String KEY = "drone_sound_probe";

    private DroneSoundJoinProbe() {
    }

    private static final class Probe {
        final int start;
        final long mark = SoundPacketLog.mark();
        @Nullable Vec3 last;
        int stillSince = -1;
        boolean fpv;
        HearingProbe.@Nullable Result hearing;

        Probe(int start) {
            this.start = start;
        }
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        Probe probe = (Probe) state.computeIfAbsent(KEY, key -> new Probe(tick));
        if (mc.player == null) {
            return null;
        }
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        if (session.isFpv() && drone != null) {
            probe.fpv = true;
            Vec3 at = drone.position();
            boolean still = probe.last != null && probe.last.distanceTo(at) < 0.05;
            probe.last = at;
            if (!still) {
                probe.stillSince = tick;
            }
            if (probe.hearing == null && tick - probe.stillSince >= STILL_TICKS && at.distanceTo(mc.player.position()) >= FAR_ENOUGH) {
                probe.hearing = HearingProbe.check(mc, drone);
            }
        }
        String server = serverLine(mc);
        if (server == null && tick - probe.start <= TIMEOUT_TICKS) {
            return null;
        }
        Vec3 body = mc.player.position();
        List<SoundPacketLog.Entry> entries = SoundPacketLog.since(probe.mark);
        DroneSoundCheck.Counts far = DroneSoundCheck.count(entries, body, Phase.FAR);
        DroneSoundCheck.Counts near = DroneSoundCheck.count(entries, body, Phase.NEAR);
        HearingProbe.Result hearing = probe.hearing;
        String heard = hearing == null ? "missing" : !hearing.measured() ? "skipped" : hearing.ok() ? "ok" : "bad";
        return "dronesound far=" + far.got() + "/4 near=" + near.got() + "/4 exact=" + (far.exact() && near.exact() ? "yes" : "no") + " hearing=" + heard
                + " fpv=" + (probe.fpv ? "yes" : "no") + "\ndronesound_detail " + far.line() + "; " + near.line() + "; "
                + (hearing == null ? "Hörposition nicht gemessen" : hearing.line()) + "; " + (server == null ? "Server ohne Abschluss" : server) + "\n";
    }

    private static @Nullable String serverLine(Minecraft mc) {
        try {
            Field field = ChatComponent.class.getDeclaredField("allMessages");
            field.setAccessible(true);
            List<?> messages = (List<?>) field.get(mc.gui.hud.getChat());
            for (Object message : new ArrayList<>(messages)) {
                if (message instanceof GuiMessage gui) {
                    String text = gui.content().getString();
                    if (text.startsWith(DroneSoundJoinCheck.PREFIX)) {
                        return text;
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
        return null;
    }
}
