package de.lardtom.propwash.drone.bind;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BindCodecsTest {
    private static final UUID PILOT = UUID.fromString("1c2d3e4f-0000-4000-8000-00000000abcd");
    private static final List<ReceiverBind> SAMPLES = List.of(
            ReceiverBind.of(PILOT),
            ReceiverBind.of(new UUID(5L, 6L)),
            ReceiverBind.CLEARED);

    @Test
    void jsonAndNbtRoundTrip() {
        for (ReceiverBind bind : SAMPLES) {
            JsonElement json = BindCodecs.CODEC.encodeStart(JsonOps.INSTANCE, bind).getOrThrow();
            assertEquals(bind, BindCodecs.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
            Tag tag = BindCodecs.CODEC.encodeStart(NbtOps.INSTANCE, bind).getOrThrow();
            assertEquals(bind, BindCodecs.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow());
        }
    }

    @Test
    void clearedIsAnEmptyObject() {
        JsonElement json = BindCodecs.CODEC.encodeStart(JsonOps.INSTANCE, ReceiverBind.CLEARED).getOrThrow();
        assertTrue(json.getAsJsonObject().isEmpty());
        assertTrue(BindCodecs.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().cleared());
    }

    @Test
    void savedPhraseLocksAreIgnored() {
        JsonObject legacy = BindCodecs.CODEC.encodeStart(JsonOps.INSTANCE, ReceiverBind.of(PILOT)).getOrThrow().getAsJsonObject();
        legacy.addProperty("lock", 0x7FED_CBA9_8765_4321L);
        ReceiverBind read = BindCodecs.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow();
        assertEquals(ReceiverBind.of(PILOT), read);
        JsonObject written = BindCodecs.CODEC.encodeStart(JsonOps.INSTANCE, read).getOrThrow().getAsJsonObject();
        assertFalse(written.has("lock"));
        CompoundTag tag = (CompoundTag) BindCodecs.CODEC.encodeStart(NbtOps.INSTANCE, ReceiverBind.of(PILOT)).getOrThrow();
        tag.putLong("lock", -42L);
        assertEquals(ReceiverBind.of(PILOT), BindCodecs.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow());
    }

    @Test
    void legacyFilesAreRemovedOnce(@TempDir Path folder) throws IOException {
        Path file = ReceiverBinds.legacySecretFile(folder);
        assertEquals(folder.resolve("propwash").resolve("receiver_bind.dat"), file);
        assertFalse(ReceiverBinds.dropLegacyFile(file));
        Files.createDirectories(file.getParent());
        Files.writeString(file, "alt");
        assertTrue(ReceiverBinds.dropLegacyFile(file));
        assertFalse(Files.exists(file));
        assertFalse(ReceiverBinds.dropLegacyFile(file));
    }

    @Test
    void streamRoundTrip() {
        for (ReceiverBind bind : SAMPLES) {
            ByteBuf buffer = Unpooled.buffer();
            BindCodecs.STREAM_CODEC.encode(buffer, bind);
            assertEquals(bind, BindCodecs.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        }
    }
}
