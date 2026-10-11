package de.lardtom.propwash.ui.client.theme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class ThemedToast implements Toast {
    private static final int MIN_WIDTH = 160;
    private static final int MAX_TEXT_WIDTH = 200;
    private static final int TEXT_X = 10;
    private static final int PAD_RIGHT = 10;
    private static final int PAD_Y = 7;
    private static final int LINE = 12;
    private static final int MARGIN = 6;

    private final Object token;
    private final long displayMillis;
    private Component title;
    private @Nullable Component message;
    private int accent;
    private List<FormattedCharSequence> titleLines = List.of();
    private List<FormattedCharSequence> messageLines = List.of();
    private int width = MIN_WIDTH;
    private long lastChanged;
    private boolean changed;
    private boolean forceHide;
    private Toast.Visibility wantedVisibility = Toast.Visibility.HIDE;

    private ThemedToast(Object token, Component title, @Nullable Component message, int accent, long displayMillis) {
        this.token = token;
        this.displayMillis = displayMillis;
        this.title = title;
        this.accent = accent;
        this.layout(title, message);
    }

    public static ThemedToast show(ToastManager manager, Object token, Component title, @Nullable Component message, int accent, long displayMillis) {
        ThemedToast existing = manager.getToast(ThemedToast.class, token);
        if (existing != null) {
            existing.reset(title, message, accent);
            return existing;
        }
        ThemedToast toast = new ThemedToast(token, title, message, accent, displayMillis);
        manager.addToast(toast);
        return toast;
    }

    public static @Nullable ThemedToast find(ToastManager manager, Object token) {
        return manager.getToast(ThemedToast.class, token);
    }

    public Component title() {
        return this.title;
    }

    public @Nullable Component message() {
        return this.message;
    }

    public int accent() {
        return this.accent;
    }

    public void hide() {
        this.forceHide = true;
    }

    private void reset(Component title, @Nullable Component message, int accent) {
        this.title = title;
        this.accent = accent;
        this.forceHide = false;
        this.layout(title, message);
        this.changed = true;
    }

    private void layout(Component title, @Nullable Component message) {
        this.message = message;
        Font font = Minecraft.getInstance().font;
        int room = textWidth(Minecraft.getInstance().getWindow().getGuiScaledWidth());
        this.titleLines = font.split(title, room);
        this.messageLines = message == null ? List.of() : ThemeText.balanced(font, message, room);
        int text = 0;
        for (FormattedCharSequence line : this.titleLines) {
            text = Math.max(text, font.width(line));
        }
        for (FormattedCharSequence line : this.messageLines) {
            text = Math.max(text, font.width(line));
        }
        this.width = Math.max(MIN_WIDTH, TEXT_X + text + PAD_RIGHT);
    }

    public static int textWidth(int screenWidth) {
        return Math.max(40, Math.min(MAX_TEXT_WIDTH, screenWidth - TEXT_X - PAD_RIGHT - MARGIN));
    }

    @Override
    public Object getToken() {
        return this.token;
    }

    @Override
    public int width() {
        return this.width + MARGIN;
    }

    @Override
    public int height() {
        return Math.max(Toast.SLOT_HEIGHT, this.boxHeight() + MARGIN);
    }

    @Override
    public float yPos(int firstSlotIndex) {
        return firstSlotIndex * Toast.SLOT_HEIGHT;
    }

    private int boxHeight() {
        return Math.max(Toast.SLOT_HEIGHT - MARGIN, PAD_Y * 2 + this.lines() * LINE - (LINE - Metrics.TEXT_HEIGHT));
    }

    private int lines() {
        return Math.max(1, this.titleLines.size() + this.messageLines.size());
    }

    @Override
    public Toast.Visibility getWantedVisibility() {
        return this.wantedVisibility;
    }

    @Override
    public void update(ToastManager manager, long fullyVisibleForMs) {
        if (this.changed) {
            this.lastChanged = fullyVisibleForMs;
            this.changed = false;
        }
        double shown = this.displayMillis * manager.getNotificationDisplayTimeMultiplier();
        this.wantedVisibility = !this.forceHide && fullyVisibleForMs - this.lastChanged < shown ? Toast.Visibility.SHOW : Toast.Visibility.HIDE;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long fullyVisibleForMs) {
        int w = this.width;
        int h = this.boxHeight();
        int top = MARGIN;
        ThemeDraw.notice(graphics, 0, top, w, top + h, this.accent, 1.0F);
        int y = top + (h - this.lines() * LINE + (LINE - Metrics.TEXT_HEIGHT)) / 2;
        for (FormattedCharSequence line : this.titleLines) {
            ThemeText.text(graphics, font, line, TEXT_X, y, Palette.TEXT);
            y += LINE;
        }
        for (FormattedCharSequence line : this.messageLines) {
            ThemeText.text(graphics, font, line, TEXT_X, y, Palette.TEXT_MUTED);
            y += LINE;
        }
    }
}
