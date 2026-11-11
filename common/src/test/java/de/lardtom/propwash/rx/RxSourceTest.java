package de.lardtom.propwash.rx;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RxSourceTest {
    @Test
    void codecRoundTripsEveryKind() {
        for (RxSource source : RxSource.choices(RxSource.AXES, RxSource.BUTTONS)) {
            JsonElement json = RxSource.CODEC.encodeStart(JsonOps.INSTANCE, source).getOrThrow();
            assertEquals(source, RxSource.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
            Tag tag = RxSource.CODEC.encodeStart(NbtOps.INSTANCE, source).getOrThrow();
            assertEquals(source, RxSource.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow());
            ByteBuf buf = Unpooled.buffer();
            RxSource.STREAM_CODEC.encode(buf, source);
            assertEquals(source, RxSource.STREAM_CODEC.decode(buf));
        }
    }

    @Test
    void savedFormIsKindAndIndex() {
        JsonObject json = RxSource.CODEC.encodeStart(JsonOps.INSTANCE, RxSource.GAS).getOrThrow().getAsJsonObject();
        assertEquals("stick", json.get("kind").getAsString());
        assertEquals(3, json.get("index").getAsInt());
    }

    @Test
    void indicesOutsideTheKindAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> RxSource.stick(4));
        assertThrows(IllegalArgumentException.class, () -> RxSource.axis(32));
        assertThrows(IllegalArgumentException.class, () -> RxSource.button(-1));
        JsonObject json = new JsonObject();
        json.addProperty("kind", "stick");
        json.addProperty("index", 7);
        assertTrue(RxSource.CODEC.parse(JsonOps.INSTANCE, json).isError());
        json.addProperty("kind", "pedal");
        json.addProperty("index", 0);
        assertTrue(RxSource.CODEC.parse(JsonOps.INSTANCE, json).isError());
        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(0);
        buf.writeByte(9);
        assertThrows(DecoderException.class, () -> RxSource.STREAM_CODEC.decode(buf));
        ByteBuf kind = Unpooled.buffer();
        kind.writeByte(5);
        kind.writeByte(0);
        assertThrows(DecoderException.class, () -> RxSource.STREAM_CODEC.decode(kind));
    }

    @Test
    void choicesStartWithTheSticks() {
        List<RxSource> choices = RxSource.choices(2, 1);
        assertEquals(List.of(RxSource.stick(0), RxSource.stick(1), RxSource.stick(2), RxSource.stick(3), RxSource.axis(0), RxSource.axis(1),
                RxSource.button(0)), choices);
        assertEquals(4 + 32 + 32, RxSource.choices(99, 99).size());
    }

    @Test
    void defaultEndpointsPerKind() {
        assertEquals(-1.0F, RxSource.GAS.defaultLow());
        assertEquals(1.0F, RxSource.GAS.defaultHigh());
        assertEquals(-1.0F, RxSource.axis(6).defaultLow());
        assertEquals(0.0F, RxSource.button(1).defaultLow());
        assertEquals(1.0F, RxSource.button(1).defaultHigh());
    }
}
