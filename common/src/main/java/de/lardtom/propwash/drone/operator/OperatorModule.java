package de.lardtom.propwash.drone.operator;

import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.operator.net.OperatorInputC2S;
import de.lardtom.propwash.drone.operator.net.OperatorRoleS2C;
import de.lardtom.propwash.drone.operator.net.OperatorStateS2C;
import de.lardtom.propwash.network.PropwashNet;

public final class OperatorModule {
    private static boolean initialized;

    private OperatorModule() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PropwashNet.registerC2S(OperatorInputC2S.TYPE, OperatorInputC2S.CODEC, OperatorServer::onInput);
        PropwashNet.registerS2C(OperatorStateS2C.TYPE, OperatorStateS2C.CODEC);
        PropwashNet.registerS2C(OperatorRoleS2C.TYPE, OperatorRoleS2C.CODEC);
        QuadcopterEntity.PILOT_CHANGED.register(OperatorServer::onPilotChanged);
        ServerBus.TICK_END.register(server -> {
            OperatorServer.onTick(server);
            AutonomousFeeds.onTick(server);
        });
        ServerBus.PLAYER_LEAVE.register(OperatorServer::onLeave);
        ServerBus.STARTED.register(server -> {
            OperatorServer.clear();
            AutonomousFeeds.clear();
        });
        ServerBus.STOPPED.register(server -> {
            OperatorServer.clear();
            AutonomousFeeds.clear();
        });
    }
}
