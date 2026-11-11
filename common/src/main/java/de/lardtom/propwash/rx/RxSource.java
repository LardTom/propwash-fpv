package de.lardtom.propwash.rx;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record RxSource(Kind kind, int index) {
    public static final int STICKS = 4;
    public static final int AXES = 32;
    public static final int BUTTONS = 32;
    public static final int THROTTLE = 3;
    public static final RxSource GAS = new RxSource(Kind.STICK, THROTTLE);

    public static final Codec<RxSource> CODEC = RecordCodecBuilder.<Raw>create(i -> i.group(
            Kind.CODEC.fieldOf("kind").forGetter(Raw::kind),
            Codec.INT.fieldOf("index").forGetter(Raw::index)
    ).apply(i, Raw::new)).comapFlatMap(RxSource::checked, source -> new Raw(source.kind, source.index));

    public static final StreamCodec<ByteBuf, RxSource> STREAM_CODEC = StreamCodec.of(RxSource::write, RxSource::read);

    public enum Kind implements StringRepresentable {
        STICK(STICKS, 2.0F),
        AXIS(AXES, 2.0F),
        BUTTON(BUTTONS, 1.0F);

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);
        private static final Kind[] VALUES = values();

        private final int count;
        private final float travel;

        Kind(int count, float travel) {
            this.count = count;
            this.travel = travel;
        }

        public int count() {
            return this.count;
        }

        public float travel() {
            return this.travel;
        }

        public float defaultLow() {
            return this == BUTTON ? 0.0F : -1.0F;
        }

        public float defaultHigh() {
            return 1.0F;
        }

        public float min() {
            return this == BUTTON ? 0.0F : -1.0F;
        }

        public static Kind byOrdinal(int ordinal) {
            if (ordinal < 0 || ordinal >= VALUES.length) {
                throw new IllegalArgumentException("Unbekannte Art einer RX-Quelle: " + ordinal);
            }
            return VALUES[ordinal];
        }

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public RxSource {
        Objects.requireNonNull(kind, "kind");
        if (index < 0 || index >= kind.count()) {
            throw new IllegalArgumentException("RX-Quelle " + kind.getSerializedName() + " " + index + " außerhalb 0–" + (kind.count() - 1));
        }
    }

    public static boolean valid(Kind kind, int index) {
        return kind != null && index >= 0 && index < kind.count();
    }

    public static RxSource stick(int index) {
        return new RxSource(Kind.STICK, index);
    }

    public static RxSource axis(int index) {
        return new RxSource(Kind.AXIS, index);
    }

    public static RxSource button(int index) {
        return new RxSource(Kind.BUTTON, index);
    }

    public static List<RxSource> choices(int axes, int buttons) {
        List<RxSource> out = new ArrayList<>();
        for (int i = 0; i < STICKS; i++) {
            out.add(stick(i));
        }
        for (int i = 0; i < Math.clamp(axes, 0, AXES); i++) {
            out.add(axis(i));
        }
        for (int i = 0; i < Math.clamp(buttons, 0, BUTTONS); i++) {
            out.add(button(i));
        }
        return out;
    }

    public int bit() {
        return 1 << this.index;
    }

    public float defaultLow() {
        return this.kind.defaultLow();
    }

    public float defaultHigh() {
        return this.kind.defaultHigh();
    }

    public String describe() {
        return this.kind.getSerializedName() + ":" + this.index;
    }

    private static DataResult<RxSource> checked(Raw raw) {
        if (!valid(raw.kind, raw.index)) {
            return DataResult.error(() -> "RX-Quelle außerhalb des Bereichs: " + raw.kind + " " + raw.index);
        }
        return DataResult.success(new RxSource(raw.kind, raw.index));
    }

    private record Raw(Kind kind, int index) {
    }

    private static void write(ByteBuf buf, RxSource source) {
        buf.writeByte(source.kind.ordinal());
        buf.writeByte(source.index);
    }

    private static RxSource read(ByteBuf buf) {
        int kind = buf.readUnsignedByte();
        int index = buf.readUnsignedByte();
        try {
            return new RxSource(Kind.byOrdinal(kind), index);
        } catch (IllegalArgumentException e) {
            throw new DecoderException(e.getMessage());
        }
    }
}
