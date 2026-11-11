package de.lardtom.propwash.network.fabricsync;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record FabricSyncCompletePayload() implements CustomPacketPayload {
    public static final FabricSyncCompletePayload INSTANCE = new FabricSyncCompletePayload();
    public static final Type<FabricSyncCompletePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("fabric", "registry/sync/complete"));
    public static final StreamCodec<FriendlyByteBuf, FabricSyncCompletePayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<FabricSyncCompletePayload> type() {
        return TYPE;
    }
}
