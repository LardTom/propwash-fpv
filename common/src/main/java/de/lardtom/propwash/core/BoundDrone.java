package de.lardtom.propwash.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PartNames;
import de.lardtom.propwash.build.VideoSystem;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record BoundDrone(UUID drone, String name, FrameType frame, VideoSystem video) {
    public static final int MAX_NAME_LENGTH = 48;

    public static final Codec<VideoSystem> VIDEO_SYSTEM_CODEC = Codec.STRING.comapFlatMap(BoundDrone::parseSystem,
            system -> system.name().toLowerCase(Locale.ROOT));

    public static final StreamCodec<ByteBuf, VideoSystem> VIDEO_SYSTEM_STREAM = ByteBufCodecs.VAR_INT.map(ordinal -> {
        VideoSystem[] values = VideoSystem.values();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new DecoderException("Unbekanntes Videosystem: " + ordinal);
        }
        return values[ordinal];
    }, VideoSystem::ordinal);

    public static final Codec<BoundDrone> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("drone").forGetter(BoundDrone::drone),
            Codec.string(0, MAX_NAME_LENGTH).optionalFieldOf("name", "").forGetter(BoundDrone::name),
            DroneBuildCodecs.FRAME.fieldOf("frame").forGetter(BoundDrone::frame),
            VIDEO_SYSTEM_CODEC.fieldOf("video").forGetter(BoundDrone::video)
    ).apply(i, BoundDrone::new));

    public static final StreamCodec<ByteBuf, BoundDrone> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, BoundDrone::drone,
            ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH), BoundDrone::name,
            DroneBuildCodecs.FRAME_STREAM, BoundDrone::frame,
            VIDEO_SYSTEM_STREAM, BoundDrone::video,
            BoundDrone::new);

    public BoundDrone {
        Objects.requireNonNull(drone, "drone");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(video, "video");
        name = trimName(name);
    }

    public static String trimName(String name) {
        if (name == null) {
            return "";
        }
        return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
    }

    public Component displayName() {
        if (!this.name.isEmpty()) {
            return Component.literal(this.name);
        }
        return PartNames.frameShort(this.frame);
    }

    private static DataResult<VideoSystem> parseSystem(String name) {
        for (VideoSystem system : VideoSystem.values()) {
            if (system.name().toLowerCase(Locale.ROOT).equals(name)) {
                return DataResult.success(system);
            }
        }
        return DataResult.error(() -> "Unbekanntes Videosystem: " + name);
    }
}
