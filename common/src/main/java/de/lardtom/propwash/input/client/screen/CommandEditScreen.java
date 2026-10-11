package de.lardtom.propwash.input.client.screen;

import de.lardtom.propwash.input.SwitchCommand;
import de.lardtom.propwash.input.SwitchCommands;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.client.SwitchCommandRunner;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeStats;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.ui.client.widget.TextField;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

public final class CommandEditScreen extends Screen implements ThemedScreen {
    public static final int FIELD_MAX_LENGTH = 1024;
    private static final int MAX_WIDTH = 360;
    private static final int HEADER = Metrics.HEADER_HEIGHT;
    private static final int FOOTER = Metrics.FOOTER_HEIGHT;
    private static final int PAD = 10;
    private static final int FIELD_HEIGHT = 16;
    private static final int LABEL_GAP = 3;
    private static final int STATUS_GAP = 3;
    private static final int BLOCK_GAP = 8;
    private static final int BLOCK_GAP_MIN = 3;
    private static final int PAD_MIN = 4;

    private int blockGap = BLOCK_GAP;
    private int padY = PAD;
    private final @Nullable Screen parent;
    private final SwitchFunction function;
    private final SwitchCommand initial;
    private final Component device;
    private final Component trigger;
    private final Component key;
    private final Consumer<SwitchCommand> onApply;
    private @Nullable EditBox onField;
    private @Nullable EditBox offField;
    private @Nullable FlatButton applyButton;
    private String onText;
    private String offText;
    private int windowLeft;
    private int windowTop;
    private int windowRight;
    private int windowBottom;
    private int onLabelY;
    private int offLabelY;
    private boolean applied;
    private String knownText = "";
    private boolean knownResult = true;
    private String knownOffText = "";
    private boolean knownOffResult = true;

    public CommandEditScreen(@Nullable Screen parent, SwitchFunction function, SwitchCommand initial, Component device, Component trigger,
                             Component key, Consumer<SwitchCommand> onApply) {
        super(Component.translatable("propwash.input.command.edit.title", Component.translatable(function.translationKey())));
        this.parent = parent;
        this.function = function;
        this.initial = initial.copy();
        this.device = device;
        this.trigger = trigger;
        this.key = key;
        this.onApply = onApply;
        this.onText = this.initial.on;
        this.offText = this.initial.off;
    }

    public SwitchFunction function() {
        return this.function;
    }

    public void setTexts(String on, String off) {
        this.onText = on == null ? "" : on;
        this.offText = off == null ? "" : off;
        if (this.onField != null) {
            this.onField.setValue(this.onText);
        }
        if (this.offField != null) {
            this.offField.setValue(this.offText);
        }
        this.updateApply();
    }

    public boolean canApply() {
        return problem(this.onText) == SwitchCommands.Problem.NONE && problem(this.offText) == SwitchCommands.Problem.NONE;
    }

    public SwitchCommand result() {
        return new SwitchCommand(SwitchCommands.normalize(this.onText), SwitchCommands.normalize(this.offText));
    }

    public boolean apply() {
        if (!this.canApply()) {
            return false;
        }
        this.applied = true;
        this.onApply.accept(this.result());
        this.minecraft.gui.setScreen(this.parent);
        return true;
    }

    public boolean applied() {
        return this.applied;
    }

    private static SwitchCommands.Problem problem(String raw) {
        return SwitchCommands.problem(SwitchCommands.normalize(raw));
    }

    private int textWidth() {
        return this.windowRight - this.windowLeft - 2 * PAD;
    }

    private Component sourceLine() {
        return Component.translatable("propwash.input.command.edit.source", this.device, this.trigger, this.key);
    }

    private Component rules() {
        return Component.translatable("propwash.input.command.edit.rules", SwitchCommands.MAX_LENGTH);
    }

    private int bodyHeight(int textWidth) {
        int h = ThemeText.wrappedHeight(this.font, this.sourceLine(), textWidth) + this.blockGap;
        h += 2 * (Metrics.TEXT_HEIGHT + LABEL_GAP + FIELD_HEIGHT + STATUS_GAP + Metrics.TEXT_HEIGHT + this.blockGap);
        h += ThemeText.wrappedHeight(this.font, this.rules(), textWidth);
        return h;
    }

