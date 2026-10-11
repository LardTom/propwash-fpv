package de.lardtom.propwash.core;

import com.mojang.serialization.Codec;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.registry.PropwashRegistries;
import de.lardtom.propwash.registry.RegHolder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.syncher.EntityDataSerializer;

import java.util.UUID;

public final class CoreComponents {
    public static final RegHolder<DataComponentType<DroneBuild>> DRONE_BUILD = PropwashRegistries.component("drone_build",
            b -> b.persistent(DroneBuildCodecs.CODEC).networkSynchronized(DroneBuildCodecs.STREAM_CODEC).cacheEncoding());

    public static final RegHolder<DataComponentType<Float>> BATTERY_CHARGE = PropwashRegistries.component("battery_charge",
            b -> b.persistent(Codec.floatRange(0.0F, 1.0F)).networkSynchronized(ByteBufCodecs.FLOAT));

    public static final RegHolder<DataComponentType<PartRef>> PART = PropwashRegistries.component("part",
            b -> b.persistent(PartRef.CODEC).networkSynchronized(PartRef.STREAM_CODEC).cacheEncoding());

    public static final RegHolder<DataComponentType<FlightTune>> FLIGHT_TUNE = PropwashRegistries.component("flight_tune",
            b -> b.persistent(FlightTune.CODEC).networkSynchronized(FlightTune.STREAM_CODEC).cacheEncoding());

    public static final RegHolder<DataComponentType<BoundDrone>> BOUND_DRONE = PropwashRegistries.component("bound_drone",
            b -> b.persistent(BoundDrone.CODEC).networkSynchronized(BoundDrone.STREAM_CODEC).cacheEncoding());

    public static final RegHolder<DataComponentType<UUID>> DRONE_ID = PropwashRegistries.component("drone_id",
            b -> b.persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC));

    public static final RegHolder<DataComponentType<Float>> CAMERA_CHARGE = PropwashRegistries.component("camera_charge",
            b -> b.persistent(Codec.floatRange(0.0F, 1.0F)).networkSynchronized(ByteBufCodecs.FLOAT));

    public static final RegHolder<DataComponentType<Boolean>> BATTERY_INSTALLED = PropwashRegistries.component("battery_installed",
            b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    public static final RegHolder<DataComponentType<PartWear>> PART_WEAR = PropwashRegistries.component("part_wear",
            b -> b.persistent(WearCodecs.CODEC).networkSynchronized(WearCodecs.STREAM_CODEC).cacheEncoding());

    public static final RegHolder<DataComponentType<Float>> PART_CONDITION = PropwashRegistries.component("part_condition",
            b -> b.persistent(WearCodecs.CONDITION).networkSynchronized(ByteBufCodecs.FLOAT));

    public static final EntityDataSerializer<DroneBuild> DRONE_BUILD_SERIALIZER = PropwashRegistries.entityDataSerializer("drone_build",
            EntityDataSerializer.forValueType(DroneBuildCodecs.STREAM_CODEC));

    public static final EntityDataSerializer<FlightTune> FLIGHT_TUNE_SERIALIZER = PropwashRegistries.entityDataSerializer("flight_tune",
            EntityDataSerializer.forValueType(FlightTune.STREAM_CODEC));

    public static final EntityDataSerializer<PartWear> PART_WEAR_SERIALIZER = PropwashRegistries.entityDataSerializer("part_wear",
            EntityDataSerializer.forValueType(WearCodecs.STREAM_CODEC));

    private CoreComponents() {
    }

    static void bootstrap() {
    }
}
