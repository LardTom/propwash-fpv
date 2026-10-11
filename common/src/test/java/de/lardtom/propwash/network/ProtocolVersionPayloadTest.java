package de.lardtom.propwash.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolVersionPayloadTest {
    @Test
    void roundTripsThroughTheCodec() {
        ProtocolVersionPayload payload = new ProtocolVersionPayload(7, "0.3.1+26.3");
        ByteBuf buffer = Unpooled.buffer();
        ProtocolVersionPayload.CODEC.encode(buffer, payload);
        assertEquals(payload, ProtocolVersionPayload.CODEC.decode(buffer));
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void onlyTheProtocolNumberDecidesCompatibility() {
        ProtocolVersionPayload server = new ProtocolVersionPayload(2, "0.2.0");
        assertTrue(server.compatibleWith(new ProtocolVersionPayload(2, "0.2.5")));
        assertFalse(server.compatibleWith(new ProtocolVersionPayload(1, "0.2.0")));
        assertFalse(server.compatibleWith(new ProtocolVersionPayload(3, "0.2.0")));
    }

    @Test
    void mismatchMessageNamesBothSides() {
        Component message = ProtocolVersionPayload.mismatchMessage(new ProtocolVersionPayload(2, "0.2.0"),
                new ProtocolVersionPayload(1, "0.1.0"));
        TranslatableContents contents = (TranslatableContents) message.getContents();
        assertEquals(ProtocolVersionPayload.MISMATCH_KEY, contents.getKey());
        assertArrayEquals(new Object[]{"0.2.0", "2", "0.1.0", "1"}, contents.getArgs());
        assertEquals(4, placeholders(contents.getFallback()));
    }

    @Test
    void missingMessageNamesTheServerVersion() {
        Component message = ProtocolVersionPayload.missingMessage(new ProtocolVersionPayload(2, "0.2.0"));
        TranslatableContents contents = (TranslatableContents) message.getContents();
        assertEquals(ProtocolVersionPayload.MISSING_KEY, contents.getKey());
        assertArrayEquals(new Object[]{"0.2.0", "2"}, contents.getArgs());
        assertEquals(2, placeholders(contents.getFallback()));
    }

    @Test
    void bothLanguagesTranslateTheMessagesWithTheSamePlaceholders() throws Exception {
        for (String language : new String[]{"en_us", "de_de"}) {
            JsonObject lang = load(language);
            assertEquals(4, placeholders(lang.get(ProtocolVersionPayload.MISMATCH_KEY).getAsString()), language);
            assertEquals(2, placeholders(lang.get(ProtocolVersionPayload.MISSING_KEY).getAsString()), language);
        }
    }

    private static JsonObject load(String language) throws Exception {
        Path path = Path.of("common/src/lang/core", language + ".json");
        if (!Files.exists(path)) {
            path = Path.of("src/lang/core", language + ".json");
        }
        try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static int placeholders(String text) {
        int count = 0;
        int index = text.indexOf("%s");
        while (index >= 0) {
            count++;
            index = text.indexOf("%s", index + 2);
        }
        return count;
    }
}
