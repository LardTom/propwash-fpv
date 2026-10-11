package de.lardtom.propwash.content.paint;

import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.registry.PropwashRegistries;
import de.lardtom.propwash.registry.RegHolder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.syncher.EntityDataSerializer;

public final class PaintComponents {
    public static final RegHolder<DataComponentType<DronePaint>> DRONE_PAINT = PropwashRegistries.component("drone_paint",
            b -> b.persistent(PaintCodecs.CODEC).networkSynchronized(PaintCodecs.STREAM_CODEC).cacheEncoding());

    public static final EntityDataSerializer<DronePaint> DRONE_PAINT_SERIALIZER = PropwashRegistries.entityDataSerializer("drone_paint",
            EntityDataSerializer.forValueType(PaintCodecs.STREAM_CODEC));

    private PaintComponents() {
    }

    public static void bootstrap() {
    }
}
