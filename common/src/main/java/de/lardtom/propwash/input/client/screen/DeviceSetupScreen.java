package de.lardtom.propwash.input.client.screen;

import de.lardtom.propwash.input.SetupPrompts;
import de.lardtom.propwash.input.StickMode;
import de.lardtom.propwash.input.client.DeviceInfo;
import de.lardtom.propwash.input.client.DeviceOnboarding;
import de.lardtom.propwash.input.client.StickModes;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeStats;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public final class DeviceSetupScreen extends Screen implements ThemedScreen {
    private static final int MAX_WIDTH = 320;
    private static final int HEADER = Metrics.HEADER_HEIGHT;
    private static final int FOOTER = Metrics.FOOTER_HEIGHT;
    private static final int PAD = 10;
    private static final int NAME_GAP = 4;
    private static final int PARAGRAPH_GAP = 8;

    private final DeviceInfo device;
    private SetupPrompts.@Nullable Answer answer;
    private int windowLeft;
    private int windowTop;
    private int windowRight;
    private int windowBottom;
    private long frames;

    public DeviceSetupScreen(DeviceInfo device) {
        super(DeviceOnboarding.title(device));
        this.device = device;
    }

    public DeviceInfo device() {
        return this.device;
    }

    public long frames() {
        return this.frames;
    }

    public void press(SetupPrompts.Answer choice) {
        if (this.answer != null) {
            return;
        }
        this.answer = choice;
        DeviceOnboarding.INSTANCE.answer(this.device, choice, this.minecraft);
    }

    private Component question() {
        return Component.translatable("propwash.input.setup.question");
    }

    private Component explanation() {
        if (this.device.gamepadByDefault()) {
            StickMode mode = StickModes.current();
            return Component.translatable("propwash.input.setup.prefill_gamepad", StickModes.name(mode), StickModes.layout(mode));
        }
        return Component.translatable("propwash.input.setup.prefill_radio");
    }

    private Component hint() {
        return Component.translatable("propwash.input.setup.hint", DeviceOnboarding.configuratorKey());
    }

    private int textWidth() {
        return this.windowRight - this.windowLeft - 2 * PAD;
    }

    private int bodyHeight(int textWidth) {
        int h = Metrics.TEXT_HEIGHT + NAME_GAP + Metrics.TEXT_HEIGHT + PARAGRAPH_GAP;
        h += ThemeText.wrappedHeight(this.font, this.question(), textWidth) + NAME_GAP;
        h += ThemeText.wrappedHeight(this.font, this.explanation(), textWidth) + PARAGRAPH_GAP;
        h += ThemeText.wrappedHeight(this.font, this.hint(), textWidth);
        return h;
    }

    @Override
    protected void init() {
        int w = Math.min(this.width - 16, MAX_WIDTH);
        this.windowLeft = (this.width - w) / 2;
        this.windowRight = this.windowLeft + w;
        int h = Math.min(this.height - 8, HEADER + 2 * PAD + this.bodyHeight(this.textWidth()) + FOOTER);
        this.windowTop = (this.height - h) / 2;
        this.windowBottom = this.windowTop + h;
        int buttonY = this.windowBottom - FOOTER + (FOOTER - Metrics.CONTROL_HEIGHT) / 2;
        Component setup = Component.translatable("propwash.input.setup.button.setup");
        Component later = Component.translatable("propwash.input.setup.button.later");
        Component never = Component.translatable("propwash.input.setup.button.never");
        int setupWidth = Math.max(72, this.font.width(setup) + 16);
        int laterWidth = Math.max(56, this.font.width(later) + 16);
        int neverWidth = Math.min(this.font.width(never) + 16, w - setupWidth - laterWidth - 2 * Metrics.PADDING - 2 * Metrics.GAP);
        int x = this.windowRight - Metrics.PADDING - setupWidth;
        this.addRenderableWidget(new FlatButton(x, buttonY, setupWidth, Metrics.CONTROL_HEIGHT, setup, FlatButton.Style.PRIMARY,
                () -> this.press(SetupPrompts.Answer.SET_UP)));
        x -= Metrics.GAP + laterWidth;
        this.addRenderableWidget(new FlatButton(x, buttonY, laterWidth, Metrics.CONTROL_HEIGHT, later, FlatButton.Style.NORMAL,
                () -> this.press(SetupPrompts.Answer.LATER)));
        this.addRenderableWidget(new FlatButton(this.windowLeft + Metrics.PADDING, buttonY, neverWidth, Metrics.CONTROL_HEIGHT, never, FlatButton.Style.NORMAL,
                () -> this.press(SetupPrompts.Answer.NEVER)));
    }

    @Override
    public void tick() {
        if (this.answer == null && !DeviceOnboarding.INSTANCE.connected(this.device.guid())) {
            this.minecraft.gui.setScreen(null);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isConfirmation() && this.getFocused() == null) {
            this.press(SetupPrompts.Answer.SET_UP);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        this.press(SetupPrompts.Answer.LATER);
    }

    @Override
    public void removed() {
        if (this.answer == null) {
            DeviceOnboarding.INSTANCE.promptClosed(this.device);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ThemeDraw.backdrop(g, this.width, this.height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ThemeStats.frame(this);
        this.frames++;
        ThemeDraw.window(g, this.windowLeft, this.windowTop, this.windowRight, this.windowBottom);
        ThemeDraw.headerBar(g, this.windowLeft, this.windowTop, this.windowRight, HEADER);
        int textY = Metrics.textY(this.windowTop, HEADER - 1);
        int titleX = ThemeDraw.brand(g, this.font, this.windowLeft + 8, textY) + 10;
        ThemeDraw.verticalSeparator(g, titleX - 5, this.windowTop + 5, this.windowTop + HEADER - 6);
        ThemeText.fitted(g, this.font, this.title, titleX, textY, this.windowRight - titleX - 8, Palette.TEXT);

        int x = this.windowLeft + PAD;
        int width = this.textWidth();
        int y = this.windowTop + HEADER + PAD;
        int footerTop = this.windowBottom - FOOTER;
        g.enableScissor(this.windowLeft, this.windowTop + HEADER, this.windowRight, footerTop);
        g.fill(x, y - 1, x + 2, y + Metrics.TEXT_HEIGHT * 2 + NAME_GAP, Palette.ACCENT);
        int nameX = x + 8;
        ThemeText.fitted(g, this.font, Component.literal(this.device.displayName()), nameX, y, width - 8, Palette.TEXT);
        y += Metrics.TEXT_HEIGHT + NAME_GAP;
        ThemeText.fitted(g, this.font, InputLabels.deviceTags(this.device), nameX, y, width - 8, Palette.TEXT_MUTED);
        y += Metrics.TEXT_HEIGHT + PARAGRAPH_GAP;
        y += ThemeText.wrapped(g, this.font, this.question(), x, y, width, Palette.TEXT) + NAME_GAP;
        y += ThemeText.wrapped(g, this.font, this.explanation(), x, y, width, Palette.TEXT_MUTED) + PARAGRAPH_GAP;
        ThemeText.wrapped(g, this.font, this.hint(), x, y, width, Palette.TEXT_FAINT);
        g.disableScissor();

        ThemeDraw.footerBar(g, this.windowLeft, footerTop, this.windowRight, this.windowBottom);
        super.extractRenderState(g, mouseX, mouseY, a);
        ThemeDraw.windowBorder(g, this.windowLeft, this.windowTop, this.windowRight, this.windowBottom);
    }
}
