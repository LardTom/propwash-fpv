package de.lardtom.propwash.network;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.platform.Services;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ProtocolVersionPayload(int protocol, String modVersion) implements CustomPacketPayload {
    private static final int MAX_VERSION_LENGTH = 64;
    public static final Type<ProtocolVersionPayload> TYPE = new Type<>(Constants.id("protocol_version"));
    public static final StreamCodec<ByteBuf, ProtocolVersionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ProtocolVersionPayload::protocol,
            ByteBufCodecs.stringUtf8(MAX_VERSION_LENGTH), ProtocolVersionPayload::modVersion,
            ProtocolVersionPayload::new);
    public static final String MISMATCH_KEY = "propwash.network.version_mismatch";
    public static final String MISSING_KEY = "propwash.network.version_missing";
    private static volatile int localProtocol = Constants.PROTOCOL_VERSION;

    public static int localProtocol() {
        return localProtocol;
    }

    public static void overrideLocalProtocol(int protocol) {
        localProtocol = protocol;
    }

    public static ProtocolVersionPayload local() {
        String version = Services.PLATFORM.getModVersion();
        if (version.length() > MAX_VERSION_LENGTH) {
            version = version.substring(0, MAX_VERSION_LENGTH);
        }
        return new ProtocolVersionPayload(localProtocol, version);
    }

    public boolean compatibleWith(ProtocolVersionPayload other) {
        return this.protocol == other.protocol;
    }

    public static Component mismatchMessage(ProtocolVersionPayload server, ProtocolVersionPayload client) {
        return Component.translatableWithFallback(MISMATCH_KEY,
                "Propwash FPV version mismatch.\nServer: %s (network protocol %s)\nYou: %s (network protocol %s)\nInstall the same Propwash FPV version as the server.",
                server.modVersion(), String.valueOf(server.protocol()), client.modVersion(), String.valueOf(client.protocol()));
    }

    public static Component missingMessage(ProtocolVersionPayload server) {
        return Component.translatableWithFallback(MISSING_KEY,
                "This server requires Propwash FPV %s (network protocol %s). Install exactly this version.",
                server.modVersion(), String.valueOf(server.protocol()));
    }

    @Override
    public Type<ProtocolVersionPayload> type() {
        return TYPE;
    }
}
