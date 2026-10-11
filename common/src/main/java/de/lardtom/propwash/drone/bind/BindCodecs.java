package de.lardtom.propwash.drone.bind;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;

public final class BindCodecs {
    public static final Codec<ReceiverBind> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.optionalFieldOf("pilot").forGetter(bind -> Optional.ofNullable(bind.pilot()))
    ).apply(i, pilot -> new ReceiverBind(pilot.orElse(null))));

    public static final StreamCodec<ByteBuf, ReceiverBind> STREAM_CODEC = ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC)
            .map(pilot -> new ReceiverBind(pilot.orElse(null)), bind -> Optional.ofNullable(bind.pilot()));

    private BindCodecs() {
    }
}
