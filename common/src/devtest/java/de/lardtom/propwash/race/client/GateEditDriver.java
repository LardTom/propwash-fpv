package de.lardtom.propwash.race.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.race.CourseElement;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

final class GateEditDriver {
    private GateEditDriver() {
    }

    static void scroll(Minecraft mc, int notches) {
        double direction = Math.signum(notches);
        for (int i = 0; i < Math.abs(notches); i++) {
            mc.mouseHandler.onScroll(mc.getWindow().handle(), 0.0, direction);
        }
    }

    static void sneakScroll(Minecraft mc, int notches) {
        mc.options.keyShift.setDown(true);
        scroll(mc, notches);
        mc.options.keyShift.setDown(false);
    }

    static boolean select(Minecraft mc, GateEditor editor, GateEditModel.Row row) {
        for (int i = 0; i < GateEditModel.Row.values().length && editor.model().selected() != row; i++) {
            sneakScroll(mc, -1);
        }
        return editor.model().selected() == row;
    }

    static void press(Minecraft mc, int key) {
        KeyEvent event = new KeyEvent(key, 0, 0);
        mc.keyboardHandler.keyPress(mc.getWindow().handle(), InputConstants.PRESS, event);
        mc.keyboardHandler.keyPress(mc.getWindow().handle(), InputConstants.RELEASE, event);
    }

    static void release(Minecraft mc, int key) {
        mc.keyboardHandler.keyPress(mc.getWindow().handle(), InputConstants.RELEASE, new KeyEvent(key, 0, 0));
    }

    static void rightClick(Minecraft mc) {
        KeyMapping.click(InputConstants.getKey(mc.options.keyUse.saveString()));
    }

    static boolean cursorHeld(Minecraft mc, boolean held) {
        KeyMapping key = GateEditor.cursorKey();
        if (key == null) {
            return false;
        }
        if (held) {
            key.setDown(true);
            return true;
        }
        release(mc, InputConstants.getKey(key.saveString()).getValue());
        key.setDown(false);
        return true;
    }

    static @Nullable GateEditPanel panel(Minecraft mc) {
        return mc.gui.screen() instanceof GateEditCursorScreen screen ? screen.panel() : null;
    }

    static boolean click(Minecraft mc, AbstractWidget widget) {
        if (!(mc.gui.screen() instanceof GateEditCursorScreen screen)) {
            return false;
        }
        double x = widget.getX() + widget.getWidth() / 2.0;
        double y = widget.getY() + widget.getHeight() / 2.0;
        MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        boolean handled = screen.mouseClicked(event, false);
        screen.mouseReleased(event);
        return handled;
    }

    static void hover(Minecraft mc, AbstractWidget widget) {
        double scaleX = (double) mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
        double scaleY = (double) mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight();
        mc.mouseHandler.onMove(mc.getWindow().handle(), (widget.getX() + widget.getWidth() * 0.62) * scaleX,
                (widget.getY() + widget.getHeight() / 2.0) * scaleY, 0.0, 0.0);
    }

    static @Nullable String titleCut(Minecraft mc) {
        for (CourseElement kind : CourseElement.values()) {
            Component title = GateEditPanel.title(kind);
            int needed = ThemeDraw.brandedTitleX(mc.font, 0) + mc.font.width(title) + Metrics.PADDING;
            int width = GateEditPanel.panelWidth(mc.font, title);
            if (needed > width) {
                return "Titel „" + title.getString() + "“ braucht " + needed + " statt " + width + " GUI-Pixel";
            }
        }
        return null;
    }
}
