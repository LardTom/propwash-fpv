package de.lardtom.propwash.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import de.lardtom.propwash.build.PartWear;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WearCodecsTest {
    private static final PartWear SAMPLE = PartWear.PRISTINE.withArm(2, 0.61F).withStack(0.97F).withMotor(0, 0.5F).withProp(1, 0.0F)
            .withProp(3, 0.333F).withVideo(0.81F).withBattery(0.25F);

    @Test
    void jsonRoundTrip() {
        JsonElement json = WearCodecs.CODEC.encodeStart(JsonOps.INSTANCE, SAMPLE).getOrThrow();
        assertEquals(SAMPLE, WearCodecs.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void missingFieldsLoadAsNew() {
        assertSame(PartWear.PRISTINE, WearCodecs.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow());
        JsonObject partial = new JsonObject();
        partial.addProperty("video", 0.5F);
        PartWear wear = WearCodecs.CODEC.parse(JsonOps.INSTANCE, partial).getOrThrow();
        assertEquals(0.5F, wear.video());
        assertEquals(1.0F, wear.worstProp());
    }

    @Test
    void wrongListLengthIsAnError() {
        JsonObject bad = WearCodecs.CODEC.encodeStart(JsonOps.INSTANCE, SAMPLE).getOrThrow().getAsJsonObject();
        bad.getAsJsonArray("props").remove(0);
        assertTrue(WearCodecs.CODEC.parse(JsonOps.INSTANCE, bad).isError());
    }

    @Test
    void networkRoundTrip() {
        for (PartWear wear : new PartWear[]{PartWear.PRISTINE, SAMPLE}) {
            ByteBuf buffer = Unpooled.buffer();
            WearCodecs.STREAM_CODEC.encode(buffer, wear);
            assertEquals(wear, WearCodecs.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        }
        ByteBuf pristine = Unpooled.buffer();
        WearCodecs.STREAM_CODEC.encode(pristine, PartWear.PRISTINE);
        assertEquals(1, pristine.readableBytes());
    }
}
