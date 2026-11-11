package de.lardtom.propwash.share.chat;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.share.client.ShareConfigTab;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Style;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public final class ShareChatJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 60;
    private static final String START = "share_chat_start";

    private ShareChatJoinProbe() {
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        int start = (int) state.computeIfAbsent(START, key -> tick);
        String code = ShareChatJoinCheck.CODE;
        Style style = ShareChatHarness.shownStyle(mc, code);
        if (!ShareChatHarness.ours(style, code)) {
            if (tick - start < TIMEOUT_TICKS) {
                return null;
            }
            return "share chat link missing: " + ShareChatHarness.shown(mc).stream().map(ShareChatHarness.Shown::plain).toList() + "\n";
        }
        StringBuilder out = new StringBuilder();
        out.append("share chat link ok\n");
        out.append("share chat cells ").append(ShareChatHarness.shownCells(mc, code)).append(" of ").append(code.length()).append('\n');
        ShareChatHarness.Shown line = ShareChatHarness.lineWith(mc, "[Server]");
        Style first = line == null ? null : line.styleAt(0);
        boolean senderPlain = first != null && first.getClickEvent() == null && !first.isUnderlined();
        out.append("share chat line ").append(line == null ? "–" : line.plain().replace('\n', ' ')).append('\n');
        out.append("share chat sender ").append(senderPlain ? "unchanged" : "changed " + first).append('\n');
        Style legacy = ShareChatHarness.shownStyle(mc, ShareChatJoinCheck.LEGACY);
        out.append("share chat legacy ").append(ShareChatHarness.ours(legacy, ShareChatJoinCheck.LEGACY) ? "link ok" : "missing").append('\n');
        ChatScreen chat = new ChatScreen("", false);
        mc.gui.setScreen(chat);
        float[] at = ShareChatHarness.position(mc, candidate -> ShareChatHarness.ours(candidate, code));
        boolean clicked = at != null && mc.gui.screen() == chat
                && chat.mouseClicked(new MouseButtonEvent(at[0], at[1], new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
        ShareConfigTab tab = ShareConfigTab.active();
        boolean open = clicked && mc.gui.screen() instanceof ConfiguratorScreen && tab != null && code.equals(tab.state().text())
                && tab.state().decoded().ok() && tab.lastResult() == null;
        out.append(open ? "share chat import open\n" : "share chat import missing: clicked=" + clicked + " screen=" + mc.gui.screen() + "\n");
        if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
            screen.discardAndClose();
        }
        if (mc.gui.screen() != null) {
            mc.gui.setScreen(null);
        }
        return out.toString();
    }
}
