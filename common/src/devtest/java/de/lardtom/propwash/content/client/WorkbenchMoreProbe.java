package de.lardtom.propwash.content.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class WorkbenchMoreProbe {
    private WorkbenchMoreProbe() {
    }

    public static boolean open(int count) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof DroneWorkbenchScreen workbench)) {
            return false;
        }
        List<WorkbenchHost.Entry> entries = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Component label = Component.literal("Add-on-Eintrag " + i + (i % 3 == 0 ? " mit einem ziemlich langen Namen für die Werkbank" : ""));
            entries.add(new WorkbenchHost.Entry(label, null, null, Component.literal("Tooltip " + i), () -> {
            }));
        }
        mc.gui.setScreen(new WorkbenchMoreScreen(workbench, workbench.getMenu(), entries));
        return true;
    }
}
