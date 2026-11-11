package de.lardtom.propwash.rx;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RxPayloadTest {
    private static <T> T roundTrip(net.minecraft.network.codec.StreamCodec<ByteBuf, T> codec, T value) {
        ByteBuf buf = Unpooled.buffer();
        codec.encode(buf, value);
        T read = codec.decode(buf);
        assertEquals(0, buf.readableBytes(), "Reste im Puffer");
        return read;
    }

    @Test
    void quantizationKeepsValuesWithinOneStep() {
        for (int i = -1000; i <= 1000; i++) {
            float value = i / 1000.0F;
            float back = RxInputC2S.value(RxInputC2S.quantize(value));
            assertEquals(value, back, 1.0F / RxInputC2S.FULL_SCALE);
        }
        assertEquals(RxInputC2S.FULL_SCALE, RxInputC2S.quantize(1.0F));
        assertEquals(-RxInputC2S.FULL_SCALE, RxInputC2S.quantize(-1.0F));
        assertEquals(RxInputC2S.FULL_SCALE, RxInputC2S.quantize(5.0F));
        assertEquals(0, RxInputC2S.quantize(Float.NaN));
        assertEquals(-1.0F, RxInputC2S.value(Short.MIN_VALUE));
    }

    @Test
    void inputRoundTripsWithMasks() {
        RxMask mask = RxMask.of(RxSource.GAS).with(RxSource.stick(0)).with(RxSource.axis(6)).with(RxSource.axis(31)).with(RxSource.button(4));
        float[] sticks = {-0.25F, 0.0F, 0.0F, 0.75F};
        float[] axes = new float[32];
        axes[6] = 1.0F;
        axes[31] = -0.5F;
        axes[2] = 0.9F;
        RxInputC2S payload = RxInputC2S.of(42, mask, sticks, axes, (1 << 4) | (1 << 7));
        assertEquals(4, payload.values().length);
        assertEquals(1 << 4, payload.pressed());
        RxInputC2S read = roundTrip(RxInputC2S.CODEC, payload);
        assertEquals(payload, read);
        assertEquals(0.75F, read.valueOf(RxSource.GAS), 1.0E-4F);
        assertEquals(-0.25F, read.valueOf(RxSource.stick(0)), 1.0E-4F);
        assertEquals(1.0F, read.valueOf(RxSource.axis(6)), 1.0E-4F);
        assertEquals(-0.5F, read.valueOf(RxSource.axis(31)), 1.0E-4F);
        assertEquals(1.0F, read.valueOf(RxSource.button(4)));
        assertTrue(Float.isNaN(read.valueOf(RxSource.axis(2))));
        assertTrue(Float.isNaN(read.valueOf(RxSource.stick(1))));
        float[] outSticks = new float[4];
        float[] outAxes = new float[32];
        read.unpack(outSticks, outAxes);
        assertEquals(0.75F, outSticks[3], 1.0E-4F);
        assertEquals(0.0F, outAxes[2]);
    }

    @Test
    void maskRules() {
        RxMask demand = RxMask.of(RxSource.GAS).with(RxSource.axis(5));
        assertTrue(RxMask.of(RxSource.GAS).within(demand));
        assertTrue(RxMask.NONE.within(demand));
        assertFalse(RxMask.of(RxSource.stick(0)).within(demand));
        assertFalse(RxMask.of(RxSource.button(5)).within(demand));
        assertTrue(demand.contains(RxSource.axis(5)));
        assertFalse(demand.contains(RxSource.button(5)));
        assertEquals("stick:3,axis:5", demand.describe());
        assertEquals("none", RxMask.NONE.describe());
        assertEquals(demand, roundTrip(RxMask.STREAM_CODEC, demand));
        assertEquals(new RxDemandS2C(demand), roundTrip(RxDemandS2C.CODEC, new RxDemandS2C(demand)));
        assertThrows(IllegalArgumentException.class, () -> new RxMask(1 << 4, 0, 0));
        ByteBuf bad = Unpooled.buffer();
        bad.writeByte(0x30);
        bad.writeInt(0);
        bad.writeInt(0);
        assertThrows(DecoderException.class, () -> RxMask.STREAM_CODEC.decode(bad));
    }

    @Test
    void truncatedInputIsRejected() {
        ByteBuf buf = Unpooled.buffer();
        RxInputC2S.CODEC.encode(buf, RxInputC2S.of(1, RxMask.of(RxSource.GAS).with(RxSource.axis(1)), new float[4], new float[32], 0));
        ByteBuf cut = buf.copy(0, buf.readableBytes() - 3);
        assertThrows(DecoderException.class, () -> RxInputC2S.CODEC.decode(cut));
    }

    @Test
    void configRoundTripsEveryAction() {
        BlockPos pos = new BlockPos(-123, 70, 4567);
        for (RxConfigC2S config : new RxConfigC2S[]{RxConfigC2S.bind(pos), RxConfigC2S.unbind(pos), RxConfigC2S.source(pos, RxSource.axis(6)),
                RxConfigC2S.low(pos, -0.4F), RxConfigC2S.high(pos, Float.NaN), RxConfigC2S.range(pos, 128)}) {
            RxConfigC2S read = roundTrip(RxConfigC2S.CODEC, config);
            assertEquals(config.pos(), read.pos());
            assertEquals(config.action(), read.action());
            assertEquals(config.source(), read.source());
            assertEquals(Float.floatToIntBits(config.value()), Float.floatToIntBits(read.value()));
        }
        assertNull(RxConfigC2S.low(pos, 0.5F).source());
        assertThrows(IllegalArgumentException.class, () -> new RxConfigC2S(pos, RxConfigC2S.Action.SOURCE, null, 0.0F));
        ByteBuf bad = Unpooled.buffer();
        BlockPos.STREAM_CODEC.encode(bad, pos);
        bad.writeByte(9);
        assertThrows(DecoderException.class, () -> RxConfigC2S.CODEC.decode(bad));
    }
}
