package de.lardtom.propwash.content.paint;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.PaintPreset;
import de.lardtom.propwash.build.PaintSlot;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaintCodecsTest {
    private static final DronePaint SAMPLE = DronePaint.DEFAULT.with(PaintSlot.FRAME, 0x2A2C30).with(PaintSlot.PROP_FRONT_LEFT, 0xFED83D)
            .with(PaintSlot.PROP_REAR_RIGHT, 0x123456).with(PaintSlot.ACCESSORIES, 0);

    @Test
    void jsonRoundTrip() {
        for (DronePaint paint : new DronePaint[]{DronePaint.DEFAULT, SAMPLE, PaintPreset.NEON.paint()}) {
            JsonElement json = PaintCodecs.CODEC.encodeStart(JsonOps.INSTANCE, paint).getOrThrow();
            assertEquals(paint, PaintCodecs.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        }
    }

    @Test
    void jsonUsesSlotKeysAndHexColours() {
        JsonObject json = PaintCodecs.CODEC.encodeStart(JsonOps.INSTANCE, SAMPLE).getOrThrow().getAsJsonObject();
        assertEquals("#2a2c30", json.get("frame").getAsString());
        assertEquals("#fed83d", json.get("prop_fl").getAsString());
        assertEquals("#000000", json.get("accessories").getAsString());
        assertEquals(4, json.size());
    }

    @Test
    void unknownSlotsAndBrokenColoursAreSkipped() {
        JsonObject json = new JsonObject();
        json.addProperty("frame", "#ff0000");
        json.addProperty("wings", "#00ff00");
        json.addProperty("motors", "grün");
        json.addProperty("camera", 0x0000FF);
        DronePaint paint = PaintCodecs.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(OptionalInt.of(0xFF0000), paint.color(PaintSlot.FRAME));
        assertEquals(OptionalInt.of(0x0000FF), paint.color(PaintSlot.CAMERA));
        assertEquals(2, paint.count());
    }

    @Test
    void emptyObjectIsTheDefault() {
        assertTrue(PaintCodecs.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow().isDefault());
    }

    @Test
    void networkRoundTrip() {
        for (DronePaint paint : new DronePaint[]{DronePaint.DEFAULT, SAMPLE, PaintPreset.ARCTIC.paint()}) {
            ByteBuf buffer = Unpooled.buffer();
            PaintCodecs.STREAM_CODEC.encode(buffer, paint);
            assertEquals(1 + 4 * paint.count(), buffer.readableBytes());
            assertEquals(paint, PaintCodecs.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        }
    }

    @Test
    void networkRejectsOversizedCounts() {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeByte(PaintSlot.COUNT + 1);
        assertThrows(IllegalArgumentException.class, () -> PaintCodecs.STREAM_CODEC.decode(buffer));
    }

    @Test
    void payloadRoundTrip() {
        ByteBuf buffer = Unpooled.buffer();
        PaintC2S payload = new PaintC2S(7, SAMPLE);
        PaintC2S.CODEC.encode(buffer, payload);
        assertEquals(payload, PaintC2S.CODEC.decode(buffer));
    }
}
