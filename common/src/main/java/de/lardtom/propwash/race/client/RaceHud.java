package de.lardtom.propwash.race.client;

import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.osd.OsdGrid;
import de.lardtom.propwash.osd.client.OsdHud;
import de.lardtom.propwash.osd.client.OsdText;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.MessageWrap;
import de.lardtom.propwash.race.StartLights;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

public final class RaceHud {
    public static final int WHITE = 0xFFFFFF;
    public static final int GREEN = 0x55FF77;
    public static final int RED = 0xFF5555;
    public static final int YELLOW = 0xFFE066;
    public static final int GRAY = 0xBBBBBB;
    public static final int GOLD = 0xFFAA00;
    private static final int MAX_LINES = 4;
    private static final int TOAST_TOP = 6;
    private static final int TOAST_PAD_X = 7;
    private static final int TOAST_HEIGHT = 16;
    private static final int TOAST_GAP = 3;
    private static final int TOAST_MARGIN_X = 8;
    private static final int TOAST_MIN_WIDTH = 80;
    private static final int TOAST_LINE_GAP = 1;
    private static final int OSD_ROW = 11;
    private static final int ASSIST_ROW = 4;
    private static final int ASSIST_HEIGHT = 12;
    private static final long MESSAGE_NANOS = 3_000_000_000L;
    private static final long FADE_NANOS = 600_000_000L;
    private static final int LAMP_SIZE = 14;
    private static final int LAMP_GAP = 5;
    private static final int LAMP_PAD = 5;
    private static final int LAMP_RED = 0xFFE53935;
    private static final int LAMP_GREEN = 0xFF43E06A;
    private static final int LAMP_AMBER = 0xFFFFA726;
    private static final Deque<Line> LINES = new ArrayDeque<>();
    private static @Nullable Component big;
    private static int bigColor = WHITE;
    private static long bigUntil;

    private record Line(Component text, int rgb, long until) {
    }

    private RaceHud() {
    }

    public static void message(Component text, int rgb) {
        message(text, rgb, MESSAGE_NANOS);
    }

    public static void message(Component text, int rgb, long durationNanos) {
        LINES.addFirst(new Line(text, rgb, Util.getNanos() + durationNanos));
        while (LINES.size() > MAX_LINES) {
            LINES.removeLast();
        }
    }

    public static void big(Component text, int rgb, long durationNanos) {
        big = text;
        bigColor = rgb;
        bigUntil = Util.getNanos() + durationNanos;
    }

    public static List<Component> recentMessages() {
        List<Component> out = new ArrayList<>();
        for (Line line : LINES) {
            out.add(line.text());
        }
        return out;
    }

    public static void clear() {
        LINES.clear();
        big = null;
        bigUntil = 0L;
    }

    private static void extractLights(GuiGraphicsExtractor graphics, int centerX, int top) {
        ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
        long remaining = tracker.tracker().countdownRemainingNanos(tracker.now());
        long total = tracker.countdownTotalNanos();
        boolean countdown = remaining >= 0L;
        boolean go = !countdown && tracker.tracker().racing() && tracker.sinceGoNanos() < ClientRaceTracker.GO_LIGHT_NANOS;
        if (!countdown && !go) {
            return;
        }
        int lamps = StartLights.lamps(total);
        int lit = countdown ? StartLights.lit(total, remaining) : lamps;
        int color = go ? LAMP_GREEN : tracker.tracker().falseStarted() ? LAMP_AMBER : LAMP_RED;
        int width = lamps * LAMP_SIZE + (lamps - 1) * LAMP_GAP + 2 * LAMP_PAD;
        int left = centerX - width / 2;
        ThemeDraw.framedBox(graphics, left, top, left + width, top + LAMP_SIZE + 2 * LAMP_PAD, Palette.BACKGROUND, Palette.BORDER_STRONG);
        for (int i = 0; i < lamps; i++) {
            int x = left + LAMP_PAD + i * (LAMP_SIZE + LAMP_GAP);
            ThemeDraw.lamp(graphics, x, top + LAMP_PAD, LAMP_SIZE, LAMP_SIZE, i < lit ? color : 0);
        }
    }

    private static void extractLobby(GuiGraphicsExtractor graphics, Font font, int centerX, boolean osd) {
        ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
        List<String> pilots = tracker.lobbyPilots();
        if (pilots.isEmpty() || tracker.countdownActive() || tracker.tracker().racing()) {
            return;
        }
        long seconds = (tracker.lobbyRemainingNanos() + LapTracker.SECOND - 1L) / LapTracker.SECOND;
        Component text = tracker.lobbyMember()
                ? Component.translatable("propwash.race.lobby.waiting", tracker.lobbyCourse(), pilots.size(), seconds)
                : Component.translatable("propwash.race.lobby.open", tracker.lobbyCourse(), pilots.size());
        Component names = Component.literal(String.join(", ", pilots));
        int y = (int) (graphics.guiHeight() * 0.72F);
        if (osd) {
            float scale = OsdText.scale(graphics, 1.0F);
            OsdText.drawCentered(graphics, font, text.getString(), centerX, y, scale, ARGB.opaque(YELLOW));
            OsdText.drawCentered(graphics, font, names.getString(), centerX, y + OsdText.height(scale) + 3.0F * scale, scale, ARGB.opaque(GRAY));
            return;
        }
        int half = Math.max(font.width(text), font.width(names)) / 2 + TOAST_PAD_X;
        ThemeDraw.notice(graphics, centerX - half, y - 3, centerX + half, y + font.lineHeight * 2 + 6, ARGB.opaque(YELLOW), 1.0F);
        ThemeText.textCentered(graphics, font, text, centerX + 1, y + 1, ARGB.opaque(YELLOW));
        ThemeText.textCentered(graphics, font, names, centerX + 1, y + font.lineHeight + 3, Palette.TEXT_MUTED);
    }

