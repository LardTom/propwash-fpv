package de.lardtom.propwash.video.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.drone.client.FpvView;
import de.lardtom.propwash.osd.OsdGrid;
import de.lardtom.propwash.osd.client.OsdHud;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.video.net.ChannelListS2C;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public final class SpectateHud {
    public static final Identifier ID = Constants.id("spectate");
    public static final long HINT_NANOS = 6_000_000_000L;
    private static final int BANNER_ROW = 3;
    private static final int PAD_X = 8;
    private static final int PAD_Y = 4;
    private static final int PANEL_MARGIN = 12;
    private static int renderedFrames;
    private static String lastBanner = "";

    private SpectateHud() {
    }

    public static int renderedFrames() {
        return renderedFrames;
    }

    public static String lastBanner() {
        return lastBanner;
    }

    public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        SpectateSession session = SpectateSession.INSTANCE;
        Minecraft minecraft = Minecraft.getInstance();
        if (!session.active() || minecraft.gui.screen() instanceof ThemedScreen) {
            return;
        }
        Font font = minecraft.font;
        graphics.nextStratum();
        ChannelListS2C.Entry entry = session.targetEntry();
        Component channel = entry != null ? SpectateSession.channelName(entry) : Component.translatable("propwash.video.channel.unassigned");
        if (!FpvView.spectating() || session.phase() != SpectateSession.Phase.WATCHING) {
            Component searching = Component.translatable("propwash.video.spectate.searching", channel);
            banner(graphics, font, searching, null);
            lastBanner = searching.getString();
            renderedFrames++;
            return;
        }
        if (!SpectateConfig.get().showBanner) {
            renderedFrames++;
            return;
        }
        Component banner = entry != null
                ? Component.translatable("propwash.video.spectate.banner", channel, SpectateSession.pilotName(entry), SpectateSession.droneName(entry))
                : Component.translatable("propwash.video.spectate.banner_short", channel);
        Component hint = Util.getNanos() - session.watchingSinceNanos() < HINT_NANOS
                ? Component.translatable("propwash.video.spectate.hint", SpectateKeys.CYCLE.getTranslatedKeyMessage())
                : null;
        banner(graphics, font, banner, hint);
        lastBanner = banner.getString();
        renderedFrames++;
    }

    public static int bannerTop(int guiWidth, int guiHeight) {
        return Math.round(OsdHud.grid(guiWidth, guiHeight).cellTop(BANNER_ROW));
    }

    private static void banner(GuiGraphicsExtractor graphics, Font font, Component caption, @Nullable Component hint) {
        OsdGrid grid = OsdHud.grid(graphics.guiWidth(), graphics.guiHeight());
        int maxWidth = Math.max(80, Math.round(grid.width()) - 2 * PANEL_MARGIN);
        String captionText = ThemeText.fit(font, caption, maxWidth - 2 * PAD_X);
        String hintText = hint == null ? null : ThemeText.fit(font, hint, maxWidth - 2 * PAD_X);
        int textWidth = font.width(captionText);
        if (hintText != null) {
            textWidth = Math.max(textWidth, font.width(hintText));
        }
        int half = textWidth / 2 + PAD_X;
        int centerX = (int) (grid.left() + grid.width() / 2.0F);
        int top = Math.round(grid.cellTop(BANNER_ROW));
        int bottom = top + PAD_Y * 2 + font.lineHeight + (hintText == null ? 0 : Metrics.LINE_HEIGHT + 1);
        ThemeDraw.notice(graphics, centerX - half, top, centerX + half, bottom, Palette.ACCENT, 1.0F);
        ThemeText.textCentered(graphics, font, captionText, centerX + 1, top + PAD_Y + 1, Palette.TEXT);
        if (hintText != null) {
            ThemeText.textCentered(graphics, font, hintText, centerX + 1, top + PAD_Y + font.lineHeight + 2, Palette.TEXT_MUTED);
        }
    }
}
