package de.lardtom.propwash.video.client.record;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.ActionCamSpec;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.video.record.CamPower;
import de.lardtom.propwash.video.record.RecordingDiagnosis;
import de.lardtom.propwash.video.record.RecordingFormat;
import de.lardtom.propwash.video.record.RecordingJob;
import de.lardtom.propwash.video.record.external.HardwareEncoders;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RecordingHud {
    public static final Identifier ID = Constants.id("action_cam");
    public static final int MARGIN = 6;
    public static final int MIN_WIDTH = 120;
    public static final int MAX_TEXT_WIDTH = 250;
    public static final float LOW_CAMERA_CHARGE = 0.3F;
    public static final long ENCODER_NOTICE_NANOS = 6_000_000_000L;
    private static final float CRITICAL_CAMERA_CHARGE = 0.15F;
    private static final int LINE = 11;
    private static final int PAD = 4;
    private static final int INSET = PAD + 3;
    private static final int DOT = 9;
    private static final int BODY = Palette.withAlpha(Palette.PANEL, 0xD8);
    private static int renderedFrames;
    private static int noticeFrames;
    private static int savingFrames;
    private static int flashbackFrames;

    private RecordingHud() {
    }

    public static int renderedFrames() {
        return renderedFrames;
    }

    public static int noticeFrames() {
        return noticeFrames;
    }

    public static int savingFrames() {
        return savingFrames;
    }

    public static int flashbackFrames() {
        return flashbackFrames;
    }

    public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (!RecordingConfig.get().showIndicator) {
            return;
        }
        FlashbackCam flashback = FlashbackCam.INSTANCE;
        if (flashback.recording() && (!PilotSession.INSTANCE.isFpv() || flashback.paused())) {
            flashbackPanel(graphics, Minecraft.getInstance().font, flashback, MARGIN);
            flashbackFrames++;
            return;
        }
        ActionCamRecorder recorder = ActionCamRecorder.INSTANCE;
        List<RecordingJob.Progress> saving = PilotSession.INSTANCE.isFpv() ? List.of() : recorder.saving();
        List<Component> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        if (recorder.phase() == ActionCamRecorder.Phase.RECORDING) {
            notices(recorder, lines, colors);
        }
        if (lines.isEmpty() && saving.isEmpty()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        int y = MARGIN;
        if (!lines.isEmpty()) {
            y = noticePanel(graphics, font, recorder, lines, colors, y) + 3;
            noticeFrames++;
        }
        if (!saving.isEmpty()) {
            savingPanel(graphics, font, saving, y);
            savingFrames++;
        }
        renderedFrames++;
    }

    public static List<Component> notices(ActionCamRecorder recorder) {
        List<Component> lines = new ArrayList<>();
        notices(recorder, lines, new ArrayList<>());
        return lines;
    }

    private static void notices(ActionCamRecorder recorder, List<Component> lines, List<Integer> colors) {
        AccessoryType camera = recorder.camera();
        ActionCamSpec spec = camera == null ? null : camera.camera().orElse(null);
        RecordingFormat format = recorder.format();
        if (recorder.recordedNanos() < ENCODER_NOTICE_NANOS) {
            lines.add(encoderLine(recorder.hardwareRecording(), recorder.startStatus()));
            colors.add(Palette.TEXT_MUTED);
        }
        if (recorder.paused()) {
            lines.add(Component.translatable("propwash.video.record.hud.paused"));
            colors.add(Palette.WARNING);
        }
        RecordingDiagnosis diagnosis = recorder.diagnosis();
        if (!recorder.paused() && diagnosis.limit() != RecordingDiagnosis.Limit.NONE) {
            lines.add(limitLine(diagnosis));
            colors.add(diagnosis.losing() ? Palette.ERROR : Palette.WARNING);
            if (diagnosis.limit() == RecordingDiagnosis.Limit.ENCODER) {
                lines.add(Component.translatable("propwash.video.record.hud.tip.encoder", Math.max(1L, Math.round(diagnosis.saveSeconds()))));
                colors.add(Palette.TEXT_MUTED);
            }
            Component tip = tipLine(diagnosis, format);
            if (tip != null) {
                lines.add(tip);
                colors.add(Palette.TEXT_MUTED);
            }
        }
        if (spec != null && spec.ownBattery()) {
            float charge = recorder.cameraCharge();
            if (charge < LOW_CAMERA_CHARGE) {
                int height = format == null ? RecordingFormat.DEFAULT.height() : format.height();
                long minutes = Math.max(0L, Math.round(CamPower.secondsLeft(charge, spec, height) / 60.0));
                lines.add(Component.translatable("propwash.video.record.hud.battery", Math.round(charge * 100.0F), minutes));
                colors.add(charge < CRITICAL_CAMERA_CHARGE ? Palette.ERROR : Palette.WARNING);
            }
        }
    }

    public static Component encoderLine(boolean hardware, HardwareEncoders.Status status) {
        String key = "propwash.video.record.hud.encoder.";
        if (hardware && status.codec() != null) {
            return Component.translatable(key + "hardware", status.codec().label());
        }
        return switch (status.state()) {
            case NO_FFMPEG -> Component.translatable(key + "no_ffmpeg");
            case CHECKING -> Component.translatable(key + "checking");
            case NO_HARDWARE, UNUSABLE, BROKEN -> Component.translatable(key + "no_hardware");
            case OFF, READY -> Component.translatable(key + "builtin");
        };
    }

    public static Component encoderShort(boolean hardware, HardwareEncoders.Status status) {
        if (hardware && status.codec() != null) {
            return Component.literal(status.codec().label());
        }
        return Component.translatable("propwash.video.record.hud.encoder.short.builtin");
    }

    public static Component limitLine(RecordingDiagnosis diagnosis) {
        String key = "propwash.video.record.hud.limit." + diagnosis.limit().key();
        return switch (diagnosis.limit()) {
            case GAME -> Component.translatable(key, Math.round(diagnosis.gameFps()), diagnosis.targetFps());
            case ENCODER -> Component.translatable(key, Math.round(diagnosis.encoderFps()), diagnosis.targetFps());
            case READBACK, DISK, MEMORY -> Component.translatable(key, diagnosis.lostFrames());
            case NONE -> Component.empty();
        };
    }

    public static @Nullable Component tipLine(RecordingDiagnosis diagnosis, @Nullable RecordingFormat format) {
        RecordingDiagnosis.Tip tip = diagnosis.tip();
        if (tip == RecordingDiagnosis.Tip.NONE) {
            return null;
        }
        String key = "propwash.video.record.hud.tip." + tip.key();
        if (!tip.suggestsLower()) {
            return Component.translatable(key);
        }
        Component lower = lowerText(format);
        if (tip == RecordingDiagnosis.Tip.LOWER) {
            return lower != null ? Component.translatable(key, lower) : Component.translatable("propwash.video.record.hud.tip.busy");
        }
        return lower != null ? Component.translatable(key + ".or_lower", lower) : Component.translatable(key);
    }

    private static @Nullable Component lowerText(@Nullable RecordingFormat format) {
        if (format == null) {
            return null;
        }
        Component size = Component.literal(RecordingDiagnosis.Lower.lowerHeight(format) + "p");
        Component rate = Component.literal(RecordingFormat.RATES.getFirst() + " fps");
        return switch (RecordingDiagnosis.Lower.of(format)) {
            case NONE -> null;
            case RESOLUTION -> size;
            case FRAME_RATE -> rate;
            case BOTH -> Component.translatable("propwash.video.record.hud.lower.both", size, rate);
        };
    }

    private static int noticePanel(GuiGraphicsExtractor g, Font font, ActionCamRecorder recorder, List<Component> lines, List<Integer> colors, int y) {
        RecordingFormat format = recorder.format();
        boolean paused = recorder.paused();
        long seconds = recorder.recordedNanos() / 1_000_000_000L;
        Component rec = Component.translatable("propwash.video.record.hud.rec",
                String.format(Locale.ROOT, "%02d:%02d", seconds / 60L, seconds % 60L));
        Component label = format == null ? Component.empty()
                : Component.translatable("propwash.video.record.hud.format", format.label(),
                encoderShort(recorder.hardwareRecording(), recorder.startStatus()));
        int maxText = Math.max(MIN_WIDTH, Math.min(MAX_TEXT_WIDTH, g.guiWidth() - 2 * MARGIN - INSET - PAD));
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        List<Integer> wrappedColors = new ArrayList<>();
        int width = DOT + font.width(rec) + 12 + font.width(label);
        for (int i = 0; i < lines.size(); i++) {
            for (FormattedCharSequence part : ThemeText.balanced(font, lines.get(i), maxText)) {
                wrapped.add(part);
                wrappedColors.add(colors.get(i));
                width = Math.max(width, font.width(part));
            }
        }
        width = Math.max(MIN_WIDTH, width + INSET + PAD);
        int x = (g.guiWidth() - width) / 2;
        int height = PAD * 2 + (1 + wrapped.size()) * LINE - 2;
        g.fill(x, y, x + width, y + height, BODY);
        g.outline(x - 1, y - 1, width + 2, height + 2, Palette.BORDER);
        g.fill(x, y, x + 2, y + height, ActionCamRecorder.REC_ACCENT);
        int textX = x + INSET;
        int rowY = y + PAD;
        boolean blink = (Util.getMillis() / 500L) % 2L == 0L;
        if (paused) {
            g.fill(textX, rowY + 1, textX + 2, rowY + 7, Palette.WARNING);
            g.fill(textX + 4, rowY + 1, textX + 6, rowY + 7, Palette.WARNING);
        } else if (blink) {
            g.fill(textX, rowY + 1, textX + 6, rowY + 7, ActionCamRecorder.REC_ACCENT);
        }
        g.text(font, rec, textX + DOT, rowY, paused ? Palette.WARNING : Palette.TEXT, false);
        g.text(font, label, x + width - PAD - font.width(label), rowY, Palette.TEXT_MUTED, false);
        for (int i = 0; i < wrapped.size(); i++) {
            rowY += LINE;
            g.text(font, wrapped.get(i), textX, rowY, wrappedColors.get(i), false);
        }
        return y + height;
    }

    private static void flashbackPanel(GuiGraphicsExtractor g, Font font, FlashbackCam cam, int y) {
        boolean paused = cam.paused();
        long seconds = Math.round(cam.recordedSeconds());
        Component rec = Component.translatable("propwash.video.flashback.hud.rec",
                String.format(Locale.ROOT, "%02d:%02d", seconds / 60L, seconds % 60L));
        Component label = Component.translatable("propwash.video.flashback.hud.label");
        Component notice = paused ? Component.translatable("propwash.video.flashback.hud.paused") : null;
        int width = DOT + font.width(rec) + 12 + font.width(label);
        if (notice != null) {
            width = Math.max(width, font.width(notice));
        }
        width = Math.max(MIN_WIDTH, width + INSET + PAD);
        int x = (g.guiWidth() - width) / 2;
        int height = PAD * 2 + (notice == null ? 1 : 2) * LINE - 2;
        g.fill(x, y, x + width, y + height, BODY);
        g.outline(x - 1, y - 1, width + 2, height + 2, Palette.BORDER);
        g.fill(x, y, x + 2, y + height, ActionCamRecorder.REC_ACCENT);
        int textX = x + INSET;
        int rowY = y + PAD;
        boolean blink = (Util.getMillis() / 500L) % 2L == 0L;
        if (paused) {
            g.fill(textX, rowY + 1, textX + 2, rowY + 7, Palette.WARNING);
            g.fill(textX + 4, rowY + 1, textX + 6, rowY + 7, Palette.WARNING);
        } else if (blink) {
            g.fill(textX, rowY + 1, textX + 6, rowY + 7, ActionCamRecorder.REC_ACCENT);
        }
        g.text(font, rec, textX + DOT, rowY, paused ? Palette.WARNING : Palette.TEXT, false);
        g.text(font, label, x + width - PAD - font.width(label), rowY, Palette.TEXT_MUTED, false);
        if (notice != null) {
            g.text(font, notice, textX, rowY + LINE, Palette.WARNING, false);
        }
    }

    private static void savingPanel(GuiGraphicsExtractor g, Font font, List<RecordingJob.Progress> saving, int y) {
        RecordingJob.Progress first = saving.getFirst();
        int percent = Math.round(first.fraction() * 100.0F);
        Component text = saving.size() > 1
                ? Component.translatable("propwash.video.record.hud.saving_more", percent, saving.size() - 1)
                : Component.translatable("propwash.video.record.hud.saving", percent);
        int width = Math.max(MIN_WIDTH, font.width(text) + INSET + PAD);
        int x = (g.guiWidth() - width) / 2;
        int height = PAD * 2 + LINE + 6;
        g.fill(x, y, x + width, y + height, BODY);
        g.outline(x - 1, y - 1, width + 2, height + 2, Palette.BORDER);
        g.fill(x, y, x + 2, y + height, Palette.ACCENT);
        int textX = x + INSET;
        g.text(font, text, textX, y + PAD, Palette.TEXT, false);
        int barY = y + PAD + LINE;
        int barWidth = width - INSET - PAD;
        g.fill(textX, barY, textX + barWidth, barY + 4, Palette.FIELD);
        int filled = Math.round(barWidth * first.fraction());
        if (filled > 0) {
            g.fill(textX, barY, textX + filled, barY + 4, Palette.ACCENT);
        }
    }
}
