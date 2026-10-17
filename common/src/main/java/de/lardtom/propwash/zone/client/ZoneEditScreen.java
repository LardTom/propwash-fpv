package de.lardtom.propwash.zone.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.race.client.GateEditor;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.ui.client.widget.TextField;
import de.lardtom.propwash.zone.ZoneDraft;
import de.lardtom.propwash.zone.ZoneLimits;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.ToggleKeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class ZoneEditScreen extends Screen implements ThemedScreen {
    private static final int TOOLTIP_WIDTH = 220;
    private static final int TOOLTIP_GAP = 2;
    private final ZoneEditor editor;
    private boolean sticky;
    private @Nullable ZoneEditPanel panel;
    private @Nullable TextField name;
    private boolean windowActive;

    ZoneEditScreen(ZoneEditor editor) {
        this(editor, false);
    }

    ZoneEditScreen(ZoneEditor editor, boolean sticky) {
        super(Component.translatable(ZoneEditPanel.KEY + "title.edit"));
        this.editor = editor;
        this.sticky = sticky;
    }

    public static boolean openSticky() {
        ZoneEditor editor = ZoneEditor.current();
        if (editor == null) {
            return false;
        }
        Minecraft.getInstance().gui.setScreen(new ZoneEditScreen(editor, true));
        return true;
    }

    public ZoneEditor editor() {
        return this.editor;
    }

    public @Nullable ZoneEditPanel panel() {
        return this.panel;
    }

    public @Nullable TextField nameField() {
        return this.name;
    }

    private static List<KeyMapping> movement(Options options) {
        return List.of(options.keyUp, options.keyDown, options.keyLeft, options.keyRight, options.keyJump, options.keyShift, options.keySprint);
    }

    @Override
    protected void init() {
        ZoneEditPanel built = new ZoneEditPanel(this.editor, true);
        built.layout(this.width, this.height);
        this.panel = built;
        ScreenRectangle field = built.nameField();
        TextField text = new TextField(this.font, field.left(), field.top(), field.width(), field.height(),
                Component.translatable(ZoneEditPanel.KEY + "row.name"));
        text.setMaxLength(ZoneLimits.MAX_NAME);
        text.setValue(this.editor.draft().name());
        text.setResponder(this.editor.model()::setName);
        this.name = this.addRenderableWidget(text);
        this.button(built, 0, ZoneEditPanel.KEY + "button.apply", FlatButton.Style.PRIMARY, this.editor::apply);
        this.button(built, 1, ZoneEditPanel.KEY + "button.cancel", FlatButton.Style.NORMAL, this.editor::cancel);
        this.button(built, 2, ZoneEditPanel.KEY + "button.center", FlatButton.Style.NORMAL, this.editor::centerOnPlayer);
        if (!this.editor.model().isNew()) {
            this.button(built, 3, ZoneEditPanel.KEY + "button.delete", FlatButton.Style.DANGER, this.editor::delete);
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

    private void button(ZoneEditPanel built, int index, String key, FlatButton.Style style, Runnable action) {
        ScreenRectangle rect = built.button(index);
        this.addRenderableWidget(new FlatButton(rect.left(), rect.top(), rect.width(), rect.height(), Component.translatable(key), style, action));
    }

    @Override
    public void tick() {
        if (ZoneEditor.current() != this.editor) {
            this.onClose();
            return;
        }
        boolean active = this.minecraft.isWindowActive();
        if (this.windowActive && !active && !this.sticky) {
            this.onClose();
            return;
        }
        this.windowActive = active;
        ZoneEditPanel built = this.panel;
        if (built != null) {
            built.layout(this.width, this.height);
        }
    }

    private boolean typing() {
        return this.name != null && this.name.isFocused();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        KeyMapping cursor = GateEditor.cursorKey();
        if (cursor != null && cursor.matches(event)) {
            this.sticky = false;
            return true;
        }
        if (this.typing()) {
            if (event.isEscape()) {
                this.setFocused(null);
                return true;
            }
            if (event.isConfirmation()) {
                this.editor.apply();
                return true;
            }
            return super.keyPressed(event);
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
        int key = event.key();
        if (key == InputConstants.KEY_DELETE || key == InputConstants.KEY_BACKSPACE) {
            this.editor.reset(this.editor.model().selected());
            return true;
        }
        if (key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN) {
            this.editor.model().moveSelection(key == InputConstants.KEY_UP ? -1 : 1);
            return true;
        }
        if (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_RIGHT) {
            this.editor.change(this.editor.model().selected(), key == InputConstants.KEY_RIGHT ? 1 : -1);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        KeyMapping cursor = GateEditor.cursorKey();
        if (cursor != null && cursor.matches(event)) {
            if (!this.sticky && !this.typing()) {
                this.onClose();
            }
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        ZoneEditPanel built = this.panel;
        if (built != null) {
            ZoneDraft.Row row = built.rowAt(event.x(), event.y());
            if (row != null) {
                this.setFocused(null);
                if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                    this.editor.reset(row);
                    return true;
                }
                int arrow = built.arrowAt(event.x(), event.y());
                if (arrow != 0) {
                    this.editor.change(row, arrow);
                } else if (row.kind() == ZoneDraft.Kind.SHAPE || row.kind() == ZoneDraft.Kind.RACE_LEGAL) {
                    this.editor.change(row, 1);
                } else {
                    this.editor.model().select(row);
                }
                return true;
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
        ZoneEditPanel built = this.panel;
        ZoneDraft.Row row = built == null ? null : built.rowAt(x, y);
        this.editor.change(row != null ? row : this.editor.model().selected(), notches);
        return true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        if (this.panel != null) {
            this.panel.draw(g, mouseX, mouseY);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        ZoneEditPanel built = this.panel;
        if (built == null) {
            return;
        }
        ZoneDraft.Row row = built.rowAt(mouseX, mouseY);
        if (row != null) {
            Component tip = built.tooltip(row);
            if (tip != null) {
                ThemeText.tooltipWrapped(g, this.font, tip, TOOLTIP_WIDTH, built.right() + TOOLTIP_GAP, mouseY);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
