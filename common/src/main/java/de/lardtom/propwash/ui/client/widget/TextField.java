package de.lardtom.propwash.ui.client.widget;

import de.lardtom.propwash.ui.client.theme.Palette;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public class TextField extends EditBox {
    public TextField(Font font, int x, int y, int width, int height, Component narration) {
        super(font, x, y, width, height, narration);
    }

    public TextField hint(Component hint) {
        this.setHint(hint.copy().withColor(Palette.TEXT_FAINT & 0xFFFFFF));
        return this;
    }
}
