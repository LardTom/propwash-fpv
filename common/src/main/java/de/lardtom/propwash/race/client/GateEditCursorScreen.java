package de.lardtom.propwash.race.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.ToggleKeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class GateEditCursorScreen extends Screen implements ThemedScreen {
    private final GateEditor editor;
    private @Nullable GateEditPanel panel;
    private boolean windowActive;

    GateEditCursorScreen(GateEditor editor) {
        super(GateEditPanel.title(editor.gate().kind()));
        this.editor = editor;
    }

    public GateEditor editor() {
        return this.editor;
    }

    public @Nullable GateEditPanel panel() {
        return this.panel;
    }

    private static List<KeyMapping> movement(Options options) {
        return List.of(options.keyUp, options.keyDown, options.keyLeft, options.keyRight, options.keyJump, options.keyShift, options.keySprint);
    }

    @Override
    protected void init() {
        GateEditPanel built = new GateEditPanel(this.editor, true);
        built.layout(this.width, this.height);
        this.panel = built;
        for (AbstractWidget widget : built.widgets()) {
            this.addRenderableWidget(widget);
        }
        this.windowActive = this.minecraft.isWindowActive();
        for (KeyMapping mapping : movement(this.minecraft.options)) {
            if (mapping instanceof ToggleKeyMapping) {
                continue;
            }
            InputConstants.Key key = InputConstants.getKey(mapping.saveString());
            if (key.getType() == InputConstants.Type.KEYBOARD && InputConstants.isKeyDown(key.getValue())) {
                mapping.setDown(true);
            }
        }
    }

    @Override
    public void tick() {
        if (GateEditor.current() != this.editor) {
            this.onClose();
            return;
        }
        boolean active = this.minecraft.isWindowActive();
        if (this.windowActive && !active) {
            this.onClose();
            return;
        }
        this.windowActive = active;
        GateEditPanel built = this.panel;
        if (built == null || built.stale(this.width, this.height)) {
            this.rebuildWidgets();
            return;
        }
        built.sync();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        KeyMapping cursor = GateEditor.cursorKey();
        if (cursor != null && cursor.matches(event)) {
            return true;
        }
        boolean moved = false;
        for (KeyMapping mapping : movement(this.minecraft.options)) {
            if (mapping.matches(event)) {
                mapping.setDown(true);
                moved = true;
            }
        }
        if (moved) {
            return true;
        }
        if (event.isEscape()) {
            this.editor.cancel();
            return true;
        }
        if (event.isConfirmation()) {
            this.editor.apply();
            return true;
        }
        KeyMapping rotate = GateMountKeys.mapping();
        if (rotate != null && rotate.matches(event)) {
            this.editor.rotateKey(this.minecraft.options.keyShift.isDown());
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        KeyMapping cursor = GateEditor.cursorKey();
        if (cursor != null && cursor.matches(event)) {
            this.onClose();
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        GateEditPanel built = this.panel;
        if (built != null) {
            GateEditModel.Row row = built.rowAt(event.x(), event.y());
            if (row != null) {
                this.editor.model().select(row);
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        int notches = GateEditor.notches(scrollX, scrollY);
        if (notches == 0) {
            return true;
        }
        GateEditPanel built = this.panel;
        GateEditModel.Row row = built == null ? null : built.rowAt(x, y);
        this.editor.change(row != null ? row : this.editor.model().selected(), notches);
        return true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        if (this.panel != null) {
            this.panel.background(g);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        if (this.panel != null) {
            this.panel.foreground(g);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public static boolean open() {
        Minecraft minecraft = Minecraft.getInstance();
        GateEditor editor = GateEditor.current();
        if (editor == null || minecraft.gui.screen() != null) {
            return false;
        }
        minecraft.gui.setScreen(new GateEditCursorScreen(editor));
        return true;
    }
}
