package de.lardtom.propwash.dev.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.dev.AutonomyOfflineJoinCheck;
import de.lardtom.propwash.race.client.RaceDataChat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

public final class AutonomyOfflineJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 60 * 18;
    public static final String AWAY = "autonomy_offline_away";
    private static final String START = "autonomy_offline_start";
    private static final String SINCE = "autonomy_offline_since";
    private static final String RECONNECT = "autonomy_offline_reconnect";
    private static final String LEFT = "autonomy_offline_left";
    private static final String KEY = "autonomy_offline";
    private static final int RECONNECT_TIMEOUT_TICKS = 20 * 60;
    private static final int DEFAULT_AWAY_SECONDS = 20;

    private AutonomyOfflineJoinProbe() {
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        state.putIfAbsent(START, tick);
        if (tick - (int) state.get(START) > TIMEOUT_TICKS) {
            return KEY + " timeout after " + state.getOrDefault(LEFT, 0) + " leaves\n";
        }
        Optional<String> result = RaceDataChat.line(mc, AutonomyOfflineJoinCheck.RESULT);
        if (result.isPresent()) {
            String line = KEY + "=" + result.get().substring(AutonomyOfflineJoinCheck.RESULT.length()).trim() + " leaves " + state.getOrDefault(LEFT, 0) + "\n";
            Constants.LOG.info("[Beitrittstest] Autonomie ohne Besitzer (Client): {}", line.trim());
            return line;
        }
        Optional<String> leave = RaceDataChat.line(mc, AutonomyOfflineJoinCheck.LEAVE);
        if (leave.isPresent()) {
            int away = awaySeconds(leave.get());
            state.put(AWAY, away * 20);
            state.put(SINCE, -1);
            state.merge(LEFT, 1, (a, b) -> (int) a + (int) b);
            Constants.LOG.info("[Beitrittstest] Autonomie ohne Besitzer (Client): {} – trenne für {} s", leave.get(), away);
            mc.disconnectFromWorld(Component.literal("Propwash Beitrittstest: Besitzer trennt"));
        }
        return null;
    }

    public static void whileAway(Map<String, Object> state, int tick, Minecraft mc, String target) {
        if (!state.containsKey(RECONNECT)) {
            if (mc.level != null) {
                return;
            }
            if ((int) state.get(SINCE) < 0) {
                state.put(SINCE, tick);
            }
            if (tick - (int) state.get(SINCE) < (int) state.get(AWAY)) {
                return;
            }
            Constants.LOG.info("[Beitrittstest] Autonomie ohne Besitzer (Client): verbinde wieder mit {}", target);
            ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(target),
                    new ServerData("Propwash join test", target, ServerData.Type.OTHER), false, null);
            state.put(RECONNECT, tick);
            return;
        }
        if (mc.level != null && mc.player != null && mc.gui.screen() == null) {
            state.remove(AWAY);
            state.remove(SINCE);
            state.remove(RECONNECT);
            return;
        }
        if (tick - (int) state.get(RECONNECT) > RECONNECT_TIMEOUT_TICKS) {
            state.put(KEY, KEY + " reconnect failed after " + state.getOrDefault(LEFT, 0) + " leaves\n");
            state.remove(AWAY);
            state.remove(SINCE);
            state.remove(RECONNECT);
        }
    }

    private static int awaySeconds(String line) {
        int at = line.lastIndexOf(" away ");
        if (at < 0) {
            return DEFAULT_AWAY_SECONDS;
        }
        try {
            return Math.max(1, Integer.parseInt(line.substring(at + " away ".length()).trim()));
        } catch (NumberFormatException e) {
            return DEFAULT_AWAY_SECONDS;
        }
    }
}
