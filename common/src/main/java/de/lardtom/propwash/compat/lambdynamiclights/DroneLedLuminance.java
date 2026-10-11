package de.lardtom.propwash.compat.lambdynamiclights;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.led.DroneLeds;
import dev.lambdaurora.lambdynlights.api.entity.luminance.EntityLuminance;
import dev.lambdaurora.lambdynlights.api.item.ItemLightSourceManager;
import net.minecraft.world.entity.Entity;

public final class DroneLedLuminance implements EntityLuminance {
    public static final DroneLedLuminance INSTANCE = new DroneLedLuminance();
    public static final Type TYPE = Type.registerSimple(Constants.id("drone_leds"), INSTANCE);

    private DroneLedLuminance() {
    }

    @Override
    public Type type() {
        return TYPE;
    }

    @Override
    public int getLuminance(ItemLightSourceManager itemLightSourceManager, Entity entity) {
        if (entity instanceof QuadcopterEntity drone && !drone.isRemoved()) {
            return DroneLeds.luminance(drone);
        }
        return 0;
    }
}
