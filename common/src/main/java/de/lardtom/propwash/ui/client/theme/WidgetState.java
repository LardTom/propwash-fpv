package de.lardtom.propwash.ui.client.theme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;

public record WidgetState(boolean active, boolean hovered, boolean focused, boolean pressed) {
    public static final WidgetState IDLE = new WidgetState(true, false, false, false);

    public static WidgetState of(AbstractWidget widget) {
        boolean hovered = widget.isHovered();
        boolean pressed = hovered && widget.active && leftPressed();
        return new WidgetState(widget.active, hovered, widget.isFocused(), pressed);
    }

    public static WidgetState of(AbstractWidget widget, boolean hovered) {
        boolean pressed = hovered && widget.active && leftPressed();
        return new WidgetState(widget.active, hovered, widget.isFocused(), pressed);
    }

    private static boolean leftPressed() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.mouseHandler != null && minecraft.mouseHandler.isLeftPressed();
    }

    public boolean hot() {
        return this.active && (this.hovered || this.focused);
    }

    public WidgetState withFocused(boolean focused) {
        return new WidgetState(this.active, this.hovered, focused, this.pressed);
    }

    public WidgetState withActive(boolean active) {
        return new WidgetState(active, this.hovered, this.focused, this.pressed && active);
    }
}
