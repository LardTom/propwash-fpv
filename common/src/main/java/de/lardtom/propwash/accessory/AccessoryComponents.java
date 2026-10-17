package de.lardtom.propwash.accessory;

import de.lardtom.propwash.registry.PropwashRegistries;
import de.lardtom.propwash.registry.RegHolder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.syncher.EntityDataSerializer;

public final class AccessoryComponents {
    public static final RegHolder<DataComponentType<AccessoryContents>> CONTENTS = PropwashRegistries.component("accessory_contents",
            b -> b.persistent(AccessoryContents.CODEC).networkSynchronized(AccessoryContents.STREAM_CODEC).cacheEncoding());

    public static final EntityDataSerializer<AccessoryContents> CONTENTS_SERIALIZER = PropwashRegistries.entityDataSerializer(
            "accessory_contents", EntityDataSerializer.forValueType(AccessoryContents.STREAM_CODEC));

    private AccessoryComponents() {
    }

    static void bootstrap() {
    }
}
