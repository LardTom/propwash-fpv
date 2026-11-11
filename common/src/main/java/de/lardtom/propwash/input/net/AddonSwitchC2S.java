package de.lardtom.propwash.input.net;

import de.lardtom.propwash.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

public record AddonSwitchC2S(int droneId, int epoch, List<Entry> entries) implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 16;
    public static final Type<AddonSwitchC2S> TYPE = new Type<>(Constants.id("addon_switch"));
    public static final StreamCodec<ByteBuf, AddonSwitchC2S> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AddonSwitchC2S::droneId,
            ByteBufCodecs.VAR_INT, AddonSwitchC2S::epoch,
            Entry.CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)), AddonSwitchC2S::entries,
            AddonSwitchC2S::new);

    public AddonSwitchC2S {
        entries = List.copyOf(entries);
        if (entries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Höchstens " + MAX_ENTRIES + " Add-on-Schalter je Paket: " + entries.size());
        }
    }

    @Override
    public Type<AddonSwitchC2S> type() {
        return TYPE;
    }

    public record Entry(Identifier id, boolean on) {
        public static final StreamCodec<ByteBuf, Entry> CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Entry::id,
                ByteBufCodecs.BOOL, Entry::on,
                Entry::new);
    }
}
