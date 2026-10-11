package de.lardtom.propwash.content.client;

import de.lardtom.propwash.content.ContentBlocks;
import de.lardtom.propwash.content.TooltipShift;
import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import net.minecraft.client.Minecraft;

public final class ContentClient {
    private static final int TOOLTIP_FRAME = 14;
    private static final int TOOLTIP_LINE = 10;

    private ContentClient() {
    }

    public static void init() {
        TooltipShift.SHIFT = () -> Minecraft.getInstance().hasShiftDown();
        TooltipShift.LINES = () -> (Minecraft.getInstance().getWindow().getGuiScaledHeight() - TOOLTIP_FRAME) / TOOLTIP_LINE;
        ClientRegistrar.menuScreen(ContentBlocks.DRONE_WORKBENCH_MENU, DroneWorkbenchScreen::new);
        ClientRegistrar.menuScreen(ContentBlocks.BATTERY_CHARGER_MENU, BatteryChargerScreen::new);
        ClientRegistrar.blockTints(ContentBlocks.BATTERY_CHARGER.block(), ChargerPackTint.layers());
        ClientBus.TICK_END.register(WorkbenchChildren::tick);
    }
}
