package de.lardtom.propwash.server;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.registry.PropwashRegistries;
import de.lardtom.propwash.registry.RegHolder;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ServerModule {
    public static final RegHolder<TicketType> DRONE_TICKET = PropwashRegistries.ticketType("drone",
            TicketLedger.TIMEOUT_TICKS, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION);
    public static final RegHolder<TicketType> DRONE_VIEW_TICKET = PropwashRegistries.ticketType("drone_view",
            TicketLedger.TIMEOUT_TICKS, TicketType.FLAG_LOADING);
    public static final RegHolder<TicketType> DRONE_LOOKAHEAD_TICKET = PropwashRegistries.ticketType("drone_lookahead",
            TicketLedger.TIMEOUT_TICKS, TicketType.FLAG_LOADING);
    public static final RegHolder<TicketType> PILOT_TICKET = PropwashRegistries.ticketType("pilot_anchor",
            TicketLedger.TIMEOUT_TICKS, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION);

    private static final int RULES_RESYNC_INTERVAL = 40;
    private static final Map<UUID, ServerRulesS2C> SENT_RULES = new HashMap<>();
    private static boolean initialized;
    private static int resyncTimer;
    private static boolean legacyPartWearOff;

    private ServerModule() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        legacyPartWearOff = LegacyServerSettings.partWearOff(ServerRulesConfig.CONFIG.path());
        ServerRulesConfig.CONFIG.load();
        PropwashNet.registerS2C(ServerRulesS2C.TYPE, ServerRulesS2C.CODEC);
        PropwashNet.registerS2C(RangeStatusS2C.TYPE, RangeStatusS2C.CODEC);
        PropwashNet.registerS2C(GenerationEdgeS2C.TYPE, GenerationEdgeS2C.CODEC);
        ServerBus.STARTED.register(server -> {
            if (LegacyServerSettings.partWearOff(ServerRulesConfig.CONFIG.path())) {
                legacyPartWearOff = true;
            }
            ServerRulesConfig.CONFIG.load();
            SENT_RULES.clear();
            RangeManager.reset();
            carryOverPartWear(server);
        });
        ServerBus.STOPPED.register(server -> SENT_RULES.clear());
        ServerBus.PLAYER_JOIN.register(ServerModule::sendRules);
        ServerBus.PLAYER_LEAVE.register(player -> SENT_RULES.remove(player.getUUID()));
        ServerBus.TICK_END.register(ServerModule::resyncRules);
        ServerBus.REGISTER_COMMANDS.register(ServerCommands::register);
        RangeManager.init();
        WorldRules.init();
    }

    private static void carryOverPartWear(MinecraftServer server) {
        if (!legacyPartWearOff) {
            return;
        }
        legacyPartWearOff = false;
        if (WorldRules.set(server, WorldRuleSpecs.CRASH_DAMAGE, 0)) {
            Constants.LOG.info("server.json: {} = false aus 0.4.0 als Welt-Regel propwash:{} = false übernommen", LegacyServerSettings.PART_WEAR,
                    WorldRuleSpecs.CRASH_DAMAGE.name());
        }
    }

    public static void sendRules(ServerPlayer player) {
        ServerRulesS2C rules = ServerRules.snapshotFor(player);
        if (PropwashNet.sendToPlayer(player, rules)) {
            SENT_RULES.put(player.getUUID(), rules);
        }
    }

    public static void broadcastRules(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendRules(player);
        }
    }

    private static void resyncRules(MinecraftServer server) {
        if (++resyncTimer < RULES_RESYNC_INTERVAL) {
            return;
        }
        resyncTimer = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerRulesS2C sent = SENT_RULES.get(player.getUUID());
            if (sent != null && !sent.equals(ServerRules.snapshotFor(player))) {
                sendRules(player);
            }
        }
    }
}
