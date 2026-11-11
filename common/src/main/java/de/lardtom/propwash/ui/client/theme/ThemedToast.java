package de.lardtom.propwash.ui.client.theme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

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
    private final ToastText text;
    private int accent;
    private long lastChanged;
    private boolean changed;
    private boolean forceHide;
    private Toast.Visibility wantedVisibility = Toast.Visibility.HIDE;

    private ThemedToast(Object token, Component title, @Nullable Component message, int accent, long displayMillis) {
        this.token = token;
        this.displayMillis = displayMillis;
        this.text = new ToastText(title, message);
        this.accent = accent;
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
        return this.text.title();
    }

    public @Nullable Component message() {
        return this.text.message();
    }

    public int accent() {
        return this.accent;
    }

    public void hide() {
        this.forceHide = true;
    }

    private void reset(Component title, @Nullable Component message, int accent) {
        this.text.set(title, message);
        this.accent = accent;
        this.forceHide = false;
        this.changed = true;
    }

    private ToastText.Layout layout(Font font) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean settled = minecraft.gui == null || minecraft.gui.overlay() == null;
        return this.text.layout(font, textWidth(minecraft.getWindow().getGuiScaledWidth()), settled);
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
        return boxWidth(this.layout(Minecraft.getInstance().font)) + MARGIN;
    }

    @Override
    public int height() {
        return Math.max(Toast.SLOT_HEIGHT, boxHeight(this.layout(Minecraft.getInstance().font)) + MARGIN);
    }

    @Override
    public float yPos(int firstSlotIndex) {
        return firstSlotIndex * Toast.SLOT_HEIGHT;
    }

    static int boxWidth(ToastText.Layout layout) {
        return Math.max(MIN_WIDTH, TEXT_X + layout.widest() + PAD_RIGHT);
    }

    private static int boxHeight(ToastText.Layout layout) {
        return Math.max(Toast.SLOT_HEIGHT - MARGIN, PAD_Y * 2 + layout.lines() * LINE - (LINE - Metrics.TEXT_HEIGHT));
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
        ToastText.Layout layout = this.layout(font);
        int w = boxWidth(layout);
        int h = boxHeight(layout);
        int top = MARGIN;
        ThemeDraw.notice(graphics, 0, top, w, top + h, this.accent, 1.0F);
        int y = top + (h - layout.lines() * LINE + (LINE - Metrics.TEXT_HEIGHT)) / 2;
        for (FormattedCharSequence line : layout.titleLines()) {
            ThemeText.text(graphics, font, line, TEXT_X, y, Palette.TEXT);
            y += LINE;
        }
        for (FormattedCharSequence line : layout.messageLines()) {
            ThemeText.text(graphics, font, line, TEXT_X, y, Palette.TEXT_MUTED);
            y += LINE;
        }
    }
}
