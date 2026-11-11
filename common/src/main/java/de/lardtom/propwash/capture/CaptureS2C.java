package de.lardtom.propwash.capture;

import de.lardtom.propwash.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

public record CaptureS2C(int storageRadius, List<Entry> areas, int answerEntity, int answerStatus) implements CustomPacketPayload {
    public static final int NO_ANSWER = -1;
    public static final int MAX_ENTRIES = 16;
    public static final Type<CaptureS2C> TYPE = new Type<>(Constants.id("camera_chunks"));
    public static final StreamCodec<ByteBuf, CaptureS2C> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CaptureS2C::storageRadius,
            Entry.CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)), CaptureS2C::areas,
            ByteBufCodecs.VAR_INT, CaptureS2C::answerEntity,
            ByteBufCodecs.VAR_INT, CaptureS2C::answerStatus,
            CaptureS2C::new);

    public CaptureS2C {
        areas = List.copyOf(areas);
    }

    @Override
    public Type<CaptureS2C> type() {
        return TYPE;
    }

    public record Entry(int entityId, int radius, boolean paused) {
        public static final StreamCodec<ByteBuf, Entry> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Entry::entityId,
                ByteBufCodecs.VAR_INT, Entry::radius,
                ByteBufCodecs.BOOL, Entry::paused,
                Entry::new);
    }
}
