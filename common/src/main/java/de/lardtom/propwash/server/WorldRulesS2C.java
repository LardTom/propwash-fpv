package de.lardtom.propwash.server;

import de.lardtom.propwash.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

public record WorldRulesS2C(boolean editable, List<Entry> entries) implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 256;
    public static final Type<WorldRulesS2C> TYPE = new Type<>(Constants.id("world_rules"));
    public static final StreamCodec<ByteBuf, WorldRulesS2C> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, WorldRulesS2C::editable,
            Entry.CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)), WorldRulesS2C::entries,
            WorldRulesS2C::new);

    public WorldRulesS2C {
        entries = List.copyOf(entries);
    }

    @Override
    public Type<WorldRulesS2C> type() {
        return TYPE;
    }

    public record Entry(Identifier id, String value) {
        public static final int MAX_VALUE_LENGTH = 64;
        public static final StreamCodec<ByteBuf, Entry> CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Entry::id,
                ByteBufCodecs.stringUtf8(MAX_VALUE_LENGTH), Entry::value,
                Entry::new);
    }
}
