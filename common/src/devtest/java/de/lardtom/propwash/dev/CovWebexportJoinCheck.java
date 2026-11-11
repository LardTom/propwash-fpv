package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.core.event.ServerBus;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class CovWebexportJoinCheck {
    private static boolean initialized;

    private CovWebexportJoinCheck() {
    }

    static synchronized void init() {
        if (initialized || !JoinTestEnv.covWebexport()) {
            return;
        }
        initialized = true;
        ServerBus.PLAYER_JOIN.register(CovWebexportJoinCheck::joined);
    }

    private static void joined(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        server.getPlayerList().deop(player.nameAndId());
        Constants.LOG.info("[Beitrittstest] Web-Export: {} ohne Operator-Rechte beigetreten, Stufe {}", player.getPlainTextName(),
                server.getProfilePermissions(player.nameAndId()));
    }
}
