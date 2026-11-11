package de.lardtom.propwash.video.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.apiimpl.Api17Registries;
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
    private static final int PAD_X = 8;
    private static final int PAD_Y = 4;
    private static final int PANEL_MARGIN = 12;
    private static int renderedFrames;
    private static String lastBanner = "";
    private static final int[] LAST_BOX = new int[4];
    private static int lastRow = -1;

    private SpectateHud() {
    }

    public static int renderedFrames() {
        return renderedFrames;
    }

    public static String lastBanner() {
        return lastBanner;
    }

    public static int[] lastBox() {
        return LAST_BOX.clone();
    }

    public static int lastRow() {
        return lastRow;
    }

    public static int bannerTop() {
        return LAST_BOX[1];
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
            banner(graphics, font, searching, null, hint());
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
        Component full = hint();
        Component hint = Util.getNanos() - session.watchingSinceNanos() < HINT_NANOS ? full : null;
        banner(graphics, font, banner, hint, full);
        lastBanner = banner.getString();
        renderedFrames++;
    }

    private static Component hint() {
        return Component.translatable("propwash.video.spectate.hint", SpectateKeys.CYCLE.getTranslatedKeyMessage());
    }

    private static void banner(GuiGraphicsExtractor graphics, Font font, Component caption, @Nullable Component hint, Component fullHint) {
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
        int placeHalf = Math.max(font.width(captionText), font.width(ThemeText.fit(font, fullHint, maxWidth - 2 * PAD_X))) / 2 + PAD_X;
        int fullHeight = PAD_Y * 2 + font.lineHeight + Metrics.LINE_HEIGHT + 1;
        int row = SpectateBannerPlacement.row(grid, SpectateBannerPlacement.occupants(OsdHud.config(), Api17Registries.Client.osdElements()),
                centerX - placeHalf, centerX + placeHalf, fullHeight);
        int top = Math.round(grid.cellTop(row));
        int bottom = top + PAD_Y * 2 + font.lineHeight + (hintText == null ? 0 : Metrics.LINE_HEIGHT + 1);
        LAST_BOX[0] = centerX - half;
        LAST_BOX[1] = top;
        LAST_BOX[2] = centerX + half;
        LAST_BOX[3] = bottom;
        lastRow = row;
        ThemeDraw.notice(graphics, centerX - half, top, centerX + half, bottom, Palette.ACCENT, 1.0F);
        ThemeText.textCentered(graphics, font, captionText, centerX + 1, top + PAD_Y + 1, Palette.TEXT);
        if (hintText != null) {
            ThemeText.textCentered(graphics, font, hintText, centerX + 1, top + PAD_Y + font.lineHeight + 2, Palette.TEXT_MUTED);
        }
    }
}
