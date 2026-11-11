package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.dev.example.ExampleRules;
import de.lardtom.propwash.server.CovAddonRulesJoinAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class CovAddonRulesJoinCheck {
    public static final int CHANGE_AFTER_TICKS = 60;
    public static final int VALUE = 12;
    private static boolean initialized;
    private static int joinedAt = -1;
    private static boolean changed;
    private static int empty;

    private CovAddonRulesJoinCheck() {
    }

    static synchronized void init() {
        if (initialized || !JoinTestEnv.covAddonRules()) {
            return;
        }
        initialized = true;
        ServerBus.PLAYER_JOIN.register(CovAddonRulesJoinCheck::joined);
    }

    private static void joined(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        server.getPlayerList().deop(player.nameAndId());
        Constants.LOG.info("[Beitrittstest] Add-on-Regeln: {} tritt ohne Operator bei, {} = {}", player.getPlainTextName(), ExampleRules.LAUNCHER_COOLDOWN,
                ExampleRules.registered() ? ExampleRules.cooldown().get(server.overworld()) : "nicht angemeldet");
    }

    static void tick(MinecraftServer server, int tick) {
        if (!ExampleRules.registered()) {
            return;
        }
        boolean present = !server.getPlayerList().getPlayers().isEmpty();
        if (present && joinedAt < 0) {
            joinedAt = tick;
        }
        if (present && !changed && tick - joinedAt >= CHANGE_AFTER_TICKS) {
            changed = true;
            String result = CovAddonRulesJoinAccess.changeAsOperator(server, ExampleRules.LAUNCHER_COOLDOWN, Integer.toString(VALUE));
            Constants.LOG.info("[Beitrittstest] Add-on-Regeln: Operator (simuliert am Server) setzt {} auf {}: {}, Wert jetzt {}",
                    ExampleRules.LAUNCHER_COOLDOWN, VALUE, result, ExampleRules.cooldown().get(server.overworld()));
        }
        if (changed && !present) {
            empty++;
            if (empty == 40) {
                CovAddonRulesJoinAccess.reset(server, ExampleRules.cooldown());
                Constants.LOG.info("[Beitrittstest] Add-on-Regeln: Spieler weg, {} zurück auf {}", ExampleRules.LAUNCHER_COOLDOWN,
                        ExampleRules.cooldown().get(server.overworld()));
                joinedAt = -1;
                changed = false;
                empty = 0;
            }
        } else {
            empty = 0;
        }
    }
}
