package de.lardtom.propwash.guard;

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

public final class CovGuardDatapack {
    public static final String NAME = "cov_api_guard";
    public static final String ROOT = "/cov/cov-api/guard_pack/";
    public static final String CHANGED_FILE = "data/propwash/recipe/aio_20.json";
    public static final String NEW_FILE = "data/propwash/recipe/cov_api_extra.json";
    private static final List<String> FILES = List.of(CHANGED_FILE, NEW_FILE);

    private CovGuardDatapack() {
    }

    public static String packId() {
        return "file/" + NAME;
    }

    private static Path dir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(NAME);
    }

    public static void install(MinecraftServer server) {
        Path root = dir(server);
        try {
            delete(root);
            Files.createDirectories(root);
            String format = "[" + SharedConstants.DATA_PACK_FORMAT_MAJOR + ", " + SharedConstants.DATA_PACK_FORMAT_MINOR + "]";
            Files.writeString(root.resolve("pack.mcmeta"), "{\"pack\": {\"description\": \"Propwash Abdeckung cov-api\", \"min_format\": " + format
                    + ", \"max_format\": " + format + "}}\n", StandardCharsets.UTF_8);
            for (String file : FILES) {
                try (InputStream in = CovGuardDatapack.class.getResourceAsStream(ROOT + file)) {
                    if (in == null) {
                        throw new IllegalStateException("Datenpaket-Datei fehlt: " + ROOT + file);
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
        if (!server.getPackRepository().getAvailableIds().contains(packId())) {
            throw new IllegalStateException("Datenpaket " + packId() + " nicht gefunden");
        }
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "datapack enable \"" + packId() + "\"");
    }

    public static void remove(MinecraftServer server) {
        if (server.getPackRepository().getSelectedIds().contains(packId())) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "datapack disable \"" + packId() + "\"");
        }
        try {
            delete(dir(server));
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