    @Override
    protected void init() {
        int w = Math.min(this.width - 16, MAX_WIDTH);
        this.windowLeft = (this.width - w) / 2;
        this.windowRight = this.windowLeft + w;
        this.blockGap = BLOCK_GAP;
        this.padY = PAD;
        int over = HEADER + 2 * PAD + this.bodyHeight(this.textWidth()) + FOOTER - (this.height - 8);
        if (over > 0) {
            this.blockGap = Math.max(BLOCK_GAP_MIN, BLOCK_GAP - (over + 2) / 3);
            over -= 3 * (BLOCK_GAP - this.blockGap);
        }
        if (over > 0) {
            this.padY = Math.max(PAD_MIN, PAD - (over + 1) / 2);
        }
        int h = Math.min(this.height - 8, HEADER + 2 * this.padY + this.bodyHeight(this.textWidth()) + FOOTER);
        this.windowTop = (this.height - h) / 2;
        this.windowBottom = this.windowTop + h;
        int x = this.windowLeft + PAD;
        int y = this.windowTop + HEADER + this.padY + ThemeText.wrappedHeight(this.font, this.sourceLine(), this.textWidth()) + this.blockGap;
        this.onLabelY = y;
        this.onField = this.field(x, y + Metrics.TEXT_HEIGHT + LABEL_GAP, "propwash.input.command.edit.on", "propwash.input.command.edit.on.hint",
                this.onText, v -> {
                    this.onText = v;
                    this.updateApply();
                });
        y += Metrics.TEXT_HEIGHT + LABEL_GAP + FIELD_HEIGHT + STATUS_GAP + Metrics.TEXT_HEIGHT + this.blockGap;
        this.offLabelY = y;
        this.offField = this.field(x, y + Metrics.TEXT_HEIGHT + LABEL_GAP, "propwash.input.command.edit.off", "propwash.input.command.edit.off.hint",
                this.offText, v -> {
                    this.offText = v;
                    this.updateApply();
                });
        int buttonY = this.windowBottom - FOOTER + (FOOTER - Metrics.CONTROL_HEIGHT) / 2;
        Component apply = Component.translatable("propwash.input.command.edit.apply");
        Component cancel = Component.translatable("propwash.input.command.edit.cancel");
        Component clear = Component.translatable("propwash.input.command.edit.clear");
        int applyWidth = Math.max(72, this.font.width(apply) + 16);
        int cancelWidth = Math.max(56, this.font.width(cancel) + 16);
        int clearWidth = Math.max(56, this.font.width(clear) + 16);
        int bx = this.windowRight - Metrics.PADDING - applyWidth;
        this.applyButton = this.addRenderableWidget(new FlatButton(bx, buttonY, applyWidth, Metrics.CONTROL_HEIGHT, apply, FlatButton.Style.PRIMARY,
                this::apply));
        bx -= Metrics.GAP + cancelWidth;
        this.addRenderableWidget(new FlatButton(bx, buttonY, cancelWidth, Metrics.CONTROL_HEIGHT, cancel, FlatButton.Style.NORMAL, this::onClose));
        FlatButton clearButton = new FlatButton(this.windowLeft + Metrics.PADDING, buttonY, clearWidth, Metrics.CONTROL_HEIGHT, clear,
                FlatButton.Style.NORMAL, () -> this.setTexts("", ""));
        clearButton.setTooltip(Tooltip.create(Component.translatable("propwash.input.command.edit.clear.tooltip")));
        this.addRenderableWidget(clearButton);
        this.setInitialFocus(this.onField);
        this.updateApply();
    }

    private EditBox field(int x, int y, String labelKey, String hintKey, String value, Consumer<String> responder) {
        TextField box = new TextField(this.font, x, y, this.textWidth(), FIELD_HEIGHT, Component.translatable(labelKey));
        box.setMaxLength(FIELD_MAX_LENGTH);
        box.hint(Component.translatable(hintKey));
        box.setValue(value);
        box.setResponder(responder);
        return this.addRenderableWidget(box);
    }

    private void updateApply() {
        if (this.applyButton != null) {
            this.applyButton.active = this.canApply();
        }
    }

