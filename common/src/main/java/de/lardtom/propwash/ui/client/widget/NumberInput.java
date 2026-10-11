package de.lardtom.propwash.ui.client.widget;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import org.jspecify.annotations.Nullable;

public interface NumberInput {
    boolean isEditing();

    void cancelEdit();

    void commitPending();

    NumberEditor editor();

    String shownValue();

    default boolean typeable() {
        return true;
    }

    static boolean cancelEditing(@Nullable GuiEventListener focused) {
        if (focused instanceof NumberInput input && input.isEditing()) {
            input.cancelEdit();
            return true;
        }
        return false;
    }

    static void releaseOnClickAway(ContainerEventHandler screen, double mouseX, double mouseY) {
        GuiEventListener focused = screen.getFocused();
        if (focused instanceof NumberInput && focused instanceof AbstractWidget widget
                && !(widget.visible && mouseX >= widget.getX() && mouseX < widget.getX() + widget.getWidth()
                && mouseY >= widget.getY() && mouseY < widget.getY() + widget.getHeight())) {
            screen.setFocused(null);
        }
    }
}
