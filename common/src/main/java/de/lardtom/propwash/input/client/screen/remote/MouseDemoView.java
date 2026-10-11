package de.lardtom.propwash.input.client.screen.remote;

import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.client.render.RemoteGimbalRenderer;
import de.lardtom.propwash.input.MouseAxis;
import de.lardtom.propwash.input.MouseDemo;
import de.lardtom.propwash.input.MouseSettings;
import de.lardtom.propwash.input.MouseSticks;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.input.client.InputKeys;
import de.lardtom.propwash.ui.client.theme.GuiLayerAccess;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class MouseDemoView {
    public static final int MIN_WIDTH = 96;
    public static final int MIN_HEIGHT = 72;
    private static final int PADDING = 4;
    private static final int CAPTION_LINES = 2;
    private static final float PAD_HALF_CM = 6.0F;

    private final ItemStackRenderState item = new ItemStackRenderState();
    private @Nullable ItemStack stack;
    private long startNanos = Util.getNanos();
    private float frozenSeconds = Float.NaN;
    private MouseDemo.@Nullable Frame lastFrame;
    private boolean drawn;
    private long frames;

    public void restart() {
        this.startNanos = Util.getNanos();
    }

    public void freezeAt(float seconds) {
        this.frozenSeconds = seconds;
    }

    public float seconds() {
        if (!Float.isNaN(this.frozenSeconds)) {
            return this.frozenSeconds;
        }
        return (Util.getNanos() - this.startNanos) / 1.0E9F;
    }

    public MouseDemo.@Nullable Frame lastFrame() {
        return this.lastFrame;
    }

    public boolean drawn() {
        return this.drawn;
    }

    public long frames() {
        return this.frames;
    }

    public static boolean fits(int width, int height) {
        return width >= MIN_WIDTH && height >= MIN_HEIGHT;
    }

    public void extract(GuiGraphicsExtractor g, Font font, int x0, int y0, int x1, int y1, MouseSettings settings, boolean selfLevel,
                        @Nullable StickPositions live) {
        this.drawn = false;
        if (!fits(x1 - x0, y1 - y0)) {
            return;
        }
        ThemeDraw.previewStage(g, x0, y0, x1, y1);
        Component title = Component.translatable(live != null ? "propwash.input.mouse.demo.live_title" : "propwash.input.mouse.demo.title");
        ThemeText.fitted(g, font, title, x0 + PADDING, y0 + PADDING, x1 - x0 - 2 * PADDING, Palette.TEXT_FAINT);
        int width = x1 - x0 - 2 * PADDING;
        int captionTop = y1 - PADDING - CAPTION_LINES * Metrics.LINE_HEIGHT + 2;
        int stageTop = y0 + PADDING + Metrics.LINE_HEIGHT;
        int stageBottom = captionTop - 3;
        if (stageBottom - stageTop < 30) {
            return;
        }
        MouseDemo.Frame frame = MouseDemo.frame(settings, selfLevel, this.seconds());
        this.lastFrame = frame;
        MouseSticks.Output out = frame.sticks();
        StickPositions sticks = live != null ? live : new StickPositions(out.roll(), out.pitch(), out.yaw(), 0.0F);
        int padSize = Math.min((x1 - x0) / 3, stageBottom - stageTop);
        int padX1 = x1 - PADDING;
        int padX0 = padX1 - padSize;
        int padY0 = stageTop + (stageBottom - stageTop - padSize) / 2;
        int remoteX1 = padX0 - PADDING;
        if (remoteX1 - x0 > 40) {
            RemoteProjection projection = RemoteProjection.fit(RemoteProjection.Region.WHOLE, x0 + PADDING, stageTop, remoteX1, stageBottom)
                    .centeredAt((x0 + 1 + remoteX1) * 0.5F, (stageTop + stageBottom) * 0.5F);
            if (this.submit(g, sticks, projection, x0 + 1, stageTop, remoteX1, stageBottom)) {
                this.drawn = true;
                this.frames++;
            }
        }
        float mx = live != null ? 0.0F : frame.mouseX();
        float my = live != null ? 0.0F : frame.mouseY();
        boolean moving = live == null && frame.cue().moving();
        mousePad(g, padX0, padY0, padX0 + padSize, padY0 + padSize, mx, my, moving);
        Component caption = live != null ? Component.translatable("propwash.input.mouse.demo.live") : caption(frame.cue(), settings, out);
        List<FormattedCharSequence> lines = font.split(caption, width);
        int count = ThemeText.shownLines(caption, lines.size(), CAPTION_LINES);
        int y = captionTop + (CAPTION_LINES - count) * Metrics.LINE_HEIGHT / 2;
        int color = frame.cue().moving() && live == null ? Palette.ACCENT : Palette.TEXT_MUTED;
        for (int i = 0; i < count; i++) {
            FormattedCharSequence line = lines.get(i);
            g.text(font, line, (x0 + x1) / 2 - font.width(line) / 2, y + i * Metrics.LINE_HEIGHT, color, false);
        }
    }

    public static Component caption(MouseDemo.Cue cue, MouseSettings settings, MouseSticks.Output out) {
        String base = cue.translationKey();
        return switch (cue) {
            case RIGHT -> Component.translatable(base + (settings.xAxis == MouseAxis.YAW ? ".yaw" : ".roll"));
            case FORWARD -> Component.translatable(base + (settings.invertY ? ".inverted" : ".normal"));
            case STOP_SIDE, STOP_FORWARD -> {
                boolean hold = cue == MouseDemo.Cue.STOP_SIDE ? out.holdX() : out.holdY();
                yield Component.translatable(hold ? (settings.returnSpeed > 0.0F ? base + ".hold_return" : base + ".hold") : base + ".rate");
            }
            case STOP_END -> Component.translatable(base, InputKeys.label(InputKeys.THROTTLE_UP), InputKeys.label(InputKeys.THROTTLE_DOWN),
                    InputKeys.label(InputKeys.YAW_LEFT), InputKeys.label(InputKeys.YAW_RIGHT),
                    Component.translatable(settings.xAxis == MouseAxis.YAW ? "propwash.input.mouse.demo.roll_word" : "propwash.input.mouse.demo.yaw_word"));
            default -> Component.translatable(base);
        };
    }

    private boolean submit(GuiGraphicsExtractor g, StickPositions sticks, RemoteProjection projection, int x0, int y0, int x1, int y1) {
        if (!(g instanceof GuiLayerAccess access)) {
            return false;
        }
        ItemStack remote = this.stack();
        if (remote.isEmpty()) {
            return false;
        }
        RemoteGimbalRenderer.Pose pose = RemoteGimbalRenderer.Pose.of(sticks);
        Minecraft minecraft = Minecraft.getInstance();
        RemoteGimbalRenderer.extractPosed(remote, pose,
                () -> minecraft.getItemModelResolver().updateForTopItem(this.item, remote, ItemDisplayContext.NONE, minecraft.level, null, 0));
        access.propwash$renderState().addPicturesInPictureState(new RemoteDemoRenderState(this.item, projection, x0, y0, x1, y1, access.propwash$scissor()));
        return true;
    }

    private ItemStack stack() {
        if (this.stack == null) {
            this.stack = DroneModule.REMOTE.isBound() ? new ItemStack(DroneModule.REMOTE.get()) : ItemStack.EMPTY;
        }
        return this.stack;
    }

    static void mousePad(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, float cmX, float cmY, boolean moving) {
        ThemeDraw.framedBox(g, x0, y0, x1, y1, Palette.withAlpha(Palette.FIELD, 0xE0), Palette.BORDER);
        int cx = (x0 + x1) / 2;
        int cy = (y0 + y1) / 2;
        g.fill(x0 + 3, cy, x1 - 3, cy + 1, Palette.withAlpha(Palette.GRID, 0x90));
        g.fill(cx, y0 + 3, cx + 1, y1 - 3, Palette.withAlpha(Palette.GRID, 0x90));
        int size = x1 - x0;
        int bodyW = Math.max(6, size / 6);
        int bodyH = Math.round(bodyW * 1.5F);
        float pixelsPerCm = (size / 2.0F - bodyH / 2.0F - 2.0F) / PAD_HALF_CM;
        int mx = cx + Math.round(cmX * pixelsPerCm);
        int my = cy - Math.round(cmY * pixelsPerCm);
        int bx0 = mx - bodyW / 2;
        int by0 = my - bodyH / 2;
        int edge = moving ? Palette.ACCENT : Palette.TEXT_MUTED;
        g.fill(bx0 + 1, by0, bx0 + bodyW - 1, by0 + bodyH, edge);
        g.fill(bx0, by0 + 1, bx0 + bodyW, by0 + bodyH - 1, edge);
        g.fill(bx0 + 1, by0 + 1, bx0 + bodyW - 1, by0 + bodyH - 1, Palette.BUTTON);
        int split = by0 + bodyH * 2 / 5;
        g.fill(bx0 + 1, split, bx0 + bodyW - 1, split + 1, edge);
        g.fill(mx, by0 + 1, mx + 1, split, edge);
        g.fill(mx - 1, by0 + 2, mx + 1, by0 + 2 + Math.max(2, bodyH / 6), Palette.ACCENT);
    }
}
