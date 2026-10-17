package de.lardtom.propwash.input.client.screen;

import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.MouseAxis;
import de.lardtom.propwash.input.MouseDemo;
import de.lardtom.propwash.input.MouseMode;
import de.lardtom.propwash.input.MouseSettings;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.input.client.TransmitterInput;
import de.lardtom.propwash.input.client.screen.remote.MouseDemoView;
import de.lardtom.propwash.ui.client.config.StepSlider;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeStats;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public final class MouseSetupScreen extends Screen implements ThemedScreen {
    private static final int HEADER_SPACE = 36;
    private static final int FOOTER_SPACE = 30;
    private static final int FOOTER_GROUP_GAP = 16;
    private static final int COLUMN_GAP = 10;
    private static final int HINT_SPACE = 14;
    private static final long LIVE_MILLIS = 1500L;

    private final ReceiverWizardScreen wizard;
    private final InputConfig working;
    private final MouseDemoView demo = new MouseDemoView();
    private final MousePreview preview = new MousePreview();
    private @Nullable CycleButton<Boolean> invertPitch;
    private long liveUntil;
    private int left;
    private int panelWidth;
    private int columnWidth;
    private int rightX;
    private int contentTop;
    private int previewTop;
    private int footerY;
    private int windowTop;
    private int windowBottom;

    public MouseSetupScreen(ReceiverWizardScreen wizard, InputConfig working) {
        super(Component.translatable("propwash.input.wizard.title"));
        this.wizard = wizard;
        this.working = working;
    }

    public MouseDemoView demo() {
        return this.demo;
    }

    public MousePreview preview() {
        return this.preview;
    }

    public InputConfig working() {
        return this.working;
    }

    public @Nullable CycleButton<Boolean> invertPitchButton() {
        return this.invertPitch;
    }

    @Override
    protected void init() {
        this.panelWidth = Math.min(this.width - 16, 440);
        this.left = (this.width - this.panelWidth) / 2;
        this.windowTop = 2;
        this.windowBottom = this.height - 2;
        this.contentTop = this.windowTop + HEADER_SPACE;
        this.footerY = this.windowBottom - FOOTER_SPACE + 6;
        this.columnWidth = (this.panelWidth - COLUMN_GAP) / 2;
        this.rightX = this.left + this.columnWidth + COLUMN_GAP;
        MouseSettings mouse = this.working.mouse;
        int y = this.contentTop;
        int w = this.columnWidth;
        this.addRenderableWidget(CycleButton.builder((MouseMode m) -> Component.translatable(m.translationKey()), mouse.mode)
                .withValues(MouseMode.values())
                .withTooltip(m -> Tooltip.create(Component.translatable(m.descriptionKey())))
                .create(this.rightX, y, w, Metrics.CONTROL_HEIGHT, Component.translatable("propwash.input.mouse.mode"), (b, v) -> {
                    mouse.mode = v;
                    this.changed();
                }));
        y += Metrics.ROW_HEIGHT;
        this.addRenderableWidget(CycleButton.builder((MouseAxis a) -> Component.translatable(a.translationKey()), mouse.xAxis)
                .withValues(MouseAxis.values())
                .withTooltip(a -> Tooltip.create(Component.translatable("propwash.input.mouse.x_axis.tooltip")))
                .create(this.rightX, y, w, Metrics.CONTROL_HEIGHT, Component.translatable("propwash.input.mouse.x_axis"), (b, v) -> {
                    mouse.xAxis = v;
                    this.changed();
                }));
        y += Metrics.ROW_HEIGHT;
        StepSlider sensitivity = new StepSlider(this.rightX, y, w, Metrics.CONTROL_HEIGHT, MouseSettings.MIN_SENSITIVITY, MouseSettings.MAX_SENSITIVITY,
                0.05F, mouse.sensitivity, v -> Component.translatable("propwash.input.mouse.sensitivity", Math.round(v * 100.0F)),
                v -> mouse.sensitivity = (float) v);
        sensitivity.setTooltip(Tooltip.create(Component.translatable("propwash.input.mouse.sensitivity.tooltip")));
        this.addRenderableWidget(sensitivity);
        y += Metrics.ROW_HEIGHT;
        this.invertPitch = this.addRenderableWidget(CycleButton.onOffBuilder(this.working.invertPitch)
                .withTooltip(v -> Tooltip.create(Component.translatable("propwash.input.invert_pitch.tooltip")))
                .create(this.rightX, y, w, Metrics.CONTROL_HEIGHT, Component.translatable("propwash.input.invert_pitch"), (b, v) -> {
                    this.working.invertPitch = v;
                    this.changed();
                }));
        y += Metrics.ROW_HEIGHT + 4;
        this.previewTop = y;
        int slot = (this.panelWidth - 4 - FOOTER_GROUP_GAP) / 3;
        this.addRenderableWidget(Button.builder(Component.translatable("propwash.input.wizard.back"), b -> this.onClose())
                .bounds(this.left, this.footerY, slot, Metrics.BUTTON_HEIGHT).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> this.wizard.onClose())
                .bounds(this.left + this.panelWidth - 2 * slot - 4, this.footerY, slot, Metrics.BUTTON_HEIGHT).build());
        Button done = this.addRenderableWidget(Button.builder(Component.translatable("propwash.input.wizard.finish"), b -> this.wizard.finishFromMouse())
                .bounds(this.left + this.panelWidth - slot, this.footerY, slot, Metrics.BUTTON_HEIGHT).build());
        done.setTooltip(Tooltip.create(Component.translatable("propwash.input.mouse.wizard.finish.tooltip")));
    }

    private void changed() {
        this.preview.reset();
        this.demo.restart();
    }

    @Override
    public void tick() {
        TransmitterInput.INSTANCE.requestPreview(InputConfig.MOUSE_GUID);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.preview.mouseClicked(event.x(), event.y(), event.button())) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.preview.mouseReleased();
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.preview.dragging()) {
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.wizard);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ThemeDraw.backdrop(g, this.width, this.height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ThemeStats.frame(this);
        int wx0 = this.left - 8;
        int wx1 = this.left + this.panelWidth + 8;
        ThemeDraw.window(g, wx0, this.windowTop, wx1, this.windowBottom);
        ThemeDraw.headerBar(g, wx0, this.windowTop, wx1, this.contentTop - 6 - this.windowTop);
        ThemeDraw.footerBar(g, wx0, this.footerY - 4, wx1, this.windowBottom);
        int titleX = ThemeDraw.brand(g, this.font, wx0 + 8, this.windowTop + 6) + 10;
        ThemeDraw.verticalSeparator(g, titleX - 5, this.windowTop + 3, this.windowTop + 16);
        ThemeText.text(g, this.font, this.title, titleX, this.windowTop + 6, Palette.TEXT);
        Component steps = Component.translatable("propwash.input.mouse.wizard.steps");
        ThemeText.fitted(g, this.font, steps, wx0 + 8, this.windowTop + 19, this.panelWidth, Palette.ACCENT);
        int bottom = this.footerY - 4 - HINT_SPACE;
        int previewHeight = Math.max(MousePreview.GIMBAL_MIN, bottom - this.previewTop);
        long now = Util.getMillis();
        if (this.preview.dragging()) {
            this.liveUntil = now + LIVE_MILLIS;
        }
        StickPositions live = now < this.liveUntil ? this.preview.sticks() : null;
        this.demo.mode(this.working.stickMode());
        this.demo.extract(g, this.font, this.left, this.contentTop, this.left + this.columnWidth, bottom, this.working.mouse, this.working.invertPitch,
                this.selfLevel(), live);
        MouseDemo.Frame shown = this.demo.lastFrame();
        this.preview.show(live == null && shown != null ? shown.sticks() : null);
        this.preview.mode(this.working.stickMode());
        this.preview.extract(g, this.font, this.rightX, this.previewTop, this.columnWidth, previewHeight, this.working.mouse, this.working.invertPitch, mouseX,
                mouseY);
        Component hint = Component.translatable("propwash.input.mouse.wizard.hint");
        ThemeText.fittedCentered(g, this.font, hint, this.width / 2, this.footerY - 16, this.panelWidth, Palette.TEXT_MUTED);
        super.extractRenderState(g, mouseX, mouseY, a);
        ThemeDraw.windowBorder(g, wx0, this.windowTop, wx1, this.windowBottom);
    }

    private boolean selfLevel() {
        if (this.working.mouse.mode != MouseMode.AUTO) {
            return this.working.mouse.mode == MouseMode.HOLD;
        }
        return !this.preview.acro();
    }
}
