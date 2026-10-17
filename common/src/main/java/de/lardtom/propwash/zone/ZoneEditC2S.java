package de.lardtom.propwash.zone;

import de.lardtom.propwash.Constants;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ZoneEditC2S(Action action, Zone zone) implements CustomPacketPayload {
    public static final Type<ZoneEditC2S> TYPE = new Type<>(Constants.id("zone_edit"));
    public static final StreamCodec<FriendlyByteBuf, ZoneEditC2S> CODEC = StreamCodec.of(ZoneEditC2S::write, ZoneEditC2S::read);

    public enum Action {
        SAVE,
        DELETE
    }

    private static void write(FriendlyByteBuf buf, ZoneEditC2S payload) {
        buf.writeByte(payload.action.ordinal());
        ZoneWire.write(buf, payload.zone);
    }

    private static ZoneEditC2S read(FriendlyByteBuf buf) {
        int action = buf.readUnsignedByte();
        Action[] actions = Action.values();
        if (action >= actions.length) {
            throw new DecoderException("Unbekannte Zonen-Aktion: " + action);
        }
        return new ZoneEditC2S(actions[action], ZoneWire.read(buf));
    }

    @Override
    public Type<ZoneEditC2S> type() {
        return TYPE;
    }
}
