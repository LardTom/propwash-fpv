package de.lardtom.propwash.drone.data;

import de.lardtom.propwash.registry.PropwashRegistries;
import de.lardtom.propwash.registry.RegHolder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.syncher.EntityDataSerializer;

public final class DroneDataComponents {
    public static final String NAME = "addon_data";

    public static final RegHolder<DataComponentType<AddonData>> ADDON_DATA = PropwashRegistries.component(NAME,
            b -> b.persistent(AddonData.CODEC).networkSynchronized(AddonData.STREAM_CODEC));

    public static final EntityDataSerializer<AddonData> SERIALIZER = PropwashRegistries.entityDataSerializer(NAME,
            EntityDataSerializer.forValueType(AddonData.STREAM_CODEC));

    private DroneDataComponents() {
    }

    static void bootstrap() {
    }
}
