package de.lardtom.propwash.race.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.guide.GuideScenes;
import de.lardtom.propwash.guide.client.GuideOpener;
import de.lardtom.propwash.guide.client.screen.GuideDraw;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scenes.GateMenuScene;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeStats;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.ui.client.theme.ThemedWidgets;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.ui.client.widget.PanelWidget;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class GateHintScreen extends Screen implements ThemedScreen {
    public static final String KEY = "propwash.race.gate_hint.";
    public static final int MAX_WIDTH = 300;
    private static final int HEADER = Metrics.HEADER_HEIGHT;
    private static final int FOOTER = Metrics.FOOTER_HEIGHT;
    private static final int PAD = 8;
    private static final int CHIP_HEIGHT = 12;
    private static final int BOX = 12;

    private List<FormattedCharSequence> lines = List.of();
    private int left;
    private int top;
    private int right;
    private int bottom;
    private int textTop;
    private GateHintMemory.Choice choice = GateHintMemory.Choice.OK;
    private boolean finished;
    private boolean checkedBeforeInit;
    private @Nullable FlatButton chapterButton;
    private @Nullable FlatButton okButton;
    private @Nullable NeverBox neverBox;

    public GateHintScreen() {
        super(Component.translatable(KEY + "title"));
    }

    @Override
    protected void init() {
        boolean checked = this.neverBox != null ? this.neverBox.checked() : this.checkedBeforeInit;
        int w = Math.min(this.width - 16, MAX_WIDTH);
        this.left = (this.width - w) / 2;
        this.right = this.left + w;
        int inner = w - 2 * PAD;
        this.lines = ThemeText.balanced(this.font, Component.translatable(KEY + "text"), Math.max(40, inner));
        Component never = Component.translatable(KEY + "never");
        int neverLines = Math.clamp(this.font.split(never, Math.max(20, inner - BOX - 4)).size(), 1, 2);
        int neverHeight = Math.max(BOX, neverLines * this.font.lineHeight) + 2;
        int body = PAD + CHIP_HEIGHT + 5 + this.lines.size() * Metrics.LINE_HEIGHT + 6 + neverHeight + PAD;
        int h = HEADER + body + FOOTER;
        this.top = Math.max(4, (this.height - h) / 2);
        this.bottom = this.top + h;
        this.textTop = this.top + HEADER + PAD + CHIP_HEIGHT + 5;
        int neverTop = this.textTop + this.lines.size() * Metrics.LINE_HEIGHT + 6;
        NeverBox box = new NeverBox(this.left + PAD, neverTop, inner, neverHeight, never);
        box.setChecked(checked);
        box.setTooltip(Tooltip.create(Component.translatable(KEY + "never.tooltip")));
        this.neverBox = this.addRenderableWidget(box);

        int buttonY = this.bottom - FOOTER + (FOOTER - Metrics.CONTROL_HEIGHT) / 2;
        Component ok = Component.translatable(KEY + "ok");
        Component chapter = Component.translatable(KEY + "open");
        int room = w - 2 * Metrics.PADDING - Metrics.GAP;
        int okWidth = Math.min(Math.max(56, this.font.width(ok) + 20), room / 2);
        int chapterWidth = Math.min(this.font.width(chapter) + 16, room - okWidth);
        this.okButton = this.addRenderableWidget(new FlatButton(this.right - Metrics.PADDING - okWidth, buttonY, okWidth, Metrics.CONTROL_HEIGHT, ok,
                FlatButton.Style.PRIMARY, this::confirm));
        FlatButton open = new FlatButton(this.left + Metrics.PADDING, buttonY, chapterWidth, Metrics.CONTROL_HEIGHT, chapter, FlatButton.Style.NORMAL,
                this::openChapter);
        open.setTooltip(Tooltip.create(Component.translatable(KEY + "open.tooltip")));
        this.chapterButton = this.addRenderableWidget(open);
    }

    public @Nullable FlatButton chapterButton() {
        return this.chapterButton;
    }

    public @Nullable FlatButton okButton() {
        return this.okButton;
    }

    public @Nullable NeverBox neverBox() {
        return this.neverBox;
    }

    public boolean checked() {
        return this.neverBox != null ? this.neverBox.checked() : this.checkedBeforeInit;
    }

    public void setChecked(boolean checked) {
        this.checkedBeforeInit = checked;
        if (this.neverBox != null) {
            this.neverBox.setChecked(checked);
        }
    }

    public void confirm() {
        this.choice = GateHintMemory.Choice.OK;
        Minecraft.getInstance().gui.setScreen(null);
    }

    public void openChapter() {
        this.choice = GateHintMemory.Choice.CHAPTER;
        Scene scene = GuideScenes.byId(GateMenuScene.ID);
        if (scene == null) {
            Minecraft.getInstance().gui.setScreen(null);
            return;
        }
        GuideOpener.openScene(scene, null);
    }

    @Override
    public void removed() {
        if (!this.finished) {
            this.finished = true;
            GatePlaceHint.closed(this.choice, this.checked());
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public Component getNarrationMessage() {
        return Component.literal(this.title.getString() + ". " + Component.translatable(KEY + "text").getString());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ThemeDraw.backdrop(g, this.width, this.height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ThemeStats.frame(this);
        ThemeDraw.window(g, this.left, this.top, this.right, this.bottom);
        ThemeDraw.headerBar(g, this.left, this.top, this.right, HEADER);
        int titleY = Metrics.textY(this.top, HEADER - 1);
        int titleX = ThemeDraw.brand(g, this.font, this.left + 8, titleY) + 10;
        ThemeDraw.verticalSeparator(g, titleX - 5, this.top + 5, this.top + HEADER - 6);
        ThemeText.fitted(g, this.font, this.title, titleX, titleY, this.right - titleX - 8, Palette.TEXT);

        int x = this.left + PAD;
        int chipY = this.top + HEADER + PAD;
        x += Chip.of(Minecraft.getInstance().options.keyShift).draw(g, this.font, x, chipY);
        x += 4;
        ThemeText.text(g, this.font, "+", x, chipY + 2, Palette.TEXT_FAINT);
        x += this.font.width("+") + 4;
        Chip.of(Minecraft.getInstance().options.keyUse).draw(g, this.font, x, chipY);
        int y = this.textTop;
        for (FormattedCharSequence line : this.lines) {
            ThemeText.text(g, this.font, line, this.left + PAD, y, Palette.TEXT);
            y += Metrics.LINE_HEIGHT;
        }

        ThemeDraw.footerBar(g, this.left, this.bottom - FOOTER, this.right, this.bottom);
        super.extractRenderState(g, mouseX, mouseY, a);
        ThemeDraw.windowBorder(g, this.left, this.top, this.right, this.bottom);
    }

    record Chip(int mouseButton, String text) {
        static Chip of(KeyMapping mapping) {
            if (mapping.isUnbound()) {
                return new Chip(0, Component.translatable("propwash.guide.key.unbound").getString());
            }
            InputConstants.Key bound;
            try {
                bound = InputConstants.getKey(mapping.saveString());
            } catch (IllegalArgumentException e) {
                bound = InputConstants.UNKNOWN;
            }
            if (bound.getType() == InputConstants.Type.MOUSE && bound.getValue() >= InputConstants.MOUSE_BUTTON_LEFT
                    && bound.getValue() <= InputConstants.MOUSE_BUTTON_RIGHT) {
                int button = bound.getValue();
                String key = switch (button) {
                    case InputConstants.MOUSE_BUTTON_LEFT -> "propwash.guide.mouse.left";
                    case InputConstants.MOUSE_BUTTON_MIDDLE -> "propwash.guide.mouse.middle";
                    default -> "propwash.guide.mouse.right";
                };
                return new Chip(button, Component.translatable(key).getString());
            }
            return new Chip(0, GuideDraw.shortKeyName(mapping.getTranslatedKeyMessage().getString()));
        }

        int draw(GuiGraphicsExtractor g, Font font, int x, int y) {
            if (this.mouseButton > 0) {
                GuideDraw.mouse(g, x, y + 1, this.mouseButton, 255);
                ThemeText.text(g, font, this.text, x + GuideDraw.MOUSE_WIDTH + 3, y + 2, Palette.ACCENT);
                return GuideDraw.MOUSE_WIDTH + 3 + font.width(this.text);
            }
            return GuideDraw.keycap(g, font, this.text, x, y, CHIP_HEIGHT, 255);
        }
    }

    public static final class NeverBox extends PanelWidget {
        private boolean checked;

        NeverBox(int x, int y, int width, int height, Component message) {
            super(x, y, width, height, message);
        }

        public boolean checked() {
            return this.checked;
        }

        public void setChecked(boolean checked) {
            this.checked = checked;
        }

        public void toggle() {
            this.checked = !this.checked;
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            this.toggle();
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (this.isActive() && event.isSelection()) {
                this.playDownSound(Minecraft.getInstance().getSoundManager());
                this.toggle();
                return true;
            }
            return false;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            ThemedWidgets.checkbox(g, this.font(), this.getX(), this.getY(), this.width, this.height, BOX, this.getMessage(), this.checked,
                    this.state());
        }
    }
}
