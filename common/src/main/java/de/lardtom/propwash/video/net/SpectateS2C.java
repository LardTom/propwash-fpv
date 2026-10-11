package de.lardtom.propwash.video.net;

import de.lardtom.propwash.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Locale;
import java.util.UUID;

public record SpectateS2C(Result result, UUID drone, int entityId) implements CustomPacketPayload {
    public static final UUID NONE = new UUID(0L, 0L);
    public static final Type<SpectateS2C> TYPE = new Type<>(Constants.id("video_spectate_result"));
    private static final StreamCodec<ByteBuf, Result> RESULT = ByteBufCodecs.idMapper(Result::byOrdinal, Result::ordinal);
    public static final StreamCodec<ByteBuf, SpectateS2C> CODEC = StreamCodec.composite(
            RESULT, SpectateS2C::result,
            UUIDUtil.STREAM_CODEC, SpectateS2C::drone,
            ByteBufCodecs.VAR_INT, SpectateS2C::entityId,
            SpectateS2C::new);

    public static SpectateS2C ended(Result result) {
        return new SpectateS2C(result, NONE, -1);
    }

    @Override
    public Type<SpectateS2C> type() {
        return TYPE;
    }

    public enum Result {
        STARTED,
        STOPPED,
        NOT_FOUND,
        RULE_OFF,
        NO_GOGGLES,
        WRONG_SYSTEM,
        PILOTING,
        SIGNAL_ENDED,
        OTHER_DIMENSION;

        public static Result byOrdinal(int ordinal) {
            Result[] all = values();
            return ordinal >= 0 && ordinal < all.length ? all[ordinal] : STOPPED;
        }

        public String translationKey() {
            return "propwash.video.spectate.result." + this.name().toLowerCase(Locale.ROOT);
        }
    }
}
