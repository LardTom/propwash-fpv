package de.lardtom.propwash.rescue;

import de.lardtom.propwash.rescue.net.HomePointC2S;
import de.lardtom.propwash.rescue.net.HomePointS2C;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomePointCodecsTest {
    private static final HomePoint ARMED = new HomePoint(12.5, 64.0, -3.25, "minecraft:overworld", 123456L, HomePoint.Source.ARM);

    @Test
    void nbtUsesReadableKeys() {
        CompoundTag tag = (CompoundTag) HomePointCodecs.CODEC.encodeStart(NbtOps.INSTANCE, ARMED).getOrThrow();
        assertEquals(12.5, tag.getDoubleOr("x", 0.0));
        assertEquals(64.0, tag.getDoubleOr("y", 0.0));
        assertEquals(-3.25, tag.getDoubleOr("z", 0.0));
        assertEquals("minecraft:overworld", tag.getStringOr("dimension", ""));
        assertEquals(123456L, tag.getLongOr("game_time", 0L));
        assertEquals("arm", tag.getStringOr("source", ""));
        assertEquals(6, tag.size());
        assertEquals(ARMED, HomePointCodecs.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow());
    }

    @Test
    void handWrittenTagsLoad() {
        CompoundTag typed = new CompoundTag();
        typed.putInt("x", 100);
        typed.putInt("y", 64);
        typed.putDouble("z", -20.5);
        typed.putString("dimension", "the_nether");
        HomePoint home = HomePointCodecs.CODEC.parse(NbtOps.INSTANCE, typed).getOrThrow();
        assertEquals(new HomePoint(100.0, 64.0, -20.5, "minecraft:the_nether", HomePoint.UNKNOWN_TIME, HomePoint.Source.COMMAND), home);
        CompoundTag bare = new CompoundTag();
        bare.putDouble("x", 1.0);
        bare.putDouble("y", 2.0);
        bare.putDouble("z", 3.0);
        assertEquals("", HomePointCodecs.CODEC.parse(NbtOps.INSTANCE, bare).getOrThrow().dimension());
    }

    @Test
    void brokenTagsAreErrors() {
        CompoundTag missing = new CompoundTag();
        missing.putDouble("x", 1.0);
        missing.putDouble("z", 3.0);
        assertTrue(HomePointCodecs.CODEC.parse(NbtOps.INSTANCE, missing).isError());
        CompoundTag text = full();
        text.putString("y", "64");
        assertTrue(HomePointCodecs.CODEC.parse(NbtOps.INSTANCE, text).isError());
        CompoundTag source = full();
        source.putString("source", "teleport");
        assertTrue(HomePointCodecs.CODEC.parse(NbtOps.INSTANCE, source).isError());
        CompoundTag dimension = full();
        dimension.putString("dimension", "Not A Dimension");
        assertTrue(HomePointCodecs.CODEC.parse(NbtOps.INSTANCE, dimension).isError());
        Tag list = new ListTag();
        assertTrue(HomePointCodecs.CODEC.parse(NbtOps.INSTANCE, list).isError());
    }

    @Test
    void packetsRoundTrip() {
        HomePointC2S set = new HomePointC2S(42, 7, 1.5, -60.0, 1.0e6, HomePoint.Source.PILOT);
        ByteBuf buffer = Unpooled.buffer();
        HomePointC2S.CODEC.encode(buffer, set);
        assertEquals(set, HomePointC2S.CODEC.decode(buffer));
        assertEquals(0, buffer.readableBytes());

        for (HomePointS2C sync : new HomePointS2C[]{new HomePointS2C(42, 7, true, ARMED), new HomePointS2C(3, 0, false, null)}) {
            ByteBuf out = Unpooled.buffer();
            HomePointS2C.CODEC.encode(out, sync);
            assertEquals(sync, HomePointS2C.CODEC.decode(out));
            assertEquals(0, out.readableBytes());
        }
    }

    private static CompoundTag full() {
        return (CompoundTag) HomePointCodecs.CODEC.encodeStart(NbtOps.INSTANCE, ARMED).getOrThrow();
    }
}
