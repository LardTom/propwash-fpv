package de.lardtom.propwash.drone;

import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class CovFlightDatapack {
    public static final String ROOT = "/cov/cov-flight/";
    private static final String PREFIX = "cov_flight_";

    private CovFlightDatapack() {
    }

    public static String packId(String name) {
        return "file/" + PREFIX + name;
    }

    private static Path dir(MinecraftServer server, String name) {
        return server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(PREFIX + name);
    }

    public static void install(MinecraftServer server, String name, List<String> files) {
        Path root = dir(server, name);
        try {
            delete(root);
            Files.createDirectories(root);
            String format = "[" + SharedConstants.DATA_PACK_FORMAT_MAJOR + ", " + SharedConstants.DATA_PACK_FORMAT_MINOR + "]";
            Files.writeString(root.resolve("pack.mcmeta"), "{\"pack\": {\"description\": \"Propwash Abdeckung " + name + "\", \"min_format\": " + format
                    + ", \"max_format\": " + format + "}}\n", StandardCharsets.UTF_8);
            for (String file : files) {
                try (InputStream in = CovFlightDatapack.class.getResourceAsStream(ROOT + name + "/" + file)) {
                    if (in == null) {
                        throw new IllegalStateException("Datenpaket-Datei fehlt: " + ROOT + name + "/" + file);
                    }
                    Path target = root.resolve(file);
                    Files.createDirectories(target.getParent());
                    Files.write(target, in.readAllBytes());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.getPackRepository().reload();
        if (!server.getPackRepository().getAvailableIds().contains(packId(name))) {
            throw new IllegalStateException("Datenpaket " + packId(name) + " nicht gefunden");
        }
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "datapack enable \"" + packId(name) + "\"");
    }

    public static boolean enabled(MinecraftServer server, String name) {
        return server.getPackRepository().getSelectedIds().contains(packId(name));
    }

    public static void remove(MinecraftServer server, String name) {
        if (enabled(server, name)) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "datapack disable \"" + packId(name) + "\"");
        }
        try {
            delete(dir(server, name));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void removeFolder(MinecraftServer server, String name) {
        try {
            delete(dir(server, name));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void delete(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
