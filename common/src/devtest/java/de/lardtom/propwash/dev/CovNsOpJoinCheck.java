package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.guard.NamespaceGuard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

public final class CovNsOpJoinCheck {
    public static final String OPERATOR_PREFIX = "PwOp";
    private static boolean initialized;
    private static int joins;

    private CovNsOpJoinCheck() {
    }

    static synchronized void init() {
        if (initialized || !JoinTestEnv.covNsOp()) {
            return;
        }
        initialized = true;
        ServerBus.PLAYER_JOIN.register(CovNsOpJoinCheck::joined);
    }

    private static void joined(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        joins++;
        String name = player.getPlainTextName();
        boolean operator = name.toLowerCase(Locale.ROOT).startsWith(OPERATOR_PREFIX.toLowerCase(Locale.ROOT));
        if (operator) {
            server.getPlayerList().op(player.nameAndId());
        } else {
            server.getPlayerList().deop(player.nameAndId());
        }
        Constants.LOG.info("[Beitrittstest] Namensraum-Operator: Beitritt {} seit dem Serverstart von {}, Operator {}, Funde {} von {}", joins, name,
                operator, NamespaceGuard.report().count(), NamespaceGuard.report().summary());
    }
}
