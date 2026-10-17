package de.lardtom.propwash.osd.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.PartNames;
import de.lardtom.propwash.content.Batteries;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.RadioSource;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.OsdFormat;
import de.lardtom.propwash.osd.OsdSampler;
import de.lardtom.propwash.sim.HoldMode;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.video.client.GogglesBlackout;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.Locale;

public final class RadioHud {
    public static final Identifier ID = Constants.id("radio");
    public static final int WIDTH = 140;
    public static final int HEIGHT = 64;
    public static final int MARGIN = 6;
    private static final int BODY = Palette.withAlpha(Palette.PANEL, 0xE6);
    private static final int HEADER_BAR = Palette.withAlpha(Palette.PANEL_HEADER, 0xF0);
    private static final int LINK_OFF = Palette.BORDER_STRONG;
    private static final String KEY = "propwash.osd.radio.";

    private static final OsdSampler SAMPLER = new OsdSampler(new LosOsdSource());
    private static int renderedFrames;

    private RadioHud() {
    }

    public static void onFrame(Minecraft minecraft, DeltaTracker deltaTracker) {
        SAMPLER.sample(Util.getNanos());
    }

    public static void onDisconnect(Minecraft minecraft) {
        SAMPLER.reset();
    }

    public static boolean active() {
        return SAMPLER.data().active;
    }

    public static int renderedFrames() {
        return renderedFrames;
    }

