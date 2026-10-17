package de.lardtom.propwash.rescue.client;

import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.rescue.net.HomeResetC2S;
import net.minecraft.client.Minecraft;

public final class HomeResetInput {
    private static boolean switchActive;
    private static int sent;

    private HomeResetInput() {
    }

    static void tick(Minecraft minecraft) {
        boolean requested = false;
        while (RescueKeys.RESET.consumeClick()) {
            requested = true;
        }
        boolean active = RadioState.INSTANCE.isActive(SwitchFunction.RESET);
        if (active && !switchActive) {
            requested = true;
        }
        switchActive = active;
        if (requested && !minecraft.isPaused()) {
            request();
        }
    }

    public static boolean request() {
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.isActive() ? session.drone() : null;
        if (drone == null || !ClientNet.canSend(HomeResetC2S.TYPE)) {
            return false;
        }
        ClientNet.sendToServer(new HomeResetC2S(drone.getId()));
        sent++;
        return true;
    }

    public static int sent() {
        return sent;
    }

    static void clear() {
        switchActive = false;
    }
}
