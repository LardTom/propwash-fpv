package de.lardtom.propwash.rx;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.StreamCodec;

public record RxMask(int sticks, int axes, int buttons) {
    public static final int STICK_BITS = (1 << RxSource.STICKS) - 1;
    public static final RxMask NONE = new RxMask(0, 0, 0);
    public static final StreamCodec<ByteBuf, RxMask> STREAM_CODEC = StreamCodec.of(RxMask::write, RxMask::read);

    public RxMask {
        if ((sticks & ~STICK_BITS) != 0) {
            throw new IllegalArgumentException("Knüppel-Maske außerhalb von 4 Bit: " + Integer.toBinaryString(sticks));
        }
    }

    public static RxMask of(RxSource source) {
        return NONE.with(source);
    }

    public boolean empty() {
        return this.sticks == 0 && this.axes == 0 && this.buttons == 0;
    }

    public boolean contains(RxSource source) {
        return switch (source.kind()) {
            case STICK -> (this.sticks & source.bit()) != 0;
            case AXIS -> (this.axes & source.bit()) != 0;
            case BUTTON -> (this.buttons & source.bit()) != 0;
        };
    }

    public RxMask with(RxSource source) {
        return switch (source.kind()) {
            case STICK -> new RxMask(this.sticks | source.bit(), this.axes, this.buttons);
            case AXIS -> new RxMask(this.sticks, this.axes | source.bit(), this.buttons);
            case BUTTON -> new RxMask(this.sticks, this.axes, this.buttons | source.bit());
        };
    }

    public RxMask union(RxMask other) {
        return new RxMask(this.sticks | other.sticks, this.axes | other.axes, this.buttons | other.buttons);
    }

    public boolean within(RxMask allowed) {
        return (this.sticks & ~allowed.sticks) == 0 && (this.axes & ~allowed.axes) == 0 && (this.buttons & ~allowed.buttons) == 0;
    }

    public int analogCount() {
        return Integer.bitCount(this.sticks) + Integer.bitCount(this.axes);
    }

    public String describe() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < RxSource.STICKS; i++) {
            if ((this.sticks & (1 << i)) != 0) {
                append(out, "stick:" + i);
            }
        }
        for (int i = 0; i < RxSource.AXES; i++) {
            if ((this.axes & (1 << i)) != 0) {
                append(out, "axis:" + i);
            }
        }
        for (int i = 0; i < RxSource.BUTTONS; i++) {
            if ((this.buttons & (1 << i)) != 0) {
                append(out, "button:" + i);
            }
        }
        return out.isEmpty() ? "none" : out.toString();
    }

    private static void append(StringBuilder out, String part) {
        if (!out.isEmpty()) {
            out.append(',');
        }
        out.append(part);
    }

    private static void write(ByteBuf buf, RxMask mask) {
        buf.writeByte(mask.sticks);
        buf.writeInt(mask.axes);
        buf.writeInt(mask.buttons);
    }

    private static RxMask read(ByteBuf buf) {
        int sticks = buf.readUnsignedByte();
        if ((sticks & ~STICK_BITS) != 0) {
            throw new DecoderException("Knüppel-Maske außerhalb von 4 Bit");
        }
        return new RxMask(sticks, buf.readInt(), buf.readInt());
    }
}
