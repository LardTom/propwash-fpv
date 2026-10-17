package de.lardtom.propwash.zone.client;

import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.osd.OsdGrid;
import de.lardtom.propwash.osd.client.OsdHud;
import de.lardtom.propwash.osd.client.OsdText;
import de.lardtom.propwash.race.MessageWrap;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.zone.Zone;
import de.lardtom.propwash.zone.ZoneText;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public final class ZoneHud {
    public static final long SHOW_NANOS = 3_500_000_000L;
    public static final long FADE_NANOS = 700_000_000L;
    public static final int OSD_ROW = 5;
    private static final int UNSET = -1;
    private static final int PAD_X = 8;
    private static final int LINE_GAP = 2;
    private static final int MARGIN_X = 8;
    private static final int MIN_WIDTH = 80;
    private static int lastTop = UNSET;
    private static String lastName = "";
    private static @Nullable Notice notice;
    private static int shown;

    public record Notice(Component title, Component detail, int rgb, long until) {
    }

    private ZoneHud() {
    }

    public static @Nullable Notice notice() {
        Notice current = notice;
        return current != null && current.until > Util.getNanos() ? current : null;
    }

    public static int shown() {
        return shown;
    }

    static void tick(Minecraft minecraft) {
        PilotSession session = PilotSession.INSTANCE;
        if (!session.isActive() || session.sim() == null) {
            lastTop = UNSET;
            lastName = "";
            return;
        }
        Zone top = session.zoneResolution().top();
        int id = top == null ? 0 : top.id();
        if (id == lastTop) {
            if (top != null) {
                lastName = top.name();
            }
            return;
        }
        boolean starting = lastTop == UNSET;
        if (top != null) {
            show(entered(top));
        } else if (!starting) {
            show(new Notice(Component.translatable("propwash.zone.hud.left", lastName), Component.translatable("propwash.zone.hud.world"),
                    ZoneRenderer.NEUTRAL, 0L));
        }
        lastTop = id;
        lastName = top == null ? "" : top.name();
    }

    public static Notice entered(Zone zone) {
        Component kind = ZoneText.kind(zone);
        Component detail = zone.overrides().isEmpty() ? kind : Component.translatable("propwash.zone.hud.detail", ZoneText.overrides(zone), kind);
        return new Notice(Component.translatable("propwash.zone.hud.entered", zone.name()), detail, ZoneRenderer.color(zone), 0L);
    }

    public static void show(Notice next) {
        notice = new Notice(next.title, next.detail, next.rgb, Util.getNanos() + SHOW_NANOS);
        shown++;
    }

    static void clear() {
        notice = null;
        lastTop = UNSET;
        lastName = "";
    }

    public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Notice current = notice();
        Minecraft minecraft = Minecraft.getInstance();
        if (current == null || minecraft.player == null || !PilotSession.INSTANCE.isActive()) {
            return;
        }
        long left = current.until - Util.getNanos();
        float alpha = Math.min(1.0F, left / (float) FADE_NANOS);
        int a = Math.max(16, Math.round(alpha * 255.0F));
        Font font = minecraft.font;
        int centerX = graphics.guiWidth() / 2;
        if (PilotSession.INSTANCE.isFpv()) {
            OsdGrid grid = OsdHud.grid(graphics.guiWidth(), graphics.guiHeight());
            float scale = OsdText.scale(graphics, 1.0F);
            float y = grid.cellTop(OSD_ROW);
            float room = grid.width() - 2.0F * grid.cellWidth();
            float step = OsdText.height(scale) + 3.0F * scale;
            for (String part : MessageWrap.wrap(current.title.getString().toUpperCase(Locale.ROOT), room, text -> OsdText.width(font, text, scale))) {
                OsdText.drawCentered(graphics, font, part, centerX, y, scale, ARGB.color(a, current.rgb));
                y += step;
            }
            for (String part : MessageWrap.wrap(current.detail.getString().toUpperCase(Locale.ROOT), room, text -> OsdText.width(font, text, scale))) {
                OsdText.drawCentered(graphics, font, part, centerX, y, scale, ARGB.color(a, 0xFFFFFF));
                y += step;
            }
            return;
        }
        int room = Math.max(MIN_WIDTH, graphics.guiWidth() - 2 * MARGIN_X - 2 * PAD_X);
        List<FormattedCharSequence> titles = font.split(current.title, room);
        List<FormattedCharSequence> details = font.split(current.detail, room);
        int widest = 0;
        for (FormattedCharSequence line : titles) {
            widest = Math.max(widest, font.width(line));
        }
        for (FormattedCharSequence line : details) {
            widest = Math.max(widest, font.width(line));
        }
        int width = widest / 2 + PAD_X;
        int lines = titles.size() + details.size();
        int top = graphics.guiHeight() / 4;
        int bottom = top + font.lineHeight * lines + LINE_GAP * Math.max(0, lines - 1) + 8;
        ThemeDraw.notice(graphics, centerX - width, top, centerX + width, bottom, ARGB.opaque(current.rgb), alpha);
        int y = top + 4;
        for (FormattedCharSequence line : titles) {
            graphics.centeredText(font, line, centerX, y, ARGB.color(a, current.rgb));
            y += font.lineHeight + LINE_GAP;
        }
        for (FormattedCharSequence line : details) {
            graphics.centeredText(font, line, centerX, y, ARGB.color(a, 0xE8E8E8));
            y += font.lineHeight + LINE_GAP;
        }
    }
}
