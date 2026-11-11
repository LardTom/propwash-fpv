package de.lardtom.propwash.dev.client;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

public final class CovRegionFiles {
    private CovRegionFiles() {
    }

    public static Path overworld(Path world, String folder) {
        return world.resolve("dimensions").resolve("minecraft").resolve("overworld").resolve(folder);
    }

    private static @Nullable CompoundTag chunk(byte[] data, int index) throws IOException {
        int at = index * 4;
        if (data.length < 8192 || at + 4 > data.length) {
            return null;
        }
        int location = ((data[at] & 255) << 24) | ((data[at + 1] & 255) << 16) | ((data[at + 2] & 255) << 8) | (data[at + 3] & 255);
        if (location == 0) {
            return null;
        }
        int offset = (location >>> 8) * 4096;
        if (offset + 5 > data.length) {
            throw new IOException("Chunk " + index + " hinter dem Dateiende");
        }
        int length = ((data[offset] & 255) << 24) | ((data[offset + 1] & 255) << 16) | ((data[offset + 2] & 255) << 8) | (data[offset + 3] & 255);
        int compression = data[offset + 4] & 255;
        ByteArrayInputStream raw = new ByteArrayInputStream(data, offset + 5, length - 1);
        InputStream in = switch (compression) {
            case 1 -> new GZIPInputStream(raw);
            case 2 -> new InflaterInputStream(raw);
            case 3 -> raw;
            default -> throw new IOException("Kompression " + compression);
        };
        try (DataInputStream input = new DataInputStream(in)) {
            return NbtIo.read(input, NbtAccounter.unlimitedHeap());
        }
    }

    public static List<CompoundTag> chunks(Path file) throws IOException {
        byte[] data = Files.readAllBytes(file);
        List<CompoundTag> out = new ArrayList<>();
        for (int i = 0; i < 1024; i++) {
            CompoundTag tag = chunk(data, i);
            if (tag != null) {
                out.add(tag);
            }
        }
        return out;
    }

    public static @Nullable String status(Path world, int cx, int cz) throws IOException {
        Path file = overworld(world, "region").resolve("r." + (cx >> 5) + "." + (cz >> 5) + ".mca");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        CompoundTag tag = chunk(Files.readAllBytes(file), (cx & 31) + (cz & 31) * 32);
        return tag == null ? null : tag.getString("Status").orElse("?");
    }

    public static Map<String, CompoundTag> entities(Path world) throws IOException {
        Map<String, CompoundTag> out = new TreeMap<>();
        Path dir = overworld(world, "entities");
        if (!Files.isDirectory(dir)) {
            return out;
        }
        List<Path> files;
        try (Stream<Path> list = Files.list(dir)) {
            files = list.filter(p -> p.getFileName().toString().endsWith(".mca")).sorted().toList();
        }
        for (Path file : files) {
            for (CompoundTag chunk : chunks(file)) {
                ListTag entities = chunk.getListOrEmpty("Entities");
                for (int i = 0; i < entities.size(); i++) {
                    CompoundTag entity = entities.getCompoundOrEmpty(i);
                    entity.getIntArray("UUID").map(UUIDUtil::uuidFromIntArray).ifPresent(uuid -> out.put(uuid.toString(), entity));
                }
            }
        }
        return out;
    }

    public static String gameRules(Path world) throws IOException {
        Path file = world.resolve("data").resolve("minecraft").resolve("game_rules.dat");
        return Files.isRegularFile(file) ? NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()).toString() : "";
    }
}
