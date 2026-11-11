package de.lardtom.propwash.webexport.client;

import de.lardtom.propwash.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.server.permissions.Permissions;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public final class CovWebexportJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 20;
    private static final String START = "cov_webexport_start";

    private CovWebexportJoinProbe() {
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        int start = (int) state.computeIfAbsent(START, key -> tick);
        ClientPacketListener connection = mc.getConnection();
        if (connection == null || mc.player == null || tick - start < 60) {
            return null;
        }
        var root = connection.getCommands().getRoot().getChild("propwash");
        if (root == null && tick - start < TIMEOUT_TICKS) {
            return null;
        }
        boolean operator = mc.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        String webexport = root == null ? "covwebexport root missing" : root.getChild("webexport") == null ? "covwebexport absent" : "covwebexport present";
        String code = root == null || root.getChild("code") == null ? "covwebexport code missing" : "covwebexport code present";
        String text = "covwebexport op=" + (operator ? "yes" : "no") + "\n" + webexport + "\n" + code + "\n";
        Constants.LOG.info("[Beitrittstest] Web-Export: {}", text.replace('\n', ' '));
        return text;
    }
}
