package de.lardtom.propwash.rx;

import de.lardtom.propwash.Constants;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Arrays;

public record RxInputC2S(int sequence, RxMask mask, short[] values, int pressed) implements CustomPacketPayload {
    public static final Type<RxInputC2S> TYPE = new Type<>(Constants.id("rx_input"));
    public static final StreamCodec<ByteBuf, RxInputC2S> CODEC = StreamCodec.of(RxInputC2S::write, RxInputC2S::read);
    public static final int FULL_SCALE = 32767;

    public RxInputC2S {
        values = values.clone();
        if (values.length != mask.analogCount()) {
            throw new IllegalArgumentException("RX-Werte passen nicht zur Maske: " + values.length + " statt " + mask.analogCount());
        }
        pressed &= mask.buttons();
    }

    public static short quantize(float value) {
        if (!Float.isFinite(value)) {
            return 0;
        }
        return (short) Math.clamp(Math.round(value * FULL_SCALE), -FULL_SCALE, FULL_SCALE);
    }

    public static float value(short raw) {
        return Math.clamp(raw, -FULL_SCALE, FULL_SCALE) / (float) FULL_SCALE;
    }

    public static RxInputC2S of(int sequence, RxMask mask, float[] sticks, float[] axes, int pressed) {
        short[] values = new short[mask.analogCount()];
        int at = 0;
        for (int i = 0; i < RxSource.STICKS; i++) {
            if ((mask.sticks() & (1 << i)) != 0) {
                values[at++] = quantize(i < sticks.length ? sticks[i] : 0.0F);
            }
        }
        for (int i = 0; i < RxSource.AXES; i++) {
            if ((mask.axes() & (1 << i)) != 0) {
                values[at++] = quantize(i < axes.length ? axes[i] : 0.0F);
            }
        }
        return new RxInputC2S(sequence, mask, values, pressed);
    }

    public void unpack(float[] sticks, float[] axes) {
        int at = 0;
        for (int i = 0; i < RxSource.STICKS; i++) {
            if ((this.mask.sticks() & (1 << i)) != 0) {
                sticks[i] = value(this.values[at++]);
            }
        }
        for (int i = 0; i < RxSource.AXES; i++) {
            if ((this.mask.axes() & (1 << i)) != 0) {
                axes[i] = value(this.values[at++]);
            }
        }
    }

    public float valueOf(RxSource source) {
        if (!this.mask.contains(source)) {
            return Float.NaN;
        }
        if (source.kind() == RxSource.Kind.BUTTON) {
            return (this.pressed & source.bit()) != 0 ? 1.0F : 0.0F;
        }
        int at = 0;
        for (int i = 0; i < RxSource.STICKS; i++) {
            if ((this.mask.sticks() & (1 << i)) != 0) {
                if (source.kind() == RxSource.Kind.STICK && source.index() == i) {
                    return value(this.values[at]);
                }
                at++;
            }
        }
        for (int i = 0; i < RxSource.AXES; i++) {
            if ((this.mask.axes() & (1 << i)) != 0) {
                if (source.kind() == RxSource.Kind.AXIS && source.index() == i) {
                    return value(this.values[at]);
                }
                at++;
            }
        }
        return Float.NaN;
    }

    private static void write(ByteBuf buf, RxInputC2S payload) {
        VarInt.write(buf, payload.sequence);
        RxMask.STREAM_CODEC.encode(buf, payload.mask);
        for (short value : payload.values) {
            buf.writeShort(value);
        }
        buf.writeInt(payload.pressed);
    }

    private static RxInputC2S read(ByteBuf buf) {
        int sequence = VarInt.read(buf);
        RxMask mask = RxMask.STREAM_CODEC.decode(buf);
        int count = mask.analogCount();
        if (buf.readableBytes() < count * 2 + 4) {
            throw new DecoderException("RX-Werte unvollständig");
        }
        short[] values = new short[count];
        for (int i = 0; i < count; i++) {
            values[i] = buf.readShort();
        }
        int pressed = buf.readInt();
        return new RxInputC2S(sequence, mask, values, pressed);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RxInputC2S that && this.sequence == that.sequence && this.mask.equals(that.mask) && Arrays.equals(this.values, that.values)
                && this.pressed == that.pressed;
    }

    @Override
    public int hashCode() {
        return 31 * (31 * (31 * this.sequence + this.mask.hashCode()) + Arrays.hashCode(this.values)) + this.pressed;
    }

    @Override
    public String toString() {
        return "RxInputC2S[" + this.sequence + ", " + this.mask.describe() + ", " + Arrays.toString(this.values) + ", " + this.pressed + "]";
    }

    @Override
    public Type<RxInputC2S> type() {
        return TYPE;
    }
}
