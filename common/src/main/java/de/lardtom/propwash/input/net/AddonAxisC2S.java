package de.lardtom.propwash.input.net;

import de.lardtom.propwash.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

public record AddonAxisC2S(int droneId, int epoch, List<Entry> entries) implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 8;
    public static final int PER_SECOND = 10;
    public static final Type<AddonAxisC2S> TYPE = new Type<>(Constants.id("addon_axis"));
    public static final StreamCodec<ByteBuf, AddonAxisC2S> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AddonAxisC2S::droneId,
            ByteBufCodecs.VAR_INT, AddonAxisC2S::epoch,
            Entry.CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)), AddonAxisC2S::entries,
            AddonAxisC2S::new);

    public AddonAxisC2S {
        entries = List.copyOf(entries);
        if (entries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Höchstens " + MAX_ENTRIES + " Add-on-Achsen je Paket: " + entries.size());
        }
    }

    @Override
    public Type<AddonAxisC2S> type() {
        return TYPE;
    }

    public record Entry(Identifier id, short value) {
        public static final StreamCodec<ByteBuf, Entry> CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Entry::id,
                ByteBufCodecs.SHORT, Entry::value,
                Entry::new);
    }
}
