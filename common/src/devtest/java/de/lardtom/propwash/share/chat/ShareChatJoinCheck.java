package de.lardtom.propwash.share.chat;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.share.ShareVectors;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ShareChatJoinCheck {
    public static final String CODE = ShareVectors.NAMED_WHOOP;
    public static final String LEGACY = ShareVectors.PW2_NAMED;
    public static final int SAY_TICKS = 60;
    public static final int REPEAT_TICKS = 300;
    private static final Map<UUID, Integer> JOINED = new HashMap<>();

    private ShareChatJoinCheck() {
    }

    public static void tick(MinecraftServer server, int tick) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int age = tick - JOINED.computeIfAbsent(player.getUUID(), id -> tick);
            if (age == SAY_TICKS || age == SAY_TICKS + REPEAT_TICKS) {
                int said = execute(server, "say Neue Drohne zum Nachbauen: " + CODE + " – viel Spaß");
                int told = execute(server, "tellraw " + player.getScoreboardName() + " [\"<Bea> mein alter Code: \",{\"text\":\"" + LEGACY
                        + "\",\"color\":\"gray\"}]");
                Constants.LOG.info("[Beitrittstest] Sharecode im Chat: /say mit PW3-Code an {} ergab {}, tellraw mit PW2-Code {}",
                        player.getPlainTextName(), said, told);
            }
        }
    }

    private static int execute(MinecraftServer server, String command) {
        try {
            return server.getCommands().getDispatcher().execute(command, server.createCommandSourceStack());
        } catch (Exception e) {
            Constants.LOG.error("[Beitrittstest] Sharecode im Chat: Befehl {} warf", command, e);
            return -1;
        }
    }
}
