package de.lardtom.propwash.osd.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.osd.OsdElement;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

public final class OsdTypingSupport {
    private OsdTypingSupport() {
    }

    public static boolean selectElement(OsdConfigTab tab, boolean options) {
        if (tab.page() != (options ? OsdConfigTab.Page.OPTIONS : OsdConfigTab.Page.STYLE)) {
            tab.switchPage();
            return false;
        }
        if (tab.selected() != null) {
            return true;
        }
        for (OsdElement element : OsdElement.values()) {
            if (!element.movable() || !tab.renderer().wasDrawn(element)) {
                continue;
            }
            float[] b = tab.renderer().bounds(element);
            MouseButtonEvent event = new MouseButtonEvent((b[0] + b[2]) * 0.5, (b[1] + b[3]) * 0.5,
                    new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
            tab.mouseClicked(event, false);
            tab.mouseReleased(event);
            tab.endDrag();
            return tab.selected() != null;
        }
        return false;
    }
}