    public Status status(boolean on) {
        String raw = on ? this.onText : this.offText;
        String normalized = SwitchCommands.normalize(raw);
        SwitchCommands.Problem problem = SwitchCommands.problem(normalized);
        if (problem == SwitchCommands.Problem.TOO_LONG) {
            return new Status(Component.translatable("propwash.input.command.edit.too_long", normalized.length(), SwitchCommands.MAX_LENGTH), Palette.ERROR);
        }
        if (problem == SwitchCommands.Problem.ILLEGAL_CHARACTER) {
            return new Status(Component.translatable("propwash.input.command.edit.illegal"), Palette.ERROR);
        }
        if (normalized.isEmpty()) {
            return new Status(Component.translatable(on ? "propwash.input.command.edit.on.empty" : "propwash.input.command.edit.off.empty"),
                    Palette.TEXT_FAINT);
        }
        if (!this.known(on, normalized)) {
            return new Status(Component.translatable("propwash.input.command.edit.unknown"), Palette.WARNING);
        }
        return new Status(Component.translatable("propwash.input.command.edit.ok", normalized.length(), SwitchCommands.MAX_LENGTH), Palette.TEXT_FAINT);
    }

    private boolean known(boolean on, String normalized) {
        if (normalized.equals(on ? this.knownText : this.knownOffText)) {
            return on ? this.knownResult : this.knownOffResult;
        }
        ClientPacketListener connection = this.minecraft == null ? null : this.minecraft.getConnection();
        boolean known = connection == null || SwitchCommandRunner.known(connection, SwitchCommands.body(normalized));
        if (on) {
            this.knownText = normalized;
            this.knownResult = known;
        } else {
            this.knownOffText = normalized;
            this.knownOffResult = known;
        }
        return known;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isConfirmation() && (this.getFocused() == null || this.getFocused() instanceof EditBox)) {
            this.apply();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ThemeDraw.backdrop(g, this.width, this.height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ThemeStats.frame(this);
        ThemeDraw.window(g, this.windowLeft, this.windowTop, this.windowRight, this.windowBottom);
        ThemeDraw.headerBar(g, this.windowLeft, this.windowTop, this.windowRight, HEADER);
        int textY = Metrics.textY(this.windowTop, HEADER - 1);
        int titleX = ThemeDraw.brand(g, this.font, this.windowLeft + 8, textY) + 10;
        ThemeDraw.verticalSeparator(g, titleX - 5, this.windowTop + 5, this.windowTop + HEADER - 6);
        ThemeText.fitted(g, this.font, this.title, titleX, textY, this.windowRight - titleX - 8, Palette.TEXT);

        int x = this.windowLeft + PAD;
        int width = this.textWidth();
        int footerTop = this.windowBottom - FOOTER;
        g.enableScissor(this.windowLeft, this.windowTop + HEADER, this.windowRight, footerTop);
        ThemeText.wrapped(g, this.font, this.sourceLine(), x, this.windowTop + HEADER + this.padY, width, Palette.TEXT_MUTED);
        this.extractBlock(g, x, this.onLabelY, width, true);
        int afterOff = this.extractBlock(g, x, this.offLabelY, width, false);
        ThemeText.wrapped(g, this.font, this.rules(), x, afterOff + this.blockGap, width, Palette.TEXT_FAINT);
        g.disableScissor();

        ThemeDraw.footerBar(g, this.windowLeft, footerTop, this.windowRight, this.windowBottom);
        super.extractRenderState(g, mouseX, mouseY, a);
        ThemeDraw.windowBorder(g, this.windowLeft, this.windowTop, this.windowRight, this.windowBottom);
    }

    private int extractBlock(GuiGraphicsExtractor g, int x, int y, int width, boolean on) {
        Component label = Component.translatable(on ? "propwash.input.command.edit.on" : "propwash.input.command.edit.off");
        ThemeText.fitted(g, this.font, label, x, y, width, Palette.TEXT);
        int statusY = y + Metrics.TEXT_HEIGHT + LABEL_GAP + FIELD_HEIGHT + STATUS_GAP;
        Status status = this.status(on);
        ThemeText.fitted(g, this.font, status.text(), x, statusY, width, status.color());
        return statusY + Metrics.TEXT_HEIGHT;
    }

    public record Status(Component text, int color) {
    }
}
