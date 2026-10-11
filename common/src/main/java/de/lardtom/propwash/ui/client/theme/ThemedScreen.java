package de.lardtom.propwash.ui.client.theme;

import net.minecraft.client.Minecraft;

public interface ThemedScreen {
    static boolean active() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.gui != null && minecraft.gui.screen() instanceof ThemedScreen;
    }
}
