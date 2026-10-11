package de.lardtom.propwash.input.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.ui.NumberEntry;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.widget.NumberEditor;
import de.lardtom.propwash.ui.client.widget.NumberInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public final class MicrosField extends AbstractWidget implements NumberInput {
    private static final int ARROW = 8;
    private static final int DRAG_PIXELS_PER_STEP = 4;
    private static final int FIELD = Palette.FIELD;
    private static final int FIELD_HOVER = Palette.FIELD_HOVER;

    private final IntSupplier getter;
    private final IntConsumer setter;
    private final int step;
    private final NumberEditor editor = new NumberEditor(false);
    private double dragAccumulator;
    private boolean dragging;
    private boolean dragged;

    public MicrosField(int x, int y, int width, int height, Component label, IntSupplier getter, IntConsumer setter, int step, int min, int max) {
        super(x, y, width, height, label);
        this.getter = getter;
        this.setter = setter;
        this.step = step;
        this.editor.range(min, max);
        this.editor.rangeFormat(v -> Integer.toString((int) Math.round(v)));
    }

    @Override
    public boolean isEditing() {
        return this.editor.editing();
    }

    @Override
    public NumberEditor editor() {
        return this.editor;
    }

    @Override
    public String shownValue() {
        return Integer.toString(this.getter.getAsInt());
    }

    public String editText() {
        String text = this.editor.text();
        return text == null ? "" : text;
    }

    @Override
    public void cancelEdit() {
        this.editor.cancel();
    }

    @Override
    public void commitPending() {
        NumberEntry.Result result = this.editor.result();
        if (result.usable()) {
            this.commitEdit();
        } else {
            this.editor.cancel();
        }
    }

    public void commitEdit() {
        if (!this.editor.editing()) {
            return;
        }
        NumberEntry.Result result = this.editor.finish();
        if (result.usable()) {
            this.setter.accept((int) Math.round(result.value()));
        }
    }

    private void startEdit(boolean selectAll) {
        this.editor.start(Integer.toString(this.getter.getAsInt()), selectAll);
    }

    private void stepBy(int steps) {
        this.setter.accept(this.getter.getAsInt() + steps * this.step);
    }

    private static int multiplier(boolean shift) {
        return shift ? 10 : 1;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        int mult = multiplier(event.hasShiftDown());
        if (event.x() < this.getX() + ARROW) {
            this.commitPending();
            this.stepBy(-mult);
            return;
        }
        if (event.x() >= this.getX() + this.width - ARROW) {
            this.commitPending();
            this.stepBy(mult);
            return;
        }
        if (this.editor.editing()) {
            if (doubleClick && this.editor.entry() != null) {
                this.editor.entry().selectAll();
            } else {
                this.editor.caretFromMouse(Minecraft.getInstance().font, event.x(), this.getX() + this.width / 2);
            }
            return;
        }
        if (doubleClick) {
            this.startEdit(true);
            return;
        }
        this.dragging = true;
        this.dragged = false;
        this.dragAccumulator = 0.0;
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dx, double dy) {
        if (!this.dragging) {
            return;
        }
        this.dragAccumulator += dx;
        int steps = (int) (this.dragAccumulator / DRAG_PIXELS_PER_STEP);
        if (steps != 0) {
            this.dragAccumulator -= steps * DRAG_PIXELS_PER_STEP;
            this.dragged = true;
            this.stepBy(steps * multiplier(event.hasShiftDown()));
        }
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        if (this.dragging && !this.dragged && this.isFocused() && !this.editor.editing()) {
            this.startEdit(true);
        }
        this.dragging = false;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!this.isFocused() || !this.active) {
            return false;
        }
        return this.editor.charTyped(event.codepoint());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!this.active) {
            return false;
        }
        if (this.editor.editing()) {
            NumberEditor.Action action = this.editor.keyPressed(event);
            switch (action) {
                case COMMIT -> {
                    NumberEntry.Result result = this.editor.result();
                    if (result.status() == NumberEntry.Status.INVALID) {
                        return true;
                    }
                    if (result.status() == NumberEntry.Status.EMPTY) {
                        this.editor.cancel();
                    } else {
                        this.commitEdit();
                    }
                    return true;
                }
                case CANCEL -> {
                    this.editor.cancel();
                    return true;
                }
                case STEP_UP, STEP_DOWN -> {
                    this.commitPending();
                    this.stepBy((action == NumberEditor.Action.STEP_UP ? 1 : -1) * multiplier(event.hasShiftDown()));
                    return true;
                }
                case IGNORED -> {
                    return false;
                }
                default -> {
                    return true;
                }
            }
        }
        if (event.isUp() || event.isRight()) {
            this.stepBy(multiplier(event.hasShiftDown()));
            return true;
        }
        if (event.isDown() || event.isLeft()) {
            this.stepBy(-multiplier(event.hasShiftDown()));
            return true;
        }
        if (event.isConfirmation() || event.isSelectAll()) {
            this.startEdit(true);
            return true;
        }
        if (event.key() == InputConstants.KEY_BACKSPACE || event.key() == InputConstants.KEY_DELETE) {
            this.startEdit(false);
            this.editor.keyPressed(event);
            return true;
        }
        if (event.isPaste()) {
            this.editor.startEmpty();
            this.editor.keyPressed(event);
            return true;
        }
        return NumberEditor.numericKey(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.isFocused() || scrollY == 0.0) {
            return false;
        }
        this.commitPending();
        this.stepBy((scrollY > 0 ? 1 : -1) * multiplier(Minecraft.getInstance().hasShiftDown()));
        return true;
    }

    @Override
    public void setFocused(boolean focused) {
        boolean was = this.isFocused();
        super.setFocused(focused);
        if (!focused) {
            this.commitPending();
            this.dragging = false;
        }
        if (was != focused) {
            Minecraft.getInstance().onTextInputFocusChange(this, focused);
        }
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        Font font = Minecraft.getInstance().font;
        int x0 = this.getX();
        int y0 = this.getY();
        int x1 = x0 + this.width;
        boolean editing = this.editor.editing();
        boolean hot = this.active && (this.isHovered() || this.isFocused());
        g.fill(x0, y0, x1, y0 + this.height, hot ? FIELD_HOVER : FIELD);
        int frame = this.editor.frameColor();
        int outline = frame != 0 && (editing || this.editor.noticeActive()) ? frame
                : this.isFocused() ? InputGui.ACCENT : Palette.BORDER_STRONG;
        g.outline(x0, y0, this.width, this.height, outline);
        int textY = y0 + (this.height - 8) / 2;
        if (this.active && (this.isHovered() || editing)) {
            boolean overLeft = this.isHovered() && mouseX < x0 + ARROW;
            boolean overRight = this.isHovered() && mouseX >= x1 - ARROW;
            g.text(font, "‹", x0 + 2, textY, overLeft ? InputGui.ACCENT : InputGui.DIM, false);
            g.text(font, "›", x1 - 2 - font.width("›"), textY, overRight ? InputGui.ACCENT : InputGui.DIM, false);
        }
        if (editing) {
            this.editor.extract(g, font, x0 + this.width / 2, textY, this.width - 2 * ARROW - 4);
            if (this.isFocused()) {
                this.editor.extractHint(g, font, this.getRectangle());
            }
            return;
        }
        int budget = this.active && this.isHovered() ? this.width - 2 * ARROW - 2 : this.width - 4;
        String text = InputGui.fit(font, Integer.toString(this.getter.getAsInt()), budget);
        g.text(font, text, x0 + (this.width - font.width(text)) / 2, textY, this.active ? InputGui.TEXT : InputGui.FAINT, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.empty().append(this.getMessage()).append(" ").append(Integer.toString(this.getter.getAsInt())));
    }
}