    public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        OsdData d = SAMPLER.data();
        if (!d.active || GogglesBlackout.active()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int x = MARGIN;
        int y = MARGIN;
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, BODY);
        graphics.outline(x - 1, y - 1, WIDTH + 2, HEIGHT + 2, Palette.BORDER);
        boolean blinkOn = ((long) (d.blinkSeconds * 4.0)) % 2L == 0L;
        header(graphics, font, d, x, y);
        battery(graphics, font, d, x, y + 12, blinkOn);
        armed(graphics, font, d, x, y + 30);
        timer(graphics, font, d, mc.player, x, y + 42);
        status(graphics, font, d, x, y + 54, blinkOn);
        renderedFrames++;
    }

    private static void header(GuiGraphicsExtractor g, Font font, OsdData d, int x, int y) {
        g.fill(x, y, x + WIDTH, y + 10, HEADER_BAR);
        g.fill(x, y, x + 2, y + 10, Palette.ACCENT);
        g.fill(x, y + 10, x + WIDTH, y + 11, Palette.BORDER);
        String name = d.craftName == null || d.craftName.isEmpty() ? modelName() : d.craftName;
        int lq = (int) Math.round(Math.max(0.0, Math.min(1.0, d.linkStrength)) * 100.0);
        String link = "LQ " + lq;
        int bars = lq <= 0 ? 0 : Math.min(4, 1 + lq / 25);
        int barsX = x + WIDTH - 3 - 11;
        for (int i = 0; i < 4; i++) {
            int h = 2 + i * 2;
            int color = i < bars ? Palette.ACCENT : LINK_OFF;
            g.fill(barsX + i * 3, y + 9 - h, barsX + i * 3 + 2, y + 9, color);
        }
        int linkWidth = font.width(link);
        int linkX = barsX - 3 - linkWidth;
        g.text(font, link, linkX, y + 1, Palette.TEXT_MUTED, false);
        ThemeText.fitted(g, font, name, x + 5, y + 1, linkX - x - 8, Palette.TEXT);
    }

    private static String modelName() {
        PilotSession session = PilotSession.INSTANCE;
        if (session.drone() != null) {
            Component custom = session.drone().getCustomName();
            if (custom != null && !custom.getString().isBlank()) {
                return custom.getString();
            }
            return PartNames.frameShort(session.drone().build().frame()).getString();
        }
        return "";
    }

    private static void battery(GuiGraphicsExtractor g, Font font, OsdData d, int x, int y, boolean blinkOn) {
        String volts = Double.isFinite(d.packVoltage) ? String.format(Locale.ROOT, "%.1fV", d.packVoltage) : "--.-V";
        g.pose().pushMatrix();
        g.pose().translate(x + 3, y + 1);
        g.pose().scale(2.0F, 2.0F);
        g.text(font, volts, 0, 0, charge(d) < 0.2 ? Palette.ERROR : Palette.TEXT, false);
        g.pose().popMatrix();
        double charge = charge(d);
        String percent = Component.translatable("propwash.osd.editor.percent", Math.round(charge * 100.0)).getString();
        int right = x + WIDTH - 3;
        g.text(font, percent, right - font.width(percent), y + 1, Palette.TEXT_MUTED, false);
        int barX = right - 40;
        int barY = y + 11;
        g.fill(barX, barY, barX + 40, barY + 5, Palette.FIELD);
        g.outline(barX, barY, 40, 5, Palette.BORDER_STRONG);
        boolean show = charge >= 0.2 || blinkOn;
        int filled = (int) Math.round(38 * charge);
        if (show && filled > 0) {
            int fill = 0xFF000000 | Batteries.barColor((float) charge);
            g.fill(barX + 1, barY + 1, barX + 1 + filled, barY + 4, fill);
        }
    }

    private static double charge(OsdData d) {
        return Double.isFinite(d.chargeFraction) ? Math.max(0.0, Math.min(1.0, d.chargeFraction)) : 0.0;
    }

    private static void armed(GuiGraphicsExtractor g, Font font, OsdData d, int x, int y) {
        Component label = Component.translatable(KEY + (d.armed ? "armed" : "disarmed"));
        int width = font.width(label) + 4;
        g.fill(x + 2, y, x + 2 + width, y + 10, d.armed ? Palette.ERROR_BG : Palette.SUCCESS_BG);
        g.text(font, label, x + 4, y + 1, d.armed ? Palette.ERROR : Palette.SUCCESS, false);
        String modeKey = d.holdMode == HoldMode.OFF ? d.mode.name() : d.holdMode.name();
        String prefix = d.mode3d && d.holdMode == HoldMode.OFF ? "propwash.osd.mode_3d." : "propwash.osd.mode.";
        Component mode = d.turtle ? Component.translatable("propwash.osd.mode.turtle")
                : Component.translatable(prefix + modeKey.toLowerCase(Locale.ROOT));
        g.text(font, mode, x + WIDTH - 3 - font.width(mode), y + 1, Palette.ACCENT, false);
        if (d.mode3d) {
            Component throttle = Component.translatable("propwash.osd.label.throttle").append(" ")
                    .append(OsdFormat.signedPercentText(OsdFormat.signedPercent(d.throttle)));
            g.text(font, throttle, x + 2 + width + 6, y + 1, Palette.TEXT, false);
        }
    }

    private static void timer(GuiGraphicsExtractor g, Font font, OsdData d, LocalPlayer player, int x, int y) {
        long seconds = (long) Math.max(0.0, d.flightSeconds);
        String time = String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
        g.text(font, Component.translatable(KEY + "timer", time), x + 3, y + 1, Palette.TEXT, false);
        if (player != null && d.hasPosition) {
            double dx = d.x - player.getX();
            double dy = d.y - player.getY();
            double dz = d.z - player.getZ();
            long distance = Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
            Component text = Component.translatable(KEY + "distance", distance);
            g.text(font, text, x + WIDTH - 3 - font.width(text), y + 1, Palette.TEXT, false);
        }
    }

    private static void status(GuiGraphicsExtractor g, Font font, OsdData d, int x, int y, boolean blinkOn) {
        Component text;
        boolean keyboard = RadioState.INSTANCE.source() == RadioSource.KEYBOARD || !RadioState.INSTANCE.connected();
        if (!keyboard && d.linkStrength <= 0.0) {
            if (!blinkOn) {
                return;
            }
            text = Component.translatable(KEY + "no_link");
        } else if (d.rescueActive) {
            text = Component.translatable(KEY + "rescue", d.rescuePhase);
        } else if (keyboard) {
            text = Component.translatable(KEY + "keyboard");
        } else if (Double.isFinite(d.altitude)) {
            text = Component.translatable(KEY + "height", Math.round(d.altitude));
        } else {
            return;
        }
        ThemeText.fitted(g, font, text.getString(), x + 3, y + 1, WIDTH - 6, Palette.TEXT_MUTED);
    }
}
