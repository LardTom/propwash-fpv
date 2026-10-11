package de.lardtom.propwash.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.build.Accessories;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.creative.CreativeCodecs;
import de.lardtom.propwash.creative.CreativeSpec;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class DroneBuildCodecs {
    public static final int MAX_KEY_LENGTH = PartIndex.MAX_KEY_LENGTH;

    public static final Codec<FrameType> FRAME = byKey(FrameType.class);
    public static final Codec<StackType> STACK = byKey(StackType.class);
    public static final Codec<MotorType> MOTOR = byKey(MotorType.class);
    public static final Codec<PropType> PROP = byKey(PropType.class);
    public static final Codec<VideoType> VIDEO = byKey(VideoType.class);
    public static final Codec<BatteryType> BATTERY = byKey(BatteryType.class);
    public static final Codec<AccessoryType> ACCESSORY = byKey(AccessoryType.class);
    public static final Codec<AccessoryMount> ACCESSORY_MOUNT = Codec.stringResolver(AccessoryMount::key,
            key -> AccessoryMount.byKey(key).orElse(null));

    public static final StreamCodec<ByteBuf, FrameType> FRAME_STREAM = streamByKey(FrameType.class);
    public static final StreamCodec<ByteBuf, StackType> STACK_STREAM = streamByKey(StackType.class);
    public static final StreamCodec<ByteBuf, MotorType> MOTOR_STREAM = streamByKey(MotorType.class);
    public static final StreamCodec<ByteBuf, PropType> PROP_STREAM = streamByKey(PropType.class);
    public static final StreamCodec<ByteBuf, VideoType> VIDEO_STREAM = streamByKey(VideoType.class);
    public static final StreamCodec<ByteBuf, BatteryType> BATTERY_STREAM = streamByKey(BatteryType.class);
    public static final StreamCodec<ByteBuf, AccessoryType> ACCESSORY_STREAM = streamByKey(AccessoryType.class);
    public static final StreamCodec<ByteBuf, AccessoryMount> ACCESSORY_MOUNT_STREAM = ByteBufCodecs.stringUtf8(16).map(
            key -> AccessoryMount.byKey(key).orElseThrow(() -> new DecoderException("Unbekannte Halterung: " + key)), AccessoryMount::key);

    public static final Codec<Accessories> ACCESSORIES = Codec.unboundedMap(ACCESSORY_MOUNT, Codec.STRING).comapFlatMap(
            DroneBuildCodecs::accessories, DroneBuildCodecs::accessoryKeys);
    public static final StreamCodec<ByteBuf, Accessories> ACCESSORIES_STREAM = ByteBufCodecs.<ByteBuf, AccessoryMount, String, Map<AccessoryMount, String>>map(
            HashMap::new, ACCESSORY_MOUNT_STREAM, ByteBufCodecs.stringUtf8(MAX_KEY_LENGTH), AccessoryMount.values().length).map(map -> {
                DataResult<Accessories> result = accessories(map);
                return result.result().orElseThrow(() -> new DecoderException("Ungültiges Zubehör: "
                        + result.error().map(error -> error.message()).orElse("?")));
            }, DroneBuildCodecs::accessoryKeys);

    public static final Codec<DroneBuild> CODEC = RecordCodecBuilder.create(i -> i.group(
            FRAME.fieldOf("frame").forGetter(DroneBuild::frame),
            STACK.fieldOf("stack").forGetter(DroneBuild::stack),
            MOTOR.fieldOf("motor").forGetter(DroneBuild::motor),
            PROP.fieldOf("prop").forGetter(DroneBuild::prop),
            VIDEO.fieldOf("video").forGetter(DroneBuild::video),
            BATTERY.fieldOf("battery").forGetter(DroneBuild::battery),
            ACCESSORIES.lenientOptionalFieldOf("accessories", Accessories.NONE).forGetter(DroneBuild::accessories),
            CreativeCodecs.CODEC.lenientOptionalFieldOf("creative", CreativeSpec.EMPTY).forGetter(DroneBuild::creative)
    ).apply(i, DroneBuild::new));

    private static final StreamCodec<ByteBuf, DroneBuild> PARTS_STREAM = StreamCodec.composite(
            FRAME_STREAM, DroneBuild::frame,
            STACK_STREAM, DroneBuild::stack,
            MOTOR_STREAM, DroneBuild::motor,
            PROP_STREAM, DroneBuild::prop,
            VIDEO_STREAM, DroneBuild::video,
            BATTERY_STREAM, DroneBuild::battery,
            ACCESSORIES_STREAM, DroneBuild::accessories,
            DroneBuild::new);

    public static final StreamCodec<ByteBuf, DroneBuild> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public DroneBuild decode(ByteBuf buffer) {
            DroneBuild build = PARTS_STREAM.decode(buffer);
            return build.isCreative() ? build.withCreative(CreativeCodecs.STREAM_CODEC.decode(buffer)) : build;
        }

        @Override
        public void encode(ByteBuf buffer, DroneBuild build) {
            PARTS_STREAM.encode(buffer, build);
            if (build.isCreative()) {
                CreativeCodecs.STREAM_CODEC.encode(buffer, build.creative());
            }
        }
    };

    private DroneBuildCodecs() {
    }

    private static DataResult<Accessories> accessories(Map<AccessoryMount, String> keys) {
        EnumMap<AccessoryMount, AccessoryType> map = new EnumMap<>(AccessoryMount.class);
        for (Map.Entry<AccessoryMount, String> entry : keys.entrySet()) {
            Optional<AccessoryType> type = PartIndex.resolveOrMissing(PartKind.ACCESSORY, entry.getValue(), entry.getKey().slot())
                    .filter(AccessoryType.class::isInstance).map(AccessoryType.class::cast);
            if (type.isEmpty()) {
                return DataResult.error(() -> "Unbekanntes Zubehör " + entry.getValue() + " an " + entry.getKey().key());
            }
            map.put(entry.getKey(), type.get());
        }
        try {
            return DataResult.success(new Accessories(map));
        } catch (IllegalArgumentException e) {
            return DataResult.error(e::getMessage);
        }
    }

    private static Map<AccessoryMount, String> accessoryKeys(Accessories accessories) {
        Map<AccessoryMount, String> keys = new LinkedHashMap<>();
        for (Map.Entry<AccessoryMount, AccessoryType> entry : accessories.byMount().entrySet()) {
            keys.put(entry.getKey(), entry.getValue().key());
        }
        return keys;
    }

    public static PartKind kindOf(Class<? extends PartInfo> type) {
        if (type == FrameType.class) {
            return PartKind.FRAME;
        }
        if (type == StackType.class) {
            return PartKind.STACK;
        }
        if (type == MotorType.class) {
            return PartKind.MOTOR;
        }
        if (type == PropType.class) {
            return PartKind.PROP;
        }
        if (type == VideoType.class) {
            return PartKind.VIDEO;
        }
        if (type == BatteryType.class) {
            return PartKind.BATTERY;
        }
        if (type == AccessoryType.class) {
            return PartKind.ACCESSORY;
        }
        throw new IllegalArgumentException("Keine Bauteil-Klasse: " + type);
    }

    public static <E extends PartInfo> @Nullable E resolve(Class<E> type, String key) {
        return PartIndex.resolveOrMissing(type, kindOf(type), key).orElse(null);
    }

    public static <E extends PartInfo> Codec<E> byKey(Class<E> type) {
        return Codec.stringResolver(PartInfo::key, key -> resolve(type, key));
    }

    public static <E extends PartInfo> StreamCodec<ByteBuf, E> streamByKey(Class<E> type) {
        return ByteBufCodecs.stringUtf8(MAX_KEY_LENGTH).map(key -> {
            E part = resolve(type, key);
            if (part == null) {
                throw new DecoderException("Unbekanntes Bauteil: " + key);
            }
            return part;
        }, PartInfo::key);
    }
}
