package de.lardtom.propwash.network.fabricsync;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record FabricOpenScreenPayload(Identifier menuType, int containerId, Component title, byte[] data) implements CustomPacketPayload {
    public static final Type<FabricOpenScreenPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("fabric-menu-api-v1", "open_screen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FabricOpenScreenPayload> CODEC = StreamCodec.of(
            (buf, payload) -> payload.write(buf), FabricOpenScreenPayload::read);

    private static FabricOpenScreenPayload read(RegistryFriendlyByteBuf buf) {
        Identifier menuType = buf.readIdentifier();
        int containerId = buf.readByte();
        Component title = ComponentSerialization.STREAM_CODEC.decode(buf);
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        return new FabricOpenScreenPayload(menuType, containerId, title, data);
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeIdentifier(this.menuType);
        buf.writeByte(this.containerId);
        ComponentSerialization.STREAM_CODEC.encode(buf, this.title);
        buf.writeBytes(this.data);
    }

    @Override
    public Type<FabricOpenScreenPayload> type() {
        return TYPE;
    }
}
