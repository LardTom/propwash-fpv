package de.lardtom.propwash.guard.client;

import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.guard.GuardServer;
import de.lardtom.propwash.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.permissions.Permissions;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CovGuardJoinProbes {
    public static final int TIMEOUT_TICKS = 20 * 90;
    private static final int SETTLE_TICKS = 80;
    private static final int HOLD_TICKS = 60;
    private static final String START = "cov_guard_join_start";
    private static final String FPV = "cov_guard_join_fpv";

    private CovGuardJoinProbes() {
    }

    public static int serverHints(Minecraft mc) {
        int count = 0;
        try {
            Field field = ChatComponent.class.getDeclaredField("allMessages");
            field.setAccessible(true);
            List<?> messages = (List<?>) field.get(mc.gui.hud.getChat());
            for (Object message : new ArrayList<>(messages)) {
                if (message instanceof GuiMessage gui && gui.content().getContents() instanceof TranslatableContents contents
                        && GuardServer.HINT_KEY.equals(contents.getKey())) {
                    count++;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
        return count;
    }

    private static String hints(Minecraft mc) {
        boolean operator = mc.player != null && mc.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        return "ns_server_hint=" + serverHints(mc) + "\nns_client_hint=" + GuardClient.hints() + "\nns_operator=" + (operator ? "yes" : "no")
                + "\nnoclip_client=" + (Services.PLATFORM.isModLoaded(CovGuardSelfTests.NOCLIP) ? "loaded" : "absent") + "\n";
    }

    public static @Nullable String nsOp(Map<String, Object> state, int tick, Minecraft mc) {
        int start = (int) state.computeIfAbsent(START, key -> tick);
        if (tick - start < SETTLE_TICKS) {
            return null;
        }
        return hints(mc);
    }

    public static @Nullable String noclip(Map<String, Object> state, int tick, Minecraft mc) {
        int start = (int) state.computeIfAbsent(START, key -> tick);
        PilotSession session = PilotSession.INSTANCE;
        if (!state.containsKey(FPV)) {
            if (session.isFpv() && session.drone() != null) {
                state.put(FPV, tick);
                return null;
            }
            return tick - start > TIMEOUT_TICKS ? "noclip_fpv=none\n" + hints(mc) : null;
        }
        if (tick - (int) state.get(FPV) < HOLD_TICKS) {
            return null;
        }
        return "noclip_fpv=" + (session.isFpv() ? "held" : "ended") + "\n" + hints(mc);
    }
}
