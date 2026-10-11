package de.lardtom.propwash.content.client;

import de.lardtom.propwash.content.DroneWorkbenchMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

public final class WorkbenchChildren {
    private static @Nullable Watch watch;

    private WorkbenchChildren() {
    }

    static void watch(Screen child, Screen parent, DroneWorkbenchMenu menu) {
        watch = new Watch(child, parent, menu);
    }

    public static boolean watching() {
        return watch != null;
    }

    static void tick(Minecraft minecraft) {
        Watch current = watch;
        if (current == null) {
            return;
        }
        Screen screen = minecraft.gui.screen();
        if (minecraft.player == null || minecraft.player.containerMenu != current.menu()) {
            watch = null;
            if (screen == current.child()) {
                minecraft.gui.setScreen(null);
            }
            return;
        }
        if (screen == current.child()) {
            return;
        }
        watch = null;
        if (screen == null) {
            minecraft.gui.setScreen(current.parent());
        }
    }

    private record Watch(Screen child, Screen parent, DroneWorkbenchMenu menu) {
    }
}
