package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.drone.PilotBodyAlert;
import de.lardtom.propwash.ui.client.theme.Palette;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;

public final class PilotBodyHud {
    public static final Identifier ID = Constants.id("pilot_body");
    public static final int EDGE_STEPS = 12;
    public static final float EDGE_SHARE = 0.12F;
    public static final float EDGE_ALPHA = 0.7F;
    public static final int EDGE_RED = 0xD01E1E;
    public static final float HIT_VOLUME = 0.8F;
    public static final int BOTTOM_GAP = 46;
    private static final int PAD = 4;
    private static final int INSET = PAD + 3;
    private static final int BAR = 3;
    private static final int LINE = 11;
    private static final int BODY = Palette.withAlpha(Palette.PANEL, 0xD8);
    private static final int AIR_BLUE = 0xFF4EA1F3;
    private static final PilotBodyAlert ALERT = new PilotBodyAlert();
    private static int hitSounds;
    private static int flashFrames;
    private static int noticeFrames;
    private static PilotBodyAlert.Notice lastNotice = PilotBodyAlert.Notice.NONE;

    private PilotBodyHud() {
    }

    public static void onFrame(Minecraft mc, DeltaTracker deltaTracker) {
        LocalPlayer player = mc.player;
        if (player == null || !watching()) {
            ALERT.reset();
            lastNotice = PilotBodyAlert.Notice.NONE;
            return;
        }
        long now = Util.getNanos();
        boolean hit = ALERT.update(player.getHealth(), player.getAbsorptionAmount(), player.getMaxHealth(), player.getAirSupply(),
                player.getMaxAirSupply(), now);
        if (hit) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_HURT, 1.0F, HIT_VOLUME));
            hitSounds++;
        }
    }

    public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (!watching() || !ALERT.known()) {
            return;
        }
        long now = Util.getNanos();
        float flash = ALERT.flash(now);
        if (flash > 0.0F) {
            edges(graphics, flash);
            flashFrames++;
        }
        PilotBodyAlert.Notice notice = ALERT.notice(now);
        lastNotice = notice;
        if (notice == PilotBodyAlert.Notice.NONE) {
            return;
        }
        float alpha = ALERT.noticeAlpha(now);
        if (alpha <= 0.0F) {
            return;
        }
        card(graphics, Minecraft.getInstance().font, notice, alpha);
        noticeFrames++;
    }

    public static boolean watching() {
        return PilotSession.INSTANCE.isFpv() || FpvView.spectating();
    }

    private static void edges(GuiGraphicsExtractor g, float strength) {
        int w = g.guiWidth();
        int h = g.guiHeight();
        int depth = Math.max(EDGE_STEPS, Math.round(Math.min(w, h) * EDGE_SHARE));
        for (int i = 0; i < EDGE_STEPS; i++) {
            float fade = 1.0F - (float) i / EDGE_STEPS;
            int alpha = Math.round(255.0F * strength * fade * fade * EDGE_ALPHA);
            if (alpha <= 0) {
                continue;
            }
            int color = alpha << 24 | EDGE_RED;
            int a = depth * i / EDGE_STEPS;
            int b = depth * (i + 1) / EDGE_STEPS;
            g.fill(0, a, w, b, color);
            g.fill(0, h - b, w, h - a, color);
            g.fill(a, b, b, h - b, color);
            g.fill(w - b, b, w - a, h - b, color);
        }
    }

    private static void card(GuiGraphicsExtractor g, Font font, PilotBodyAlert.Notice notice, float alpha) {
        Component title = switch (notice) {
            case CRITICAL -> Component.translatable("propwash.drone.body.critical");
            case AIR -> Component.translatable("propwash.drone.body.air", ALERT.airPercent());
            default -> Component.translatable("propwash.drone.body.hit");
        };
        int accent = notice == PilotBodyAlert.Notice.AIR ? AIR_BLUE : Palette.ERROR;
        int width = Math.max(120, font.width(title) + INSET + PAD);
        int height = PAD * 2 + LINE + 2 + BAR;
        int x = (g.guiWidth() - width) / 2;
        int y = g.guiHeight() - BOTTOM_GAP - height;
        g.fill(x, y, x + width, y + height, fade(BODY, alpha));
        g.outline(x - 1, y - 1, width + 2, height + 2, fade(Palette.BORDER, alpha));
        g.fill(x, y, x + 2, y + height, fade(accent, alpha));
        int textX = x + INSET;
        g.text(font, title, textX, y + PAD, fade(notice == PilotBodyAlert.Notice.HIT ? Palette.TEXT : accent, alpha), false);
        int barY = y + PAD + LINE + 1;
        int barW = width - INSET - PAD;
        g.fill(textX, barY, textX + barW, barY + BAR, fade(Palette.FIELD, alpha));
        float share = notice == PilotBodyAlert.Notice.AIR ? ALERT.airPercent() / 100.0F : ALERT.healthShare();
        int filled = Math.round(barW * Math.clamp(share, 0.0F, 1.0F));
        if (filled > 0) {
            g.fill(textX, barY, textX + filled, barY + BAR, fade(accent, alpha));
        }
    }

    private static int fade(int color, float alpha) {
        int a = Math.round(((color >>> 24) & 0xFF) * Math.clamp(alpha, 0.0F, 1.0F));
        return a << 24 | (color & 0xFFFFFF);
    }

    public static PilotBodyAlert alert() {
        return ALERT;
    }

    public static int hitSounds() {
        return hitSounds;
    }

    public static int flashFrames() {
        return flashFrames;
    }

    public static int noticeFrames() {
        return noticeFrames;
    }

    public static PilotBodyAlert.Notice lastNotice() {
        return lastNotice;
    }
}
