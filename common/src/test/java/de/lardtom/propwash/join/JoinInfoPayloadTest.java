package de.lardtom.propwash.join;

import de.lardtom.propwash.network.JoinInfoPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.ByteBufCodecs;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JoinInfoPayloadTest {
    private static final JoinInfo INFO = new JoinInfo("0.5.0", 13, "abcdef0123456789", List.of(
            new AddonInfo("justmoreparts", "Propwash: Just More Parts", "1.1.0", "https://modrinth.com/mod/propwash-just-more-parts", List.of()),
            new AddonInfo("example_mod", "Example Add-on", "2.0.0-beta.1", "", List.of("example_parts", "example_more"))));

    @Test
    void roundTrip() {
        ByteBuf buffer = Unpooled.buffer();
        JoinInfoPayload.CODEC.encode(buffer, new JoinInfoPayload(INFO));
        JoinInfoPayload decoded = JoinInfoPayload.CODEC.decode(buffer);
        assertEquals(INFO, decoded.info());
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void newerFormatWithExtraFieldsStillReads() {
        ByteBuf body = Unpooled.buffer();
        ByteBufCodecs.stringUtf8(1024).encode(body, "0.6.0");
        VarInt.write(body, 20);
        ByteBufCodecs.stringUtf8(1024).encode(body, "fp");
        VarInt.write(body, 1);
        for (String text : List.of("future_mod", "Future", "3.0.0", "https://example.org/future")) {
            ByteBufCodecs.stringUtf8(1024).encode(body, text);
        }
        VarInt.write(body, 0);
        ByteBufCodecs.stringUtf8(1024).encode(body, "a field this version does not know");
        ByteBuf buffer = Unpooled.buffer();
        VarInt.write(buffer, 7);
        VarInt.write(buffer, body.readableBytes());
        buffer.writeBytes(body);
        buffer.writeByte(42);
        JoinInfo decoded = JoinInfoPayload.CODEC.decode(buffer).info();
        assertEquals(20, decoded.protocol());
        assertEquals("0.6.0", decoded.propwashVersion());
        assertEquals("Future", decoded.addons().getFirst().name());
        assertEquals(1, buffer.readableBytes());
    }

    @Test
    void hostileSizesAreRejected() {
        ByteBuf tooLong = Unpooled.buffer();
        VarInt.write(tooLong, 1);
        VarInt.write(tooLong, JoinInfoPayload.MAX_BODY + 1);
        assertThrows(DecoderException.class, () -> JoinInfoPayload.CODEC.decode(tooLong));
        ByteBuf body = Unpooled.buffer();
        ByteBufCodecs.stringUtf8(1024).encode(body, "0.5.0");
        VarInt.write(body, 13);
        ByteBufCodecs.stringUtf8(1024).encode(body, "fp");
        VarInt.write(body, 100000);
        ByteBuf manyAddons = Unpooled.buffer();
        VarInt.write(manyAddons, 1);
        VarInt.write(manyAddons, body.readableBytes());
        manyAddons.writeBytes(body);
        assertThrows(DecoderException.class, () -> JoinInfoPayload.CODEC.decode(manyAddons));
    }

    @Test
    void decodedTextIsSanitized() {
        ByteBuf body = Unpooled.buffer();
        ByteBufCodecs.stringUtf8(1024).encode(body, "0.5.0");
        VarInt.write(body, 13);
        ByteBufCodecs.stringUtf8(1024).encode(body, "fp");
        VarInt.write(body, 1);
        for (String text : List.of("evil_mod", "§kEvil\nName", "1.0", "javascript:alert(1)")) {
            ByteBufCodecs.stringUtf8(1024).encode(body, text);
        }
        VarInt.write(body, 0);
        ByteBuf buffer = Unpooled.buffer();
        VarInt.write(buffer, 1);
        VarInt.write(buffer, body.readableBytes());
        buffer.writeBytes(body);
        AddonInfo addon = JoinInfoPayload.CODEC.decode(buffer).info().addons().getFirst();
        assertEquals("Evil Name", addon.name());
        assertEquals("", addon.homepage());
    }
}
