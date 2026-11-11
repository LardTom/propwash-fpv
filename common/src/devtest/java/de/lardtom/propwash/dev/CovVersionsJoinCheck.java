package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.network.ProtocolVersionPayload;
import de.lardtom.propwash.platform.Services;
import net.minecraft.server.MinecraftServer;

public final class CovVersionsJoinCheck {
    private static final int REPORT_TICKS = 20 * 30;
    private static boolean registered;
    private static int joins;

    private CovVersionsJoinCheck() {
    }

    static void tick(MinecraftServer server, int tick) {
        if (!registered) {
            registered = true;
            ServerBus.PLAYER_JOIN.register(player -> {
                joins++;
                Constants.LOG.warn("[Beitrittstest] Versionen: {} ist beigetreten – erwartet war eine Absage wegen der anderen Propwash-Version",
                        player.getPlainTextName());
            });
            Constants.LOG.info("[Beitrittstest] Versionen: Server Propwash {} (Netzprotokoll {}) wartet auf einen Client mit einer anderen Version",
                    Services.PLATFORM.getModVersion(), ProtocolVersionPayload.localProtocol());
        }
        if (tick % REPORT_TICKS == 0) {
            Constants.LOG.info("[Beitrittstest] Versionen: {} Beitritte bisher, {} Spieler online", joins, server.getPlayerList().getPlayerCount());
        }
    }
}
