package de.lardtom.propwash.input.client.screen.remote;

import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.client.render.RemoteGimbalModel;
import de.lardtom.propwash.drone.client.render.RemoteGimbalRenderer;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.RemoteDemo;
import de.lardtom.propwash.input.StickMode;
import de.lardtom.propwash.input.client.screen.InputGui;
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

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class RemoteDemoView {
    public static final int MIN_WIDTH = 64;
    public static final int MIN_HEIGHT = 56;
    private static final int PADDING = 4;
    private static final float PLATE_RADIUS = 2.1F;
    private static final float SAMPLE_STEP = 0.1F;

    private final ItemStackRenderState item = new ItemStackRenderState();
    private @Nullable ItemStack stack;
    private RemoteDemo.Target target = new RemoteDemo.Target(RemoteDemo.Motion.THROTTLE, 0);
    private long startNanos = Util.getNanos();
    private float frozenSeconds = Float.NaN;
    private RemoteDemo.Frame lastFrame = RemoteDemo.frame(this.target, 0.0F);
    private boolean drawn;
    private long frames;
    private @Nullable Set<RemoteDemo.Cue> cues;
    private int captionWidth = -1;
    private int captionLines = 1;
    private @Nullable StickMode mode;

    public void show(RemoteDemo.Target next) {
        if (!next.equals(this.target)) {
            this.target = next;
            this.startNanos = Util.getNanos();
            this.cues = null;
            this.captionWidth = -1;
        }
    }

    public void show(RemoteDemo.Motion motion) {
        this.show(new RemoteDemo.Target(motion, 0));
    }

    public void mode(@Nullable StickMode next) {
        this.mode = next;
    }

    public StickMode mode() {
        return this.mode != null ? this.mode : InputConfig.get().stickMode();
    }

    public RemoteDemo.Target target() {
        return this.target;
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

    public RemoteDemo.Frame lastFrame() {
        return this.lastFrame;
    }

    public void beginFrame() {
        this.drawn = false;
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

    public void extract(GuiGraphicsExtractor g, Font font, int x0, int y0, int x1, int y1) {
        if (!fits(x1 - x0, y1 - y0)) {
            return;
        }
        RemoteDemo.Frame frame = RemoteDemo.frame(this.target, this.seconds(), this.mode());
        this.lastFrame = frame;
        ThemeDraw.previewStage(g, x0, y0, x1, y1);
        Component title = Component.translatable("propwash.input.demo.title");
        ThemeText.fitted(g, font, title, x0 + PADDING, y0 + PADDING, x1 - x0 - 2 * PADDING, Palette.TEXT_FAINT);
        int width = x1 - x0 - 2 * PADDING;
        int lines = this.captionLines(font, width);
        int captionTop = y1 - PADDING - lines * Metrics.LINE_HEIGHT + 2;
        int barY = captionTop - 4;
        int stageTop = y0 + PADDING + Metrics.LINE_HEIGHT;
        int stageBottom = barY - 2;
        if (stageBottom - stageTop < 24) {
            return;
        }
        RemoteProjection.Region region = switch (frame.focus()) {
            case SWITCH -> RemoteProjection.Region.SWITCHES;
            case BUTTON -> RemoteProjection.Region.BUTTONS;
            default -> RemoteProjection.Region.WHOLE;
        };
        int boxX0 = x0 + 1;
        int boxX1 = x1 - 1;
        RemoteProjection projection = RemoteProjection.fit(region, x0 + PADDING, stageTop, x1 - PADDING, stageBottom)
                .centeredAt((boxX0 + boxX1) * 0.5F, (stageTop + stageBottom) * 0.5F);
        if (this.submit(g, frame, projection, boxX0, stageTop, boxX1, stageBottom)) {
            this.drawn = true;
            this.frames++;
        }
        g.enableScissor(boxX0, stageTop, boxX1, stageBottom);
        try {
            this.overlay(g, frame, projection);
        } finally {
            g.disableScissor();
        }
        this.caption(g, font, frame, x0, x1, captionTop, barY, width);
    }

    private int captionLines(Font font, int width) {
        if (width == this.captionWidth) {
            return this.captionLines;
        }
        if (this.cues == null) {
            Set<RemoteDemo.Cue> found = EnumSet.noneOf(RemoteDemo.Cue.class);
            for (float t = 0.0F; t < this.target.motion().period(); t += SAMPLE_STEP) {
                found.add(RemoteDemo.frame(this.target, t).cue());
            }
            found.remove(RemoteDemo.Cue.NONE);
            this.cues = found;
        }
        int lines = 1;
        for (RemoteDemo.Cue cue : this.cues) {
            lines = Math.max(lines, Math.min(2, font.split(Component.translatable(cue.translationKey()), width).size()));
        }
        this.captionWidth = width;
        this.captionLines = lines;
        return lines;
    }

    private boolean submit(GuiGraphicsExtractor g, RemoteDemo.Frame frame, RemoteProjection projection, int x0, int y0, int x1, int y1) {
        if (!(g instanceof GuiLayerAccess access)) {
            return false;
        }
        ItemStack remote = this.stack();
        if (remote.isEmpty()) {
            return false;
        }
        RemoteGimbalRenderer.Pose pose = new RemoteGimbalRenderer.Pose(frame.leftX(), frame.leftY(), frame.rightX(), frame.rightY(),
                new RemoteGimbalRenderer.Controls(frame.switch0(), frame.switch1(), frame.switch2(), frame.switch3(), frame.button0(),
                        frame.button1(), frame.button2()));
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

    private void overlay(GuiGraphicsExtractor g, RemoteDemo.Frame frame, RemoteProjection p) {
        int color = frame.channel() == null ? Palette.ACCENT : InputGui.channelColor(frame.channel());
        float pulse = 0.65F + 0.35F * (float) Math.sin(this.seconds() * 6.0F);
        int ring = Palette.withAlpha(color, Math.round(255 * pulse));
        switch (frame.focus()) {
            case LEFT_STICK -> this.stickOverlay(g, p, RemoteGimbalModel.LEFT_X, frame.leftX(), frame.leftY(), frame.arrow(), ring, color);
            case RIGHT_STICK -> this.stickOverlay(g, p, RemoteGimbalModel.RIGHT_X, frame.rightX(), frame.rightY(), frame.arrow(), ring, color);
            case BOTH_STICKS -> {
                this.stickOverlay(g, p, RemoteGimbalModel.LEFT_X, frame.leftX(), frame.leftY(), frame.arrow(), ring, color);
                this.stickOverlay(g, p, RemoteGimbalModel.RIGHT_X, frame.rightX(), frame.rightY(), frame.arrow(), ring, color);
            }
            case SWITCH -> this.switchOverlay(g, p, frame, ring, color);
            case BUTTON -> this.buttonOverlay(g, p, frame, ring, color);
            case NONE -> {
            }
        }
    }

    private void stickOverlay(GuiGraphicsExtractor g, RemoteProjection p, float pivotX, float stickX, float stickY, RemoteDemo.Arrow arrow,
                              int ring, int color) {
        float cx = p.screenX(pivotX, RemoteGimbalModel.PIVOT_Y, RemoteGimbalModel.PIVOT_Z);
        float cy = p.screenY(pivotX, RemoteGimbalModel.PIVOT_Y, RemoteGimbalModel.PIVOT_Z);
        float rx = PLATE_RADIUS * p.pixelScale();
        float ry = rx * (float) Math.cos(Math.toRadians(p.tiltDegrees()));
        outline(g, Math.round(cx - rx), Math.round(cy - ry), Math.round(cx + rx), Math.round(cy + ry), ring);
        float[] tip = p.stickTip(pivotX, RemoteGimbalModel.PIVOT_Y, RemoteGimbalModel.PIVOT_Z, RemoteGimbalModel.STICK_LENGTH, stickX, stickY);
        int dotX = Math.round(tip[0]);
        int dotY = Math.round(tip[1]);
        g.fill(dotX - 2, dotY - 2, dotX + 3, dotY + 3, Palette.withAlpha(Palette.SHADOW, 0xC0));
        g.fill(dotX - 1, dotY - 1, dotX + 2, dotY + 2, color);
        int size = Math.max(2, Math.round(p.pixelScale() * 0.7F));
        int gap = 2;
        switch (arrow) {
            case UP -> triangle(g, Math.round(cx), Math.round(cy - ry) - gap - size, size, 0, color);
            case DOWN -> triangle(g, Math.round(cx), Math.round(cy + ry) + gap + size, size, 2, color);
            case RIGHT -> triangle(g, Math.round(cx + rx) + gap + size, Math.round(cy), size, 1, color);
            default -> {
            }
        }
    }

    private void switchOverlay(GuiGraphicsExtractor g, RemoteProjection p, RemoteDemo.Frame frame, int ring, int color) {
        int index = Math.clamp(frame.target(), 0, RemoteGimbalModel.SWITCH_X.length - 1);
        float x = RemoteGimbalModel.SWITCH_X[index];
        float position = frame.switchPosition(index);
        int cx = Math.round(p.screenX(x, RemoteGimbalModel.SWITCH_Y, RemoteGimbalModel.SWITCH_Z));
        float baseY = p.screenY(x, RemoteGimbalModel.SWITCH_Y, RemoteGimbalModel.SWITCH_Z);
        float topY = p.screenY(x, RemoteGimbalModel.SWITCH_Y + RemoteGimbalModel.SWITCH_LENGTH, RemoteGimbalModel.SWITCH_Z);
        int r = Math.max(3, Math.round(p.pixelScale() * 0.75F));
        int top = Math.round(topY) - r;
        int bottom = Math.round(baseY) + r / 2;
        outline(g, cx - r, top, cx + r + 1, bottom, ring);
        int trackX = cx + r + 3;
        g.fill(trackX, top, trackX + 3, bottom, Palette.withAlpha(Palette.FIELD, 0xE0));
        g.fill(trackX + 1, top + 1, trackX + 2, bottom - 1, Palette.withAlpha(Palette.TEXT_FAINT, 0x80));
        float fraction = (position + 1.0F) * 0.5F;
        int knob = Math.round(top + 1 + (bottom - top - 5) * fraction);
        g.fill(trackX - 1, knob, trackX + 4, knob + 3, color);
        if (frame.arrow() == RemoteDemo.Arrow.FLIP) {
            triangle(g, cx, top - 2 - 3, Math.max(2, Math.round(p.pixelScale() * 0.5F)), 2, color);
        }
    }

    private void buttonOverlay(GuiGraphicsExtractor g, RemoteProjection p, RemoteDemo.Frame frame, int ring, int color) {
        int index = Math.clamp(frame.target(), 0, RemoteGimbalModel.BUTTON_X.length - 1);
        float x = RemoteGimbalModel.BUTTON_X[index];
        float cx = p.screenX(x, RemoteGimbalModel.BUTTON_Y, RemoteGimbalModel.BUTTON_Z);
        float cy = p.screenY(x, RemoteGimbalModel.BUTTON_Y, RemoteGimbalModel.BUTTON_Z);
        int r = Math.max(3, Math.round(p.pixelScale() * 0.85F));
        boolean pressed = frame.buttonPress(index) > 0.5F;
        if (pressed) {
            g.fill(Math.round(cx) - r + 1, Math.round(cy) - r + 1, Math.round(cx) + r, Math.round(cy) + r, Palette.withAlpha(color, 0x50));
        }
        outline(g, Math.round(cx) - r, Math.round(cy) - r, Math.round(cx) + r + 1, Math.round(cy) + r + 1, ring);
        int size = Math.max(2, Math.round(p.pixelScale() * 0.5F));
        int offset = pressed ? 1 : 3;
        triangle(g, Math.round(cx), Math.round(cy) + r + offset + size, size, 0, pressed ? color : Palette.withAlpha(color, 0x70));
    }

    private void caption(GuiGraphicsExtractor g, Font font, RemoteDemo.Frame frame, int x0, int x1, int captionTop, int barY, int width) {
        if (frame.cue() != RemoteDemo.Cue.NONE) {
            Component text = Component.translatable(frame.cue().translationKey());
            int color = frame.cue() == RemoteDemo.Cue.DETECTED ? Palette.SUCCESS : Palette.ACCENT;
            List<FormattedCharSequence> lines = font.split(text, width);
            int count = ThemeText.shownLines(text, lines.size(), this.captionLines);
            int y = captionTop + (this.captionLines - count) * Metrics.LINE_HEIGHT / 2;
            for (int i = 0; i < count; i++) {
                FormattedCharSequence line = lines.get(i);
                g.text(font, line, (x0 + x1) / 2 - font.width(line) / 2, y + i * Metrics.LINE_HEIGHT, color, false);
            }
        }
        if (frame.holding()) {
            int barX0 = x0 + PADDING + width / 6;
            int barX1 = x1 - PADDING - width / 6;
            g.fill(barX0, barY, barX1, barY + 2, Palette.FIELD);
            g.fill(barX0, barY, barX0 + Math.round((barX1 - barX0) * frame.hold()), barY + 2, Palette.SUCCESS);
        }
    }

    private static void outline(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
        if (x1 - x0 < 2 || y1 - y0 < 2) {
            return;
        }
        g.fill(x0 + 1, y0, x1 - 1, y0 + 1, color);
        g.fill(x0 + 1, y1 - 1, x1 - 1, y1, color);
        g.fill(x0, y0 + 1, x0 + 1, y1 - 1, color);
        g.fill(x1 - 1, y0 + 1, x1, y1 - 1, color);
    }

    private static void triangle(GuiGraphicsExtractor g, int tipX, int tipY, int size, int direction, int color) {
        for (int i = 0; i < size; i++) {
            switch (direction) {
                case 0 -> g.fill(tipX - i, tipY + i, tipX + i + 1, tipY + i + 1, color);
                case 1 -> g.fill(tipX - i - 1, tipY - i, tipX - i, tipY + i + 1, color);
                case 2 -> g.fill(tipX - i, tipY - i - 1, tipX + i + 1, tipY - i, color);
                default -> {
                }
            }
        }
    }
}
