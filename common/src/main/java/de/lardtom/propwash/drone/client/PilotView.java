package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.video.client.SpectateSession;
import net.minecraft.client.Minecraft;

public final class PilotView {
    public static final int USE_LATCH_TICKS = 10;

    private static int useLatch;

    private PilotView() {
    }

    public static boolean active() {
        return PilotSession.INSTANCE.isActive() || FpvView.spectating();
    }

    public static boolean useLatched() {
        return useLatch > 0;
    }

    public static void onUseKeyWhilePiloting() {
        useLatch = USE_LATCH_TICKS;
        if (PilotSession.INSTANCE.isActive()) {
            PilotSession.INSTANCE.requestStop();
        } else if (SpectateSession.INSTANCE.active()) {
            SpectateSession.INSTANCE.consumeEscape();
        }
    }

    static void onClientTick(Minecraft mc) {
        if (useLatch > 0) {
            useLatch--;
        }
    }

    static void reset() {
        useLatch = 0;
    }
}