    public static boolean showsAssistTag() {
        ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
        return tracker.tracker().racing() && tracker.lapAssisted() && ClientRaceTracker.savesAssisted();
    }

    public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !RaceConfig.CONFIG.get().showMessages) {
            return;
        }
        long now = Util.getNanos();
        Font font = minecraft.font;
        boolean osd = PilotSession.INSTANCE.isFpv();
        if (osd) {
            graphics.nextStratum();
        }
        int centerX = graphics.guiWidth() / 2;
        int baseY = (int) (graphics.guiHeight() * 0.28F);
        if (RaceConfig.CONFIG.get().startLights) {
            extractLights(graphics, centerX, baseY - font.lineHeight * 2 - 6 - LAMP_SIZE - 2 * LAMP_PAD - 6);
        }
        extractLobby(graphics, font, centerX, osd);
        Component bigText = null;
        int bigRgb = bigColor;
        long countdown = ClientRaceTracker.INSTANCE.tracker().countdownRemainingNanos(ClientRaceTracker.INSTANCE.now());
        if (countdown >= 0L) {
            long seconds = (countdown + 999_999_999L) / 1_000_000_000L;
            bigText = Component.literal(Long.toString(Math.max(1L, seconds)));
            bigRgb = ClientRaceTracker.INSTANCE.tracker().falseStarted() ? RED : YELLOW;
        } else if (big != null && now < bigUntil) {
            bigText = big;
        }
        if (bigText != null) {
            if (osd) {
                float scale = OsdText.scale(graphics, 3.0F);
                OsdText.drawCentered(graphics, font, bigText.getString(), centerX, baseY - OsdText.height(scale) / 2.0F, scale,
                        ARGB.opaque(bigRgb));
            } else {
                int half = font.width(bigText) * 2 + 10;
                int top = baseY - font.lineHeight * 2 - 6;
                int bottom = baseY + font.lineHeight * 2 + 4;
                ThemeDraw.notice(graphics, centerX - half, top, centerX + half, bottom, ARGB.opaque(bigRgb), 1.0F);
                Matrix3x2fStack pose = graphics.pose();
                pose.pushMatrix();
                pose.translate(centerX, baseY);
                pose.scale(4.0F, 4.0F);
                graphics.centeredText(font, bigText, 0, -font.lineHeight / 2, ARGB.opaque(bigRgb));
                pose.popMatrix();
            }
        }
        float osdScale = osd ? OsdText.scale(graphics, 1.0F) : 1.0F;
        float y = osd ? OsdHud.grid(graphics.guiWidth(), graphics.guiHeight()).cellTop(OSD_ROW) : TOAST_TOP;
        if (showsAssistTag()) {
            Component tag = Component.translatable("propwash.race.hud.assisted");
            if (osd) {
                OsdText.drawCentered(graphics, font, tag.getString(), centerX, OsdHud.grid(graphics.guiWidth(), graphics.guiHeight()).cellTop(ASSIST_ROW),
                        osdScale, ARGB.opaque(YELLOW));
            } else {
                int half = font.width(tag) / 2 + 4;
                ThemeDraw.badge(graphics, font, tag, centerX - half, (int) y, (int) y + ASSIST_HEIGHT, Palette.WARNING, Palette.BACKGROUND);
                y += ASSIST_HEIGHT + TOAST_GAP;
            }
        }
        Iterator<Line> iterator = LINES.iterator();
        while (iterator.hasNext()) {
            Line line = iterator.next();
            long left = line.until - now;
            if (left <= 0L) {
                iterator.remove();
                continue;
            }
            float alpha = Math.min(1.0F, left / (float) FADE_NANOS);
            if (osd) {
                OsdGrid grid = OsdHud.grid(graphics.guiWidth(), graphics.guiHeight());
                float scale = osdScale;
                for (String part : MessageWrap.wrap(line.text.getString(), grid.width() - 2.0F * grid.cellWidth(),
                        text -> OsdText.width(font, text, scale))) {
                    OsdText.drawCentered(graphics, font, part, centerX, y, osdScale, ARGB.color(Math.max(16, (int) (alpha * 255.0F)), line.rgb));
                    y += OsdText.height(osdScale) + 4.0F * osdScale;
                }
                continue;
            }
            int room = Math.max(TOAST_MIN_WIDTH, graphics.guiWidth() - 2 * TOAST_MARGIN_X - 2 * TOAST_PAD_X);
            List<FormattedCharSequence> parts = font.split(line.text, room);
            int widest = 0;
            for (FormattedCharSequence part : parts) {
                widest = Math.max(widest, font.width(part));
            }
            int half = widest / 2 + TOAST_PAD_X;
            int top = (int) y;
            int height = TOAST_HEIGHT + Math.max(0, parts.size() - 1) * (font.lineHeight + TOAST_LINE_GAP);
            ThemeDraw.notice(graphics, centerX - half, top, centerX + half, top + height, ARGB.opaque(line.rgb), alpha);
            int textY = top + (TOAST_HEIGHT - font.lineHeight) / 2 + 1;
            int color = ARGB.color(Math.max(16, (int) (alpha * 255.0F)), line.rgb);
            for (FormattedCharSequence part : parts) {
                graphics.centeredText(font, part, centerX + 1, textY, color);
                textY += font.lineHeight + TOAST_LINE_GAP;
            }
            y += height + TOAST_GAP;
        }
    }
}
