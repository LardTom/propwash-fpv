package de.lardtom.propwash.network;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.parts.PartsHandshake;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

public record PartsManifestPayload(String fingerprint, List<Entry> entries) implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 8192;
    public static final Type<PartsManifestPayload> TYPE = new Type<>(Constants.id("parts"));
    public static final StreamCodec<ByteBuf, Entry> ENTRY_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(16), Entry::kind,
            Identifier.STREAM_CODEC, Entry::id,
            ByteBufCodecs.stringUtf8(64), Entry::hash,
            Entry::new);
    public static final StreamCodec<ByteBuf, PartsManifestPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(128), PartsManifestPayload::fingerprint,
            ENTRY_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)), PartsManifestPayload::entries,
            PartsManifestPayload::new);

    public PartsManifestPayload {
        entries = List.copyOf(entries);
    }

    public static PartsManifestPayload local() {
        return PartsHandshake.localManifest();
    }

    @Override
    public Type<PartsManifestPayload> type() {
        return TYPE;
    }

    public record Entry(String kind, Identifier id, String hash) {
    }
}
