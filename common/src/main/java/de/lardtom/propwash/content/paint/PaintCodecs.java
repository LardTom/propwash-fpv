package de.lardtom.propwash.content.paint;

import com.mojang.serialization.Codec;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.PaintSlot;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

public final class PaintCodecs {
    private static final Codec<String> COLOR_TEXT = Codec.STRING.withAlternative(Codec.INT, value -> DronePaint.hex(value));

    public static final Codec<DronePaint> CODEC = Codec.unboundedMap(Codec.STRING, COLOR_TEXT).xmap(PaintCodecs::fromMap, PaintCodecs::toMap);

    public static final StreamCodec<ByteBuf, DronePaint> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public DronePaint decode(ByteBuf buffer) {
            int count = buffer.readUnsignedByte();
            if (count > PaintSlot.COUNT) {
                throw new IllegalArgumentException("Zu viele Lack-Einträge: " + count);
            }
            Map<PaintSlot, Integer> colors = new EnumMap<>(PaintSlot.class);
            for (int i = 0; i < count; i++) {
                int slot = buffer.readUnsignedByte();
                int rgb = buffer.readUnsignedMedium();
                if (slot < PaintSlot.COUNT) {
                    colors.put(PaintSlot.values()[slot], rgb);
                }
            }
            return DronePaint.of(colors);
        }

        @Override
        public void encode(ByteBuf buffer, DronePaint paint) {
            buffer.writeByte(paint.colors().size());
            for (Map.Entry<PaintSlot, Integer> entry : paint.colors().entrySet()) {
                buffer.writeByte(entry.getKey().ordinal());
                buffer.writeMedium(entry.getValue() & 0xFFFFFF);
            }
        }
    };

    private PaintCodecs() {
    }

    private static DronePaint fromMap(Map<String, String> raw) {
        Map<PaintSlot, Integer> colors = new EnumMap<>(PaintSlot.class);
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            Optional<PaintSlot> slot = PaintSlot.byKey(entry.getKey());
            OptionalInt rgb = DronePaint.parseHex(entry.getValue());
            if (slot.isPresent() && rgb.isPresent()) {
                colors.put(slot.get(), rgb.getAsInt());
            }
        }
        return DronePaint.of(colors);
    }

    private static Map<String, String> toMap(DronePaint paint) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<PaintSlot, Integer> entry : paint.colors().entrySet()) {
            out.put(entry.getKey().key(), DronePaint.hex(entry.getValue()));
        }
        return out;
    }
}
