package de.lardtom.propwash.rx;

import de.lardtom.propwash.Constants;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public record RxConfigC2S(BlockPos pos, Action action, @Nullable RxSource source, float value) implements CustomPacketPayload {
    public static final Type<RxConfigC2S> TYPE = new Type<>(Constants.id("rx_config"));
    public static final StreamCodec<ByteBuf, RxConfigC2S> CODEC = StreamCodec.of(RxConfigC2S::write, RxConfigC2S::read);

    public enum Action {
        BIND,
        UNBIND,
        SOURCE,
        LOW,
        HIGH,
        RANGE;

        private static final Action[] VALUES = values();
    }

    public RxConfigC2S {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(action, "action");
        if (action == Action.SOURCE && source == null) {
            throw new IllegalArgumentException("RX-Einstellung SOURCE ohne Quelle");
        }
        if (action != Action.SOURCE) {
            source = null;
        }
    }

    public static RxConfigC2S bind(BlockPos pos) {
        return new RxConfigC2S(pos, Action.BIND, null, 0.0F);
    }

    public static RxConfigC2S unbind(BlockPos pos) {
        return new RxConfigC2S(pos, Action.UNBIND, null, 0.0F);
    }

    public static RxConfigC2S source(BlockPos pos, RxSource source) {
        return new RxConfigC2S(pos, Action.SOURCE, source, 0.0F);
    }

    public static RxConfigC2S low(BlockPos pos, float value) {
        return new RxConfigC2S(pos, Action.LOW, null, value);
    }

    public static RxConfigC2S high(BlockPos pos, float value) {
        return new RxConfigC2S(pos, Action.HIGH, null, value);
    }

    public static RxConfigC2S range(BlockPos pos, int range) {
        return new RxConfigC2S(pos, Action.RANGE, null, range);
    }

    private static void write(ByteBuf buf, RxConfigC2S payload) {
        BlockPos.STREAM_CODEC.encode(buf, payload.pos);
        buf.writeByte(payload.action.ordinal());
        switch (payload.action) {
            case SOURCE -> RxSource.STREAM_CODEC.encode(buf, Objects.requireNonNull(payload.source));
            case LOW, HIGH -> buf.writeFloat(payload.value);
            case RANGE -> VarInt.write(buf, (int) payload.value);
            default -> {
            }
        }
    }

    private static RxConfigC2S read(ByteBuf buf) {
        BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
        int ordinal = buf.readUnsignedByte();
        if (ordinal >= Action.VALUES.length) {
            throw new DecoderException("Unbekannte RX-Einstellung: " + ordinal);
        }
        Action action = Action.VALUES[ordinal];
        return switch (action) {
            case SOURCE -> new RxConfigC2S(pos, action, RxSource.STREAM_CODEC.decode(buf), 0.0F);
            case LOW, HIGH -> new RxConfigC2S(pos, action, null, buf.readFloat());
            case RANGE -> new RxConfigC2S(pos, action, null, VarInt.read(buf));
            default -> new RxConfigC2S(pos, action, null, 0.0F);
        };
    }

    @Override
    public Type<RxConfigC2S> type() {
        return TYPE;
    }
}
