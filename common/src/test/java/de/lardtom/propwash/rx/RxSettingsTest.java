package de.lardtom.propwash.rx;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RxSettingsTest {
    private static final UUID PILOT = UUID.fromString("1c2d3e4f-0000-4000-8000-00000000abcd");

    private static CompoundTag save(RxSettings settings) {
        return (CompoundTag) RxSettings.MAP_CODEC.codec().encodeStart(NbtOps.INSTANCE, settings).getOrThrow();
    }

    private static RxSettings load(Tag tag) {
        return RxSettings.MAP_CODEC.codec().parse(NbtOps.INSTANCE, tag).getOrThrow();
    }

    @Test
    void nbtRoundTrip() {
        RxSettings bound = new RxSettings(PILOT, RxSource.axis(6), -0.25F, 0.75F, 100);
        assertEquals(bound, load(save(bound)));
        assertEquals(RxSettings.EMPTY, load(save(RxSettings.EMPTY)));
        RxSettings button = RxSettings.EMPTY.withOwner(PILOT).withSource(RxSource.button(3));
        assertEquals(button, load(save(button)));
        assertEquals(bound, RxSettings.MAP_CODEC.codec().parse(JsonOps.INSTANCE,
                RxSettings.MAP_CODEC.codec().encodeStart(JsonOps.INSTANCE, bound).getOrThrow()).getOrThrow());
    }

    @Test
    void savedLayoutMatchesTheDesign() {
        CompoundTag tag = save(new RxSettings(PILOT, RxSource.GAS, -1.0F, 1.0F, 64));
        assertEquals(1, tag.getIntOr("Format", 0));
        assertTrue(tag.getCompound("Bind").isPresent());
        assertEquals(4, tag.getCompound("Bind").get().getIntArray("pilot").orElseThrow().length);
        assertEquals("stick", tag.getCompound("Source").get().getStringOr("kind", ""));
        assertEquals(3, tag.getCompound("Source").get().getIntOr("index", -1));
        assertEquals(-1.0F, tag.getFloatOr("Low", 0.0F));
        assertEquals(1.0F, tag.getFloatOr("High", 0.0F));
        assertEquals(64, tag.getIntOr("Range", 0));
        CompoundTag empty = save(RxSettings.EMPTY);
        assertFalse(empty.contains("Bind"));
        assertFalse(empty.contains("Source"));
    }

    @Test
    void missingFieldsUseDefaults() {
        RxSettings read = load(new CompoundTag());
        assertNull(read.owner());
        assertNull(read.source());
        assertEquals(-1.0F, read.low());
        assertEquals(1.0F, read.high());
        assertEquals(RxLink.DEFAULT_RANGE, read.range());
        CompoundTag button = new CompoundTag();
        CompoundTag source = new CompoundTag();
        source.putString("kind", "button");
        source.putInt("index", 2);
        button.put("Source", source);
        RxSettings buttonRead = load(button);
        assertEquals(0.0F, buttonRead.low());
        assertEquals(1.0F, buttonRead.high());
    }

    @Test
    void brokenFieldsAreRepairedNotFatal() {
        CompoundTag tag = save(new RxSettings(PILOT, RxSource.GAS, -0.5F, 0.5F, 64));
        tag.putFloat("Low", Float.NaN);
        tag.putFloat("High", 7.0F);
        tag.putInt("Range", 9000);
        CompoundTag source = new CompoundTag();
        source.putString("kind", "stick");
        source.putInt("index", 12);
        tag.put("Source", source);
        tag.putString("Bind", "kaputt");
        RxSettings read = load(tag);
        assertNull(read.owner());
        assertNull(read.source());
        assertEquals(-1.0F, read.low());
        assertEquals(1.0F, read.high());
        assertEquals(RxLink.MAX_RANGE, read.range());
        assertEquals(read, RxSettings.MAP_CODEC.codec().parse(new Dynamic<>(NbtOps.INSTANCE, tag)).getOrThrow());
    }

    @Test
    void changingTheSourceResetsTheEndpoints() {
        RxSettings settings = RxSettings.EMPTY.withOwner(PILOT).withSource(RxSource.GAS).withLow(-0.2F).withHigh(0.3F).withRange(20);
        RxSettings button = settings.withSource(RxSource.button(0));
        assertEquals(0.0F, button.low());
        assertEquals(1.0F, button.high());
        assertEquals(20, button.range());
        RxSettings unbound = settings.unbound();
        assertNull(unbound.owner());
        assertNull(unbound.source());
        assertEquals(20, unbound.range());
        assertTrue(settings.ownedBy(PILOT));
        assertFalse(unbound.bound());
    }

    @Test
    void endpointsStayWithinOneAndRangeWithinLimits() {
        assertTrue(RxSettings.validEndpoint(-1.0F));
        assertTrue(RxSettings.validEndpoint(1.0F));
        assertFalse(RxSettings.validEndpoint(1.01F));
        assertFalse(RxSettings.validEndpoint(Float.NaN));
        assertEquals(1, new RxSettings(null, null, 0.0F, 0.0F, -4).range());
        assertEquals(128, new RxSettings(null, null, 0.0F, 0.0F, 129).range());
    }
}
