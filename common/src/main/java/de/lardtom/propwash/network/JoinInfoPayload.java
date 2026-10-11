package de.lardtom.propwash.network;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.join.AddonInfo;
import de.lardtom.propwash.join.JoinInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

public record JoinInfoPayload(JoinInfo info) implements CustomPacketPayload {
    public static final Type<JoinInfoPayload> TYPE = new Type<>(Constants.id("join_info"));
    public static final int FORMAT = 1;
    public static final int MAX_BODY = 1 << 18;
    private static final int READ_LIMIT = 1024;
    private static final StreamCodec<ByteBuf, String> TEXT = ByteBufCodecs.stringUtf8(READ_LIMIT);
    public static final StreamCodec<ByteBuf, JoinInfoPayload> CODEC = StreamCodec.of(JoinInfoPayload::write, JoinInfoPayload::read);

    private static void write(ByteBuf out, JoinInfoPayload payload) {
        JoinInfo info = payload.info();
        ByteBuf body = Unpooled.buffer();
        try {
            TEXT.encode(body, info.propwashVersion());
            VarInt.write(body, info.protocol());
            TEXT.encode(body, info.builtinFingerprint());
            VarInt.write(body, info.addons().size());
            for (AddonInfo addon : info.addons()) {
                TEXT.encode(body, addon.modId());
                TEXT.encode(body, addon.name());
                TEXT.encode(body, addon.version());
                TEXT.encode(body, addon.homepage());
                VarInt.write(body, addon.namespaces().size());
                for (String namespace : addon.namespaces()) {
                    TEXT.encode(body, namespace);
                }
            }
            if (body.readableBytes() > MAX_BODY) {
                throw new IllegalStateException("Propwash-Beitrittsdaten zu groß: " + body.readableBytes() + " Bytes");
            }
            VarInt.write(out, FORMAT);
            VarInt.write(out, body.readableBytes());
            out.writeBytes(body);
        } finally {
            body.release();
        }
    }

    private static JoinInfoPayload read(ByteBuf in) {
        int format = VarInt.read(in);
        int length = VarInt.read(in);
        if (format < 1 || length < 0 || length > MAX_BODY || length > in.readableBytes()) {
            throw new DecoderException("Propwash-Beitrittsdaten ungültig (Format " + format + ", " + length + " Bytes)");
        }
        ByteBuf body = in.readSlice(length);
        String version = TEXT.decode(body);
        int protocol = VarInt.read(body);
        String builtins = TEXT.decode(body);
        int count = VarInt.read(body);
        if (count < 0 || count > JoinInfo.MAX_ADDONS) {
            throw new DecoderException("Propwash-Beitrittsdaten: " + count + " Add-ons");
        }
        List<AddonInfo> addons = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String modId = TEXT.decode(body);
            String name = TEXT.decode(body);
            String addonVersion = TEXT.decode(body);
            String homepage = TEXT.decode(body);
            int namespaceCount = VarInt.read(body);
            if (namespaceCount < 0 || namespaceCount > AddonInfo.MAX_NAMESPACES) {
                throw new DecoderException("Propwash-Beitrittsdaten: " + namespaceCount + " Namensräume");
            }
            List<String> namespaces = new ArrayList<>(namespaceCount);
            for (int n = 0; n < namespaceCount; n++) {
                namespaces.add(TEXT.decode(body));
            }
            addons.add(new AddonInfo(modId, name, addonVersion, homepage, namespaces));
        }
        return new JoinInfoPayload(new JoinInfo(version, protocol, builtins, addons));
    }

    @Override
    public Type<JoinInfoPayload> type() {
        return TYPE;
    }
}
