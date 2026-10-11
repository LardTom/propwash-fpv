package de.lardtom.propwash.interact.client;

import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.interact.CargoMass;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemedToast;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class CargoMessages {
    public static final Object TOAST_TOKEN = new Object();
    private static final long TOAST_MILLIS = 2500L;

    private CargoMessages() {
    }

    static void nothingToDrop(QuadcopterEntity drone) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        String key = CargoMass.hasGripper(drone.build()) ? "propwash.interact.cargo.empty" : "propwash.interact.cargo.no_gripper";
        ThemedToast.show(mc.gui.toastManager(), TOAST_TOKEN, Component.translatable(key), null, Palette.WARNING, TOAST_MILLIS);
    }
}
